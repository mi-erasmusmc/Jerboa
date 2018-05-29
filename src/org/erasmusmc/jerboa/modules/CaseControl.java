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

package org.erasmusmc.jerboa.modules;

import java.security.InvalidParameterException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.erasmusmc.jerboa.Jerboa;
import org.erasmusmc.jerboa.config.DataDefinition;
import org.erasmusmc.jerboa.config.FilePaths;
import org.erasmusmc.jerboa.config.Parameters;
import org.erasmusmc.jerboa.dataClasses.Event;
import org.erasmusmc.jerboa.dataClasses.Patient;
import org.erasmusmc.jerboa.dataClasses.Prescription;
import org.erasmusmc.jerboa.engine.Module;
import org.erasmusmc.jerboa.utilities.Covariates;
import org.erasmusmc.jerboa.utilities.Covariates.PatientCovariateInfo;
import org.erasmusmc.jerboa.utilities.DateUtilities;
import org.erasmusmc.jerboa.utilities.ExclusionCriteria;
import org.erasmusmc.jerboa.utilities.Item;
import org.erasmusmc.jerboa.utilities.ItemList;
import org.erasmusmc.jerboa.utilities.Logging;
import org.erasmusmc.jerboa.utilities.MemoryUtilities;
import org.erasmusmc.jerboa.utilities.TimeUtilities;

/**
 * This module creates a case/control sets base on the specified matching criteria.
 * Additional covariates may be added to the output.
 * @author bmosseveld
 *
 */

public class CaseControl extends Module {

	/* GENERAL PARAMETERS */

	/**
	 * If true, the controls will be sampled randomly.
	 */
	public boolean randomSample;

	/**
	 * Flag if multiple cohorts should be used.
	 */
	public boolean useMultipleCohorts = false;


	/* MATCHING PARAMETERS */

	/**
	 * List of events to assess for the cases and controls.
	 */
	public List<String> eventsOfInterest = new ArrayList<String>();

	/**
	 * List of ATCs that should be considered in the drugCount and the module output.
	 */
	public List<String> atcsOfInterest = new ArrayList<String>();

	/**
	 * Specifies if only the first event of a type should be considered for a patient to be a case.
	 */
	public boolean firstEventOnly;

	/**
	 * Specifies the maximum number of controls to be sampled per case.
	 * If -1 then all all potential controls (for testing).
	 */
	public int controlsPerCase;

	/**
	 * Specifies if a control may become a case.
	 * When true a patient can only be a control if he never becomes a case.
	 */
	public boolean controlsNeverCase;

	/**
	 * Specifies whether controls should have the same gender as the case.
	 */
	public boolean matchOnPracticeID;

	/**
	 * Specifies whether controls should have the same gender as the case.
	 */
	public boolean matchOnGender;

	/**
	 * Maximum number of days difference in date of birth between case and control.
	 * If -1 then no matching on birthdate is required.
	 * If -2 then case and control should match on birth year.
	 * If -3 then case and control should match on birth year and month.
	 */
	public int maxDifferenceBetweenBirthDates;

	/**
	 * Maximum number of days or percentage difference in days in cohort date at index date.
	 * Format:
	 *
	 *   <N>;<Unit>
	 *
	 *   N       A number. If -1 then no matching on time in cohort is required.
	 *   <Unit>  Days or Percentage
	 *
	 * Examples:
	 *
	 *   -1               No matching on cohort time.
	 *   -1;              No matching on cohort time.
	 *   365;Days         Cohort time control = cohort time case +/- 365 days
	 *   25;Percent       Cohort time control = cohort time case +/- 25 percent
	 */
	public String maxDifferenceInTimeInCohort;
	private final byte MATCH_COHORT_TIME_DAYS       = 0;
	private final byte MATCH_COHORT_TIME_PERCENTAGE = 1;

	/**
	 * Specifies whether controls should have the same drug count as the case.
	 * If emtpty no matching on drug count is performed, otherwise it should be:
	 *
	 * <start window>;<end window>
	 *
	 * specifying the time window with respect to the index date where to count different ATC's
	 * Examples:
	 *
	 *   matchOnDrugCount = ;        -> from ever before till ever after index date
	 *   matchOnDrugCount = -365;30  -> from 365 days before the index date till 30 days after the index date
	 */
	public String matchOnDrugCount;

	/**
	 * Specifies whether controls should be matched on an external caseset ID included in the events file.
	 * If empty no matching on external caseset ID is done, otherwise the value is taken as the prefix of
	 * the caseset ID that is in the event type.
	 */
	public String matchOnExternalCaseSetId;

	/**
	 * Specifies whether controls should be matched on exposure to the same ATC class.
	 * If emtpty no matching on ATC class is performed, otherwise it should be:
	 *
	 * <start window>;<end window>;<ATC level>
	 *
	 * specifying the time window with respect to the index date where to check for the ATC classes
	 * Examples:
	 *
	 *   matchOnATCClass = ;;4        -> from ever before till ever after index date on level 4 ATC
	 *   matchOnATCClass = -365;30;7  -> from 365 days before the index date till 30 days after the index date on level 7 ATC
	 */
	public String matchOnATCClass;

	/**
	 * List of events that are checked for their number of appearances in the specified period around the index date
	 * multiple occurrences are ignored
	 * Format: EventType;;Label;Description
	 *
	 *   EventType        - the variable name used in the event file
	 *   Label            - the text used in the result tables
	 *   Description      - description
	 *   MinCount         - the minimum required number of events in the specified window
	 *   MaxCount         - the maximum required number of events in the specified window
	 *   CountDifference  - match on the number of events with a maximum difference of CountDifference
	 *   TimewindowStart  - start of time window before index date. Start day is not included. -1 = All history
	 *   TimewindowEnd    - end of time window relative to index date. End day is not included. cohortEnd = end of cohort
	 *
	 * For example: "MI;MI1;Myocardial Infarction;;;;-365"                  : Look for the presence of MI events in the window  indexDate-365< X <=indexDate
	 *              "MI;MI2;Myocardial Infarction;;;;-365;10"               : Look for the presence of 1 to 3 MI events in the window  indexDate-365< X <indexDate + 10
	 *              "MI;MI3;Myocardial Infarction;;;;-365;cohortEnd"        : Look for the presence of MI events in the window  indexDate-365< X <cohortEnd
	 *              "MI;MI4;Myocardial Infarction;;;;;cohortEnd"            : Look for the presence of MI events in the window  indexDate<= X <cohortEnd
	 *              "MI;MI5;Myocardial Infarction;;;;cohortStart;cohortEnd" : Look for the presence of MI events in the window  cohortStart<= X <cohortEnd
	 *              "MI;MI6;Myocardial Infarction;1;10;5;-365"              : Look for the 1 to 10 MI events in the window  indexDate-365< X <=indexDate  with a maximum difference of 5
	 *              "MI;MI7;Myocardial Infarction;1;10;5;-365;10"           : Look for the 1 to 10 of MI events in the window  indexDate-365< X <indexDate + 10  with a maximum difference of 5
	 *              "MI;MI8;Myocardial Infarction;1;10;5;-365;cohortEnd"    : Look for the 1 to 10 of MI events in the window  indexDate-365< X <cohortEnd  with a maximum difference of 5
	 */
	public List<String> matchOnEventCounts = new ArrayList<String>();
	private final byte MATCH_EVENT_COUNT_PARAMETER_MINIMUM      = 0;
	private final byte MATCH_EVENT_COUNT_PARAMETER_MAXIMUM      = 1;
	private final byte MATCH_EVENT_COUNT_PARAMETER_DIFFERENCE   = 2;
	private final byte MATCH_EVENT_COUNT_PARAMETER_WINDOW_START = 3;
	private final byte MATCH_EVENT_COUNT_PARAMETER_WINDOW_END   = 4;


	/* EXCLUSION CRITERIA */

	/**
	 * List of exclusion criteria for the cases and controls. There are three types of exclusion criteria:
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
	 *                               CountATC             : The number of different ATC's the patient is exposed to within the time window. Incomplete ATC codes are ignored!
	 *                               Present              : 0 if there are no prescription starts of the drugs within the time window, otherwise 1.
	 *                               DaysOfUse            : The number of days the drug is used within the window.
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
	 *                                                      for the range test. In case it is used as a maximum value and it is empty there is
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
	 *   value reference           The point of reference for the value TimToIncident. Possible values are:
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
	 *                                                      for the range test. In case it is used as a maximum value and it is empty there is
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
	 *                               Count                     : The number of measurements within the time window.
	 *                               Present                   : 0 if there are no measurements within the time window, otherwise 1.
	 *                               Nearest                   : The value of the measurement that is nearest in time to the value reference.
	 *                               NearestDistance           : The distance in time of the measurement that is nearest in time to the value reference.
	 *                               NearestBefore             : The value of the measurement that is nearest in time before the value reference.
	 *                               NearestBeforeDistance     : The distance in time of the measurement that is nearest in time before the value reference.
	 *                               NearestAfter              : The value of the measurement that is nearest in time after the value reference.
	 *                               NearestAfterDistance      : The distance in time of the measurement that is nearest in time after the value reference.
	 *                               NearestBeforeAfter        : The value of the measurement that is nearest in time before or if no measurement before the nearest in time after the value reference.
	 *                               NearestBeforeAfterDistance: The distance in time of the measurement that is nearest in time before or if no measurement before the nearest in time after the value reference.
	 *                               Highest                   : The highest measurement value of the measurements with a valid numerical value within the time window.
	 *                               HighestUnit               : The unit of the highest measurement value of the measurements with a valid numerical value within the time window.
	 *                               Lowest                    : The lowest measurement value of the measurements with a valid numerical value within the time window.
	 *                               LowestUnit                : The unit of the lowest measurement value of the measurements with a valid numerical value within the time window.
	 *                               Average                   : The average value of the measurements with a valid numerical value within the time window.
	 *                               AverageCount              : The number of measurements within the time window with a valid numerical value.
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
	 *                                                      for the range test. In case it is used as a maximum value and it is empty there is
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
	 *
	 * Multiple exclusion criteria can be defined.
	 */
	public List<String> exclusionCriteria = new ArrayList<String>();


	/* COVARIATES */

	/**
	 * List of covariates to assess for the cases and controls. There are three types of covariates:
	 *
	 * ATC covariates:
	 *
	 * ATC codes to assess as covariates for the cases and controls with their
	 * windows during which exposure to drugs with the specified ATC's will be measured.
	 * The format is:
	 *
	 * ATC;atc;label;window start reference;window start;window end reference;window end;value reference;value reference offset;value
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
	 *                               CountATC             : The number of different ATC's the patient is exposed to within the time window. Incomplete ATC codes are ignored!
	 *                               Present              : 0 if there are no prescription starts of the drugs within the time window, otherwise 1.
	 *                               DaysOfUse            : The number of days the drug is used within the window.
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
	 *   value reference           The point of reference for the value TimToIncident. Possible values are:
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
	 *                               Lowest                    : The lowest measurement value of the measurements with a valid numerical value within the time window.
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
	 *
	 * Multiple covariates can be defined.
	 */
	public List<String> covariates = new ArrayList<String>();


	/* OUTPUT PARAMETERS */

	/**
	 * Output the sequence number of the cohort.
	 */
	public boolean outputCohortSequenceNr;

	/**
	 * Output the type of the cohort.
	 */
	public boolean outputCohortType;

	/**
	 * Output the calendar year of the cohort start date.
	 */
	public boolean outputCohortYear;

	/**
	 * Output the calendar month of the cohort start date.
	 */
	public boolean outputCohortMonth;

	/**
	 * Output the calendar year of the index date.
	 */
	public boolean outputYear;

	/**
	 * Output the calendar month of the index date.
	 */
	public boolean outputMonth;

	/**
	 * Output the gender of the cases and controls.
	 */
	public boolean outputGender;

	/**
	 * Output the age of the cases and controls at index date.
	 */
	public boolean outputAge;

	/**
	 * Output the age of the cases and controls at start of cohort.
	 */
	public boolean outputAgeAtCohortStart;

	/**
	 * Output the follow-up time before the event.
	 */
	public boolean outputFollowUpBeforeEvent;

	/**
	 * Output the follow-up time after the event.
	 */
	public boolean outputFollowUpAfterEvent;

	/**
	 * Output the drug counts for the cases and controls.
	 */
	public boolean outputDrugCount;

	/**
	 * Output the time the patient has been in the cohort at index date.
	 */
	public boolean outputTimeInCohort;

	/**
	 * Output the exclusion values that are used in the exclusion criteria.
	 */
	public boolean outputExclusionCriteria;

	/**
	 * If set to true, the real patient IDs, and if available the real practice IDs, will be included in the output. Intended for debugging!
	 * Otherwise, just sequence number will be output as patient ID and practice ID.
	 */
	public boolean outputPatientID;

	/**
	 * If true an attrition filename will be created or appended on called Attrition.csv
	 */
	public boolean attritionFile;



	// Counter for case patterns that also serves as caseset ID.
	private int casePatternCount;

	// List to contain the case patterns found.
	private List<CasePattern> casePatterns = new ArrayList<CasePattern>();

	// Set of all case patient ID's.
	// Only used when controlsNeverCase = true
	private Set<String> caseIDs;

	// Match on drug count window definition
	private int matchOnDrugCountStart = 0;
	private int matchOnDrugCountEnd   = 0;

	// Match on ATC class window and level definition
	private int matchOnATCClassStart = 0;
	private int matchOnATCClassEnd   = 0;
	private int matchOnATCClassLevel = 0;

	// Match on cohort time
	private int matchCohortTimeCount = -1;
	private byte matchCohortTimeUnit = MATCH_COHORT_TIME_DAYS;

	// Item lists for match on events
	private ItemList matchOnEventCountsList = new ItemList(true,0);

	// Exclusion criteria
	private ExclusionCriteria exclusionCriteriaDefinitions;

	// Covariates
	private Covariates covariateDefinitions;

	// Counters
	private List<String> exclusionKeys;
	private String eventOfInterestDescription;
	private int originalCount;
	private Map<String, Integer> exclusionCounters;
	private Map<String, Integer> totalCases;
	private Map<String, Integer> totalControls;

	private String attritionFileName;   //used to save the attrition

	// For debugging only
	private boolean postProcessStarted;
	private int patientNr;
	private int subPatientNr;
	private int displayLimit = 10000;

	// Filenames
	String statsFileName;


	@Override
	public boolean init() {
		boolean initOK = true;

		setPostProcessingNeeded(true);
		setShufflePatients(randomSample);

		// Initialize the case pattern count.
		casePatternCount = 0;

		if (controlsNeverCase) {
			caseIDs = new HashSet<String>();
		}

		// Interpret the matchOnDrugCount parameter.
		if (!matchOnDrugCount.equals("")) {
			String[] matchOnDrugCountSplit = matchOnDrugCount.split(";");
			if (matchOnDrugCountSplit[0].equals("")) {
				matchOnDrugCountStart = Integer.MIN_VALUE;
			}
			else {
				try {
					matchOnDrugCountStart = Integer.parseInt(matchOnDrugCountSplit[0]);
				} catch (NumberFormatException e) {
					Logging.add("Error in matchOnDrugCount window start!");
					initOK = false;
				}
			}
			if (matchOnDrugCountSplit[1].equals("")) {
				matchOnDrugCountEnd = Integer.MAX_VALUE;
			}
			else {
				try {
					matchOnDrugCountEnd = Integer.parseInt(matchOnDrugCountSplit[1]);
				} catch (NumberFormatException e) {
					Logging.add("Error in matchOnDrugCount window end!");
					initOK = false;
				}
			}
		}

		// Interpret the matchOnATCClass parameter.
		if (!matchOnATCClass.equals("")) {
			String[] matchOnATCClassSplit = matchOnATCClass.split(";");
			if (matchOnATCClassSplit.length >= 3) {
				if (matchOnATCClassSplit[0].equals("")) {
					matchOnATCClassStart = Integer.MIN_VALUE;
				}
				else {
					try {
						matchOnATCClassStart = Integer.parseInt(matchOnATCClassSplit[0]);
					} catch (NumberFormatException e) {
						Logging.add("Error in matchOnATCClass window start!");
						initOK = false;
					}
				}
				if (matchOnATCClassSplit[1].equals("")) {
					matchOnATCClassEnd = Integer.MAX_VALUE;
				}
				else {
					try {
						matchOnATCClassEnd = Integer.parseInt(matchOnATCClassSplit[1]);
					} catch (NumberFormatException e) {
						Logging.add("Error in matchOnATCClass window end!");
						initOK = false;
					}
				}
				try {
					matchOnATCClassLevel = Integer.parseInt(matchOnATCClassSplit[2]);
					if ((matchOnATCClassLevel < 1) || (matchOnATCClassLevel > 7)) {
						Logging.add("Error in matchOnATCClass ATC level. Should be in range 1-7.");
						initOK = false;
					}
				} catch (NumberFormatException e) {
					Logging.add("Error in matchOnATCClass ATC level!");
					initOK = false;
				}
			}
			else {
				Logging.add("Not enough (3) parameters specified in matchOnATCClass!");
				initOK = false;
			}
		}

		// Parse module parameters to item lists
		try {
			matchOnEventCountsList.parse(matchOnEventCounts);
		}
		catch (InvalidParameterException e) {
			Logging.add(e.getMessage());
			initOK = false;
		}

		// Match on cohort time
		String[] matchOnCohortTimeSplit = maxDifferenceInTimeInCohort.split(";");
		try {
			matchCohortTimeCount = Integer.parseInt(matchOnCohortTimeSplit[0]);
		}
		catch (NumberFormatException e) {
			Logging.add("Error in count maxDifferenceInTimeInCohort!");
			initOK = false;
		}
		if (initOK && (matchCohortTimeCount != -1) && (matchOnCohortTimeSplit.length > 1)) {
			if (matchOnCohortTimeSplit[1].equals("DAYS")) {
				matchCohortTimeUnit = MATCH_COHORT_TIME_DAYS;
			}
			else if (matchOnCohortTimeSplit[1].equals("PERCENT")) {
				matchCohortTimeUnit = MATCH_COHORT_TIME_PERCENTAGE;
			}
			else {
				Logging.add("Error in unit maxDifferenceInTimeInCohort!");
				initOK = false;
			}
		}

		// Check and interpret the exclusion criteria definitions
		exclusionCriteriaDefinitions = new ExclusionCriteria(exclusionCriteria);
		initOK = initOK && exclusionCriteriaDefinitions.isOK;

		// Check and interpret the covariate definitions
		covariateDefinitions = new Covariates(covariates);
		initOK = initOK && covariateDefinitions.isOK;

		// Create output files
		if (initOK) {
			initOK = initOK && Jerboa.getOutputManager().addFile(outputFileName, 100);
			String header = "Database";
			header += "," + "CaseSetID";
			header += "," + "EventType";
			header += "," + "IsCase";
			if (matchOnPracticeID)						header += "," + "PracticeID";
														header += "," + "PatientID";
			if (outputAge)								header += "," + "Age";
			if (outputGender)							header += "," + "Gender";
			if (outputCohortType)						header += "," + "CohortType";
			if (outputCohortSequenceNr)					header += "," + "CohortSequence";
			if (outputCohortMonth)						header += "," + "CohortMonth";
			if (outputCohortYear)						header += "," + "CohortYear";
			if (outputMonth)							header += "," + "IndexMonth";
			if (outputYear)								header += "," + "IndexYear";
			if (outputDrugCount)						header += "," + "DrugCount";
			if (outputTimeInCohort)						header += "," + "DaysInCohort";
			if (outputAgeAtCohortStart)					header += "," + "AgeAtCohortEntry";
			if (outputFollowUpBeforeEvent)				header += "," + "FollowUpPre";
			if (outputFollowUpAfterEvent)				header += "," + "FollowUpPost";

			for (Item matchOnEventCount : matchOnEventCountsList.getItemList()) {
				header += "," + (matchOnEventCount.getLabel().equals("") ? (matchOnEventCount.getDescription().equals("") ? matchOnEventCount : matchOnEventCount.getDescription()) : matchOnEventCount.getLabel());
			}

			if (outputExclusionCriteria) {
				header += exclusionCriteriaDefinitions.getHeader();
			}

			header += covariateDefinitions.getHeader();

			Jerboa.getOutputManager().writeln(outputFileName, header, false);
		}

		if (initOK) {
			statsFileName = outputFileName.substring(0, outputFileName.length() - 4) + "_Stats.csv";
			initOK = initOK && Jerboa.getOutputManager().addFile(statsFileName, 100);
			Jerboa.getOutputManager().writeln(statsFileName, "EventType,Cases,Controls,Average controls per case", false);
		}

		if (initOK && intermediateFiles) {
			initOK &= Jerboa.getOutputManager().addFile(this.intermediateFileName);
			Jerboa.getOutputManager().writeln(this.intermediateFileName,"SubsetID,PatientID,BirthDate,Gender,StartDate,EndDate,PopulationStart,PopulationEnd,CohortStart,CohortEnd,IsCase,Exclusion", false);
		}

		if (initOK && attritionFile && (!Jerboa.unitTest)) {
			attritionFileName = FilePaths.WORKFLOW_PATH+this.title+"/"+
					Parameters.DATABASE_NAME+"_"+this.title+
					"_"+TimeUtilities.TIME_STAMP+"_"+Parameters.VERSION+"_attrition.csv";
			Jerboa.getOutputManager().addFile(attritionFileName, 100);
		}

		// Initialize counters
		originalCount = 0;
		exclusionKeys = new ArrayList<String>();
		exclusionCounters = new HashMap<String, Integer>();
		eventOfInterestDescription = (firstEventOnly ? "incident " : "") + "event of interest (";
		for (int eventNr = 0; eventNr < eventsOfInterest.size(); eventNr++) {
			eventOfInterestDescription += (eventNr > 0 ? ", " : "") + eventsOfInterest.get(eventNr);
		}
		eventOfInterestDescription += ") inside cohort time";
		exclusionKeys.add(eventOfInterestDescription);
		exclusionCounters.put(eventOfInterestDescription, 0);
		totalCases    = new HashMap<String, Integer>();
		totalControls = new HashMap<String, Integer>();
		for (String eventType : eventsOfInterest) {
			for (Item matchOnEventCount : matchOnEventCountsList) {
				if (!matchOnEventCount.getParameters().get(MATCH_EVENT_COUNT_PARAMETER_MINIMUM).equals("")) {
					String eventDescription = getExclusionNotEnoughEvents(matchOnEventCount.getDescription(), Integer.parseInt(matchOnEventCount.getParameters().get(MATCH_EVENT_COUNT_PARAMETER_MINIMUM)));
					exclusionKeys.add(eventDescription);
					exclusionCounters.put(eventDescription, 0);
				}
				if (!matchOnEventCount.getParameters().get(MATCH_EVENT_COUNT_PARAMETER_MAXIMUM).equals("")) {
					String eventDescription = getExclusionTooManyEvents(matchOnEventCount.getDescription(), Integer.parseInt(matchOnEventCount.getParameters().get(MATCH_EVENT_COUNT_PARAMETER_MAXIMUM)));
					exclusionKeys.add(eventDescription);
					exclusionCounters.put(eventDescription, 0);
				}
			}
			totalCases.put(eventType, 0);
			totalControls.put(eventType, 0);
		}

		// For debugging only
		if (Jerboa.isInDebugMode) {
			postProcessStarted = false;
			patientNr = -1;
			subPatientNr = -1;
		}

		return initOK;
	}


	@Override
	public Patient process(Patient patient) {

		if (patient.isInPopulation()) {
			if (useMultipleCohorts) {
				if (patient.getCohorts().size() > 0) {
					saveCases(patient, patient.getCohorts().get(0).getCohortStartDate(), patient.getCohorts().get(patient.getCohorts().size() - 1).getCohortEndDate());
				}
			}
			else {
				if (patient.isInCohort()) {
					saveCases(patient, patient.getCohortStartDate(), patient.getCohortEndDate());
				}
			}

			// For debugging only
			if (Jerboa.isInDebugMode) {
				if (subPatientNr == -1) {
					patientNr = 0;
					subPatientNr = 0;
					Logging.add("Phase,Patients,Cases,Maximum,Allocated,Free,Used");
					Logging.add("ProcessStart," + Integer.toString(patientNr) + "," + Integer.toString(casePatterns.size()) + "," + Long.toString(MemoryUtilities.getMaxMemory()) + "," + Long.toString(MemoryUtilities.getAllocatedMemory()) + "," + Long.toString(MemoryUtilities.getFreeMemory()) + "," + Long.toString(MemoryUtilities.getUsedMemory()));
				}
				patientNr++;
				subPatientNr++;
				if (subPatientNr == displayLimit) {
					subPatientNr = 0;
					Logging.add("Process," + Integer.toString(patientNr) + "," + Integer.toString(casePatterns.size()) + "," + Long.toString(MemoryUtilities.getMaxMemory()) + "," + Long.toString(MemoryUtilities.getAllocatedMemory()) + "," + Long.toString(MemoryUtilities.getFreeMemory()) + "," + Long.toString(MemoryUtilities.getUsedMemory()));
				}
			}
		}

		return patient;
	}


	@Override
	public Patient postProcess(Patient patient) {

		if ((!(controlsNeverCase && caseIDs.contains(patient.getPatientID()))) && patient.isInPopulation()) {
			if (useMultipleCohorts) {
				if (patient.getCohorts().size() > 0) {
					saveControls(patient, patient.getCohorts().get(0).getCohortStartDate(), patient.getCohorts().get(patient.getCohorts().size() - 1).getCohortEndDate());
				}
			}
			else {
				if (patient.isInCohort()) {
					saveControls(patient, patient.getCohortStartDate(), patient.getCohortEndDate());
				}
			}

			// For debugging only
			if (Jerboa.isInDebugMode) {
				if (!postProcessStarted) {
					postProcessStarted = true;
					patientNr = 0;
					subPatientNr = 0;
					Logging.add("PostProcessStart," + Integer.toString(patientNr) + "," + Integer.toString(casePatterns.size()) + "," + Long.toString(MemoryUtilities.getMaxMemory()) + "," + Long.toString(MemoryUtilities.getAllocatedMemory()) + "," + Long.toString(MemoryUtilities.getFreeMemory()) + "," + Long.toString(MemoryUtilities.getUsedMemory()));
				}
				patientNr++;
				subPatientNr++;
				if (subPatientNr == displayLimit) {
					subPatientNr = 0;
					Logging.add("PostProcess," + Integer.toString(patientNr) + "," + Integer.toString(casePatterns.size()) + "," + Long.toString(MemoryUtilities.getMaxMemory()) + "," + Long.toString(MemoryUtilities.getAllocatedMemory()) + "," + Long.toString(MemoryUtilities.getFreeMemory()) + "," + Long.toString(MemoryUtilities.getUsedMemory()));
				}
			}
		}

		return patient;
	}


	@Override
	public void outputResults() {
		int cases    = 0;
		int controls = 0;
		for (String eventType : totalCases.keySet()) {
			int eventCases    = totalCases.get(eventType);
			int eventControls = totalControls.get(eventType);
			float average = (eventCases > 0) ? ((float)eventControls / (float)eventCases) : 0F;
			Jerboa.getOutputManager().writeln(statsFileName, eventType + "," + eventCases + "," + eventControls + "," + average, true);
			cases    += eventCases;
			controls += eventControls;
		}
		float average = (cases > 0) ? ((float)controls / (float)cases) : 0F;
		Jerboa.getOutputManager().writeln(statsFileName, "TOTAL" + "," + cases + "," + controls + "," + average, true);

		// Close output files
		if (intermediateFiles) {
			Jerboa.getOutputManager().closeFile(intermediateFileName);
		}
		Jerboa.getOutputManager().closeFile(statsFileName);
		Jerboa.getOutputManager().closeFile(outputFileName);

		// Write information to the attrition file
		if ((!Jerboa.unitTest) && attritionFile) {
			Jerboa.getOutputManager().writeln(attritionFileName, title + " (CaseControl),", true);
			Jerboa.getOutputManager().writeln(attritionFileName, "Patients to select cases from," + originalCount,true);
			int excludedCount = 0;
			for (String exclusion : exclusionKeys) {
				int exclusionCount = exclusionCounters.containsKey(exclusion) ? exclusionCounters.get(exclusion) : 0;
				excludedCount += exclusionCount;
				Jerboa.getOutputManager().writeln(attritionFileName, "Patients with " + exclusion + "," + Integer.toString(originalCount - excludedCount),true);
			}
			for (String eventType : totalCases.keySet()) {
				Jerboa.getOutputManager().writeln(attritionFileName, "Total cases " + eventType + "," + totalCases.get(eventType),true);
				Jerboa.getOutputManager().writeln(attritionFileName, "Total controls " + eventType + "," + totalControls.get(eventType),true);
			}
			Jerboa.getOutputManager().writeln(attritionFileName, " ,",true);
			Jerboa.getOutputManager().flush(attritionFileName);
		}
	}


	@Override
	public void setNeededFiles() {
		setRequiredFile(DataDefinition.PATIENTS_FILE);
		setRequiredFile(DataDefinition.EVENTS_FILE);
		boolean atcCovariates = false;
		boolean measurementCovariates = false;
		for (String covariate : covariates) {
			String[] covariateSplit = covariate.toUpperCase().split(";");
			if (covariateSplit.length > 0) {
				if (covariateSplit[0].equals("ATC")) {
					atcCovariates = true;
				}
				else if (covariateSplit[0].equals("MEASUREMENT")) {
					measurementCovariates = true;
				}
				if (atcCovariates && measurementCovariates) {
					break;
				}
			}
		}
		if (((matchOnDrugCount != null) && (!matchOnDrugCount.equals(""))) || outputDrugCount || atcCovariates) {
 			setRequiredFile(DataDefinition.PRESCRIPTIONS_FILE);
		}
		if (measurementCovariates) {
			setRequiredFile(DataDefinition.MEASUREMENTS_FILE);
		}
	}


	@Override
	public void setNeededExtendedColumns() {
		if (matchOnPracticeID) {
			setRequiredExtendedColumn(DataDefinition.PATIENTS_FILE, "practiceid");
		}
	}

	@Override
	public void setNeededNumericColumns() {
		// TODO Auto-generated method stub

	}


	@Override
	public boolean checkParameters(){
		List<String> exceptions = new ArrayList<String>();
		// The following integer parameters can be negative
		exceptions.add("controlsPerCase");
		exceptions.add("maxDifferenceBetweenBirthDates");
		exceptions.add("maxDifferenceInTimeInCohort");
		return checkParameterValues(exceptions);
	}


	/**
	 * Check if the patient is a case and save its case pattern
	 */
	private void saveCases(Patient patient, int cohortStart, int cohortEnd) {
		// Set containing the event types found
		Set<String> eventTypesFound = new HashSet<String>();

		originalCount++;

		// Get the case patterns of the patient
		boolean isCase = false;
		String exclusion = eventOfInterestDescription;
		for (Event event : patient.getEvents()) {
			if ((eventsOfInterest.size() == 0) || eventsOfInterest.contains(event.getType())) {
				if ((event.date >= cohortStart) && (event.date < cohortEnd)) {
					if ((!firstEventOnly) || eventTypesFound.add(event.getType())) {
						isCase = true;
						for (Item matchOnEventCount : matchOnEventCountsList) {
							String eventDescription = matchOnEventCount.getDescription();
							int caseEventCount = getEventCount(patient, event.date, matchOnEventCount);
							if (!matchOnEventCount.getParameters().get(MATCH_EVENT_COUNT_PARAMETER_MINIMUM).equals("")) {
								int minimumCount = Integer.parseInt(matchOnEventCount.getParameters().get(MATCH_EVENT_COUNT_PARAMETER_MINIMUM));
								if (caseEventCount < minimumCount) {
									exclusion = getExclusionNotEnoughEvents(eventDescription, minimumCount);
									isCase = false;
								}
							}
							if (!matchOnEventCount.getParameters().get(MATCH_EVENT_COUNT_PARAMETER_MAXIMUM).equals("")) {
								int maximumCount = Integer.parseInt(matchOnEventCount.getParameters().get(MATCH_EVENT_COUNT_PARAMETER_MAXIMUM));
								if (caseEventCount > maximumCount) {
									exclusion = getExclusionTooManyEvents(eventDescription, maximumCount);
									isCase = false;
								}
							}
							if (!isCase) {
								break;
							}
						}
						if (isCase) {
							// Identify and match case patterns and add them to the list
							CasePattern casePattern = new CasePattern(event, patient, casePatternCount++);
							int cohortStartDate = useMultipleCohorts ? patient.getCohortStartDate(casePattern.event.getDate()) : cohortStart;
							int cohortEndDate = useMultipleCohorts ? patient.getCohortEndDate(casePattern.event.getDate()) : cohortEnd;
							// Check exclusion criteria
							if (!exclusionCriteriaDefinitions.excludePatient(patient, casePattern.event.getDate(), cohortStartDate, cohortEndDate, exclusionCriteriaDefinitions.new PatientCovariateInfo(patient, exclusionCriteriaDefinitions))) {
								casePatterns.add(casePattern);
								writeCaseControl(patient, casePattern, covariateDefinitions.new PatientCovariateInfo(patient, covariateDefinitions), null);
								totalCases.put(event.getType(), totalCases.get(event.getType()) + 1);
								if (controlsNeverCase) {
									caseIDs.add(patient.getPatientID());
								}
							}
							else {
								casePatternCount--;
							}
						}
					}
				}
			}
		}

		if (!isCase) {
			exclusionCounters.put(exclusion, exclusionCounters.get(exclusion) + 1);
		}

		if (intermediateFiles) {
			Jerboa.getOutputManager().writeln(
					this.intermediateFileName,
					patient.subset + "," +
					patient.ID + "," +
					patient.birthDate + "," +
					patient.gender + "," +
					patient.startDate + "," +
					patient.endDate + "," +
					patient.getPopulationStartDate() + "," +
					patient.getPopulationEndDate() + "," +
					cohortStart + "," +
					patient.getCohortEndDate() + "," +
					(isCase ? "YES" : "NO") + "," +
					(isCase ? "" : exclusion),
					true);
		}
	}

	/**
	 * Check if the patient is a control and write it to the output file
	 */
	private void saveControls(Patient patient, int cohortStart, int cohortEnd) {
		int casePatternNr = casePatterns.size() - 1;
		PatientCovariateInfo controlCovariateInfo = null;
		while (casePatternNr >= 0) {
			CasePattern casePattern = casePatterns.get(casePatternNr);

			int cohortStartDate = useMultipleCohorts ? patient.getCohortStartDate(casePattern.event.getDate()) : cohortStart;
			int cohortEndDate = useMultipleCohorts ? patient.getCohortEndDate(casePattern.event.getDate()) : cohortEnd;

			// If control matches case pattern write it to the output file
			Map<Item, Integer> controlEventCounts = new HashMap<Item, Integer>();
			if (casePattern.matchControl(patient, controlEventCounts)) {

				// Check exclusion criteria
				if (!exclusionCriteriaDefinitions.excludePatient(patient, casePattern.event.getDate(), cohortStartDate, cohortEndDate, exclusionCriteriaDefinitions.new PatientCovariateInfo(patient, exclusionCriteriaDefinitions))) {

					// Write the control to the output file
					if (controlCovariateInfo == null) {
						controlCovariateInfo = covariateDefinitions.new PatientCovariateInfo(patient, covariateDefinitions);
					}
					writeCaseControl(patient, casePattern, controlCovariateInfo, controlEventCounts);
					totalControls.put(casePattern.event.getType(), totalControls.get(casePattern.event.getType()) + 1);

					// If the maximum number of controls per case is found remove the case pattern
					if (casePattern.allControlsFound()) {
						casePatterns.remove(casePatternNr);
					}
				}
			}

			casePatternNr--;
		}
	}


	/**
	 * Write a patient to the output file
	 */
	private void writeCaseControl(Patient patient, CasePattern casePattern, PatientCovariateInfo covariateInfo, Map<Item, Integer> controlEventCounts) {
		boolean isCase = (patient.getPatientID().equals(casePattern.patientID));

		int cohortSequenceNr = useMultipleCohorts ? patient.getCohortSequenceNr(casePattern.event.getDate()) : 0;
		String cohortType = useMultipleCohorts ? patient.getCohortType(casePattern.event.getDate()) : "";
		int cohortStartDate = useMultipleCohorts ? patient.getCohortStartDate(casePattern.event.getDate()) : patient.getCohortStartDate();
		int cohortEndDate = useMultipleCohorts ? patient.getCohortEndDate(casePattern.event.getDate()) : patient.getCohortEndDate();

		String record = Parameters.DATABASE_NAME;
		record += "," + Integer.toString(casePattern.id);
		record += "," + casePattern.event.getType();
		record += "," + (isCase ? "1" : "0");
		if (matchOnPracticeID)						record += "," + (outputPatientID ? patient.getPracticeIDAsString() : patient.getAnonymizedPracticeID());
													record += "," + (outputPatientID ? patient.ID : patient.getAnonymizedPatientId());
		if (outputAge)								record += "," + patient.getAgeAtDateInYears(casePattern.event.getDate());
		if (outputGender)							record += "," + Patient.convertGender(patient.gender);
		if (outputCohortType)						record += "," + cohortType;
		if (outputCohortSequenceNr)					record += "," + (cohortSequenceNr == -1 ? "None" : Integer.toString(cohortSequenceNr));
		if (outputCohortMonth)						record += "," + (cohortStartDate == -1 ? "None" : DateUtilities.getMonthFromDays(cohortStartDate));
		if (outputCohortYear)						record += "," + (cohortStartDate == -1 ? "None" : DateUtilities.getYearFromDays(cohortStartDate));
		if (outputMonth)							record += "," + DateUtilities.getMonthFromDays(casePattern.event.getDate());
		if (outputYear)								record += "," + DateUtilities.getYearFromDays(casePattern.event.getDate());
		if (outputDrugCount)						record += "," + getDrugCount(patient, casePattern.event.getDate(), casePattern.event.getDate() + 1, new ArrayList<String>());
		if (outputTimeInCohort)						record += "," + Integer.toString(casePattern.event.getDate() - cohortStartDate);
		if (outputAgeAtCohortStart)					record += "," + patient.getAgeAtDateInYears(cohortStartDate);
		if (outputFollowUpBeforeEvent)				record += "," + Double.toString((casePattern.event.getDate() - patient.getPopulationStartDate()) / 365.25);
		if (outputFollowUpAfterEvent)				record += "," + Double.toString((patient.getPopulationEndDate() - casePattern.event.getDate()) / 365.25);

		for (Item matchOnEventCount : matchOnEventCountsList.getItemList()) {
			record += "," + (isCase ? casePattern.eventCounts.get(matchOnEventCount) : controlEventCounts.get(matchOnEventCount));
		}

		// Get exclusion criteria
		if (outputExclusionCriteria) {
			record += exclusionCriteriaDefinitions.getCovariateValues(patient, casePattern.event.getDate(), cohortStartDate, cohortEndDate, covariateInfo);
		}

		// Get covariate values
		record += covariateDefinitions.getCovariateValues(patient, casePattern.event.getDate(), cohortStartDate, cohortEndDate, covariateInfo);

		Jerboa.getOutputManager().writeln(outputFileName, record, true);
	}


	private int getDrugCount(Patient patient, int startWindow, int endWindow, List<String> atcList) {
		Set<String> atcFound = new HashSet<String>();
		for (Prescription prescription : patient.getPrescriptions()) {
			if ((prescription.getEndDate() >= startWindow) && (prescription.getDate() < endWindow) && ((atcList.size() == 0) || prescription.startsWith(atcList))) {
				atcFound.add(prescription.getATC());
			}
		}

		return atcFound.size();
	}


	private int getEventCount(Patient patient, int indexDate, Item matchOnEventCount) {
		List<String> parameters = matchOnEventCount.getParameters();
		int count = 0;
		int windowStart = 0;
		if (parameters.get(MATCH_EVENT_COUNT_PARAMETER_WINDOW_START).toLowerCase().equals("cohortstart")) { // Note: if windowStart is cohortStart then cohortStart is included
			windowStart = patient.getCohortStartDate() - 1;
		}
		else if (parameters.get(MATCH_EVENT_COUNT_PARAMETER_WINDOW_START).toLowerCase().equals("")) { // Note: if windowStart is not defined then it is the indexDate and it is included
			windowStart = indexDate - 1;
		}
		else {
			windowStart = indexDate + Integer.valueOf(parameters.get(MATCH_EVENT_COUNT_PARAMETER_WINDOW_START));
		}
		int windowEnd = windowStart;
		if (parameters.get(MATCH_EVENT_COUNT_PARAMETER_WINDOW_END).toLowerCase().equals("cohortend")) {
			windowEnd = patient.getCohortEndDate();
		}
		else if (parameters.get(MATCH_EVENT_COUNT_PARAMETER_WINDOW_END).toLowerCase().equals("")) { // Note: if windowEnd is not defined then it is the indexDate and it is included
			windowEnd = indexDate + 1;
		}
		else {
			windowEnd = indexDate + Integer.valueOf(parameters.get(MATCH_EVENT_COUNT_PARAMETER_WINDOW_END));
		}
		for (Event event : patient.getEvents()) {
			if ((matchOnEventCount.getLookup().contains(event.getType())) && (windowStart < event.getDate()) && (event.getDate() < windowEnd)) {
				count++;
			}
		}
		return count;
	}


	/**
	 * Exclusion reasons based on event.
	 */
	private String getExclusionNotEnoughEvents(String eventDescription, int minimumCount) {
		return "number of " + eventDescription + " >= " + Integer.toString(minimumCount);
	}


	private String getExclusionTooManyEvents(String eventDescription, int maximumCount) {
		return "number of " + eventDescription + " <= " + Integer.toString(maximumCount);
	}



	/**
	 * Defines a pattern of a case, based on the specified matching criteria.
	 */
	protected class CasePattern {
		// Case data
		public Set<String> atcClass;
		//public Patient casePatient;
		public String patientID;
		public int patientPracticeID;
		public int patientBirthDate;
		public int patientBirthYear;
		public int patientBirthMonth;
		public byte patientGender;
		public int patientCohortStartDate;
		public int patientCohortEndDate;
		public int id;
		public int drugCount = 0;
		public Map<Item, Integer> eventCounts = new HashMap<Item, Integer>();
		public Event event;
		public String externalCaseSetId;
		public int controlsFound = 0;
		public long timeInCohort;

		// Control data
		public int controlDrugCount = 0;


		/**
		 * Constructor of the case pattern object. Will initialize a pattern to be searched for a patient through its events.<BR>
		 * Takes in consideration the specified criteria for case identification.
		 *
		 * @param event - the event of the patient
		 * @param patientPrescriptionEvent - the patient with all data
		 * @param patternID - the id of the pattern
		 */
		public CasePattern(Event event, Patient patient, int patternID) {
			//casePatient = patient;
			patientID = patient.getPatientID();
			patientPracticeID = patient.getPracticeID();
			patientBirthDate = patient.getBirthDate();
			patientBirthYear = patient.getBirthYear();
			patientBirthMonth = patient.getBirthMonth();
			patientGender = patient.gender;
			patientCohortStartDate = patient.getCohortStartDate();
			patientCohortEndDate = patient.getCohortEndDate();

			if (!matchOnDrugCount.equals("")) {
				drugCount = getDrugCount(patient, event.getDate() + matchOnDrugCountStart, event.getDate() + matchOnDrugCountEnd, atcsOfInterest);
			}

			if (!matchOnATCClass.equals("")) {
				atcClass = getATCClasses(patient, event.getDate() + matchOnATCClassStart, event.getDate() + matchOnATCClassEnd);
			}

			if (!matchOnExternalCaseSetId.equals("")) {
				externalCaseSetId = getExternalCaseSetID(patient);
			}

			for (Item matchOnEventCount : matchOnEventCountsList.getItemList()) {
				eventCounts.put(matchOnEventCount, getEventCount(patient, event.getDate(), matchOnEventCount));
			}

			timeInCohort = event.date - patient.getCohortStartDate();

			this.event = event;
			this.id = patternID;
		}


		/**
		 * Match a control to the case pattern
		 */
		public boolean matchControl(Patient potentialControl, Map<Item, Integer> controlEventCounts) {
			// Initialize control data
			controlDrugCount = 0;

			// Check if the patient is not the case itself.
			if (potentialControl.ID.equals(patientID)) {
				return false;
			}

			// Match the control to the case pattern

			// Match on practiceID
			if (matchOnPracticeID && potentialControl.getPracticeID() != patientPracticeID) {
				return false;
			}

			// Match on age
			if (maxDifferenceBetweenBirthDates >= 0) {
				long startRange = patientBirthDate - maxDifferenceBetweenBirthDates;
				long endRange = patientBirthDate + maxDifferenceBetweenBirthDates;
				if (potentialControl.birthDate <= startRange) {
					return false;
				}
				if (potentialControl.birthDate > endRange) {
					return false;
				}
			}
			else if (maxDifferenceBetweenBirthDates < -1) {
				if (patientBirthYear != potentialControl.getBirthYear()) {
					return false;
				}
				else if (maxDifferenceBetweenBirthDates < -2) {
					if ((patientBirthYear != potentialControl.getBirthYear()) || (patientBirthMonth != potentialControl.getBirthMonth())) {
						return false;
					}
				}
			}

			// Match on gender
			if (matchOnGender && (potentialControl.gender != patientGender)) {
				return false;
			}

			int indexDate = event.getDate();

			// Index date should be inside cohort time of control
			if ((indexDate < potentialControl.getCohortStartDate()) || (indexDate >= potentialControl.getCohortEndDate())) {
				return false;
			}

			// Match on external caseset ID
			if ((!matchOnExternalCaseSetId.equals("")) && (!getExternalCaseSetID(potentialControl).equals(externalCaseSetId))) {
				return false;
			}

			// Match on entry time in cohort
			if (matchCohortTimeCount != -1) {
				if ((matchCohortTimeUnit == MATCH_COHORT_TIME_DAYS) && (Math.abs(potentialControl.getCohortStartDate() - patientCohortStartDate) > matchCohortTimeCount)) {
					return false;
				}
				else if (
							(matchCohortTimeUnit == MATCH_COHORT_TIME_PERCENTAGE) &&
							(Math.abs((potentialControl.getCohortEndDate() - potentialControl.getCohortStartDate()) - (patientCohortEndDate - patientCohortStartDate)) > ((matchCohortTimeCount * (patientCohortEndDate - patientCohortStartDate)) / 100))
						) {
					return false;
				}
			}

			// Control may not have event of same type prior to index date
			for (Event controlEvent : potentialControl.getEvents()) {
				if ((controlEvent.getDate() >= potentialControl.getCohortStartDate()) && (controlEvent.getDate() < potentialControl.getCohortEndDate())) {
					if (controlEvent.getDate() >= indexDate) {
						// Events are sorted on date so we can stop checking
						break;
					}
					if (controlEvent.getType() == event.getType()) {
						return false;
					}
				}
			}

			// Match on drug count
			if (!matchOnDrugCount.equals("")) {
				controlDrugCount = getDrugCount(potentialControl, indexDate + matchOnDrugCountStart, indexDate + matchOnDrugCountEnd, atcsOfInterest);
				if (controlDrugCount != drugCount) {
					return false;
				}
			}

			// Match on ATC class exposure
			if (!matchOnATCClass.equals("")) {
				Set<String> controlATCClass = getATCClasses(potentialControl, indexDate + matchOnATCClassStart, indexDate + matchOnATCClassEnd);
				if ((controlATCClass.size() != atcClass.size()) || (!controlATCClass.containsAll(atcClass))) {
					return false;
				}
			}

			// Match on event counts
			if (matchOnEventCounts.size() > 0) {
				for (Item matchOnEventCount : matchOnEventCountsList.getItemList()) {
					int caseEventCount = eventCounts.get(matchOnEventCount);
					int controlEventCount = getEventCount(potentialControl, indexDate, matchOnEventCount);
					if (!matchOnEventCount.getParameters().get(MATCH_EVENT_COUNT_PARAMETER_MINIMUM).equals("")) {
						int minimumCount = Integer.parseInt(matchOnEventCount.getParameters().get(MATCH_EVENT_COUNT_PARAMETER_MINIMUM));
						if (controlEventCount < minimumCount) {
							return false;
						}
					}
					if (!matchOnEventCount.getParameters().get(MATCH_EVENT_COUNT_PARAMETER_MAXIMUM).equals("")) {
						int maximumCount = Integer.parseInt(matchOnEventCount.getParameters().get(MATCH_EVENT_COUNT_PARAMETER_MAXIMUM));
						if (controlEventCount > maximumCount) {
							return false;
						}
					}
					if (matchOnEventCount.getParameters().get(MATCH_EVENT_COUNT_PARAMETER_DIFFERENCE).equals("")) {
						// Only presence should match
						if (!(((caseEventCount > 0) && (controlEventCount > 0)) || ((caseEventCount == 0) && (controlEventCount == 0)))) {
							return false;
						}
					}
					else {
						// Count should match within boundaries
						int countDifference = Integer.valueOf(matchOnEventCount.getParameters().get(MATCH_EVENT_COUNT_PARAMETER_DIFFERENCE));
						if (Math.abs(caseEventCount - controlEventCount) > countDifference) {
							return false;
						}
					}
					controlEventCounts.put(matchOnEventCount, controlEventCount);
				}
			}


			// The control matches the case pattern so increase control count
			controlsFound++;

			return true;
		}


		/**
		 * Checks if all cases and controls have been checked.
		 */
		public boolean firstControlFound() {
			return (controlsFound == 1);
		}


		/**
		 * Checks if all cases and controls have been checked.
		 */
		public boolean allControlsFound() {
			return (controlsPerCase == -1 ? false : controlsFound >= controlsPerCase);
		}


		private String getExternalCaseSetID(Patient patient) {
			for (Event caseSetIDEvent : patient.getEvents()) {
				if (caseSetIDEvent.getType().substring(0, Math.min(caseSetIDEvent.getType().length(), matchOnExternalCaseSetId.length())).equals(matchOnExternalCaseSetId)) {
					return caseSetIDEvent.getType();
				}
			}

			return "";
		}


		private Set<String> getATCClasses(Patient patient, int startWindow, int endWindow) {
			Set<String> atcClassSet = new HashSet<String>();
			for (Prescription prescription : patient.getPrescriptions()) {
				if ((prescription.getDate() > startWindow) && (prescription.getDate() < endWindow) && (prescription.getATC().length() >= matchOnATCClassLevel)) {
					atcClassSet.add(prescription.getATC().substring(0, matchOnATCClassLevel));
				}
			}

			return atcClassSet;
		}
	}
}
