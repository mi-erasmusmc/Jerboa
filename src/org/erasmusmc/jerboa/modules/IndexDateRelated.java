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
 * Author: Marius Gheorghe (MG) - department of Medical Informatics						  *			
 * 																						  *	
 * $Rev:: 3961              $:  Revision of last commit                                   *
 * $Author:: root           $:  Author of last commit                                     *
 * $Date:: 2013-05#$:  Date and time (CET) of last commit								  *
 ******************************************************************************************/

package org.erasmusmc.jerboa.modules;

import java.security.InvalidParameterException;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.commons.collections.bag.HashBag;
import org.apache.commons.io.FilenameUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.text.StrBuilder;
import org.erasmusmc.jerboa.Jerboa;
import org.erasmusmc.jerboa.dataClasses.Measurement;
import org.erasmusmc.jerboa.dataClasses.Event;
import org.erasmusmc.jerboa.dataClasses.Patient;
import org.erasmusmc.jerboa.dataClasses.Prescription;
import org.erasmusmc.jerboa.engine.Module;
import org.erasmusmc.jerboa.modifiers.PopulationDefinition;
import org.erasmusmc.jerboa.modifiers.PrescriptionCohortDefinition;
import org.erasmusmc.jerboa.config.DataDefinition;
import org.erasmusmc.jerboa.config.Parameters;
import org.erasmusmc.jerboa.utilities.DateUtilities;
import org.erasmusmc.jerboa.utilities.DelimitedStringBuilder;
import org.erasmusmc.jerboa.utilities.ExtendedMultiKey;
import org.erasmusmc.jerboa.utilities.FileUtilities;
import org.erasmusmc.jerboa.utilities.Item;
import org.erasmusmc.jerboa.utilities.ItemList;
import org.erasmusmc.jerboa.utilities.Logging;
import org.erasmusmc.jerboa.utilities.MultiKeyBag;
import org.erasmusmc.jerboa.utilities.StringUtilities;
import org.erasmusmc.jerboa.utilities.Wildcard;
import org.erasmusmc.jerboa.utilities.stats.HistogramStats;
import org.erasmusmc.jerboa.utilities.stats.formulas.WilsonCI;

/**
 * This module outputs descriptives in a window relative to cohort start or a fixed date
 * @author PR
 *
 */
public class IndexDateRelated extends Module{

	/**
	 * List of comorbidities that are checked for their presence before and at index date
	 * multiple occurrences are ignored
	 * Format: EventType;Description
	 * EventType		- the variable name used in the event file
	 * Label            - the label, when empty the event type is used 
	 * Description 		- the text used in the result tables
	 * 
	 * Optional parameter that overrule the timeWindowComorbidity setting
	 * TimewindowStart	- start of time window before index date. Start day is not included. -1 = All history 
	 * 
	 * Optional parameter that overrules the index date at TimeWindowEnd. Note that here the end day is not included!
	 * TimewindowEnd	- end of time window relative to index date. End day is not included. cohortEnd = end of cohort
	 * 
	 * For example: "MI;Myocardial Infarction"				  : Look for MI in the window  indexDate-timeWindowComoorbidities< X <=indexDate
	 * 				"MI;Myocardial Infarction;365"   		  : Look for MI in the window  indexDate-365< X <=indexDate
	 * 				"MI;Myocardial Infarction;365;10"  		  : Look for MI in the window  indexDate-365< X <indexDate + 10
	 * 				"MI;Myocardial Infarction;365;cohortEnd"  : Look for MI in the window  indexDate-365< X <cohortEnd
	 * 
	 */	
	public List<String> comorbidities = new ArrayList<String>();

	/*
	 * determines if the index date is included in case no window is defined
	 */
	public boolean comorbiditiesIncludeIndexdate;

	/**
	 * List of events to be checked for their presence in cohort time. 
	 * patient is determined for this list
	 * Format: EventType;Description
	 * EventType		- the variable name used in the event file, multiple event are allowed
	 * Label            - the label, when empty the event type is used 
	 * Description 		- the text used in the result tables
	 * 
	 * Example: "RA;RA;RA Event"
	 */	
	public List<String> events = new ArrayList<String>();

	/**
	 * List of events to be counted before cohort entry. The number of occurrences per event type.
	 * Format: EventType;Description
	 * EventType		- the variable name used in the event file, multiple event are allowed
	 * Description 		- the text used in the result tables
	 * 
	 * Example: "RA;RA Event"
	 */	
	public List<String> priorEventCounts = new ArrayList<String>();

	/**
	 * List of events to be counted during the specified period around the cohort entry.
	 * The number of occurrences per event type. 
	 * Format:
	 *   EventType;Label;Description;CONTNUOUS;WindowStart;WindowEnd
	 * 
	 * where
	 * 
	 * EventType		- the variable name used in the event file, multiple event are allowed
	 * Label            - the label, when empty the event type is used 
	 * Description 		- the text used in the result tables
	 * WindowStart      - Start of the time window (inclusive) relative to the index date or empty (= all history)
	 * WindowEnd        - End of the time window (exclusive) relative to the index date or empty (= all future)
	 * 
	 * Example:
	 *   "RA;RA;RA Event Count;continuous;;183"
	 */	
	public List<String> eventCounts = new ArrayList<String>();

	/**
	 * List of events to be counted during the specified period around the cohort entry and
	 * checked against a minimum and maximum value in a specified period. 
	 * Format:
	 * 
	 *   EventType;Label;Description;WindowStart;WindowEnd;MinCount;MaxCount;CountPeriod
	 * 
	 * where
	 * 
	 * EventType		- the variable name used in the event file, multiple event are allowed
	 * Label            - the label, when empty the event type is used 
	 * Description 		- the text used in the result tables
	 * WindowStart      - Start of the time window (inclusive) relative to the index date or empty (= all history)
	 * WindowEnd        - End of the time window (exclusive) relative to the index date or empty (= all future)
	 * MinCount         - The minimum number of events to be counted in a period of CountPeriod days.
	 * MaxCount         - The maximum number of events to be counted in a period of CountPeriod days.
	 * CountPeriod      - The period in which the MinCount and MaxCount should be found. Empty is WindowStart - WindowEnd
	 * 
	 * Example:
	 *   "COPD;DefiniteCOPD;Definite COPD;;183;2;;366"
	 */	
	public List<String> eventCountDefinitions = new ArrayList<String>();

	/**
	 * if set the counts higher or equal than this value are aggregated
	 */
	public int eventCountsHigherEqual;

	/**
	 * List of prescriptions to be counted before cohort entry. The number of occurrences per
	 * patient is determined for this list
	 * Format: ATC;Description;Count
	 * ATC				- the ATC, higher levels allowed
	 * Description 		- the text used in the result tables
	 * Indication		- only count if for this indication (multiple allowed)
	 * 
	 * Example: "H02AB;Corticosteroids;COPD;"
	 */	
	public List<String> prescriptionCounts = new ArrayList<String>();

	/**
	 * if set the counts higher or equal than this value are aggregated
	 */
	public int prescriptionCountsHigherEqual;

	/**
	 * List of measurements of interest to be counted before cohort entry
	 * Format: MeaurementType;Values;Description
	 * MeasurementType	- the variable name used in the measurement file
	 * Label            - The label, when empty the MeasurementType is used.
	 * Description 		- the text used in the result tables
	 * Values			- a comma-separated list of values, 
	 * 					  or "continuous" to define a continuous valued variable
	 * 
	 * Optional parameter that overrule the timeWindowComorbidity setting
	 * TimewindowStart	- start of time window before index date. Start day is not included. -1 = All history 
	 * 
	 * Optional parameter that overrules the index date at TimeWindowEnd. Note that here the end day is not included!
	 * TimewindowEnd	- end of time window relative to index date. End day is not included. cohortEnd = end of cohort
	 * 
	 * Example: "Smoker;Smoking status;Smoking status;1,2"
	 * 			"BMI;BMI;Body Mass Index;continuous"
	 */	
	public List<String> measurements = new ArrayList<String>();

	/**
	 * List of ATC codes and their indications to be counted
	 * Format: ATC;Indications;Description
	 * ATC				- a comma-separated list of ATC codes (higher level is allowed).
	 * Label            - The label, when empty the ATC code is used.
	 * Description 		- the text used in the result tables.
	 * Indications		- a comma-separated list of indications.
	 * 
	 * Example: "R03BB06;NVA237;NVA237;COPD,ASTHMA,COPDASTHMA,OTHER"
	 */
	public List<String> indications = new ArrayList<String>();

	/**
	 * List of ATC codes to be counted prior cohort entry
	 * Format: ATC;Description
	 * ATC				- a comma-separated list of ATC codes (higher level is allowed)
	 * Description 		- the text used in the result tables	
	 * 
	 * Example: "R03AC02,R03AC03,RO3AC04;Single-ingredient short-acting B2 agonists"
	 * 			"R03AC;Single-ingredient short-acting B2 agonists"
	 */
	public List<String> priorATCOfInterest = new ArrayList<String>();

	/**
	 * Determines if the index date is include in the search episode or not.
	 */
	public boolean priorATCIncludeIndexDate;

	/**
	 * List of ATC codes to be checked for concomitant use at index date
	 * Format: ATC;Description
	 * ATC of Interest	- a comma-separated list of ATC codes (higher level is allowed)
	 * Label - The label, when empty ATC is used.
	 * Description 		- the text used in the result tables	
	 * Indication of interest - list of indications or empty 
	 * WindowStart - Start of the time window (inclusive) relative to the index date or empty (= 0)
	 * WindowEnd   - End of the time window (exclusive) relative to the index date or empty (= 1)
	 * 
	 * Example: "R03BB06;NVA237+ICS;NVA237+ICS;COPD,COPDASHTMA"
	 */
	public List<String> concomitantATCs = new ArrayList<String>();

	/**
	 * List of events for which the time since needs to be counted relative to cohort start
	 * Format: <EventType>;<Label>;<Description>;CONTINUOUS
	 * EventType		- the name of the event in the event file
	 * Label            - the name of the event in the output
	 * Description		- the text used in the result tables
	 * 
	 * Example: "DM;DM;Diabetes Melitus;CONTINUOUS"
	 */
	public List<String> timeSinceEvents = new ArrayList<String>();

	/**
	 * List of events for which the time to the next event after the index date needs to be counted
	 * Format: <EventType>;<Label>;<Description>;CONTINUOUS
	 * EventType		- the name of the event in the event file
	 * Label            - the name of the event in the output
	 * Description		- the text used in the result tables
	 * 
	 * Example: "DM;DM;Diabetes Melitus;CONTINUOUS"
	 */
	public List<String> timeToEvents = new ArrayList<String>();

	/**
	 * Value (years) used to put an event in the history without knowing the exact date
	 * This means this event or measurement is excluded from the TimeSince calculation
	 */
	public int ignoreDeltaEvents;

	/**
	 * List of measurements for which the time since needs to be counted relative to cohort start
	 * Format: <MeasurementType>;<Label>;<Description>;CONTINUOUS
	 * MeasurementType	- the name of the measurement in the measurement file
	 * Label            - the name of the measurement in the output
	 * Description		- the text used in the result tables
	 * 
	 * Example: "FEV1Severity;Spirometry;CONTINUOUS"
	 */
	public List<String> timeSinceMeasurements = new ArrayList<String>();

	/**
	 * Value (years) used to put a measurement in the history without knowing the exact date
	 * This means this event or measurement is excluded from the TimeSince calculation
	 */
	public int ignoreDeltaMeasurements;

	/**
	 * List of drug for which the time to needs to be counted relative to cohort start
	 * Format: <ATC>,...,<ATC>;<Label>;<Description>;CONTINUOUS
	 * ATC          	- the ATC-code of the drug
	 * Label            - the name of the drug in the output
	 * Description		- the text used in the result tables
	 * 
	 * Example: "R03BB06;NVA;Time to NVA in cohort time;CONTINUOUS"
	 */
	public List<String> timeToDrugs = new ArrayList<String>();

	/**
	 * List of drug for which the exposure needs to be counted during cohort time
	 * Format: <ATC>,...,<ATC>;<Label>;<Description>;CONTINUOUS
	 * ATC          	- the ATC-code of the drug
	 * Label            - the name of the drug in the output
	 * Description		- the text used in the result tables
	 * 
	 * Example: "R03BB06;NVA;Exposure to NVA in cohort time;CONTINUOUS"
	 */
	public List<String> exposureToDrugs = new ArrayList<String>();

	/**
	 * Value (years) used to put a drug in the future without knowing the exact date
	 * This means this event or drug is excluded from the TimeTo calculation
	 */
	public int ignoreDeltaDrugs;

	/**
	 * List of dosages to be counted in the prescription file.
	 * Format: ATC;Dosages;Description
	 * ATC				- a comma-separated list of ATC codes (higher level is allowed)
	 * Label            - the name of the drug in the output
	 * Description		- the text used in the result tables
	 * Dosages			- a comma-sperated list of dosage strings
	 * 
	 * Example:	 "R03BB06;Dosage R03BB06;Dosage R03BB06;0.5,1,2,999"
	 * */
	public List<String> dosages = new ArrayList<String>();

	/**
	 * Look back period in days for presents of prescriptions.
	 */
	public int timeWindowPriorATC;

	/**
	 * Look back period in days for the dose.
	 */
	public int timeWindowDose;

	/**
	 * Look back period in days for history of events.
	 */
	public int timeWindowComorbidity;

	/**
	 * Look back period in days for counting events.
	 * Default: -1 = all history
	 */
	public int timeWindowPriorEventCount;

	/**
	 * Look back period in days for prescription counts.
	 */	
	public int timeWindowPrescriptionCount;

	/**
	 * Look back period in days for measurements.
	 */	
	public int timeWindowMeasurement;

	/**
	 * If true concomitant use is only determined on the index date/cohort start date.
	 */
	public boolean concomitantOnlyIndexDate;

	/**
	 * List of age groups as min;max;label
	 */
	public List<String> ageGroups = new ArrayList<String>();

	/**
	 * Will output a parameter with the number of patients longer in the cohort than
	 * the value specified: label;nrdays
	 */
	public List<String> cohortTimeHigherThan = new ArrayList<String>();

	/**
	 * Determines if the labels or descriptions are use in the output
	 */
	public boolean useLabel = false;

	/**
	 * if true the output is sorted alphabetically, otherwise the order in the script is maintained.
	 */
	public boolean sortOutput;

	/**
	 * The number of decimals shown in the results.
	 * Possible values: 1 or 2
	 */
	public int precision = 1;

	/**
	 * If true an extra patient level output file is created
	 */
	public boolean patientLevelOutput;
	
	/**
	 * If true for "YES" and "NO" are used for boolean values, otherwise "1" and "0". 
	 */
	public boolean outputYesNo;

	// Debug settings
	/** 
	 * If true an anonymizedPatientID is used instead of the original patient ID
	 */
	public boolean anonymizePatientID;

	//output intermediate files
	private String outputFile;		

	private List<AgeGroup> ageGroupsParsed = null;

	//bags
	private MultiKeyBag ageBag;
	private MultiKeyBag ageGroupBag;
	private MultiKeyBag cohortTimeBag; //TODO: maybe this belongs to the cohortDefinition Module? Discuss with Mees
	private MultiKeyBag priorEventCountsBag;
	private MultiKeyBag eventCountsBag;
	private HashBag eventCountDefinitionsBag;
	private HashBag eventsBag;
	private HashBag comorbiditiesBag;
	private HashBag concomitantBag;
	private MultiKeyBag measContinuesBag;
	private MultiKeyBag measCategoryBag;
	private MultiKeyBag timeSinceEventsBag;
	private MultiKeyBag timeToEventsBag;
	private MultiKeyBag timeSinceMeasurementsBag;
	private MultiKeyBag timeToDrugsBag;
	private MultiKeyBag exposureToDrugsBag;
	private MultiKeyBag prescriptionsBag; 
	private MultiKeyBag indicationsBag;
	private MultiKeyBag dosagesBag;	
	private MultiKeyBag prescriptionCountsBag;	

	//counters
	private long nrPatients = 0;
	private long nrFemales = 0;
	private long nrMales = 0;

	private double percentageFemales = 0;
	private double percentageMales = 0;
	private double ageMean = 0;
	private double ageSD = 0;
	private double cohortMedian = 0;
	@SuppressWarnings("unused")
	private double cohortMin = 0;
	@SuppressWarnings("unused")
	private double cohortMax = 0;
	private List<Long>  cohortHigher;

	// Precision
	DecimalFormat precisionFormat = StringUtilities.DECIMAL_FORMAT_FORCE_PRECISION;

	//Item lists used for the aggregated
	private ItemList comorbiditiesList;
	private ItemList prescriptionsList;
	private ItemList priorEventCountsList;
	private ItemList eventCountsList;
	private ItemList eventCountDefinitionsList;
	private ItemList eventsList;
	private ItemList measurementsList;
	private ItemList timeSinceEventsList;
	private ItemList timeToEventsList;
	private ItemList timeSinceMeasurementsList;	
	private ItemList timeToDrugsList;	
	private ItemList exposureToDrugsList;
	private ItemList indicationsList;
	private ItemList dosagesList;
	private ItemList prescriptionCountsList;
	private ItemList prescriptionContinuousList;
	private ItemList prescriptionsConcomitantList;

	//Intermediate files
	private String intermediateTimeSinceEventsFile;
	private String intermediateTimeToEventsFile;
	private String intermediateTimeSinceMeasurementsFile;
	private String intermediateTimeToDrugsFile;
	private String intermediateExposureToDrugsFile;
	private String intermediatePrescriptonsFile;
	private String intermediateComorbiditiesFile;
	private String intermediateMeasurementsFile;
	private String intermediatePriorEventCountsFile;
	private String intermediateEventCountsFile;
	private String intermediateEventCountDefinitionsFile;
	private String intermediatePrescriptionCountsFile;
	private String intermediatePrescriptionConcomitantFile;
	private String intermediateEventsFile;
	private String intermediateAnonymizationFile;
	private String patientLevelOutputFile;

	//Hook to modifiers that need to be accessed
	PopulationDefinition population;
	PrescriptionCohortDefinition cohort;

	@Override
	public void setNeededFiles() {
		setRequiredFiles(new int[]{ DataDefinition.PATIENTS_FILE, DataDefinition.EVENTS_FILE,
				DataDefinition.PRESCRIPTIONS_FILE, DataDefinition.MEASUREMENTS_FILE} );
	}

	@Override
	public void setNeededExtendedColumns() {/* NOTHING TO ADD */}

	@Override
	public void setNeededNumericColumns() {
		// TODO Auto-generated method stub
		
	}

	@Override
	public boolean init() {

		//get the needed modifiers
		population = (PopulationDefinition)this.getModifier(PopulationDefinition.class);
		cohort = (PrescriptionCohortDefinition)this.getModifier(PrescriptionCohortDefinition.class);

		comorbiditiesList = new ItemList(this.useLabel,0);
		prescriptionsList = new ItemList(this.useLabel,0);
		priorEventCountsList = new ItemList(this.useLabel,0);
		eventCountsList = new ItemList(this.useLabel,0);
		eventCountDefinitionsList = new ItemList(this.useLabel,0);
		eventsList = new ItemList(this.useLabel,0);
		measurementsList = new ItemList(this.useLabel,0);
		timeSinceEventsList = new ItemList(this.useLabel,0);	
		timeToEventsList = new ItemList(this.useLabel,0);	
		timeSinceMeasurementsList = new ItemList(this.useLabel,0);	
		timeToDrugsList = new ItemList(this.useLabel,0);
		exposureToDrugsList = new ItemList(this.useLabel,0);
		indicationsList = new ItemList(this.useLabel,0);
		dosagesList = new ItemList(this.useLabel,0);
		prescriptionCountsList = new ItemList(this.useLabel,0);
		prescriptionContinuousList = new ItemList(this.useLabel,0);
		prescriptionsConcomitantList = new ItemList(this.useLabel,0);
		boolean initOK = true;

		// Check precision value
		if ((precision == 1) || (precision == 2)) {
			precisionFormat = (precision == 1) ? StringUtilities.DECIMAL_FORMAT_1_FORCE_PRECISION : StringUtilities.DECIMAL_FORMAT_FORCE_PRECISION;

			this.outputFile = StringUtilities.addSuffixToFileName(outputFileName, ".txt", true);

			initOK = FileUtilities.writeLineToFile(outputFile, "Results IndexDateRelated Module " + DateUtilities.getCurrentDateAndTime(), false);
			FileUtilities.writeLineToFile(outputFile, "", true);

			//parse the age groups
			ageGroupsParsed = new ArrayList<AgeGroup>();
			for (String agegroup : ageGroups){
				String[] splitGroup = agegroup.split(";");
				if (splitGroup.length!=3)
					throw new InvalidParameterException("Please check the script. agegroup [" + agegroup + "] should be label;min;max");
				ageGroupsParsed.add(new AgeGroup(splitGroup[0],Integer.valueOf(splitGroup[1]), Integer.valueOf(splitGroup[2])));
			}

			//check the cohortTimeHigherThan
			cohortHigher = new ArrayList<Long>();
			for (String def : cohortTimeHigherThan){
				String[] split = def.split(";");
				if (split.length!=2)
					throw new InvalidParameterException("Please check the script. cohortTimeHigherThan [" + def + "] should be label;days");
			}

			//initialize bags
			this.ageBag = new MultiKeyBag(); 	
			this.ageGroupBag = new MultiKeyBag(); 	
			this.cohortTimeBag = new MultiKeyBag(); 	
			this.priorEventCountsBag = new MultiKeyBag();  
			this.eventCountsBag = new MultiKeyBag();
			this.eventCountDefinitionsBag = new HashBag();
			this.eventsBag = new HashBag(); 
			this.comorbiditiesBag = new HashBag(); 
			this.concomitantBag = new HashBag(); 
			this.prescriptionsBag = new MultiKeyBag(); 
			this.measContinuesBag = new MultiKeyBag();
			this.measCategoryBag = new MultiKeyBag(); 
			this.timeSinceEventsBag = new MultiKeyBag();  
			this.timeToEventsBag = new MultiKeyBag();  
			this.timeSinceMeasurementsBag = new MultiKeyBag();  
			this.timeToDrugsBag = new MultiKeyBag();
			this.exposureToDrugsBag = new MultiKeyBag();
			this.indicationsBag = new MultiKeyBag();
			this.dosagesBag = new MultiKeyBag();
			this.prescriptionCountsBag = new MultiKeyBag();

			//parse module parameters to item lists
			try {

				comorbiditiesList.parse(comorbidities);
				eventsList.parse(events);
				prescriptionsList.parse(priorATCOfInterest);
				prescriptionsConcomitantList.parse(concomitantATCs);
				priorEventCountsList.parseWithValue(priorEventCounts);
				eventCountsList.parseWithValue(eventCounts);
				eventCountDefinitionsList.parse(eventCountDefinitions);
				timeSinceEventsList.parseWithValue(timeSinceEvents);
				timeToEventsList.parseWithValue(timeToEvents);
				timeSinceMeasurementsList.parseWithValue(timeSinceMeasurements);
				timeToDrugsList.parseWithValue(timeToDrugs);
				exposureToDrugsList.parseWithValue(exposureToDrugs);
				dosagesList.parse(dosages);	
				prescriptionCountsList.parse(prescriptionCounts);

				measurementsList.parseWithValue(measurements); //continuous is determined based on parameter "continuous"
				indicationsList.parseWithValue(indications);

			} catch (InvalidParameterException e) {
				Logging.add(e.getMessage());
				return false;
			}

			if (intermediateFiles){

				if (!timeSinceEvents.isEmpty()){
					intermediateTimeSinceEventsFile = StringUtilities.addSuffixToFileName(intermediateFileName, "_timeSinceEvents");
					initOK &= Jerboa.getOutputManager().addFile(intermediateTimeSinceEventsFile);
					Jerboa.getOutputManager().writeln(intermediateTimeSinceEventsFile, "PatientID,EventType,IndexDate,EventDate,Delta", false);
				}

				if (!timeToEvents.isEmpty()){
					intermediateTimeToEventsFile = StringUtilities.addSuffixToFileName(intermediateFileName, "_timeToEvents");
					initOK &= Jerboa.getOutputManager().addFile(intermediateTimeToEventsFile);
					Jerboa.getOutputManager().writeln(intermediateTimeToEventsFile, "PatientID,EventType,IndexDate,EventDate,Delta", false);
				}

				if (!timeSinceMeasurements.isEmpty()){
					intermediateTimeSinceMeasurementsFile = StringUtilities.addSuffixToFileName(intermediateFileName, "_timeSinceMeasurements");
					initOK &= Jerboa.getOutputManager().addFile(intermediateTimeSinceMeasurementsFile);
					Jerboa.getOutputManager().writeln(intermediateTimeSinceMeasurementsFile, "PatientID,EventType,IndexDate,MeasurementDate,Delta", false);
				}

				if (!timeToDrugs.isEmpty()){
					intermediateTimeToDrugsFile = StringUtilities.addSuffixToFileName(intermediateFileName, "_timeToDrugs");
					initOK &= Jerboa.getOutputManager().addFile(intermediateTimeToDrugsFile);
					Jerboa.getOutputManager().writeln(intermediateTimeToDrugsFile, "PatientID,EventType,IndexDate,PrescriptionDate,Delta", false);
				}

				if (!exposureToDrugs.isEmpty()){
					intermediateExposureToDrugsFile = StringUtilities.addSuffixToFileName(intermediateFileName, "_exposureToDrugs");
					initOK &= Jerboa.getOutputManager().addFile(intermediateExposureToDrugsFile);
					Jerboa.getOutputManager().writeln(intermediateExposureToDrugsFile, "PatientID,EventType,IndexDate,PrescriptionDate,Exposure", false);
				}

				if (!comorbidities.isEmpty()){
					intermediateComorbiditiesFile = StringUtilities.addSuffixToFileName(intermediateFileName, "_comorbidities");
					initOK &= Jerboa.getOutputManager().addFile(intermediateComorbiditiesFile);
					Jerboa.getOutputManager().writeln(intermediateComorbiditiesFile, "PatientID,EventType", false);
				}

				if (!priorATCOfInterest.isEmpty()){

					intermediatePrescriptonsFile = StringUtilities.addSuffixToFileName(intermediateFileName, "_prescription");
					initOK &= Jerboa.getOutputManager().addFile(intermediatePrescriptonsFile);
					Jerboa.getOutputManager().writeln(intermediatePrescriptonsFile, "PatientID,Type,Prescription,PrescriptionStartDate,PrescriptionEndDate,IndexDate,Value", false);

				}

				if (!concomitantATCs.isEmpty()){
					intermediatePrescriptionConcomitantFile = StringUtilities.addSuffixToFileName(intermediateFileName, "_prescriptionconcomitant");
					initOK &= Jerboa.getOutputManager().addFile(intermediatePrescriptionConcomitantFile);
					Jerboa.getOutputManager().writeln(intermediatePrescriptionConcomitantFile, "PatientID,ATCofInterest,Indication,ConcomitantATC", false);

				}

				if (!measurements.isEmpty()){
					intermediateMeasurementsFile = StringUtilities.addSuffixToFileName(intermediateFileName, "_measurements");
					initOK &= Jerboa.getOutputManager().addFile(intermediateMeasurementsFile);
					Jerboa.getOutputManager().writeln(intermediateMeasurementsFile, "PatientID,MeasurementType,IndexDate,Value", false);
				}


				if (!prescriptionCounts.isEmpty()){
					intermediatePrescriptionCountsFile = StringUtilities.addSuffixToFileName(intermediateFileName, "_prescriptioncount");
					initOK &= Jerboa.getOutputManager().addFile(intermediatePrescriptionCountsFile);
					Jerboa.getOutputManager().writeln(intermediatePrescriptionCountsFile, "PatientID,Prescription,Indication,Count", false);
				}


				if (!priorEventCounts.isEmpty()){
					intermediatePriorEventCountsFile = StringUtilities.addSuffixToFileName(intermediateFileName, "_prioreventcount");
					initOK &= Jerboa.getOutputManager().addFile(intermediatePriorEventCountsFile);
					Jerboa.getOutputManager().writeln(intermediatePriorEventCountsFile, "PatientID,EventType,Count", false);
				}


				if (!eventCounts.isEmpty()){
					intermediateEventCountsFile = StringUtilities.addSuffixToFileName(intermediateFileName, "_eventcount");
					initOK &= Jerboa.getOutputManager().addFile(intermediateEventCountsFile);
					Jerboa.getOutputManager().writeln(intermediateEventCountsFile, "PatientID,EventType,Count", false);
				}


				if (!eventCountDefinitions.isEmpty()){
					intermediateEventCountDefinitionsFile = StringUtilities.addSuffixToFileName(intermediateFileName, "_eventcountDefinition");
					initOK &= Jerboa.getOutputManager().addFile(intermediateEventCountDefinitionsFile);
					Jerboa.getOutputManager().writeln(intermediateEventCountDefinitionsFile, "PatientID,EventType,Valid", false);
				}


				if (!events.isEmpty()){
					intermediateEventsFile = StringUtilities.addSuffixToFileName(intermediateFileName, "_events");
					initOK &= Jerboa.getOutputManager().addFile(intermediateEventsFile);
					Jerboa.getOutputManager().writeln(intermediateEventsFile, "PatientID,EventType", false);
					Jerboa.getOutputManager().writeln(intermediateEventsFile, "", true);
				}
			}
		}
		else {
			initOK = false;
		}

		// add headers to patient level output file
		if (patientLevelOutput){
			this.patientLevelOutputFile = FilenameUtils.getFullPathNoEndSeparator(outputFileName)+"/"+
					FilenameUtils.getBaseName(outputFileName)+"_patient.csv";
			Jerboa.getOutputManager().addFile(patientLevelOutputFile);
			DelimitedStringBuilder labels = new DelimitedStringBuilder();
			if (prescriptionsList.size()>0)
				labels.append(getLabels(prescriptionsList,""));
			if (prescriptionsConcomitantList.size()>0)
				labels.append(getLabels(prescriptionsConcomitantList,"C_"));
			if (indicationsList.size()>0)
				labels.append(getLabels(indicationsList,""));
			if (dosagesList.size()>0)
				labels.append(getLabels(dosagesList,""));
			if (prescriptionCountsList.size()>0)
				labels.append(getLabels(prescriptionCountsList,""));
			if (comorbiditiesList.size()>0)
				labels.append(getLabels(comorbiditiesList,""));
			if (priorEventCountsList.size()>0)
				labels.append(getLabels(priorEventCountsList,""));
			if (eventCountsList.size()>0)
				labels.append(getLabels(eventCountsList,""));
			if (eventCountDefinitionsList.size()>0)
				labels.append(getLabels(eventCountDefinitionsList,""));
			if (eventsList.size()>0)
				labels.append(getLabels(eventsList,""));
			if (measurementsList.size()>0)
				labels.append(getLabels(measurementsList,""));
			if (timeSinceEventsList.size()>0)
				labels.append(getLabels(timeSinceEventsList,""));
			if (timeToEventsList.size()>0)
				labels.append(getLabels(timeToEventsList,""));
			if (timeSinceMeasurementsList.size()>0)
				labels.append(getLabels(timeSinceMeasurementsList,""));
			if (timeToDrugsList.size()>0)
				labels.append(getLabels(timeToDrugsList,""));
			if (exposureToDrugsList.size()>0)
				labels.append(getLabels(exposureToDrugsList, ""));

			String header = "Database";
			header += "," + "PatientID";
			header += "," + "Gender";
			header += "," + "CohortTime";
			if (cohort != null) {
				header += "," + "CohortTimeUnres";
			}
			header += "," + "FollowUpTime";
			header += "," + "FollowUpPreTime";
			header += "," + "AgeStart";
			header += "," + "AgeGroup";
			header += "," + "YearStart";
			header += ",";
			Jerboa.getOutputManager().writeln(patientLevelOutputFile, header + labels, false);
		}
		
		if (anonymizePatientID) {
			intermediateAnonymizationFile = StringUtilities.addSuffixToFileName(intermediateFileName, "_anonymization");
			initOK &= Jerboa.getOutputManager().addFile(intermediateAnonymizationFile);
			Jerboa.getOutputManager().writeln(intermediateAnonymizationFile, "AnonymousID,PatientID", false);
		}

		return initOK;
	}

	/**
	 * get the labels for the header
	 * @param itemList
	 * @param prefix - added in front of each label
	 * @return comma-seperated string with all the labels
	 */
	private String getLabels(ItemList itemList, String prefix){
		DelimitedStringBuilder result = new DelimitedStringBuilder();
		// do not add multiple descriptions in case of multiple lookups
		String lastDescription="";
		for (Item item : itemList){
			if (!item.getDescription().equals(lastDescription))
				if (this.useLabel)
					result.append(prefix+item.getLabel());
				else
					result.append(prefix+item.getDescription());

			lastDescription = item.getDescription();
		}
		return result.toString();
	}
	/**
	 * Run the indexDateRelated module on a patient.
	 * 
	 */
	public Patient process(Patient patient){
		if (anonymizePatientID) {
			Jerboa.getOutputManager().writeln(intermediateAnonymizationFile, patient.getAnonymizedPatientId() + "," + patient.getPatientID(), true);
		}

		if (patient != null && patient.isInPopulation() && patient.isInCohort()){
			nrPatients++;

			DelimitedStringBuilder result = new DelimitedStringBuilder();

			//Run population definitions
			double age = patient.getAgeAtDate(patient.cohortStartDate)/365.25;
			String agegroup="unknown";
			for (AgeGroup group : ageGroupsParsed) {
				if (group.inAgeGroup(age)) {
					agegroup = group.label;
					break;
				}
			}

			String gender =  patient.gender == 0 ? "Female" : "Male";
			ageGroupBag.add(new ExtendedMultiKey(gender, agegroup));

			ageBag.add(new ExtendedMultiKey(patient.gender, patient.getAgeAtDate(patient.cohortStartDate)));

			cohortTimeBag.add(new ExtendedMultiKey(patient.gender, patient.getCohortTime()));

			for (String def: cohortTimeHigherThan){
				String[] split = def.split(";");
				if (patient.getCohortTime()>Integer.valueOf(split[1]))
					Jerboa.getResultSet().add(patient.getPatientID(), split[0], "1");
				else
					Jerboa.getResultSet().add(patient.getPatientID(), split[0], "0");
			}

			// create patient level output
			if (patientLevelOutput){
				result.append(Parameters.DATABASE_NAME);
				if (anonymizePatientID) {
					result.append(String.valueOf(patient.getAnonymizedPatientId()));
				} else
					result.append(patient.getPatientID());
				result.append(patient.getGender());
				result.append(String.valueOf(patient.getCohortTime()));
				if (cohort != null) {
					int cohortEndUnCensored = cohort.getCohortEndUnCensored();
					result.append(cohortEndUnCensored == -1 ? " " : Integer.toString(cohortEndUnCensored - patient.getCohortStartDate()));
				}
				result.append(String.valueOf(patient.getPopulationEndDate()-patient.getCohortStartDate()));
				result.append(String.valueOf(patient.getCohortStartDate()-patient.getPopulationStartDate()));
				result.append(precisionFormat.format(patient.getAge(patient.getCohortStartDate())));
				result.append(agegroup);
				result.append(String.valueOf(DateUtilities.getYearFromDays(patient.getCohortStartDate())));

				if (prescriptionsList.size()>0)
					result.append(processPrescriptions(patient).toString());
				if (prescriptionsConcomitantList.size()>0)
					result.append(processPrescriptionConcomitant(patient).toString());
				//TODO indicationsList
				//TODO dosagesList
				if (prescriptionCountsList.size()>0)
					result.append(processPrescriptionCounts(patient).toString());
				if (comorbiditiesList.size()>0)
					result.append(processComorbidities(patient).toString());
				if (priorEventCountsList.size()>0)
					result.append(processPriorEventCounts(patient).toString());
				if (eventCountsList.size()>0)
					result.append(processEventCounts(patient).toString());
				if (eventCountDefinitionsList.size()>0)
					result.append(processEventCountDefinitions(patient).toString());
				if (eventsList.size()>0)
					result.append(processEvents(patient).toString());
				if (measurementsList.size()>0)
					result.append(processMeasurements(patient).toString());
				if (timeSinceEventsList.size()>0)
					result.append(processTimeSinceEvents(patient).toString());
				if (timeToEventsList.size()>0)
					result.append(processTimeToEvents(patient).toString());
				if (timeSinceMeasurementsList.size()>0)
					result.append(processTimeSinceMeasurements(patient).toString());
				if (timeToDrugsList.size()>0)
					result.append(processTimeToDrugs(patient).toString());
				if (exposureToDrugsList.size()>0)
					result.append(processExposureToDrugs(patient).toString());

				Jerboa.getOutputManager().writeln(patientLevelOutputFile, result.toString(), true);
			} else {
				processPrescriptions(patient);
				//TODO indicationsList
				//TODO dosagesList
				processPrescriptionConcomitant(patient);
				processPrescriptionCounts(patient);
				processComorbidities(patient);
				processPriorEventCounts(patient);
				processEvents(patient);
				processMeasurements(patient);
				processTimeSinceEvents(patient);
				processTimeToEvents(patient);
				processTimeSinceMeasurements(patient);
				processTimeToDrugs(patient);
			}
		}
		return patient;
	}

	/**
	 * Determines if the prescriptions of interest are used in
	 * the user specified time window before a date. Counts the indications
	 * Updates the prescriptionsBag and indicationsBag
	 * @param Prescriptions - all prescriptions of the patient
	 * @param indexDate - index date
	 */
	private DelimitedStringBuilder  processPrescriptions(Patient patient){
		int indexDate = patient.getCohortStartDate();
		Set<String> foundPrescriptions = new HashSet<String>(); //only count the atc;indication once
		Map<String, String> mapPrescriptions = new HashMap<String, String>();
		for (Item priorATC : prescriptionsList){
			mapPrescriptions.put(priorATC.getDescription(), (outputYesNo ? "NO" : "0"));		
		}
		Map<String, String> mapIndications = new HashMap<String, String>();
		for (Item indication : indicationsList) {
			mapIndications.put(indication.getDescription(), (outputYesNo ? "NO" : "0"));		
		}
		Map<String, String> mapDosages = new HashMap<String, String>();
		for (Item dosage : dosagesList) {
			mapDosages.put(dosage.getDescription(), "UNKNOWN");		
		}

		for (Prescription prescription : patient.getPrescriptions()) {
			// Check if prior drug of interest

			for (Item priorATC : prescriptionsList) {
				// check if the current prescription is in the lookup string array
				List<String> parameters = new ArrayList<String>(priorATC.getParameters());
				if (prescription.startsWith(priorATC.getLookup()) && 
						(parameters.isEmpty() || (prescription.hasIndication() && parameters.get(0).equals(prescription.getIndication()))) &&
						prescription.isInPeriod(indexDate-timeWindowPriorATC,indexDate,false,priorATCIncludeIndexDate)) {
					if (parameters.isEmpty()) {
						foundPrescriptions.add(priorATC.getDescription());
						mapPrescriptions.put(priorATC.getDescription(), (outputYesNo ? "YES" : "1"));
					}
					else {
						foundPrescriptions.add(priorATC.getDescription()+";"+(prescription.hasIndication() ? prescription.getIndication() : ""));					
						mapPrescriptions.put(priorATC.getDescription(), (outputYesNo ? "YES" : "1"));
					}					
					if (intermediateFiles){

						Jerboa.getOutputManager().writeln( intermediatePrescriptonsFile, patient.getPatientID() + ",priorUse," + priorATC.getDescription()+
								","+DateUtilities.daysToDate(prescription.getDate())+","+
								DateUtilities.daysToDate(prescription.getEndDate())+","+DateUtilities.daysToDate(indexDate)+","+
								(prescription.getEndDate()-indexDate)+","+(prescription.hasIndication() ? prescription.getIndication() : ""), true);


					}
					Jerboa.getResultSet().add(patient.getPatientID(),priorATC.getLabel(),"1");
				}
			}




			// Check indications.  note that all indications are added even empty once!
			if (prescription.date==indexDate){
				for (Item indication : indicationsList){
					if (prescription.startsWith(indication.getLookup())) {
						if ((!prescription.hasIndication()) || (prescription.getIndication()==null)) {
							indicationsBag.add(new ExtendedMultiKey(indication.getDescription(),"Unknown"));
							mapIndications.put(indication.getDescription(), "UNKNOWN");
							if (intermediateFiles){
								Jerboa.getOutputManager().writeln(intermediatePrescriptonsFile, patient.getPatientID() + ",indication," + indication.getDescription()+
										","+DateUtilities.daysToDate(prescription.getDate())+","+
										DateUtilities.daysToDate(prescription.getEndDate())+","+DateUtilities.daysToDate(indexDate)+","+
										"unknown",true);
							}
						} else {
							indicationsBag.add(new ExtendedMultiKey(indication.getDescription(),prescription.getIndication()));
							mapIndications.put(indication.getDescription(), prescription.getIndication());
							if (intermediateFiles){
								Jerboa.getOutputManager().writeln( intermediatePrescriptonsFile, patient.getPatientID() + ",indication," + indication.getDescription()+
										","+DateUtilities.daysToDate(prescription.getDate())+","+
										DateUtilities.daysToDate(prescription.getEndDate())+","+DateUtilities.daysToDate(indexDate)+","+
										prescription.getIndication(),true);
							}
						}
						break;
					}
				}

				// Check dosages
				for (Item dosage : dosagesList){
					if (prescription.startsWith(dosage.getLookup())) {
						if (prescription.getDose()==null) {
							dosagesBag.add(new ExtendedMultiKey(dosage.getDescription(), Double.valueOf("Unknown")));
							mapDosages.put(dosage.getDescription(), "99999");
							if (intermediateFiles){
								Jerboa.getOutputManager().writeln(intermediatePrescriptonsFile, patient.getPatientID() + ",dose," + dosage.getDescription()+
										","+DateUtilities.daysToDate(prescription.getDate())+","+
										DateUtilities.daysToDate(prescription.getEndDate())+","+DateUtilities.daysToDate(indexDate)+","+
										"missing", true);
							}					
						} else {
							dosagesBag.add(new ExtendedMultiKey(dosage.getDescription(),Double.valueOf(prescription.getDose())));
							mapDosages.put(dosage.getDescription(), prescription.getDose());
							if (intermediateFiles){
								Jerboa.getOutputManager().writeln(intermediatePrescriptonsFile, patient.getPatientID() + ",dose," + dosage.getDescription()+
										","+DateUtilities.daysToDate(prescription.getDate())+","+
										DateUtilities.daysToDate(prescription.getEndDate())+","+DateUtilities.daysToDate(indexDate)+","+
										prescription.getDose(),true);
							}
						}
						break;
					}
				}
			}
		}	


		// add prescriptions to bags on description level

		for (String description: foundPrescriptions){
			String [] Split = description.split(";");
			String descriptionStr = Split[0];
			String indicationStr = "";
			if (Split.length == 2){
				indicationStr = Split[1];
				prescriptionsBag.add(new ExtendedMultiKey(descriptionStr,indicationStr));
			}   
			else
				prescriptionsBag.add(new ExtendedMultiKey(descriptionStr,"No Indication"));

		}

		// return the results as a string
		DelimitedStringBuilder result = new DelimitedStringBuilder();
		HashSet<String> done = new HashSet<String>(); //note order in map is not guaranteed therefore needed!
		for (Item key : prescriptionsList) 
			if (!done.contains(key.getDescription())){
				result.append(mapPrescriptions.get(key.getDescription()));
				done.add(key.getDescription());
			}

		done.clear();

		for (Item key : indicationsList) 
			if (!done.contains(key.getDescription())){
				result.append(mapIndications.get(key.getDescription()));
				done.add(key.getDescription());
			}
		done.clear();

		for (Item key : dosagesList) 
			if (!done.contains(key.getDescription())){
				result.append(mapDosages.get(key.getDescription()));
				done.add(key.getDescription());
			}

		return result;
	}

	/**
	 * Determines if the patient has comorbidities in the user specified
	 * time window. Updates the comorbiditiesBag
	 * @param Events - all events of the patient
	 * @param indexDate - index date
	 */
	private DelimitedStringBuilder  processComorbidities(Patient patient){
		int indexDate = patient.cohortStartDate;
		int windowStart;
		int windowEnd;
		Set<String> foundEvents = new HashSet<String>(); //only count the events once
		Map<String, String> map = new HashMap<String, String>();
		for (Item comorbidity : comorbiditiesList) {
			map.put(comorbidity.getLabel(), (outputYesNo ? "NO" : "0"));		
		}
		for (Item comorbidity : comorbiditiesList) {
			Jerboa.getResultSet().add(patient.getPatientID(), comorbidity.getDescription(), "0");
		}
		for (Event event : patient.getEvents()) {
			for (Item comorbidity : comorbiditiesList) {

				List<String> parameters = new ArrayList<String>(comorbidity.getParameters());
				windowStart = timeWindowComorbidity;
				if (comorbiditiesIncludeIndexdate)
					windowEnd = indexDate + 1; //note if no windowEnd is defined indexDate is included!
				else
					windowEnd = indexDate;

				//parse windowStart and throw if needed
				try {

					if (parameters.size()>0) {
						windowStart = parameters.get(0).equals("-1") ? -1 : indexDate-Integer.valueOf(parameters.get(0));
					}
				}
				catch(NumberFormatException e)
				{
					throw new InvalidParameterException("Please check the script. comorbidity [" + comorbidity.getLookup() + "] should have a numeric window start");
				}

				try {
					if (parameters.size()>1) {
						if (parameters.get(1).toLowerCase().equals("cohortend"))
							windowEnd = patient.getCohortEndDate();
						else
							windowEnd = Integer.valueOf(parameters.get(1))+indexDate;
					}	
				}
				catch(NumberFormatException e)
				{
					throw new InvalidParameterException("Please check the script. comorbidity [" + comorbidity.getLookup() + "] should have a numeric window end or cohortend");
				}

				if (parameters.size()>2) {
					throw new InvalidParameterException("Please check the script. comorbidity [" + comorbidity.getLookup() + "] should have max four fields");
				}

				//windowStart is full history
				if (windowStart==-1) {
					for (String lookup:comorbidity.getLookup()){
						if (StringUtils.upperCase(event.getType()).equals(StringUtils.upperCase(lookup)) && (event.date<windowEnd)) {
							foundEvents.add(comorbidity.getLabel());
							map.put(comorbidity.getLabel(), (outputYesNo ? "YES" : "1"));
						}
					}
				} else {
					for (String lookup:comorbidity.getLookup()){
						if (StringUtils.upperCase(event.getType()).equals(StringUtils.upperCase(lookup)) && event.isInPeriod(windowStart,windowEnd,false,false)) {
							foundEvents.add(comorbidity.getLabel());
							map.put(comorbidity.getLabel(), (outputYesNo ? "YES" : "1"));
						}
					}					

				}

			}
		}	
		// add events to bags

		for (String event: foundEvents){
			comorbiditiesBag.add(event);
			Jerboa.getResultSet().add(patient.getPatientID(), event, "1");
			if (intermediateFiles){
				Jerboa.getOutputManager().writeln(intermediateComorbiditiesFile, patient.getPatientID() + "," + event,true);
			}
		}	

		// return the results as a string
		DelimitedStringBuilder result = new DelimitedStringBuilder();
		for (Item key : comorbiditiesList){
			result.append(map.get(key.getLabel()));
		}
		return result;
	}

	/**
	 * Determines if the patient has events in cohort time.
	 * Updates the eventsBag.
	 * @param patient - a patient object
	 */
	private DelimitedStringBuilder processEvents(Patient patient){
		Set<String> foundEvents = new HashSet<String>(); //only count the events once
		Map<String, String> map = new HashMap<String, String>();
		for (Item event : eventsList) {
			map.put(event.getDescription(), (outputYesNo ? "NO" : "0"));		
		}
		for (Event event : patient.getEvents()) {
			// Check if event of interest
			for (Item eventOfInterest : eventsList) {
				for (String lookup:eventOfInterest.getLookup()){
					if (StringUtils.upperCase(event.getType()).equals(StringUtils.upperCase(lookup)) && (event.isInPeriod(patient.getCohortStartDate(), patient.getCohortEndDate()))) {
						foundEvents.add(eventOfInterest.getDescription());
						map.put(eventOfInterest.getDescription(), (outputYesNo ? "YES" : "1"));
					}
				}	

			}
		}	
		// add events to bags

		for (String event: foundEvents){
			eventsBag.add(event);	
			Jerboa.getResultSet().add(patient.getPatientID(),"D"+event,"1");
			if (intermediateFiles){
				Jerboa.getOutputManager().writeln(intermediateEventsFile, patient.getPatientID() + "," + event,true);
			}
		}

		// return the results as a string
		DelimitedStringBuilder result = new DelimitedStringBuilder();
		for (Item key : eventsList){
			result.append(map.get(key.getDescription()));
		}
		return result;
	}

	/**
	 * Counts the number of occurrences of an event in the user specified
	 * time window before a date. Updates the eventCountsBag.
	 * multiple lookups are not allowed!
	 * @param patient - a patient object
	 */
	private DelimitedStringBuilder processPriorEventCounts(Patient patient){
		int indexDate = patient.cohortStartDate;
		int[] eventCounts = new int[priorEventCountsList.size()]; 
		boolean[] foundOnDay = new boolean[priorEventCountsList.size()];

		Map<String, String> map = new HashMap<String, String>();
		for (Item event : priorEventCountsList) {
			map.put(event.getDescription(), "0");		
		}

		int previousDate = 0;

		for (Event event : patient.getEvents()) {
			//reset if this is a new day
			if (event.date!=previousDate || previousDate==0){
				for (int j=0;j<priorEventCountsList.size();j++)
					foundOnDay[j]=false;
				previousDate=event.date;
			}	

			// Check if event of interest
			int i = 0;
			for (Item eventCount : priorEventCountsList) {
				if (timeWindowPriorEventCount>0) {
					//only first item in lookup table will be used!
					if (StringUtils.upperCase(event.getType()).equals(StringUtils.upperCase(eventCount.getLookup().get(0))) && event.isInPeriod(indexDate-timeWindowPriorEventCount,indexDate,true,false)) {
						if (!foundOnDay[i]){
							eventCounts[i]++;
							foundOnDay[i] = true;
						}   
						break;
					}
				} else {
					if (StringUtils.upperCase(event.getType()).equals(StringUtils.upperCase(eventCount.getLookup().get(0))) && (event.date<indexDate)) {
						if (!foundOnDay[i]){
							eventCounts[i]++;
							foundOnDay[i] = true;
						}
						break;
					}
				}
				i++;
			}
		}	

		// add event + frequency to the bag	
		int i=0;
		for (Item eventCount : priorEventCountsList){
			ExtendedMultiKey NameCount = new ExtendedMultiKey(eventCount.getDescription(),eventCounts[i]);
			priorEventCountsBag.add(NameCount);	
			map.put(eventCount.getDescription(), String.valueOf(eventCounts[i]));
			Jerboa.getResultSet().add(patient.getPatientID(),"N"+eventCount.getLookup().toString(), String.valueOf(eventCounts[i]));
			if (intermediateFiles){
				Jerboa.getOutputManager().writeln(intermediatePriorEventCountsFile, patient.getPatientID() + "," + eventCount.getLookup().toString() +"," + eventCounts[i],true);
			}


			i++;
		}

		// return the results as a string
		DelimitedStringBuilder result = new DelimitedStringBuilder();
		for (Item key : priorEventCountsList){
			result.append(map.get(key.getDescription()));
		}
		return result;
	}

	/**
	 * Counts the number of occurrences of an event in the user specified
	 * time window before a date. Updates the eventCountsBag.
	 * multiple lookups are not allowed!
	 * @param patient - a patient object
	 */
	private DelimitedStringBuilder processEventCounts(Patient patient){
		HashMap<String, List<Integer>> counts = new HashMap<String, List<Integer>>();

		int indexDate = patient.cohortStartDate;

		//initialize map with empty values
		for (Item eventCountItem : eventCountsList) {
			counts.put(eventCountItem.getDescription(), new ArrayList<Integer>());
		}

		for (Event event : patient.getEvents()) {
			for (Item eventCount : eventCountsList) {
				
				if (StringUtils.upperCase(event.getType()).equals(StringUtils.upperCase(eventCount.getLookup().get(0)))) {
					List<String> parameters = new ArrayList<String>(eventCount.getParameters());
					int windowStart = -1;
					if ((parameters.size() > 0) && (!parameters.get(0).trim().equals(""))) {
						try {
							windowStart = Integer.valueOf(parameters.get(0));
						}
						catch (NumberFormatException e) {
							Logging.add(this.title + ": Illegal windowStart (" + parameters.get(0) + ") of eventCounts: " + eventCount.getDescription(), Logging.ERROR);
							throw new InvalidParameterException(this.title + ": Illegal windowStart (" + parameters.get(0) + ") of eventCounts: " + eventCount.getDescription());
						}
					}
					int windowEnd = -1;
					if ((parameters.size() > 1) && (!parameters.get(1).trim().equals(""))) {
						try {
							windowEnd = Integer.valueOf(parameters.get(1));
						}
						catch (NumberFormatException e) {
							Logging.add(this.title + ": Illegal windowEnd (" + parameters.get(1) + ") of eventCounts: " + eventCount.getDescription(), Logging.ERROR);
							throw new InvalidParameterException(this.title + ": Illegal windowEnd (" + parameters.get(1) + ") of eventCounts: " + eventCount.getDescription());
						}
					}
					
					int periodStart = windowStart == -1 ? Integer.MIN_VALUE : indexDate + windowStart;
					int periodEnd = windowEnd == -1 ? Integer.MAX_VALUE : indexDate + windowEnd;
					
					if (event.isInPeriod(periodStart, periodEnd, true, false)) {
						List<Integer> eventList = counts.get(eventCount.getDescription());
						if (!eventList.contains(event.getDate())) {
							eventList.add(event.getDate());
						}
					}
				}
			}
		}

		DelimitedStringBuilder result = new DelimitedStringBuilder();
		for (Item eventCount : eventCountsList) {
			List<Integer> eventList = counts.get(eventCount.getDescription());
			ExtendedMultiKey NameCount = new ExtendedMultiKey(eventCount.getDescription(), eventList.size());
			eventCountsBag.add(NameCount);	
			result.append(String.valueOf(eventList.size()));
			Jerboa.getResultSet().add(patient.getPatientID(),eventCount.getLookup().toString(), String.valueOf(eventList.size()));
			if (intermediateFiles){
				Jerboa.getOutputManager().writeln(intermediateEventCountsFile, patient.getPatientID() + "," + eventCount.getDescription() +"," + eventList.size(),true);
			}
		}

		return result;
	}

	/**
	 * Counts the number of occurrences of an event in the user specified
	 * time window before a date. Updates the eventCountsBag.
	 * multiple lookups are not allowed!
	 * @param patient - a patient object
	 */
	private DelimitedStringBuilder processEventCountDefinitions(Patient patient){
		HashMap<String, List<Integer>> counts = new HashMap<String, List<Integer>>();

		int indexDate = patient.cohortStartDate;

		//initialize map with empty values
		for (Item eventCountItem : eventCountDefinitionsList) {
			counts.put(eventCountItem.getDescription(), new ArrayList<Integer>());
		}

		for (Event event : patient.getEvents()) {
			for (Item eventCount : eventCountDefinitionsList) {
				
				if (StringUtils.upperCase(event.getType()).equals(StringUtils.upperCase(eventCount.getLookup().get(0)))) {
					List<String> parameters = new ArrayList<String>(eventCount.getParameters());
					int windowStart = -1;
					if ((parameters.size() > 0) && (!parameters.get(0).trim().equals(""))) {
						try {
							windowStart = Integer.valueOf(parameters.get(0));
						}
						catch (NumberFormatException e) {
							Logging.add(this.title + ": Illegal windowStart (" + parameters.get(0) + ") of eventCountDefinitions: " + eventCount.getDescription(), Logging.ERROR);
							throw new InvalidParameterException(this.title + ": Illegal windowStart (" + parameters.get(0) + ") of eventCountDefinitions: " + eventCount.getDescription());
						}
					}
					int windowEnd = -1;
					if ((parameters.size() > 1) && (!parameters.get(1).trim().equals(""))) {
						try {
							windowEnd = Integer.valueOf(parameters.get(1));
						}
						catch (NumberFormatException e) {
							Logging.add(this.title + ": Illegal windowEnd (" + parameters.get(1) + ") of eventCountDefinitions: " + eventCount.getDescription(), Logging.ERROR);
							throw new InvalidParameterException(this.title + ": Illegal windowEnd (" + parameters.get(1) + ") of eventCountDefinitions: " + eventCount.getDescription());
						}
					}
					
					int periodStart = windowStart == -1 ? Integer.MIN_VALUE : indexDate + windowStart;
					int periodEnd = windowEnd == -1 ? Integer.MAX_VALUE : indexDate + windowEnd;
					
					if (event.isInPeriod(periodStart, periodEnd, true, false)) {
						List<Integer> eventList = counts.get(eventCount.getDescription());
						if (!eventList.contains(event.getDate())) {
							eventList.add(event.getDate());
						}
					}
				}
			}
		}

		DelimitedStringBuilder result = new DelimitedStringBuilder();
		for (Item eventCountDefinition : eventCountDefinitionsList) {
			List<String> parameters = new ArrayList<String>(eventCountDefinition.getParameters());

			int minCount = 0;
			if ((parameters.size() > 2) && (!parameters.get(2).trim().equals(""))) {
				try {
					minCount = Integer.valueOf(parameters.get(2));
				}
				catch (NumberFormatException e) {
					Logging.add(this.title + ": Illegal minCount (" + parameters.get(2) + ") of eventCountDefinitions: " + eventCountDefinition.getDescription(), Logging.ERROR);
					throw new InvalidParameterException(this.title + ": Illegal minCount (" + parameters.get(2) + ") of eventCountDefinitions: " + eventCountDefinition.getDescription());
				}
			}
			int maxCount = Integer.MAX_VALUE;
			if ((parameters.size() > 3) && (!parameters.get(3).trim().equals(""))) {
				try {
					maxCount = Integer.valueOf(parameters.get(3));
				}
				catch (NumberFormatException e) {
					Logging.add(this.title + ": Illegal maxCount (" + parameters.get(3) + ") of eventCountDefinitions: " + eventCountDefinition.getDescription(), Logging.ERROR);
					throw new InvalidParameterException(this.title + ": Illegal maxCount (" + parameters.get(3) + ") of eventCountDefinitions: " + eventCountDefinition.getDescription());
				}
			}
			int countPeriod = -1;
			if ((parameters.size() > 4) && (!parameters.get(4).trim().equals(""))) {
				try {
					countPeriod = Integer.valueOf(parameters.get(4));
				}
				catch (NumberFormatException e) {
					Logging.add(this.title + ": Illegal countPeriod (" + parameters.get(4) + ") of eventCountDefinitions: " + eventCountDefinition.getDescription(), Logging.ERROR);
					throw new InvalidParameterException(this.title + ": Illegal countPeriod (" + parameters.get(4) + ") of eventCountDefinitions: " + eventCountDefinition.getDescription());
				}
			}
			
			List<Integer> eventList = counts.get(eventCountDefinition.getDescription());
			if (countPeriod != -1) {
				boolean found = false;
				for (int periodStartNr = 0; periodStartNr < eventList.size(); periodStartNr++) {
					int periodStart = eventList.get(periodStartNr);
					int count = 0;
					for (int periodEndNr = periodStartNr; periodEndNr < eventList.size(); periodEndNr++) {
						if (eventList.get(periodEndNr) > (periodStart + countPeriod)) {
							break;
						}
						count++;
					}
					if ((count >= minCount) && (count <= maxCount)) {
						found = true;
						break;
					}
				}
				if (found) {
					eventCountDefinitionsBag.add(eventCountDefinition.getDescription());
				}
				result.append(found ? (outputYesNo ? "YES" : "1") : (outputYesNo ? "NO" : "0"));
				Jerboa.getResultSet().add(patient.getPatientID(),eventCountDefinition.getLookup().toString(), found ? (outputYesNo ? "YES" : "1") : (outputYesNo ? "NO" : "0"));
				if (intermediateFiles){
					Jerboa.getOutputManager().writeln(intermediateEventCountDefinitionsFile, patient.getPatientID() + "," + eventCountDefinition.getDescription() +"," + (found ? "1" : "0"),true);
				}
			}
			else {
				boolean found = ((eventList.size() >= minCount) && (eventList.size() <= maxCount));
				if (found) {
					eventCountDefinitionsBag.add(eventCountDefinition.getDescription());
				}
				result.append(found ? (outputYesNo ? "YES" : "1") : (outputYesNo ? "NO" : "0"));
				Jerboa.getResultSet().add(patient.getPatientID(),eventCountDefinition.getLookup().toString(), found ? (outputYesNo ? "YES" : "1") : (outputYesNo ? "NO" : "0"));
				if (intermediateFiles){
					Jerboa.getOutputManager().writeln(intermediateEventCountDefinitionsFile, patient.getPatientID() + "," + eventCountDefinition.getDescription() +"," + (((eventList.size() >= minCount) && (eventList.size() <= maxCount)) ? "1" : "0"),true);
				}
			}
			
		}

		return result;
	}

	/**
	 * Counts the number of occurrences of prescription in the user specified
	 * time window before a date. Updates the prescriptionCountsBag.
	 * @param patient - the patient object
	 */
	private DelimitedStringBuilder processPrescriptionCounts(Patient patient){
		int indexDate = patient.cohortStartDate;
		int[] prescriptionCounter = new int[prescriptionCounts.size()]; // counter for each atc
		Map<String, String> map = new HashMap<String, String>();
		for (Prescription prescription : patient.getPrescriptions()) {

			// Check if prescription of interest
			// List of indications is allowed

			for (int i =0; i<prescriptionCounts.size(); i++){
				String[] prescriptionSplit = prescriptionCounts.get(i).split(";");
				String atc = prescriptionSplit[0];
				String indication = prescriptionSplit[2];
				String[] indicationSplit = indication.split(",");
				if (timeWindowPrescriptionCount>0) {
					//only first item in lookup table will be used!
					if (prescription.startsWith(atc) && prescription.isInPeriod(indexDate-timeWindowPrescriptionCount,indexDate,false,true) &&
							prescription.hasIndication() &&
							prescription.getIndication()!=null &&
							prescription.hasIndication(indicationSplit)) {
						prescriptionCounter[i]++;
					}
				} else {
					if (prescription.startsWith(atc) && (prescription.date<=indexDate) &&
							prescription.hasIndication() &&
							prescription.getIndication()!=null &&
							prescription.hasIndication(indicationSplit)) {
						prescriptionCounter[i]++;
					}
				}
			}
		}	

		// add prescription + frequency to the bag
		DelimitedStringBuilder result = new DelimitedStringBuilder();

		for (int i =0; i<prescriptionCounts.size(); i++){
			String[] prescriptionSplit = prescriptionCounts.get(i).split(";");
			String atc = prescriptionSplit[0];
			String description = prescriptionSplit[1];
			String indication = prescriptionSplit[2];
			ExtendedMultiKey atcCount = new ExtendedMultiKey(description,prescriptionCounter[i]);

			prescriptionCountsBag.add(atcCount);	
			map.put(atc, String.valueOf(prescriptionCounter[i]));
			result.append(String.valueOf(prescriptionCounter[i]));

			if (intermediateFiles){
				Jerboa.getOutputManager().writeln(intermediatePrescriptionCountsFile, patient.getPatientID() + "," + atc +"," + indication + ","+ prescriptionCounter[i],true);
			}
		}
		return result;
	}

	/**
	 * Determines if drugs are used at cohort start.
	 * Updates the concomitantsBag.
	 * @param patient - the patient object
	 */
	private DelimitedStringBuilder processPrescriptionConcomitant(Patient patient){
		int indexDate = patient.cohortStartDate;
		Set<String> foundConcomitant = new HashSet<String>(); //only count the concomitant once
		Map<String, String> mapPrescriptions = new HashMap<String, String>();
		for (Item concomitantATC : prescriptionsConcomitantList){
			mapPrescriptions.put(concomitantATC.getDescription(), (outputYesNo ? "NO" : "0"));		
		}
		for (Prescription prescription : patient.getPrescriptions()) {
			for (Item concomitantATC : prescriptionsConcomitantList) {
				// check if the current prescription is in the lookup string array
				List<String> parameters = new ArrayList<String>(concomitantATC.getParameters());
				String requiredIndication = (parameters.size() > 0) ? parameters.get(0).trim() : "";
				int windowStart = 0;
				if ((parameters.size() > 1) && (!parameters.get(1).trim().equals(""))) {
					try {
						windowStart = Integer.valueOf(parameters.get(1));
					}
					catch (NumberFormatException e) {
						Logging.add(this.title + ": Illegal windowStart (" + parameters.get(1) + ") of concomitantATCs: " + concomitantATC.getDescription(), Logging.ERROR);
						throw new InvalidParameterException(this.title + ": Illegal windowStart (" + parameters.get(1) + ") of concomitantATCs: " + concomitantATC.getDescription());
					}
				}
				int windowEnd = 1;
				if ((parameters.size() > 2) && (!parameters.get(2).trim().equals(""))) {
					try {
						windowEnd = Integer.valueOf(parameters.get(2));
					}
					catch (NumberFormatException e) {
						Logging.add(this.title + ": Illegal windowEnd (" + parameters.get(2) + ") of concomitantATCs: " + concomitantATC.getDescription(), Logging.ERROR);
						throw new InvalidParameterException(this.title + ": Illegal windowEnd (" + parameters.get(2) + ") of concomitantATCs: " + concomitantATC.getDescription());
					}
				}
				
				if (prescription.startsWith(concomitantATC.getLookup()) && 
						(requiredIndication.equals("") || (prescription.hasIndication() && requiredIndication.equals(prescription.getIndication()))) &&
						prescription.isInPeriod(indexDate + windowStart, indexDate + windowEnd, false, false)) {
					foundConcomitant.add(concomitantATC.getDescription());
					mapPrescriptions.put(concomitantATC.getDescription(), (outputYesNo ? "YES" : "1"));

					if (intermediateFiles){
						Jerboa.getOutputManager().writeln(intermediatePrescriptionConcomitantFile, patient.getPatientID() + "," + 
								concomitantATC.getDescription() +
								"," + (prescription.hasIndication() ? prescription.getIndication() : ""),true);
					}

					Jerboa.getResultSet().add(patient.getPatientID(),concomitantATC.getLabel(),"1");
				}

			}
		}

		// add concomitant to bag
		for (String concomitant: foundConcomitant)
			concomitantBag.add(concomitant);	

		// return the results as a string
		DelimitedStringBuilder result = new DelimitedStringBuilder();
		HashSet<String> done = new HashSet<String>(); //note order in map is not guaranteed therefore needed!
		for (Item key : prescriptionsConcomitantList) 
			if (!done.contains(key.getDescription())){
				result.append(mapPrescriptions.get(key.getDescription()));
				done.add(key.getDescription());
			}

		return result;
	}

	/**
	 * Select the measurement closest to the index date in the user specified
	 * time window. Updates the measurementsBag. Measurements should be sorted by date in the
	 * patient object!
	 * @param patient - a patient object
	 * @throws IllegalArgumentException if patient measurements are not sorted
	 */
	private DelimitedStringBuilder processMeasurements(Patient patient) throws IllegalArgumentException {

		int indexDate = patient.cohortStartDate;
		//use map to only retain the last measurement in the timeWindowMeasurement
		Map<String,String> selectedMeasurements = new HashMap<String,String>();

		//initialize map with unique set of measurements empty values
		for (Item measurementItem : measurementsList) {
			selectedMeasurements.put(measurementItem.getDescription(),"");
		}

		//populate the map with the last value of the measurement
		int date = 0;
		for (Measurement measurement : patient.getMeasurements()) {

			//check measurements are still sorted		
			if (measurement.date<date) {
				Logging.add("Descriptives:Measurements were not sorted by date!");
				throw new IllegalArgumentException("Measurements should be sorted by date!");
			} else
				date = measurement.date;
			// Check if measurement of interest
			int windowStart = -1;
			int windowEnd = indexDate;
			for (Item measurementItem : measurementsList) {
				List<String> parameters = new ArrayList<String>(measurementItem.getParameters());
				if (timeWindowMeasurement>0) {
					windowStart = indexDate - timeWindowMeasurement;
					windowEnd = indexDate; //note if no windowEnd is defined indexDate is included!
				} else {
					windowStart = -1;
					windowEnd = indexDate;
				}


				//parse windowStart and throw if needed
				try {

					if (parameters.size()>0) {
						windowStart = Math.max(0,indexDate-Integer.valueOf(parameters.get(0)));
					}
				}
				catch(NumberFormatException e)
				{
					throw new InvalidParameterException("Please check the script. comorbidity [" +measurementItem.getLookup() + "] should have a numeric window start");
				}

				try {
					if (parameters.size()>1) {
						if (parameters.get(1).toLowerCase().equals("cohortend"))
							windowEnd = patient.getCohortEndDate();
						else
							windowEnd = Integer.valueOf(parameters.get(1))+indexDate;
					}	
				}
				catch(NumberFormatException e)
				{
					throw new InvalidParameterException("Please check the script. comorbidity [" + measurementItem.getLookup() + "] should have a numeric window end or cohortend");
				}

				if (windowStart>=0) {
					//only the last 
					if (StringUtils.upperCase(measurement.getType()).equals(StringUtils.upperCase(measurementItem.getLookup().get(0))) && measurement.isInPeriod(windowStart,windowEnd)) {
						selectedMeasurements.put(measurementItem.getDescription(),measurement.getValue());
						break;
					}
				} else {
					if (StringUtils.upperCase(measurement.getType()).equals(StringUtils.upperCase(measurementItem.getLookup().get(0))) && (measurement.date<=indexDate)) {
						selectedMeasurements.put(measurementItem.getDescription(),measurement.getValue());
						break;
					}
				}
			}
		}	
		// add meaurements to bags
		for (String key : selectedMeasurements.keySet()){

			//Add the measurement to the measurementBag or the unknownMeasurement Bag.
			if (!selectedMeasurements.get(key).isEmpty()){
				// find the corresponding item
				for (Item measurementItem : measurementsList) {
					if (measurementItem.getDescription().equals(key)){
						Jerboa.getResultSet().add(patient.getPatientID(),key, selectedMeasurements.get(key).toString());
						
						if (measurementItem.getValue().equals("CONTINUOUS")) {
							try {
								measContinuesBag.add(new ExtendedMultiKey(key,  Double.valueOf(selectedMeasurements.get(key))));
							} catch (NumberFormatException e) {
								Logging.add("Patient: "+patient.getPatientID()+" has a non numeric value for measurement "+ key);
								throw new NumberFormatException("Patient: "+patient.getPatientID()+" has a non numeric value for measurement "+ key);
							}
						}
						else {
							measCategoryBag.add(new ExtendedMultiKey(key, StringUtils.upperCase(selectedMeasurements.get(key))));
						}						
						if (intermediateFiles){
							Jerboa.getOutputManager().writeln(intermediateMeasurementsFile, patient.getPatientID() + "," + measurementItem.getLookup().get(0) +"," + DateUtilities.daysToDate(indexDate)+ "," +
									selectedMeasurements.get(key),true);	
						}
						break;
					}
				}

			} else
				measCategoryBag.add(new ExtendedMultiKey(key, "UNKNOWN"));
		}
		DelimitedStringBuilder result = new DelimitedStringBuilder();
		HashSet<String> done = new HashSet<String>();
		for (Item key : measurementsList){
			if (!done.contains(key.getDescription())) {
				if (!selectedMeasurements.get(key.getDescription()).isEmpty())
					if (key.getValue().equals("CONTINUOUS"))
						result.append(precisionFormat.format(Double.valueOf(selectedMeasurements.get(key.getDescription()))));
					else
						result.append(selectedMeasurements.get(key.getDescription()));
				else
					if (key.getValue().equals("CONTINUOUS"))
						result.append(" ");
					else
						result.append("UNKNOWN");
				done.add(key.getDescription());
			}
		}
		return result;
	}

	/**
	 * Measures the time since the first occurrence of the event before the index date.
	 * Updates the timeSinceEventBag. Events should be sorted by date in the
	 * patient object!
	 * @param patient - a patient object
	 */
	private DelimitedStringBuilder processTimeSinceEvents(Patient patient){
		HashMap<String, Integer> foundEvents = new HashMap<String,Integer>(); 

		int indexDate = patient.cohortStartDate;
		boolean eventFound = false;

		//initialize map with empty values
		for (Item timeSinceEventItem : timeSinceEventsList) {
			foundEvents.put(timeSinceEventItem.getDescription(),-1);
		}
		int date = 0;
		for (Event event : patient.getEvents()) {
			//if (ignoreDate==null || event.date!=DateUtilities.dateToDays(ignoreDate,DateUtilities.DATE_ON_YYYYMMDD)){
			//check measurements are still sorted		
			if (event.date<date) {
				Logging.add("Descriptives:Events were not sorted by date!");
				throw new IllegalArgumentException("Events should be sorted by date!");
			} else
				date = event.date;

			// Check if event of interest
			for (Item timeSinceEvent : timeSinceEventsList) {
				if (StringUtils.upperCase(event.getType()).equals(StringUtils.upperCase(timeSinceEvent.getLookup().get(0))) && (event.date<=indexDate)) {
					Integer timeSince = indexDate-event.getDate();
					if (timeSince<(ignoreDeltaEvents*365.25)) {
						eventFound = true;
						//only take the first event (events are ordered by date)
						if (foundEvents.get(timeSinceEvent.getDescription())==-1)
							foundEvents.put(timeSinceEvent.getDescription(),timeSince);
						break;
					} else
						Logging.add ("Time Since Event >" + ignoreDeltaEvents + " years, ignored event " + event.getType() + " for patient " + event.getPatientID());

				}
			}
		}	
		// add time since event to bags
		for (Map.Entry<String, Integer> entry : foundEvents.entrySet()) {
			String key = entry.getKey();
			Integer timeSince = entry.getValue();
			if (timeSince >= 0 && eventFound) { 
				timeSinceEventsBag.add(new ExtendedMultiKey(key, Double.valueOf(timeSince/365.25)));

				if (intermediateFiles) {
					Jerboa.getOutputManager().writeln(intermediateTimeSinceEventsFile,patient.getPatientID() + "," + key +"," + DateUtilities.daysToDate(indexDate)+ "," +
							DateUtilities.daysToDate(indexDate-timeSince.intValue()) + "," + (double) timeSince/365.25,true);
				}
			} 
		}

		DelimitedStringBuilder result = new DelimitedStringBuilder();
		for (Item key : timeSinceEventsList) {
			if (foundEvents.get(key.getDescription())==-1)
				result.append(" ");
			else
				result.append(foundEvents.get(key.getDescription()).toString());
		}
		return result;
	}

	/**
	 * Measures the time since the first occurrence of the event before the index date.
	 * Updates the timeSinceEventBag. Events should be sorted by date in the
	 * patient object!
	 * @param patient - a patient object
	 */
	private DelimitedStringBuilder processTimeToEvents(Patient patient){
		HashMap<String, Integer> foundEvents = new HashMap<String,Integer>(); 

		int indexDate = patient.cohortStartDate;
		boolean eventFound = false;

		//initialize map with empty values
		for (Item timeSinceEventItem : timeToEventsList) {
			foundEvents.put(timeSinceEventItem.getDescription(),-1);
		}
		int date = 0;
		for (Event event : patient.getEvents()) {
			//if (ignoreDate==null || event.date!=DateUtilities.dateToDays(ignoreDate,DateUtilities.DATE_ON_YYYYMMDD)){
			//check measurements are still sorted		
			if (event.date<date) {
				Logging.add("Descriptives:Events were not sorted by date!");
				throw new IllegalArgumentException("Events should be sorted by date!");
			} else
				date = event.date;

			// Check if event not after population end
			if (event.getDate() <= patient.getPopulationEndDate()) {
				// Check if event of interest
				for (Item timeToEvent : timeToEventsList) {
					// 2017-05-09 if (StringUtils.upperCase(event.getType()).equals(StringUtils.upperCase(timeToEvent.getLookup().get(0))) && (event.date>indexDate)) {
					if (StringUtils.upperCase(event.getType()).equals(StringUtils.upperCase(timeToEvent.getLookup().get(0))) && (event.date>=indexDate)) {
						Integer timeTo = event.getDate()-indexDate;
						if (timeTo<(ignoreDeltaEvents*365.25)) {
							eventFound = true;
							//only take the first event (events are ordered by date)
							if (foundEvents.get(timeToEvent.getDescription())==-1)
								foundEvents.put(timeToEvent.getDescription(),timeTo);
							break;
						} else
							Logging.add ("Time To Event >" + ignoreDeltaEvents + " years, ignored event " + event.getType() + " for patient " + event.getPatientID());

					}
				}
			}
		}	
		// add time since event to bags
		for (Map.Entry<String, Integer> entry : foundEvents.entrySet()) {
			String key = entry.getKey();
			Integer timeTo = entry.getValue();
			if (timeTo >= 0 && eventFound){ 
				timeToEventsBag.add(new ExtendedMultiKey(key, Double.valueOf(timeTo/365.25)));

				if (intermediateFiles){
					Jerboa.getOutputManager().writeln(intermediateTimeToEventsFile,patient.getPatientID() + "," + key +"," + DateUtilities.daysToDate(indexDate)+ "," +
							DateUtilities.daysToDate(indexDate-timeTo.intValue()) + "," + (double) timeTo/365.25,true);
				}
			} 
		}

		DelimitedStringBuilder result = new DelimitedStringBuilder();
		for (Item key : timeToEventsList){
			if (foundEvents.get(key.getDescription())==-1)
				result.append(" ");
			else
				result.append(foundEvents.get(key.getDescription()).toString());
		}
		return result;
	}

	/**
	 * Measures the time since the first occurrence of the measurement before the index date.
	 * Updates the timeSinceMeasurementBag. Measurements should be sorted by date in the
	 * patient object!
	 * @param patient - a patient object
	 */
	private DelimitedStringBuilder processTimeSinceMeasurements(Patient patient){
		HashMap<String, Integer> foundMeasurements = new HashMap<String,Integer>(); 

		int indexDate = patient.cohortStartDate;
		boolean measurementFound = false;

		//initialize map with empty values
		for (Item timeSinceMeasurementItem : timeSinceMeasurementsList) {
			foundMeasurements.put(timeSinceMeasurementItem.getDescription(),-1);
		}
		int date = 0;
		for (Measurement measurement : patient.getMeasurements()) {
			//if (ignoreDate==null || event.date!=DateUtilities.dateToDays(ignoreDate,DateUtilities.DATE_ON_YYYYMMDD)){
			//check measurements are still sorted		
			if (measurement.date<date) {
				Logging.add("Descriptives:Measurements were not sorted by date!");
				throw new IllegalArgumentException("Measurements should be sorted by date!");
			} else
				date = measurement.date;

			// Check if measurement of interest
			int windowStart = -1;
			int windowEnd = indexDate;
			for (Item timeSinceMeasurement : timeSinceMeasurementsList) {
				List<String> parameters = new ArrayList<String>(timeSinceMeasurement.getParameters());
				if (timeWindowMeasurement>0) {
					windowStart = indexDate - timeWindowMeasurement;
					windowEnd = indexDate; //note if no windowEnd is defined indexDate is included!
				} else {
					windowStart = -1;
					windowEnd = indexDate;
				}


				//parse windowStart and throw if needed
				try {

					if (parameters.size()>0) {
						windowStart = indexDate-Integer.valueOf(parameters.get(0));
					}
				}
				catch(NumberFormatException e)
				{
					throw new InvalidParameterException("Please check the script. comorbidity [" +timeSinceMeasurement.getLookup() + "] should have a numeric window start");
				}

				try {
					if (parameters.size()>1) {
						if (parameters.get(1).toLowerCase().equals("cohortend"))
							windowEnd = patient.getCohortEndDate();
						else
							windowEnd = Integer.valueOf(parameters.get(1))+indexDate;
					}	
				}
				catch(NumberFormatException e)
				{
					throw new InvalidParameterException("Please check the script. comorbidity [" + timeSinceMeasurement.getLookup() + "] should have a numeric window end or cohortend");
				}

				if (windowStart>0) {
					//only the last 
					if (timeSinceMeasurement.getLookup().contains(StringUtils.upperCase(measurement.getType())) && measurement.isInPeriod(windowStart,windowEnd)) {
						Integer timeSince = indexDate-measurement.getDate();
						if (timeSince<ignoreDeltaMeasurements*365.25){
							measurementFound = true;
							//only take the last measurement (measurements are ordered by date) before indexDate
							foundMeasurements.put(timeSinceMeasurement.getDescription(),timeSince);
							break;
						} else
							Logging.add ("Time Since Measurement >" + ignoreDeltaMeasurements + " years, ignored measurement " + measurement.getType() + " for patient " + measurement.getPatientID());
					}
				} else {
					if (timeSinceMeasurement.getLookup().contains(StringUtils.upperCase(measurement.getType())) && (measurement.date<=indexDate)) {
						Integer timeSince = indexDate-measurement.getDate();
						if (timeSince<ignoreDeltaMeasurements*365.25){
							measurementFound = true;
							//only take the last measurement (measurements are ordered by date) before indexDate
							foundMeasurements.put(timeSinceMeasurement.getDescription(),timeSince);
							break;
						} else
							Logging.add ("Time Since Measurement >" + ignoreDeltaMeasurements + " years, ignored measurement " + measurement.getType() + " for patient " + measurement.getPatientID());
					}
				}

			}
			
			
		}	
		// add time since measurement to bags
		for (Map.Entry<String, Integer> entry : foundMeasurements.entrySet()) {
			String key = entry.getKey();
			Integer timeSince = entry.getValue();
			if (timeSince >= 0 && measurementFound){ 
				timeSinceMeasurementsBag.add(new ExtendedMultiKey(key, Double.valueOf(timeSince/365.25)));

				if (intermediateFiles){
					Jerboa.getOutputManager().writeln(intermediateTimeSinceMeasurementsFile,patient.getPatientID() + "," + key +"," + DateUtilities.daysToDate(indexDate)+ "," +
							DateUtilities.daysToDate(indexDate-timeSince.intValue()) + "," + (double) timeSince/365.25,true);
				}
			} 
		}

		DelimitedStringBuilder result = new DelimitedStringBuilder();
		for (Item key : timeSinceMeasurementsList){
			if (foundMeasurements.get(key.getDescription())==-1)
				result.append(" ");
			else
				result.append(foundMeasurements.get(key.getDescription()).toString());
		}
		return result;
	}

	/**
	 * Measures the time to the first prescription of the drugs during cohort time.
	 * Updates the timeToDrugsBag. Prescriptions should be sorted by date in the
	 * patient object!
	 * @param patient - a patient object
	 */
	private DelimitedStringBuilder processTimeToDrugs(Patient patient){
		HashMap<String, Integer> foundDrugs = new HashMap<String,Integer>(); 

		int indexDate = patient.cohortStartDate;
		boolean drugFound = false;

		//initialize map with empty values
		for (Item timeToDrugItem : timeToDrugsList) {
			foundDrugs.put(timeToDrugItem.getDescription(),-1);
		}
		int date = 0;
		for (Prescription prescription : patient.getPrescriptions()) {
			//if (ignoreDate==null || event.date!=DateUtilities.dateToDays(ignoreDate,DateUtilities.DATE_ON_YYYYMMDD)){
			//check measurements are still sorted		
			if (prescription.date<date) {
				Logging.add("Descriptives:Prescriptions were not sorted by date!");
				throw new IllegalArgumentException("Prescriptions should be sorted by date!");
			} else
				date = prescription.date;

			// Check if ATC of interest
			for (Item timeToDrug : timeToDrugsList) {
				if (prescription.startsWith(timeToDrug.getLookup())) {
					if (prescription.date>=indexDate) {
						Integer timeTo = prescription.getDate()-indexDate;
						if (timeTo<ignoreDeltaDrugs*365.25){
							drugFound = true;
							//only take the first prescription (prescriptions are ordered by date)
							if (foundDrugs.get(timeToDrug.getDescription())==-1)
								foundDrugs.put(timeToDrug.getDescription(),timeTo);
							break;
						} else
							Logging.add ("TimeToDrug >" + ignoreDeltaDrugs + " years, ignored measurement " + prescription.getType() + " for patient " + prescription.getPatientID());
					}
					else if (prescription.onDate(indexDate)) {
						drugFound = true;
						//only take the first prescription (prescriptions are ordered by date)
						if (foundDrugs.get(timeToDrug.getDescription())==-1)
							foundDrugs.put(timeToDrug.getDescription(),0);
						break;
					}
				}
			}
		}
		// add time to drug to bags
		for (Map.Entry<String, Integer> entry : foundDrugs.entrySet()) {
			String key = entry.getKey();
			Integer timeTo = entry.getValue();
			if (timeTo >= 0 && drugFound){ 
				timeToDrugsBag.add(new ExtendedMultiKey(key, Double.valueOf(timeTo/365.25)));

				if (intermediateFiles){
					Jerboa.getOutputManager().writeln(intermediateTimeToDrugsFile,patient.getPatientID() + "," + key +"," + DateUtilities.daysToDate(indexDate)+ "," +
							DateUtilities.daysToDate(indexDate-timeTo.intValue()) + "," + (double) timeTo/365.25,true);
				}
			} 
		}

		DelimitedStringBuilder result = new DelimitedStringBuilder();
		for (Item key : timeToDrugsList){
			if (foundDrugs.get(key.getDescription())==-1)
				result.append(" ");
			else
				result.append(foundDrugs.get(key.getDescription()).toString());
		}
		return result;
	}

	/**
	 * Measures the to the drugs during cohort time.
	 * Updates the exposureToDrugsBag. Prescriptions should be sorted by date in the
	 * patient object!
	 * @param patient - a patient object
	 */
	private DelimitedStringBuilder processExposureToDrugs(Patient patient){
		HashMap<String, List<List<Integer>>> foundExposures = new HashMap<String,List<List<Integer>>>(); 

		int indexDate = patient.cohortStartDate;
		boolean drugFound = false;

		//initialize map with empty values
		for (Item exposureToDrugItem : exposureToDrugsList) {
			foundExposures.put(exposureToDrugItem.getDescription(),null);
		}
		int date = 0;
		for (Prescription prescription : patient.getPrescriptions()) {
			//if (ignoreDate==null || event.date!=DateUtilities.dateToDays(ignoreDate,DateUtilities.DATE_ON_YYYYMMDD)){
			//check measurements are still sorted		
			if (prescription.date<date) {
				Logging.add("Descriptives:Prescriptions were not sorted by date!");
				throw new IllegalArgumentException("Prescriptions should be sorted by date!");
			} else
				date = prescription.date;

			// Check if ATC of interest
			for (Item exposureToDrug : exposureToDrugsList) {
				if (prescription.startsWith(exposureToDrug.getLookup())) {
					drugFound = true;
					foundExposures.put(exposureToDrug.getDescription(), addExposure(patient, foundExposures.get(exposureToDrug.getDescription()), prescription.getDate(), prescription.getEndDate()));
				}
			}
		}
		// add time to drug to bags
		for (Map.Entry<String, List<List<Integer>>> entry : foundExposures.entrySet()) {
			String key = entry.getKey();
			List<List<Integer>> exposures = entry.getValue();
			if (exposures != null) {
				Integer exposure = 0;
				for (List<Integer> exposureInterval : exposures) {
					exposure += (exposureInterval.get(1) - exposureInterval.get(0));
				}
				if (exposure >= 0 && drugFound){ 
					exposureToDrugsBag.add(new ExtendedMultiKey(key, Double.valueOf(exposure/365.25)));

					if (intermediateFiles){
						Jerboa.getOutputManager().writeln(intermediateExposureToDrugsFile,patient.getPatientID() + "," + key +"," + DateUtilities.daysToDate(indexDate)+ "," +
								DateUtilities.daysToDate(indexDate-exposure.intValue()) + "," + (double) exposure/365.25,true);
					}
				}
			} 
		}

		DelimitedStringBuilder result = new DelimitedStringBuilder();
		for (Item key : exposureToDrugsList){
			if (foundExposures.get(key.getDescription())==null)
				result.append(" ");
			else {
				List<List<Integer>> exposures = foundExposures.get(key.getDescription());
				Integer exposure = 0;
				for (List<Integer> exposureInterval : exposures) {
					exposure += (exposureInterval.get(1) - exposureInterval.get(0));
				} 
				result.append(exposure.toString());
			}
		}
		return result;
	}
	
	
	private List<List<Integer>> addExposure(Patient patient, List<List<Integer>> exposures, int start, int end) {
		List<List<Integer>> newExposures = exposures;
		int exposureStart = Math.max(start, patient.getCohortStartDate());
		int exposureEnd = Math.min(end, patient.getCohortEndDate());
		List<Integer> newExposure = null;
		if ((exposureEnd - exposureStart) > 0) {
			newExposure = new ArrayList<Integer>();
			newExposure.add(exposureStart);
			newExposure.add(exposureEnd);
		}
		if (newExposure != null) {
			newExposures = new ArrayList<List<Integer>>();
			if (exposures == null) {
				newExposures.add(newExposure);
			}
			else {
				for (int exposureNr = 0; exposureNr < exposures.size(); exposureNr++) {
					List<Integer> exposure = exposures.get(exposureNr);
					if ((newExposure.get(0) <= exposure.get(1)) && (newExposure.get(1) >= exposure.get(0))) { // Overlap or connect -> merge
						newExposure.set(0, Math.min(newExposure.get(0), exposure.get(0)));
						newExposure.set(1, Math.max(newExposure.get(1), exposure.get(1)));
					}
					else if (newExposure.get(1) < exposure.get(0)) { // newExposure before exposure
						newExposures.add(newExposure);
						for (int nr = exposureNr; nr < exposure.size(); nr++) {
							newExposures.add(exposures.get(nr));
						}
						newExposure = null;
						break;
					}
					else { // newExposure after exposure
						newExposures.add(exposures.get(exposureNr));
					}
				}
				if (newExposure != null) {
					newExposures.add(newExposure);
				}
			}
		}
		
		return newExposures;
	}
	
	

	@Override
	public void calcStats(){
		nrFemales = ageBag.getCount(new ExtendedMultiKey ((byte) 0, Wildcard.INTEGER()));
		nrMales = ageBag.getCount(new ExtendedMultiKey((byte) 1, Wildcard.INTEGER()));
		if (nrPatients!=0){
			percentageMales = (double) nrMales*100/ (double) nrPatients;
			percentageFemales = (double) nrFemales*100/ (double) nrPatients;
		}

		HistogramStats ageStats= ageBag.getHistogramStats(new ExtendedMultiKey (Wildcard.BYTE(), Wildcard.INTEGER()));
		ageMean = ageStats.getMean()/365.25;
		ageSD = ageStats.getStdDev()/365.25;

		HistogramStats cohortStats= cohortTimeBag.getHistogramStats(new ExtendedMultiKey (Wildcard.BYTE(), Wildcard.INTEGER()));
		cohortMedian = cohortStats.getMedian();
		cohortMin = cohortStats.getMin();
		cohortMax = cohortStats.getMax();
		for (String def : cohortTimeHigherThan){
			String[] split = def.split(";");
			cohortHigher.add(cohortStats.getHigherThan(Integer.valueOf(split[1])));
		}

		//fill the comorbidity counts from the bag 
		for (Item comorbidity : comorbiditiesList){
			comorbidity.setCount(comorbiditiesBag.getCount(comorbidity.getDescription()));
			comorbidity.setTotal(nrPatients);
		}

		//fill the prescription counts from the bag
		for (Item prescription : prescriptionsList){
			double count = prescriptionsBag.getCount(new ExtendedMultiKey(prescription.getDescription(), Wildcard.STRING()));
			prescription.setCount((int) count);
			prescription.setTotal(nrPatients);
		}

		//fill the event counts from the bag
		for (Item eventCount : priorEventCountsList){
			ExtendedMultiKey key = new ExtendedMultiKey (eventCount.getDescription(),Wildcard.INTEGER());
			HistogramStats priorEventCountStats= priorEventCountsBag.getHistogramStats(key);
			eventCount.setCount(priorEventCountStats.getCount());
			eventCount.setMedian(priorEventCountStats.getMedian());
			eventCount.setMean(priorEventCountStats.getMean());
			eventCount.setSD(priorEventCountStats.getStdDev());
			eventCount.setMin(priorEventCountStats.getMin());
			eventCount.setMax(priorEventCountStats.getMax());		
			eventCount.setP25(priorEventCountStats.getPercentile(25));
			eventCount.setP75(priorEventCountStats.getPercentile(75));
			eventCount.setTotal(nrPatients);
		}

		//fill the event counts from the bag
		for (Item eventCount : eventCountsList){
			ExtendedMultiKey key = new ExtendedMultiKey (eventCount.getDescription(),Wildcard.INTEGER());
			HistogramStats eventCountStats= eventCountsBag.getHistogramStats(key);
			eventCount.setCount(eventCountStats.getCount());
			eventCount.setMedian(eventCountStats.getMedian());
			eventCount.setMean(eventCountStats.getMean());
			eventCount.setSD(eventCountStats.getStdDev());
			eventCount.setMin(eventCountStats.getMin());
			eventCount.setMax(eventCountStats.getMax());		
			eventCount.setP25(eventCountStats.getPercentile(25));
			eventCount.setP75(eventCountStats.getPercentile(75));
			eventCount.setTotal(nrPatients);
		}

		//fill the event count definitions from the bag
		for (Item eventCount : eventCountDefinitionsList){
			double count = eventCountDefinitionsBag.getCount(eventCount.getDescription());
			eventCount.setCount((int) count);
			eventCount.setTotal(nrPatients);
		}


		//fill the boolean event count item list from the bag
		for (Item prescription : prescriptionsConcomitantList){
			double count = concomitantBag.getCount(prescription.getDescription());
			prescription.setCount((int) count);
			prescription.setTotal(nrPatients);
		}

		//fill the measurement counts from the bag
		measurementsList.addMissingItems(measCategoryBag,false);
		for (Item measurementCount : measurementsList){
			if (measurementCount.getValue().equals("CONTINUOUS")) {
				ExtendedMultiKey key = new ExtendedMultiKey (measurementCount.getDescription(),Wildcard.DOUBLE());
				HistogramStats measurementsStats= measContinuesBag.getHistogramStats(key);
				measurementCount.setCount(measurementsStats.getCount());
				measurementCount.setMean(measurementsStats.getMean());
				measurementCount.setMedian(measurementsStats.getMedian());
				measurementCount.setSD(measurementsStats.getStdDev());
				measurementCount.setMin(measurementsStats.getMin());
				measurementCount.setMax(measurementsStats.getMax());
				measurementCount.setP25(measurementsStats.getPercentile(25));
				measurementCount.setP75(measurementsStats.getPercentile(75));
				measurementCount.setTotal(nrPatients);
			}  else {
				measurementCount.setCount(measCategoryBag.getCount(new ExtendedMultiKey (measurementCount.getDescription(),measurementCount.getValue())));
				if (!measurementCount.getValue().equals("UNKNOWN"))
					measurementCount.setTotal(nrPatients - measCategoryBag.getCount(new ExtendedMultiKey (measurementCount.getDescription(),"UNKNOWN")));
				else
					measurementCount.setTotal(nrPatients);
			}
		}

		//fill the timeSince event counts from the bag 
		for (Item timeSinceEvent : timeSinceEventsList){
			ExtendedMultiKey key = new ExtendedMultiKey (timeSinceEvent.getDescription(), Wildcard.DOUBLE());
			timeSinceEvent.setCount(timeSinceEventsBag.getCount(key));
			timeSinceEvent.setTotal(nrPatients);
			HistogramStats timeSinceEventStats = timeSinceEventsBag.getHistogramStats(key);
			timeSinceEvent.setMean(timeSinceEventStats.getMean());
			timeSinceEvent.setMedian(timeSinceEventStats.getMedian());
			timeSinceEvent.setSD(timeSinceEventStats.getStdDev());
			timeSinceEvent.setMax(timeSinceEventStats.getMax());
			timeSinceEvent.setMin(timeSinceEventStats.getMin());
			timeSinceEvent.setP25(timeSinceEventStats.getPercentile(25));
			timeSinceEvent.setP75(timeSinceEventStats.getPercentile(75));
		}

		//fill the timeTo event counts from the bag 
		for (Item timeToEvent : timeToEventsList){
			ExtendedMultiKey key = new ExtendedMultiKey (timeToEvent.getDescription(), Wildcard.DOUBLE());
			timeToEvent.setCount(timeToEventsBag.getCount(key));
			timeToEvent.setTotal(nrPatients);
			HistogramStats timeSinceEventStats = timeToEventsBag.getHistogramStats(key);
			timeToEvent.setMean(timeSinceEventStats.getMean());
			timeToEvent.setMedian(timeSinceEventStats.getMedian());
			timeToEvent.setSD(timeSinceEventStats.getStdDev());
			timeToEvent.setMax(timeSinceEventStats.getMax());
			timeToEvent.setMin(timeSinceEventStats.getMin());
			timeToEvent.setP25(timeSinceEventStats.getPercentile(25));
			timeToEvent.setP75(timeSinceEventStats.getPercentile(75));
		}

		//fill the timeSince measurement counts from the bag 
		for (Item timeSinceMeasurement : timeSinceMeasurementsList){
			ExtendedMultiKey key = new ExtendedMultiKey (timeSinceMeasurement.getDescription(), Wildcard.DOUBLE());
			timeSinceMeasurement.setCount(timeSinceMeasurementsBag.getCount(key));
			timeSinceMeasurement.setTotal(nrPatients);
			HistogramStats timeSinceMeasurementStats = timeSinceMeasurementsBag.getHistogramStats(key);
			timeSinceMeasurement.setMean(timeSinceMeasurementStats.getMean());
			timeSinceMeasurement.setMedian(timeSinceMeasurementStats.getMedian());
			timeSinceMeasurement.setSD(timeSinceMeasurementStats.getStdDev());
			timeSinceMeasurement.setMax(timeSinceMeasurementStats.getMax());
			timeSinceMeasurement.setMin(timeSinceMeasurementStats.getMin());
			timeSinceMeasurement.setP25(timeSinceMeasurementStats.getPercentile(25));
			timeSinceMeasurement.setP75(timeSinceMeasurementStats.getPercentile(75));
		}

		//fill the timeTo drug counts from the bag 
		for (Item timeToDrug : timeToDrugsList){
			ExtendedMultiKey key = new ExtendedMultiKey (timeToDrug.getDescription(), Wildcard.DOUBLE());
			timeToDrug.setCount(timeToDrugsBag.getCount(key));
			timeToDrug.setTotal(nrPatients);
			HistogramStats timeToDrugStats = timeToDrugsBag.getHistogramStats(key);
			timeToDrug.setMean(timeToDrugStats.getMean());
			timeToDrug.setMedian(timeToDrugStats.getMedian());
			timeToDrug.setSD(timeToDrugStats.getStdDev());
			timeToDrug.setMax(timeToDrugStats.getMax());
			timeToDrug.setMin(timeToDrugStats.getMin());
			timeToDrug.setP25(timeToDrugStats.getPercentile(25));
			timeToDrug.setP75(timeToDrugStats.getPercentile(75));
		}

		//fill the exposureTo drug counts from the bag 
		for (Item exposureToDrug : exposureToDrugsList){
			ExtendedMultiKey key = new ExtendedMultiKey (exposureToDrug.getDescription(), Wildcard.DOUBLE());
			exposureToDrug.setCount(exposureToDrugsBag.getCount(key));
			exposureToDrug.setTotal(nrPatients);
			HistogramStats exposureToDrugStats = exposureToDrugsBag.getHistogramStats(key);
			exposureToDrug.setMean(exposureToDrugStats.getMean());
			exposureToDrug.setMedian(exposureToDrugStats.getMedian());
			exposureToDrug.setSD(exposureToDrugStats.getStdDev());
			exposureToDrug.setMax(exposureToDrugStats.getMax());
			exposureToDrug.setMin(exposureToDrugStats.getMin());
			exposureToDrug.setP25(exposureToDrugStats.getPercentile(25));
			exposureToDrug.setP75(exposureToDrugStats.getPercentile(75));
		}


		//fill the indication counts from bag		
		indicationsList.addMissingItems(indicationsBag,false);
		for (Item indication : indicationsList){
			indication.setCount(indicationsBag.getCount(new ExtendedMultiKey (indication.getDescription(), indication.getValue())));
			if (!indication.getValue().equals("UNKNOWN"))
				indication.setTotal(nrPatients -indicationsBag.getCount(new ExtendedMultiKey (indication.getDescription(),"UNKNOWN")));
			else
				indication.setTotal(nrPatients);
		}

		//fill the dosages counts from bag
		dosagesList.addMissingItems(dosagesBag,true);
		dosagesList.sort();

		for (Item dose : dosagesList){
			if (dose.getValue().equals("Unknown"))
				dose.setCount(dosagesBag.getCount(new ExtendedMultiKey (dose.getDescription(), dose.getValue())));
			else
				dose.setCount(dosagesBag.getCount(new ExtendedMultiKey (dose.getDescription(), Double.valueOf(dose.getValue()))));
			dose.setTotal(nrPatients);
		}

		prescriptionCountsList.clear();  //clear because all will be filled in now
		for (int i =0; i<prescriptionCounts.size(); i++){
			String[] prescriptionSplit = prescriptionCounts.get(i).split(";");
			String atc = prescriptionSplit[0];
			String description = prescriptionSplit[1];
			for (ExtendedMultiKey key : prescriptionCountsBag.getSortedKeySet()){
				if (((String) key.getKey(0)).equals(description)){
					//check the count in the multikey
					if (prescriptionCountsHigherEqual<0 || (int) key.getKey(1)<prescriptionCountsHigherEqual) {
						Item prescriptionCountItem = new Item();
						prescriptionCountItem.addLookup(atc);
						prescriptionCountItem.setValue(String.valueOf(key.getKey(1)));
						prescriptionCountItem.setDescription(description);
						prescriptionCountItem.setCount(prescriptionCountsBag.getCount(key));
						prescriptionCountItem.setTotal(nrPatients);
						prescriptionCountsList.add(prescriptionCountItem);						
					} 
				}
			}

			//add the higher than to the list
			if (prescriptionCountsHigherEqual>=0){
				Item prescriptionCountItem = new Item();
				prescriptionCountItem.addLookup(atc);
				prescriptionCountItem.setValue(">="+prescriptionCountsHigherEqual);
				prescriptionCountItem.setDescription(description);
				HistogramStats histogramStats = prescriptionCountsBag.getHistogramStats(new ExtendedMultiKey(description, Wildcard.INTEGER()));
				prescriptionCountItem.setCount(histogramStats.getHigherThan(prescriptionCountsHigherEqual-1));
				prescriptionCountItem.setTotal(nrPatients);
				prescriptionCountsList.add(prescriptionCountItem);		
			}
		}

		//fill the continuous prescription counts from the bag
		for (int i =0; i<prescriptionCounts.size(); i++){
			String[] prescriptionSplit = prescriptionCounts.get(i).split(";");
			String atc = prescriptionSplit[0];
			String description = prescriptionSplit[1];

			ExtendedMultiKey key = new ExtendedMultiKey (description,Wildcard.INTEGER());
			HistogramStats prescriptionCountStat = prescriptionCountsBag.getHistogramStats(key);
			Item prescriptionCountItem = new Item();
			prescriptionCountItem.addLookup(atc);
			prescriptionCountItem.setLabel(atc);
			prescriptionCountItem.setDescription(description);
			prescriptionCountItem.setValue("CONTINUOUS");
			prescriptionCountItem.setCount(prescriptionCountStat.getCount());
			prescriptionCountItem.setMedian(prescriptionCountStat.getMedian());
			prescriptionCountItem.setMean(prescriptionCountStat.getMean());
			prescriptionCountItem.setSD(prescriptionCountStat.getStdDev());
			prescriptionCountItem.setMin(prescriptionCountStat.getMin());
			prescriptionCountItem.setMax(prescriptionCountStat.getMax());		
			prescriptionCountItem.setP25(prescriptionCountStat.getPercentile(25));
			prescriptionCountItem.setP75(prescriptionCountStat.getPercentile(75));			
			prescriptionCountItem.setTotal(nrPatients);
			prescriptionContinuousList.add(prescriptionCountItem);	
		}


		//fill the concomitant item list from the bag
		for (Item prescription : prescriptionsConcomitantList){
			double count = concomitantBag.getCount(prescription.getDescription());
			prescription.setCount((int) count);
			prescription.setTotal(nrPatients);
		}	

		//fill the total event counts from the bag 
		for (Item event : eventsList){
			event.setCount(eventsBag.getCount(event.getDescription()));
			event.setTotal(nrPatients);
		}

		//fill in the prior event counts per patient from the bag
		for (int i =0; i<priorEventCounts.size(); i++){
			String[] eventSplit = priorEventCounts.get(i).split(";");
			String atc = eventSplit[0];
			String description = eventSplit[1];
			for (ExtendedMultiKey key : priorEventCountsBag.getSortedKeySet()){
				if (((String) key.getKey(0)).equals(description)){
					//check the count in the multikey
					if (eventCountsHigherEqual<0 || (int) key.getKey(1)<eventCountsHigherEqual) {
						Item eventCountItem = new Item();
						eventCountItem.addLookup(atc);
						eventCountItem.setValue(String.valueOf(key.getKey(1)));
						eventCountItem.setDescription(description);
						eventCountItem.setCount(priorEventCountsBag.getCount(key));
						eventCountItem.setTotal(nrPatients);

						priorEventCountsList.add(eventCountItem);						
					} 


				}
			}

			//add the higher than to the list
			if (eventCountsHigherEqual>=0){
				Item eventCountItem = new Item();
				eventCountItem.addLookup(atc);
				eventCountItem.setValue(">="+eventCountsHigherEqual);
				eventCountItem.setDescription(description);
				HistogramStats histogramStats = priorEventCountsBag.getHistogramStats(new ExtendedMultiKey(description, Wildcard.INTEGER()));
				eventCountItem.setCount(histogramStats.getHigherThan(eventCountsHigherEqual-1));
				eventCountItem.setTotal(nrPatients);
				priorEventCountsList.add(eventCountItem);		
			}
		}

		//fill in the event counts per patient from the bag
		for (int i =0; i<eventCounts.size(); i++){
			String[] eventSplit = eventCounts.get(i).split(";");
			String atc = eventSplit[0];
			String description = eventSplit[1];
			for (ExtendedMultiKey key : eventCountsBag.getSortedKeySet()){
				if (((String) key.getKey(0)).equals(description)){
					//check the count in the multikey
					if (eventCountsHigherEqual<0 || (int) key.getKey(1)<eventCountsHigherEqual) {
						Item eventCountItem = new Item();
						eventCountItem.addLookup(atc);
						eventCountItem.setValue(String.valueOf(key.getKey(1)));
						eventCountItem.setDescription(description);
						eventCountItem.setCount(eventCountsBag.getCount(key));
						eventCountItem.setTotal(nrPatients);

						eventCountsList.add(eventCountItem);						
					} 


				}
			}

			//add the higher than to the list
			if (eventCountsHigherEqual>=0){
				Item eventCountItem = new Item();
				eventCountItem.addLookup(atc);
				eventCountItem.setValue(">="+eventCountsHigherEqual);
				eventCountItem.setDescription(description);
				HistogramStats histogramStats = eventCountsBag.getHistogramStats(new ExtendedMultiKey(description, Wildcard.INTEGER()));
				eventCountItem.setCount(histogramStats.getHigherThan(eventCountsHigherEqual-1));
				eventCountItem.setTotal(nrPatients);
				eventCountsList.add(eventCountItem);		
			}
		}

		//fill the event count defintions per patient from the bag
		for (Item eventCountDefinition : eventCountDefinitionsList){
			double count = eventCountDefinitionsBag.getCount(eventCountDefinition.getDescription());
			eventCountDefinition.setCount((int) count);
			eventCountDefinition.setTotal(nrPatients);
		}

	}

	@Override
	public void outputResults() {

		calcStats();

		//sort the list alphabetically
		if (sortOutput) {
			comorbiditiesList.sort();
			eventsList.sort();
			prescriptionsList.sort();
			prescriptionCountsList.sort();
			prescriptionContinuousList.sort();
			prescriptionsConcomitantList.sort();
			priorEventCountsList.sort();
			eventCountsList.sort();
			eventCountDefinitionsList.sort();
			measurementsList.sort();
			timeSinceEventsList.sort();
			timeToEventsList.sort();
			timeSinceMeasurementsList.sort();
			timeToDrugsList.sort();
			exposureToDrugsList.sort();
			indicationsList.sort();	
			dosagesList.sort();
		}

		//Output statistics to log/console
		Logging.addNewLine();
		Logging.add("Descriptive module results:");
		Logging.add("-----------------------------\tN");
		Logging.add("Patients: " + nrPatients);
		Logging.add("Age: " + precisionFormat.format(ageMean) + 
				" ("+precisionFormat.format(ageSD)+")");

		HistogramStats cohortStats= cohortTimeBag.getHistogramStats(new ExtendedMultiKey (Wildcard.BYTE(), Wildcard.INTEGER()));
		double cohortMean = cohortStats.getMean(); 
		double cohortSD = cohortStats.getStdDev();
		double cohortCIL = cohortMean - 1.96*cohortSD/Math.sqrt(cohortStats.getCount());
		double cohortCIH = cohortMean + 1.96*cohortSD/Math.sqrt(cohortStats.getCount());
		double cohortP25 = cohortStats.getFirstQuartile();
		double cohortP75 = cohortStats.getThirdQuartile();
		double cohortMin = cohortStats.getMin();
		double cohortMax = cohortStats.getMax();
		long cohortCount = cohortStats.getCount();
		Logging.addNewLine();
		Logging.add("Cohort time (N,mean,sd,CI lower,CI upper,median,P25,P75,min,max) in days:" + cohortCount + "," +
				precisionFormat.format(cohortMean) +","+
				precisionFormat.format(cohortSD) +","+
				precisionFormat.format(cohortCIL) +","+
				precisionFormat.format(cohortCIH) +","+	
				precisionFormat.format(cohortMedian) +","+
				precisionFormat.format(cohortP25) +","+
				precisionFormat.format(cohortP75) +","+	
				precisionFormat.format(cohortMin)+","+
				precisionFormat.format(cohortMax));
		Logging.addNewLine();

		//output general stats
		StrBuilder out = new StrBuilder();

		//put header
		out.appendln("General");
		out.appendln("-----------------------------------\tN");

		if (population != null) {
			out.appendln("Original number of patients\t" + population.getOriginalCount());
			out.appendln("Patients with FU in study period\t" + (population.getOriginalCount() - population.getCountRemoved()));
			out.appendln("Patients with no FU in study period\t" + population.getCountRemoved());
		}
		if (cohort != null) {
			if (cohort.eventsInclusion.size() != 0) {
				out.appendln("Patients with event of interest\t" + cohort.getCountEventOfInterest());
			}
			out.appendln("Patients with drug of interest prescription\t" + cohort.getCountDrugOfInterest());
			if (population != null) {
				out.appendln("Patients with no drug of interest prescription\t" + (population.getOriginalCount() - population.getCountRemoved() - cohort.getCountDrugOfInterest()));
			}
			out.appendln("Patients with naive drug of interest prescription\t" + cohort.getCountNaiveDrugOfInterest());
			out.appendln("Patients with drug of interest prescription before study period\t" + (cohort.getCountDrugOfInterest() - cohort.getCountNaiveDrugOfInterest()));
			out.appendln("Patients using exclusion drug at start drug of interest prescription\t" + (cohort.getCountNaiveDrugOfInterest() - nrPatients));
		}
		out.appendln("Final cohort\t"+ nrPatients);
		out.appendNewLine();

		HistogramStats ageStats= ageBag.getHistogramStats(new ExtendedMultiKey (Wildcard.BYTE(), Wildcard.INTEGER()));
		ageMean = ageStats.getMean()/365.25;
		ageSD = ageStats.getStdDev()/365.25;
		double ageMean = ageStats.getMean()/365.25; 
		double ageMedian = ageStats.getMedian()/365.25; 
		double ageSD = ageStats.getStdDev()/365.25;
		double ageCIL = ageMean - 1.96*ageSD/Math.sqrt(ageStats.getCount());
		double ageCIH = ageMean + 1.96*ageSD/Math.sqrt(ageStats.getCount());
		double ageP25 = ageStats.getFirstQuartile()/365.25;
		double ageP75 = ageStats.getThirdQuartile()/365.25;
		double ageMin = ageStats.getMin()/365.25;
		double ageMax = ageStats.getMax()/365.25;

		out.appendln("\tN\tmean\tsd\tCIlow\tCIup\tmedian\tP25\tP75\tmin\tmax");
		out.appendln("Age\t"+ cohortCount + "\t" +
				precisionFormat.format(ageMean) +"\t"+
				precisionFormat.format(ageSD) +"\t"+
				precisionFormat.format(ageCIL) +"\t"+
				precisionFormat.format(ageCIH) +"\t"+	
				precisionFormat.format(ageMedian) +"\t"+
				precisionFormat.format(ageP25) +"\t"+
				precisionFormat.format(ageP75) +"\t"+	
				precisionFormat.format(ageMin)+"\t"+
				precisionFormat.format(ageMax));

		FileUtilities.writeStringToFile(outputFile,out.toString(),true);


		//Continuous measurements
		measurementsList.itemListToFile("",true, precisionFormat, this.outputFile);

		//Prior event counts
		priorEventCountsList.itemListToFile("",true,precisionFormat, this.outputFile);
		//Event counts
		eventCountsList.itemListToFile("",true,precisionFormat, this.outputFile);
		//Event count definitions
		eventCountDefinitionsList.itemListToFile("",true,precisionFormat, this.outputFile);
		//Time since event
		timeSinceEventsList.itemListToFile("",true, precisionFormat, this.outputFile);
		//Time to event
		timeToEventsList.itemListToFile("",true, precisionFormat, this.outputFile);
		//Time since measurement
		timeSinceMeasurementsList.itemListToFile("",true, precisionFormat, this.outputFile);
		//Time to drug
		timeToDrugsList.itemListToFile("",true, precisionFormat, this.outputFile);
		//Exposure to drug
		exposureToDrugsList.itemListToFile("",true, precisionFormat, this.outputFile);

		prescriptionContinuousList.itemListToFile("",true, precisionFormat, this.outputFile);

		out.setLength(0);

		out.appendln("Cohort time\t"+cohortCount + "\t" +
				precisionFormat.format(cohortMean) +"\t"+
				precisionFormat.format(cohortSD) +"\t"+
				precisionFormat.format(cohortCIL) +"\t"+
				precisionFormat.format(cohortCIH) +"\t"+
				precisionFormat.format(cohortMedian) +"\t"+
				precisionFormat.format(cohortP25) +"\t"+
				precisionFormat.format(cohortP75) +"\t"+	
				precisionFormat.format(cohortMin)+"\t"+
				precisionFormat.format(cohortMax));
		out.appendNewLine();

		if (cohortTimeHigherThan.size()>0) {
			out.appendln("Cohort Time Higher Than");
			out.append("----------"+"\tn (%)\tCI"+System.lineSeparator());
			if (nrPatients>0){
				int i = 0;
				for (String def: cohortTimeHigherThan){
					String[] split = def.split(";");
					out.appendln(split[0]+"\t" + cohortHigher.get(i) + " (" + precisionFormat.format(cohortHigher.get(i)*100/nrPatients) +
							")\t" + getWilsonCI(nrPatients,cohortHigher.get(i)));	
					i++;
				}
			} else {
				for (String def: cohortTimeHigherThan){
					String[] split = def.split(";");
					out.appendln(split[0]+"\t 0 (0.00) \t");	
				}
			}
		}

		out.appendNewLine();
		out.appendln("----------"+"\tn (%)\tCI");
		out.appendln("Female \t" + nrFemales + " (" + precisionFormat.format(percentageFemales) + ")\t" + getWilsonCI(nrPatients, nrFemales));
		out.appendln("Male \t" + nrMales + " (" + precisionFormat.format(percentageMales) + ")\t" + getWilsonCI(nrPatients, nrMales));

		out.appendln("Age (years)");
		for (AgeGroup group : ageGroupsParsed) {
			long sum = ageGroupBag.getCount(new ExtendedMultiKey(Wildcard.STRING(),group.label));
			double percentage = ((double)sum / nrPatients) * 100;
			out.appendln(group.label + " \t" + sum +	" (" + precisionFormat.format(percentage) + ")\t" +
					getWilsonCI(nrPatients, sum));
		}

		//output all tables
		boolean ok = true;
		ok &= FileUtilities.writeLineToFile(outputFile,out.toString(),true);
		ok &= measurementsList.itemListToFile("Measurements",false, precisionFormat, this.outputFile);

		//TODO: hard coded needs to be removed
		// TODO: these CI are calculated per PROXY or FEV1 Group, needs to be flexible.

		long total = 0;
		total = total + measCategoryBag.getCount(new ExtendedMultiKey("COPDSEV", "FEV1 VERY SEVERE"));
		total = total + measCategoryBag.getCount(new ExtendedMultiKey("COPDSEV", "FEV1 SEVERE"));
		total = total + measCategoryBag.getCount(new ExtendedMultiKey("COPDSEV", "FEV1 MODERATE"));
		total = total + measCategoryBag.getCount(new ExtendedMultiKey("COPDSEV", "FEV1 MILD"));
		total = total + measCategoryBag.getCount(new ExtendedMultiKey("COPDSEV", "FEV1 NO COPD"));

		out.setLength(0);

		out.appendln("\nCOPD Severity with CI for known severity cases");
		out.appendln("----------"+"\tn (%)\tCI");
		out.appendln("COPD Severity FEV1 No COPD\t" + 
				+ measCategoryBag.getCount(new ExtendedMultiKey("COPDSEV", "FEV1 NO COPD"))+" (" + 
				precisionFormat.format(((double) + measCategoryBag.getCount(new ExtendedMultiKey("COPDSEV", "FEV1 NO COPD"))/ total) * 100) + 
				")\t" + 
				getWilsonCI(total, (long)+ measCategoryBag.getCount(new ExtendedMultiKey("COPDSEV", "FEV1 NO COPD"))));

		out.appendln("COPD Severity FEV1 MILD\t" + 
				+ measCategoryBag.getCount(new ExtendedMultiKey("COPDSEV", "FEV1 MILD"))+" (" + 
				precisionFormat.format(((double) + measCategoryBag.getCount(new ExtendedMultiKey("COPDSEV", "FEV1 MILD"))/ total) * 100) + 
				")\t" + 
				getWilsonCI(total, (long)+ measCategoryBag.getCount(new ExtendedMultiKey("COPDSEV", "FEV1 MILD"))));
		out.appendln("COPD Severity FEV1 MODERATE\t" + 
				+ measCategoryBag.getCount(new ExtendedMultiKey("COPDSEV", "FEV1 MODERATE"))+" (" + 
				precisionFormat.format(((double) + measCategoryBag.getCount(new ExtendedMultiKey("COPDSEV", "FEV1 MODERATE"))/ total) * 100) + 
				")\t" + 
				getWilsonCI(total, (long)+ measCategoryBag.getCount(new ExtendedMultiKey("COPDSEV", "FEV1 MODERATE"))));
		out.appendln("COPD Severity FEV1 SEVERE\t" + 
				+ measCategoryBag.getCount(new ExtendedMultiKey("COPDSEV", "FEV1 SEVERE"))+" (" + 
				precisionFormat.format(((double) + measCategoryBag.getCount(new ExtendedMultiKey("COPDSEV", "FEV1 SEVERE"))/ total) * 100) + 
				")\t" + 
				getWilsonCI(total, (long)+ measCategoryBag.getCount(new ExtendedMultiKey("COPDSEV", "FEV1 SEVERE"))));
		out.appendln("COPD Severity FEV1 VERY SEVERE\t" + 
				+ measCategoryBag.getCount(new ExtendedMultiKey("COPDSEV", "FEV1 VERY SEVERE"))+" (" + 
				precisionFormat.format(((double) + measCategoryBag.getCount(new ExtendedMultiKey("COPDSEV", "FEV1 VERY SEVERE"))/ total) * 100) + 
				")\t" + 
				getWilsonCI(total, (long)+ measCategoryBag.getCount(new ExtendedMultiKey("COPDSEV", "FEV1 VERY SEVERE"))));

		// ALL FEV1
		total = 0;
		total = total + measCategoryBag.getCount(new ExtendedMultiKey("ALLFEV1SEV", "VERY SEVERE"));
		total = total + measCategoryBag.getCount(new ExtendedMultiKey("ALLFEV1SEV", "SEVERE"));
		total = total + measCategoryBag.getCount(new ExtendedMultiKey("ALLFEV1SEV", "MODERATE"));
		total = total + measCategoryBag.getCount(new ExtendedMultiKey("ALLFEV1SEV", "MILD"));
		total = total + measCategoryBag.getCount(new ExtendedMultiKey("ALLFEV1SEV", "NO COPD"));

		out.appendln("\nCOPD Severity with CI for known severity cases");
		out.appendln("----------"+"\tn (%)\tCI");
		out.appendln("COPD Severity FEV1 No COPD\t" + 
				+ measCategoryBag.getCount(new ExtendedMultiKey("COPDSEV", "FEV1 NO COPD"))+" (" + 
				precisionFormat.format(((double) + measCategoryBag.getCount(new ExtendedMultiKey("COPDSEV", "FEV1 NO COPD"))/ total) * 100) + 
				")\t" + 
				getWilsonCI(total, (long)+ measCategoryBag.getCount(new ExtendedMultiKey("COPDSEV", "FEV1 NO COPD"))));

		out.appendln("COPD Severity FEV1 MILD\t" + 
				+ measCategoryBag.getCount(new ExtendedMultiKey("COPDSEV", "FEV1 MILD"))+" (" + 
				precisionFormat.format(((double) + measCategoryBag.getCount(new ExtendedMultiKey("COPDSEV", "FEV1 MILD"))/ total) * 100) + 
				")\t" + 
				getWilsonCI(total, (long)+ measCategoryBag.getCount(new ExtendedMultiKey("COPDSEV", "FEV1 MILD"))));
		out.appendln("COPD Severity FEV1 MODERATE\t" + 
				+ measCategoryBag.getCount(new ExtendedMultiKey("COPDSEV", "FEV1 MODERATE"))+" (" + 
				precisionFormat.format(((double) + measCategoryBag.getCount(new ExtendedMultiKey("COPDSEV", "FEV1 MODERATE"))/ total) * 100) + 
				")\t" + 
				getWilsonCI(total, (long)+ measCategoryBag.getCount(new ExtendedMultiKey("COPDSEV", "FEV1 MODERATE"))));
		out.appendln("COPD Severity FEV1 SEVERE\t" + 
				+ measCategoryBag.getCount(new ExtendedMultiKey("COPDSEV", "FEV1 SEVERE"))+" (" + 
				precisionFormat.format(((double) + measCategoryBag.getCount(new ExtendedMultiKey("COPDSEV", "FEV1 SEVERE"))/ total) * 100) + 
				")\t" + 
				getWilsonCI(total, (long)+ measCategoryBag.getCount(new ExtendedMultiKey("COPDSEV", "FEV1 SEVERE"))));
		out.appendln("COPD Severity FEV1 VERY SEVERE\t" + 
				+ measCategoryBag.getCount(new ExtendedMultiKey("COPDSEV", "FEV1 VERY SEVERE"))+" (" + 
				precisionFormat.format(((double) + measCategoryBag.getCount(new ExtendedMultiKey("COPDSEV", "FEV1 VERY SEVERE"))/ total) * 100) + 
				")\t" + 
				getWilsonCI(total, (long)+ measCategoryBag.getCount(new ExtendedMultiKey("COPDSEV", "FEV1 VERY SEVERE"))));


		//PROXY
		//       
		total = 0;
		total = total + measCategoryBag.getCount(new ExtendedMultiKey("COPDSEV", "PROXY VERY SEVERE"));
		total = total + measCategoryBag.getCount(new ExtendedMultiKey("COPDSEV", "PROXY SEVERE"));
		total = total + measCategoryBag.getCount(new ExtendedMultiKey("COPDSEV", "PROXY MODERATE"));
		total = total + measCategoryBag.getCount(new ExtendedMultiKey("COPDSEV", "PROXY MILD"));
		total = total + measCategoryBag.getCount(new ExtendedMultiKey("COPDSEV", "PROXY NO COPD"));
		out.appendln("COPD Severity PROXY NO COPD\t " + 
				+ measCategoryBag.getCount(new ExtendedMultiKey("COPDSEV", "PROXY NO COPD"))+" (" + 
				precisionFormat.format(((double) + measCategoryBag.getCount(new ExtendedMultiKey("COPDSEV", "PROXY NO COPD"))/ total) * 100) + 
				")\t" + 
				getWilsonCI(total, (long)+ measCategoryBag.getCount(new ExtendedMultiKey("COPDSEV", "PROXY NO COPD"))));

		out.appendln("COPD Severity MILD\t" + 
				+ measCategoryBag.getCount(new ExtendedMultiKey("COPDSEV", "PROXY MILD"))+" (" + 
				precisionFormat.format(((double) + measCategoryBag.getCount(new ExtendedMultiKey("COPDSEV", "PROXY MILD"))/ total) * 100) + 
				")\t" + 
				getWilsonCI(total, (long)+ measCategoryBag.getCount(new ExtendedMultiKey("COPDSEV", "PROXY MILD"))));
		out.appendln("COPD Severity MODERATE\t" + 
				+ measCategoryBag.getCount(new ExtendedMultiKey("COPDSEV", "PROXY MODERATE"))+" (" + 
				precisionFormat.format(((double) + measCategoryBag.getCount(new ExtendedMultiKey("COPDSEV", "PROXY MODERATE"))/ total) * 100) + 
				")\t" + 
				getWilsonCI(total, (long)+ measCategoryBag.getCount(new ExtendedMultiKey("COPDSEV", "PROXY MODERATE"))));
		out.appendln("COPD Severity SEVERE\t" + 
				+ measCategoryBag.getCount(new ExtendedMultiKey("COPDSEV", "PROXY SEVERE"))+" (" + 
				precisionFormat.format(((double) + measCategoryBag.getCount(new ExtendedMultiKey("COPDSEV", "PROXY SEVERE"))/ total) * 100) + 
				")\t" + 
				getWilsonCI(total, (long)+ measCategoryBag.getCount(new ExtendedMultiKey("COPDSEV", "PROXY SEVERE"))));

		out.appendln("COPD Severity VERY SEVERE\t" + 
				+ measCategoryBag.getCount(new ExtendedMultiKey("COPDSEV", "PROXY VERY SEVERE"))+" (" + 
				precisionFormat.format(((double) + measCategoryBag.getCount(new ExtendedMultiKey("COPDSEV", "PROXY VERY SEVERE"))/ total) * 100) + 
				")\t" + 
				getWilsonCI(total, (long)+ measCategoryBag.getCount(new ExtendedMultiKey("COPDSEV", "PROXY VERY SEVERE"))));

		ok &= FileUtilities.writeStringToFile(outputFile,out.toString()+System.lineSeparator(),true);
		ok &= priorEventCountsList.itemListToFile("Prior Event Counts", true, precisionFormat, this.outputFile);
		ok &= eventCountsList.itemListToFile("Event Counts", true, precisionFormat, this.outputFile);
		ok &= eventCountDefinitionsList.itemListToFile("Event Count Definitions", false, precisionFormat, this.outputFile);
		ok &= prescriptionCountsList.itemListToFile("Prescription Counts", false, precisionFormat, this.outputFile);
		ok &= indicationsList.itemListToFile("Indications", false, precisionFormat, this.outputFile);
		ok &= dosagesList.itemListToFile("Dosage", false, precisionFormat, this.outputFile);

		ok &= prescriptionsList.itemListToFile("Prior prescriptions", false, precisionFormat, this.outputFile);
		ok &= prescriptionsConcomitantList.itemListToFile("Concomitant", false, precisionFormat, this.outputFile);
		ok &= comorbiditiesList.itemListToFile("Comorbidities", false, precisionFormat, this.outputFile);
		ok &= eventsList.itemListToFile("Events in cohort time", false, precisionFormat, this.outputFile);

		if (!ok)
			Logging.add("Could not write to file "+outputFile);

		Jerboa.getOutputManager().closeAll();
		Logging.add("Descriptive files have been created succesfully");

		return;
	}

	/**
	 * Retrieve the Wilson confidence interval for @total and @count.
	 */
	private String getWilsonCI(long total, long count) {
		WilsonCI wilson = new WilsonCI(0.05,(int) total, count);
		return precisionFormat.format(wilson.getLowerLimit()*100) + "-" + precisionFormat.format(wilson.getUpperLimit()*100);
	}

	/**
	 * The AgeGroup inner class is used to hold an ageGroup definition.
	 */
	private class AgeGroup {
		private String label = "";
		private int minAge = 0;
		private int maxAge = 999;

		public AgeGroup(String label, int min, int max) {
			this.label = label;
			this.minAge = min;
			this.maxAge = max;
		}

		public boolean inAgeGroup(double age) {
			return (age>=minAge && age<maxAge);
		}
	}

}
