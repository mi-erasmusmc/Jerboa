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

import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.apache.commons.collections.bag.HashBag;
import org.erasmusmc.jerboa.Jerboa;
import org.erasmusmc.jerboa.config.DataDefinition;
import org.erasmusmc.jerboa.config.Parameters;
import org.erasmusmc.jerboa.dataClasses.Event;
import org.erasmusmc.jerboa.dataClasses.Measurement;
import org.erasmusmc.jerboa.dataClasses.Patient;
import org.erasmusmc.jerboa.engine.Module;
import org.erasmusmc.jerboa.modifiers.EventCohortDefinition;
import org.erasmusmc.jerboa.utilities.AgeGroupDefinition;
import org.erasmusmc.jerboa.utilities.DateUtilities;
import org.erasmusmc.jerboa.utilities.DelimitedStringBuilder;
import org.erasmusmc.jerboa.utilities.IndexDateUtilities;
import org.erasmusmc.jerboa.utilities.Item;
import org.erasmusmc.jerboa.utilities.ItemList;
import org.erasmusmc.jerboa.utilities.Logging;
import org.erasmusmc.jerboa.utilities.MultiKeyBag;
import org.erasmusmc.jerboa.utilities.StringUtilities;

/**
 * Module to perform a Cohort Study.
 * 
 * The  module outputs patient level data containing information about comorbidities, measurements,
 * prescriptions etc before or during cohort time. The search windows around cohort start can be defined on item level.
 * If multipleValueMeasurements is set for each value of the measurements in the multiValueMeasurements list the module
 * outputs patient level data containing information about comorbidities, measurements, prescriptions etc before or
 * during cohort time. The search windows around cohort start can be defined on item level.
 * Additionally, this data can be extracted around the date of death as defined by deathLabels in the
 * event file (not complete yet)
 * 
 * @author Rijnbeek
 *
 */
public class CohortStudy extends Module{
	
	// Settings that define patient characteristics to search for around cohort start and death
	
	/**
	 * List of measurements for which multiple values in population time have to be written to the output file.
	 * Format: MeasurementType;Label
	 * 
	 * The Label is optional. If it is not specified the MeasurementType is used as label.
	 * For each measurement specified it adds the columns <label>Value and TimeTo<label>.
	 */
	public List<String> multipleValueMeasurements = new ArrayList<String>();
		
	/**
	 * List of comorbidities that are checked for their presence in a time window around cohort start
	 * Format: EventType;Label;Description[;[TimeWindowStart][;[TimeWindowEnd]]]
	 * 
	 * EventType		- The variable name used in the event file
	 * Label            - The label used in the output file.
	 * Description 		- The text used in the result tables
	 * TimeWindowStart	- Optional parameter that overrules the timeWindowComorbidity setting.
	 *                    Start of time window before index date. Start day is included. -1 = All history 
	 * TimeWindowEnd	- Optional parameter that overrules the index date at TimeWindowEnd. The end of time
	 *                    window relative to index date. End day is not included. cohortEnd = end of cohort
	 * 
	 * For example: "MI;MI;Myocardial Infarction"				  : Look for MI in the window  indexDate-timeWindowComorbidities<= X <indexDate
	 * 				"MI;MI;Myocardial Infarction;-365" 		  : Look for MI in the window  indexDate-365<= X <indexDate
	 * 				"MI;MI;Myocardial Infarction;-365;10" 		  : Look for MI in the window  indexDate-365<= X <indexDate + 10
	 * 				"MI;MI;Myocardial Infarction;-365;cohortEnd" : Look for MI in the window  indexDate-365<= X <cohortEnd
	 * 
	 */	
	public List<String> comorbidities = new ArrayList<String>();
	
	/**
	 * List of comorbidities that are checked for their presence before and at death date in a time window
	 * multiple occurrences are ignored
	 * Format: EventType;Label;Description[;[TimeWindowStart][;[TimeWindowEnd]]]
	 * 
	 * EventType		- The variable name used in the event file. 
	 * 					  If EventType = "COMORBIDITIES" all comorbidity definitions in <comorbidities> are added.
	 * Label            - The label used in the output file.
	 * Description 		- The text used in the result tables
	 * TimeWindowStart	- Optional parameter that overrules the timeWindowComorbidity setting.
	 *                    Start of time window before deathDate. Start day is included. -1 = All history 
	 * TimeWindowEnd	- Optional parameter that overrules the deathDate at TimeWindowEnd. The end of time
	 *                    window relative to deathDate. End day is not included. cohortEnd = end of cohort
	 * 
	 * For example: "MI;MI;Myocardial Infarction"				  : Look for MI in the window  deathDate-timeWindowComoorbidities<= X <deathDate
	 * 				"MI;MI;Myocardial Infarction;-365" 		  : Look for MI in the window  deathDate-365<= X <deathDate
	 * 				"MI;MI;Myocardial Infarction;-365;10" 		  : Look for MI in the window  deathDate-365<= X <deathDate + 10
	 * 				"MI;MI;Myocardial Infarction;-365;cohortEnd" : Look for MI in the window  deathDate-365<= X <cohortEnd
	 */	
	public List<String> comorbiditiesDeath = new ArrayList<String>();
	
	
	/**
	 * List of measurements of interest to be searched a time window (inclusive) around cohort start
	 * The closest measurement is selected.
	 * Format: MeaurementType;Values;Description[;[TimeWindowStart][;[TimeWindowEnd[;[MissingValue]]]]]
	 * MeasurementType	- the variable name used in the measurement file
	 * Values			- a comma-separated list of values 
	 * 
	 *  TODO:(if empty all values are considered), make flexibel when not all are needed
	 *  
	 * 					  or "continues" to define a continuous valued variable
	 * Label	 		- the label used in the result tables
	 * TimewindowStart	- Optional parameter that overrule the timeWindowMeasurement setting.
	 *                    Start of time window before index date. Start day is included. -1 = All history 
	 * TimewindowEnd	- Optional parameter that overrules the index date at TimeWindowEnd.
	 *                    End of time window relative to index date. End day is not included. cohortEnd = end of cohort
	 * MissingValue     - Optional parameter the overrules the UNKNOWN label used for missing values.
	 * 
	 * For example: "SMOKER;RECENT,NEVER,PAST;Smoking"		  		  		: Smoking Status in the window  indexDate-timeWindowMeasurements<= X <indexDate
	 * 				"SMOKER;RECENT,NEVER,PAST;Smoking;-365" 		  		: Smoking Status in the window  indexDate-365<= X <indexDate
	 * 				"SMOKER;RECENT,NEVER,PAST;Smoking;-365;10" 		  		: Smoking Status in the window  indexDate-365<= X <indexDate + 10
	 * 				"SMOKER;RECENT,NEVER,PAST;Smoking;-365;cohortEnd" 		: Smoking Status in the window  indexDate-365<= X <cohortEnd
	 * 				"SMOKER;RECENT,NEVER,PAST;Smoking;-365;cohortEnd;NONE" 	: Smoking Status in the window  indexDate-365<= X <cohortEnd use NONE as missing value
	 *				"BMI;continuous"								  		: BMI in the window indexDate-timeWindowMeasurements< X <=indexDate
	 */	
	public List<String> measurements = new ArrayList<String>();
	
	//TODO: measurements before death also in flexible time windows etc.
	/**
	 * List of measurements are search before and at death date 
	 * Format: MeaurementType;Values;Description
	 * MeasurementType	- the variable name used in the measurement file
	 * 					  if MeasurementType = "MEASUREMENTS" all measurement definitions 
	 * 					  as defined in measurements are added
	 * Values			- a comma-separated list of values, 
	 * 					  or "continues" to define a continuous valued variable
	 * MissingValue		- one value used if no measurement is available
	 * Description 		- the text used in the result tables
	 * 
	 * Example: "Smoker;1,2;Smoking status"
	 * 			"BMI;continues;Body Mass Index"
	 */	
	public List<String> measurementsDeath = new ArrayList<String>();

	/**
	 * List of measurements of interest checked to count the number of occurrences in a time window (inclusive) around the index date.
	 * Format: Measurement types;Label;Description;MinAge;MaxAge;TimeWindoStart;TimeWindowEnd
	 * Measurement types - A comma separated list of measurement types.
	 * Label             - The label used in the result tables.
	 * Description       - The description.
	 * MinAge            - The minimum age value (inclusive), -1 means no minimum.
	 * MaxAge            - The maximum age value (exclusive), -1 means no maximum.
	 * TimeWindowStart   - Start of time window before index date. Start day is included.
	 *                     birth = All history 
	 * TimeWindowEnd     - Optional parameter that overrules the index date as TimeWindowEnd. Note that here the end day is NOT included!
	 *                     End of time window relative to index date. End day is not included.
	 *                     cohortEnd = end of cohort
	 * 
	 * For example: "BMI;BMI_15-40;Body Mass Index;15;40;-365"           : The number of BMI measurements at an age in the range 15 to 40 in the window indexDate-365<= X <indexDate
	 *              "BMI;BMI_15-40;Body Mass Index;15;40;-365,10"        : The number of BMI measurements at an age in the range 15 to 40 in the window indexDate-365<= X <indexDate + 10
	 *              "BMI;BMI_15-40;Body Mass Index;15;40;-365,cohortEnd" : The number of BMI measurements at an age in the range 15 to 40 in the window indexDate-365<= X <cohortEnd
	 */	
	public List<String> measurementCount = new ArrayList<String>();
	
	
	/**
	 * List of prescriptions of interest checked for their presence in a time window (inclusive) around cohort start.
	 * Format:
	 * 
	 *   ATC;Label;[;[TimeWindowStart][;[TimeWindowEnd]]]
	 * 
	 * ATC              - a list of atc codes, higher level allowed
	 * Label            - the label used in the result tables
	 * TimeWindowStart  - Optional parameter that overrules the timeWindowPrescription setting.
	 *                    Start of time window before index date. Start day is included. -1 = All history.
	 * TimeWindowEnd    - Optional parameter that overrules the index date at TimeWindowEnd.
	 *                    End of time window relative to index date. End day is not included. cohortEnd = end of cohort.
	 * 
	 * For example: "R03BB06,R03BB07;LAMA"		  		  			  : Use of drugs in the window  indexDate-timeWindowMeasurements<= X <indexDate
	 * 				"R03BB06,R03BB07;LAMA;-365" 		  			  : Use of drugs in the window  indexDate-365<= X <indexDate
	 * 				"R03BB06,R03BB07;LAMA;-365;10" 		  			  : Use of drugs in the window  indexDate-365<= X <indexDate + 10
	 * 				"R03BB06,R03BB07;LAMA;-365;cohortEnd" 		      : Use of drugs in the window  indexDate-365<= X <cohortEnd
	 */	
	public List<String> prescriptions = new ArrayList<String>();

	/**
	 * List of prescriptions for which the time till the next event will
	 * be measured. If their is no next prescription the value is missing
	 * Format: ATC; Label; Description; InCohort; IncludeIndexDate; WindowEnd
	 *
	 * ATC                          - label of the event in inputfile
	 * Label                        - label used in the output
	 * Description                  - text used if useLabel = false. Can be empty
	 * InCohort (optional)          - if true only search during cohort time (default = false)
	 * IncludeIndexDate (optional)  - if true indexDate is included (default = false)
	 * WindowEnd                    - cohortEnd or number of days relative to index date
	 * 
	 * Examples:
	 *   "R03BB06,R03BB07;DaysToNextLAMA;;true;true;cohortend"
	 *   "R03BB06,R03BB07;DaysToNextLAMAWithinYear;;true;true;365"
	 */
	public List<String> daysToNextPrescription = new ArrayList<String>();
	
	/**
	 * List of prescriptions for which the time till the previous prescription will
	 * be measured. If their is no previous prescription the value is missing
	 * Format: ATC; Label; Description; InCohort; IncludeIndexDate WindowStart
	 *
	 * ATC                          - label of the event in inputfile
	 * Label                        - label used in the output
	 * Description                  - text used if useLabel = false. Can be empty
	 * InCohort (optional)          - if true only search during cohort time (default = false)
	 * IncludeIndexDate (optional)  - if true indexDate is included (default = false)
	 * WindowStart                  - birth or number of days relative to index date
	 *  
	 * Examples:
	 *   "R03BB06,R03BB07;DaysSincePreviousLAMA;;true;true;cohortend"
	 *   "R03BB06,R03BB07;DaysSincePreviousLAMAWithinYear;;true;true;-365"
	 */
	public List<String> daysSincePreviousPrescription = new ArrayList<String>();
	
	
	/**
	 * List of prescriptions of interest to be searched for around death label.
	 * Format:
	 * 
	 *   ATC;Label;[;[TimeWindowStart][;[TimeWindowEnd]]]
	 * 
	 * ATC              - a list of atc codes, higher level allowed
	 * Label            - the label used in the result tables
	 * 
	 * Optional parameter that overrules the timeWindowPrescription setting
	 * 
	 * TimewindowStart  - start of time window before deathDate. Start day is not included. -1 = All history 
	 * 
	 * Optional parameter that overrules the deathDate at TimeWindowEnd. Note that here the end day is not included!
	 * TimewindowEnd	- end of time window relative to deathDate. End day is not included. cohortEnd = end of cohort
	 * 
	 * For example: "R03BB06,R03BB07;LAMA"                   : Use of drugs in the window  deathDate-timeWindowMeasurements< X <=deathDate
	 * 				"R03BB06,R03BB07;LAMA;;-365"             : Use of drugs in the window  deathDate-365< X <=deathDate
	 * 				"R03BB06,R03BB07;LAMA;;-365;10"          : Use of drugs in the window  deathDate-365< X <=deathDate + 10
	 * 				"R03BB06,R03BB07;LAMA;;-365;cohortEnd"   : Use of drugs in the window  deathDate-365< X <=cohortEnd
	 */	
	public List<String> prescriptionsDeath = new ArrayList<String>();
	
	
	/**
	 * Labels in the event file that define death
	 */
	public List<String> deathLabels = new ArrayList<String>();
	
	/** Age group definition used for the Age at cohort start categories
	 * Format: Min;Max;Label
	 * Min				- minimum age in years (inclusive)
	 * Max				- maximum age in years (inclusive)
	 * Label			- label used in output
	 * 
	 * Example: "0;5;0-4"
	 */
	public List<String> ageGroups = new ArrayList<String>();
	
	/**
	 * Defines for which events the year and month of the first diagnosis
	 * needs to be determined in full patient history. 
	 * 
	 * This will add a column for the month (Month<EventType>) and
	 * for the year (Year<EventType>) in the output, e.g. YearHTN
	 * Format:Eventtype
	 * 
	 * EventType			- label of the event in inputfile
	 * 
	 * Example: HTN
	 * 
	 */
	public List<String> firstEventYearMonth = new ArrayList<String>();


	/**
	 * List of Events for which the time till the next event will
	 * be measured. If their is no next event the values is missing
	 * Format: EventType; Label; Description; InCohort; IncludeIndexDate
	 *
	 * EventType			- label of the event in inputfile
	 * Label				- label used in the output
	 * Description			- text used if useLabel = false. Can be empty
	 * InCohort	(optional)	- if true only search during cohort time (default = true)
	 * IncludeIndexDate (optional)	- if true indexDate is included (default = false)
	 * 
	 * Example: "HOSP;DaysToNextHosp;;true"
	 */
	public List<String> daysToNextEvent = new ArrayList<String>();
	
	/**
	 * List of Events for which the time till the previous event will
	 * be measured. If their is no next event the values is missing
	 * Format: EventType; Label; Description; InCohort; IncludeIndexDate
	 *
	 * EventType			- label of the event in inputfile
	 * Label				- label used in the output
	 * Description			- text used if useLabel = false. Can be empty
	 * InCohort	(optional)	- if true only search during cohort time (default = true)
	 * IncludeIndexDate (optional)	- if true indexDate is included (default = false)
	 * 
	 * Example: "HOSP;HOSP;DaysToPrevioustHosp"
	 */
	public List<String> daysToPreviousEvent = new ArrayList<String>();

	/**
	 * List of events of interest checked to count the number of occurrences in a time window around the index date.
	 * Format: Event types;Label;Description;MinAge;MaxAge;TimeWindoStart;TimeWindowEnd
	 * Event types       - A comma separated list of event types.
	 * Label             - The label used in the result tables.
	 * Description       - The description.
	 * MinAge            - The minimum age value (inclusive), -1 means no minimum.
	 * MaxAge            - The maximum age value (exclusive), -1 means no maximum.
	 * TimeWindowStart   - Start of time window before index date. Start day is included.
	 *                     birth = All history 
	 * TimeWindowEnd     - Optional parameter that overrules the index date as TimeWindowEnd. Note that here the end day is included!
	 *                     End of time window relative to index date. End day is not included.
	 *                     cohortEnd = end of cohort
	 * 
	 * For example: "MI;MI_15-40;Miocardial Infarction;15;40;-365"           : The number of MI events at an age in the range 15 to 40 in the window indexDate-365< X <=indexDate
	 *              "MI;MI_15-40;Miocardial Infarction;15;40;-365,10"        : The number of MI events at an age in the range 15 to 40 in the window indexDate-365< X <=indexDate + 10
	 *              "MI;MI_15-40;Miocardial Infarction;15;40;-365,cohortEnd" : The number of MI events at an age in the range 15 to 40 in the window indexDate-365< X <=cohortEnd
	 */	
	public List<String> eventCount = new ArrayList<String>();
	
	/**
	 * List of measurements for which the time till the next event will
	 * be measured. If their is no next event the values is missing
	 * Format: EventType; Label; Description; Value; InCohort; IncludeIndexDate
	 * 
	 * EventType			- label of the event in inputfile
	 * Label				- label used in the output
	 * Description			- text used if useLabel = false. Can be empty
	 * Value				- value to search for. if "*" all values match
	 * InCohort	(optional)	- if true only search during cohort time (default = true)
	 * IncludeIndexDate (optional)	- if true indexDate is included (default = false)
	 * 
	 * Example: "SMOKING;DaysToNextSmokeCurrent;;CURRENT;true;true"
	 */
	public List<String> daysToNextMeasurement = new ArrayList<String>();
	
	/**
	 * List of measurements for which the time till the previous event will
	 * be measured. If their is no next event the values is missing
	 * Format: MeasurementType; Label; Description; Value; InCohort; IncludeIndexDate
	 * 
	 * MeasurementType		- label of the measurement in inputfile
	 * Label				- label used in the output
	 * Description			- text used if useLabel = false. Can be empty
	 * Value				- value to search for. if "*" all values match
	 * InCohort	(optional)	- if true only search during cohort time (default = true)
	 * IncludeIndexDate (optional)	- if true indexDate is included (default = false)
	 * 
	 * Example: "SMOKING;SMOKING;DaysToPreviousSmokeCurrent;CURRENT;true;true"
	 */
	public List<String> daysToPreviousMeasurement = new ArrayList<String>();
	
	
	// Default windowStart settings used in case not defined in the per item settings
	/**
	 * Default number of days relevant to cohort start to search for measurements.
	 * 
	 * For example -365 means 365 days before cohort start
	 * Can be overruled by measurement specific definitions
	 */
	public int defaultWindowStartMeasurement;
	
	/**
	 * Default number of days relative to cohort start to search for prescriptions.
	 * For example -365 means 365 days before cohort start.
	 * Can be overruled by measurement specific definitions.
	 */
	public int defaultWindowStartPrescription;
	
	/**
	 * Default number of days before cohort start to search for comorbidities.
	 * For example -365 means 365 days before cohort start.
	 * Can be overruled by comorbidity specific definitions.
	 */
	public int defaultWindowStartComorbidity;
	
	/**
	 * Default number of days relative to prescription start to search for events.
	 * For example 365 means 365 days before prescription start.
	 * Can be overruled by event specific definitions.
	 */
	public int defaultWindowStartEvent;
	
	
	// Output settings
	/**
	 * Output Prevalent/Incident status at cohort start.
	 * This value is set by the EventCohortDefinition modifier otherwise the value
	 * UNKNOWN will be used
	 */
	public boolean outputPrevalentIncident;
	
	/**
	 * When true the ageAtStart is computed as a fraction of years otherwise it is the normal age.
	 */
	public boolean ageAtStartAsFraction = false;
	
	// Debug settings
	/** 
	 * If true an anonymizedPatientID is used instead of the original patient ID
	 */
	public boolean anonymizePatientID;
	
	private String resultsFile;
	private String intermediateFile;

	private List<String> multipleValueMeasurementTypes;
	private Map<String, String> multipleValueMeasurementsLabels;
	private ItemList comorbiditiesList = new ItemList(true,0);
	private ItemList measurementsList = new ItemList(true,0);
	private ItemList measurementCountsList = new ItemList(true,0);
	private ItemList prescriptionsList = new ItemList(true,0);
	private ItemList daysToNextPrescriptionsList = new ItemList(0);
	private ItemList daysSincePreviousPrescriptionsList = new ItemList(0);
	private ItemList firstEventMonthYearList = new ItemList(true,0);
	private ItemList daysToNextEventList = new ItemList(true,0);
	private ItemList daysToPreviousEventList = new ItemList(true,0);
	private ItemList eventCountsList = new ItemList(true,0);
	private ItemList daysToNextMeasurementList = new ItemList(true,0);
	private ItemList daysToPreviousMeasurementList = new ItemList(true,0);
	private HashBag comorbiditiesBag = new HashBag();
	private MultiKeyBag eventCountsBag = new MultiKeyBag();
	private HashBag prescriptionsBag = new HashBag();
	private MultiKeyBag measContinuesBag = new MultiKeyBag();
	private MultiKeyBag measCategoryBag = new MultiKeyBag();
	private MultiKeyBag measurementCountsBag = new MultiKeyBag();
	
	//TODO maybe only do this if intermediateStats are needed??
	private HashBag comorbiditiesDeathBag = new HashBag();
	private HashBag prescriptionsDeathBag = new HashBag();
	private MultiKeyBag measContinuesDeathBag = new MultiKeyBag();
	private MultiKeyBag measCategoryDeathBag = new MultiKeyBag();

	//TODO add all the lists also for DEATH
	private ItemList comorbiditiesDeathList = new ItemList(true,0);
	private ItemList measurementsDeathList = new ItemList(true,0);
	private ItemList prescriptionsDeathList = new ItemList(true,0);

	private AgeGroupDefinition ageGroupDefinition;
	
	//If true patientdates in output
	private boolean SASCompare = false;
	
	private String intermediateAnonymizationFile;
	
	// Hooks to modifiers
	private EventCohortDefinition eventCohortDefinition;
	
	
	private DecimalFormat precisionFormat = StringUtilities.DECIMAL_FORMAT_FORCE_PRECISION;

	@Override
	public boolean init() {
		boolean initOK = true;
		
		// Get modifier hooks
		eventCohortDefinition = (EventCohortDefinition)this.getModifier(EventCohortDefinition.class);

		multipleValueMeasurementTypes = new ArrayList<String>();
		multipleValueMeasurementsLabels = new HashMap<String, String>();
		for (String mvMeasuermentDefinition : multipleValueMeasurements) {
			String[] mvMeasuermentDefinitionSplit = mvMeasuermentDefinition.split(";");
			String mvMeasurementType = mvMeasuermentDefinitionSplit[0];
			String mvMeasurementLabel = mvMeasurementType;
			if (mvMeasuermentDefinitionSplit.length > 1) {
				mvMeasurementLabel = mvMeasuermentDefinitionSplit[1];
			}
			if (!multipleValueMeasurementsLabels.containsKey(mvMeasurementType)) {
				multipleValueMeasurementTypes.add(mvMeasurementType);
				multipleValueMeasurementsLabels.put(mvMeasurementType, mvMeasurementLabel);
			}
			else {
				initOK = false;
				Logging.add("Duplicate measurement \"" + mvMeasurementType + "\" in multipleValueMeasurements", Logging.ERROR);
			}
		}
		Collections.sort(multipleValueMeasurementTypes);
		
		// allow inserting of the other list in script
		if (comorbiditiesDeath.contains("COMORBIDITIES")){
			comorbiditiesDeath.remove("COMORBIDITIES");
			comorbiditiesDeath.addAll(comorbidities);
		}
		
		if (measurementsDeath.contains("MEASUREMENTS")){
			measurementsDeath.remove("MEASUREMENTS");
			measurementsDeath.addAll(measurements);
		}
		
		if (prescriptionsDeath.contains("PRESCRIPTIONS")){
			prescriptionsDeath.remove("PRESCRIPTIONS");
			prescriptionsDeath.addAll(prescriptions);
		}
		
		// parse parameters
		ageGroupDefinition = new AgeGroupDefinition(ageGroups);	
		if (ageGroupDefinition.hasGaps()) {
			Logging.add("Note: for module " + this.title +" age group definitions in the script have gaps!");
			Logging.add("");
		}
		
		comorbiditiesList.parse(comorbidities);
		comorbiditiesDeathList.parse(comorbiditiesDeath);
		daysToNextEventList.parse(daysToNextEvent);
		daysToPreviousEventList.parse(daysToPreviousEvent);
		eventCountsList.parse(eventCount);
		daysToNextMeasurementList.parseWithValue(daysToNextMeasurement);
		daysToPreviousMeasurementList.parseWithValue(daysToPreviousMeasurement);
		
		measurementsList.parseWithValue(measurements); //continuous is determined based on parameter "continuous"
		measurementsDeathList.parseWithValue(measurementsDeath); //continuous is determined based on parameter "continuous"
		measurementCountsList.parse(measurementCount);
		prescriptionsList.parse(prescriptions); 
		daysToNextPrescriptionsList.parse(daysToNextPrescription);
		daysSincePreviousPrescriptionsList.parse(daysSincePreviousPrescription);
		firstEventMonthYearList.parseParamList(firstEventYearMonth);
	
		// set headers of result and intermediate file based on parameters
		resultsFile = outputFileName;
		intermediateFile = intermediateFileName;
		
		// Add eventType if EventCohortDefinition
		String eventTypeHeader = eventCohortDefinition != null ? ",EventType" : "";

		initOK = initOK && Jerboa.getOutputManager().addFile(this.resultsFile, 1);
		if (initOK) {
			DelimitedStringBuilder  header = new DelimitedStringBuilder();
			if (!SASCompare){
				header.append("Database" + eventTypeHeader + ",PatientID,Gender,AgeStart,Year,FollowUpPre,FollowUpPost");
			} else {
				header.append("Database" + eventTypeHeader + ",PatientID,Gender,BirthDate,PopulationStart,PopulationEnd,CohortStart,CohortEnd,AgeStart,Year,FollowUpPre,FollowUpPost");
			}
			if (ageGroups.size()>0)
				header.append("AgeStartCat");
			if (outputPrevalentIncident)
				header.append("PrevInc");
			
			if (firstEventMonthYearList.size() > 0) {
				ArrayList<String> extLabels = new ArrayList<String>();
				extLabels.add("Year");
				extLabels.add("Month");
				extLabels.add("Age");
				header.append(firstEventMonthYearList.getExtendedLabels(extLabels).toString());
			}
			if (comorbiditiesList.size()>0)
				header.append(comorbiditiesList.getLabels("", false).toString());
			if (daysToNextEventList.size()>0)
				header.append(daysToNextEventList.getLabels("", false).toString());
			if (daysToPreviousEventList.size()>0)
				header.append(daysToPreviousEventList.getLabels("", false).toString());
			if (eventCountsList.size()>0)
				header.append(eventCountsList.getLabels("", false).toString());
			if (prescriptionsList.size()>0)
				header.append(prescriptionsList.getLabels("", false).toString());
			if (daysToNextPrescriptionsList.size()>0)
				header.append(daysToNextPrescriptionsList.getLabels("", false).toString());
			if (daysSincePreviousPrescriptionsList.size()>0)
				header.append(daysSincePreviousPrescriptionsList.getLabels("", false).toString());
			if (measurementsList.size()>0)
				header.append(measurementsList.getLabels("", true).toString());
			if (measurementCountsList.size()>0)
				header.append(measurementCountsList.getLabels("", false).toString());
			if (daysToNextMeasurementList.size()>0)
				header.append(daysToNextMeasurementList.getLabels("", false).toString());
			if (daysToPreviousMeasurementList.size()>0)
				header.append(daysToPreviousMeasurementList.getLabels("", false).toString());
			
			//TODO: add the other lists
			if (deathLabels.size()>0) {
				header.append("CauseDeath,DaysYrDth,AgeDeath,AgeDeathCat");
				if (measurementsDeathList.size()>0)
					header.append(measurementsDeathList.getLabels("", false).toString());
				if (prescriptionsDeathList.size()>0)
					header.append(prescriptionsDeathList.getLabels("", false).toString());
				if (comorbiditiesDeathList.size()>0)
					header.append(comorbiditiesDeathList.getLabels("", false).toString());
				
			}	
			header.append("CohortTime");
			for (String mvMeasurementType : multipleValueMeasurementTypes) {
				header.append(multipleValueMeasurementsLabels.get(mvMeasurementType) + "Value");
				header.append("TimeTo" + multipleValueMeasurementsLabels.get(mvMeasurementType));
			}
			Jerboa.getOutputManager().writeln(this.resultsFile, header.toString(), true);
		}
		
		if (intermediateFiles){
			initOK = initOK && Jerboa.getOutputManager().addFile(this.intermediateFile, 1);
			DelimitedStringBuilder  header = new DelimitedStringBuilder();
			header.append("Database" + eventTypeHeader + ",PatientID,Gender,BirthDate,PopulationStart,PopulationEnd,CohortStart,CohortEnd,AgeStart");
			if (ageGroups.size() > 0)
				header.append("AgeStartCat");
			if (outputPrevalentIncident)
				header.append("PrevInci");
			if (firstEventMonthYearList.size() > 0)
				header.append(firstEventMonthYearList.getLabels("", false).toString());
			if (comorbiditiesList.size() > 0)
				header.append(comorbiditiesList.getLabels("", false).toString());
			if (daysToNextEventList.size() > 0)
				header.append(daysToNextEventList.getLabels("", false).toString());
			if (daysToPreviousEventList.size() > 0)
				header.append(daysToPreviousEventList.getLabels("", false).toString());
			if (eventCountsList.size() > 0)
				header.append(eventCountsList.getLabels("", false).toString());
			if (prescriptionsList.size()> 0)
				header.append(prescriptionsList.getLabels("", false).toString());
			if (daysToNextPrescriptionsList.size() > 0)
				header.append(daysToNextPrescriptionsList.getLabels("", false).toString());
			if (daysSincePreviousPrescriptionsList.size() > 0)
				header.append(daysSincePreviousPrescriptionsList.getLabels("", false).toString());
			if (measurementsList.size() > 0)
				header.append(measurementsList.getLabels("", true).toString());
			if (measurementCountsList.size() > 0)
				header.append(measurementCountsList.getLabels("", false).toString());
			if (daysToNextMeasurementList.size() > 0)
				header.append(daysToNextMeasurementList.getLabels("", true).toString());
			if (daysToPreviousMeasurementList.size() > 0)
				header.append(daysToPreviousMeasurementList.getLabels("", true).toString());
			if (deathLabels.size() > 0) {
				header.append("CauseDeath,DaysYrDth,AgeDeath");
				if (ageGroups.size() > 0)
					header.append("AgeDeathCat");
				if (comorbiditiesDeathList.size() > 0)
					header.append(comorbiditiesDeathList.getLabels("", false).toString());
				if (prescriptionsDeathList.size() > 0)
					header.append(prescriptionsDeathList.getLabels("", false).toString());
				if (measurementsDeathList.size() > 0)
					header.append(measurementsDeathList.getLabels("", false).toString());
			}	
			header.append("CohortTime");
			Jerboa.getOutputManager().writeln(this.intermediateFile, header.toString(), true);
		}

		if (anonymizePatientID) {
			intermediateAnonymizationFile = StringUtilities.addSuffixToFileName(intermediateFileName, "_anonymization");
			initOK &= Jerboa.getOutputManager().addFile(intermediateAnonymizationFile);
			Jerboa.getOutputManager().writeln(intermediateAnonymizationFile, "AnonymousID,PatientID", false);
		}

		return initOK;
	}

	@Override
	public Patient process(Patient patient) {
		if (anonymizePatientID) {
			Jerboa.getOutputManager().writeln(intermediateAnonymizationFile, patient.getAnonymizedPatientId() + "," + patient.getPatientID(), true);
		}
		
		if (patient.inCohort) {
			// Get the measurements with multiple values inside population time
			List<DelimitedStringBuilder> multipleValueMeasurementRows = new ArrayList<DelimitedStringBuilder>();
			for (Measurement measurement : patient.getMeasurements()) {
				if (multipleValueMeasurementTypes.contains(measurement.getType())) {
					if (measurement.isInPeriod(patient.getPopulationStartDate(), patient.getPopulationEndDate(), true, false)) {
						DelimitedStringBuilder columns = new DelimitedStringBuilder();
						for (String measurementColumnPairType : multipleValueMeasurementTypes) {
							if (measurementColumnPairType.equals(measurement.getType())) {
								columns.append(measurement.getValue());
								columns.append(Integer.toString(measurement.getDate() - patient.getCohortStartDate())); 
							}
							else {
								columns.append(" ");
								columns.append(" ");
							}
						}
						multipleValueMeasurementRows.add(columns);
					}
				}
			}

			// Create the result file
			DelimitedStringBuilder  results = new DelimitedStringBuilder();
			results.append(Parameters.DATABASE_NAME);
			if (eventCohortDefinition != null) {
				results.append(eventCohortDefinition.getIndexEventType());
			}
			if (anonymizePatientID) {
				results.append(String.valueOf(patient.getAnonymizedPatientId()));
			} else
				results.append(patient.getPatientID());
			results.append(patient.getGender());
			if (SASCompare){
				results.append(DateUtilities.daysToDate(patient.getBirthDate()));
				results.append(DateUtilities.daysToDate(patient.getPopulationStartDate()));
				results.append(DateUtilities.daysToDate(patient.getPopulationEndDate()));
				results.append(DateUtilities.daysToDate(patient.getCohortStartDate()));
				results.append(DateUtilities.daysToDate(patient.getCohortEndDate()));
			}
			//results.append(DateUtilities.daysToDate(dateFirstTreatAsthma));
			//results.append(String.valueOf(patient.getCohortStartDate()-dateFirstTreatAsthma));
			if (ageAtStartAsFraction) {
				results.append(precisionFormat.format(patient.getAge(patient.getCohortStartDate())));
			}
			else {
				results.append(Integer.toString(patient.getAgeAtDateInYears(patient.getCohortStartDate())));
			}
			results.append(Integer.toString(DateUtilities.getYearFromDays(patient.getCohortStartDate())));
			
			results.append(precisionFormat.format((patient.getCohortStartDate() - patient.getPopulationStartDate())/365.25));
			results.append(precisionFormat.format((patient.getPopulationEndDate() - patient.getCohortStartDate())/365.25));

			if (ageGroups.size()>0) {
				if (ageGroupDefinition.getAgeGroups(patient.getAgeAtDateInYears(patient.getCohortStartDate())).size()>0)
					results.append(ageGroupDefinition.getAgeGroups(patient.getAgeAtDateInYears(patient.getCohortStartDate())).get(0).getLabel());
				else
					results.append("UNKNOWN");
			}
			
			if (outputPrevalentIncident) {
				if(patient.getExtended().getAttributeAsString("ISPREVALENT").equals("YES"))
					results.append("PREVALENT");
				else
					results.append("INCIDENT");
			}
			
			if (firstEventMonthYearList.size() > 0)
				results.append(getFirstEvents(patient, firstEventMonthYearList));
			
			if (comorbiditiesList.size() > 0)
				results.append(IndexDateUtilities.processComorbidities(patient, patient.getCohortStartDate(), comorbiditiesList, defaultWindowStartComorbidity, comorbiditiesBag));
			
			if (daysToNextEvent.size() > 0)
				results.append(IndexDateUtilities.processDaysToNextEvent(patient, patient.getCohortStartDate(), daysToNextEventList));
			
			if (daysToPreviousEvent.size() > 0)
				results.append(IndexDateUtilities.processDaysToPreviousEvent(patient, patient.getCohortStartDate(), daysToPreviousEventList));
			
			if (eventCount.size() > 0)
				results.append(IndexDateUtilities.processEventCounts(patient, patient.getCohortStartDate(), eventCountsList, -defaultWindowStartEvent, eventCountsBag));
			
			if (prescriptionsList.size() > 0)
				results.append(IndexDateUtilities.processPrescriptions(patient, patient.getCohortStartDate(), prescriptionsList, defaultWindowStartPrescription, prescriptionsBag));
			
			if (daysToNextPrescriptionsList.size() > 0)
				results.append(IndexDateUtilities.processDaysToNextPrescription(patient, patient.getCohortStartDate(), daysToNextPrescriptionsList));
			
			if (daysSincePreviousPrescriptionsList.size() > 0)
				results.append(IndexDateUtilities.processDaysToPreviousPrescription(patient, patient.getCohortStartDate(), daysSincePreviousPrescriptionsList));
			
			if (measurementsList.size() > 0)
				results.append(IndexDateUtilities.processMeasurements(patient, patient.getCohortStartDate(), measurementsList, defaultWindowStartMeasurement, measContinuesBag, measCategoryBag, true));
			
			if (measurementCount.size() > 0)
				results.append(IndexDateUtilities.processMeasurementCounts(patient, patient.getCohortStartDate(), measurementCountsList, -defaultWindowStartMeasurement, measurementCountsBag));
			
			if (daysToNextMeasurement.size() > 0)
				results.append(IndexDateUtilities.processDaysToNextMeasurement(patient, patient.getCohortStartDate(), daysToNextMeasurementList));
			
			if (daysToPreviousMeasurement.size() > 0)
				results.append(IndexDateUtilities.processDaysToPreviousMeasurement(patient, patient.getCohortStartDate(), daysToPreviousMeasurementList));
			
			//parameters at time of death
			if (deathLabels.size() > 0) {
				int dateDeath = IndexDateUtilities.getFirstEventDate(patient, deathLabels,true);
				if (dateDeath != -1){
					//results.append(DateUtilities.daysToDate(dateDeath));
					results.append(IndexDateUtilities.getFirstEventType(patient, deathLabels, true));
					int dateYrDth = Math.max(dateDeath - 365,patient.cohortStartDate);
					results.append(String.valueOf(dateYrDth-dateDeath));
					results.append(precisionFormat.format(patient.getAgeAtDate(dateDeath)/365.25));
					int ageDeath = patient.getAgeAtDateInYears(dateDeath);
					if (ageGroups.size()>0) {
						if (ageGroupDefinition.getAgeGroups(ageDeath).size()>0)
							results.append(ageGroupDefinition.getAgeGroups(ageDeath).get(0).getLabel());
						else
							results.append("Unknown");
					}
					
					if (comorbiditiesDeathList.size() > 0)
						results.append(IndexDateUtilities.processComorbidities(patient, dateDeath, comorbiditiesDeathList, defaultWindowStartComorbidity, comorbiditiesDeathBag));
					if (prescriptionsDeathList.size() > 0)
						results.append(IndexDateUtilities.processPrescriptions(patient, dateDeath, prescriptionsDeathList, defaultWindowStartPrescription, prescriptionsDeathBag));
					if (measurementsDeathList.size() > 0)
						results.append(IndexDateUtilities.processMeasurements(patient, dateDeath, measurementsDeathList, defaultWindowStartMeasurement, measContinuesDeathBag, measCategoryDeathBag, false));
	
				} else {
					//add missing values
					results.append("");
					results.append("");
					for (int i = 0; i<comorbiditiesDeathList.size(); i++)
						results.append("");
					for (int i = 0; i<measurementsDeathList.size(); i++)
						results.append("");
				}
			}
			results.append(Integer.toString(patient.cohortEndDate - patient.cohortStartDate));

			if (multipleValueMeasurementRows.size() > 0) {
				for (DelimitedStringBuilder multipleValueMeasurementRow : multipleValueMeasurementRows) {
					Jerboa.getOutputManager().writeln(this.resultsFile,results.toString() + multipleValueMeasurementRow.GetDelimiter() + multipleValueMeasurementRow.toString(),true);
				}
			}
			else {
				Jerboa.getOutputManager().writeln(this.resultsFile,results.toString(),true);
			}
			
			//TODO Fill the intermediate file further
			if (intermediateFiles) {
				// Create the result file
				DelimitedStringBuilder  intermediateResults = new DelimitedStringBuilder();
				intermediateResults.append(Parameters.DATABASE_NAME);
				if (eventCohortDefinition != null) {
					results.append(eventCohortDefinition.getIndexEventType());
				}
				intermediateResults.append(patient.getPatientID());
				intermediateResults.append(patient.getGender());
				intermediateResults.append(DateUtilities.daysToDate(patient.getBirthDate()));
				intermediateResults.append(DateUtilities.daysToDate(patient.getPopulationStartDate()));
				intermediateResults.append(DateUtilities.daysToDate(patient.getPopulationEndDate()));
				intermediateResults.append(DateUtilities.daysToDate(patient.getCohortStartDate()));
				intermediateResults.append(DateUtilities.daysToDate(patient.getCohortEndDate()));
				Jerboa.getOutputManager().writeln(this.intermediateFile,intermediateResults.toString(),true);
			}
		}

		return patient;
	}

	@Override
	public void outputResults() {
		Jerboa.getOutputManager().closeAll();
		
	}

	@Override
	public void setNeededFiles() {
		setRequiredFile(DataDefinition.PATIENTS_FILE);
		if ((comorbidities.size() + comorbiditiesDeath.size() + firstEventYearMonth.size() + daysToNextEvent.size() + daysToPreviousEvent.size() + eventCount.size()) > 0)
			setRequiredFile(DataDefinition.EVENTS_FILE);
		if ((prescriptions.size() + daysToNextPrescription.size() + daysSincePreviousPrescription.size() + prescriptionsDeath.size()) > 0)
			setRequiredFile(DataDefinition.PRESCRIPTIONS_FILE);
		if (((multipleValueMeasurements.size() + measurements.size() + measurementsDeath.size() + measurementCount.size() + daysToNextMeasurement.size() + daysToPreviousMeasurement.size()) > 0) || (measurements.size() > 0))
			setRequiredFile(DataDefinition.MEASUREMENTS_FILE);
	}

	@Override
	public void setNeededExtendedColumns() {
		// TODO Auto-generated method stub
		
	}

	@Override
	public void setNeededNumericColumns() {
		// TODO Auto-generated method stub
		
	}

	/**
	 * Determines the year and month of the first occurrence of the events
	 * in eventsList. It returns a comma-separated string with YearEventType,
	 * MonthEventType values.
	 * @param patient				- patient object
	 * @param eventsList			- items with the events of interest
	 * @return
	 */
	public String getFirstEvents(Patient patient, ItemList eventsList){
		Map<String,Integer> foundEvents = new HashMap<String,Integer>(); 

		for (Event event : patient.getEvents()) {
			for (Item item : eventsList) {
				if (event.inList(item.getLookup())) {
					//only count the first events
					if (!foundEvents.containsKey(item.getLabel()))
							foundEvents.put(item.getLabel(),Integer.valueOf(event.date));
				}
			}
		}	

		DelimitedStringBuilder result = new DelimitedStringBuilder(",");
		for (Item item : eventsList) {
			if (foundEvents.containsKey(item.getLabel())){
				int eventDate = foundEvents.get(item.getLabel());
				result.append(DateUtilities.getYearFromDays(eventDate).toString());
				result.append(DateUtilities.getMonthFromDays(eventDate).toString());
				result.append(precisionFormat.format(patient.getAge(eventDate)));
			} else {
				result.append(" ");
				result.append(" ");
				result.append(" ");
			}
		}
		return result.toString();
	}
}
