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
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.erasmusmc.jerboa.Jerboa;
import org.erasmusmc.jerboa.config.DataDefinition;
import org.erasmusmc.jerboa.dataClasses.Measurement;
import org.erasmusmc.jerboa.dataClasses.Patient;
import org.erasmusmc.jerboa.engine.Modifier;
import org.erasmusmc.jerboa.utilities.DateUtilities;
import org.erasmusmc.jerboa.utilities.Logging;

/**
 * This modifier searches for the first and last measurements of the specified types
 * in population or cohort time and creates new measurements for them with the specified types.
 * If the first and last measurement of a type are the same then only the first is
 * created.
 * @author bmosseveld
 */
public class MeasurementFirstAndLast extends Modifier {

	/**
	 * The window for the first and last measurement.
	 * Format:
	 *
	 *   <start reference>;<start offset>;<end reference>;<end offset>
	 *
	 * where
	 *
	 *   start reference = PatientStart, PatientEnd, PopulationStart, PopulationEnd, CohortStart, CohortEnd, or a date as yyyymmdd
	 *   start offset    = The offset relative to <start reference>. When empty it is beginning of time.
	 *   end reference   = PatientStart, PatientEnd, PopulationStart, PopulationEnd, CohortStart, CohortEnd, or a date as yyyymmdd
	 *   end offset      = The offset relative to <end reference>. When empty it is end of time.
	 *
	 * When all parameters are empty or window is the empty string it is unlimited.
	 * The start of the window is inclusive and the end of the window is exclusive.
	 */
	public String window;

	/**
	 * The list of measurements to search for and the new measurements to create.
	 * Format:
	 *
	 *   <measurement type>;<first measurement type>;<last measurement type>
	 */
	public List<String> measurements = new ArrayList<String>();


	// Reference constants
	private static final int REFERENCE_PATIENT_START    = 0;
	private static final int REFERENCE_PATIENT_END      = 1;
	private static final int REFERENCE_POPULATION_START = 2;
	private static final int REFERENCE_POPULATION_END   = 3;
	private static final int REFERENCE_COHORT_START     = 4;
	private static final int REFERENCE_COHORT_END       = 5;
	private static final int REFERENCE_DATE             = 6;

	// Window variables
	private Integer startReference       = -1;
	private Integer startReferenceOffset = null;
	private Integer startReferenceDate   = -1;
	private Integer endReference         = -1;
	private Integer endReferenceOffset   = null;
	private Integer endReferenceDate     = -1;

	// Variables For storing the definitions
	private Set<String> measurementSet;
	private Map<String, String> firstMeasurementType;
	private Map<String, String> lastMeasurementType;

	// Intermediate file name
	private String fileName;

	// Result set for unit test
	private Map<String, Measurement> unitTestFirst;
	private Map<String, Measurement> unitTestLast;


	@Override
	public boolean init() {
		boolean initOK = true;

		initOK = interpretWindow();

		measurementSet = new HashSet<String>();
		firstMeasurementType = new HashMap<String, String>();
		lastMeasurementType = new HashMap<String, String>();

		for (String measurementDefintion : measurements) {
			String[] measurementDefintionSplit = measurementDefintion.split(";");
			if (measurementDefintionSplit.length > 2) {
				if (measurementDefintionSplit.length > 3) {
					Logging.add("WARNIG: Extra parameters ignored in measurement definition: " + measurementDefintion);
				}
				String measurementType = measurementDefintionSplit[0].trim().toUpperCase();
				String firstType = measurementDefintionSplit[1].trim().toUpperCase();
				String lastType = measurementDefintionSplit[2].trim().toUpperCase();
				if (measurementType.equals("")) {
					Logging.add("ERROR: No measurement type speicified in measurement definition: " + measurementDefintion);
					initOK = false;
				}
				else if (firstType.equals("")) {
					Logging.add("ERROR: No first measurement type speicified in measurement definition: " + measurementDefintion);
					initOK = false;
				}
				else if (lastType.equals("")) {
					Logging.add("ERROR: No last measurement type speicified in measurement definition: " + measurementDefintion);
					initOK = false;
				}
				else if (measurementSet.add(measurementType)) {
					firstMeasurementType.put(measurementType, firstType);
					lastMeasurementType.put(measurementType, lastType);
				}
				else {
					Logging.add("ERROR: Duplicate measurement type speicified in measurement definition: " + measurementDefintion);
					initOK = false;
				}
			}
			else {
				Logging.add("ERROR: Not enough parameters (3) specified in measurement definition: " + measurementDefintion);
				initOK = false;
			}
		}

		if (initOK && (!Jerboa.unitTest) && intermediateFiles) {
			fileName = this.intermediateFileName;
			if (Jerboa.getOutputManager().addFile(fileName)) {
				String header = "SubsetID";
				header += "," + "PatientID";
				header += "," + "Date";
				header += "," + "MeasurementType";
				header += "," + "Value";
				header += "," + "First/Last";
				Jerboa.getOutputManager().writeln(fileName, header, false);
			}
			else {
				initOK = false;
			}
		}

		return initOK;
	}


	@Override
	public Patient process(Patient patient) {

		if (Jerboa.unitTest) {
			unitTestFirst = new HashMap<String, Measurement>();
			unitTestLast = new HashMap<String, Measurement>();
		}

		int periodStart = getWindowBoundary(patient, startReference, startReferenceOffset, startReferenceDate);
		int periodEnd   = getWindowBoundary(patient, endReference, endReferenceOffset, endReferenceDate);

		Map<String, Measurement> firstMeasurements = new HashMap<String, Measurement>();
		Map<String, Measurement> lastMeasurements = new HashMap<String, Measurement>();

		if (measurementSet.size() > 0) {
			for (Measurement measurement : patient.getMeasurements()) {
				if (measurementSet.contains(measurement.getType()) && measurement.isInPeriod(periodStart, periodEnd, true, false)) {
					if ((firstMeasurements.get(measurement.getType()) == null) || (measurement.getDate() < firstMeasurements.get(measurement.getType()).getDate())) {
						firstMeasurements.put(measurement.getType(), measurement);
					}
					if ((lastMeasurements.get(measurement.getType()) == null) || (measurement.getDate() > lastMeasurements.get(measurement.getType()).getDate())) {
						lastMeasurements.put(measurement.getType(), measurement);
					}
				}
			}

			for (String measurementType : firstMeasurements.keySet()) {
				Measurement firstMeasurement = new Measurement(firstMeasurements.get(measurementType));
				firstMeasurement.setType(firstMeasurementType.get(measurementType));
				patient.getMeasurements().add(firstMeasurement);

				if ((!Jerboa.unitTest) && intermediateFiles) {
					String record = patient.subset;
					record += "," + patient.ID;
					record += "," + DateUtilities.daysToDate(firstMeasurement.getDate());
					record += "," + firstMeasurement.getType();
					record += "," + firstMeasurement.getValue();
					record += "," + "First";
					Jerboa.getOutputManager().writeln(fileName, record, true);
				}

				if (Jerboa.unitTest) {
					unitTestFirst.put(measurementType, firstMeasurement);
				}

				if (lastMeasurements.get(measurementType) != firstMeasurements.get(measurementType)) {
					Measurement lastMeasurement = new Measurement(lastMeasurements.get(measurementType));
					lastMeasurement.setType(lastMeasurementType.get(measurementType));
					patient.getMeasurements().add(lastMeasurement);

					if ((!Jerboa.unitTest) && intermediateFiles) {
						String record = patient.subset;
						record += "," + patient.ID;
						record += "," + DateUtilities.daysToDate(lastMeasurement.getDate());
						record += "," + lastMeasurement.getType();
						record += "," + lastMeasurement.getValue();
						record += "," + "Last";
						Jerboa.getOutputManager().writeln(fileName, record, true);
					}

					if (Jerboa.unitTest) {
						unitTestLast.put(measurementType, lastMeasurement);
					}
				}
			}

			Collections.sort(patient.getMeasurements());
		}

		return patient;
	}


	@Override
	public void outputResults() {
		if ((!Jerboa.unitTest) && intermediateFiles) {
			Jerboa.getOutputManager().closeFile(fileName);
		}
	}


	@Override
	public void setNeededFiles() {
		setRequiredFile(DataDefinition.PATIENTS_FILE);
		if (measurements.size() > 0)
			setRequiredFile(DataDefinition.MEASUREMENTS_FILE);
	}


	@Override
	public void setNeededExtendedColumns() { }


	@Override
	public void setNeededNumericColumns() { }


	private boolean interpretWindow() {
		boolean result = true;
		String start       = "";
		String startOffset = "";
		String end         = "";
		String endOffset   = "";

		String[] windowSplit = window.split(";");
		if (windowSplit.length > 0) start       = windowSplit[0].trim().toUpperCase();
		if (windowSplit.length > 1) startOffset = windowSplit[1].trim().toUpperCase();
		if (windowSplit.length > 2) end         = windowSplit[2].trim().toUpperCase();
		if (windowSplit.length > 3) endOffset   = windowSplit[3].trim().toUpperCase();

		if (startOffset.equals("")) {
			startReference       = REFERENCE_DATE;
			startReferenceOffset = 0;
			startReferenceDate   = 0;
		}
		else {
			if      (start.equals("PATIENTSTART"))    startReference = REFERENCE_PATIENT_START;
			else if (start.equals("PATIENTEND"))      startReference = REFERENCE_PATIENT_END;
			else if (start.equals("POPULATIONSTART")) startReference = REFERENCE_POPULATION_START;
			else if (start.equals("POPULATIONEND"))   startReference = REFERENCE_POPULATION_END;
			else if (start.equals("COHORTSTART"))     startReference = REFERENCE_COHORT_START;
			else if (start.equals("COHORTEND"))       startReference = REFERENCE_COHORT_END;
			else {
				startReference = REFERENCE_DATE;
				startReferenceDate = DateUtilities.dateToDays(start, DateUtilities.DATE_ON_YYYYMMDD);
				if (startReferenceDate == null) {
					Logging.add("ERROR: Illegal date (" + start + ") as window start reference!", Logging.ERROR);
					result = false;
				}
			}
			try {
				startReferenceOffset = Integer.parseInt(startOffset);
			}
			catch (NumberFormatException e) {
				Logging.add("ERROR: Illegal window start offset (" + startOffset + ")!", Logging.ERROR);
				result = false;
			}
		}

		if (endOffset.equals("")) {
			endReference       = REFERENCE_DATE;
			endReferenceOffset = 0;
			endReferenceDate   = Integer.MAX_VALUE;
		}
		else {
			if      (end.equals("PATIENTSTART"))    endReference = REFERENCE_PATIENT_START;
			else if (end.equals("PATIENTEND"))      endReference = REFERENCE_PATIENT_END;
			else if (end.equals("POPULATIONSTART")) endReference = REFERENCE_POPULATION_START;
			else if (end.equals("POPULATIONEND"))   endReference = REFERENCE_POPULATION_END;
			else if (end.equals("COHORTSTART"))     endReference = REFERENCE_COHORT_START;
			else if (end.equals("COHORTEND"))       endReference = REFERENCE_COHORT_END;
			else {
				endReference = REFERENCE_DATE;
				endReferenceDate = DateUtilities.dateToDays(end, DateUtilities.DATE_ON_YYYYMMDD);
				if (endReferenceDate == null) {
					Logging.add("ERROR: Illegal date (" + end + ") as window end reference!", Logging.ERROR);
					result = false;
				}
			}
			try {
				endReferenceOffset = Integer.parseInt(endOffset);
			}
			catch (NumberFormatException e) {
				Logging.add("ERROR: Illegal window end offset (" + endOffset + ")!", Logging.ERROR);
				result = false;
			}
		}

		return result;
	}


	private int getWindowBoundary(Patient patient, int reference, int offset, int date) {
		int boundary = 0;

		switch (reference) {
			case REFERENCE_PATIENT_START:
				boundary = patient.startDate;
				break;
			case REFERENCE_PATIENT_END:
				boundary = patient.endDate;
				break;
			case REFERENCE_POPULATION_START:
				boundary = patient.getPopulationStartDate();
				break;
			case REFERENCE_POPULATION_END:
				boundary = patient.getPopulationEndDate();
				break;
			case REFERENCE_COHORT_START:
				boundary = patient.getCohortStartDate();
				break;
			case REFERENCE_COHORT_END:
				boundary = patient.getCohortEndDate();
				break;
			default:
				boundary = date;
				break;
		}
		boundary += offset;

		return boundary;
	}


	// Getters for unit test

	public Map<String, Measurement> getFirst() {
		return unitTestFirst;
	}


	public Map<String, Measurement> getLast() {
		return unitTestLast;
	}

}
