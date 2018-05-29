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
 * Author: Mees Mosseveld (MM) - department of Medical Informatics						  *
 * 																						  *
 * $Rev:: 3961              $:  Revision of last commit                                   *
 * $Author:: root           $:  Author of last commit                                     *
 * $Date:: 2013-05#			$:  Date and time (CET) of last commit						  *
 ******************************************************************************************/

package org.erasmusmc.jerboa.modules;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

import org.erasmusmc.jerboa.Jerboa;
import org.erasmusmc.jerboa.dataClasses.Event;
import org.erasmusmc.jerboa.dataClasses.Measurement;
import org.erasmusmc.jerboa.dataClasses.Patient;
import org.erasmusmc.jerboa.dataClasses.Prescription;
import org.erasmusmc.jerboa.engine.Module;
import org.erasmusmc.jerboa.engine.PatientObjectCreator;
import org.erasmusmc.jerboa.gui.graphs.Graphs;
import org.erasmusmc.jerboa.gui.graphs.LinePlotDS;
import org.erasmusmc.jerboa.gui.graphs.Plot;
import org.erasmusmc.jerboa.modifiers.PopulationDefinition;
import org.erasmusmc.jerboa.modifiers.PrescriptionCohortDefinition;
import org.erasmusmc.jerboa.config.DataDefinition;
import org.erasmusmc.jerboa.config.FilePaths;
import org.erasmusmc.jerboa.config.Parameters;
import org.erasmusmc.jerboa.utilities.AgeGroupDefinition;
import org.erasmusmc.jerboa.utilities.AgeGroupDefinition.AgeGroup;
import org.erasmusmc.jerboa.utilities.stats.HistogramStats;
import org.erasmusmc.jerboa.utilities.DateUtilities;
import org.erasmusmc.jerboa.utilities.ExtendedMultiKey;
import org.erasmusmc.jerboa.utilities.FileUtilities;
import org.erasmusmc.jerboa.utilities.StringUtilities;
import org.erasmusmc.jerboa.utilities.TimeUtilities;
import org.erasmusmc.jerboa.utilities.Wildcard;
import org.erasmusmc.jerboa.utilities.InputFileUtilities;
import org.erasmusmc.jerboa.utilities.Logging;
import org.erasmusmc.jerboa.utilities.MultiKeyBag;
import org.erasmusmc.jerboa.utilities.MultiKeyMap;
import org.erasmusmc.jerboa.utilities.Progress;
import org.erasmusmc.jerboa.utilities.Timer;

/**
 * This module creates a dataset that can be used to calculate the incidence rate of the
 * specified events, aggregated on different levels.
 * @author Mees Mosseveld
 *
 */
public class IncidenceRate extends Module{

	/**
	 * MODULE PARAMETERS
	 */

	/**
	 * Optional list of events of interest.
	 * When empty all events are used.
	 *
	 *  eventType
	 *    A single event type
	 *  label;eventType1,eventType2, ... , eventTypeN
	 *    Combines the event types eventType1 - eventTypeN in one event type named label.
	 */
	public List<String> eventsOfInterest = new ArrayList<String>();

	/**
	 * When true events are searched in all history otherwise only in cohort time.
	 */
	public boolean allHistory;

	/**
	 * Age group definition used for the Age at cohort start categories.
	 * Format: Min;Max;Label
	 *
	 *   Min    - minimum age in years (inclusive)
	 *   Max    - maximum age in years (exclusive)
	 *   Label  - label used in output
	 *
	 * Examples:
	 *   0;5;0-4          0 to 4 years
	 *   0d;22d;0-21d     0 to 21 days
	 *   22d;1y;22d-1y    22 days to 1 year (not inclusive)
	 */
	public List<String> ageGroups = new ArrayList<String>();

	/**
	 * At what time the age of the patient is determined.
	 * DYNAMIC       - At the date of the event.
	 * COHORT START  - At cohort start date
	 */
	public String ageAt;

	/**
	 * Holds the data on the reference population. When no data is provided, the standardized error rate
	 * is not computed. The format should be:
	 * ageRangeLabel;percentage (e.g.: '60-64;3.72')
	 */
	public List<String> referencePopulation = new ArrayList<String>();

	/**
	 * Specifies any subgroups to be identified for the calculation additional standardized rates.
	 * Each code should be represented as two semicolon separated values:
	 *
	 * - The label of the subgroup (the same label can be used in multiple rows)
	 * - The age range
	 *
	 * For example: children;0-4
	 */
	public List<String> populationSubgroups = new ArrayList<String>();

	/**
	 * When true, patient time stops after first event.
	 */
	public boolean censorOnEvent = true;

	/**
	 * List of additional aggregation levels besides year, month, agegroup, and gender.
	 * The format should be:
	 *
	 *   EVENT;<label>;<window>;<reference>;<unknown value>;<rule>;...;<rule>
	 *   MEASUREMENT;<label>;<window>;<reference>;<unknown value>;<rule>;...;<rule>
	 *   PRESCRIPTION;<label>;<window>;<reference>;<unknown value>;<rule>;...;<rule>
	 *
	 * where
	 *
	 * <window>         Is the maximum number of days (positive) before the event.
	 * <reference>      Determines from when the history is reviewed:
	 *
	 *                    COHORT START
	 *                    EVENT or not specified
	 *
	 * <unkown value>   The value used when it is not present in the <window>
	 * <rule>           The rule for selecting events, measurements, or prescriptions.
	 *
	 *                  For events its:
	 *
	 *                    <result value>,<event type>[:<code>],...,<event type>[:<code>]
	 *
	 *                  where:
	 *
	 *                    <result value>     = The value used in the result for this rule.
	 *                    <event type>       = The event type to search for.
	 *                    <code>             = The code of the event to search for. Optional. May be empty.
	 *
	 *                  For measurements its:
	 *
	 *                    <result value>,<measurement type>:<value>,...,<measurement type>:<value>
	 *
	 *                  where:
	 *
	 *                    <result value>     = The value used in the result for this rule.
	 *                    <measurement type> = The measurement type to search for.
	 *                    <value>            = The value of the measurement to search for.
	 *
	 *                  For prescriptions its:
	 *
	 *                    <result value>,<ATC>[:<dose>[:<indication>]],...,<ATC>[:<dose>[:<indication>]]
	 *
	 *                  where:
	 *
	 *                    <result value>     = The value used in the result for this rule.
	 *                    <ATC>              = The ATC code to search for.
	 *                    <dose>             = The dose of the prescription to search for. Optional. May be empty.
	 *                    <indication>       = The indication of the prescription to search for. Optional. May be empty.
	 */
	public List<String> aggregationLevels = new ArrayList<String>();

	/**
	 * Year or Month
	 * When Month also incidence rates per month will be calculated.
	 * Default = Year
	 */
	public String timeDivision;

	/**
	 * Incidence rates are divided by this number of years.
	 */
	public int perNYears;

	/**
	 * The minimum number of subjects in a row of the resulting aggregated table. Rows with fewer subjects
	 * are deleted. Only works in combination with the compact output.
	 */
	public int minSubjectsPerRow;

	/**
	 * If true the patient time before and after is in a separate output file
	 */
	public boolean outputPatientTime;

	/**
	 * While output a separate file with the overall Incidence
	 */
	public boolean outputOverallIncidence;

	/**
	 * If true an attrition filename will be created or appended on called Attrition.csv
	 */
	public boolean attritionFile;



	/* Local variables */

	private static Byte YEAR = 0;
	private static Byte MONTH = 1;

	private String patientTimeFileName = "";

	// Bags
	private Map<String, Integer> subjectsCount;                     // Event, Year, Month, AgeGroup, Gender
	private Map<String, Integer> eventsCount;                       // Event, Year, Month, AgeGroup, Gender
	private Map<String, Long> daysCount;                         // Event, Year, Month, AgeGroup, Gender

	private MultiKeyMap incidenceOverallMap;                        // Event, AgeGroup
	private MultiKeyMap incidenceOverallGenderMap;                  // Event, AgeGroup, Gender
	private MultiKeyMap incidenceMap;                               // Event, Year, AgeGroup
	private MultiKeyMap incidenceOverallYearMap;                    // Event, Year
	private MultiKeyMap incidenceOverallYearGenderMap;              // Event, Year, Gender
	private MultiKeyMap incidenceGenderMap;                         // Event, Year, AgeGroup, Gender

	private MultiKeyBag historyBag = null;            		  		//
	private MultiKeyBag followUpBag = null;            		  		//

	// Index lists
	private Map<String, List<String> > allPeriodKeys;
	private List<String> allEventTypes = new ArrayList<String>();   // All events

	// Age group definitions
	private AgeGroupDefinition ageGroupDefinition = null;
	private AgeGroupDefinition graphAgeGroupDefinition = null;
	private AgeGroupDefinition allAgeGroupDefinition = null;
	private List<String> allAgeGroups = null;
	private List<String> graphAgeGroups = new ArrayList<String>() {
	private static final long serialVersionUID = 1664240127531037784L;

		{
			add("0;5;00-04 years");
			add("5;10;05-09 years");
			add("10;15;10-14 years");
			add("15;20;15-19 years");
			add("20;25;20-24 years");
			add("25;30;25-29 years");
			add("30;35;30-34 years");
			add("35;40;35-39 years");
			add("40;45;40-44 years");
			add("45;50;45-49 years");
			add("50;55;50-54 years");
			add("55;60;55-59 years");
			add("60;65;60-64 years");
			add("65;70;65-69 years");
			add("70;75;70-74 years");
			add("75;80;75-79 years");
			add("80;85;80-84 years");
			add("85;999;85- years");
		}
	};

	private Map<String, Set<String>> eventMapping = null;

	// Reference population definition
	private PopulationDistribution referencePopulationDistribution = null;

	// Population subgroups definition
	private Map<String, Set<String>> populationSubgroupsDefinition = null;
	private List<String> populationSubgroupList = null;

	private Byte timeDivisionPeriod = YEAR;

	private String acceptedEventsFileName = "";
	private String rejectedEventsFileName = "";
	private String countLogFileName = "";
	private String periodsFileName = "";

	private Set<Event> eventsFound;
	private Map<Byte, List<Event>> uniqueEvents;
	private Set<String> eventTypeSet = new HashSet<String>();
	private Set<String> subjectsSet;
	private Set<String> years;
	private Set<String> historyFollowupEventTypes;

	// Aggregation List
	private AggregationLevels aggregations;
	private Set<String> aggregationLevelsFound;

	// Censored flag
	private Map<Byte, Set<String>> censored;

	private Patient currentPatient = null;
	private Map<String, Map<String, Long>> periods = null;
	private boolean periodsFullKeyOnly = true;
	private int periodsHeaderSize = 0;
	private boolean periodsDefinedAgeGroupsOnly = true;

	private String incidenceBagsDebugFileName;

	// Counters
	private int originalCount;
	private Map<String, Boolean> patientCounted;
	private Map<String, Integer> censoredCount;
	private Map<String, Integer> incidentCount;

	private String attritionFileName;   //used to save the attrition

	private boolean debug = true;  //outputs bag data


	@Override
	public void setNeededFiles() {
		setRequiredFile(DataDefinition.PATIENTS_FILE);
		setRequiredFile(DataDefinition.EVENTS_FILE);
		boolean measurementAggregations = false;
		boolean prescriptionAggregations = false;
		for (String level : aggregationLevels) {
			String type = level.split(";")[0].toUpperCase();
			if (type.equals("MEASUREMENT")) {
				measurementAggregations = true;
			}
			else if (type.equals("PRESCRIPTION")) {
				prescriptionAggregations = true;
			}
		}
		if (measurementAggregations) setRequiredFile(DataDefinition.MEASUREMENTS_FILE);
		if (prescriptionAggregations) setRequiredFile(DataDefinition.PRESCRIPTIONS_FILE);
	}


	@Override
	public void setNeededExtendedColumns() { /* NOTHING TO ADD */ }


	@Override
	public void setNeededNumericColumns() {
		// TODO Auto-generated method stub

	}

	@Override
	public boolean init() {

		boolean initOK = true;

		if (timeDivision.toLowerCase().equals("month")) {
			timeDivisionPeriod = MONTH;
		}
		else {
			timeDivisionPeriod = YEAR;
		}

		// Parse age group definition and create all age group lists.
		if (ageGroups == null) {
			ageGroups = new ArrayList<String>();
			ageGroups.add(0, "0;999;ALL");
		}
		else {
			boolean allFound = false;
			for (String ageGroup : ageGroups) {
				String[] ageGroupSplit = ageGroup.split(";");
				if (ageGroupSplit[2].equals("ALL")) {
					allFound = true;
					break;
				}
			}
			if (!allFound) {
				ageGroups.add(0, "0;999;ALL");
			}
		}
		ageGroupDefinition = new AgeGroupDefinition(ageGroups);
		graphAgeGroupDefinition = new AgeGroupDefinition(graphAgeGroups);
		allAgeGroups = new ArrayList<String>();
		for (String ageGroup : ageGroups) {
			allAgeGroups.add(ageGroup);
		}
		for (String ageGroup : graphAgeGroups) {
			if (!allAgeGroups.contains(ageGroup)) {
				allAgeGroups.add(ageGroup);
			}
		}
		Collections.sort(allAgeGroups);
		allAgeGroupDefinition = new AgeGroupDefinition(allAgeGroups);

		// Parse reference population definition
		if (referencePopulation.size() > 0)
			referencePopulationDistribution = new PopulationDistribution(referencePopulation);

		// Parse population subgroups definition
		populationSubgroupsDefinition = new HashMap<String, Set<String>>();
		if (populationSubgroups.size() > 0) {
			Set<String> subGroupAgeGroups = new HashSet<String>();
			populationSubgroupsDefinition.put("Standardized: ", subGroupAgeGroups);
			for (AgeGroup ageGroup : ageGroupDefinition.getAgeGroups())
				subGroupAgeGroups.add(ageGroup.getLabel());

			for (String subgroup : populationSubgroups) {
				String[] subgroupSplit = subgroup.split(";");
				String subGroupId = "Standardized: " + subgroupSplit[0];
				if ((subGroupAgeGroups = populationSubgroupsDefinition.get(subGroupId)) == null) {
					subGroupAgeGroups = new HashSet<String>();
					populationSubgroupsDefinition.put(subGroupId, subGroupAgeGroups);
				}
				subGroupAgeGroups.add(subgroupSplit[1]);
			}
			populationSubgroupList = new ArrayList<String>();
			for (String subGroup : populationSubgroupsDefinition.keySet())
				populationSubgroupList.add(subGroup);
		}

		// Create the bags
		subjectsCount = new HashMap<String, Integer>();            // Event, Year, Month, AgeGroup, Gender
		eventsCount = new HashMap<String, Integer>();              // Event, Year, Month, AgeGroup, Gender
		daysCount = new HashMap<String, Long>();                // Event, Year, Month, AgeGroup, Gender
		incidenceOverallMap = new MultiKeyMap();                   // Event, AgeGroup, Incidence
		incidenceOverallGenderMap = new MultiKeyMap();             // Event, AgeGroup, Gender, Incidence
		incidenceMap = new MultiKeyMap();                          // Event, Period, AgeGroup, Incidence
		incidenceOverallYearMap = new MultiKeyMap();               // Event, Year, Incidence
		incidenceOverallYearGenderMap = new MultiKeyMap();         // Event, Period, Gender, Incidence
		incidenceGenderMap = new MultiKeyMap();                    // Event, Period, AgeGroup, Gender, Incidence

		historyBag = new MultiKeyBag();
		followUpBag = new MultiKeyBag();

		// Create the index lists
		eventMapping = new HashMap<String, Set<String>>();
		allPeriodKeys = new HashMap<String, List<String>>();
		allPeriodKeys.put("ALL", new ArrayList<String>());
		if (eventsOfInterest.size() > 0) {
			for (int nr = 0; nr < eventsOfInterest.size(); nr++) {
				String[] eventDefintionSplit = eventsOfInterest.get(nr).split(";");
				if (eventDefintionSplit.length > 1) {
					String[] sourceEventTypes = eventDefintionSplit[1].split(",");
					for (int part = 0; part < sourceEventTypes.length; part++) {
						if (!sourceEventTypes[part].equals("")) {
							if (!eventMapping.containsKey(sourceEventTypes[part])) {
								eventMapping.put(sourceEventTypes[part], new HashSet<String>());
							}
							eventMapping.get(sourceEventTypes[part]).add(eventDefintionSplit[0]);
						}
					}
					eventsOfInterest.set(nr, eventDefintionSplit[0]);
				}
			}
			allEventTypes = eventsOfInterest;                       // All events
		}
		else
			allEventTypes = FileUtilities.getList(InputFileUtilities.getEventTypes());     // All events

		aggregations = new AggregationLevels(aggregationLevels);
		aggregationLevelsFound = new HashSet<String>();

		if (intermediateFiles) {
			/*
			acceptedEventsFileName = StringUtilities.addSuffixToFileName(intermediateFileName, "_incidenceRateAcceptedEvents", false);
			initOK = Jerboa.getOutputManager().addFile(acceptedEventsFileName, 100);
			Jerboa.getOutputManager().writeln(acceptedEventsFileName, "PatientID,Gender,Date,EventType,Code", true);

			rejectedEventsFileName = StringUtilities.addSuffixToFileName(intermediateFileName, "_incidenceRateRejectedEvents", false);
			initOK = Jerboa.getOutputManager().addFile(rejectedEventsFileName, 100);
			Jerboa.getOutputManager().writeln(rejectedEventsFileName, "PatientID,Gender,Date,EventType,Code,Remark", true);


			countLogFileName = StringUtilities.addSuffixToFileName(intermediateFileName, "_CountLog.csv", false);
			initOK = Jerboa.getOutputManager().addFile(countLogFileName, 100);
			Jerboa.getOutputManager().writeln(countLogFileName, "PatientID,Gender,Function,Key,Count", true);
			*/

			periodsFileName = StringUtilities.addSuffixToFileName(intermediateFileName,"_Periods", false);
			initOK = Jerboa.getOutputManager().addFile(periodsFileName, 100);
			String aggregationHeader = aggregations.getAggregationHeader();
			String header = "PatientID,EventType,Year,Month,AgeGroup" + (aggregationHeader.equals("") ? "" : "," + aggregationHeader) + ",Gender,Days,Events";
			periodsHeaderSize = header.split(",").length;
			Jerboa.getOutputManager().writeln(periodsFileName, header, true);

		}

		if (outputPatientTime){
			patientTimeFileName = StringUtilities.addSuffixToFileName(outputFileName, "_patientTime", false);
			Jerboa.getOutputManager().addFile(patientTimeFileName, 100);
			Jerboa.getOutputManager().writeln(patientTimeFileName, "Event,N,MeanBefore,SDBefore,MinBefore,MaxBefore,SumBefore,MeanAfter,SDAfter,MinAfter,MaxAfter,SumAfter", true);

		}

		if (initOK && attritionFile && (!Jerboa.unitTest)) {
			attritionFileName = FilePaths.WORKFLOW_PATH+this.title+"/"+
					Parameters.DATABASE_NAME+"_"+this.title+
					"_"+TimeUtilities.TIME_STAMP+"_"+Parameters.VERSION+"_attrition.csv";
			Jerboa.getOutputManager().addFile(attritionFileName, 100);
		}

		// Initialize counters
		originalCount = 0;
		patientCounted = new HashMap<String, Boolean>();
		censoredCount = new HashMap<String, Integer>();
		incidentCount = new HashMap<String, Integer>();
		for (String eventType : allEventTypes) {
			censoredCount.put(eventType, 0);
			incidentCount.put(eventType, 0);
		}

		return initOK;
	}


	@Override
	public Patient process(Patient patient) {

		if ((patient != null) && patient.isInCohort()) {
			originalCount++;
			for (String eventType : allEventTypes) {
				patientCounted.put(eventType, false);
			}

			censored = new HashMap<Byte, Set<String>>();
			censored.put(YEAR, new HashSet<String>());
			censored.put(MONTH, new HashSet<String>());

			currentPatient = patient;

			if (intermediateFiles && (!periodsFileName.equals(""))) {
				//periods.put(currentPatient.ID, new HashMap<String, Map<String, Integer>>());
				periods = new HashMap<String, Map<String, Long>>();
			}

			Set<String> patientEventTypes = new HashSet<String>();

			// Get the events of the patient.
			List<Event> patientEvents = patient.getEvents();

			// Create a set of all eventTypes
			eventTypeSet = new HashSet<String>();
			for (String eventType : allEventTypes) {
				eventTypeSet.add(eventType);
			}

			// Collect relevant aggregation events, measurements, and prescriptions
			aggregations.getRelevantData(patient);

			// Get rid of duplicate events (sometimes the same event occurs with different code values).
			uniqueEvents = new HashMap<Byte, List<Event>>();
			uniqueEvents.put(YEAR, new ArrayList<Event>());
			uniqueEvents.put(MONTH, new ArrayList<Event>());

			List<Event> events = new ArrayList<Event>();
			Event lastEvent = null;
			for (Event orgEvent : patientEvents) {
				events.add(orgEvent);
		//		if ((allHistory && (orgEvent.getDate() >= patient.getPopulationStartDate())) || ((orgEvent.getDate() >= patient.getCohortStartDate()) && (orgEvent.getDate() <= patient.getCohortEndDate()))) {
				if (allHistory || ((orgEvent.getDate() >= patient.getCohortStartDate()) && (orgEvent.getDate() <= patient.getCohortEndDate()))) {

					Set<String> typeSet = eventMapping.get(orgEvent.getType());
					if (typeSet == null) {
						typeSet = new HashSet<String>();
					}
					typeSet.add(orgEvent.getType());
					for (String eventType : typeSet) {
						Event event = null;
						if (orgEvent.getType().equals(eventType)) {
							event = orgEvent;
						}
						else {
							event = new Event(orgEvent);
							event.setType(eventType);
							event.setCode(orgEvent.getType());
							events.add(event);
							if (intermediateFiles && (!acceptedEventsFileName.equals(""))) {
								Jerboa.getOutputManager().writeln(acceptedEventsFileName, patient.ID + "," + Patient.convertGender(patient.gender) + "," + DateUtilities.daysToDate(event.date) + ',' + event.getType() + ',' + event.getCode(), true);
							}
						}
						if (allEventTypes.contains(event.getType())) {
							if (((lastEvent == null) || (event.getDate() != lastEvent.getDate()) || (!event.getType().equals(lastEvent.getType()))) && ((!censorOnEvent) || (!patientEventTypes.contains(event.getType())))) {
								uniqueEvents.get(YEAR).add(event);
								uniqueEvents.get(MONTH).add(event);
								if (censorOnEvent && (event.getDate() < patient.getCohortStartDate())) {
									censored.get(YEAR).add(event.getType());
									censored.get(MONTH).add(event.getType());
								}
								lastEvent = event;
								patientEventTypes.add(event.getType());
								// Remove event from eventType set
								eventTypeSet.remove(event.getType());
							}
							else {
								if (intermediateFiles && (!rejectedEventsFileName.equals(""))) {
									Jerboa.getOutputManager().writeln(rejectedEventsFileName, patient.ID + "," + Patient.convertGender(patient.gender) + "," + DateUtilities.daysToDate(event.date) + ',' + event.getType() + ',' + event.getCode() + ",DUPLICATE EVENT", true);
								}
							}
						}
					}
				}
			}
			Collections.sort(events);

			int start = allHistory ? patient.getPopulationStartDate() : patient.getCohortStartDate();
			int end = patient.getCohortEndDate() + 1;

			int startPeriod = getPeriodFromDate(start, timeDivisionPeriod);
			int endPeriod   = getPeriodFromDate(end, timeDivisionPeriod);

			int period = startPeriod;
			String currentYear = (timeDivisionPeriod == MONTH ? Integer.toString(period / 100) : Integer.toString(period));
			String currentMonth = timeDivisionPeriod == MONTH ? Integer.toString(period - ((period / 100) * 100)) : "ALL";
			int nextPeriod = nextPeriod(period, timeDivisionPeriod);

			subjectsSet = new HashSet<String>();
			years = new HashSet<String>();
			historyFollowupEventTypes = new HashSet<String>();

			AddSubjects("OVERALL" + "_" + Patient.convertGender(patient.gender), 1);
			AddDays("OVERALL" + "_" + Patient.convertGender(patient.gender), patient.getCohortEndDate() - patient.getCohortStartDate());
			eventsFound = new HashSet<Event>();

			while (period <= endPeriod) {

				if (!allPeriodKeys.containsKey(currentYear)) {
					allPeriodKeys.put(currentYear, new ArrayList<String>());
				}
				if (!allPeriodKeys.get(currentYear).contains(currentMonth)) {
					allPeriodKeys.get(currentYear).add(currentMonth);
				}
				if (!allPeriodKeys.get("ALL").contains(currentMonth)) {
					allPeriodKeys.get("ALL").add(currentMonth);
				}

				if ((timeDivisionPeriod != YEAR) && years.add(currentYear)) {
					count(patient, start, end, Integer.parseInt(currentYear), Integer.parseInt(currentYear) + 1, currentYear, "ALL", YEAR);
					if (!allPeriodKeys.get(currentYear).contains("ALL")) {
						allPeriodKeys.get(currentYear).add("ALL");
					}
					if (!allPeriodKeys.get("ALL").contains("ALL")) {
						allPeriodKeys.get("ALL").add("ALL");
					}
				}

				count(patient, start, end, period, nextPeriod, currentYear, currentMonth, timeDivisionPeriod);

				period = nextPeriod;
				currentYear = (timeDivisionPeriod == MONTH ? Integer.toString(period / 100) : Integer.toString(period));
				currentMonth = timeDivisionPeriod == MONTH ? Integer.toString(period - ((period / 100) * 100)) : "ALL";
				nextPeriod = nextPeriod(period, timeDivisionPeriod);
			}

			if (intermediateFiles && (!periodsFileName.equals(""))) {
				for (String key : periods.keySet()) {
					String exportKey = (aggregations.getAggregationHeader().equals("") ? key.replaceAll("__", "_") : key).replaceAll("_", ",");
					boolean writePeriodsRecord = true;
					if (periodsFullKeyOnly) {
						if (writePeriodsRecord = (!exportKey.contains(",ALL,ALL,")) && (exportKey.split(",").length == (periodsHeaderSize - 3))) {
							if (periodsDefinedAgeGroupsOnly) {
								for (String graphAgeGroup : graphAgeGroups) {
									if ((!ageGroups.contains(graphAgeGroup)) && (exportKey.contains("," + graphAgeGroup.split(";")[2] + ","))) {
										writePeriodsRecord = false;
										break;
									}
								}
							}
						}
					}
					if (writePeriodsRecord) {
						String record = patient.ID;
						record += "," + exportKey;
						record += "," + Long.toString(periods.get(key).get("Days"));
						record += "," + Long.toString(periods.get(key).get("Events"));
						Jerboa.getOutputManager().writeln(periodsFileName, record, true);
					}
				}
			}
		}

		return patient;
	}


	/**
	 * Outputs the results in a desired format.
	 */
	@Override
	public void outputResults() {
		progress = new Progress();

		//start counter
		Timer timer = new Timer();
		timer.start();

		progress.init(allEventTypes.size() * allPeriodKeys.size(), "Computing Incidence Rates");

		Collections.sort(allEventTypes);
		if (populationSubgroupList != null && populationSubgroupList.size() > 0)
			Collections.sort(populationSubgroupList);

		int maleSubjects;
		int femaleSubjects;
		int maleEvents;
		int femaleEvents;
		long maleDays;
		long femaleDays;
		double maleIncidence;
		double femaleIncidence;
		double overallIncidence;

		Jerboa.getOutputManager().addFile(outputFileName);
		String record;
		record = "Database";
		record += "," + "EventType";
		record += "," + "Year";
		record += "," + "Month";
		record += "," + "AgeGroup";
		record += "," + "MaleSubjects";
		record += "," + "FemaleSubjects";
		record += "," + "TotalSubjects";
		record += "," + "MaleYears";
		record += "," + "FemaleYears";
		record += "," + "TotalYears";
		record += "," + "MaleEvents";
		record += "," + "FemaleEvents";
		record += "," + "TotalEvents";
		record += "," + "MaleIncidence (/ " + Integer.toString(perNYears) + ")";
		record += "," + "FemaleIncidence (/ " + Integer.toString(perNYears) + ")";
		record += "," + "TotalIncidence (/ " + Integer.toString(perNYears) + ")";
		Jerboa.getOutputManager().writeln(outputFileName, record, true);

		String overallIncidenceFileName = StringUtilities.addSuffixToFileName(outputFileName, "_OverallIncidence", false);
			if (outputOverallIncidence){
			Jerboa.getOutputManager().addFile(overallIncidenceFileName);
			String incidenceRecord;

			incidenceRecord = "Database";
			incidenceRecord += "," + "EventType";
			incidenceRecord += "," + "PersonYears";
			incidenceRecord += "," + "Events";
			incidenceRecord += "," + "Incidence Rate";
			Jerboa.getOutputManager().writeln(overallIncidenceFileName, incidenceRecord, true);
		}

		String analysisFileName = StringUtilities.addSuffixToFileName(outputFileName, "_Analysis", false);
		Jerboa.getOutputManager().addFile(analysisFileName);
		String analysisRecord = "Database";
		analysisRecord += "," + "EventType";
		analysisRecord += "," + "Year";
		analysisRecord += "," + "Month";
		analysisRecord += "," + "Gender";
		analysisRecord += "," + "AgeGroup";
		analysisRecord += (aggregations.getAggregationHeader().equals("") ? "" : "," + aggregations.getAggregationHeader()); //"," + "Special";
		analysisRecord += "," + "Subjects";
		analysisRecord += "," + "PersonYears";
		analysisRecord += "," + "Events";
		Jerboa.getOutputManager().writeln(analysisFileName, analysisRecord, true);

		for (String eventType : allEventTypes) {
			long eventMaleDays = 0;
			long eventFemaleDays = 0;

			List<String> sortedPeriodKeys = new ArrayList<String>(allPeriodKeys.keySet());
			Collections.sort(sortedPeriodKeys);
			for (String year : sortedPeriodKeys) {

				List<String> sortedMonths = new ArrayList<String>(allPeriodKeys.get(year));
				Collections.sort(sortedMonths, new StringUtilities.StringComparatorWithLength());
				for (String month : sortedMonths) {

					List<AgeGroup> sortedAgeGroups = ageGroupDefinition.getAgeGroups();
					Collections.sort(sortedAgeGroups);
					for (AgeGroup ageGroup : sortedAgeGroups) {
						String ageGroupLabel = ageGroup.getLabel();

						List<String> sortedAggregationLevels = new ArrayList<String>(aggregationLevelsFound);
						Collections.sort(sortedAggregationLevels);
						for (String aggregationLevel : sortedAggregationLevels) {

							// Get subjects
							// subjectsBag: Event, Year, Month, AgeGroup, Gender
							maleSubjects = getSubjects(eventType + "_" + year + "_" + month + "_" + ageGroupLabel + "_" + aggregationLevel + "_" + "M");
							femaleSubjects = getSubjects(eventType + "_" + year + "_" + month + "_" + ageGroupLabel + "_" + aggregationLevel + "_" + "F");

							if ((maleSubjects + femaleSubjects) >= minSubjectsPerRow) {
								// Get events
								// eventsBag: Event, Year, Month, AgeGroup, Gender
								maleEvents = getEvents(eventType + "_" + year + "_" + month + "_" + ageGroupLabel + "_" + aggregationLevel + "_" + "M");
								femaleEvents = getEvents(eventType + "_" + year + "_" + month + "_" + ageGroupLabel + "_" + aggregationLevel + "_" + "F");

								// Get days
								// daysBag: Year, Month, AgeGroup, Gender, Days
								maleDays = getDays(eventType + "_" + year + "_" + month + "_" + ageGroupLabel + "_" + aggregationLevel + "_" + "M");
								femaleDays = getDays(eventType + "_" + year + "_" + month + "_" + ageGroupLabel + "_" + aggregationLevel + "_" + "F");
								if (year.equals("ALL") && month.equals("ALL")) {
									eventMaleDays += maleDays;
									eventFemaleDays += femaleDays;
								}

								// Compute incidence rate
								maleIncidence = maleEvents / (maleDays / (perNYears * DateUtilities.daysPerYear));
								femaleIncidence = femaleEvents / (femaleDays / (perNYears * DateUtilities.daysPerYear));
								overallIncidence = (maleEvents + femaleEvents) / ((maleDays + femaleDays) / (perNYears * DateUtilities.daysPerYear));


								record =        Parameters.DATABASE_NAME;
								record += "," + eventType;
								record += "," + year;
								record += "," + month;
								record += "," + ageGroupLabel;
								record += "," + Integer.toString(maleSubjects);
								record += "," + Integer.toString(femaleSubjects);
								record += "," + Integer.toString(maleSubjects + femaleSubjects);
								record += "," + Double.toString(maleDays / DateUtilities.daysPerYear);
								record += "," + Double.toString(femaleDays / DateUtilities.daysPerYear);
								record += "," + Double.toString((maleDays / DateUtilities.daysPerYear) + (femaleDays / DateUtilities.daysPerYear));
								record += "," + Integer.toString(maleEvents);
								record += "," + Integer.toString(femaleEvents);
								record += "," + Integer.toString(maleEvents + femaleEvents);
								record += "," + Double.toString(maleIncidence);
								record += "," + Double.toString(femaleIncidence);
								record += "," + Double.toString(overallIncidence);
								Jerboa.getOutputManager().writeln(outputFileName, record, true);


								if (femaleDays > 0) {
									analysisRecord  =       Parameters.DATABASE_NAME;
									analysisRecord += "," + eventType;
									analysisRecord += "," + year;
									analysisRecord += "," + month;
									analysisRecord += "," + "F";
									analysisRecord += "," + ageGroupLabel;
									analysisRecord += (aggregations.getAggregationHeader().equals("") ? "" : "," + aggregationLevel);
									analysisRecord += "," + Integer.toString(femaleSubjects);
									analysisRecord += "," + Double.toString(femaleDays / DateUtilities.daysPerYear);
									analysisRecord += "," + Integer.toString(femaleEvents);
									Jerboa.getOutputManager().writeln(analysisFileName, analysisRecord, true);
								}

								if (maleDays > 0) {
									analysisRecord  =       Parameters.DATABASE_NAME;
									analysisRecord += "," + eventType;
									analysisRecord += "," + year;
									analysisRecord += "," + month;
									analysisRecord += "," + "M";
									analysisRecord += "," + ageGroupLabel;
									analysisRecord += (aggregations.getAggregationHeader().equals("") ? "" : "," + aggregationLevel);
									analysisRecord += "," + Integer.toString(maleSubjects);
									analysisRecord += "," + Double.toString(maleDays / DateUtilities.daysPerYear);
									analysisRecord += "," + Integer.toString(maleEvents);
									Jerboa.getOutputManager().writeln(analysisFileName, analysisRecord, true);
								}
							}
						}
					}

					if (referencePopulationDistribution != null && populationSubgroupList != null) {
						for (String populationSubgroup : populationSubgroupList) {
							record =        Parameters.DATABASE_NAME;
							record += "," + eventType;
							record += "," + year;
							record += "," + month;
							record += "," + populationSubgroup;
							record += "," + "";
							record += "," + "";
							record += "," + "";
							record += "," + "";
							record += "," + "";
							record += "," + "";
							record += "," + "";
							record += "," + "";
							record += "," + "";
							record += "," + "";
							record += "," + "";
							record += "," + computeStdIncidenceRate(populationSubgroupsDefinition.get(populationSubgroup), eventType, year, month);
							Jerboa.getOutputManager().writeln(outputFileName, record, true);
						}
					}

				}

				//refresh loop variable and progress
				progress.update();
			}

			//int totalEvents = (int) eventsBag.getCount(new ExtendedMultiKey(eventType, Wildcard.STRING(), Wildcard.STRING(), "A", Wildcard.INTEGER()));
			//int totalDays = (int) daysBag.getCount(new ExtendedMultiKey(eventType, Wildcard.STRING(), Wildcard.STRING(), "NO_AGEGROUP", Wildcard.INTEGER()));
			int totalEvents = getEvents(eventType + "_" + "M") + getEvents(eventType + "_" + "F");
			long totalDays = eventMaleDays + eventFemaleDays;
			double totalIncidence = (double)totalEvents / ((double)totalDays / (perNYears * DateUtilities.daysPerYear));

			if (outputOverallIncidence) {
				String incidenceRecord  =       Parameters.DATABASE_NAME;
				incidenceRecord += "," + eventType;
				incidenceRecord += "," + Double.toString(totalDays / DateUtilities.daysPerYear);
				incidenceRecord += "," + Integer.toString(totalEvents);
				incidenceRecord += "," + Double.toString(totalIncidence);
				Jerboa.getOutputManager().writeln(overallIncidenceFileName, incidenceRecord, true);
			}

			//write the patient time before and after first event
			if (outputPatientTime){
				HistogramStats historyStats = historyBag.getHistogramStats(new ExtendedMultiKey (eventType, Wildcard.INTEGER()));
				HistogramStats followUpStats = followUpBag.getHistogramStats(new ExtendedMultiKey (eventType, Wildcard.INTEGER()));
				String timeRecord = eventType;
				if (historyStats != null) {
					timeRecord += "," + StringUtilities.format(historyStats.getCount());
					timeRecord += "," + StringUtilities.format(historyStats.getMean()/365.25);
					timeRecord += "," + StringUtilities.format(historyStats.getStdDev()/365.25);
					timeRecord += "," + StringUtilities.format(historyStats.getMin()/365.25);
					timeRecord += "," + StringUtilities.format(historyStats.getMax()/365.25);
				}
				else {
					timeRecord += ",,,,,";
				}
				if (followUpStats != null) {
					timeRecord += "," + StringUtilities.format(followUpStats.getMean()/365.25);
					timeRecord += "," + StringUtilities.format(followUpStats.getStdDev()/365.25);
					timeRecord += "," + StringUtilities.format(followUpStats.getMin()/365.25);
					timeRecord += "," + StringUtilities.format(followUpStats.getMax()/365.25);
					timeRecord += "," + StringUtilities.format(followUpStats.getSum()/365.25);
				}
				else {
					timeRecord += ",,,,,";
				}
				Jerboa.getOutputManager().writeln(patientTimeFileName,timeRecord,true);
			}
		}

		// Close all output files
		if (intermediateFiles) {
			if (!acceptedEventsFileName.equals("")) Jerboa.getOutputManager().closeFile(acceptedEventsFileName);
			if (!rejectedEventsFileName.equals("")) Jerboa.getOutputManager().closeFile(rejectedEventsFileName);
			if (!countLogFileName.equals(""))       Jerboa.getOutputManager().closeFile(countLogFileName);
			if (!periodsFileName.equals(""))        Jerboa.getOutputManager().closeFile(periodsFileName);
		}
		if (outputOverallIncidence) Jerboa.getOutputManager().closeFile(overallIncidenceFileName);
		if (outputPatientTime) Jerboa.getOutputManager().closeFile(patientTimeFileName);
		Jerboa.getOutputManager().closeFile(outputFileName);
		Jerboa.getOutputManager().closeFile(analysisFileName);

		// Write information to the attrition file
		if ((!Jerboa.unitTest) && attritionFile) {
			Jerboa.getOutputManager().writeln(attritionFileName, title + " (IncidenceRate),", true);
			for (String eventType : eventsOfInterest) {
				Jerboa.getOutputManager().writeln(attritionFileName, "Patients censored for " + eventType + " before cohort start," + censoredCount.get(eventType),true);
				Jerboa.getOutputManager().writeln(attritionFileName, "Patients contributing time for " + eventType+ "," + (originalCount - censoredCount.get(eventType)),true);
				Jerboa.getOutputManager().writeln(attritionFileName, "Patients incident for " + eventType+ "," + incidentCount.get(eventType),true);
			}
			Jerboa.getOutputManager().writeln(attritionFileName, " ,",true);
			Jerboa.getOutputManager().flush(attritionFileName);
		}

		// Make sure the progress bar is closed
		progress.close();

		// Display execution timers
		timer.stop();
		timer.displayTotal("Incidence calculation done in");
	}


	@Override
	public void displayGraphs() {

		if (debug) {
			incidenceBagsDebugFileName = StringUtilities.addSuffixToFileName(intermediateFileName, "_debugBags.csv", true);
			Jerboa.getOutputManager().addFile(incidenceBagsDebugFileName, 100);
			Jerboa.getOutputManager().writeln(incidenceBagsDebugFileName, "bag,eventType,year,ageGroup,gender,incidence", true);

		}

		/******** PREPARE DATA FOR PLOTS *******/

		// Create graph bags
		int maleSubjects;
		int femaleSubjects;
		int maleEvents;
		int femaleEvents;
		long maleDays;
		long femaleDays;
		double maleIncidence;
		double femaleIncidence;
		double overallIncidence;


		for (String eventType : allEventTypes) {

 			for (String year : allPeriodKeys.keySet()) {
				if (!year.equals("ALL")) {

					// Get subjects
					// subjectsBag: Event, Year, AgeGroup, Gender
					maleSubjects = getSubjects(eventType + "_" + year + "_" + "ALL" + "_" + "ALL" + "_" + aggregations.getEmptyAggregationHeader() + "_" + "M");
					femaleSubjects = getSubjects(eventType + "_" + year + "_" + "ALL" + "_" + "ALL" + "_" + aggregations.getEmptyAggregationHeader() + "_" + "F");

					if ((maleSubjects + femaleSubjects) >= minSubjectsPerRow) {

						// Get events
						// eventsBag: Event, Year, AgeGroup, Gender
						maleEvents = getEvents(eventType + "_" + year + "_" + "ALL" + "_" + "ALL" + "_" + aggregations.getEmptyAggregationHeader() + "_" + "M");
						femaleEvents = getEvents(eventType + "_" + year + "_" + "ALL" + "_" + "ALL" + "_" + aggregations.getEmptyAggregationHeader() + "_" + "F");

						// Get days
						// daysBag: Year, AgeGroup, Gender, Days
						maleDays = getDays(eventType + "_" + year + "_" + "ALL" + "_" + "ALL" + "_" + aggregations.getEmptyAggregationHeader() + "_" + "M");
						femaleDays = getDays(eventType + "_" + year + "_" + "ALL" + "_" + "ALL" + "_" + aggregations.getEmptyAggregationHeader() + "_" + "F");

						// Compute incidence rate
						maleIncidence = (double)maleEvents / ((double)maleDays / (perNYears * DateUtilities.daysPerYear));
						femaleIncidence = (double)femaleEvents / ((double)femaleDays / (perNYears * DateUtilities.daysPerYear));
						overallIncidence = (double)(maleEvents + femaleEvents) / (double)((maleDays + femaleDays) / (perNYears * DateUtilities.daysPerYear));

						// incidence map: key=(Event, AgeGroup), value=Incidence
						if (!Double.isNaN(maleIncidence)) {
							// incidence overall per gender map: key=(Event, Year), value=Incidence
							incidenceOverallYearMap.put(new ExtendedMultiKey(eventType, year), overallIncidence);
									if (debug) {
										Jerboa.getOutputManager().writeln(incidenceBagsDebugFileName, "incidenceOverallGenderBag," + eventType + ","+year+",,," + Double.toString(overallIncidence), true);
									}
						}
						// incidence map: key=(Event, AgeGroup), value=Incidence
						if (!Double.isNaN(maleIncidence)) {
							// incidence overall per gender map: key=(Event, Year, Gender), value=Incidence
							incidenceOverallYearGenderMap.put(new ExtendedMultiKey(eventType, year, (int) DataDefinition.MALE_GENDER), maleIncidence);
									if (debug) {
										Jerboa.getOutputManager().writeln(incidenceBagsDebugFileName, "incidenceOverallGenderBag," + eventType + ","+year+",,," + Byte.toString(DataDefinition.MALE_GENDER) + "," + Double.toString(maleIncidence), true);
									}
						}
						if (!Double.isNaN(femaleIncidence)) {
							// incidence overall per gender map: key=(Event, Year, Gender), value=Incidence
							incidenceOverallYearGenderMap.put(new ExtendedMultiKey(eventType, year, (int) DataDefinition.FEMALE_GENDER), femaleIncidence);
									if (debug) {
										Jerboa.getOutputManager().writeln(incidenceBagsDebugFileName, "incidenceOverallGenderBag," + eventType + ","+year+",,," + Byte.toString(DataDefinition.FEMALE_GENDER) + "," + Double.toString(femaleIncidence), true);
									}
						}
					}
				}
			}

			for (AgeGroup ageGroup : graphAgeGroupDefinition.getAgeGroups()) {
				String ageGroupLabel = ageGroup.getLabel();

				// Get subjects
				// subjectsBag: Event, Year, AgeGroup, Gender
				maleSubjects = getSubjects(eventType + "_" + "ALL" + "_" + "ALL" + "_" + ageGroupLabel + "_" + aggregations.getEmptyAggregationHeader() + "_" + "M");
				femaleSubjects = getSubjects(eventType + "_" + "ALL" + "_" + "ALL" + "_" + ageGroupLabel + "_" + aggregations.getEmptyAggregationHeader() + "_" + "F");

				if ((maleSubjects + femaleSubjects) >= minSubjectsPerRow) {

					// Get events
					// eventsBag: Event, Year, AgeGroup, Gender
					//maleEvents = (int) eventsBag.getCount(new ExtendedMultiKey(eventType, Wildcard.INTEGER(), ageGroupLabel, (int) DataDefinition.MALE_GENDER));
					//femaleEvents = (int) eventsBag.getCount(new ExtendedMultiKey(eventType, Wildcard.INTEGER(), ageGroupLabel, (int) DataDefinition.FEMALE_GENDER));
					maleEvents = getEvents(eventType + "_" + "ALL" + "_" + "ALL" + "_" + ageGroupLabel + "_" + aggregations.getEmptyAggregationHeader() + "_" + "M");
					femaleEvents = getEvents(eventType + "_" + "ALL" + "_" + "ALL" + "_" + ageGroupLabel + "_" + aggregations.getEmptyAggregationHeader() + "_" + "F");

					// Get days
					// daysBag: Year, AgeGroup, Gender, Days
					//maleDays = (int) daysBag.getCount(new ExtendedMultiKey(eventType, Wildcard.INTEGER(), ageGroupLabel, (int) DataDefinition.MALE_GENDER));
					//femaleDays = (int) daysBag.getCount(new ExtendedMultiKey(eventType, Wildcard.INTEGER(), ageGroupLabel, (int) DataDefinition.FEMALE_GENDER));
					maleDays = getDays(eventType + "_" + "ALL" + "_" + "ALL" + "_" + ageGroupLabel + "_" + aggregations.getEmptyAggregationHeader() + "_" + "M");
					femaleDays = getDays(eventType + "_" + "ALL" + "_" + "ALL" + "_" + ageGroupLabel + "_" + aggregations.getEmptyAggregationHeader() + "_" + "F");

					// Compute incidence rate
					maleIncidence = (double)maleEvents / ((double)maleDays / (perNYears * DateUtilities.daysPerYear));
					femaleIncidence = (double)femaleEvents / ((double)femaleDays / (perNYears * DateUtilities.daysPerYear));
					overallIncidence = (double)(maleEvents + femaleEvents) / ((double)(maleDays + femaleDays) / (perNYears * DateUtilities.daysPerYear));

					// incidence map: key=(Event, AgeGroup), value=Incidence
					if (!Double.isNaN(overallIncidence)) {
						incidenceOverallMap.put(new ExtendedMultiKey(eventType, ageGroupLabel), overallIncidence);
						if (debug) {
							Jerboa.getOutputManager().writeln(incidenceBagsDebugFileName, "incidenceOverallBag," + eventType + ",," + ageGroupLabel + ",," + Double.toString(overallIncidence), true);
						}
					}
					if (!Double.isNaN(maleIncidence)) {
						// incidence overall per gender map: key=(Event, AgeGroup, Gender), value=Incidence
						incidenceOverallGenderMap.put(new ExtendedMultiKey(eventType, ageGroupLabel, (int) DataDefinition.MALE_GENDER), maleIncidence);
						if (debug) {
							Jerboa.getOutputManager().writeln(incidenceBagsDebugFileName, "incidenceOverallGenderBag," + eventType + ",," + ageGroupLabel + "," + Byte.toString(DataDefinition.MALE_GENDER) + "," + Double.toString(maleIncidence), true);
						}
					}
					if (!Double.isNaN(femaleIncidence)) {
						// incidence overall per gender map: key=(Event, AgeGroup, Gender), value=Incidence
						incidenceOverallGenderMap.put(new ExtendedMultiKey(eventType, ageGroupLabel, (int) DataDefinition.FEMALE_GENDER), femaleIncidence);
						if (debug) {
							Jerboa.getOutputManager().writeln(incidenceBagsDebugFileName, "incidenceOverallGenderBag," + eventType + ",," + ageGroupLabel + "," + Byte.toString(DataDefinition.FEMALE_GENDER) + "," + Double.toString(femaleIncidence), true);
						}
					}

				}
			}


			for (String year : allPeriodKeys.keySet()) {
				for (String month : allPeriodKeys.get(year)) {

					for (AgeGroup ageGroup : graphAgeGroupDefinition.getAgeGroups()) {
						String ageGroupLabel = ageGroup.getLabel();

						// Get subjects
						// subjectsBag: Year, AgeGroup, Gender
						maleSubjects = getSubjects(eventType + "_" + year + "_" + month + "_" + ageGroupLabel + "_" + aggregations.getEmptyAggregationHeader() + "_" + "M");
						femaleSubjects = getSubjects(eventType + "_" + year + "_" + month + "_" + ageGroupLabel + "_" + aggregations.getEmptyAggregationHeader() + "_" + "F");

						if ((maleSubjects + femaleSubjects) >= minSubjectsPerRow) {
							// Get events
							// eventsBag: Event, Year, AgeGroup, Gender
							maleEvents = getEvents(eventType + "_" + year + "_" + month + "_" + ageGroupLabel + "_" + aggregations.getEmptyAggregationHeader() + "_" + "M");
							femaleEvents = getEvents(eventType + "_" + year + "_" + month + "_" + ageGroupLabel + "_" + aggregations.getEmptyAggregationHeader() + "_" + "F");

							// Get days
							// daysBag: Year, AgeGroup, Gender, Days
							maleDays = getDays(eventType + "_" + year + "_" + month + "_" + ageGroupLabel + "_" + aggregations.getEmptyAggregationHeader() + "_" + "M");
							femaleDays = getDays(eventType + "_" + year + "_" + month + "_" + ageGroupLabel + "_" + aggregations.getEmptyAggregationHeader() + "_" + "F");

							// Compute incidence rate
							maleIncidence = (double)maleEvents / ((double)maleDays / (perNYears * DateUtilities.daysPerYear));
							femaleIncidence = (double)femaleEvents / ((double)femaleDays / (perNYears * DateUtilities.daysPerYear));
							overallIncidence = (double)(maleEvents + femaleEvents) / ((double)(maleDays + femaleDays) / (perNYears * DateUtilities.daysPerYear));

							if ((month.equals("ALL")) && (!Double.isNaN(overallIncidence))) {
								// incidence per year map: key=(Event, Period, AgeGroup), value=Incidence
								incidenceMap.put(new ExtendedMultiKey(eventType, year, ageGroupLabel), overallIncidence);
								if (debug) {
										Jerboa.getOutputManager().writeln(incidenceBagsDebugFileName, "incidenceBag," + eventType + "," + year + "," + ageGroupLabel + ",," + Double.toString(overallIncidence), true);
								}
							}
							if ((month.equals("")) && (!Double.isNaN(maleIncidence))) {
								/// incidence per year per gender map: key=(Event, Period, AgeGroup, Gender), value=Incidence
								incidenceGenderMap.put(new ExtendedMultiKey(eventType, year, ageGroupLabel, (int) DataDefinition.MALE_GENDER), maleIncidence);
								if (debug ) {
									Jerboa.getOutputManager().writeln(incidenceBagsDebugFileName, "incidenceGenderBag," + eventType + "," + year + "," + ageGroupLabel + "," + Byte.toString(DataDefinition.MALE_GENDER) + "," + Double.toString(maleIncidence), true);
								}

							}
							if ((month.equals("")) && (!Double.isNaN(femaleIncidence))) {
								// incidence per year per gender map: key=(Event, Period, AgeGroup, Gender), value=Incidence
								incidenceGenderMap.put(new ExtendedMultiKey(eventType, year, ageGroupLabel, (int) DataDefinition.FEMALE_GENDER), femaleIncidence);
								if (debug) {
									Jerboa.getOutputManager().writeln(incidenceBagsDebugFileName, "incidenceGenderBag," + eventType + "," + year + "," + ageGroupLabel + "," + Byte.toString(DataDefinition.FEMALE_GENDER) + "," + Double.toString(femaleIncidence), true);
								}
							}
						}

					}
				}
			}

		}
		if (debug) {
			Jerboa.getOutputManager().closeFile(incidenceBagsDebugFileName);
		}
		/********* CREATE THE ACTUAL PLOTS ***********/

		//Event, AgeGroup
		if ((incidenceOverallMap != null && !incidenceOverallMap.isEmpty()) &&
				(incidenceOverallGenderMap != null && !incidenceOverallGenderMap.isEmpty())){
			Logging.add("Creating incidence graphs...", Logging.HINT);

			Progress progress = new Progress();
			Timer timer = new Timer();

			List<Plot> listPerAgeGroup = new ArrayList<Plot>();
			List<Plot> listPerYear = new ArrayList<Plot>();

			Set<Object> ageGroups;
			Set<Object> years;
			List<Object> values;
			TreeMap<Object, Object> data;
			Iterator<Object> it;

			Set<Object> events = incidenceOverallMap.getKeyValues(0);

			progress.init(events.size()*4 , "Creating incidence rates plots");
			timer.start();

			//OVERALL PLOTS
			for (Object event : events){
				listPerAgeGroup = new ArrayList<Plot>();
				listPerYear = new ArrayList<Plot>();

				//OVERALL PLOTS PER GENDER AND PER AGE GROUP
				//total population
				MultiKeyMap subMap = incidenceOverallMap.getSubMap(0, event);
				if (subMap != null && !subMap.isEmpty()){
					ageGroups = subMap.getKeyValues(1);
					values = subMap.getValues(new ExtendedMultiKey(event, Wildcard.STRING()));
					data = new TreeMap<Object,Object>();
					it = values.iterator();
					for (Object ageGroup : ageGroups)
						data.put(ageGroup, it.next());

					Plot plot = new LinePlotDS.Builder("Incidence "+event+" - Total population")
					.data(data).XLabel("Age group").YLabel("Incidence per "+ perNYears + " patient years")
					.showLegend(false).build();
					listPerAgeGroup.add(plot);
				}

				subMap = incidenceOverallYearMap.getSubMap(0, event);
				if (subMap != null && !subMap.isEmpty()){
					//overall age groups per calendar year - NO BAG CONTAINING THAT DATA
					data = new TreeMap<Object,Object>();
					years = subMap.getKeyValues(1);
					for (Object year : years)
						data.put(year, subMap.getSum(new ExtendedMultiKey(event, year)));

					Plot plot = new LinePlotDS.Builder("Incidence "+event+" - Total population")
					.data(data).XLabel("Calendar year").YLabel("Incidence per "+ perNYears + " patient years")
					.showLegend(false).build();
					listPerYear.add(plot);
				}

				//Event, AgeGroup, Gender
				subMap = incidenceOverallGenderMap.getSubMap(0, event);
				if (subMap != null && !subMap.isEmpty()){
					//females
					MultiKeyMap subSubMap = subMap.getSubMap(2, (int)DataDefinition.FEMALE_GENDER);
					if (subSubMap != null && !subSubMap.isEmpty()){
						ageGroups = subSubMap.getKeyValues(1);
						values = subMap.getValues(new ExtendedMultiKey(event , Wildcard.STRING(), (int)DataDefinition.FEMALE_GENDER));
						data = new TreeMap<Object,Object>();
						it = values.iterator();
						for (Object ageGroup : ageGroups)
							data.put(ageGroup, it.next());

						Plot plot = new LinePlotDS.Builder("Incidence "+event+" - Female population")
						.data(data).XLabel("Age group").YLabel("Incidence per "+ perNYears + " patient years")
						.showLegend(false).build();
						listPerAgeGroup.add(plot);
					}

					//males
					subSubMap = subMap.getSubMap(2, (int)DataDefinition.MALE_GENDER);
					if (subSubMap != null && !subSubMap.isEmpty()){
						ageGroups = subSubMap.getKeyValues(1);
						values = subMap.getValues(new ExtendedMultiKey(event , Wildcard.STRING(), (int)DataDefinition.MALE_GENDER));
						data = new TreeMap<Object,Object>();
						it = values.iterator();
						for (Object ageGroup : ageGroups)
							data.put(ageGroup, it.next());

						Plot plot = new LinePlotDS.Builder("Incidence "+event+" - Male population")
						.data(data).XLabel("Age group").YLabel("Incidence per "+ perNYears + " patient years")
						.showLegend(false).build();
						listPerAgeGroup.add(plot);
					}
				}

				//OVERALL PLOTS PER GENDER AND PER CALENDAR YEAR
				subMap = incidenceOverallYearGenderMap.getSubMap(0, event); //Event, Year, Gender
				if (subMap != null && !subMap.isEmpty()){

					//females
					MultiKeyMap subSubMap = subMap.getSubMap(2, (int)DataDefinition.FEMALE_GENDER);
					if (subSubMap != null && !subSubMap.isEmpty()){
						years = subSubMap.getKeyValues(1);
						values = subMap.getValues(new ExtendedMultiKey(event , Wildcard.STRING(), (int)DataDefinition.FEMALE_GENDER));
						data = new TreeMap<Object,Object>();
						it = values.iterator();
						for (Object year : years)
							data.put(year, it.next());

						Plot plot = new LinePlotDS.Builder("Incidence "+event+" - Female population")
						.data(data).XLabel("Calendar year").YLabel("Incidence per "+ perNYears + " patient years")
						.showLegend(false).build();
						listPerYear.add(plot);
					}

					//males
					subSubMap = subMap.getSubMap(2, (int)DataDefinition.MALE_GENDER);
					if (subSubMap != null && !subSubMap.isEmpty()){
						years = subSubMap.getKeyValues(1);
						values = subMap.getValues(new ExtendedMultiKey(event , Wildcard.STRING(), (int)DataDefinition.MALE_GENDER));
						data = new TreeMap<Object,Object>();
						it = values.iterator();
						for (Object year : years)
							data.put(year, it.next());

						Plot plot = new LinePlotDS.Builder("Incidence "+event+" - Male population")
						.data(data).XLabel("Calendar year").YLabel("Incidence per "+ perNYears + " patient years")
						.showLegend(false).build();
						listPerYear.add(plot);
					}
				}

				Graphs.addPlots(this.title,"Incidence "+event+" per year (all age groups)", listPerYear);
				progress.update();
				Graphs.addPlots(this.title,"Incidence "+event+" per age group (all years)", listPerAgeGroup);
				progress.update();
			}

			//PLOTS PER AGE GROUP/YEAR
			if (incidenceMap != null){ //Event, Year,  AgeGroup, Incidence

				events = incidenceMap.getKeyValues(0);
				progress.init(events.size(), "Creating plots for incidence rates per year");

				//per calendar year for all age groups
				for (Object event : events){
					listPerYear = new ArrayList<Plot>();
					MultiKeyMap subMap = incidenceMap.getSubMap(0, event);
					if (subMap != null && !subMap.isEmpty()){
						years = subMap.getKeyValues(1);
						for (Object year : years){
							MultiKeyMap subSubMap = subMap.getSubMap(1, year);
							if (subSubMap != null && !subSubMap.isEmpty()){
								ageGroups = subSubMap.getKeyValues(2);
								values = subMap.getValues(new ExtendedMultiKey(event, year, Wildcard.STRING()));
								data = new TreeMap<Object,Object>();
								it = values.iterator();
								for (Object ageGroup : ageGroups)
									data.put(ageGroup, it.next());

								Plot plot = new LinePlotDS.Builder("Incidence "+event+" in year "+year)
								.data(data).XLabel("Age group").YLabel("Incidence per "+perNYears + " patient years")
								.showLegend(false).build();

								listPerYear.add(plot);
							}
						}

						//per age group for all calendar years
						listPerAgeGroup = new ArrayList<Plot>();
						ageGroups = subMap.getKeyValues(2);
						for (Object ageGroup : ageGroups){
							MultiKeyMap subSubMap = subMap.getSubMap(2, ageGroup);
							if (subSubMap != null && !subSubMap.isEmpty()){
								years = subSubMap.getKeyValues(1);
								values = subMap.getValues(new ExtendedMultiKey(event, Wildcard.STRING(), ageGroup));
								data = new TreeMap<Object,Object>();
								it = values.iterator();
								for (Object year : years)
									data.put(year, it.next());

								Plot plot = new LinePlotDS.Builder("Incidence "+event+" in age group "+ageGroup)
								.data(data).XLabel("Calendar year").YLabel("Incidence per "+perNYears + " patient years")
								.showLegend(false).build();

								listPerAgeGroup.add(plot);
							}
						}

						Graphs.addPlots(this.title,"Incidence "+event+" per year", listPerYear);
						progress.update();
						Graphs.addPlots(this.title,"Incidence "+event+" per age group", listPerAgeGroup);
						progress.update();
					}
				}

			}

			//display execution timers
			progress.close();
			timer.stop();
			timer.displayTotal("Incidence rate graphs done in");
		}else{
			Logging.add("No data to plot.", Logging.HINT);
		}


		//TODO - decide if still needed

		//BAGS - event counts

		//create plots for events per type
		//		if (bagList != null && bagList.size() > 0){			Logging.add("Creating graphs..", Logging.HINT);
		//
		//			list = new ArrayList<Plot>();
		//
		//			//per type //event, year, ageGroup, gender
		//			if (bagList.get("Events") != null){
		//				Set<String> codes = bagList.get("Events").getKeyValues(0);
		//				MultiKeyBag dataBag = new MultiKeyBag();
		//				if (codes != null && codes.size() > 0){
		//					for (String code : codes){
		//						SortedSet<String> years = bagList.get("Events").getSubBag(0, code).getKeyValues(1);
		//						for (String year : years){
		//							dataBag.add(new ExtendedMultiKey(code, year),
		//									(int)bagList.get("Events").getSum(new ExtendedMultiKey(code, Integer.valueOf(year), "*", "*")));
		//						}
		//					}
		//				}
		//				HorizontalBarPlot plot =
		//						(new HorizontalBarPlot.Builder("Event Count")
		//						.dataBag(dataBag)
		//						.XLabel("Type")
		//						.YLabel("Count")
		//						.aggregate(true)
		//						.layered(false).build());
		//				list.add(plot);
		//				charts.put(plot.getPlotTitle(), plot.getChart());
		//				plots.put("Event Count", list);
		//
		//				//per type per year  //event, year, ageGroup, gender
		//				if (codes != null && codes.size() > 0){
		//					for (String code : codes){
		//						//get different year values for this code
		//						SortedSet<String> years = bagList.get("Events").getSubBag(0, code).getKeyValues(1);
		//						//reset list of bags
		//						TreeMap<String, MultiKeyBag> dataBags = new TreeMap<String, MultiKeyBag>();
		//						list = new ArrayList<Plot>();
		//						for (String year : years){
		//							//create a sub bag with info for only this code and this calendar year
		//							MultiKeyBag subBag = bagList.get("Events").getSubBag(0, code.toString()).getSubBag(1, Integer.valueOf(year));
		//							//and add it to plot list
		//							dataBags.put("Event count "+code+" in year "+year , subBag);
		//							plot =
		//									(new HorizontalBarPlot.Builder("Count for event "+code+" in year "+year)
		//									.dataBags(dataBags)
		//									.onValue(code)
		//									.XLabel("Age group")
		//									.YLabel("Count")
		//									.aggregate(false)
		//									.byGender(true)
		//									.layered(false)
		//									.forceVertical(true)
		//									.showLegend(true).build());
		//							list.add(plot);
		//							charts.put(plot.getPlotTitle(), plot.getChart());
		//							plots.put("Event count "+code, list);
		//						}
		//					}
		//				}
		//			}
		//		}
	}


	private void count(Patient patient, int start, int end, int period, int nextPeriod, String currentYear, String currentMonth, Byte division) {
		// Count patient time per period
		String gender = Patient.convertGender(patient.gender);

		//TODO: double check with Mees
		int birthday  = patient.getBirthdayInYear(getYearFromPeriod(period, division));
		int periodStart =  Math.max(start, periodToDays(period, division));
		int periodEnd   = Math.min(end, periodToDays(nextPeriod, division));

		Set<String> eventTypesFound = new HashSet<String>();

		if (periodEnd > periodStart) {

			List<Event> finishedEvents = new ArrayList<Event>();

			// Birthday is inside period
			if ((birthday >= periodStart) && (birthday < periodEnd)) {

				for (Event event : uniqueEvents.get(division)) {

					// Stop if event is a after period
					if (event.getDate() >= periodEnd) {
						break;
					}

					// Event in history
					if (event.getDate() < patient.startDate) {
						if (censorOnEvent) {
							censored.get(division).add(event.getType());
							censoredCount.put(event.getType(), censoredCount.get(event.getType()) + 1);
						}

						// Mark event to remove it from the list
						finishedEvents.add(event);
					}
					else {
						if (!patientCounted.get(event.getType())) {
							incidentCount.put(event.getType(), incidentCount.get(event.getType()) + 1);
							patientCounted.put(event.getType(), true);
						}
						if (eventsFound.add(event)) {
							AddEvents(event.getType() + "_" + patient.getGender(), 1);
						}

						// Event is before birthday
						if ((event.getDate() >= periodStart) && (event.getDate() < birthday)) {

							// Register event types found
							eventTypesFound.add(event.getType());

							List<String> aggregationLevels = aggregations.getAggregationLevels(patient, event);

							// Count per age group (including ALL)
							if ((censorOnEvent && ((event.getDate() + 1 - periodStart) > 0)) || ((!censorOnEvent) && ((birthday - periodStart) > 0))) {
								List<AgeGroup> ageGroupsBefore = allAgeGroupDefinition.getAgeGroups(patient, getAgeDate(patient, birthday - 1));
								for (AgeGroup ageGroupBefore : ageGroupsBefore) {
									for (String aggregationLevel : aggregationLevels) {

										String key = event.getType() + "_" + currentYear + "_" + currentMonth + "_" + ageGroupBefore.getLabel() + "_" + aggregationLevel + "_" + gender;
										AddSubjects(key, 1);
										AddEvents(key, 1);

										if (censorOnEvent) {
											if (!censored.get(division).contains(event.getType())) {
												AddDays(key, event.getDate() + 1 - periodStart);
											}
										}
										else {
											AddDays(key, birthday - periodStart);
										}

										// Count overall
										key = event.getType() + "_" + "ALL" + "_" + currentMonth + "_" + ageGroupBefore.getLabel() + "_" + aggregationLevel + "_" + gender;
										AddSubjects(key, 1);
										AddEvents(key, 1);

										if (censorOnEvent) {
											if (!censored.get(division).contains(event.getType())) {
												AddDays(key, event.getDate() + 1 - periodStart);
											}
										}
										else {
											AddDays(key, birthday - periodStart);
										}
									}
								}
							}

							if (censorOnEvent) {
								censored.get(division).add(event.getType());
							}

							if ((periodEnd - birthday) > 0) {
								List<AgeGroup> ageGroupsAfter = allAgeGroupDefinition.getAgeGroups(patient, getAgeDate(patient, birthday + 1));
								for (AgeGroup ageGroupAfter : ageGroupsAfter) {
									for (String aggregationLevel : aggregationLevels) {

										String key = event.getType() + "_" + currentYear + "_" + currentMonth + "_" + ageGroupAfter.getLabel() + "_" + aggregationLevel + "_" + gender;
										AddSubjects(key, 1);

										if (!censored.get(division).contains(event.getType())) {
											AddDays(key, periodEnd - birthday);
										}

										// Count overall
										key = event.getType() + "_" + "ALL" + "_" + currentMonth + "_" + ageGroupAfter.getLabel() + "_" + aggregationLevel + "_" + gender;
										AddSubjects(key, 1);

										if (!censored.get(division).contains(event.getType())) {
											AddDays(key, periodEnd - birthday);
										}
									}
								}
							}

							// Mark event to remove it from the list
							finishedEvents.add(event);
						}
						// Event is after birthday
						else if ((event.getDate() >= birthday) && (event.getDate() < periodEnd)) {

							// Register event types found
							eventTypesFound.add(event.getType());

							List<String> aggregationLevels = aggregations.getAggregationLevels(patient, event);

							// Count per age group (including ALL)
							if ((birthday - periodStart) > 0) {
								List<AgeGroup> ageGroupsBefore = allAgeGroupDefinition.getAgeGroups(patient, getAgeDate(patient, birthday - 1));
								for (AgeGroup ageGroupBefore : ageGroupsBefore) {
									for (String aggregationLevel : aggregationLevels) {

										String key = event.getType() + "_" + currentYear + "_" + currentMonth + "_" + ageGroupBefore.getLabel() + "_" + aggregationLevel + "_" + gender;
										AddSubjects(key, 1);

										if (!censored.get(division).contains(event.getType())) {
											AddDays(key, birthday - periodStart);
										}

										// Count overall
										key = event.getType() + "_" + "ALL" + "_" + currentMonth + "_" + ageGroupBefore.getLabel() + "_" + aggregationLevel + "_" + gender;
										AddSubjects(key, 1);

										if (!censored.get(division).contains(event.getType())) {
											AddDays(key, birthday - periodStart);
										}
									}
								}
							}

							if ((censorOnEvent && ((event.getDate() + 1 - birthday) > 0)) || ((!censorOnEvent) && ((periodEnd - birthday) > 0))) {
								List<AgeGroup> ageGroupsAfter = allAgeGroupDefinition.getAgeGroups(patient, getAgeDate(patient, birthday + 1));
								for (AgeGroup ageGroupAfter : ageGroupsAfter) {
									for (String aggregationLevel : aggregationLevels) {

										String key = event.getType() + "_" + currentYear + "_" + currentMonth + "_" + ageGroupAfter.getLabel() + "_" + aggregationLevel + "_" + gender;
										AddSubjects(key, 1);
										AddEvents(key, 1);

										if (censorOnEvent) {
											if (!censored.get(division).contains(event.getType())) {
												AddDays(key, event.getDate() + 1 - birthday);
											}
										}
										else {
											AddDays(key, periodEnd - birthday);
										}

										// Count overall
										key = event.getType() + "_" + "ALL" + "_" + currentMonth + "_" + ageGroupAfter.getLabel() + "_" + aggregationLevel + "_" + gender;
										AddSubjects(key, 1);
										AddEvents(key, 1);

										if (censorOnEvent) {
											if (!censored.get(division).contains(event.getType())) {
												AddDays(key, event.getDate() + 1 - birthday);
											}
										}
										else {
											AddDays(key, periodEnd - birthday);
										}
									}
								}
							}

							if (censorOnEvent) {
								censored.get(division).add(event.getType());
							}

							// Mark event to remove it from the list
							finishedEvents.add(event);
						}
					}

					if (historyFollowupEventTypes.add(event.getType()) && outputPatientTime) {
						//save patient time variables
						historyBag.add(new ExtendedMultiKey(event.getType(),patient.getPatientTimeFromCohortStartInDays(event.getDate())));
						followUpBag.add(new ExtendedMultiKey(event.getType(), patient.getPatientTimeUntilCohortEndInDays(event.getDate())));
					}
				}

				// Add patient time for all event types not encountered
				for (String eventType : allEventTypes) {

					if (!eventTypesFound.contains(eventType) && (!censored.get(division).contains(eventType))) {

						List<String> aggregationLevels = aggregations.getAggregationLevels(patient, null);

						if ((birthday - periodStart) > 0) {
							List<AgeGroup> ageGroupsBefore = allAgeGroupDefinition.getAgeGroups(patient, getAgeDate(patient, birthday - 1));
							for (AgeGroup ageGroupBefore : ageGroupsBefore) {
								for (String aggregationLevel : aggregationLevels) {

									String key = eventType + "_" + currentYear + "_" + currentMonth + "_" + ageGroupBefore.getLabel() + "_" + aggregationLevel + "_" + gender;
									AddSubjects(key, 1);
									AddDays(key, birthday - periodStart);

									// Count overall
									key = eventType + "_" + "ALL" + "_" + currentMonth + "_" + ageGroupBefore.getLabel() + "_" + aggregationLevel + "_" + gender;
									AddSubjects(key, 1);
									AddDays(key, birthday - periodStart);
								}
							}
						}
						if ((periodEnd - birthday) > 0) {
							List<AgeGroup> ageGroupsAfter = allAgeGroupDefinition.getAgeGroups(patient, getAgeDate(patient, birthday + 1));
							for (AgeGroup ageGroupAfter : ageGroupsAfter) {
								for (String aggregationLevel : aggregationLevels) {

									String key = eventType + "_" + currentYear + "_" + currentMonth + "_" + ageGroupAfter.getLabel() + "_" + aggregationLevel + "_" + gender;
									AddSubjects(key, 1);
									AddDays(key, periodEnd - birthday);

									// Count overall
									key = eventType + "_" + "ALL" + "_" + currentMonth + "_" + ageGroupAfter.getLabel() + "_" + aggregationLevel + "_" + gender;
									AddSubjects(key, 1);
									AddDays(key, periodEnd - birthday);
								}
							}
						}
					}
				}
			}
			// Birthday is outside period
			else {

				for (Event event : uniqueEvents.get(division)) {

					// Stop if event is a after period
					if (event.getDate() >= periodEnd) {
						break;
					}

					// Event in history
					if (event.getDate() < patient.startDate) {
						if (censorOnEvent) {
							censored.get(division).add(event.getType());
							censoredCount.put(event.getType(), censoredCount.get(event.getType()) + 1);
						}

						// Mark event to remove it from the list
						finishedEvents.add(event);
					}
					else {
						if (!patientCounted.get(event.getType())) {
							incidentCount.put(event.getType(), incidentCount.get(event.getType()) + 1);
							patientCounted.put(event.getType(), true);
						}
						if (eventsFound.add(event)) {
							AddEvents(event.getType() + "_" + patient.getGender(), 1);
						}

						// Event is inside period
						if ((event.getDate() >= periodStart) && (event.getDate() < periodEnd)) {

							// Register event types found
							eventTypesFound.add(event.getType());

							List<String> aggregationLevels = aggregations.getAggregationLevels(patient, event);

							if ((censorOnEvent && ((event.getDate() + 1 - periodStart) > 0)) || ((!censorOnEvent) && ((periodEnd - periodStart) > 0))) {
								for (AgeGroup ageGroup : allAgeGroupDefinition.getAgeGroups(patient, getAgeDate(patient, periodStart + 1))) {
									for (String aggregationLevel : aggregationLevels) {

										String key = event.getType() + "_" + currentYear + "_" + currentMonth + "_" + ageGroup.getLabel() + "_" + aggregationLevel + "_" + gender;
										AddSubjects(key, 1);
										AddEvents(key, 1);

										if (censorOnEvent) {
											if (!censored.get(division).contains(event.getType())) {
												AddDays(key, event.getDate() + 1 - periodStart);
											}
										}
										else {
											AddDays(key, periodEnd - periodStart);
										}

										// Count overall
										key = event.getType() + "_" + "ALL" + "_" + currentMonth + "_" + ageGroup.getLabel() + "_" + aggregationLevel + "_" + gender;
										AddSubjects(key, 1);
										AddEvents(key, 1);

										if (censorOnEvent) {
											if (!censored.get(division).contains(event.getType())) {
												AddDays(key, event.getDate() + 1 - periodStart);
											}
										}
										else {
											AddDays(key, periodEnd - periodStart);
										}
									}
								}
							}

							if (censorOnEvent) {
								censored.get(division).add(event.getType());
							}

							// Mark event to remove it from the list
							finishedEvents.add(event);
						}
					}

					if (historyFollowupEventTypes.add(event.getType()) && outputPatientTime) {
						//save patient time variables
						historyBag.add(new ExtendedMultiKey(event.getType(),patient.getPatientTimeFromCohortStartInDays(event.getDate())));
						followUpBag.add(new ExtendedMultiKey(event.getType(), patient.getPatientTimeUntilCohortEndInDays(event.getDate())));
					}
				}

				// Add patient time for all event types not encountered
				for (String eventType : allEventTypes) {

					if (!eventTypesFound.contains(eventType) && (!censored.get(division).contains(eventType))) {

						List<String> aggregationLevels = aggregations.getAggregationLevels(patient, null);

						for (AgeGroup ageGroup : allAgeGroupDefinition.getAgeGroups(patient, getAgeDate(patient, periodStart + 1))) {
							for (String aggregationLevel : aggregationLevels) {

								String key = eventType + "_" + currentYear + "_" + currentMonth + "_" + ageGroup.getLabel() + "_" + aggregationLevel + "_" + gender;
								AddSubjects(key, 1);
								AddDays(key, periodEnd - periodStart);

								// Count overall
								key = eventType + "_" + "ALL" + "_" + currentMonth + "_" + ageGroup.getLabel() + "_" + aggregationLevel + "_" + gender;
								AddSubjects(key, 1);
								AddDays(key, periodEnd - periodStart);
							}
						}
					}
				}
			}

			for (Event event : finishedEvents) {
				uniqueEvents.get(division).remove(event);
			}
		}
	}


	private int getAgeDate(Patient patient, int date) {
		return ageAt.equals("COHORT START") ? patient.getCohortStartDate() : date;
	}


	private class AggregationLevels {
		//
		// Event;<label>;<window>;<reference>;<event type>[:<code>],...,<event type>[:<code>];...;<event type>[:<code>],...,<event type>[:<code>]
		// Measurement;<label>;<window>;<reference>;<unknown value>;<result value>,<measurement type>:<value>,...,<measurement type>:<value>;...;<result value>,<measurement type>:<value>,...,<measurement type>:<value>
		// Prescription;<label>;<window>;<reference>;<ATC>[:<dose>[:<indication>]],...,<ATC>[:<dose>[:<indication>]]
		//
		// where
		//
		// <window> is the maximum number of days (positive) before the event
		// <reference> determines from when the history is reviewed:
		//   COHORT START
		//   EVENT or not specified
		//

		private String header = "";
		private String emptyHeader = "";
		private List<AggregationLevel> levelList = new ArrayList<AggregationLevel>();
		private List<AggregationLevel> eventAggregationLevels = new ArrayList<AggregationLevel>();
		private List<AggregationLevel> measurementAggregationLevels = new ArrayList<AggregationLevel>();
		private List<AggregationLevel> prescriptionAggregationLevels = new ArrayList<AggregationLevel>();


		public AggregationLevels(List<String> aggregationDefinitions) {
			for (String aggregationDefinition : aggregationDefinitions) {
				AggregationLevel level = new AggregationLevel(aggregationDefinition);
				levelList.add(level);
				switch (level.getType()) {
					case AggregationLevel.EVENT:
						eventAggregationLevels.add(level);
						break;
					case AggregationLevel.MEASUREMENT:
						measurementAggregationLevels.add(level);
						break;
					case AggregationLevel.PRESCRIPTION:
						prescriptionAggregationLevels.add(level);
						break;
					default:
						break;
				}
				header += "," + level.getLabel();
				emptyHeader += ",";
			}
			if (!header.equals("")) {
				header = header.substring(1);
				emptyHeader = emptyHeader.substring(1);
			}
		}


		public void init() {
			for (AggregationLevel level : levelList) {
				level.init();
			}
		}


		public void getRelevantData(Patient patient) {
			init();

			// Collect relevant events
			for (Event event : patient.getEvents()) {
				for (AggregationLevel level : eventAggregationLevels) {
					level.addRelevantEvent(event);
				}
			}

			// Collect relevant measurements
			for (Measurement measurement : patient.getMeasurements()) {
				for (AggregationLevel level : measurementAggregationLevels) {
					level.addRelevantMeasurement(measurement);
				}
			}

			// Collect relevant prescriptions
			for (Prescription prescription : patient.getPrescriptions()) {
				for (AggregationLevel level : prescriptionAggregationLevels) {
					level.addRelevantPrescription(prescription);
				}
			}
		}


		public String getAggregationHeader() {
			return header;
		}


		public String getEmptyAggregationHeader() {
			return emptyHeader;
		}


		public List<String> getAggregationLevels(Patient patient, Event event) {
			List<String> result = new ArrayList<String>();

			String emptyAggregation = "";
			String aggregation = "";
			for (AggregationLevel level : levelList) {
				emptyAggregation += ",";
				aggregation += "," + level.getValue(patient, event);
			}
			if (!aggregation.equals("")) {
				emptyAggregation = emptyAggregation.substring(1);
				aggregation = aggregation.substring(1);
			}

			aggregationLevelsFound.add(emptyAggregation);
			aggregationLevelsFound.add(aggregation);

			result.add(emptyAggregation);
			if (!aggregation.equals(emptyAggregation)) {
				result.add(aggregation);
			}

			return result;
		}
	}


	private class AggregationLevel {
		//
		// EVENT;<label>;<window>;<reference>;<unknown value>;<result value>,<event type>[:<code>],...,<event type>[:<code>];...;<result value>,<event type>[:<code>],...,<event type>[:<code>]
		// MEASUREMENT;<label>;<window>;<reference>;<unknown value>;<result value>,<measurement type>:<value>,...,<measurement type>:<value>;...;<result value>,<measurement type>:<value>,...,<measurement type>:<value>
		// PRESCRIPTION;<label>;<window>;<reference>;<unknown value>;<result value>,<ATC>[:<dose>[:<indication>]],...,<ATC>[:<dose>[:<indication>]];<result value>,<ATC>[:<dose>[:<indication>]],...,<ATC>[:<dose>[:<indication>]]
		//
		// where
		//
		// <window> is the maximum number of days (positive) before the event
		// <reference> determines from when the history is reviewed:
		//   COHORT START
		//   EVENT or not specified
		//
		static final int EVENT        = 0;
		static final int MEASUREMENT  = 1;
		static final int PRESCRIPTION = 2;

		private int type = -1;
		private String label = "";
		private int window = Integer.MAX_VALUE;
		private String reference = "EVENT";
		private String unknownValue = "NO";
		private List<AggregationRule> aggregationRules = new ArrayList<AggregationRule>();
		private List<Event> relevantEvents;
		private List<Measurement> relevantMeasurements;
		private List<Prescription> relevantPrescriptions;
		private boolean valueSet = false;
		private String value = "";


		public AggregationLevel(String definition) {
			String[] defintionSplit = definition.split(";");

			// Get the base type
			if (defintionSplit[0].equals("EVENT"))             type = EVENT;
			else if (defintionSplit[0].equals("MEASUREMENT"))  type = MEASUREMENT;
			else if (defintionSplit[0].equals("PRESCRIPTION")) type = PRESCRIPTION;

			// Get the label
			label = defintionSplit[1];

			// Get the window
			if ((defintionSplit.length > 2)  && (!defintionSplit[2].equals(""))) {
				window = Integer.parseInt(defintionSplit[2]);
			}

			// Get the reference date: CORHORT START or EVENT
			if ((defintionSplit.length > 3)  && (!defintionSplit[3].equals(""))) {
				reference = defintionSplit[3];
			}

			// Get the value in case there is no data available to satisfy the level
			if ((defintionSplit.length > 4)  && (!defintionSplit[4].equals(""))) {
				unknownValue = defintionSplit[4];
			}
			for (int definitionPartNr = 5; definitionPartNr < defintionSplit.length; definitionPartNr++) {
				aggregationRules.add(new AggregationRule(type, defintionSplit[definitionPartNr]));
			}
		}


		public void init() {
			valueSet = false;
			value = "";
			switch (type) {
				case EVENT:
					relevantEvents = new ArrayList<Event>();
					break;
				case MEASUREMENT:
					relevantMeasurements = new ArrayList<Measurement>();
					break;
				case PRESCRIPTION:
					relevantPrescriptions = new ArrayList<Prescription>();
					break;
				default:
					break;
			}
		}


		// Event level methods
		public void addRelevantEvent(Event event) {
			for (AggregationRule rule : aggregationRules) {
				if ((!relevantEvents.contains(event)) && rule.isRelevantEvent(event)) {
					relevantEvents.add(0, event);
				}
			}
		}


		// Measurement level methods
		public void addRelevantMeasurement(Measurement measurement) {
			for (AggregationRule rule : aggregationRules) {
				if ((!relevantMeasurements.contains(measurement)) && rule.isRelevantMeasurement(measurement)) {
					relevantMeasurements.add(0, measurement);
				}
			}
		}


		// Prescription level methods
		public void addRelevantPrescription(Prescription prescription) {
			for (AggregationRule rule : aggregationRules) {
				if ((!relevantPrescriptions.contains(prescription)) && rule.isRelevantPrescription(prescription)) {
					relevantPrescriptions.add(0, prescription);
				}
			}
		}


		// General methods
		public int getType() {
			return type;
		}


		public String getLabel() {
			return label;
		}


		public String getValue(Patient patient, Event referenceEvent) {
			String result = unknownValue;
			if (valueSet) {
				result = value;
			}
			else {
				String levelValue = unknownValue;
				int referenceDate = -1;
				if (reference.equals("COHORT START")) referenceDate = patient.getCohortStartDate();
				if (reference.equals("EVENT") && (referenceEvent != null)) referenceDate = referenceEvent.getDate();
				if (referenceDate != -1) {
					switch (type) {
						case EVENT:
							for (Event event : relevantEvents) {
								for (AggregationRule rule : aggregationRules) {
									if (rule.isValid(event, referenceDate, window)) {
										levelValue = rule.getValue();
										break;
									}
								}
								if (!levelValue.equals(unknownValue)) {
									break;
								}
							}
							break;
						case MEASUREMENT:
							for (Measurement measurement : relevantMeasurements) {
								for (AggregationRule rule : aggregationRules) {
									if (rule.isValid(measurement, referenceDate, window)) {
										levelValue = rule.getValue();
										break;
									}
								}
								if (!levelValue.equals(unknownValue)) {
									break;
								}
							}
							break;
						case PRESCRIPTION:
							for (Prescription prescription : relevantPrescriptions) {
								for (AggregationRule rule : aggregationRules) {
									if (rule.isValid(prescription, referenceDate, window)) {
										levelValue = rule.getValue();
										break;
									}
								}
								if (!levelValue.equals(unknownValue)) {
									break;
								}
							}
							break;
						default:
							break;
					}
					result = levelValue;
					if (reference.equals("COHORT START")) {
						value = result;
						valueSet = true;
					}
				}
			}
			return result;
		}
	}


	public class AggregationRule {
		//
		// <result value>,<event type>[:<code>],...,<event type>[:<code>]
		// <result value>,<measurement type>:<value>,...,<measurement type>:<value>
		// <result value>,<ATC>[:<dose>[:<indication>]],...,<ATC>[:<dose>[:<indication>]]
		//

		private List<String> types = new ArrayList<String>();       // Event Types, Measurement Types, or Prescription ATCs
		private List<String> values = new ArrayList<String>();      // Event Codes, Measurement Values or Prescription Doses
		private List<String> indications = new ArrayList<String>(); // Prescription indications
		private String resultValue = "YES";


		public AggregationRule(int ruleType, String ruleDefinition) {
			String[] ruleDefinitionSplit = ruleDefinition.split(",");
			resultValue = ruleDefinitionSplit[0];
			for (int rulePart = 1; rulePart < ruleDefinitionSplit.length; rulePart++) {
				String[] rulePartSplit = ruleDefinitionSplit[rulePart].split(":");
				types.add(rulePartSplit[0]);
				if (rulePartSplit.length > 1) {
					values.add(rulePartSplit[1]);
				}
				else {
					values.add("");
				}
				if (rulePartSplit.length > 2) {
					indications.add(rulePartSplit[2]);
				}
				else {
					indications.add("");
				}
			}
		}


		// Event rule methods
		public boolean isRelevantEvent(Event event) {
			return types.contains(event.getType());
		}


		public boolean isValid(Event event, int referenceDate, int window) {
			boolean result = false;
			if ((event.getDate() < referenceDate - window) || (event.getDate() > referenceDate)) {
				result = false;
			}
			else {
				result = false;
				for (int nr=0; nr < types.size(); nr++) {
					if (
							(event.getType().equals(types.get(nr))) &&
							(values.get(nr).equals("") || (event.hasCode() && event.getCode().equals(values.get(nr))))
					) {
						result = true;
						break;
					}
				}
			}
			return result;
		}


		// Measurement rule methods
		public boolean isRelevantMeasurement(Measurement measurement) {
			return getTypes().contains(measurement.getType());
		}


		public boolean isValid(Measurement measurement, int referenceDate, int window) {
			boolean result = false;
			if ((measurement.getDate() < referenceDate - window) || (measurement.getDate() > referenceDate)) {
				result = false;
			}
			else {
				result = false;
				for (int nr=0; nr < types.size(); nr++) {
					if (
							(measurement.getType().equals(types.get(nr))) &&
							(values.get(nr).equals("") || measurement.getValue().equals(values.get(nr)))
					) {
						result = true;
						break;
					}
				}
			}
			return result;
		}


		// Prescription rule methods
		public boolean isRelevantPrescription(Prescription prescription) {
			return prescription.startsWith(getTypes());
		}


		public boolean isValid(Prescription prescription, int referenceDate, int window) {
			boolean result = false;
			if ((prescription.getDate() < referenceDate - window) || (prescription.getDate() > referenceDate)) {
				result = false;
			}
			else {
				result = false;
				for (int nr=0; nr < types.size(); nr++) {
					if (
							(prescription.startsWith(types.get(nr))) &&
							(values.get(nr).equals("") || (prescription.hasDose() && prescription.getDose().equals(values.get(nr)))) &&
							(indications.get(nr).equals("") || (prescription.hasIndication() && prescription.getIndication().equals(indications.get(nr))))
					) {
						result = true;
						break;
					}
				}
			}
			return result;
		}


		// General methods
		public List<String> getTypes() {
			return types;
		}


		public String getValue() {
			return resultValue;
		}
	}


	private void AddSubjects(String key, int count) {

		if (intermediateFiles && (!countLogFileName.equals(""))) {
			String record = currentPatient.ID;
			record += "," + Patient.convertGender(currentPatient.gender);
			record += "," + "AddSubjects";
			record += "," + key;
			record += "," + Integer.toString(count);
			Jerboa.getOutputManager().writeln(countLogFileName, record, true);
		}

		if (subjectsSet.add(key)) {
			if (!subjectsCount.containsKey(key)) {
				subjectsCount.put(key, count);
			}
			else {
				subjectsCount.put(key, subjectsCount.get(key) + count);
			}
		}
	}


	private int getSubjects(String key) {
		return (subjectsCount.containsKey(key) ? subjectsCount.get(key) : 0);
	}


	private void AddEvents(String key, int count) {

		if (intermediateFiles && (!countLogFileName.equals(""))) {
			String record = currentPatient.ID;
			record += "," + Patient.convertGender(currentPatient.gender);
			record += "," + "AddEvents";
			record += "," + key;
			record += "," + Integer.toString(count);
			Jerboa.getOutputManager().writeln(countLogFileName, record, true);
		}

		if (intermediateFiles && (!periodsFileName.equals(""))) {
			if (!periods.containsKey(key)) {
				Map<String, Long> dataMap = new HashMap<String, Long>();
				dataMap.put("Days", 0L);
				dataMap.put("Events", 0L);
				periods.put(key, dataMap);
			}
			periods.get(key).put("Events", periods.get(key).get("Events") + count);
		}

		if (!eventsCount.containsKey(key)) {
			eventsCount.put(key, count);
		}
		else {
			eventsCount.put(key, eventsCount.get(key) + count);
		}
	}


	private int getEvents(String key) {
		return (eventsCount.containsKey(key) ? eventsCount.get(key) : 0);
	}


	private void AddDays(String key, long count) {

		if (intermediateFiles && (!countLogFileName.equals(""))) {
			String record = currentPatient.ID;
			record += "," + Patient.convertGender(currentPatient.gender);
			record += "," + "AddDays";
			record += "," + key;
			record += "," + Long.toString(count);
			Jerboa.getOutputManager().writeln(countLogFileName, record, true);
		}

		if (intermediateFiles && (!periodsFileName.equals(""))) {
			if (!periods.containsKey(key)) {
				Map<String, Long> dataMap = new HashMap<String, Long>();
				dataMap.put("Days", 0L);
				dataMap.put("Events", 0L);
				periods.put(key, dataMap);
			}
			periods.get(key).put("Days", periods.get(key).get("Days") + count);
		}

		if (!daysCount.containsKey(key)) {
			daysCount.put(key, count);
		}
		else {
			daysCount.put(key, daysCount.get(key) + count);
		}
	}


	private long getDays(String key) {
		return (daysCount.containsKey(key) ? daysCount.get(key) : 0);
	}


	private int getPeriodFromDate(int date, Byte division) {
		int datePeriod = DateUtilities.getYearFromDays(date);
		if (division == MONTH) {
			datePeriod = (datePeriod * 100) + DateUtilities.getMonthFromDays(date);
		}
		return datePeriod;
	}


	private int getYearFromPeriod(int period, Byte division) {
		int year = period;
		if (division == MONTH) {
			year = period / 100;
		}
		return year;
	}


	private int nextPeriod(int period, Byte division) {
		int nextPeriod = Integer.MAX_VALUE;
		if (division == YEAR) {
			nextPeriod = period + 1;
		}
		else { // division == MONTH
			int nextYear = period / 100;
			int nextMonth = period - (nextYear * 100) + 1;
			if (nextMonth > 12) {
				nextMonth = 1;
				nextYear++;
			}
			nextPeriod = (nextYear * 100) + nextMonth;
		}
		return nextPeriod;
	}


	private int periodToDays(int period, Byte division) {
		int year = 0;
		int month = 0;
		if (division == YEAR) {
			year = period;
			month = 1;
		}
		else { // division == MONTH
			year = period / 100;
			month = period - (year * 100);
		}

		return DateUtilities.dateToDays(new int[]{ year, month, 1 });
	}


	private String computeStdIncidenceRate(Collection<String> selectedAgeRanges, String eventType, String year, String month) {
		double rate = 0;
		double populationPercentage = 0;
		boolean illegal = true;

		int totalEvents = 0;
		int totalDays = 0;

		for (String ageRange : selectedAgeRanges){
			int events = 0;
			long days = 0;

			// Events: Event, Year, Month, AgeGroup, Gender
			if (eventType.equals("")) {
				for (String type : allEventTypes) {
					events += getEvents(type + "_" + year + "_" + month + "_" + ageRange + "_" + "M") + getEvents(eventType + "_" + year + "_" + month + "_" + ageRange + "_" + "F");
				}
			}
			else {
				events = getEvents(eventType + "_" + year + "_" + month + "_" + ageRange + "_" + "M") + getEvents(eventType + "_" + year + "_" + month + "_" + ageRange + "_" + "F");
			}
			if (events != 0) {
				illegal = false;
			}

			// Days: Event, Year, Month, AgeGroup, Gender
			if (eventType.equals("")) {
				for (String type : allEventTypes) {
					days += getDays(type + "_" + year + "_" + month + "_" + ageRange + "_" + "M") + getEvents(eventType + "_" + year + "_" + month + "_" + ageRange + "_" + "F");
				}

			}
			else {
				days = getDays(eventType + "_" + year + "_" + month + "_" + ageRange + "_" + "M") + getEvents(eventType + "_" + year + "_" + month + "_" + ageRange + "_" + "F");
			}
			if (days != 0) {
				illegal = false;
			}


			double normalisedCount = referencePopulationDistribution.getNormalisedCount(ageRange);
			if (normalisedCount != -1D) {
				totalEvents += events;
				totalDays += days;

				double localRate = events == 0 ? 0 : perNYears * DateUtilities.daysPerYear * events / (double)days;
				rate += localRate * normalisedCount;
				populationPercentage += normalisedCount;
			}
		}

		if (illegal || (totalEvents == 0) || (totalDays == 0))
			return "incomparable population";
		else
			return Double.toString(rate / populationPercentage);
	}


	private class PopulationDistribution {
		private Map<String, Double> age2count = new HashMap<String, Double>();
		private double totalPatientTime = 0;


		public PopulationDistribution(List<String> table){
			for (String line : table){
				String[] cells = line.split(";");
				String age = cells[0];
				Double patientTime = Double.parseDouble(cells[1]);
				age2count.put(age, patientTime);
				totalPatientTime += patientTime;
			}
		}

		@SuppressWarnings("unused")
		public Set<String> getAgeGroups(){
			return age2count.keySet();
		}

		public double getNormalisedCount(String age){
			Double normalisedCount = -1D;
			if (age2count.containsKey(age)) {
				normalisedCount = age2count.get(age) / (double)totalPatientTime;
			}
			return normalisedCount;
		}

	}


	//MAIN FOR DEBUGGING

	public static void main(String[] args) {

		PatientObjectCreator poc = new PatientObjectCreator();

		String testDataPath = "D:/Work/TestData/novartis/";
		FilePaths.WORKFLOW_PATH = "D:/Work/TestData/novartis/";
		FilePaths.INTERMEDIATE_PATH = "D:/Work/TestData/novartis/Intermediate/";
		FilePaths.LOG_PATH = FilePaths.WORKFLOW_PATH + "Log/";
		String patientsFile = testDataPath + "Patients.txt";
		String eventsFile = testDataPath + "Events.txt";
		Logging.prepareOutputLog();

		PopulationDefinition populationDefinition = new PopulationDefinition();

		populationDefinition.setOutputFileNamesInDebug("D:/Work/TestData/novartis/");

		populationDefinition.runInPeriod.add("0");
		populationDefinition.childInclusionPeriod     = 0;
		populationDefinition.studyStart               = "19970101";
		populationDefinition.studyEnd                 = "20080101";
		populationDefinition.minAge                   = 0;
		populationDefinition.maxAge                   = 999;
		populationDefinition.minimumDaysOfPatientTime = 0;

		populationDefinition.intermediateFiles        = true;
		populationDefinition.intermediateStats        = true;



		if (populationDefinition.init()) {

			PrescriptionCohortDefinition cohortDefinition = new PrescriptionCohortDefinition();
			/*
			//cohortDefinition.naivePeriod = 365;
			//cohortDefinition.drugsOfInterest.add("B01AB01");
			//cohortDefinition.eventsEndpoint.add("DEATH");
			//cohortDefinition.drugsEndpoint.add("C01AB01");
			//cohortDefinition.drugsEndpoint.add("N01AB01");
			 */
			cohortDefinition.intermediateFiles = true;
			cohortDefinition.intermediateStats = true;

			cohortDefinition.setOutputFileNamesInDebug("D:/Work/TestData/novartis/");
			if (cohortDefinition.init()) {
				IncidenceRate incidenceRate = new IncidenceRate();
				incidenceRate.setOutputFileNamesInDebug("D:/Work/TestData/novartis/");

				incidenceRate.intermediateFiles = true;

				//incidenceRate.runAsApplication = true;

				incidenceRate.ageGroups.add("0;99;ALL");
				incidenceRate.perNYears = 100000;
				incidenceRate.minSubjectsPerRow = 0;

				incidenceRate.ageGroups.add("0;5;00-04");
				incidenceRate.ageGroups.add("5;10;05-09");
				incidenceRate.ageGroups.add("10;15;10-14");
				incidenceRate.ageGroups.add("15;20;15-19");
				incidenceRate.ageGroups.add("20;25;20-24");
				incidenceRate.ageGroups.add("25;30;25-29");
				incidenceRate.ageGroups.add("30;35;30-34");
				incidenceRate.ageGroups.add("35;40;35-39");
				incidenceRate.ageGroups.add("40;45;40-44");
				incidenceRate.ageGroups.add("45;50;45-49");
				incidenceRate.ageGroups.add("50;55;50-54");
				incidenceRate.ageGroups.add("55;60;55-59");
				incidenceRate.ageGroups.add("60;65;60-64");
				incidenceRate.ageGroups.add("65;70;65-69");
				incidenceRate.ageGroups.add("70;75;70-74");
				incidenceRate.ageGroups.add("75;80;75-79");
				incidenceRate.ageGroups.add("80;85;80-84");
				incidenceRate.ageGroups.add("85;999;85-");
				incidenceRate.referencePopulation.add("00-04;8.86");
				incidenceRate.referencePopulation.add("05-09;8.69");
				incidenceRate.referencePopulation.add("10-14;8.6");
				incidenceRate.referencePopulation.add("15-19;8.47");
				incidenceRate.referencePopulation.add("20-24;8.22");
				incidenceRate.referencePopulation.add("25-29;7.93");
				incidenceRate.referencePopulation.add("30-34;7.61");
				incidenceRate.referencePopulation.add("35-39;7.15");
				incidenceRate.referencePopulation.add("40-44;6.59");
				incidenceRate.referencePopulation.add("45-49;6.04");
				incidenceRate.referencePopulation.add("50-54;5.37");
				incidenceRate.referencePopulation.add("55-59;4.55");
				incidenceRate.referencePopulation.add("60-64;3.72");
				incidenceRate.referencePopulation.add("65-69;2.96");
				incidenceRate.referencePopulation.add("70-74;2.21");
				incidenceRate.referencePopulation.add("75-79;1.52");
				incidenceRate.referencePopulation.add("80-84;0.91");
				incidenceRate.referencePopulation.add("85-;0.63");
				incidenceRate.populationSubgroups.add("Children;00-04");
				incidenceRate.populationSubgroups.add("Children;05-09");
				incidenceRate.populationSubgroups.add("Children;10-14");
				incidenceRate.populationSubgroups.add("Adults;15-19");
				incidenceRate.populationSubgroups.add("Adults;20-24");
				incidenceRate.populationSubgroups.add("Adults;25-29");
				incidenceRate.populationSubgroups.add("Adults;30-34");
				incidenceRate.populationSubgroups.add("Adults;35-39");
				incidenceRate.populationSubgroups.add("Adults;40-44");
				incidenceRate.populationSubgroups.add("Adults;45-49");
				incidenceRate.populationSubgroups.add("Adults;50-54");
				incidenceRate.populationSubgroups.add("Adults;55-59");
				incidenceRate.populationSubgroups.add("Adults;60-64");
				incidenceRate.populationSubgroups.add("Adults;65-69");
				incidenceRate.populationSubgroups.add("Adults;70-74");
				incidenceRate.populationSubgroups.add("Adults;75-79");
				incidenceRate.populationSubgroups.add("Adults;80-84");
				incidenceRate.populationSubgroups.add("Adults;85-");
				//incidenceRate.postFix = " years";
				//incidenceRate.censorOnEvent = false;
				incidenceRate.perNYears = 100000;
				incidenceRate.minSubjectsPerRow = 0;


				if (incidenceRate.init()) {

					Logging.addNewLine();
					Logging.add("  Patient file       = " + patientsFile);
					Logging.add("  Events file        = " + eventsFile);

					try{
						List<Patient> patients = poc.createPatients(patientsFile, eventsFile, null, null);

						if (patients != null && patients.size() > 0){

							Timer timer = new Timer();
							timer.start();

							for (Patient patient : patients){
								Patient populationPatient = populationDefinition.process(patient);

								if (populationPatient.inPopulation) {
									Patient cohortPatient = cohortDefinition.process(populationPatient);

									if (cohortPatient.isInCohort()) {
										incidenceRate.process(cohortPatient);
									}
								}
							}

							Logging.addNewLine();

							incidenceRate.outputResults();

							cohortDefinition.outputResults();

							populationDefinition.outputResults();

							timer.stop();

							System.out.println("Incidence rates of "+ Integer.toString(patients.size()) +" patients run in: "+timer);
						}
					}
					catch(IOException e){
						System.out.println("Error while opening input files");
					}
				}
			}
		}
	}

}
