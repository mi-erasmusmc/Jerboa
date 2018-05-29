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

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.erasmusmc.jerboa.dataClasses.Patient;



/**
 * This class can be used to define exclusion criteria and to compute the result of the OR of the exclusion
 * criteria.
 * The exclusion criteria can be defined by creating an instance while passing the list of exclusion criteria
 * definitions to the constructor:
 *
 *   ExclusionCriteria(List<String> exclusionDefinitions)
 *
 * Before retrieving the result of the exclusion criteria an instance of the class PatientCovariateInfo
 * should be created by calling the constructor:
 *
 *   PatientCovariateInfo(Patient patient, Covariates covariates)
 *
 * where the parameter covariates is the instance created with the previous call.
 * After that the result of the exclusion criteria can be retrieved with the method:
 *
 *   excludePatient(Patient patient, int indexDate, PatientCovariateInfo covariateInfo)
 *
 * It returns true if one of the exclusion criteria is true, otherwise false.
 *
 * This list of exclusion criteria definitions may contain three types of exclusion criteria:
 *
 * ATC exclusion criteria:
 *
 * ATC codes to use as exclusion criteria for the cases and controls with their
 * windows during which exposure to drugs with the specified ATC's will be measured.
 * The format is:
 *
 * ATC;atc;label;window start reference;window start;window end reference;window end;value reference;value reference offset;value;criterion1;crierion2
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
 *                               DaysSinceStart       : The number of days since start until the value reference.
 *                               DaysSinceUse         : The number of days since use until the value reference.
 *
 *   criterion1                Possible values are:
 *                               =                    : If the value resulting from the definition in the previous fields is equal to
 *                                                      the value in criterion2, the patient is excluded.
 *                               <>                   : If the value resulting from the definition in the previous fields is not equal to
 *                                                      the value in criterion2, the patient is excluded.
 *                               <minimum>            : If the value resulting from the definition in the previous fields is greater or
 *                                                      equal to the value in criterion1 an less or equal to the value in criterion2 the
 *                                                      patient is excluded. If criterion1 is empty there is no minimum value.
 *   criterion2                Possible values are:
 *                               <value/maximum>      : This field contains either the comparison value for criterion1 or the maximum value
 *                                                      for the range test. In case it is used as a maximum value and if it is empty there is
 *                                                      no maximum value.
 *
 * Example: R03AC01;;Index;-30;Index;0;Index;DaysSinceUse;15;20
 *
 *
 * Event exclusion criteria:
 *
 * Events to use as exclusion criteria for the cases and controls with their
 * windows during which exposure to a events of the specified types will be measured.
 * The format is:
 *
 * EVENT;event type;label;window start reference;window start;window end reference;window end;value reference;value reference offset;value;criterion1;crierion2
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
 *   criterion1                Possible values are:
 *                               =                    : If the value resulting from the definition in the previous fields is equal to
 *                                                      the value in criterion2, the patient is excluded.
 *                               <>                   : If the value resulting from the definition in the previous fields is not equal to
 *                                                      the value in criterion2, the patient is excluded.
 *                               <minimum>            : If the value resulting from the definition in the previous fields is greater or
 *                                                      equal to the value in criterion1 an less or equal to the value in criterion2 the
 *                                                      patient is excluded. If criterion1 is empty there is no minimum value.
 *   criterion2                Possible values are:
 *                               <value/maximum>      : This field contains either the comparison value for criterion1 or the maximum value
 *                                                      for the range test. In case it is used as a maximum value and if it is empty there is
 *                                                      no maximum value.
 *
 * Example: MI;;index;-30;Index;0;;Incident;=;0
 *
 *
 * Measurement exclusion criteria:
 *
 * List of measurements to use as exclusion criteria for the cases and controls with their
 * windows during which measurements of the specified types will be measured.
 * The format is:
 *
 * MEASUREMENT;measurement type,measurement value 1,...,measurement value N;label;window start reference;window start;window end reference;window end;value reference;value reference offset;value;criterion1;crierion2
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
 *                               Index                : Index date (time of event)
 *                               StartOfPopulation    : Population start date
 *                               EndOfPopulation      : Population end date
 *                               StartOfCohort        : Cohort start date
 *                               EndOfCohort          : Cohort end date
 *                               Age                  : The date the patient becomes the age in years specified in window start
 *
 *   window start              The start of the time window as number of days relative to window start reference in
 *                             which the measurement should be present.
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
 *                             which the measurement should be present.
 *                             May be empty indicating all time after.
 *   value reference           The point of reference for the value Nearest, NearestDistance, NearestBefore,
 *                             NearestBeforeDistance, NearestAfter, or NearestAfterDistance. Possible values are:
 *
 *                               Index                : Index date (time of event)
 *                               StartOfPopulation    : Population start date
 *                               EndOfPopulation      : Population end date
 *                               StartOfCohort        : Cohort start date
 *                               EndOfCohort          : Cohort end date
 *
 *                             If the value is not one of Nearest, NearestDistance, NearestBefore, NearestBeforeDistance,
 *                             NearestAfter, or NearestAfterDistance it should be empty.
 *   value reference offset    The offset to the value reference.
 *   value                     The value that has to be written to the file. Possible values are:
 *
 *                               Count                : The number of measurements within the time window.
 *                               Present              : 0 if there are no measurements within the time window, otherwise 1.
 *                               Nearest              : The value of the measurement that is nearest in time to the value reference.
 *                               NearestDistance      : The distance in time of the measurement that is nearest in time to the value reference.
 *                               NearestBefore        : The value of the measurement that is nearest in time to the value reference.
 *                               NearestBeforeDistance: The distance in time of the measurement that is nearest in time to the value reference.
 *                               NearestAfter         : The value of the measurement that is nearest in time to the value reference.
 *                               NearestAfterDistance : The distance in time of the measurement that is nearest in time to the value reference.
 *                               Highest              : The highest measurement value of the measurements with a valid numerical value within the time window.
 *                               Lowest               : The lowest measurement value of the measurements with a valid numerical value within the time window.
 *                               Average              : The average value of the measurements with a valid numerical value within the time window.
 *                               AverageCount         : The number of measurements within the time window with a valid numerical value.
 *
 *   criterion1                Possible values are:
 *                               =                    : If the value resulting from the definition in the previous fields is equal to
 *                                                      the value in criterion2, the patient is excluded.
 *                               <>                   : If the value resulting from the definition in the previous fields is not equal to
 *                                                      the value in criterion2, the patient is excluded.
 *                               <minimum>            : If the value resulting from the definition in the previous fields is greater or
 *                                                      equal to the value in criterion1 an less or equal to the value in criterion2 the
 *                                                      patient is excluded. If criterion1 is empty there is no minimum value.
 *   criterion2                Possible values are:
 *                               <value/maximum>      : This field contains either the comparison value for criterion1 or the maximum value
 *                                                      for the range test. In case it is used as a maximum value and if it is empty there is
 *                                                      no maximum value.
 *
 * Example: BMI;;Index;-30;Index;0;Index;NearestBefore;25;
 *
 * There is one special measurement exclusion criterion for the GARVAN risk score. It can be defined as follows:
 *
 *   MEASUREMENT;GARVAN;label;period start reference;period start offset;value reference;value reference offset;;estimation period;10;                      GARVAN risk based on weight and no hip fractures
 *   MEASUREMENT;GARVAN,BMD;label;period start reference;period start offset;value reference;value reference offset;;estimation period;10;                  GARVAN risk based on BMD and no hip fractures
 *   MEASUREMENT;GARVAN,HIPFRACTURES;label;period start reference;period start offset;value reference;value reference offset;;estimation period;10;         GARVAN risk based on weight with hip fractures
 *   MEASUREMENT;GARVAN,BMD,HIPFRACTURES;label;period start reference;period start offset;value reference;value reference offset;;estimation period;10;     GARVAN risk based on BMD with hip fractures
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
public class ExclusionCriteria extends Covariates {
	private Map<Covariate, String> criteria1 = new HashMap<Covariate, String>();
	private Map<Covariate, String> criteria2 = new HashMap<Covariate, String>();


	public ExclusionCriteria(List<String> exclusionDefinitions) {
		super(exclusionDefinitions, "exclusion criterion");
		if (isOK) {
			for (int exclusionDefinitionNr = 0; exclusionDefinitionNr < exclusionDefinitions.size(); exclusionDefinitionNr++) {
				String exclusionDefinition = exclusionDefinitions.get(exclusionDefinitionNr);
				String[] exclusionDefinitionSplit = exclusionDefinition.split(";");
				if (exclusionDefinitionSplit.length == 12) {
					String criterion1 = exclusionDefinitionSplit[10];
					String criterion2 = exclusionDefinitionSplit[11];

					if ((!criterion1.equals("=") && (!criterion1.equals("<>")))) {
						if (!criterion1.equals("")) {
							try {
								Double.parseDouble(criterion1);
							}
							catch (NumberFormatException e) {
								Logging.add("Error in minimum of exclusion criterion definition: " + exclusionDefinition);
								isOK = false;
							}
						}
						if (!criterion2.equals("")) {
							try {
								Double.parseDouble(criterion2);
							}
							catch (NumberFormatException e) {
								Logging.add("Error in maximum of exclusion criterion definition: " + exclusionDefinition);
								isOK = false;
							}
						}
						if (criterion1.equals("") && criterion2.equals("")) {
							Logging.add("Error in minimum and maximum (both empty) of exclusion criterion definition: " + exclusionDefinition);
							isOK = false;
						}
					}

					criteria1.put(covariates.get(exclusionDefinitionNr), criterion1);
					criteria2.put(covariates.get(exclusionDefinitionNr), criterion2);
				}
				else {
					Logging.add("Not the correct number (12) of parameters specified in exclusion criterion definition: " + exclusionDefinition);
					isOK = false;
				}
			}
		}
	}

	/*
	public String getHeader() {
		String header = "";
		for (int covariateNr = 0; covariateNr < covariates.size(); covariateNr++) {
			Covariate covariate = covariates.get(covariateNr);
			header += "," + covariate.getDescription();
			String criterion1 = criteria1.get(covariate);
			String criterion2 = criteria2.get(covariate);
			if ((!criterion1.equals("=")) && (!criterion1.equals("<>"))) {
				if (criterion1.equals("")) {
					header += "_less_or equal_to_" + criterion2;
				}
				else if (criterion2.equals("")) {
					header += "_greater_or_equal_to_" + criterion2;
				}
				else {
					header += "_from_" + criterion1 + "_to_" + criterion2;
				}
			}
			else {
				header += (criterion1.equals("=") ? "_equal_to_" : "_not_equal_to_") + criterion2;
			}
		}
		return header;
	}
	*/

	public boolean excludePatient(Patient patient, int indexDate, int cohortStartDate, int cohortEndDate, PatientCovariateInfo covariateInfo) {
		boolean exclude = false;
		getCovariateValues(patient, indexDate, cohortStartDate, cohortEndDate, covariateInfo);
		for (Covariate covariate : covariates) {
			String value = covariateValues.get(covariate);
			String criterion1 = criteria1.get(covariate);
			String criterion2 = criteria2.get(covariate);
			if (criterion1.equals("=")) {
				exclude = exclude || value.equals(criterion2);
			}
			else if (criterion1.equals("<>")) {
				exclude = exclude || (!value.equals(criterion2));
			}
			else if (criterion1.equals("")) {
				exclude = exclude || (Double.parseDouble(value) <= Double.parseDouble(criterion2));
			}
			else if (criterion2.equals("")) {
				exclude = exclude || (Double.parseDouble(value) >= Double.parseDouble(criterion1));
			}
			else {
				exclude = exclude || ((Double.parseDouble(value) >= Double.parseDouble(criterion1)) && (Double.parseDouble(value) <= Double.parseDouble(criterion2)));
			}
			if (exclude) {
				break;
			}
		}
		return exclude;
	}
}
