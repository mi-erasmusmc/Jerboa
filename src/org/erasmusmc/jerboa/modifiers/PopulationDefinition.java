/***********************************************************************************
 *                                                                                 *
 * Copyright (C) 2017  Erasmus MC, Rotterdam, The Netherlands                      *
 *                                                                                 *
 * This file is part of Jerboa.                                                    *
 *                                                                                 *
 * This program is free software; you can redistribute it and/or                   *
 * modify it under the terms of the GNU General Public License                     *
 * as published by the Free Software Foundation; either version 2                  *
 * of the License, or (at your option) any later version.                          *
 *                                                                                 *
 * This program is distributed in the hope that it will be useful,                 *
 * but WITHOUT ANY WARRANTY; without even the implied warranty of                  *
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the                   *
 * GNU General Public License for more details.                                    *
 *                                                                                 *
 * You should have received a copy of the GNU General Public License               *
 * along with this program; if not, write to the Free Software                     *
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301, USA. *
 *                                                                                 *
 ***********************************************************************************/


/******************************************************************************************
 * Jerboa software version 3.0 - Copyright Erasmus University Medical Center, Rotterdam   *
 *																				          * 	
 * Author: Marius Gheorghe (MG) Peter Rijnbeek (PR) - department of Medical Informatics	  *			
 * 																						  *	
 * $Rev:: 3961              $:  Revision of last commit                                   *
 * $Author:: root           $:  Author of last commit                                     *
 * $Date:: 2013-05#$:  Date and time (CET) of last commit								  *
 ******************************************************************************************/

package org.erasmusmc.jerboa.modifiers;

import java.util.ArrayList;
import java.util.List;

import org.erasmusmc.jerboa.Jerboa;
import org.erasmusmc.jerboa.config.FilePaths;
import org.erasmusmc.jerboa.config.Parameters;
import org.erasmusmc.jerboa.dataClasses.Patient;
import org.erasmusmc.jerboa.engine.Modifier;
import org.erasmusmc.jerboa.utilities.DateUtilities;
import org.erasmusmc.jerboa.utilities.ExtendedMultiKey;
import org.erasmusmc.jerboa.utilities.Logging;
import org.erasmusmc.jerboa.utilities.MultiKeyBag;
import org.erasmusmc.jerboa.utilities.StringUtilities;
import org.erasmusmc.jerboa.utilities.TimeUtilities;
import org.erasmusmc.jerboa.utilities.Wildcard;
import org.erasmusmc.jerboa.utilities.stats.HistogramStats;
import org.erasmusmc.jerboa.utilities.stats.Stats;


/**
 * This modifier defines the population start date and the population end date based
 * mainly on settings present in the script file. it will set the populationStartDate
 * and PopulationEndDate attributes of a patient object passed as argument in the process method.
 * 
 * @author MG {@literal &} PR
 *
 */
public class PopulationDefinition extends Modifier{

	/**
	 * The default amount of follow-up time (i.e. number of days between
	 * system entry date and population entry date).
	 * The runInPeriod is specified per database. If a database is not present in the list
	 * it will use the value specified for OTHER or 0 if that is also not present.
	 * It still accepts the old style run-in period definition of a single value for all databases.
	 * Example:
	 * 
	 *   IPCI;0         A run-in period of 0 days for the IPCI database
	 *   OTHER;365      A run-in period of 365 days for all other databases
	 */
	public List<String> runInPeriod = new ArrayList<String>();

	/**
	 * If the system entry date is less than this number of days from the
	 * birth date of the patient, the population entry date is set to the birth date.
	 */
	public int childInclusionPeriod;

	/**
	 * Only applies if childInclusionPeriod has a value!
	 * If true the patient start is set to the birth date of the patient
	 * if false the patient start is unchanged and the child enters without run in period.
	 */
	public boolean childToBirthDate;
	
	/**
	 * Start date of the study. If set, all patient start dates are limited
	 * to this date. Format: YYYYMMDD.
	 */
	public String studyStart;

	/**
	 * End date of the study. If set, all patient end dates are limited to
	 * this date. Format: YYYYMMDD.
	 */
	public String studyEnd;

	/**
	 * Age at which people can enter the population. For example if the minAge
	 * is set to 10, there will be no nine-year old in the population anymore.
	 */
	public int minAge;

	/**
	 * Age at which people must exit the population. For example if the maxAge
	 * is set to 10, there will be no eleven-year old in the population anymore.
	 */
	public int maxAge;

	/**
	 * Only keep patients that have at least this much patient time 
	 * remaining after applying run-in time and modifiers.
	 */
	public int minimumDaysOfPatientTime;
	
	/**
	 * Can be used to include the population end date 
	 */
	public boolean includePopulationEnd;
	
	/**
	 * If true an attrition filename will be created or appended on called Attrition.csv
	 */
	public boolean attritionFile;

	//run-in period
	private int currentRunInPeriod;

	
	//counters
	private int countBirthdate; 		       //keeps track of the patients for which the study start date is their birth date 
	private int countRemoved;   		       //keeps track of the patients that are not in the population
	private int originalCount;		           //keeps track of the overall patient count
	private int populationPeriodCount;	       //keeps track of patient in the study period
	private int ageRangeCount;			       //keeps track of the patients that are in the age range
	//private long patientTime;   		       //keeps track of the total patient time
	
	private int countRemovedRunInPeriod;       //keeps track of patients who have not enough time after applying runin period
	private int countRemovedInitialTime;       //keeps track of patients whose initial patient time is too short
	private int countRemovedStudyPeriod;       //keeps track of patients who have not enough patient time in the study period
	private int countRemovedAgeRange;          //keeps track of patients who have not enough patient time in the specified age range

	private MultiKeyBag patientTime;	       //used for stats on the remaining patient time
	private HistogramStats stats;		       //statistics of patient time

	private String attritionFileName;          //used to save the attrition

	//study range in days
	private Integer studyStartInDays;
	private Integer studyEndInDays;
	@SuppressWarnings("unused")
	private int minAgeInDays;
	@SuppressWarnings("unused")
	private int maxAgeInDays;


	@Override
	public void setNeededFiles() {/*NOTHING TO ADD*/}

	@Override
	public void setNeededExtendedColumns() {/*NOTHING TO ADD*/}

	@Override
	public void setNeededNumericColumns() {
		// TODO Auto-generated method stub
		
	}

	@Override
	public boolean init() {

		boolean initOK = true;
		
		currentRunInPeriod = -1;
		int defaultRunInPeriod = 0;
		for (String runInDefinition : runInPeriod) {
			String[] runInDefinitionSplit = runInDefinition.split(";");
			if ((runInPeriod.size() == 1) && (runInDefinitionSplit.length == 1)) { // Old style definition
				try {
					currentRunInPeriod = Integer.parseInt(runInDefinitionSplit[0]);
				}
				catch (NumberFormatException e) {
					Logging.add("Invalid old style runInPeriod definition: " + runInDefinition, Logging.ERROR);
					initOK = false;
				}
			}
			else {
				if (runInDefinitionSplit.length == 2) {
					String database = runInDefinitionSplit[0].toUpperCase();
					int period = -1;
					try {
						period = Integer.parseInt(runInDefinitionSplit[1]);
					}
					catch (NumberFormatException e) {
						Logging.add("Invalid period in runInPeriod definition: " + runInDefinition, Logging.ERROR);
						initOK = false;
					}
					if (initOK) {
						if (Parameters.DATABASE_NAME.toUpperCase().equals(database)) {
							currentRunInPeriod = period;
						}
						else if (database.equals("OTHER")) {
							defaultRunInPeriod = period;
						}
					}
				}
				else {
					Logging.add("Invalid runInPeriod definition <database;period> " + runInDefinition, Logging.ERROR);
					initOK = false;
				}
			}
		}
		
		if (initOK) {
			if (currentRunInPeriod == -1) {
				currentRunInPeriod = defaultRunInPeriod;
			}
		}
		
		if (studyStart.equals("")) {
			studyStartInDays = -1;
		}
		else {
			if (!DateUtilities.isValidDate(studyStart)) {
				Logging.add("Invalid studyStart", Logging.ERROR);
				Logging.addNewLine();
				initOK = false;
			}
			else {
				studyStartInDays = DateUtilities.dateToDays(studyStart, DateUtilities.DATE_ON_YYYYMMDD);
			}
		}
		
		if (studyEnd.equals("")) {
			studyEndInDays = -1;
		}
		else {
			if (!DateUtilities.isValidDate(studyEnd)) {
				Logging.add("Invalid studyEnd", Logging.ERROR);
				Logging.addNewLine();
				initOK = false;
			}
			else {
				studyEndInDays = DateUtilities.dateToDays(studyEnd, DateUtilities.DATE_ON_YYYYMMDD);
			}
		}

		minAgeInDays = (int)(minAge * 365.25);
		maxAgeInDays = (int)((maxAge+1) * 365.25);
		
		if (initOK && intermediateFiles){
			initOK &= Jerboa.getOutputManager().addFile(this.intermediateFileName);
			Jerboa.getOutputManager().writeln(this.intermediateFileName,"SubsetID,PatientID,BirthDate,Gender,StartDate,EndDate,PopulationStart,PopulationEnd", false);
		}
		
		if (attritionFile){
			//TODO: how to get the module name that uses this modifier in a better way? 
			attritionFileName = FilePaths.WORKFLOW_PATH+this.getParentModule()+"/"+
					Parameters.DATABASE_NAME+"_"+this.getParentModule()+
					"_"+TimeUtilities.TIME_STAMP+"_"+Parameters.VERSION+"_attrition.csv";
			Jerboa.getOutputManager().addFile(attritionFileName, 100);
		}
		
		patientTime = new MultiKeyBag();
		
		// Initialize counters
		countBirthdate = 0; 		       //keeps track of the patients for which the study start date is their birth date 
		countRemoved = 0;   		       //keeps track of the patients that are not in the population
		originalCount = 0;		           //keeps track of the overall patient count
		populationPeriodCount = 0;	       //keeps track of patient in the study period
		ageRangeCount = 0;			       //keeps track of the patients that are in the age range
		//patientTime = 0;                   //keeps track of the total patient time

		countRemovedRunInPeriod = 0;       //keeps track of patients who have not enough time after applying runin period
		countRemovedInitialTime = 0;       //keeps track of patients whose initial patient time is too short
		countRemovedStudyPeriod = 0;       //keeps track of patients who have not enough patient time in the study period
		countRemovedAgeRange = 0;          //keeps track of patients who have not enough patient time in the specified age range
		
		return initOK;
	}

	/**
	 *  Sets the population start date and population end date for the patient passed as argument.
	 *  @param patient - the patient to be processed
	 *  @return patient - the same patient object with a defined
	 *  population start date and population end date
	 */
	@Override
	public Patient process(Patient patient) {
		
		if (!processPatientIDs.isEmpty() && !processPatientIDs.contains(patient.getPatientID())){
			patient.setInPopulation(false);
			patient.setInCohort(false);
			return patient;
		}

		if (patient != null) {
			boolean removed = false;

			//set the population dates as the start date and end date of the patient
			patient.setPopulationStartDate(patient.startDate);
			patient.setPopulationEndDate(patient.endDate);

			//update patient counter
			if (!inPostProcessing) originalCount++;

			//patients less than one year old - no runInPeriod
			if ((patient.populationStartDate - patient.birthDate) < childInclusionPeriod) {
				if (childToBirthDate)
					patient.populationStartDate = patient.birthDate;
				if (!inPostProcessing) countBirthdate++;
			}else{
				patient.populationStartDate = patient.startDate + currentRunInPeriod;
			}
			
			if ((patient.populationEndDate - patient.populationStartDate) < minimumDaysOfPatientTime) {
				if (!inPostProcessing) countRemovedRunInPeriod++;
				removed = true;
			}
			else {
				if ((patient.populationEndDate - patient.populationStartDate) < minimumDaysOfPatientTime) {
					if (!inPostProcessing) countRemovedInitialTime++;
					removed = true;
				}
				else {
					//adjust the population start date if inferior  to the study start 
					if (studyStartInDays != -1 && patient.populationStartDate < studyStartInDays){
						patient.populationStartDate = studyStartInDays;
					}

					// adjust the population end date if superior  to the study start 
					if (studyEndInDays != -1 && patient.populationEndDate > studyEndInDays){
						patient.populationEndDate = studyEndInDays;
					}
					if ((patient.populationEndDate - patient.populationStartDate) < minimumDaysOfPatientTime) {
						if (!inPostProcessing) countRemovedStudyPeriod++;
						removed = true;
					}
					else {
						if (patient.populationEndDate > patient.populationStartDate){
							if (!inPostProcessing) populationPeriodCount++;
						}

						//set the day the patient would reach the minimum age allowed
						//changed to take care of leap years
						int minAgeDay = patient.getBirthdayInYear(patient.getBirthYear()+minAge);
						//and adjust if needed the population start date
						if (patient.populationStartDate < minAgeDay){
							patient.populationStartDate = minAgeDay;
						}

						//set the day the patient would reach the maximum age allowed
						if (maxAge!=999){
							int maxAgeDay = patient.getBirthdayInYear(patient.getBirthYear()+maxAge+1);

							//and adjust if needed the population end date
							if (patient.populationEndDate > maxAgeDay){
								patient.populationEndDate = maxAgeDay;
							}
						}
						
						if ((patient.populationEndDate - patient.populationStartDate) < minimumDaysOfPatientTime) {
							if (!inPostProcessing) countRemovedAgeRange++;
							removed = true;
							patient.setInPopulation(false);
							patient.setInCohort(false);
							patient.populationStartDate = -1;
							patient.populationEndDate = -1;
						}
						else {
							if (patient.populationEndDate > patient.populationStartDate){
								if (!inPostProcessing) ageRangeCount++;
							}

							patient.setInPopulation(true);
							patient.setInCohort(true);
							
							//update patient time per gender
							if (intermediateStats)
								patientTime.add(new ExtendedMultiKey("ALL",patient.populationEndDate - patient.populationStartDate));
							
							if (includePopulationEnd)
								patient.populationEndDate += 1;
						}
					}
				}
			}
			
			if (removed) {
				if (!inPostProcessing) countRemoved++;
				patient.setInPopulation(false);
				patient.setInCohort(false);
				patient.populationStartDate = -1;
				patient.populationEndDate = -1;
			}
			
			patient.cohortStartDate = patient.populationStartDate;
			patient.cohortEndDate = patient.populationEndDate;
			
			//add to the output  
			addToOutputBuffer(patient);
		}
		
		//update result set if needed
		if (!Jerboa.unitTest && !Jerboa.getResultSet().isOnlyInCohort()){
			if (patient.inPopulation){
				Jerboa.getResultSet().add(patient.getPatientID(),"PopulationStart", (DateUtilities.daysToDate(patient.getPopulationStartDate())));
				Jerboa.getResultSet().add(patient.getPatientID(),"PopulationEnd", (DateUtilities.daysToDate(patient.getPopulationEndDate())));
			} else {
				Jerboa.getResultSet().add(patient.getPatientID(),"PopulationStart", Jerboa.getResultSet().getMissingValue());
				Jerboa.getResultSet().add(patient.getPatientID(),"PopulationEnd", Jerboa.getResultSet().getMissingValue());		
			}
		}
			
		return patient;
	}

	@Override
	public void calcStats(){
		stats = patientTime.getHistogramStats(new ExtendedMultiKey("ALL",Wildcard.INTEGER()));
	}

	@Override
	public void addToOutputBuffer(Patient patient){
		if (intermediateFiles) {
			addToOutputBuffer(
					patient.subset+","+
					patient.ID+","+
					DateUtilities.daysToDate(patient.birthDate)+","+
					Patient.convertGender(patient.gender)+","+
					DateUtilities.daysToDate(patient.startDate)+","+
					DateUtilities.daysToDate(patient.endDate) + "," +
					(patient.getPopulationStartDate() == -1 ? "" : DateUtilities.daysToDate(patient.getPopulationStartDate())) + "," +
					(patient.getPopulationEndDate() == -1 ? "" : DateUtilities.daysToDate(patient.getPopulationEndDate())));
		}
	}

	@Override
	public void outputResults(){
		
		flushRemainingData();

		Logging.addNewLine();
		Logging.add("Population definition modifier results:");
		Logging.add("--------------------------------------------");

		if (childToBirthDate)
			Logging.add(countBirthdate + " children entered on their birthdate without run in");
		else 
			Logging.add(countBirthdate + " children entered without run in");

		Logging.addNewLine();
		Logging.add(originalCount + " patients");
		Logging.add(countRemovedInitialTime + " patients did not have enough initial patient time and were removed");
		Logging.add(countRemovedStudyPeriod + " patients did not have enough patient time in the study period and were removed");
		Logging.add(countRemovedAgeRange + " patients did not have enough patient time in the specified age range and were removed");
		
		if (countRemoved == Stats.nbPatients){
			Logging.add("There are no patients with observation time in the population.");
			Logging.addNewLine();
		}
		else{	
			Logging.add(Integer.toString(originalCount - countRemoved) + " patients with observation time in the population.");
			Logging.addNewLine();
			
			if (intermediateStats && stats!=null){
				Logging.add("Remaining patient time in population (years):");
				Logging.add("Sum: " +StringUtilities.format((float)stats.getSum()/(float)DateUtilities.daysPerYear)+" " +
						"Min: " +StringUtilities.format((float)stats.getMin()/(float)DateUtilities.daysPerYear)+" "  +
						"Max: " +StringUtilities.format((float)stats.getMax()/(float)DateUtilities.daysPerYear)+" ");
				Logging.add("Mean: " +StringUtilities.format((float)stats.getMean()/(float)DateUtilities.daysPerYear)+" " +
						"SD: " +StringUtilities.format((float)stats.getStdDev()/(float)DateUtilities.daysPerYear)+" " +
						"Nr: " +stats.getCount());
				Logging.addNewLine();
			}
		}
		
		if (attritionFile){
			Jerboa.getOutputManager().writeln(attritionFileName, title + " (PopulationDefinition),", true);
			Jerboa.getOutputManager().writeln(attritionFileName, "Patients in patient file," + originalCount, true);
			Jerboa.getOutputManager().writeln(attritionFileName, "Patients with runin period (" + currentRunInPeriod + ")," + (originalCount - countRemovedRunInPeriod), true);
			Jerboa.getOutputManager().writeln(attritionFileName, "Patients with enough additional followup time (" + minimumDaysOfPatientTime + ")," + (originalCount - countRemovedRunInPeriod - countRemovedInitialTime), true);
			Jerboa.getOutputManager().writeln(attritionFileName, "Patients in study period (" + studyStart + "-" + studyEnd + ")," + populationPeriodCount, true);
			Jerboa.getOutputManager().writeln(attritionFileName, "Patients in age range (" + minAge + "-" + maxAge + ")," + ageRangeCount, true);
			Jerboa.getOutputManager().writeln(attritionFileName, " ,",true);
			Jerboa.getOutputManager().flush(attritionFileName);
		}

	}

	//GETTERS
	public int getCountBirthdate() {
		return countBirthdate;
	}
	
	public int getCountRemoved() {
		return countRemoved;
	}
	
	public int getOriginalCount() {
		return originalCount;
	}
	
	public long getTotalPatientTime() {
		return (long) stats.getSum();
	}

}
