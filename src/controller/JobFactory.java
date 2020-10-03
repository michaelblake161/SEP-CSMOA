package controller;

import java.io.File;
import java.io.FileNotFoundException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.Scanner;

import model.Job;

public class JobFactory {
	
	private static ArrayList<Job> jobPool = new ArrayList<Job>();
	
	public static void readJobsFromCSV(String path) {
		System.out.println("init jobs start...");
		try {
			File file = new File(path);
			Scanner sc = new Scanner(file);
			String header = sc.nextLine();
			//System.out.println("Job header: "+header);
			while (sc.hasNext()) {
				boolean jobAdded = false;
				String row = sc.nextLine();
				String[] reform = row.split(",");
				String jobId = reform[0];
				String jobType = reform[1];
				String jobDescription = reform[2];
				String jobIssueCode = reform[3];
				String jobIssueDescrp = reform[4];
				String jobActType = reform[5];
				String jobActDescrp = reform[6];
				String jobTimeString = reform[8];
				String jobPriority = reform[9];
				String jobSuburb = reform[10];
				String jobStreet = reform[11];
				String houseNum1 = reform[12];
				String houseNum2 = reform[13];
				String postCode = reform[14];
				String fitterDistrict = reform[15];
				String jobDurationString = reform[16];
				String yearStr = reform[7].substring(0, 4);
				String monthStr = reform[7].substring(4, 6);
				String dayStr = reform[7].substring(6, reform[7].length());
				try {
					LocalDate jobDate = LocalDate.parse(yearStr+"-"+monthStr+"-"+dayStr);
					LocalTime jobTime = LocalTime.parse(jobTimeString);
					LocalDateTime jobDateAndTime = LocalDateTime.of(jobDate, jobTime);
					long jobDuration = Long.parseLong(jobDurationString);
					LocalDateTime endDateAndTime = LocalDateTime.of(jobDate, jobTime).plusMinutes(jobDuration);
					jobPool.add(new Job(jobId, jobType, jobDescription, jobIssueCode, jobIssueDescrp, jobActType, jobActDescrp, jobDate, jobTime, jobDateAndTime, jobPriority, jobSuburb, jobStreet, houseNum1, houseNum2, postCode, fitterDistrict, jobDuration, endDateAndTime));
					jobAdded = true;
				}
				catch (Exception e) {
					System.out.println("job data parse error.");
					e.printStackTrace();
				}
				if (!jobAdded) {
					System.err.println("\nFAILED TO INIT JOB!");
					System.out.println("Job num: "+jobId+", Job Type: "+jobType+", Job Description: "+jobDescription+", Job IsssueCode: "+jobIssueCode+", Job IssueDescription: "+jobIssueDescrp+", Job Activity: "+jobActType+", Job Activity Description: "+jobActDescrp+", Date: "+reform[7]+", Time: "+jobTimeString+
							", Priority: "+jobPriority+", Suburb: "+jobSuburb+", Street: "+jobStreet+", House Num1: "+houseNum1+", House Num2: "+houseNum2+", Poscode: "+postCode+", Fitter District: "+fitterDistrict+", Work Time Elapsed: "+jobDurationString );
				}
			}
			sc.close();
		}
		catch (FileNotFoundException e) {
			System.out.println("Something went wrong with job file.\n");
			e.printStackTrace();
		}
		System.out.println("init jobs finish.");
		System.out.println("\nAdded Jobs from the file:\n");
		for(Job j : jobPool)
			System.out.println(j);
	}

	public static ArrayList<Job> getJobPool() {
		return jobPool;
	}

	

}
