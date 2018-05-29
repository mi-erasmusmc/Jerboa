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

package org.erasmusmc.jerboa.modifiers;

import java.util.ArrayList;
import java.util.List;

import org.erasmusmc.jerboa.Jerboa;
import org.erasmusmc.jerboa.config.DataDefinition;
import org.erasmusmc.jerboa.config.FilePaths;
import org.erasmusmc.jerboa.config.Parameters;
import org.erasmusmc.jerboa.dataClasses.Event;
import org.erasmusmc.jerboa.dataClasses.Measurement;
import org.erasmusmc.jerboa.dataClasses.Patient;
import org.erasmusmc.jerboa.engine.Modifier;
import org.erasmusmc.jerboa.utilities.DateUtilities;
import org.erasmusmc.jerboa.utilities.ExtendedMultiKey;
import org.erasmusmc.jerboa.utilities.Item;
import org.erasmusmc.jerboa.utilities.ItemList;
import org.erasmusmc.jerboa.utilities.Logging;
import org.erasmusmc.jerboa.utilities.MultiKeyBag;
import org.erasmusmc.jerboa.utilities.StringUtilities;
import org.erasmusmc.jerboa.utilities.TimeUtilities;
import org.erasmusmc.jerboa.utilities.Wildcard;
import org.erasmusmc.jerboa.utilities.stats.Stats;

/**
 * This module defines the cohort start date and the cohort end date of a patient based
 * on the date of a measurement before a certain type of event.
 * It takes the first or last of any of the measurement types before the event(s) or just the first or last
 * measurement if the patient does not have events of the specified types or there are no
 * event types specified.
 *
 * @author MM
 *
 */

public class MeasurementCohortDefinition2 extends Modifier {

	/**
	 * If true the cohort start and end as defined by a previous cohort definition modifier
	 * is used, otherwise population start and end are used.
	 */
	public boolean chainCohortDefinitions;

	/**
	 * The date of the first occurrence of any of the measurements in this list will be used as
	 * cohort start date (index date).
	 * Optionally, value can be added:
	 * Format:
	 *
	 * measurementTypes[;Value]
	 *
	 * MeasurementTypes = A comma separated list of measurement types referring to a similar measurement.
	 * Value            = Optional. The required value of the measurement.
	 */
	public List<String> measurementsOfInterest = new ArrayList<String>();

	/**
	 * If true the first measurement of interest is used, otherwise the last.
	 */
	public boolean useFirstMeasurement;

	/**
	 * If true the measurement is also searched for in the full history of the patient
	 * otherwise only in cohort time in case of chain otherwise in population time.
	 */
	public boolean allHistory;

	/**
	 * A list of the event types the measurement should be before.
	 */
	public List<String> beforeEvents = new ArrayList<String>();

	/**
	 * Patients with less cohort time than this value are excluded.
	 */
	public int minimumDaysOfCohortTime;

	/**
	 * If true an attrition filename will be created or appended on called Attrition.csv
	 */
	public boolean attritionFile;


	//counters
	private int countRemoved;                     // keeps track of the patients that are not in the cohort
	private int originalCount;                    // keeps track of records in the intermediate file
	private int includedCount;
	private MultiKeyBag patientTime;              // keeps track of the total patient time

	private ItemList measurementsOfInterestList = new ItemList(1);

	private String attritionFileName;   //used to save the attrition

	private Measurement firstMeasurement = null; // used to keep the first measurement
	private Measurement lastMeasurement = null; // used to keep the last measurement


	@Override
	public boolean init() {
		boolean initOK = true;

		patientTime = new MultiKeyBag();

		measurementsOfInterestList.parseParamList(measurementsOfInterest);

		if (measurementsOfInterestList.size() == 0) {
			Logging.add("No measurement types specified.", Logging.ERROR);
			initOK = false;
		}

		if (initOK && (!Jerboa.unitTest) && intermediateFiles) {
			initOK &= Jerboa.getOutputManager().addFile(this.intermediateFileName);
			if (initOK) {
				String header = "PatientID";
				header += "," + "Gender";
				header += "," + "Birthdate";
				header += "," + "Startdate";
				header += "," + "Enddate";
				header += "," + "PopulationStart";
				header += "," + "PopulationEnd";
				header += "," + "CohortStart";
				header += "," + "CohortEnd";
				Jerboa.getOutputManager().writeln(this.intermediateFileName, header, false);
			}
		}

		if (initOK && attritionFile && (!Jerboa.unitTest)) {
			attritionFileName = FilePaths.WORKFLOW_PATH+this.getParentModule()+"/"+
					Parameters.DATABASE_NAME+"_"+this.getParentModule()+
					"_"+TimeUtilities.TIME_STAMP+"_"+Parameters.VERSION+"_attrition.csv";
			Jerboa.getOutputManager().addFile(attritionFileName, 100);
		}

		// Initialize counters
		countRemoved = 0;                     // keeps track of the patients that are not in the cohort
		originalCount = 0;                    // keeps track of records in the intermediate file
		includedCount = 0;

		return initOK;
	}


	/**
	 * Sets the cohort start date and cohort end date based on the last occurence of one of the
	 * measurements of interest before the specified events.
	 * @param patient - the patient to be checked if it will be part of the cohort or not
	 * @return patient - the patient object with a defined cohort start date and cohort end date
	 */
	@Override
	public Patient process(Patient patient) {

		if (patient != null) {
			if (chainCohortDefinitions ? patient.isInCohort() : patient.isInPopulation()) {

				// Update patient counter
				if (!inPostProcessing) originalCount++;

				int start = -1;
				int end;

				if (chainCohortDefinitions) {
					start = patient.getCohortStartDate();
					end   = patient.getCohortEndDate();
				}
				else {
					start = patient.getPopulationStartDate();
					end   = patient.getPopulationEndDate();
				}

				int measurementBeforeDate = Integer.MAX_VALUE;
				for (Event event : patient.getEvents()) {
					if (beforeEvents.contains(event.getType())) {
						measurementBeforeDate = event.getDate();
						break;
					}
				}

				firstMeasurement = null;
				lastMeasurement = null;
				for (Measurement measurement : patient.getMeasurements()) {
					if ((measurement.getDate() < measurementBeforeDate) && (allHistory ? measurement.isInPeriod(-1,end) : measurement.isInPeriod(start,end))) {
						//Check for measurement of interest
						for (Item item : measurementsOfInterestList) {
							if (measurement.inList(item.getLookup()) && ((item.getParameters().size() < 2) || (item.getParameters().get(1).contains(measurement.getValue())))) {
								if (useFirstMeasurement && (firstMeasurement == null)) {
									firstMeasurement = measurement;
									break;
								}
								lastMeasurement = measurement;
							}
						}
						if (useFirstMeasurement && (firstMeasurement != null)) {
							break;
						}
					}
				}

				Measurement startMeasurement = useFirstMeasurement ? firstMeasurement : lastMeasurement;
				if (startMeasurement != null) {
					start = startMeasurement.getDate();
					if ((end - start) >= minimumDaysOfCohortTime) {
						patient.setCohortStartDate(start);
						patient.setInCohort(true);
						if (!inPostProcessing) includedCount++;

						// update patient time
						if (intermediateStats)
							patientTime.add(new ExtendedMultiKey("ALL", patient.cohortEndDate - patient.cohortStartDate));
					}
					else {
						patient.setInCohort(false);
						if (!inPostProcessing) countRemoved++;
					}
				}
				else {
					patient.setInCohort(false);
					if (!inPostProcessing) countRemoved++;
				}
			}
			else {
				patient.setInCohort(false);
				if (!inPostProcessing) countRemoved++;
			}
		}

		if ((!Jerboa.unitTest) && intermediateFiles) {
			String record = patient.getPatientID();
			record += "," + patient.getGender();
			record += "," + DateUtilities.daysToDate(patient.getBirthDate());
			record += "," + DateUtilities.daysToDate(patient.startDate);
			record += "," + DateUtilities.daysToDate(patient.endDate);
			record += "," + (patient.isInPopulation() ? DateUtilities.daysToDate(patient.getPopulationStartDate()) : "");
			record += "," + (patient.isInPopulation() ? DateUtilities.daysToDate(patient.getPopulationEndDate()) : "");
			record += "," + (patient.isInCohort() ? DateUtilities.daysToDate(patient.getCohortStartDate()) : "");
			record += "," + (patient.isInCohort() ? DateUtilities.daysToDate(patient.getCohortEndDate()) : "");
			Jerboa.getOutputManager().writeln(this.intermediateFileName, record, true);
		}

		updateResultSet(patient);

		return patient;
	}

	/**
	 * Will update the results of this patient in the result set.
	 * @param patient - the patient to be processed
	 */
	private void updateResultSet(Patient patient) {
		if (!Jerboa.unitTest) {
			if (patient.inCohort) {
				if (Jerboa.getResultSet().isOnlyInCohort()) {
					Jerboa.getResultSet().add(patient.getPatientID(),"PopulationStart", (DateUtilities.daysToDate(patient.getPopulationStartDate())));
					Jerboa.getResultSet().add(patient.getPatientID(),"PopulationEnd", (DateUtilities.daysToDate(patient.getPopulationEndDate())));
				}
				Jerboa.getResultSet().add(patient.getPatientID(),"CohortStart", (DateUtilities.daysToDate(patient.getCohortStartDate())));
				Jerboa.getResultSet().add(patient.getPatientID(),"CohortEnd", (DateUtilities.daysToDate(patient.getCohortEndDate())));
				Jerboa.getResultSet().add(patient.getPatientID(),"CohortTime", String.valueOf(patient.getCohortTime()));
			}
			else {
				if (!Jerboa.getResultSet().isOnlyInCohort()) {
					Jerboa.getResultSet().add(patient.getPatientID(),"CohortStart", Jerboa.getResultSet().getMissingValue());
					Jerboa.getResultSet().add(patient.getPatientID(),"CohortEnd", Jerboa.getResultSet().getMissingValue());
					Jerboa.getResultSet().add(patient.getPatientID(),"CohortTime", "0");
				}
			}
		}
	}

	@Override
	public void outputResults() {

		flushRemainingData();

		if (countRemoved == Stats.nbPatients) {
			Logging.add("There are no patients left in the cohort.");
		}
		else if (intermediateStats) {
			Logging.add(this.title);
			Logging.add("Population size: " + originalCount);
			Logging.add("Cohort size: "+ (originalCount - countRemoved));
			Logging.add("Total patient time in cohort: " +StringUtilities.format((float)patientTime.getCount(new ExtendedMultiKey("ALL", Wildcard.INTEGER()))/(float)DateUtilities.daysPerYear)+" years ");
			Logging.addNewLine();
		}

		if ((!Jerboa.unitTest) && attritionFile) {
			Jerboa.getOutputManager().writeln(attritionFileName, title + " (MeasurementCohortDefinition2),", true);
			Jerboa.getOutputManager().writeln(attritionFileName, "Included patients," + includedCount,true);
			Jerboa.getOutputManager().writeln(attritionFileName, " ,",true);
			Jerboa.getOutputManager().flush(attritionFileName);
		}

		if ((!Jerboa.unitTest) && intermediateFiles) {
			Jerboa.getOutputManager().closeFile(intermediateFileName);
		}
	}

	@Override
	public void setNeededFiles() {
		setRequiredFile(DataDefinition.PATIENTS_FILE);
		setRequiredFile(DataDefinition.MEASUREMENTS_FILE);
		if (beforeEvents.size()>0)
			setRequiredFile(DataDefinition.EVENTS_FILE);
	}

	@Override
	public void setNeededExtendedColumns() { /* NOTHING TO ADD */ }


	@Override
	public void setNeededNumericColumns() {
		// TODO Auto-generated method stub

	}



	/* Extra methods for unit test */

	public Measurement getFirstMeasurement() {
		return firstMeasurement;
	}


	public Measurement getLastMeasurement() {
		return lastMeasurement;
	}

}
