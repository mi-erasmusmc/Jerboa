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

package org.erasmusmc.jerboa.utilities;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.erasmusmc.jerboa.dataClasses.Event;
import org.erasmusmc.jerboa.dataClasses.Measurement;
import org.erasmusmc.jerboa.dataClasses.Patient;
import org.erasmusmc.jerboa.dataClasses.Prescription;
import org.erasmusmc.jerboa.utilities.stats.formulas.GarvanRiskFactor;




/**
 * This class can be used to define covariates and to compute their values.
 * The covariates can be defined by creating an instance while passing the list of covariate
 * definitions to the constructor:
 *
 *   Covariates(List<String> covariateDefinitions)
 *
 * Before retrieving the values of the covraiates an instance of the class PatientCovariateInfo
 * should be created by calling the constructor:
 *
 *   PatientCovariateInfo(Patient patient, Covariates covariates)
 *
 * where the parameter covariates is the instance created with the previous call.
 * After that the values can be retrieved with the method:
 *
 *   getCovariateValues(Patient patient, int indexDate, PatientCovariateInfo covariateInfo)
 *
 * The corresponding header can be retrieved with the method:
 *
 *   getHeader()
 *
 * This list of covariate definitions may contain three types of covariates:
 *
 * ATC covariates:
 *
 * ATC codes to assess as covariates for the cases and controls with their
 * windows during which exposure to drugs with the specified ATC's will be measured.
 * The format is:
 *
 * ATC;atc1, ... ,atcN;label;window start reference;window start;window end reference;window end;value reference;value reference offset;value
 *
 * where
 *
 *   atc1, ... ,atcN           The ATC codes of the drug separated by comma's. Higher level ATC codes are allowed.
 *                             When empty all ATC codes are accepted.
 *   label                     The label that is used in the header of the output file.
 *                             When empty a standard label is generated.
 *   window start reference    The reference point for window start. Possible values are:
 *
 *                               Index                : Index date (time of event)
 *                               StartOfPopulation    : Population start date
 *                               EndOfPopulation      : Population end date
 *                               StartOfCohort        : Cohort start date
 *                               EndOfCohort          : Cohort end date
 *                               Age                  : The date the patient becomes the age in years specified in window start
 *
 *   window start              The start of the time window as number of days relative to window start reference in
 *                             which the drug should be prescribed.
 *                             May be empty indicating all time before.
 *   window end reference      The reference point for window end. Possible values are:
 *
 *                               Index                : Index date (time of event)
 *                               StartOfPopulation    : Population start date
 *                               EndOfPopulation      : Population end date
 *                               StartOfCohort        : Cohort start date
 *                               EndOfCohort          : Cohort end date
 *                               Age                  : The date the patient becomes the age in years specified in window end
 *
 *   window end                The end of the time window as number of days relative to window end reference in
 *                             which the drug should be prescribed.
 *                             May be empty indicating all time after.
 *   value reference           The point of reference for the value DaysSinceUse. Possible values are:
 *
 *                               Index                : Index date (time of event)
 *                               StartOfPopulation    : Population start date
 *                               EndOfPopulation      : Population end date
 *                               StartOfCohort        : Cohort start date
 *                               EndOfCohort          : Cohort end date
 *
 *                             If the value is not DaysSinceUse it should be empty.
 *   value reference offset    The offset to the value reference.
 *   value                     The value that has to be written to the file. Possible values are:
 *
 *                               Count                : The number of prescription starts of the drugs within the time window.
 *                               CountATC             : The number of different ATC's the patient is exposed to within the time window.
 *                                                      When no ATC-codes are specified ATC-codes starting with an underscore are ignored.
 *                               Present              : 0 if there are no prescription starts of the drugs within the time window, otherwise 1.
 *                               DaysOfUse            : The number of days the drug is used within the window.
 *                               DaysSinceStart       : The number of days since start until the value reference
 *                               DaysSinceUse         : The number of days since use until the value reference.
 *
 * Example: R03AC01;;Index;-30;Index;0;Index;DaysSinceUse
 *
 *
 * Event covariates:
 *
 * Events to assess as covariates for the cases and controls with their
 * windows during which exposure to a events of the specified types will be measured.
 * The format is:
 *
 * EVENT;event type;label;window start reference;window start;window end reference;window end;value reference;value reference offset;value (e.g. MI;;index;-30;Index;0;;Incident)
 *
 *   event type                The type of event.
 *   label                     The label that is used in the header of the output file.
 *                             When empty a standard label is generated.
 *   window start reference    The reference point for window start. Possible values are:
 *
 *                               Index                : Index date (time of event)
 *                               StartOfPopulation    : Population start date
 *                               EndOfPopulation      : Population end date
 *                               StartOfCohort        : Cohort start date
 *                               EndOfCohort          : Cohort end date
 *                               Age                  : The date the patient becomes the age in years specified in window start
 *
 *   window start              The start of the time window as number of days relative to window start reference in
 *                             which the event should be present.
 *                             May be empty indicating all time before.
 *   window end reference      The reference point for window end. Possible values are:
 *
 *                               Index                : Index date (time of event)
 *                               StartOfPopulation    : Population start date
 *                               EndOfPopulation      : Population end date
 *                               StartOfCohort        : Cohort start date
 *                               EndOfCohort          : Cohort end date
 *                               Age                  : The date the patient becomes the age in years specified in window end
 *
 *   window end                The end of the time window as number of days relative to window end reference in
 *                             which the event should be present.
 *                             May be empty indicating all time after.
 *   value reference           The point of reference for the value TimeToIncident. Possible values are:
 *
 *                               Index                : Index date (time of event)
 *                               StartOfPopulation    : Population start date
 *                               EndOfPopulation      : Population end date
 *                               StartOfCohort        : Cohort start date
 *                               EndOfCohort          : Cohort end date
 *
 *                             If the value is not TimeToIncident it should be empty.
 *   value reference offset    The offset to the value reference.
 *   value                     The value that has to be written to the file. Possible values are:
 *
 *                               Count                : The number of events within the time window.
 *                               Present              : 0 if there are no events within the time window, otherwise 1.
 *                               Incident             : 1 if the event is incident with respect to all history, in the time window, otherwise 0.
 *                               TimeToIncident       : The distance in time from the value reference to the event if it is incident.
 *                               TimeSinceFirst       : The distance in time from the first event before or on the value reference date to the value reference date.
 *                               AgeAtFirst           : The age of the patient in years at the first event before or on the value reference date to the value reference date.
 *                               TimeSinceLast        : The distance in time from the last event before or on the value reference date to the value reference date.
 *                               AgeAtLast            : The age of the patient in years at last event before or on the value reference date to the value reference date.
 *
 *
 * Measurement covariates:
 *
 * List of measurements to assess as covariates for the cases and controls with their
 * windows during which measurements of the specified types will be measured.
 * The format is:
 *
 * MEASUREMENT;measurement type,measurement value 1,...,measurement value N;label;window start reference;window start;window end reference;window end;value reference;value reference offset;value
 *
 *   measurement type          The type of measurement.
 *   measurement value, ...    The possible values the measurement should have when defined, i.e. "measurement type,measurement value 1,...,measuremnt value N" contains a comma.
 *                             Examples measurement type,measurement value:
 *
 *                               "BMI"         = BMI with no specific value.
 *                               "BMI,"        = BMI with empty value.
 *                               "BMI,HIGH"    = BMI with value "HIGH".
 *                               "BMI,LOW,UNK" = BMI with value "LOW", or "UNK".
 *
 *   label                     The label that is used in the header of the output file.
 *                             When empty a standard label is generated.
 *   window start reference    The reference point for window start. Possible values are:
 *
 *                               Index                     : Index date (time of event)
 *                               StartOfPopulation         : Population start date
 *                               EndOfPopulation           : Population end date
 *                               StartOfCohort             : Cohort start date
 *                               EndOfCohort               : Cohort end date
 *                               Age                       : The date the patient becomes the age in years specified in window start
 *
 *   window start              The start of the time window as number of days relative to window start reference in
 *                             which the measurement should be present.
 *                             May be empty indicating all time before.
 *   window end reference      The reference point for window end. Possible values are:
 *
 *                               Index                     : Index date (time of event)
 *                               StartOfPopulation         : Population start date
 *                               EndOfPopulation           : Population end date
 *                               StartOfCohort             : Cohort start date
 *                               EndOfCohort               : Cohort end date
 *                               Age                       : The date the patient becomes the age in years specified in window end
 *
 *   window end                The end of the time window as number of days relative to window end reference in
 *                             which the measurement should be present.
 *                             May be empty indicating all time after.
 *   value reference           The point of reference for the value Nearest, NearestDistance, NearestBefore,
 *                             NearestBeforeDistance, NearestAfter, or NearestAfterDistance. Possible values are:
 *
 *                               Index                     : Index date (time of event)
 *                               StartOfPopulation         : Population start date
 *                               EndOfPopulation           : Population end date
 *                               StartOfCohort             : Cohort start date
 *                               EndOfCohort               : Cohort end date
 *
 *                             If the value is not one of Nearest, NearestDistance, NearestBefore, NearestBeforeDistance,
 *                             NearestAfter, or NearestAfterDistance it should be empty.
 *   value reference offset    The offset to the value reference.
 *   value                     The value that has to be written to the file. Possible values are:
 *
 *                               Count                     : The number of measurements within the time window.
 *                               Present                   : 0 if there are no measurements within the time window, otherwise 1.
 *                               Nearest                   : The value of the measurement that is nearest in time to the value reference.
 *                               NearestDistance           : The distance in time of the measurement that is nearest in time to the value reference.
 *                               NearestUnit               : The unit of the measurement that is nearest in time to the value reference.
 *                               NearestBefore             : The value of the measurement that is nearest in time before the value reference.
 *                               NearestBeforeDistance     : The distance in time of the measurement that is nearest in time before the value reference.
 *                               NearestBeforeUnit         : The unit of the measurement that is nearest in time before the value reference.
 *                               NearestAfter              : The value of the measurement that is nearest in time after the value reference.
 *                               NearestAfterDistance      : The distance in time of the measurement that is nearest in time after the value reference.
 *                               NearestAfterUnit          : The unit of the measurement that is nearest in time after the value reference.
 *                               NearestBeforeAfter        : The value of the measurement that is nearest in time before or if no measurement before the nearest in time after the value reference.
 *                               NearestBeforeAfterDistance: The distance in time of the measurement that is nearest in time before or if no measurement before the nearest in time after the value reference.
 *                               NearestBeforeAfterUnit    : The unit of the measurement that is nearest in time before or if no measurement before the nearest in time after the value reference.
 *                               Highest                   : The highest measurement value of the measurements with a valid numerical value within the time window.
 *                               HighestUnit               : The unit of the highest measurement value of the measurements with a valid numerical value within the time window.
 *                               Lowest                    : The lowest measurement value of the measurements with a valid numerical value within the time window.
 *                               LowestUnit                : The unit of the lowest measurement value of the measurements with a valid numerical value within the time window.
 *                               Average                   : The average value of the measurements with a valid numerical value within the time window.
 *                               AverageCount              : The number of measurements within the time window with a valid numerical value.
 *
 * Example: BMI;;Index;-30;Index;0;Index;NearestBefore
 *
 * There is one special measurement covariate for the GARVAN risk score. It can be defined as follows:
 *
 *   MEASUREMENT;GARVAN;label;period start reference;period start offset;value reference;value reference offset;;estimation period                      GARVAN risk based on weight and no hip fractures
 *   MEASUREMENT;GARVAN,BMD;label;period start reference;period start offset;value reference;value reference offset;;estimation period                  GARVAN risk based on BMD and no hip fractures
 *   MEASUREMENT;GARVAN,HIPFRACTURES;label;period start reference;period start offset;value reference;value reference offset;;estimation period         GARVAN risk based on weight with hip fractures
 *   MEASUREMENT;GARVAN,BMD,HIPFRACTURES;label;period start reference;period start offset;value reference;value reference offset;;estimation period     GARVAN risk based on BMD with hip fractures
 *
 * where
 *
 *   estimation period = FIVE_YEARS or TEN_YEARS
 *
 * Other measurements that are required are:
 *
 *   WEIGHT
 *   BMD
 *   FALL
 *   FRACTURE
 */
public class Covariates {
	protected List<Covariate> covariates = new ArrayList<Covariate>();
	protected Map<Covariate, String> covariateValues;

	// atcCovariates, eventCovariates, and measurementCovariates  are kept in separate Maps to
	// avoid conflicts between atc-codes, event types, and measurement types.
	protected Map<String, List<Covariate>> atcCovariates = new HashMap<String, List<Covariate>>();
	protected Map<String, List<Covariate>> eventCovariates = new HashMap<String, List<Covariate>>();
	protected Map<String, List<Covariate>> measurementCovariates = new HashMap<String, List<Covariate>>();

	public boolean isOK = true;


	public Covariates(List<String> covariateDefinitions) {
		this(covariateDefinitions, "covariate");
	}


	public Covariates(List<String> covariateDefinitions, String covariateTypeDescription) {
		for (String covariateDefinition : covariateDefinitions) {
			Covariate covariate = new Covariate(covariateDefinition, covariateTypeDescription);
			if (covariate.isOK) {
				List<Covariate> covariateList = null;
				covariates.add(covariate);
				if (covariate.covariateType == Covariate.DATATYPE_ATC) {
					String atc = covariate.getCovariate();
					covariateList = atcCovariates.get(atc);
					if (covariateList == null) {
						covariateList = new ArrayList<Covariate>();
						atcCovariates.put(atc, covariateList);
					}
				}
				else if (covariate.covariateType == Covariate.DATATYPE_EVENT) {
					String eventType = covariate.getCovariate();
					covariateList = eventCovariates.get(eventType);
					if (covariateList == null) {
						covariateList = new ArrayList<Covariate>();
						eventCovariates.put(eventType, covariateList);
					}
				}
				else if (covariate.covariateType == Covariate.DATATYPE_MEASUREMENT) {
					String measurementType = covariate.getCovariate();
					covariateList = measurementCovariates.get(measurementType);
					if (covariateList == null) {
						covariateList = new ArrayList<Covariate>();
						measurementCovariates.put(measurementType, covariateList);
					}
				}
				if (covariateList != null) {
					covariateList.add(covariate);
				}
			}
			else {
				isOK = false;
			}
		}
	}


	public String getHeader() {
		String header = "";
		for (Covariate covariate : covariates) {
			header += "," + covariate.getDescription();
		}
		return header;
	}


	public List<Covariate> getCovariates() {
		return covariates;
	}


	public Map<String, List<Covariate>> getATCCovariates() {
		return atcCovariates;
	}


	public Map<String, List<Covariate>> getEventCovariates() {
		return eventCovariates;
	}


	public Map<String, List<Covariate>> getMeasurementCovariates() {
		return measurementCovariates;
	}


	public String getCovariateValues(Patient patient, int indexDate, int cohortStartDate, int cohortEndDate, PatientCovariateInfo covariateInfo) {
		String valueString = "";
		covariateValues = new HashMap<Covariate, String>();

		// Determine the covariate values
		getATCCovariateValues(patient, indexDate, cohortStartDate, cohortEndDate, covariateInfo);
		getEventCovariateValues(patient, indexDate, cohortStartDate, cohortEndDate, covariateInfo);
		getMeasurementCovariateValues(patient, indexDate, cohortStartDate, cohortEndDate, covariateInfo);

		// Collect the values
		for (Covariate covariate : covariates) {
			String covariateValue = covariateValues.get(covariate);
			if (covariateValue != null) {
				valueString += "," + covariateValue;
			}
			else {
				valueString += ",";
			}
		}
		return valueString;
	}


	private void getATCCovariateValues(Patient patient, int indexDate, int cohortStartDate, int cohortEndDate, PatientCovariateInfo covariateInfo) {
		for (String covariateATC : atcCovariates.keySet()) {
			for (Covariate atcCovariate : atcCovariates.get(covariateATC)) {
				covariateValues.put(atcCovariate, atcCovariate.getValue(patient, indexDate, cohortStartDate, cohortEndDate, covariateATC, covariateInfo));
			}
		}
	}


	private void getEventCovariateValues(Patient patient, int indexDate, int cohortStartDate, int cohortEndDate, PatientCovariateInfo covariateInfo) {
		for (String covariateEventType : eventCovariates.keySet()) {
			for (Covariate eventCovariate : eventCovariates.get(covariateEventType)) {
				covariateValues.put(eventCovariate, eventCovariate.getValue(patient, indexDate, cohortStartDate, cohortEndDate, covariateEventType, covariateInfo));
			}
		}
	}


	private void getMeasurementCovariateValues(Patient patient, int indexDate, int cohortStartDate, int cohortEndDate, PatientCovariateInfo covariateInfo) {
		for (String covariateMeasurementType : measurementCovariates.keySet()) {
			for (Covariate measurementCovariate : measurementCovariates.get(covariateMeasurementType)) {
				covariateValues.put(measurementCovariate, measurementCovariate.getValue(patient, indexDate, cohortStartDate, cohortEndDate, covariateMeasurementType, covariateInfo));
			}
		}
	}
	public class Covariate {
		private boolean isCovariate = true;

		public static final byte ERROR                               = -1;

		// Data types
		public static final byte DATATYPE_ATC                        =  0;
		public static final byte DATATYPE_EVENT                      =  1;
		public static final byte DATATYPE_MEASUREMENT                =  2;

		// Reference points
		public static final byte REFERENCE_INDEX                     =  0;
		public static final byte REFERENCE_POPULATION_START          =  1;
		public static final byte REFERENCE_POPULATION_END            =  2;
		public static final byte REFERENCE_COHORT_START              =  3;
		public static final byte REFERENCE_COHORT_END                =  4;
		public static final byte REFERENCE_AGE                       =  5;

		// General values
		public static final byte VALUE_COUNT                         =  0;
		public static final byte VALUE_PRESENT                       =  1;

		// Prescription specific values
		public static final byte VALUE_COUNT_ATC                     = 10;
		public static final byte VALUE_DAYS_OF_USE                   = 11;
		public static final byte VALUE_DAYS_SINCE_START              = 12;
		public static final byte VALUE_DAYS_SINCE_USE                = 13;

		// Event specific values
		public static final byte VALUE_INCIDENT                      = 20;
		public static final byte VALUE_TIME_TO_INCIDENT              = 21;
		public static final byte VALUE_TIME_SINCE_FIRST              = 22;
		public static final byte VALUE_AGE_AT_FIRST                  = 23;
		public static final byte VALUE_TIME_SINCE_LAST               = 24;
		public static final byte VALUE_AGE_AT_LAST                   = 25;

		// Measurement specific values
		public static final byte VALUE_NEAREST                       = 30;
		public static final byte VALUE_NEAREST_DISTANCE              = 31;
		public static final byte VALUE_NEAREST_UNIT                  = 32;
		public static final byte VALUE_NEAREST_BEFORE                = 33;
		public static final byte VALUE_NEAREST_BEFORE_DISTANCE       = 34;
		public static final byte VALUE_NEAREST_BEFORE_UNIT           = 35;
		public static final byte VALUE_NEAREST_AFTER                 = 36;
		public static final byte VALUE_NEAREST_AFTER_DISTANCE        = 37;
		public static final byte VALUE_NEAREST_AFTER_UNIT            = 38;
		public static final byte VALUE_NEAREST_BEFORE_AFTER          = 39;
		public static final byte VALUE_NEAREST_BEFORE_AFTER_DISTANCE = 40;
		public static final byte VALUE_NEAREST_BEFORE_AFTER_UNIT     = 41;
		public static final byte VALUE_HIGHEST                       = 42;
		public static final byte VALUE_HIGHEST_UNIT                  = 43;
		public static final byte VALUE_LOWEST                        = 44;
		public static final byte VALUE_LOWEST_UNIT                   = 45;
		public static final byte VALUE_AVERAGE                       = 46;
		public static final byte VALUE_AVERAGE_COUNT                 = 47;
		public static final byte VALUE_GARVAN_RISK_FIVE_YEARS        = 48;
		public static final byte VALUE_GARVAN_RISK_TEN_YEARS         = 49;

		public byte covariateType = ERROR;

		private String definition = "";
		private String covariate = "";
		private List<String> atcList = new ArrayList<String>();
		private boolean measurementValueDefined = false;
		private List<String> measurementValues = new ArrayList<String>();
		private String label = "";
		private byte windowStartReference = ERROR;
		private int windowStart = 0;
		private byte windowEndReference = ERROR;
		private int windowEnd = 0;
		private byte value = VALUE_COUNT;
		private byte valueReference = ERROR;
		private int valueReferenceOffset = 0;

		private boolean isOK = true;


		public Covariate(String covariateDefinition, String covariateTypeDescription) {
			/*
			 * ATC;atc;label;window start reference;window start;window end reference;window end;value reference;value
			 * EVENT;event type;label;window start reference;window start;window end reference;window end;value reference;value
			 * MEASUREMENT;measurement type;label;window start reference;window start;window end reference;window end;value reference;value
			 */
			isCovariate = covariateTypeDescription.equals("covariate");
			definition = covariateDefinition;
			String[] covariateDefinitionSplit = covariateDefinition.trim().split(";");
			if (covariateDefinitionSplit.length >= 10) {

				// Covariate type: ATC, EVENT, or MEASUEMENT
				String covariateTypeString = covariateDefinitionSplit[0].trim().toUpperCase();
				if (covariateTypeString.equals("ATC")) {
					covariateType = DATATYPE_ATC;
				}
				else if (covariateTypeString.equals("EVENT")) {
					covariateType = DATATYPE_EVENT;
				}
				else if (covariateTypeString.equals("MEASUREMENT")) {
					covariateType = DATATYPE_MEASUREMENT;
				}
				else {
					Logging.add("Unknown type of " + covariateTypeDescription + " (" + covariateTypeString + ") in " + covariateTypeDescription + " definition: " + covariateDefinition);
					isOK = false;
				}

				// Covariate: atc, event type, or measurement type
				covariate = covariateDefinitionSplit[1].trim().toUpperCase();
				if (covariate.contains(",")) {
					if (covariateType == DATATYPE_ATC) {
						String[] covariateSplit = covariate.split(",");
						for (String atc : covariateSplit) {
							if (!atcList.contains(atc)) {
								atcList.add(atc);
							}
						}
					}
					else if (covariateType == DATATYPE_MEASUREMENT) {
						String[] covariateSplit = covariate.split(",");
						covariate = covariateSplit[0].trim();
						if (covariateSplit.length > 1) {
							for (int valueNr = 1; valueNr < covariateSplit.length; valueNr++) {
								String value = covariateSplit[valueNr].trim();
								if (value.equals("")) {
									value = "NO VALUE";
								}
								measurementValues.add(value);
							}
						}
						else {
							measurementValues.add("NO VALUE");
						}
						measurementValueDefined = true;
					}
				}
				else if (covariateType == DATATYPE_ATC) {
					atcList.add(covariate);
				}

				// label
				label = covariateDefinitionSplit[2];

				// Window start reference
				if (!covariateDefinitionSplit[3].trim().equals("")) {
					windowStartReference = getReferenceType(covariateDefinitionSplit[3].trim().toUpperCase());
					if (windowStartReference == ERROR) {
						Logging.add("Error unknown type of start reference point in " + covariateTypeDescription + " definition: " + covariateDefinition);
						isOK = false;
					}
				}

				// Window start
				if (covariateDefinitionSplit[4].trim().equals("")) {
					windowStart = Integer.MIN_VALUE;
				}
				else {
					try {
						windowStart = Integer.parseInt(covariateDefinitionSplit[4].trim());
					} catch (NumberFormatException e) {
						Logging.add("Error in window start of " + covariateTypeDescription + " definition: " + covariateDefinition);
						isOK = false;
					}
				}

				// Window end reference
				if (!covariateDefinitionSplit[5].trim().equals("")) {
					windowEndReference = getReferenceType(covariateDefinitionSplit[5].trim().toUpperCase());
					if (windowEndReference == ERROR) {
						Logging.add("Error unknown type of end reference point in " + covariateTypeDescription + " definition: " + covariateDefinition);
						isOK = false;
					}
				}

				// Window end
				if (covariateDefinitionSplit[6].trim().equals("")) {
					windowEnd = Integer.MAX_VALUE;
				}
				else {
					try {
						windowEnd = Integer.parseInt(covariateDefinitionSplit[6].trim());
					} catch (NumberFormatException e) {
						Logging.add("Error in window end of " + covariateTypeDescription + " definition: " + covariateDefinition);
						isOK = false;
					}
				}

				if (!covariateDefinitionSplit[9].trim().equals("")) {
					covariateDefinitionSplit[9] = covariateDefinitionSplit[9].trim().toUpperCase();
					if (covariateDefinitionSplit[9].equals("COUNT") && (!covariate.equals("GARVAN"))) {
						value = VALUE_COUNT;
					}
					else if (covariateDefinitionSplit[9].equals("PRESENT") && (!covariate.equals("GARVAN"))) {
						value = VALUE_PRESENT;
					}
					else if (covariateDefinitionSplit[9].equals("COUNTATC") && (covariateType == DATATYPE_ATC)) {
						value = VALUE_COUNT_ATC;
					}
					else if (covariateDefinitionSplit[9].equals("DAYSOFUSE") && (covariateType == DATATYPE_ATC)) {
						value = VALUE_DAYS_OF_USE;
					}
					else if (covariateDefinitionSplit[9].equals("DAYSSINCESTART") && (covariateType == DATATYPE_ATC)) {
						value = VALUE_DAYS_SINCE_START;
					}
					else if (covariateDefinitionSplit[9].equals("DAYSSINCEUSE") && (covariateType == DATATYPE_ATC)) {
						value = VALUE_DAYS_SINCE_USE;
					}
					else if (covariateDefinitionSplit[9].equals("INCIDENT") && (covariateType == DATATYPE_EVENT)) {
						value = VALUE_INCIDENT;
					}
					else if (covariateDefinitionSplit[9].equals("TIMETOINCIDENT") && (covariateType == DATATYPE_EVENT)) {
						value = VALUE_TIME_TO_INCIDENT;
					}
					else if (covariateDefinitionSplit[9].equals("TIMESINCEFIRST") && (covariateType == DATATYPE_EVENT)) {
						value = VALUE_TIME_SINCE_FIRST;
					}
					else if (covariateDefinitionSplit[9].equals("AGEATFIRST") && (covariateType == DATATYPE_EVENT)) {
						value = VALUE_AGE_AT_FIRST;
					}
					else if (covariateDefinitionSplit[9].equals("TIMESINCELAST") && (covariateType == DATATYPE_EVENT)) {
						value = VALUE_TIME_SINCE_LAST;
					}
					else if (covariateDefinitionSplit[9].equals("AGEATLAST") && (covariateType == DATATYPE_EVENT)) {
						value = VALUE_AGE_AT_LAST;
					}
					else if (covariateDefinitionSplit[9].equals("NEAREST") && (covariateType == DATATYPE_MEASUREMENT) && (!covariate.equals("GARVAN"))) {
						value = VALUE_NEAREST;
					}
					else if (covariateDefinitionSplit[9].equals("NEARESTDISTANCE") && (covariateType == DATATYPE_MEASUREMENT) && (!covariate.equals("GARVAN"))) {
						value = VALUE_NEAREST_DISTANCE;
					}
					else if (isCovariate && covariateDefinitionSplit[9].equals("NEARESTUNIT") && (covariateType == DATATYPE_MEASUREMENT) && (!covariate.equals("GARVAN"))) {
						value = VALUE_NEAREST_UNIT;
					}
					else if (covariateDefinitionSplit[9].equals("NEARESTBEFORE") && (covariateType == DATATYPE_MEASUREMENT) && (!covariate.equals("GARVAN"))) {
						value = VALUE_NEAREST_BEFORE;
					}
					else if (covariateDefinitionSplit[9].equals("NEARESTBEFOREDISTANCE") && (covariateType == DATATYPE_MEASUREMENT) && (!covariate.equals("GARVAN"))) {
						value = VALUE_NEAREST_BEFORE_DISTANCE;
					}
					else if (isCovariate && covariateDefinitionSplit[9].equals("NEARESTBEFOREUNIT") && (covariateType == DATATYPE_MEASUREMENT) && (!covariate.equals("GARVAN"))) {
						value = VALUE_NEAREST_BEFORE_UNIT;
					}
					else if (covariateDefinitionSplit[9].equals("NEARESTAFTER") && (covariateType == DATATYPE_MEASUREMENT) && (!covariate.equals("GARVAN"))) {
						value = VALUE_NEAREST_AFTER;
					}
					else if (covariateDefinitionSplit[9].equals("NEARESTAFTERDISTANCE") && (covariateType == DATATYPE_MEASUREMENT) && (!covariate.equals("GARVAN"))) {
						value = VALUE_NEAREST_AFTER_DISTANCE;
					}
					else if (isCovariate && covariateDefinitionSplit[9].equals("NEARESTAFTERUNIT") && (covariateType == DATATYPE_MEASUREMENT) && (!covariate.equals("GARVAN"))) {
						value = VALUE_NEAREST_AFTER_UNIT;
					}
					else if (covariateDefinitionSplit[9].equals("NEARESTBEFOREAFTER") && (covariateType == DATATYPE_MEASUREMENT) && (!covariate.equals("GARVAN"))) {
						value = VALUE_NEAREST_BEFORE_AFTER;
					}
					else if (covariateDefinitionSplit[9].equals("NEARESTBEFOREAFTERDISTANCE") && (covariateType == DATATYPE_MEASUREMENT) && (!covariate.equals("GARVAN"))) {
						value = VALUE_NEAREST_BEFORE_AFTER_DISTANCE;
					}
					else if (isCovariate && covariateDefinitionSplit[9].equals("NEARESTBEFOREAFTERUNIT") && (covariateType == DATATYPE_MEASUREMENT) && (!covariate.equals("GARVAN"))) {
						value = VALUE_NEAREST_BEFORE_AFTER_UNIT;
					}
					else if (covariateDefinitionSplit[9].equals("HIGHEST") && (covariateType == DATATYPE_MEASUREMENT) && (!covariate.equals("GARVAN"))) {
						value = VALUE_HIGHEST;
					}
					else if (covariateDefinitionSplit[9].equals("HIGHESTUNIT") && (covariateType == DATATYPE_MEASUREMENT) && (!covariate.equals("GARVAN"))) {
						value = VALUE_HIGHEST_UNIT;
					}
					else if (covariateDefinitionSplit[9].equals("LOWEST") && (covariateType == DATATYPE_MEASUREMENT) && (!covariate.equals("GARVAN"))) {
						value = VALUE_LOWEST;
					}
					else if (covariateDefinitionSplit[9].equals("LOWESTUNIT") && (covariateType == DATATYPE_MEASUREMENT) && (!covariate.equals("GARVAN"))) {
						value = VALUE_LOWEST_UNIT;
					}
					else if (covariateDefinitionSplit[9].equals("AVERAGE") && (covariateType == DATATYPE_MEASUREMENT) && (!covariate.equals("GARVAN"))) {
						value = VALUE_AVERAGE;
					}
					else if (covariateDefinitionSplit[9].equals("AVERAGECOUNT") && (covariateType == DATATYPE_MEASUREMENT) && (!covariate.equals("GARVAN"))) {
						value = VALUE_AVERAGE_COUNT;
					}
					else if (covariateDefinitionSplit[9].equals("FIVE_YEARS") && (covariateType == DATATYPE_MEASUREMENT) && covariate.equals("GARVAN")) {
						value = VALUE_GARVAN_RISK_FIVE_YEARS;
					}
					else if (covariateDefinitionSplit[9].equals("TEN_YEARS") && (covariateType == DATATYPE_MEASUREMENT) && covariate.equals("GARVAN")) {
						value = VALUE_GARVAN_RISK_TEN_YEARS;
					}
					else {
						Logging.add("Error in value of " + covariateTypeDescription + " definition: " + covariateDefinition);
						isOK = false;
					}
				}
				else {
					if (covariate.equals("GARVAN")) {
						// Special covariate for GARVAN risk score
						if (windowEndReference == ERROR) {
							Logging.add("Value reference may not be empty in " + covariateTypeDescription + " definition: " + covariateDefinition);
							isOK = false;
						}
						if (windowEnd == Integer.MAX_VALUE) {
							Logging.add("Value reference offset may not be empty in " + covariateTypeDescription + " definition: " + covariateDefinition);
							isOK = false;
						}
					}
					else {
						Logging.add("Error in value (may not be empty) of " + covariateTypeDescription + " definition: " + covariateDefinition);
						isOK = false;
					}
				}

				if ((value == VALUE_COUNT)                  ||
					(value == VALUE_PRESENT)                ||
					(value == VALUE_DAYS_OF_USE)            ||
					(value == VALUE_INCIDENT)               ||
					(value == VALUE_HIGHEST)                ||
					(value == VALUE_HIGHEST_UNIT)           ||
					(value == VALUE_LOWEST)                 ||
					(value == VALUE_LOWEST_UNIT)            ||
					(value == VALUE_AVERAGE)                ||
					(value == VALUE_AVERAGE_COUNT)          ||
					(value == VALUE_COUNT_ATC)              ||
					(value == VALUE_GARVAN_RISK_FIVE_YEARS) ||
					(value == VALUE_GARVAN_RISK_TEN_YEARS)
					) {
					if (!covariateDefinitionSplit[7].trim().equals("")) {
						Logging.add("Error value reference should be empty in " + covariateTypeDescription + " definition: " + covariateDefinition);
						isOK = false;
					}
				}
				else if ((value != VALUE_COUNT_ATC)              &&
						 (value != VALUE_GARVAN_RISK_FIVE_YEARS) &&
						 (value != VALUE_GARVAN_RISK_TEN_YEARS)
						) {
					valueReference = getReferenceType(covariateDefinitionSplit[7].trim().toUpperCase());
					if (valueReference == ERROR) {
						Logging.add("Error unknown type of value reference point in " + covariateTypeDescription + " definition: " + covariateDefinition);
						isOK = false;
					}
					try {
						valueReferenceOffset = Integer.parseInt(covariateDefinitionSplit[8].trim());
					} catch (NumberFormatException e) {
						Logging.add("Error in value reference offset of " + covariateTypeDescription + " definition: " + covariateDefinition);
						isOK = false;
					}
				}
			}
			else {
				Logging.add("Not the correct number (> 9) of parameters specified in " + covariateTypeDescription + " definition: " + covariateDefinition);
				isOK = false;
			}
		}


		private byte getReferenceType(String reference) {
			byte referenceType = ERROR;
			if (reference.equals("INDEX")) {
				referenceType = REFERENCE_INDEX;
			}
			else if (reference.equals("STARTOFPOPULATION")) {
				referenceType = REFERENCE_POPULATION_START;
			}
			else if (reference.equals("ENDOFPOPULATION")) {
				referenceType = REFERENCE_POPULATION_END;
			}
			else if (reference.equals("STARTOFCOHORT")) {
				referenceType = REFERENCE_COHORT_START;
			}
			else if (reference.equals("ENDOFCOHORT")) {
				referenceType = REFERENCE_COHORT_END;
			}
			else if (reference.equals("AGE")) {
				referenceType = REFERENCE_AGE;
			}
			return referenceType;
		}


		public String getDescription() {
			String description = getLabel();
			if (description.equals("")) {
				description = covariate;
				if (isMeasurementValueDefined()) {
					description += "(";
					for (int valueNr = 0; valueNr < measurementValues.size(); valueNr++) {
						description += (valueNr > 0 ? "," : "") + measurementValues.get(valueNr);
					}
					description += ")";
				}
				description += "_";
				description += getWindowEdgeDescription(windowStartReference, windowStart);
				description += "_" + getWindowEdgeDescription(windowEndReference, windowEnd);
				description += "_";
				if (value == VALUE_COUNT) description += "Count";
				else if (value == VALUE_PRESENT) description += "Present";
				else if (value == VALUE_COUNT_ATC) description += "CountATC";
				else if (value == VALUE_DAYS_OF_USE) description += "DaysOfUse";
				else if (value == VALUE_DAYS_SINCE_START) description += "DaysSinceStart" + "_" + getReferenceDescription(valueReference);
				else if (value == VALUE_DAYS_SINCE_USE) description += "DaysSinceUse" + "_" + getReferenceDescription(valueReference);
				else if (value == VALUE_INCIDENT) description += "Incident";
				else if (value == VALUE_TIME_TO_INCIDENT) description += "TimeToIncident" + "_" + getReferenceDescription(valueReference);
				else if (value == VALUE_TIME_SINCE_FIRST) description += "TimeSinceFirst" + "_" + getReferenceDescription(valueReference);
				else if (value == VALUE_AGE_AT_FIRST) description += "AgeAtFirst" + "_" + getReferenceDescription(valueReference);
				else if (value == VALUE_TIME_SINCE_LAST) description += "TimeSinceLast" + "_" + getReferenceDescription(valueReference);
				else if (value == VALUE_AGE_AT_LAST) description += "AgeAtLast" + "_" + getReferenceDescription(valueReference);
				else if (value == VALUE_NEAREST) description += "Nearest" + "_" + getReferenceDescription(valueReference);
				else if (value == VALUE_NEAREST_DISTANCE) description += "NearestDistance" + "_" + getReferenceDescription(valueReference);
				else if (value == VALUE_NEAREST_UNIT) description += "NearestUnit" + "_" + getReferenceDescription(valueReference);
				else if (value == VALUE_NEAREST_BEFORE) description += "NearestBefore" + "_" + getReferenceDescription(valueReference);
				else if (value == VALUE_NEAREST_BEFORE_DISTANCE) description += "NearestDistanceBefore" + "_" + getReferenceDescription(valueReference);
				else if (value == VALUE_NEAREST_BEFORE_UNIT) description += "NearestBeforeUnit" + "_" + getReferenceDescription(valueReference);
				else if (value == VALUE_NEAREST_AFTER) description += "NearestAfter" + "_" + getReferenceDescription(valueReference);
				else if (value == VALUE_NEAREST_AFTER_DISTANCE) description += "NearestAfterDistance" + "_" + getReferenceDescription(valueReference);
				else if (value == VALUE_NEAREST_AFTER_UNIT) description += "NearestAfterUnit" + "_" + getReferenceDescription(valueReference);
				else if (value == VALUE_NEAREST_BEFORE_AFTER) description += "NearestBeforeAfter" + "_" + getReferenceDescription(valueReference);
				else if (value == VALUE_NEAREST_BEFORE_AFTER_DISTANCE) description += "NearestDistanceBeforeAfter" + "_" + getReferenceDescription(valueReference);
				else if (value == VALUE_NEAREST_BEFORE_AFTER_UNIT) description += "NearestBeforeAfterUnit" + "_" + getReferenceDescription(valueReference);
				else if (value == VALUE_HIGHEST) description += "Highest";
				else if (value == VALUE_HIGHEST_UNIT) description += "HighestUnit";
				else if (value == VALUE_LOWEST) description += "Lowest";
				else if (value == VALUE_LOWEST_UNIT) description += "LowestUnit";
				else if (value == VALUE_AVERAGE) description += "Average";
				else if (value == VALUE_AVERAGE_COUNT) description += "AverageCount";
				else description += "Unknown";
			}
			return description;
		}


		private String getReferenceDescription(byte reference) {
			String description = "Unknown";
			if (reference == REFERENCE_INDEX) description = "Index";
			else if (reference == REFERENCE_POPULATION_START) description = "PopulationStart";
			else if (reference == REFERENCE_POPULATION_END) description = "PopulationEnd";
			else if (reference == REFERENCE_COHORT_START) description = "CohortStart";
			else if (reference == REFERENCE_COHORT_END) description = "CohortEnd";
			return description;
		}


		private String getWindowEdgeDescription(byte reference, int delta) {
			String windowEdgeDescription = "Unknown";
			if ((delta == Integer.MIN_VALUE) || (delta == Integer.MAX_VALUE)) {
				windowEdgeDescription = "-";
			}
			else {
				if (reference == REFERENCE_INDEX) windowEdgeDescription = "Index";
				else if (reference == REFERENCE_POPULATION_START) windowEdgeDescription = "PopulationStart";
				else if (reference == REFERENCE_POPULATION_END) windowEdgeDescription = "PopulationEnd";
				else if (reference == REFERENCE_COHORT_START) windowEdgeDescription = "CohortStart";
				else if (reference == REFERENCE_COHORT_END) windowEdgeDescription = "CohortEnd";
				if (delta >= 0) {
					windowEdgeDescription += "+";
				}
				windowEdgeDescription += Integer.toString(delta);
			}
			return windowEdgeDescription;
		}


		public String getCovariate() {
			return covariate;
		}


		public List<String> getATCList() {
			return atcList;
		}


		public boolean isMeasurementValueDefined() {
			return measurementValueDefined;
		}


		public boolean hasMeasurementValue(String value) {
			return measurementValues.contains(value);
		}


		public String getLabel() {
			return label;
		}


		public int getWindowStartReferenceDate(Patient patient, int indexDate, int cohortStartDate, int cohortEndDate, int offset) {
			return getReferenceDate(windowStartReference, patient, indexDate, cohortStartDate, cohortEndDate, offset);
		}


		public int getWindowEndReferenceDate(Patient patient, int indexDate, int cohortStartDate, int cohortEndDate, int offset) {
			return getReferenceDate(windowEndReference, patient, indexDate, cohortStartDate, cohortEndDate, offset);
		}


		public int getValueReferenceDate(Patient patient, int indexDate, int cohortStartDate, int cohortEndDate, int offset) {
			return getReferenceDate(valueReference, patient, indexDate, cohortStartDate, cohortEndDate, offset);
		}


		private int getReferenceDate(byte reference, Patient patient, int indexDate, int cohortStartDate, int cohortEndDate, int offset) {
			int referenceDate = indexDate + offset; // start_reference == REFERENCE_INDEX
			if (reference == REFERENCE_POPULATION_START) {
				referenceDate = patient.getPopulationStartDate() + offset;
			}
			else if (reference == REFERENCE_POPULATION_END) {
				referenceDate = patient.getPopulationEndDate() + offset;
			}
			else if (reference == REFERENCE_COHORT_START) {
				referenceDate = cohortStartDate + offset;
			}
			else if (reference == REFERENCE_COHORT_END) {
				referenceDate = cohortEndDate + offset;
			}
			else if (reference == REFERENCE_AGE) {
				referenceDate = patient.getBirthdayInYear(patient.getBirthYear() + offset);
			}
			return referenceDate;
		}


		public int getWindowStart(Patient patient, int indexDate, int cohortStartDate, int cohortEndDate) {
			return (windowStart == Integer.MIN_VALUE ? windowStart : getWindowStartReferenceDate(patient, indexDate, cohortStartDate, cohortEndDate, windowStart));
		}


		public int getWindowEnd(Patient patient, int indexDate, int cohortStartDate, int cohortEndDate) {
			return (windowEnd == Integer.MAX_VALUE ? windowEnd : getWindowEndReferenceDate(patient, indexDate, cohortStartDate, cohortEndDate, windowEnd));
		}


		public String getValue(Patient patient, int indexDate, int cohortStartDate, int cohortEndDate, String covariateValueType, PatientCovariateInfo covariateInfo) {
			String covariateValue = "";
			if (covariateType == DATATYPE_ATC) {
				List<Prescription> prescriptions = covariateInfo.getCovariatePrescriptions(covariateValueType);

				int windowStart    = getWindowStart(patient, indexDate, cohortStartDate, cohortEndDate);
				int windowEnd      = getWindowEnd(patient, indexDate, cohortStartDate, cohortEndDate);
				int daysOfUse      = 0;
				int daysSinceStart = Integer.MIN_VALUE;
				int daysSinceUse   = Integer.MAX_VALUE;
				int count          = 0;
				Set<String> countATC = new HashSet<String>();
				boolean prescriptionFound = false;
				if (prescriptions != null) {
					for (Prescription prescription : prescriptions) {
						int valueReferenceDate = getValueReferenceDate(patient, indexDate, cohortStartDate, cohortEndDate, valueReferenceOffset);
						if ((value == Covariate.VALUE_DAYS_SINCE_START) && (prescription.getDate() < windowEnd) && (prescription.getEndDate() > windowStart)) {
							daysSinceStart = Math.max(daysSinceStart, valueReferenceDate - prescription.getDate());
							prescriptionFound = true;
						}
						else if ((value == Covariate.VALUE_DAYS_SINCE_USE) && (prescription.getDate() < windowEnd) && (prescription.getEndDate() > windowStart)) {
							if ((prescription.getDate() < valueReferenceDate) && (prescription.getEndDate() > valueReferenceDate)) {
								daysSinceUse = 0;
								prescriptionFound = true;
							}
							else if (prescription.getEndDate() <= valueReferenceDate) {
								daysSinceUse = Math.min(daysSinceUse, valueReferenceDate - prescription.getEndDate());
								prescriptionFound = true;
							}
						}
						else {
							int start = Math.max(prescription.getDate(), windowStart);
							int end = Math.min(prescription.getEndDate(), windowEnd);
							int windowUse = (end - start);
							daysOfUse += (windowUse > 0 ? windowUse : 0);
							if ((prescription.getEndDate() > windowStart) && (prescription.getStartDate() < windowEnd)) {
								count++;
								countATC.add(prescription.getATC());
							}
						}
					}
				}
				if (value == Covariate.VALUE_COUNT) {
					covariateValue = Integer.toString(count);
				}
				else if (value == Covariate.VALUE_PRESENT) {
					covariateValue = (count > 0 ? "1" : "0");
				}
				else if (value == Covariate.VALUE_COUNT_ATC) {
					covariateValue = Integer.toString(countATC.size());
				}
				else if (value == Covariate.VALUE_DAYS_OF_USE) {
					covariateValue = Integer.toString(daysOfUse);
				}
				else if (value == Covariate.VALUE_DAYS_SINCE_START) {
					covariateValue = (prescriptionFound ? Integer.toString(daysSinceStart) : "");
				}
				else if (value == Covariate.VALUE_DAYS_SINCE_USE) {
					covariateValue = (prescriptionFound ? Integer.toString(daysSinceUse) : "");
				}
			}
			else if (covariateType == DATATYPE_EVENT) {
				List<Event> events = covariateInfo.getCovariateEvents(covariateValueType);

				int windowStart = getWindowStart(patient, indexDate, cohortStartDate, cohortEndDate);
				int windowEnd   = getWindowEnd(patient, indexDate, cohortStartDate, cohortEndDate);
				int referenceDate = getValueReferenceDate(patient, indexDate, cohortStartDate, cohortEndDate, valueReferenceOffset);
				int count = 0;
				int incident = 0;
				int incidentEventDate = -1;
				int firstEventDate = -1;
				int lastEventDate = -1;
				boolean eventFound = false;
				boolean incidentEventFound = false;
				boolean firstEventFound = false;
				boolean lastEventFound = false;
				if (events != null) {
					incident = 1;
					for (Event event : events) {
						eventFound = true;
						if (event.getDate() < windowStart) {
							incident = 0;
						}
						else if ((event.getDate() >= windowStart) && (event.getDate() < windowEnd)) {
							if ((incident == 1) && (!incidentEventFound)) {
								incidentEventDate = event.getDate();
								incidentEventFound = true;
							}
							if ((event.getDate() <= referenceDate) && ((!firstEventFound) || (event.getDate() < firstEventDate))) {
								firstEventFound = true;
								firstEventDate = event.getDate();
							}
							if ((event.getDate() <= referenceDate) && ((!lastEventFound) || (event.getDate() > lastEventDate))) {
								lastEventFound = true;
								lastEventDate = event.getDate();
							}
							count++;
						}
					}
				}
				if (value == Covariate.VALUE_COUNT) {
					covariateValue = Integer.toString(count);
				}
				else if (value == Covariate.VALUE_PRESENT) {
					covariateValue = (count > 0 ? "1" : "0");
				}
				else if (value == Covariate.VALUE_INCIDENT) {
					covariateValue = (eventFound ? Integer.toString(incident) : "0");
				}
				else if (value == Covariate.VALUE_TIME_TO_INCIDENT) {
					covariateValue = (incidentEventFound ? Integer.toString(incidentEventDate - referenceDate) : "");
				}
				else if (value == Covariate.VALUE_TIME_SINCE_FIRST) {
					covariateValue = (firstEventFound ? Integer.toString(referenceDate - firstEventDate) : "");
				}
				else if (value == Covariate.VALUE_AGE_AT_FIRST) {
					covariateValue = (firstEventFound ? Integer.toString(patient.getAgeAtDateInYears(firstEventDate)) : "");
				}
				else if (value == Covariate.VALUE_TIME_SINCE_LAST) {
					covariateValue = (lastEventFound ? Integer.toString(referenceDate - lastEventDate) : "");
				}
				else if (value == Covariate.VALUE_AGE_AT_LAST) {
					covariateValue = (lastEventFound ? Integer.toString(patient.getAgeAtDateInYears(lastEventDate)) : "");
				}
			}
			else if (covariateType == DATATYPE_MEASUREMENT) {
				if (covariate.equals("GARVAN")) {
					// Calculate the GARVAN risk score
					int valueReferenceDate = getWindowEnd(patient, indexDate, cohortStartDate, cohortEndDate);
					int windowStart = getWindowStart(patient, indexDate, cohortStartDate, cohortEndDate);
					int valueReferencePeriod = (windowStart == Integer.MIN_VALUE) ? -1 : ((windowStart > valueReferenceDate) ? 0 : valueReferenceDate - windowStart);
					int period = (value == VALUE_GARVAN_RISK_FIVE_YEARS ? GarvanRiskFactor.FIVE_YEARS : GarvanRiskFactor.TEN_YEARS);
					boolean forHipFractures = measurementValues.contains("HIPFRACTURES");
					boolean bmd = measurementValues.contains("BMD");

					//Garvan parameters
					List<String> fallLabels = new ArrayList<String>();
					fallLabels.add("FALL");
					fallLabels.add("FALLS");

					List<String> fractureLabels = new ArrayList<String>();
					fractureLabels.add("HIPFRACTURES");
					fractureLabels.add("FRACTURE");
					fractureLabels.add("FRACTURES");

					new GarvanRiskFactor.Builder().bmdLabel("BMD").fallLabels(fallLabels).fractureLabels(fractureLabels).weightLabel("WEIGHT").build();
					GarvanRiskFactor.init(patient);
					// ATTENTION: valueReferenceDate - 1 because GarvanRiskFactor.calculateRisk includes the indexDate
					covariateValue = GarvanRiskFactor.calculateRisk(period, valueReferenceDate - 1, valueReferencePeriod, true, forHipFractures, bmd);
				}
				else {
					List<Measurement> measurements = covariateInfo.getCovariateMeasurements(covariateValueType);

					int windowStart = getWindowStart(patient, indexDate, cohortStartDate, cohortEndDate);
					int windowEnd = getWindowEnd(patient, indexDate, cohortStartDate, cohortEndDate);
					boolean measurementFound = false;
					Measurement nearestMeasurement = null;
					int minimumDistance = 0;
					boolean measurementFoundBefore = false;
					Measurement nearestMeasurementBefore = null;
					int minimumDistanceBefore = 0;
					boolean measurementFoundAfter = false;
					Measurement nearestMeasurementAfter = null;
					int minimumDistanceAfter = 0;
					double highest = Double.MIN_VALUE;
					String highestUnit = "";
					double lowest = Double.MAX_VALUE;
					String lowestUnit = "";
					double sum = 0;
					int count = 0;
					if (measurements != null) {
						for (Measurement measurement : measurements) {
							if ((!isMeasurementValueDefined()) || (hasMeasurementValue(measurement.getValue()))) {
								if ((measurement.getDate() >= windowStart) && (measurement.getDate() < windowEnd)) {
									int valueReferenceDate = getValueReferenceDate(patient, indexDate, cohortStartDate, cohortEndDate, valueReferenceOffset);
									int distance = measurement.getDate() - valueReferenceDate;
									if (	(value == Covariate.VALUE_NEAREST) ||
											(value == Covariate.VALUE_NEAREST_DISTANCE) ||
											(value == Covariate.VALUE_NEAREST_UNIT)
										) {
										if ((!measurementFound) || (Math.abs(distance) < Math.abs(minimumDistance))) {
											nearestMeasurement = measurement;
											minimumDistance = distance;
											measurementFound = true;
										}
									}
									if (	(	(	(value == Covariate.VALUE_NEAREST_BEFORE) ||
													(value == Covariate.VALUE_NEAREST_BEFORE_DISTANCE) ||
													(value == Covariate.VALUE_NEAREST_BEFORE_UNIT) ||
													(value == Covariate.VALUE_NEAREST_BEFORE_AFTER) ||
													(value == Covariate.VALUE_NEAREST_BEFORE_AFTER_DISTANCE) ||
													(value == Covariate.VALUE_NEAREST_BEFORE_AFTER_UNIT)
												) &&
												(distance < 0)
											)
										) {
										if ((!measurementFound) || (Math.abs(distance) < minimumDistanceBefore)) {
											nearestMeasurementBefore = measurement;
											minimumDistanceBefore = Math.abs(distance);
											measurementFoundBefore = true;
											measurementFound = true;
										}
									}
									if (	(	(	(value == Covariate.VALUE_NEAREST_AFTER) ||
													(value == Covariate.VALUE_NEAREST_AFTER_DISTANCE) ||
													(value == Covariate.VALUE_NEAREST_AFTER_UNIT) ||
													(value == Covariate.VALUE_NEAREST_BEFORE_AFTER) ||
													(value == Covariate.VALUE_NEAREST_BEFORE_AFTER_DISTANCE) ||
													(value == Covariate.VALUE_NEAREST_BEFORE_AFTER_UNIT)
												) &&
												(distance > 0)
											)
										) {
										if ((!measurementFound) || (Math.abs(distance) < minimumDistanceAfter)) {
											nearestMeasurementAfter = measurement;
											minimumDistanceAfter = Math.abs(distance);
											measurementFoundAfter = true;
											measurementFound = true;
										}
									}
									else if (	(value == Covariate.VALUE_HIGHEST) ||
												(value == Covariate.VALUE_HIGHEST_UNIT) ||
												(value == Covariate.VALUE_LOWEST) ||
												(value == Covariate.VALUE_LOWEST_UNIT) ||
												(value == Covariate.VALUE_AVERAGE) ||
												(value == Covariate.VALUE_AVERAGE_COUNT)
											) {
										try {
											double value = Double.parseDouble(measurement.getValue());
											sum += value;
											if (value > highest) {
												highest = value;
												highestUnit = measurement.getUnit();
											}
											if (value < lowest) {
												lowest = value;
												lowestUnit = measurement.getUnit();
											}
											count++;
											measurementFound = true;
										}
										catch (NumberFormatException e) {
											// Ignore values that are not numeric
										}
									}
									else if (value == Covariate.VALUE_COUNT) {
										count++;
										measurementFound = true;
									}
								}
							}
						}
					}
					if (value == Covariate.VALUE_COUNT) {
						covariateValue = Integer.toString(count);
					}
					else if (value == Covariate.VALUE_PRESENT) {
						covariateValue = (count > 0 ? "1" : "0");
					}
					else if (value == Covariate.VALUE_NEAREST) {
						covariateValue = (measurementFound ? nearestMeasurement.getValue() : "");
					}
					else if (value == Covariate.VALUE_NEAREST_DISTANCE) {
						covariateValue = (measurementFound ? Integer.toString(minimumDistance) : "");
					}
					else if (value == Covariate.VALUE_NEAREST_UNIT) {
						covariateValue = (measurementFound ? nearestMeasurement.getUnit() : "");
					}
					else if (value == Covariate.VALUE_NEAREST_BEFORE) {
						covariateValue = (measurementFoundBefore ? nearestMeasurementBefore.getValue() : "");
					}
					else if (value == Covariate.VALUE_NEAREST_BEFORE_DISTANCE) {
						covariateValue = (measurementFoundBefore ? Integer.toString(minimumDistanceBefore) : "");
					}
					else if (value == Covariate.VALUE_NEAREST_BEFORE_UNIT) {
						covariateValue = (measurementFoundBefore ? nearestMeasurementBefore.getUnit() : "");
					}
					else if (value == Covariate.VALUE_NEAREST_AFTER) {
						covariateValue = (measurementFoundAfter ? nearestMeasurementAfter.getValue() : "");
					}
					else if (value == Covariate.VALUE_NEAREST_AFTER_DISTANCE) {
						covariateValue = (measurementFoundAfter ? Integer.toString(minimumDistanceAfter) : "");
					}
					else if (value == Covariate.VALUE_NEAREST_AFTER_UNIT) {
						covariateValue = (measurementFoundAfter ? nearestMeasurementAfter.getUnit() : "");
					}
					else if (value == Covariate.VALUE_NEAREST_BEFORE_AFTER) {
						covariateValue = (measurementFoundBefore ? nearestMeasurementBefore.getValue() : (measurementFoundAfter ? nearestMeasurementAfter.getValue() : ""));
					}
					else if (value == Covariate.VALUE_NEAREST_BEFORE_AFTER_DISTANCE) {
						covariateValue = (measurementFoundBefore ? Integer.toString(minimumDistanceBefore) : (measurementFoundAfter ? Integer.toString(minimumDistanceAfter) : ""));
					}
					else if (value == Covariate.VALUE_NEAREST_BEFORE_AFTER_UNIT) {
						covariateValue = (measurementFoundBefore ? nearestMeasurementBefore.getUnit() : (measurementFoundAfter ? nearestMeasurementAfter.getUnit() : ""));
					}
					else if (value == Covariate.VALUE_HIGHEST) {
						covariateValue = (measurementFound ? Double.toString(highest) : "");
					}
					else if (value == Covariate.VALUE_HIGHEST_UNIT) {
						covariateValue = (measurementFound ? highestUnit : "");
					}
					else if (value == Covariate.VALUE_LOWEST) {
						covariateValue = (measurementFound ? Double.toString(lowest) : "");
					}
					else if (value == Covariate.VALUE_LOWEST_UNIT) {
						covariateValue = (measurementFound ? lowestUnit : "");
					}
					else if (value == Covariate.VALUE_AVERAGE) {
						covariateValue = (measurementFound ? Double.toString(sum / count) : "");
					}
					else if ((value == Covariate.VALUE_AVERAGE_COUNT) || (value == Covariate.VALUE_COUNT)) {
						covariateValue = (measurementFound ? Integer.toString(count) : "");
					}
				}
			}
			return covariateValue;
		}


		public String getDefinition() {
			return definition;
		}


		public byte getWindowStartReference() {
			return windowStartReference;
		}


		public byte getWindowEndReference() {
			return windowEndReference;
		}


		public byte getValueReference() {
			return valueReference;
		}
	}


	/**
	 * Class to hold covariate information of a patient for efficiency.
	 * Should also be used for ExclusionCriteria.
	 */
	public class PatientCovariateInfo {
		private Map<String, List<Prescription>> prescriptionsOfInterest = new HashMap<String, List<Prescription>>();
		private Map<String, List<Event>> eventsOfInterest = new HashMap<String, List<Event>>();
		private Map<String, List<Measurement>> measurementsOfInterest = new HashMap<String, List<Measurement>>();


		public PatientCovariateInfo(Patient patient, Covariates covariates) {
			//TODO Which prescriptions do we use?
			//for (Prescription prescription : patient.getOriginalPrescriptions()) {
			for (Prescription prescription : patient.getPrescriptions()) {
				for (String covariateATC : covariates.getATCCovariates().keySet()) {
					List<Covariate> atcCovariates = covariates.getATCCovariates().get(covariateATC);
					for (Covariate atcCovariate : atcCovariates) {
						// When no ATC-codes are specified ATC-codes starting with an underscore are ignored.
						if ((covariateATC.equals("") && (!prescription.getATC().substring(0, 1).equals("_"))) || ((!covariateATC.equals("")) && prescription.startsWith(atcCovariate.getATCList()))) {
							List<Prescription> prescriptionList = prescriptionsOfInterest.get(covariateATC);
							if (prescriptionList == null) {
								prescriptionList = new ArrayList<Prescription>();
								prescriptionsOfInterest.put(covariateATC, prescriptionList);
							}
							if (!prescriptionList.contains(prescription)) {
								prescriptionList.add(prescription);
							}
						}
					}
				}
			}
			for (Event event : patient.getEvents()) {
				if (covariates.getEventCovariates().containsKey(event.getType())) {
					List<Event> eventList = eventsOfInterest.get(event.getType());
					if (eventList == null) {
						eventList = new ArrayList<Event>();
						eventsOfInterest.put(event.getType(), eventList);
					}
					eventList.add(event);
				}
			}
			for (Measurement measurement : patient.getMeasurements()) {
				if (covariates.getMeasurementCovariates().containsKey(measurement.getType())) {
					List<Measurement> measurementList = measurementsOfInterest.get(measurement.getType());
					if (measurementList == null) {
						measurementList = new ArrayList<Measurement>();
						measurementsOfInterest.put(measurement.getType(), measurementList);
					}
					measurementList.add(measurement);
				}
			}
		}


		public List<Prescription> getCovariatePrescriptions(String covariateATC) {
			return prescriptionsOfInterest.get(covariateATC);
		}


		public List<Event> getCovariateEvents(String covariateEventType) {
			return eventsOfInterest.get(covariateEventType);
		}


		public List<Measurement> getCovariateMeasurements(String covariateMeasurementType) {
			return measurementsOfInterest.get(covariateMeasurementType);
		}
	}
}
