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
 * Author: Mees Mosseveld (MM) Marius Gheorghe (MG) - department of Medical Informatics	  *
 * 																						  *
 * $Rev:: 3961              $:  Revision of last commit                                   *
 * $Author:: root           $:  Author of last commit                                     *
 * $Date:: 2013-05#			$:  Date and time (CET) of last commit						  *
 ******************************************************************************************/
package org.erasmusmc.jerboa.modules;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

import org.erasmusmc.jerboa.Jerboa;
import org.erasmusmc.jerboa.config.DataDefinition;
import org.erasmusmc.jerboa.config.FilePaths;
import org.erasmusmc.jerboa.dataClasses.Event;
import org.erasmusmc.jerboa.dataClasses.Patient;
import org.erasmusmc.jerboa.engine.Module;
import org.erasmusmc.jerboa.engine.PatientObjectCreator;
import org.erasmusmc.jerboa.gui.graphs.Graphs;
import org.erasmusmc.jerboa.gui.graphs.LinePlotDS;
import org.erasmusmc.jerboa.gui.graphs.Plot;
import org.erasmusmc.jerboa.modifiers.PopulationDefinition;
import org.erasmusmc.jerboa.modifiers.PrescriptionCohortDefinition;
import org.erasmusmc.jerboa.utilities.AgeGroupDefinition;
import org.erasmusmc.jerboa.utilities.AgeGroupDefinition.AgeGroup;
import org.erasmusmc.jerboa.utilities.stats.HistogramStats;
import org.erasmusmc.jerboa.utilities.DateUtilities;
import org.erasmusmc.jerboa.utilities.ExtendedMultiKey;
import org.erasmusmc.jerboa.utilities.FileUtilities;
import org.erasmusmc.jerboa.utilities.InputFileUtilities;
import org.erasmusmc.jerboa.utilities.Logging;
import org.erasmusmc.jerboa.utilities.MultiKeyBag;
import org.erasmusmc.jerboa.utilities.MultiKeyMap;
import org.erasmusmc.jerboa.utilities.Progress;
import org.erasmusmc.jerboa.utilities.StringUtilities;
import org.erasmusmc.jerboa.utilities.Timer;
import org.erasmusmc.jerboa.utilities.Wildcard;

/**
 * The prevalence module. Computes the prevalence of events at specified moments/periods in time.
 * @author bmosseveld & MG (plots)
 *
 */
public class Prevalence extends Module {

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
	 * The list of dates as yyyymmdd for the point estimate of prevalence.
	 * If empty then the first of July of every year is taken.<br>
	 * For example: {@code 20130101}
	 */
	public List<String> indexDates = new ArrayList<String>();

	/**
	 * Period prevalence definitions.
	 * Format:
	 *
	 *   Relative;<label>;<start>;<end>  Period <start> to <end> relative to the index dates specified.
	 *                                   The label is used in the output file name for the prevalence.
	 *                                   Formula:
	 *
	 *                                     number of existing case in period / number of people with time in the period
	 *
	 *   CalendarPopulation;<label>      Period prevalence for each calendar year of the index dates
	 *                                   for the average population in that year.
	 *                                   The label is used in the output file name for the prevalence.
	 *                                   Formula:
	 *
	 *                                     number of existing case in year / ((population 1 January + population 31 december) / 2)
	 *
	 *   CalendarTime;<label>            Period prevalence for each calendar year of the index dates
	 *                                   for the person time in that year.
	 *                                   The label is used in the output file name for the prevalence.
	 *                                   Formula:
	 *
	 *                                     number of existing case in year / number of person years in year
	 */
	public List<String> periodPrevalence = new ArrayList<String>();

	/**
	 * Flag to specify if events should be taken from the total history of the patient
	 * or only from patient start to patient end.
	 * Format:
	 *   EventType;UseAllHistory
	 *
	 * where
	 *
	 *   EventType        The type of event. If evensOfInterest with a mapping to a label are
	 *                    specified then this should refer to the label.
	 *   UseAllHistory    Yes if events in all history should be used for the given type of events,
	 *                    No  if only events in cohort time should be used for the given type of event.
	 *
	 * If an event type is not specified only events in cohort time are used for that event type.
	 */
	public List<String> useAllHistory = new ArrayList<String>();

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
	 * The minimum number of subjects in a row of the resulting aggregated table. Rows with fewer subjects
	 * are deleted.
	 * default = 0
	 */
	public int minSubjectsPerRow;


	/* Local variables */

	// Debug
	private int debug = 0;
	private String debugFileName = "";
	private String patientTimeFileName = "";

	// Event map
	private Map<String, Set<String>> eventMap = null;

	// The list containing the index dates in days
	private Set<Integer> indexDays = null;

	// Age group definition
	private AgeGroupDefinition ageGroupDefinition = null;

	// Use all history flags for event types
	private Map<String, Boolean> eventUseAllHistoryMap = null;

	// Prevalence definitions
	private List<PrevalenceDefinition> allPrevalenceDefinitions = null;
	private List<PrevalenceDefinition> relativePeriodPrevalenceDefinitions = null;
	private List<PrevalenceDefinition> calendarYearPeriodPrevalenceDefinitions = null;

	// Bags
	private MultiKeyBag subjectsOverallBag = null;            // Label, Date, Gender
	private MultiKeyBag eventsOverallBag = null;              // Label, Date, Event, Gender
	private MultiKeyBag timeOverallBag = null;            // Label, Date, Gender, Time
	private MultiKeyBag subjectsAgegroupBag = null;           // Label, Date, AgeGroup, Gender
	private MultiKeyBag eventsAgegroupBag = null;             // Label, Date, Event, AgeGroup, Gender
	private MultiKeyBag timeAgegroupBag = null;           // Label, Date, AgeGroup, Gender, Time

	private MultiKeyBag historyBag = null;            		  // Date, PatientTime
	private MultiKeyBag followUpBag = null;            		  // Date, PatientTime

	private MultiKeyMap prevalenceOverallMap = null;          // Event, Date, Prevalence
	private MultiKeyMap prevalenceOverallGenderMap = null;    // Event, Date, Gender, Prevalence
	private MultiKeyMap prevalenceMap = null;                 // Event, Date, AgeGroup, Prevalence
	private MultiKeyMap prevalenceGenderMap = null;           // Event, Date, AgeGroup, Gender, Prevalence

	// List of all index dates
	private Map<Integer, Integer> allIndexYears = null;
	private Set<Integer> allIndexDates = null;

	// List of all period prevalence file names
	private Map<PrevalenceDefinition, String> prevalenceFiles = null;

	// List of all events
	private Set<String> allEventTypes = null;

	// Minimum and maximum year found
	private int today;


	@Override
	public void setNeededFiles() {
		setRequiredFile(DataDefinition.PATIENTS_FILE);
		setRequiredFile(DataDefinition.EVENTS_FILE);
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

		// Create a map of events of interest
		if (eventsOfInterest.size() == 0) {
			eventsOfInterest = FileUtilities.getList(InputFileUtilities.getEventTypes());     // All events
		}
		eventMap = new HashMap<String, Set<String>>();
		for (int nr = 0; nr < eventsOfInterest.size(); nr++) {
			String[] eventDefintionSplit = eventsOfInterest.get(nr).split(";");
			if (eventDefintionSplit.length > 0) {
				String label = eventDefintionSplit[0].trim();
				if (eventDefintionSplit.length > 1) {
					String[] sourceEventTypes = eventDefintionSplit[1].split(",");
					for (int part = 0; part < sourceEventTypes.length; part++) {
						String sourceEventType = sourceEventTypes[part].trim();
						Set<String> sourceEventMapping = eventMap.get(sourceEventType);
						if (sourceEventMapping == null) {
							sourceEventMapping = new HashSet<String>();
							eventMap.put(sourceEventType, sourceEventMapping);
						}
						sourceEventMapping.add(label);
					}
				}
				else {
					Set<String> sourceEventMapping = eventMap.get(label);
					if (sourceEventMapping == null) {
						sourceEventMapping = new HashSet<String>();
						eventMap.put(label, sourceEventMapping);
					}
					sourceEventMapping.add(label);
				}
			}
			else {
				Logging.add("WARNING: Empty eventsOfInterest[" + nr + "]");
			}
		}

		// Create the list containing the index dates in days
		indexDays = new HashSet<Integer>();

		// Parse age group definition
		ageGroupDefinition = new AgeGroupDefinition(ageGroups);
		initOK = initOK && ageGroupDefinition.isOK();

		// Create the lists for all index dates
		allIndexYears = new HashMap<Integer, Integer>();
		allIndexDates = new HashSet<Integer>();

		// Create the list for all event types
		allEventTypes = new HashSet<String>();

		eventUseAllHistoryMap = new HashMap<String, Boolean>();
		for (String eventTypeUseHistory : useAllHistory) {
			String[] eventTypeUseHistorySplit = eventTypeUseHistory.split(";");
			if (eventTypeUseHistorySplit.length > 1) {
				String eventType = eventTypeUseHistorySplit[0];
				boolean useHistory = eventTypeUseHistorySplit[1].equals("YES");
				if (!eventType.equals("")) {
					for (String label : eventMap.keySet()) {
						if (eventMap.get(label).contains(eventType)) {
							allEventTypes.add(eventType);
							eventUseAllHistoryMap.put(eventType, useHistory);
							break;
						}
					}
				}
				else {
					Logging.add("Error empty event type in useAllHistory definition: " + eventTypeUseHistory);
				}
			}
			else {
				Logging.add("Error not enough parameters in useAllHistory definition: " + eventTypeUseHistory);
			}
		}


		// Create the list for all prevalence definitions
		allPrevalenceDefinitions = new ArrayList<PrevalenceDefinition>();

		// Point prevalence definition
		allPrevalenceDefinitions.add(new PrevalenceDefinition("POINT"));

		// Period Prevalence definitions
		calendarYearPeriodPrevalenceDefinitions = new ArrayList<PrevalenceDefinition>();
		relativePeriodPrevalenceDefinitions = new ArrayList<PrevalenceDefinition>();
		for (String prevalenceDefinition : periodPrevalence) {
			PrevalenceDefinition prevalence = new PrevalenceDefinition(prevalenceDefinition);
			allPrevalenceDefinitions.add(prevalence);
			if (prevalence.getType() == PrevalenceDefinition.TYPE_RELATIVE) {
				relativePeriodPrevalenceDefinitions.add(prevalence);
			}
			else {
				calendarYearPeriodPrevalenceDefinitions.add(prevalence);
			}
			initOK = initOK && prevalence.isOK();
		}

		// Create the bags
		subjectsOverallBag = new MultiKeyBag();
		eventsOverallBag = new MultiKeyBag();
		timeOverallBag = new MultiKeyBag();
		subjectsAgegroupBag = new MultiKeyBag();
		eventsAgegroupBag = new MultiKeyBag();
		timeAgegroupBag = new MultiKeyBag();
		prevalenceOverallMap = new MultiKeyMap();
		prevalenceOverallGenderMap = new MultiKeyMap();
		prevalenceMap = new MultiKeyMap();
		prevalenceGenderMap = new MultiKeyMap();
		historyBag = new MultiKeyBag();
		followUpBag = new MultiKeyBag();

		// Create the output files
		if (intermediateFiles) {
			//TODO
		}

		debug = Jerboa.isInDebugMode ? 999 : debug;
		if (debug > 0) {
			debugFileName = StringUtilities.addSuffixToFileName(intermediateFileName, "_Debug.csv", true);
			if (Jerboa.getOutputManager().addFile(debugFileName, 100)) {
				Jerboa.getOutputManager().writeln(debugFileName, "Bag,PatientID,Label,Date,EventType,AgeGroup,Gender,Value", true);
			}
			else {
				initOK = false;
			}
		}

		patientTimeFileName = StringUtilities.addSuffixToFileName(outputFileName, "_patientTime.csv", true);
		if (Jerboa.getOutputManager().addFile(patientTimeFileName, 100)) {
			Jerboa.getOutputManager().writeln(patientTimeFileName, "Date,MeanBefore,SDBefore,MinBefore,MaxBefore,MeanAfter,SDAfter,MinAfter,MaxAfter", true);
		}

		prevalenceFiles = new HashMap<PrevalenceDefinition, String>();
		for (PrevalenceDefinition prevalence : allPrevalenceDefinitions) {
			String prevalenceFileName = StringUtilities.addSuffixToFileName(outputFileName, prevalence.getLabel() + ".csv", true);
			if (Jerboa.getOutputManager().addFile(prevalenceFileName, 100)) {

				String header = "EventType";
				header += "," + (((prevalence.getType() == PrevalenceDefinition.TYPE_CALENDARPOPULATION) || (prevalence.getType() == PrevalenceDefinition.TYPE_CALENDARTIME)) ? "Year" : "IndexDate");
				header += "," + "AgeGroup";
				header += "," + ((prevalence.getType() == PrevalenceDefinition.TYPE_CALENDARTIME) ? "MaleYears" : "MaleSubjects");
				header += "," + ((prevalence.getType() == PrevalenceDefinition.TYPE_CALENDARTIME) ? "FemaleYears" : "FemaleSubjects");
				header += "," + ((prevalence.getType() == PrevalenceDefinition.TYPE_CALENDARTIME) ? "TotalYears" : "TotalSubjects");
				header += "," + "MaleCases";
				header += "," + "Femalecases";
				header += "," + "TotalCases";
				header += "," + "MalePrevalence";
				header += "," + "FemalePrevalence";
				header += "," + "TotalPrevalence";

				Jerboa.getOutputManager().writeln(prevalenceFileName, header, true);
				prevalenceFiles.put(prevalence, prevalenceFileName);
			}
			else {
				initOK = false;
			}

			Calendar cal = Calendar.getInstance();
			cal.setTime(new Date());
			today = DateUtilities.dateToDays(new int[] { cal.get(Calendar.YEAR) , cal.get(Calendar.MONTH) + 1, cal.get(Calendar.DAY_OF_MONTH) });
		}

		return initOK;
	}

	@Override
	public Patient process(Patient patient) {
		if ((patient != null) && patient.isInCohort()) {

			// Get the first events of each type of the patient.
			Map<String, Integer> firstEvents = new HashMap<String, Integer>();
			List<Event> events = patient.getEvents();
			if (events.size() > 0) {
				for (Event event : events) {
					if (eventMap.containsKey(event.getType())) {
						for (String eventType : eventMap.get(event.getType())) {

							int start = 0;
							int end = today;

							if ((!eventUseAllHistoryMap.containsKey(eventType)) || (!eventUseAllHistoryMap.get(eventType))) {
								start = patient.cohortStartDate;
								end = patient.cohortEndDate;
							}

							allEventTypes.add(eventType);
							if ((firstEvents.get(eventType) == null) && (event.date >= start) && (event.date < end)) {
								firstEvents.put(eventType, event.date);
							}
						}
					}
				}
			}

			// If no index dates are specified take the first of July of each year where the
			// patient is inside the cohort.
			if (indexDates.size() == 0) {
				for (int year = DateUtilities.getYearFromDays(patient.getCohortStartDate()); year <= DateUtilities.getYearFromDays(patient.getCohortEndDate()); year++)  {
					String indexDate = Integer.toString(year) + "0701";
					int indexDay = DateUtilities.dateToDays(indexDate, DateUtilities.DATE_ON_YYYYMMDD);
					if (indexDays.add(indexDay)) {
						allIndexYears.put(indexDay, DateUtilities.getYearFromDays(indexDay));
					}
				}
			}
			else {
				for (String date : indexDates) {
					int indexDay = DateUtilities.dateToDays(date, DateUtilities.DATE_ON_YYYYMMDD);
					if (indexDays.add(indexDay)) {
						allIndexYears.put(indexDay, DateUtilities.getYearFromDays(indexDay));
					}
				}
			}

			Set<Integer> calendarPopulationUsedIndexYears = new HashSet<Integer>();
			Set<Integer> calendarTimeUsedIndexYears = new HashSet<Integer>();
			for (int date : indexDays) {

				allIndexDates.add(date);

				List<AgeGroup> ageGroups = ageGroupDefinition.getAgeGroups(patient,date);

				// Point prevalence
				String label = "";
				if (patient.dateInCohort(date)) {

					// Fill the patient time bags

					historyBag.add(new ExtendedMultiKey(date, patient.getPatientTimeFromCohortStartInDays(date)));
					followUpBag.add(new ExtendedMultiKey(date, patient.getPatientTimeUntilCohortEndInDays(date)));

					// Fill the subjectsOverallBag: Label, Date, Gender
					subjectsOverallBag.add(new ExtendedMultiKey(label, date, patient.gender));
					if (debug > 0) {
						Jerboa.getOutputManager().writeln(debugFileName, "subjectsOverallBag," + patient.ID + "," + label + "," + DateUtilities.daysToDate(date) + ",,," + Patient.convertGender(patient.gender) + ",1", true);
					}

					for (AgeGroup ageGroup : ageGroups) {

						// Fill the subjectsAgegroupBag: Label, Date, AgeGroup, Gender
						subjectsAgegroupBag.add(new ExtendedMultiKey(label, date, ageGroup.getLabel(), patient.gender));
						if (debug > 1) {
							Jerboa.getOutputManager().writeln(debugFileName, "subjectsAgegroupBag," + patient.ID + "," + label + "," + DateUtilities.daysToDate(date) + ",," + ageGroup.getLabel() + "," + Patient.convertGender(patient.gender) + ",1", true);
						}
					}

					for (String eventType : firstEvents.keySet()) {
						if (firstEvents.get(eventType) < date) {

							// Fill the eventsOverallBag: Label, Date, Event, Gender
							eventsOverallBag.add(new ExtendedMultiKey(label, date, eventType, patient.gender));
							if (debug > 0) {
								Jerboa.getOutputManager().writeln(debugFileName, "eventsOverallBag," + patient.ID + "," + label + "," + DateUtilities.daysToDate(date) + "," + eventType + ",," + Patient.convertGender(patient.gender) + ",1", true);
							}

							// Fill the eventsAgegroupBag: Label, Date, Event, AgeGroup, Gender
							for (AgeGroup ageGroup : ageGroups) {
								eventsAgegroupBag.add(new ExtendedMultiKey(label, date, eventType, ageGroup.getLabel(), patient.gender));
								if (debug > 1) {
									Jerboa.getOutputManager().writeln(debugFileName, "eventsAgegroupBag," + patient.ID + "," + label + "," + DateUtilities.daysToDate(date) + "," + eventType + "," + ageGroup.getLabel() + "," + Patient.convertGender(patient.gender) + ",1", true);
								}
							}
						}
					}
				}

				// Relative period prevalences
				for (PrevalenceDefinition relativePeriodPrevalence : relativePeriodPrevalenceDefinitions) {
					int periodPrevalenceStart = relativePeriodPrevalence.getPeriodStart(date);
					int periodPrevalenceEnd = relativePeriodPrevalence.getPeriodEnd(date);
					label = relativePeriodPrevalence.getLabel();

					if ((periodPrevalenceStart < patient.getCohortEndDate()) && (periodPrevalenceEnd > patient.getCohortStartDate())) {

						// Fill the subjectsOverallBag: Label, Date, Gender
						subjectsOverallBag.add(new ExtendedMultiKey(label, date, patient.gender));
						if (debug > 0) {
							Jerboa.getOutputManager().writeln(debugFileName, "subjectsOverallBag," + patient.ID + "," + label + "," + DateUtilities.daysToDate(date) + ",,," + Patient.convertGender(patient.gender) + ",1", true);
						}

						for (AgeGroup ageGroup : ageGroups) {

							// Fill the subjectsAgegroupBag: Label, Date, AgeGroup, Gender
							subjectsAgegroupBag.add(new ExtendedMultiKey(label, date, ageGroup.getLabel(), patient.gender));
							if (debug > 1) {
								Jerboa.getOutputManager().writeln(debugFileName, "subjectsAgegroupBag," + patient.ID + "," + label + "," + DateUtilities.daysToDate(date) + ",," + ageGroup.getLabel() + "," + Patient.convertGender(patient.gender) + ",1", true);
							}
						}

						// Count the patients with the specified events in the period
						Set<String> eventTypesFound = new HashSet<String>();
						for (Event event : patient.getEvents()) {
							if (eventMap.containsKey(event.getType())) {
								for (String eventType : eventMap.get(event.getType())) {
									int start = 0;
									if ((!eventUseAllHistoryMap.containsKey(eventType)) || (!eventUseAllHistoryMap.get(eventType))) {
										start = patient.cohortStartDate;
									}
									if ((event.getDate() >= start) && (event.getDate() >= periodPrevalenceStart) && (event.getDate() < periodPrevalenceEnd) && eventTypesFound.add(event.getType())) {

										// Fill the eventsOverallBag: Label, Date, Event, Gender
										eventsOverallBag.add(new ExtendedMultiKey(label, date, eventType, patient.gender));
										if (debug > 0) {
											Jerboa.getOutputManager().writeln(debugFileName, "eventsOverallBag," + patient.ID + "," + label + "," + DateUtilities.daysToDate(date) + "," + eventType + ",," + Patient.convertGender(patient.gender) + ",1", true);
										}


										// Fill the eventsAgegroupBag: Label, Date, Event, AgeGroup, Gender
										for (AgeGroup ageGroup : ageGroups) {
											eventsAgegroupBag.add(new ExtendedMultiKey(label, date, eventType, ageGroup.getLabel(), patient.gender));
											if (debug > 1) {
												Jerboa.getOutputManager().writeln(debugFileName, "eventsAgegroupBag," + patient.ID + "," + label + "," + DateUtilities.daysToDate(date) + "," + eventType + "," + ageGroup.getLabel() + "," + Patient.convertGender(patient.gender) + ",1", true);
											}
										}
									}
								}
							}
						}
					}
				}

				// Calendar year period prevalences
				for (PrevalenceDefinition calendarYearPeriodPrevalence : calendarYearPeriodPrevalenceDefinitions) {
					int periodPrevalenceStart = calendarYearPeriodPrevalence.getPeriodStart(date);
					int periodPrevalenceEnd = calendarYearPeriodPrevalence.getPeriodEnd(date);
					label = calendarYearPeriodPrevalence.getLabel();

					boolean subjectCounted = false;

					if (calendarYearPeriodPrevalence.getType() == PrevalenceDefinition.TYPE_CALENDARPOPULATION) {
						if (calendarPopulationUsedIndexYears.add(periodPrevalenceStart)) { // A year the calendar year calendar population period prevalence has not been calculated for yet

							// Count population at start of period
							if ((periodPrevalenceStart < patient.getCohortEndDate()) && (periodPrevalenceStart >= patient.getCohortStartDate())) {
								subjectCounted = true;

								// Fill the subjectsOverallBag: Label, Date, Gender
								subjectsOverallBag.add(new ExtendedMultiKey(label, periodPrevalenceStart, patient.gender));
								if (debug > 0) {
									Jerboa.getOutputManager().writeln(debugFileName, "subjectsOverallBag," + patient.ID + "," + label + "," + DateUtilities.daysToDate(periodPrevalenceStart) + ",,," + Patient.convertGender(patient.gender) + ",1", true);
								}

								for (AgeGroup ageGroup : ageGroups) {

									// Fill the subjectsAgegroupBag: Label, Date, AgeGroup, Gender
									subjectsAgegroupBag.add(new ExtendedMultiKey(label, periodPrevalenceStart, ageGroup.getLabel(), patient.gender));
									if (debug > 1) {
										Jerboa.getOutputManager().writeln(debugFileName, "subjectsAgegroupBag," + patient.ID + "," + label + "," + DateUtilities.daysToDate(periodPrevalenceStart) + ",," + ageGroup.getLabel() + "," + Patient.convertGender(patient.gender) + ",1", true);
									}
								}
							}

							// Count population at end of period
							if ((periodPrevalenceEnd <= patient.getCohortEndDate()) && (periodPrevalenceEnd > patient.getCohortStartDate())) {
								subjectCounted = true;

								// Fill the subjectsOverallBag: Label, Date, Gender
								subjectsOverallBag.add(new ExtendedMultiKey(label, periodPrevalenceStart, patient.gender));
								if (debug > 0) {
									Jerboa.getOutputManager().writeln(debugFileName, "subjectsOverallBag," + patient.ID + "," + label + "," + DateUtilities.daysToDate(periodPrevalenceStart) + ",,," + Patient.convertGender(patient.gender) + ",1", true);
								}

								for (AgeGroup ageGroup : ageGroups) {
									// Fill the subjectsAgegroupBag: Label, Date, AgeGroup, Gender
									subjectsAgegroupBag.add(new ExtendedMultiKey(label, periodPrevalenceStart, ageGroup.getLabel(), patient.gender));
									if (debug > 1) {
										Jerboa.getOutputManager().writeln(debugFileName, "subjectsAgegroupBag," + patient.ID + "," + label + "," + DateUtilities.daysToDate(periodPrevalenceStart) + ",," + ageGroup.getLabel() + "," + Patient.convertGender(patient.gender) + ",1", true);
									}
								}
							}

							// Count the patients with the specified events in the period
							Set<String> eventTypesFound = new HashSet<String>();
							for (Event event : patient.getEvents()) {
								if (eventMap.containsKey(event.getType())) {
									for (String eventType : eventMap.get(event.getType())) {
										int start = 0;
										if ((!eventUseAllHistoryMap.containsKey(eventType)) || (!eventUseAllHistoryMap.get(eventType))) {
											start = patient.cohortStartDate;
										}
										if (subjectCounted && (event.getDate() >= start) && (event.getDate() >= periodPrevalenceStart) && (event.getDate() < periodPrevalenceEnd) && eventTypesFound.add(event.getType())) {

											// Fill the eventsOverallBag: Label, Date, Event, Gender
											eventsOverallBag.add(new ExtendedMultiKey(label, periodPrevalenceStart, eventType, patient.gender));
											if (debug > 0) {
												Jerboa.getOutputManager().writeln(debugFileName, "eventsOverallBag," + patient.ID + "," + label + "," + DateUtilities.daysToDate(periodPrevalenceStart) + "," + eventType + ",," + Patient.convertGender(patient.gender) + ",1", true);
											}


											// Fill the eventsAgegroupBag: Label, Date, Event, AgeGroup, Gender
											for (AgeGroup ageGroup : ageGroups) {
												eventsAgegroupBag.add(new ExtendedMultiKey(label, periodPrevalenceStart, eventType, ageGroup.getLabel(), patient.gender));
												if (debug > 1) {
													Jerboa.getOutputManager().writeln(debugFileName, "eventsAgegroupBag," + patient.ID + "," + label + "," + DateUtilities.daysToDate(periodPrevalenceStart) + "," + eventType + "," + ageGroup.getLabel() + "," + Patient.convertGender(patient.gender) + ",1", true);
												}
											}
										}
									}
								}
							}
						}
					}
					else if (calendarYearPeriodPrevalence.getType() == PrevalenceDefinition.TYPE_CALENDARTIME) {
						if (calendarTimeUsedIndexYears.add(periodPrevalenceStart)) { // A year the calendar year calendar time period prevalence has not been calculated for yet
							int time = Math.min(periodPrevalenceEnd, patient.getCohortEndDate()) - Math.max(periodPrevalenceStart, patient.getCohortStartDate());
							if (time > 0) {
								subjectCounted = true;

								double years = time / 365.25;
//								Logging.add(patient.getPatientID() + "," + DateUtilities.getYearFromDays(periodPrevalenceStart) + "," + time + "," + years);
								// Fill the timeOverallBag: Label, Date, Gender, Time
								timeOverallBag.add(new ExtendedMultiKey(label, periodPrevalenceStart, patient.gender, years));
								if (debug > 0) {
									Jerboa.getOutputManager().writeln(debugFileName, "timeOverallBag," + patient.ID + "," + label + "," + DateUtilities.daysToDate(periodPrevalenceStart) + ",,," + Patient.convertGender(patient.gender) + "," + years, true);
								}

								for (AgeGroup ageGroup : ageGroups) {
									// Fill the timeAgegroupBag: Label, Date, AgeGroup, Gender, Time
									timeAgegroupBag.add(new ExtendedMultiKey(label, periodPrevalenceStart, ageGroup.getLabel(), patient.gender, years));
									if (debug > 1) {
										Jerboa.getOutputManager().writeln(debugFileName, "timeAgegroupBag," + patient.ID + "," + label + "," + DateUtilities.daysToDate(periodPrevalenceStart) + ",," + ageGroup.getLabel() + "," + Patient.convertGender(patient.gender) + "," + years, true);
									}
								}
							}


							// Count the patients with the specified events in the period
							Set<String> eventTypesFound = new HashSet<String>();
							for (Event event : patient.getEvents()) {
								if (eventMap.containsKey(event.getType())) {
									for (String eventType : eventMap.get(event.getType())) {
										int start = 0;
										if ((!eventUseAllHistoryMap.containsKey(eventType)) || (!eventUseAllHistoryMap.get(eventType))) {
											start = patient.cohortStartDate;
										}
										if (subjectCounted && (event.getDate() >= start) && (event.getDate() >= periodPrevalenceStart) && (event.getDate() < periodPrevalenceEnd) && eventTypesFound.add(event.getType())) {

											// Fill the eventsOverallBag: Label, Date, Event, Gender
											eventsOverallBag.add(new ExtendedMultiKey(label, periodPrevalenceStart, eventType, patient.gender));
											if (debug > 0) {
												Jerboa.getOutputManager().writeln(debugFileName, "eventsOverallBag," + patient.ID + "," + label + "," + DateUtilities.daysToDate(periodPrevalenceStart) + "," + eventType + ",," + Patient.convertGender(patient.gender) + ",1", true);
											}


											// Fill the eventsAgegroupBag: Label, Date, Event, AgeGroup, Gender
											for (AgeGroup ageGroup : ageGroups) {
												eventsAgegroupBag.add(new ExtendedMultiKey(label, periodPrevalenceStart, eventType, ageGroup.getLabel(), patient.gender));
												if (debug > 1) {
													Jerboa.getOutputManager().writeln(debugFileName, "eventsAgegroupBag," + patient.ID + "," + label + "," + DateUtilities.daysToDate(periodPrevalenceStart) + "," + eventType + "," + ageGroup.getLabel() + "," + Patient.convertGender(patient.gender) + ",1", true);
												}
											}
										}
									}
								}
							}
						}
					}
				}
			}
		}

		return patient;
	}

	@Override
	public void outputResults() {

		Timer timer = new Timer();
		progress = new Progress();

		//start counter
		timer.start();
		progress.init((allEventTypes.size() * allIndexDates.size() * (ageGroupDefinition.getCount() + 1)), "Computing Prevalence Rates");

		// Sort the index dates
		List<Integer> sortedIndexDates = new ArrayList<Integer>();
		for (int indexDate : allIndexDates)
			sortedIndexDates.add(indexDate);
		Collections.sort(sortedIndexDates);

		// Sort the event types
		List<String> sortedEventTypes = new ArrayList<String>();
		for (String eventType : allEventTypes)
			sortedEventTypes.add(eventType);
		Collections.sort(sortedEventTypes);

		for (PrevalenceDefinition prevalence : allPrevalenceDefinitions) {
			String prevalenceFileName = prevalenceFiles.get(prevalence);
			String label = prevalence.getLabel();

			for (String eventType : sortedEventTypes) {
				Set<Integer> usedIndexYears = new HashSet<Integer>();

				for (int indexDay : sortedIndexDates) {
					int indexKey = indexDay;
					String indexDate = DateUtilities.daysToDate(indexDay);

					int yearStart = prevalence.getPeriodStart(indexDay);
					if (((prevalence.getType() != PrevalenceDefinition.TYPE_CALENDARPOPULATION) && (prevalence.getType() != PrevalenceDefinition.TYPE_CALENDARTIME)) || usedIndexYears.add(yearStart)) {
						if ((prevalence.getType() == PrevalenceDefinition.TYPE_CALENDARPOPULATION) || (prevalence.getType() == PrevalenceDefinition.TYPE_CALENDARTIME)) {
							indexKey = yearStart;
							indexDate = Integer.toString(DateUtilities.getYearFromDays(yearStart));
						}

						double maleEvents = eventsOverallBag.getCount(new ExtendedMultiKey(label, indexKey, eventType, DataDefinition.MALE_GENDER));
						double femaleEvents = eventsOverallBag.getCount(new ExtendedMultiKey(label, indexKey, eventType, DataDefinition.FEMALE_GENDER));
						double males = -1;
						double females = -1;

						if (prevalence.getType() == PrevalenceDefinition.TYPE_CALENDARPOPULATION) {
							males = Math.round(subjectsOverallBag.getCount(new ExtendedMultiKey(label, indexKey, DataDefinition.MALE_GENDER)) / 2.0);
							females = Math.round(subjectsOverallBag.getCount(new ExtendedMultiKey(label, indexKey, DataDefinition.FEMALE_GENDER)) / 2.0);
						}
						else if (prevalence.getType() == PrevalenceDefinition.TYPE_CALENDARTIME) {
							males = timeOverallBag.getHistogramStats(new ExtendedMultiKey(label, indexKey, DataDefinition.MALE_GENDER)).getSum();
							females = timeOverallBag.getHistogramStats(new ExtendedMultiKey(label, indexKey, DataDefinition.FEMALE_GENDER)).getSum();
						}
						else {
							males = subjectsOverallBag.getCount(new ExtendedMultiKey(label, indexKey, DataDefinition.MALE_GENDER));
							females = subjectsOverallBag.getCount(new ExtendedMultiKey(label, indexKey, DataDefinition.FEMALE_GENDER));
						}

						double malePrevalence = maleEvents / males;
						double femalePrevalence = femaleEvents / females;
						double overallPrevalence = (maleEvents + femaleEvents) / (males + females);

						if ((males + females) >= minSubjectsPerRow) {
							String record = eventType;
							record += "," + indexDate;
							record += "," + "";
							record += "," + Integer.toString((int) males);
							record += "," + Integer.toString((int) females);
							record += "," + Integer.toString((int) (males + females));
							record += "," + Integer.toString((int) maleEvents);
							record += "," + Integer.toString((int) femaleEvents);
							record += "," + Integer.toString((int) (maleEvents + femaleEvents));
							record += "," + Double.toString(malePrevalence);
							record += "," + Double.toString(femalePrevalence);
							record += "," + Double.toString(overallPrevalence);

							Jerboa.getOutputManager().writeln(prevalenceFileName, record, true);

							if (prevalence.getType() == PrevalenceDefinition.TYPE_POINT) {
								if (!Double.isNaN(overallPrevalence)) {
									// Fill the prevalenceOverallMap: key=(Event, Date), value=Prevalence
									prevalenceOverallMap.put(new ExtendedMultiKey(eventType, indexDate), overallPrevalence);
									if (debug > 2) {
										Jerboa.getOutputManager().writeln(debugFileName, "prevalenceOverallMap,," + indexDate + "," + eventType + ",,," + Double.toString(overallPrevalence), true);
									}
								}
								if (!Double.isNaN(malePrevalence)) {
									// Fill the prevalenceOverallGenderMap: key=(Event, Date, Gender), value=Prevalence
									prevalenceOverallGenderMap.put(new ExtendedMultiKey(eventType, indexDate, DataDefinition.MALE_GENDER), malePrevalence);
									if (debug > 2) {
										Jerboa.getOutputManager().writeln(debugFileName, "prevalenceOverallGenderMap,," + indexDate + "," + eventType + ",," + Byte.toString(DataDefinition.MALE_GENDER) + "," + Double.toString(malePrevalence), true);
									}
								}
								if (!Double.isNaN(femalePrevalence)) {
									// Fill the prevalenceOverallGenderMap: key=(Event, Date, Gender), value=Prevalence
									prevalenceOverallGenderMap.put(new ExtendedMultiKey(eventType, indexDate, DataDefinition.FEMALE_GENDER), femalePrevalence);
									if (debug > 2) {
										Jerboa.getOutputManager().writeln(debugFileName, "prevalenceOverallGenderMap,," + indexDate + "," + eventType + ",," + Byte.toString(DataDefinition.FEMALE_GENDER) + "," + Double.toString(femalePrevalence), true);
									}
								}
							}
						}

						//refresh loop variable and progress
						progress.update();


						for (AgeGroup ageGroup : ageGroupDefinition.getAgeGroups()) {

							maleEvents = eventsAgegroupBag.getCount(new ExtendedMultiKey(label, indexKey, eventType, ageGroup.getLabel(), DataDefinition.MALE_GENDER));
							femaleEvents = eventsAgegroupBag.getCount(new ExtendedMultiKey(label, indexKey, eventType, ageGroup.getLabel(), DataDefinition.FEMALE_GENDER));
							males = -1;
							females = -1;

							if (prevalence.getType() == PrevalenceDefinition.TYPE_CALENDARPOPULATION) {
								males = subjectsAgegroupBag.getCount(new ExtendedMultiKey(label, indexKey, ageGroup.getLabel(), DataDefinition.MALE_GENDER)) / 2;
								females = subjectsAgegroupBag.getCount(new ExtendedMultiKey(label, indexKey, ageGroup.getLabel(), DataDefinition.FEMALE_GENDER)) / 2;
							}
							else if (prevalence.getType() == PrevalenceDefinition.TYPE_CALENDARTIME) {
								males = timeAgegroupBag.getHistogramStats(new ExtendedMultiKey(label, indexKey, ageGroup.getLabel(), DataDefinition.MALE_GENDER)).getSum();
								females = timeAgegroupBag.getHistogramStats(new ExtendedMultiKey(label, indexKey, ageGroup.getLabel(), DataDefinition.FEMALE_GENDER)).getSum();
							}
							else {
								males = subjectsAgegroupBag.getCount(new ExtendedMultiKey(label, indexKey, ageGroup.getLabel(), DataDefinition.MALE_GENDER));
								females = subjectsAgegroupBag.getCount(new ExtendedMultiKey(label, indexKey, ageGroup.getLabel(), DataDefinition.FEMALE_GENDER));
							}

							malePrevalence = maleEvents / males;
							femalePrevalence = femaleEvents / females;
							overallPrevalence = (maleEvents + femaleEvents) / (males + females);

							if ((males + females) >= minSubjectsPerRow) {

								String record = eventType;
								record += "," + indexDate;
								record += "," + ageGroup.getLabel();
								record += "," + males;
								record += "," + females;
								record += "," + (males + females);
								record += "," + maleEvents;
								record += "," + femaleEvents;
								record += "," + (maleEvents + femaleEvents);
								record += "," + malePrevalence;
								record += "," + femalePrevalence;
								record += "," + overallPrevalence;

								Jerboa.getOutputManager().writeln(prevalenceFileName, record, true);

								if (prevalence.getType() == PrevalenceDefinition.TYPE_POINT) {
									if (!Double.isNaN(overallPrevalence)) {
										// Fill the prevalenceMap: key=(Event, Date, AgeGroup), value=Prevalence
										prevalenceMap.put(new ExtendedMultiKey(eventType, indexDate, ageGroup.getLabel()), overallPrevalence);
										if (debug > 2) {
											Jerboa.getOutputManager().writeln(debugFileName, "prevalenceMap,," + indexDate + "," + eventType + "," + ageGroup.getLabel() + ",," + Double.toString(overallPrevalence), true);
										}
									}
									if (!Double.isNaN(malePrevalence)) {
										// Fill the prevalenceGenderMap: key=(Event, Date, AgeGroup, Gender), value=Prevalence
										prevalenceGenderMap.put(new ExtendedMultiKey(eventType, indexDate, ageGroup.getLabel(), DataDefinition.MALE_GENDER), malePrevalence);
										if (debug > 2) {
											Jerboa.getOutputManager().writeln(debugFileName, "prevalenceGenderMap,," + indexDate + "," + eventType + "," + ageGroup.getLabel() + "," + Byte.toString(DataDefinition.MALE_GENDER) + "," + Double.toString(malePrevalence), true);
										}
									}
									if (!Double.isNaN(femalePrevalence)) {
										// Fill the prevalenceGenderMap: key=(Event, Date, AgeGroup, Gender), value=Prevalence
										prevalenceGenderMap.put(new ExtendedMultiKey(eventType, indexDate, ageGroup.getLabel(), DataDefinition.FEMALE_GENDER), femalePrevalence);
										if (debug > 2) {
											Jerboa.getOutputManager().writeln(debugFileName, "prevalenceGenderMap,," + indexDate + "," + eventType + "," + ageGroup.getLabel() + "," + Byte.toString(DataDefinition.FEMALE_GENDER) + "," + Double.toString(femalePrevalence), true);
										}
									}
								}
							}

							//refresh loop variable and progress
							progress.update();
						}
					}
				}
			}
		}

		for (int indexDay : sortedIndexDates) {
			HistogramStats historyStats = historyBag.getHistogramStats(new ExtendedMultiKey (indexDay, Wildcard.INTEGER()));
			HistogramStats followUpStats = followUpBag.getHistogramStats(new ExtendedMultiKey (indexDay, Wildcard.INTEGER()));
			Jerboa.getOutputManager().writeln(patientTimeFileName,DateUtilities.daysToDate(indexDay)+","+StringUtilities.format(historyStats.getMean()/365.25)+","+
			StringUtilities.format(historyStats.getStdDev()/365.25)+","+
			StringUtilities.format(historyStats.getMin()/365.25)+","+
			StringUtilities.format(historyStats.getMax()/365.25)+","+
			StringUtilities.format(followUpStats.getMean()/365.25)+","+
			StringUtilities.format(followUpStats.getStdDev()/365.25)+","+
			StringUtilities.format(followUpStats.getMin()/365.25)+","+
			StringUtilities.format(followUpStats.getMax()/365.25),true);
		}

		for (PrevalenceDefinition prevalence : allPrevalenceDefinitions) {
			if (prevalenceFiles.containsKey(prevalence)) {
				Jerboa.getOutputManager().closeFile(prevalenceFiles.get(prevalence));
			}
		}
		Jerboa.getOutputManager().closeFile(patientTimeFileName);

		if (debug > 0) {
			Jerboa.getOutputManager().closeFile(debugFileName);
		}

		//make sure the progress bar is closed
		progress.close();

		// Display execution timers
		timer.stop();
		timer.displayTotal("Prevalence calculation done in");
	}



	@Override
	public void displayGraphs() {

		//let the user know if there is no data
		Progress progress = new Progress();
		Timer timer = new Timer();

		// Event, Date
		if (prevalenceOverallMap != null && prevalenceOverallGenderMap != null){


			MultiKeyMap subMap;
			Set<Object> indexDates;
			List<Object> values;
			TreeMap<Object, Object> data;
			Iterator<Object> it;

			Set<Object> events = prevalenceOverallMap.getKeyValues(0);

			timer.start();
			progress.init(events.size(), "Creating overall prevalence plots");
			List<Plot> list = new ArrayList<Plot>();

			for (Object event : events) {
				list = new ArrayList<Plot>();

				//Event, Date
				subMap = prevalenceOverallMap.getSubMap(0, event);
				if (subMap != null && !subMap.isEmpty()) {
					//total population
					indexDates = subMap.getKeyValues(1);
					values = subMap.getValues(new ExtendedMultiKey(event, Wildcard.STRING()));
					data = new TreeMap<Object,Object>();
					it = values.iterator();
					for (Object indexDate : indexDates)
						data.put(indexDate, (double)it.next()*100);

					Plot plot = new LinePlotDS.Builder("Prevalence "+event+" - Total population")
							.data(data).XLabel("Index date").YLabel("Prevalence (%)").showLegend(false)
							.build();
					list.add(plot);
				}

				// Event, Date, Gender
				subMap = prevalenceOverallGenderMap.getSubMap(0, event);
				if (subMap != null && !subMap.isEmpty()) {
					//females
					MultiKeyMap subSubMap = prevalenceOverallGenderMap.getSubMap(0, event).getSubMap(2, DataDefinition.FEMALE_GENDER);
					if (subSubMap != null && !subSubMap.isEmpty()) {
						indexDates = subSubMap.getKeyValues(1);
						values = subMap.getValues(new ExtendedMultiKey(event, Wildcard.STRING(), DataDefinition.FEMALE_GENDER));
						data = new TreeMap<Object,Object>();
						it = values.iterator();
						for (Object indexDate : indexDates)
							data.put(indexDate, (double)it.next()*100);

						Plot plot = new LinePlotDS.Builder("Prevalence "+event+" - Female population")
								.data(data).XLabel("Index date").YLabel("Prevalence (%)").showLegend(false)
								.build();
						list.add(plot);
					}

					//males
					subSubMap = prevalenceOverallGenderMap.getSubMap(0, event).getSubMap(2, DataDefinition.MALE_GENDER);
					if (subSubMap != null && !subSubMap.isEmpty()) {
						indexDates = subSubMap.getKeyValues(1);
						values = subMap.getValues(new ExtendedMultiKey(event, Wildcard.STRING(), DataDefinition.MALE_GENDER));
						data = new TreeMap<Object,Object>();
						it = values.iterator();
						for (Object indexDate : indexDates)
							data.put(indexDate, (double)it.next()*100);

						Plot plot = new LinePlotDS.Builder("Prevalence "+event+" - Male population")
								.data(data).XLabel("Index date").YLabel("Prevalence (%)").showLegend(false)
								.build();
						list.add(plot);
					}
				}

				Graphs.addPlots(this.title, "Prevalence "+event+" over time", list);
				progress.update();
			}

			progress.close();
		}

		// Event, Date, AgeGroup, Prevalence
		if (prevalenceMap != null) {
			Set<Object> events = prevalenceMap.getKeyValues(0);
			progress.init(events.size(), "Creating prevalence per age group plots");
			for (Object event : events) {
				List<Plot> list = new ArrayList<Plot>();
				MultiKeyMap subMap = prevalenceMap.getSubMap(0, event);
				if (subMap != null && !subMap.isEmpty()) {
					Set<Object> indexDates = subMap.getKeyValues(1);
					for (Object indexDate : indexDates) {
						MultiKeyMap subSubMap = subMap.getSubMap(1, indexDate);
						if (subSubMap != null && !subSubMap.isEmpty()) {
							Set<Object> ageGroups = subSubMap.getKeyValues(2);
							List<Object> values = subMap.getValues(new ExtendedMultiKey(event, indexDate, Wildcard.STRING()));
							TreeMap<Object, Object> data = new TreeMap<Object,Object>();
							Iterator<Object> it = values.iterator();
							for (Object ageGroup : ageGroups)
								data.put(ageGroup, (double)it.next()*100);

							Plot plot = new LinePlotDS.Builder("Prevalence "+event+" at "+indexDate)
									.data(data).XLabel("Age group").YLabel("Prevalence (%)").showLegend(false)
									.build();

							list.add(plot);
						}
					}
				}

				Graphs.addPlots(this.title, "Prevalence "+event+" in age groups", list);
				progress.update();
			}

			progress.close();
		}

		//display execution timers
		timer.stop();
		timer.displayTotal("Graphs done in");
	}


	private class PrevalenceDefinition {
		private static final int TYPE_POINT              = 0;
		private static final int TYPE_RELATIVE           = 1;
		private static final int TYPE_CALENDARPOPULATION = 2;
		private static final int TYPE_CALENDARTIME       = 3;

		private byte type;
		private String label;
		private int start;
		private int end;
		private boolean definitionOK = false;


		public PrevalenceDefinition(String definition) {
			String[] definitionSplit = definition.split(";");
			if (definitionSplit.length > 1) {
				label = "_" + definitionSplit[1].trim();
				if (label.equals("")) {
					Logging.add("Label may not be empty in prevalence definition " + definition);
				}
				if (definitionSplit[0].equals("CALENDARPOPULATION")) {
					type = TYPE_CALENDARPOPULATION;
					definitionOK = true;
				}
				else if (definitionSplit[0].equals("CALENDARTIME")) {
					type = TYPE_CALENDARTIME;
					definitionOK = true;
				}
				else if (definitionSplit[0].equals("RELATIVE")) {
					type = TYPE_RELATIVE;
					if ((definitionSplit.length > 2) && (!definitionSplit[2].equals(""))) {
						try {
							start = Integer.parseInt(definitionSplit[2]);
						}
						catch (NumberFormatException e) {
							Logging.add("Period start should be an integer in prevalence definition " + definition);
						}
					}
					else {
						start = Integer.MIN_VALUE;
					}
					if ((definitionSplit.length > 3) && (!definitionSplit[3].equals(""))) {
						try {
							end = Integer.parseInt(definitionSplit[3]);
						}
						catch (NumberFormatException e) {
							Logging.add("Period end should be an integer in prevalence definition " + definition);
						}
					}
					else {
						start = Integer.MAX_VALUE;
					}
					if (start >= end) {
						Logging.add("Period start should be less than end in prevalence definition " + definition);
					}
					else {
						definitionOK = true;
					}
				}
				else {
					Logging.add("Unknown type in prevalence definition " + definition);
				}
			}
			else if (definition.equals("POINT")) {
				label = "";
				definitionOK = true;
			}
			else {
				Logging.add("Incorrect period prevalence definition " + definition);
			}
		}


		public boolean isOK() {
			return definitionOK;
		}


		public String getLabel() {
			return label;
		}


		public byte getType() {
			return type;
		}


		public int getPeriodStart(int indexDate) {
			if (type == TYPE_POINT) {
				return indexDate;
			}
			else if (type == TYPE_RELATIVE) {
				return (start == Integer.MIN_VALUE ? DateUtilities.dateToDays(new int[] { allIndexYears.get(indexDate), 1, 1}) : indexDate + start);
			}
			else {
				return DateUtilities.dateToDays(new int[] { allIndexYears.get(indexDate), 1, 1});
			}
		}


		public int getPeriodEnd(int indexDate) {
			if (type == TYPE_POINT) {
				return indexDate;
			}
			else if (type == TYPE_RELATIVE) {
				return (end == Integer.MAX_VALUE ? DateUtilities.dateToDays(new int[] { allIndexYears.get(indexDate) + 1, 1, 1}) : indexDate + end);
			}
			else {
				return DateUtilities.dateToDays(new int[] { allIndexYears.get(indexDate) + 1, 1, 1});
			}
		}
	}


	/**
	 * @param args
	 */
	public static void main(String[] args) {

		PatientObjectCreator poc = new PatientObjectCreator();

		String testDataPath = "D:/Temp/Jerboa/Prevalence Rate/Test 3/Jerboa/";
		FilePaths.WORKFLOW_PATH = "D:/Temp/Jerboa/Prevalence Rate/Test 3/Jerboa/";
		FilePaths.INTERMEDIATE_PATH = "D:/Temp/Jerboa/Prevalence Rate/Test 3/Jerboa/Intermediate/";
		FilePaths.LOG_PATH = FilePaths.WORKFLOW_PATH + "Log/";
		String patientsFile = testDataPath + "Patients.txt";
		String eventsFile = testDataPath + "Events.txt";
		Logging.prepareOutputLog();

		PopulationDefinition populationDefinition = new PopulationDefinition();

		populationDefinition.setIntermediateFileName(FilePaths.INTERMEDIATE_PATH + "populationDefinition.csv");
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

			cohortDefinition.setIntermediateFileName(FilePaths.INTERMEDIATE_PATH + "cohortDefinition.csv");
			cohortDefinition.intermediateFiles = true;
			cohortDefinition.intermediateStats = true;

			if (cohortDefinition.init()) {

				Prevalence prevalence = new Prevalence();

				prevalence.debug = 1;
				prevalence.setOutputFileNamesInDebug(FilePaths.INTERMEDIATE_PATH + "prevalence_Debug.csv");
				prevalence.setIntermediateFileName(FilePaths.INTERMEDIATE_PATH + "prevalence.csv");
				prevalence.outputFileName = FilePaths.WORKFLOW_PATH + "prevalence.csv";
				prevalence.intermediateFiles = true;

				prevalence.indexDates.add("20060101");
				prevalence.ageGroups.add("0;5;00-04");
				prevalence.ageGroups.add("5;10;05-09");
				prevalence.ageGroups.add("10;15;10-14");
				prevalence.ageGroups.add("15;20;15-19");
				prevalence.ageGroups.add("20;25;20-24");
				prevalence.ageGroups.add("25;30;25-29");
				prevalence.ageGroups.add("30;35;30-34");
				prevalence.ageGroups.add("35;40;35-39");
				prevalence.ageGroups.add("40;45;40-44");
				prevalence.ageGroups.add("45;50;45-49");
				prevalence.ageGroups.add("50;55;50-54");
				prevalence.ageGroups.add("55;60;55-59");
				prevalence.ageGroups.add("60;65;60-64");
				prevalence.ageGroups.add("65;70;65-69");
				prevalence.ageGroups.add("70;75;70-74");
				prevalence.ageGroups.add("75;80;75-79");
				prevalence.ageGroups.add("80;85;80-84");
				prevalence.ageGroups.add("85;999;85-");
				prevalence.minSubjectsPerRow = 0;
				prevalence.intermediateFiles = true;
				prevalence.intermediateStats = true;


				if (prevalence.init()) {

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
										prevalence.process(cohortPatient);
									}
								}
							}

							Logging.addNewLine();

							prevalence.outputResults();

							cohortDefinition.flushRemainingData();
							cohortDefinition.outputResults();

							populationDefinition.flushRemainingData();
							populationDefinition.outputResults();

							timer.stop();

							System.out.println("Prevalence rates of "+ Integer.toString(patients.size()) +" patients run in: "+timer);
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
