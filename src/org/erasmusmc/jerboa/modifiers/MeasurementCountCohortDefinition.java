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

import java.security.InvalidParameterException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.erasmusmc.jerboa.Jerboa;
import org.erasmusmc.jerboa.config.DataDefinition;
import org.erasmusmc.jerboa.config.FilePaths;
import org.erasmusmc.jerboa.config.Parameters;
import org.erasmusmc.jerboa.dataClasses.Measurement;
import org.erasmusmc.jerboa.dataClasses.Patient;
import org.erasmusmc.jerboa.engine.Modifier;
import org.erasmusmc.jerboa.utilities.DateUtilities;
import org.erasmusmc.jerboa.utilities.ExtendedMultiKey;
import org.erasmusmc.jerboa.utilities.Logging;
import org.erasmusmc.jerboa.utilities.MultiKeyBag;
import org.erasmusmc.jerboa.utilities.StringUtilities;
import org.erasmusmc.jerboa.utilities.TimeUtilities;
import org.erasmusmc.jerboa.utilities.Wildcard;


/**
 * This module keeps patient in the cohort if they have the specified
 * number of the specified measurements in the specified period.
 *
 * @author bmosseveld
 */
public class MeasurementCountCohortDefinition extends Modifier {

	/**
	 * If true the cohort start and end as defined by a previous cohort definition modifier
	 * is used, otherwise population start and end are used.
	 */
	public boolean chainCohortDefinitions;

	/**
	 * The list of measurements the patient should not have in the specified periods.
	 * If the patient does not fulfill one of the definitions he is removed from the cohort.
	 * Format:
	 *
	 *   <measurement type>;<min count>;<max count>;<window start reference>;<window start>;<window end reference>;<window end>
	 *
	 * where
	 *
	 *   measurementType            The type of measurement
	 *   min count                  The minimum number of measurements in the specified period
	 *                              Default = 0
	 *   max count                  The maximum number of measurements in the specified period
	 *                              Default = max integer
	 *   window start reference     The reference point for window start.
	 *
	 *                                PopulationStart
	 *                                PopulationEnd
	 *                                CohortStart
	 *                                CohortEnd
	 *                                A date as yyyymmdd
	 *
	 *   window start               The number of days relative to <window start reference>.
	 *                              When empty it may not occur before any time < window end.
	 *   window end reference       The reference point for window start.
	 *
	 *                                PopulationStart
	 *                                PopulationEnd
	 *                                CohortStart
	 *                                CohortEnd
	 *                                A date as yyyymmdd
	 *
	 *   window end                 The number of days relative to <window end reference>.
	 *                              When empty it may not occur any time >= window start.
	 *
	 * Examples:
	 *
	 *   BMI;0;0;PopulationStart;;PopulationStart;     BMI may never occur for the patient to be in the cohort.
	 *   BMI;0;0;CohortStart;;CohortEnd;0              If BMI < CohortEnd the patient is removed from the cohort.
	 *   BMI;0;0;CohortStart;365;CohortEnd;-365        If (CohortStart + 365 days) <= BMI < (CohortStart - 365 days)
	 *                                                 the patient is removed from the cohort.
	 *   BMI;1;3;CohortStart;0;CohortEnd;0             If 1 to 3 BMI measurments >= CohortStart and < CohortEnd the patient is in the cohort.
	 */
	public List<String> measurementsOfInterest = new ArrayList<String>();

	/**
	 * If true an attrition filename will be created or appended on called Attrition.csv
	 */
	public boolean attritionFile;



	// Measurement definition
	private Map<String, List<MeasurementExclusion>> measurementExclusions;
	private Map<MeasurementExclusion, Integer> measurementExclusionCounts;

	// Patient time bag
	private MultiKeyBag patientTime;

	// Counters
	private int originalCount;
	private int countRemoved;

	// Attrition table filename
	private String attritionFileName;


	@Override
	public boolean init() {
		boolean initOK = true;

		initOK = parseMeasurements();

		if ((!Jerboa.unitTest) && intermediateFiles) {
			String header = "SubsetID";
			header += "," + "PatientID";
			header += "," + "Birthdate";
			header += "," + "Gender";
			header += "," + "Startdate";
			header += "," + "Enddate";
			header += "," + "PopulationStart";
			header += "," + "PopulationEnd";
			header += "," + "CohortStart";
			header += "," + "CohortEnd";
			initOK &= Jerboa.getOutputManager().addFile(this.intermediateFileName);
			Jerboa.getOutputManager().writeln(this.intermediateFileName, header, false);
		}

		if (initOK && attritionFile && (!Jerboa.unitTest)) {
			attritionFileName = FilePaths.WORKFLOW_PATH+this.getParentModule()+"/"+
					Parameters.DATABASE_NAME+"_"+this.getParentModule()+
					"_"+TimeUtilities.TIME_STAMP+"_"+Parameters.VERSION+"_attrition.csv";
			Jerboa.getOutputManager().addFile(attritionFileName, 100);
		}

		patientTime = new MultiKeyBag();

		// Initialize counters
		originalCount = 0;
		countRemoved = 0;

		return initOK;
	}


	@Override
	public Patient process(Patient patient) {

		if (patient != null && (chainCohortDefinitions ? patient.isInCohort() : patient.isInPopulation())) {
			// Initialize measurement counters
			for (MeasurementExclusion measurementExclusion : measurementExclusionCounts.keySet()) {
				measurementExclusionCounts.put(measurementExclusion, 0);
			}

			if (!inPostProcessing) originalCount++;
			boolean remove = false;
			countLoop:
			for (Measurement measurement : patient.getMeasurements()) {
				if (measurementExclusions.containsKey(measurement.getType())) {
					for (MeasurementExclusion measurementExclusion : measurementExclusions.get(measurement.getType())) {
						if (measurementExclusion.inWindow(patient,measurement)) {
							measurementExclusionCounts.put(measurementExclusion, measurementExclusionCounts.get(measurementExclusion) + 1);
							if (measurementExclusionCounts.get(measurementExclusion) > measurementExclusion.getMaximumCount()) {
								remove = true;
								break countLoop;
							}
						}
					}
				}
			}

			if (!remove) {
				checkLoop:
				for (String measurementType : measurementExclusions.keySet()) {
					for (MeasurementExclusion measurementExclusion : measurementExclusions.get(measurementType)) {
						if (
								(measurementExclusionCounts.get(measurementExclusion) < measurementExclusion.getMinimumCount()) ||
								(measurementExclusionCounts.get(measurementExclusion) > measurementExclusion.getMaximumCount())
							) {
							remove = true;
							break checkLoop;
						}
					}
				}
			}

			if (remove) {
				patient.setInCohort(false);
				if (!inPostProcessing) countRemoved++;
			}
			else {
				// Update patient time
				if (intermediateStats) {
					patientTime.add(new ExtendedMultiKey("ALL", patient.cohortEndDate - patient.cohortStartDate));
				}
			}

			// Write patient to intermediate file
			if ((!Jerboa.unitTest) && intermediateFiles) {
				String record = patient.getPatientID();
				record += "," + patient.getGender();
				record += "," + DateUtilities.daysToDate(patient.getBirthDate());
				record += "," + DateUtilities.daysToDate(patient.startDate);
				record += "," + DateUtilities.daysToDate(patient.endDate);
				record += "," + (patient.isInPopulation() ? DateUtilities.daysToDate(patient.getPopulationStartDate()) : "");
				record += "," + (patient.isInPopulation() ? DateUtilities.daysToDate(patient.getPopulationEndDate()) : "");
				record += "," + (patient.isInCohort() ? DateUtilities.daysToDate(patient.getBirthDate()) : "");
				record += "," + (patient.isInCohort() ? DateUtilities.daysToDate(patient.getBirthDate()) : "");
				Jerboa.getOutputManager().writeln(this.intermediateFileName, record, true);
			}
		}

		return patient;
	}


	@Override
	public void outputResults() {

		flushRemainingData();

		if ((!Jerboa.unitTest) && intermediateFiles) {
			Jerboa.getOutputManager().closeFile(intermediateFileName);
		}

		Logging.addNewLine();
		Logging.add("No measurement cohort definition modifier results:");
		Logging.add("--------------------------------------------------");
		Logging.add("Original cohort size        : " + originalCount);
		Logging.add("New cohort size             : "+ (originalCount - countRemoved));
		Logging.add("Total patient time in cohort: " +StringUtilities.format((float)patientTime.getHistogramStats(new ExtendedMultiKey("ALL", Wildcard.INTEGER())).getSum()/(float)DateUtilities.daysPerYear)+" years ");
		Logging.addNewLine();

		if ((!Jerboa.unitTest) && attritionFile) {
			Jerboa.getOutputManager().writeln(attritionFileName, title + " (MeasurementCountCohortDefinition),", true);
			Jerboa.getOutputManager().writeln(attritionFileName, "Included patients," + (originalCount - countRemoved),true);
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
	}


	@Override
	public void setNeededExtendedColumns() {}


	@Override
	public void setNeededNumericColumns() {}


	private boolean parseMeasurements() {
		boolean measurementsOK = true;

		measurementExclusions = new HashMap<String, List<MeasurementExclusion>>();
		measurementExclusionCounts = new HashMap<MeasurementExclusion, Integer>();

		for (String exclusionDefinition : measurementsOfInterest) {
			MeasurementExclusion measurementExclusion = new MeasurementExclusion(exclusionDefinition);
			List<MeasurementExclusion> measurementExclusionsList = measurementExclusions.get(measurementExclusion.getMeasurementType());
			if (measurementExclusionsList == null) {
				measurementExclusionsList = new ArrayList<MeasurementExclusion>();
				measurementExclusions.put(measurementExclusion.getMeasurementType(), measurementExclusionsList);
			}
			measurementExclusionsList.add(measurementExclusion);
			measurementExclusionCounts.put(measurementExclusion, 0);
			measurementsOK &= measurementExclusion.ok;
		}

		return measurementsOK;
	}


	private class MeasurementExclusion {
		//Constants
		public final static byte REFERENCE_POPULATION_START = 0;
		public final static byte REFERENCE_POPULATION_END   = 1;
		public final static byte REFERENCE_COHORT_START     = 2;
		public final static byte REFERENCE_COHORT_END       = 3;
		public final static byte REFERENCE_DATE             = 4;

		public boolean ok = true;
		public String measurementType = "";
		public int minimumCount = 0;
		public int maximumCount = Integer.MAX_VALUE;
		public byte startReference = 0;
		public int startReferenceDate = -1;
		public Integer startReferenceOffset = null;
		public byte endReference = 0;
		public int endReferenceDate = -1;
		public Integer endReferenceOffset = null;


		public MeasurementExclusion(String exclusionDefinition) {
			String[] exclusionDefinitionSplit = exclusionDefinition.toUpperCase().split(";");
			if (exclusionDefinitionSplit.length > 0) {

				// Measurement type
				measurementType = exclusionDefinitionSplit[0];

				// Minimum count
				if (exclusionDefinitionSplit.length > 1) {
					int count;
					try {
						if (exclusionDefinitionSplit[1].equals("")) {
							count = 0;
						}
						else {
							count = Integer.parseInt(exclusionDefinitionSplit[1]);
						}
						minimumCount = count;
					}
					catch (NumberFormatException e) {
						Logging.add("Error not an integer in window start of measurement definition " + exclusionDefinition);
						ok = false;
					}
				}

				// Minimum count
				if (exclusionDefinitionSplit.length > 2) {
					int count;
					try {
						if (exclusionDefinitionSplit[2].equals("")) {
							count = Integer.MAX_VALUE;
						}
						else {
							count = Integer.parseInt(exclusionDefinitionSplit[2]);
						}
						maximumCount = count;
					}
					catch (NumberFormatException e) {
						Logging.add("Error not an integer in window start of measurement definition " + exclusionDefinition);
						ok = false;
					}
				}

				// Window start reference
				if (exclusionDefinitionSplit.length > 3) {
					if (exclusionDefinitionSplit[3].equals("POPULATIONSTART")) startReference = REFERENCE_POPULATION_START;
					else if (exclusionDefinitionSplit[3].equals("POPULATIONEND")) startReference = REFERENCE_POPULATION_END;
					else if (exclusionDefinitionSplit[3].equals("COHORTSTART")) startReference = REFERENCE_COHORT_START;
					else if (exclusionDefinitionSplit[3].equals("COHORTEND")) startReference = REFERENCE_COHORT_END;
					else {
						Integer startDate = DateUtilities.dateToDays(exclusionDefinitionSplit[3], DateUtilities.DATE_ON_YYYYMMDD);
						if (startDate != null) {
							startReference = REFERENCE_DATE;
							startReferenceDate = startDate;
						}
						else {
							Logging.add("Error in window start reference of measurement definition " + exclusionDefinition);
							ok = false;
						}
					}
				}

				// Window start
				if (exclusionDefinitionSplit.length > 4) {
					int startOffset;
					try {
						if (exclusionDefinitionSplit[4].equals("")) {
							startOffset = Integer.MIN_VALUE;
						}
						else {
							startOffset = Integer.parseInt(exclusionDefinitionSplit[4]);
						}
						startReferenceOffset = startOffset;
					}
					catch (NumberFormatException e) {
						Logging.add("Error not an integer in window start of measurement definition " + exclusionDefinition);
						ok = false;
					}
				}

				// Window end reference
				if (exclusionDefinitionSplit.length > 5) {
					if (exclusionDefinitionSplit[5].equals("POPULATIONSTART")) endReference = REFERENCE_POPULATION_START;
					else if (exclusionDefinitionSplit[5].equals("POPULATIONEND")) endReference = REFERENCE_POPULATION_END;
					else if (exclusionDefinitionSplit[5].equals("COHORTSTART")) endReference = REFERENCE_COHORT_START;
					else if (exclusionDefinitionSplit[5].equals("COHORTEND")) endReference = REFERENCE_COHORT_END;
					else {
						Integer endDate = DateUtilities.dateToDays(exclusionDefinitionSplit[5], DateUtilities.DATE_ON_YYYYMMDD);
						if (endDate != null) {
							endReference = REFERENCE_DATE;
							endReferenceDate = endDate;
						}
						else {
							Logging.add("Error in window end reference of measurement definition " + exclusionDefinition);
							ok = false;
						}
					}
				}

				// Window end
				if (exclusionDefinitionSplit.length > 6) {
					int endOffset;
					try {
						if (exclusionDefinitionSplit[6].equals("")) {
							endOffset = Integer.MAX_VALUE;
						}
						else {
							endOffset = Integer.parseInt(exclusionDefinitionSplit[6]);
						}
						endReferenceOffset = endOffset;
					}
					catch (NumberFormatException e) {
						Logging.add("Error not an integer in window end of measurement definition " + exclusionDefinition);
						ok = false;
					}
				}
			}
			else {
				Logging.add("Incomplete measurement definition " + exclusionDefinition);
				ok = false;
			}
		}


		public String getMeasurementType() {
			return measurementType;
		}


		public int getMinimumCount() {
			return minimumCount;
		}


		public int getMaximumCount() {
			return maximumCount;
		}


		public boolean inWindow(Patient patient, Measurement measurement) {
			if (measurement.getType().equals(measurementType)) {
				int startDate = getReferenceDate(patient, startReference, startReferenceDate, startReferenceOffset, Integer.MIN_VALUE);
				int endDate = getReferenceDate(patient, endReference, endReferenceDate, endReferenceOffset, Integer.MAX_VALUE);
				return (startDate < endDate ? measurement.isInPeriod(startDate, endDate, true, false) : false);
			}
			else {
				return false;
			}
		}


		private Integer getReferenceDate(Patient patient, byte reference, Integer referenceDate, Integer referenceOffset, int limit) {

			if (reference == REFERENCE_POPULATION_START)      referenceDate = referenceOffset == null ? limit : patient.getPopulationStartDate() + referenceOffset;
			else if (reference == REFERENCE_POPULATION_END)   referenceDate = referenceOffset == null ? limit : patient.getPopulationEndDate() + referenceOffset;
			else if (reference == REFERENCE_COHORT_START)     referenceDate = referenceOffset == null ? limit : patient.getCohortStartDate() + referenceOffset;
			else if (reference == REFERENCE_COHORT_END)       referenceDate = referenceOffset == null ? limit : patient.getCohortEndDate() + referenceOffset;
			else if (reference == REFERENCE_DATE)             referenceDate = referenceOffset == null ? limit : referenceDate + referenceOffset;
			else throw new InvalidParameterException("Invallid measurment definition in NoMeasurementCohortDefinition!");

			return referenceDate;
		}


		public String toString() {
			String result = measurementType;
			result += " " + Integer.toString(minimumCount);
			result += "-" + Integer.toString(maximumCount);
			result += " " +
						(startReference == REFERENCE_POPULATION_START ? "PopulationStart" :
						(startReference == REFERENCE_POPULATION_END ? "PopulationEnd" :
						(startReference == REFERENCE_COHORT_START ? "CohortStart" :
						(startReference == REFERENCE_COHORT_END ? "CohortEnd" :
						(startReference == REFERENCE_DATE ? DateUtilities.daysToDate(startReferenceDate) :
						"ERROR")))));
			result += (startReferenceOffset == null ? "---" : (startReferenceOffset < 0 ? startReferenceOffset : "+" + startReferenceOffset));
			result += " - " +
					(endReference == REFERENCE_POPULATION_START ? "PopulationStart" :
					(endReference == REFERENCE_POPULATION_END ? "PopulationEnd" :
					(endReference == REFERENCE_COHORT_START ? "CohortStart" :
					(endReference == REFERENCE_COHORT_END ? "CohortEnd" :
					(endReference == REFERENCE_DATE ? DateUtilities.daysToDate(endReferenceDate) :
					"ERROR")))));
			result += (endReferenceOffset == null ? "---" : (endReferenceOffset < 0 ? endReferenceOffset : "+" + endReferenceOffset));
			return result;
		}
	}
}
