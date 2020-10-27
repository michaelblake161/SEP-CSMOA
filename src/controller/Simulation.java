package controller;

import java.io.IOException;
import java.time.LocalDateTime;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.Iterator;
import java.util.PriorityQueue;

import org.locationtech.jts.geom.Coordinate;

import com.opencsv.exceptions.CsvDataTypeMismatchException;
import com.opencsv.exceptions.CsvRequiredFieldEmptyException;

import model.CompletedJobRecord;
import model.GST;
import model.Job;

/**
 * @author Michael Blake
 * @author Clark Skinner
 * 
 *         Simulation class to handle assigning jobs to GSTs in early stages of
 *         development
 */
public class Simulation {

	// Queue storage for jobs
	private PriorityQueue<Job> jobQueue = new PriorityQueue<Job>();

	// Deque storage for jobs waiting to be added to the active Queue when there is
	// no GST available
	private Deque<Job> idleJobQueue = new ArrayDeque<Job>();

	// Store completed jobs
	private ArrayList<CompletedJobRecord> completedJobs = new ArrayList<CompletedJobRecord>();

	// ArrayList of currently available GSTs
	private ArrayList<GST> availableGSTPool = new ArrayList<GST>();

	// ArrayList of currently busy GSTs
	private ArrayList<GST> busyGSTs = new ArrayList<GST>();

	// Strings representing filenames to pass as arguments in cmdline
	private String JOB_FILE_PATH = "JobFiles/oneperday.csv";

	private String GST_FILE_PATH = "GSTFiles/gsts.csv";

	private String LOG_FILE_JOB = "jobOutput.csv";

	private String LOG_FILE_GST = "gstOutput.csv";

	// An Integer representing the required compliance time in seconds
	private final int COMPLIANCE_TIME = 1800;

	// LocalDate object referencing the next day in the simulation
	private LocalDate nextDay;

	/**
	 * Main simulation method
	 * 
	 * @throws IOException
	 * @throws SecurityException
	 * @throws CsvRequiredFieldEmptyException
	 * @throws CsvDataTypeMismatchException
	 * @throws InterruptedException
	 */

	private void simulate(LocalDateTime currentTime, LocalDateTime endTime) throws SecurityException, IOException,
			CsvDataTypeMismatchException, CsvRequiredFieldEmptyException, InterruptedException {

		int complianceCounter = 0;
		long totalTravelTime = 0;
		long jobIdleTime = 0;
		LocalDateTime startTime = currentTime;
		LocalDate thisDay = currentTime.toLocalDate();
		nextDay = null;

		availableGSTPool = GSTFactory.getNextGSTs(thisDay);
		ArrayList<Job> jobPool = JobFactory.getJobPool();
		do {
			thisDay = currentTime.toLocalDate();
			checkDay(thisDay, nextDay);
			int availableGSTs = availableGSTPool.size();
			for (Iterator<Job> jobPoolIter = jobPool.iterator(); jobPoolIter.hasNext();) {
				Job j = jobPoolIter.next();

				if (currentTime.equals(j.getOrderCreateDateAndTime()) && availableGSTs == 0) {
					idleJobQueue.addLast(j);
				}
				if (!idleJobQueue.isEmpty() && availableGSTs > 0) {
					Iterator<Job> iter = idleJobQueue.iterator();
					iter.hasNext();
					{
						Job current = iter.next();
						jobIdleTime = SimUtils.calculateTimeBetween(current.getOrderCreateDateAndTime(), currentTime);
						current.setIdleTime(jobIdleTime);
						jobQueue.add(current);
						jobPoolIter.remove();
						iter.remove();
						break;
					}

				} else if (currentTime.equals(j.getOrderCreateDateAndTime()) && availableGSTs > 0
						&& idleJobQueue.isEmpty()) {
					jobQueue.add(j);
					jobPoolIter.remove();
				}

			}

			if (jobQueue.size() > 0) {
				for (Job j : jobQueue) {
					if (j.getAssignedGST() == null) {
						Coordinate jobCoord = SimUtils.getJobLocation(j);
						LocalDateTime jobTime = j.getOrderCreateDateAndTime();
						int jobDuration = j.getJobDuration();
						GST gst = SimUtils.findClosestGst(jobCoord, COMPLIANCE_TIME, jobTime, availableGSTPool);
						int travelTime = 0;
						if (gst != null) {
							System.out.println("Found the closest GST: " + gst.getgSTid() + " in 30min isochrone.");
							Coordinate gstCoord = new Coordinate(gst.getLat(), gst.getLon());
							travelTime = AzureMapsApi.getRouteTime(gstCoord, jobCoord, currentTime);
							if ((travelTime + j.getIdleTime()) < COMPLIANCE_TIME) {
								complianceCounter++;
							}
							j.setTravelTimeInSeconds(travelTime);
							System.out.println("Travel Time is: " + SimUtils.formatSeconds(travelTime) + "\n");
							j.setEndDateAndTime(
									jobTime.plusMinutes(jobDuration).plusSeconds(travelTime).plusSeconds(jobIdleTime));
							totalTravelTime = totalTravelTime + travelTime;
							j.setAssignedGST(gst);

							// Set the time that the GST will become available again
							// Reapply the travel time to simulate a GST returning to their previous
							// position
							gst.setFinishTime(j.getEndDateAndTime().plusSeconds(travelTime));
							availableGSTPool.remove(gst);
							busyGSTs.add(gst);

						} else if (gst == null && !availableGSTPool.isEmpty()) {
							gst = SimUtils.findGstByStraightLineDistance(jobCoord, availableGSTPool);
							System.out.println("Found the closest GST: " + gst.getgSTid() + " outside isochrone");
							Coordinate gstCoord = new Coordinate(gst.getLat(), gst.getLon());
							travelTime = AzureMapsApi.getRouteTime(gstCoord, jobCoord, currentTime);
							if ((travelTime + j.getIdleTime()) < COMPLIANCE_TIME) {
								complianceCounter++;
							}
							System.out.println("Travel Time is: " + SimUtils.formatSeconds(travelTime) + "\n");
							j.setTravelTimeInSeconds(travelTime);

							// Set the finishing time for the job
							j.setEndDateAndTime(
									jobTime.plusMinutes(jobDuration).plusSeconds(travelTime).plusSeconds(jobIdleTime));
							totalTravelTime = totalTravelTime + travelTime;
							j.setAssignedGST(gst);

							// Set the time that the GST will become available again
							// Reapply the travel time to simulate a GST returning to their previous
							// position
							gst.setFinishTime(j.getEndDateAndTime().plusSeconds(travelTime));
							availableGSTPool.remove(gst);
							busyGSTs.add(gst);

						}

					}

				}
			}
			SimUtils.removeCompletedJobFromQueue(currentTime, jobQueue, completedJobs);
			SimUtils.checkGstFinished(currentTime, availableGSTPool, busyGSTs);
			currentTime = currentTime.plusSeconds(1);
			if (jobPool.isEmpty() && jobQueue.isEmpty() && busyGSTs.isEmpty()) {
				endTime = currentTime;
			}

		} while (currentTime.isBefore(endTime));
		int jobsCompleted = completedJobs.size();
		int incompleteJobs = idleJobQueue.size() + jobQueue.size();
		if (jobsCompleted == 0) {
			System.err.println("No Completed Jobs");
		} else {
			long avgTravelTime = totalTravelTime / jobsCompleted;
			float complianceRate = (float) complianceCounter / (jobsCompleted + incompleteJobs) * 100;

			SimUtils.generateOutput(avgTravelTime, complianceRate, incompleteJobs, completedJobs,
					SimUtils.getOverallGstStats(GSTFactory.getGSTpool()), LOG_FILE_JOB, LOG_FILE_GST);
		}

	}

	private void runSimulation() throws SecurityException, IOException, CsvDataTypeMismatchException,
			CsvRequiredFieldEmptyException, InterruptedException {
		JobFactory.readJobsFromCSV(JOB_FILE_PATH);
		GSTFactory.readGSTsFromCSV(GST_FILE_PATH);
		LocalDateTime firstJob = JobFactory.getJobPool().get(0).getOrderCreateDateAndTime();
		LocalTime startTime = LocalTime.of(7, 0);
		LocalDateTime startDate = LocalDateTime.of(firstJob.toLocalDate(), startTime);
		LocalDateTime endDate = JobFactory.getJobPool().get(JobFactory.getJobPool().size() - 1)
				.getOrderCreateDateAndTime().plusDays(1);
		simulate(startDate, endDate);

	}

	private void initFileNames(String jobFile, String gstFile, String outputjobs, String outputgst) {
		JOB_FILE_PATH = jobFile;
		GST_FILE_PATH = gstFile;
		LOG_FILE_JOB = outputjobs;
		LOG_FILE_GST = outputgst;

	}

	private void checkDay(LocalDate currThisDay, LocalDate currNextDay) {
		if (currNextDay == null) {
			nextDay = currThisDay.plusDays(1);
		} else if (currThisDay.compareTo(currNextDay) == 0) {
			System.out.println("This Day = Next Day\n");
			availableGSTPool.clear();
			availableGSTPool = GSTFactory.getNextGSTs(currThisDay);
			System.out.println("GSTS in Pool: \n");
			for (GST g : availableGSTPool) {
				System.out.println(g.getgSTid() + "\n");
			}
			nextDay = currThisDay.plusDays(1);
		}
	}

	public static void main(String[] args) throws SecurityException, IOException, CsvDataTypeMismatchException,
			CsvRequiredFieldEmptyException, InterruptedException {

		Simulation s = new Simulation();
		if (args.length == 4) {
			s.initFileNames(args[0], args[1], args[2], args[3]);
		}
		s.runSimulation();

	}// end main

}// end class