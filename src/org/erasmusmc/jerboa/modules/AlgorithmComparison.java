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

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.apache.commons.collections.bidimap.DualHashBidiMap;
import org.apache.commons.lang3.StringUtils;
import org.erasmusmc.jerboa.Jerboa;
import org.erasmusmc.jerboa.config.DataDefinition;
import org.erasmusmc.jerboa.config.Parameters;
import org.erasmusmc.jerboa.dataClasses.Event;
import org.erasmusmc.jerboa.dataClasses.Patient;
import org.erasmusmc.jerboa.engine.Module;
import org.erasmusmc.jerboa.utilities.AgeGroupDefinition;
import org.erasmusmc.jerboa.utilities.AgeGroupDefinition.AgeGroup;
import org.erasmusmc.jerboa.utilities.DateUtilities;
import org.erasmusmc.jerboa.utilities.InputFileUtilities;
import org.erasmusmc.jerboa.utilities.Logging;
import org.erasmusmc.jerboa.utilities.Progress;
import org.erasmusmc.jerboa.utilities.StringUtilities;
import org.erasmusmc.jerboa.utilities.Timer;

/**
 * The algorithm comparison module. Compare different algorithms to extract events from a database.
 * The algorithm used for the extraction of the event is in its Code part.
 * @author bmosseveld
 *
 */
public class AlgorithmComparison extends Module {

	/**
	 * MODULE PARAMETERS
	 */

	/**
	 * The list of dates as yyyymmdd for the point estimate of prevalence.
	 * If empty then the first of July of every year is taken.
	 * For example: 20130101
	 */
	public List<String> indexDates = new ArrayList<String>();

	/**
	 * The list of event types and their code values (algorithms. Each event definition is a string
	 * of the event type followed by all the possible codes separated by comma's
	 * followed by how far in history it should look for the event.
	 * If not specified it just takes all events from the total history of the patient
	 * and no events are counted in the follow up.
	 * Format:
	 *
	 *   Event type;Code1, ... CodeN;History;FollowUp
	 *
	 * where
	 *
	 *   Event type         The type of event to count.
	 *   Code1, ... CodeN   The codes for which to count the specified event type.
	 *                      If no codes are specified all codes are allowed.
	 *   History            The period before the index date where to look for the event.
	 *
	 *                        <empty>        If not specified all history from the index date (exclusive) of the patient is used.
	 *                        CohortStart    History is taken from index date (exclusive) until cohort start (inclusive).
	 *                        <number>       The number of days (inclusive index date - days) before the index date (exclusive) where to look for the event.
	 *
	 *   FollowUp           The period after the index date (inclusive) where the event is counted.
	 *
	 *                        NO             If not the specified event/codes are not counted in follow up time from the index date.
	 *                        <empty>        If not the specified event/codes are not counted all time from the index date (inclusive).
	 *                        CohortEnd      The specified event/codes are counted in the period from index date (inclusive) until cohort end (exclusive).
	 *                        <number>       The number of days (exclusive index date + days) from the index date (inclusive) where to the event is counted.
	 *
	 * Example: T2DM;Code,Medication,FreeText;CohortStart;365
	 */
	public List<String> eventDefinitions = new ArrayList<String>();

	/**
	 * The list of age ranges used when coding patient age. Each range should be represented as
	 * three semicolon separated values:
	 *
	 * - The start year since birth
	 * - The end year since birth (exclusive)
	 * - The label of age range
	 *
	 * For example: 0;5;0-4
	 */
	public List<String> ageGroups = new ArrayList<String>();

	/**
	 * The minimum number of subjects in a row of the resulting aggregated table. Rows with fewer subjects
	 * are deleted.
	 * Be aware that when set to 0 the output may get very big.
	 */
	public int minSubjectsPerRow;

	/**
	 * When true the real age of the patient at indexDate is used.
	 * Otherwise the age is calculated as: floor((indexYear - birthYear) / 365.25)
	 * This method is used in the Stata program of Rosa Gini.
	 */
	public boolean useRealAge;


	/* Local variables */

	// Debug
	private boolean debug = false;
	private String debugFileName = "";

	// Cohort counters
	int cohortMales = 0;
	int cohortFemales = 0;
	Map<Integer, Integer> activeMales = null;
	Map<Integer, Integer> activeFemales = null;

	// The list containing the index dates in days
	Set<Integer> indexDays = null;

	// Age group definition
	private AgeGroupDefinition ageGroupDefinition = null;

	// List of all index dates
	private Set<Integer> allIndexDates = null;
	private List<Integer> sortedIndexDates = null;

	// Lists of all event types and their codes
	private Map<String, List<String>> allEventTypes = null;
	private Map<String, Boolean> codesSpecified = null;
	private List<String> sortedEventTypes = null;
	private Map<String, String> eventTypeHistory = null;
	private Map<String, String> eventTypeFollowUp = null;

	// Maps for storing the results
	private Map<String, Map<Integer, Map<String, Integer>>> eventsOverallMap = null;        // Event, Date, Gender, CodeValue
	private Map<String, Map<Integer, Map<String, Integer>>> eventsAgegroupMap = null;       // Event, Date, Gender, AgeGroup, CodeValue

	private Set<String> intermediateFileNames = new HashSet<String>();


	@Override
	public void setNeededFiles() {
		setRequiredFile(DataDefinition.PATIENTS_FILE);
		setRequiredFile(DataDefinition.EVENTS_FILE);
	}


	@Override
	public void setNeededExtendedColumns() {
		setRequiredExtendedColumn(DataDefinition.EVENTS_FILE,"code");
	}


	@Override
	public void setNeededNumericColumns() { }


	@Override
	public boolean init() {
		boolean initOK = true;

		// Create the debug file
		debug = (debug || Jerboa.isInDebugMode);
		if (debug) {
			debugFileName = StringUtilities.addSuffixToFileName(intermediateFileName, "_Debug.csv", true);
			Jerboa.getOutputManager().addFile(debugFileName, 100);
			Jerboa.getOutputManager().writeln(debugFileName, "Bag,PatientID,Date,EventType,CodeValue,AgeGroup,Gender,Value", true);
		}

		// Initialize cohort counters
		cohortMales = 0;
		cohortFemales = 0;
		activeMales = new HashMap<Integer, Integer>();
		activeFemales = new HashMap<Integer, Integer>();

		// Create the list containing the index dates in days
		indexDays = new HashSet<Integer>();

		// Parse age group definition
		ageGroupDefinition = new AgeGroupDefinition(ageGroups);

		// Create the lists for all index dates
		allIndexDates = new HashSet<Integer>();
		sortedIndexDates = new ArrayList<Integer>();

		// Create the list for all event types
		allEventTypes = new HashMap<String, List<String>>();
		codesSpecified = new HashMap<String, Boolean>();
		sortedEventTypes = new ArrayList<String>();
		eventTypeHistory = new HashMap<String, String>();
		eventTypeFollowUp = new HashMap<String, String>();
		if (eventDefinitions.size() > 0) {
			for (String eventDefinition : eventDefinitions) {
				String[] eventDefinitionSplit = eventDefinition.split(";");
				if (eventDefinitionSplit[0].equals("")) {
					Logging.add("Event type may not be empty in event definition " + eventDefinition);
					initOK = false;
				}
				else if (sortedEventTypes.contains(eventDefinitionSplit[0])) {
					Logging.add("Event type may not be specified multiple times in event definition " + eventDefinition);
					initOK = false;
				}
				else {
					codesSpecified.put(eventDefinitionSplit[0], false);
					List<String> codeList = new ArrayList<String>();
					allEventTypes.put(eventDefinitionSplit[0], codeList);

					// Get the event codes (algorithms)
					if (eventDefinitionSplit.length > 1) {
						sortedEventTypes.add(eventDefinitionSplit[0]);
						if (!eventDefinitionSplit[1].equals("")) {
							for (String code : eventDefinitionSplit[1].split(",")) {
								if (!code.equals("")) {
									codeList.add(code);
									codesSpecified.put(eventDefinitionSplit[0], true);
								}
							}
						}
					}

					// Get the history definition
					if (eventDefinitionSplit.length > 2) {
						String history = eventDefinitionSplit[2];
						if ((!history.equals("")) && (!history.equals("COHORTSTART"))) {
							try {
								int historyValue = Integer.parseInt(eventDefinitionSplit[2]);
								if (historyValue <= 0) {
									Logging.add("History (" + history + ") should be <empty>, CohortStart, or a positive integer in event definition " + eventDefinition);
									initOK = false;
								}
								else {
									eventTypeHistory.put(eventDefinitionSplit[0], history);
								}
							}
							catch (NumberFormatException e) {
								Logging.add("History (" + history + ") should be <empty>, CohortStart, or a positive integer in event definition " + eventDefinition);
								initOK = false;
							}
						}
						else {
							eventTypeHistory.put(eventDefinitionSplit[0], history);
						}
					}

					// Get the follow up definition
					if (eventDefinitionSplit.length > 3) {
						String followUp = eventDefinitionSplit[3];
						if ((!followUp.equals("")) && (!followUp.equals("NO")) && (!followUp.equals("COHORTEND"))) {
							try {
								int followUpValue = Integer.parseInt(eventDefinitionSplit[3]);
								if (followUpValue <= 0) {
									Logging.add("Follow up (" + followUp + ") should be <empty>, No, CohortEnd, or a positive integer in event definition " + eventDefinition);
									initOK = false;
								}
								else {
									eventTypeFollowUp.put(eventDefinitionSplit[0], followUp);
								}
							}
							catch (NumberFormatException e) {
								Logging.add("Follow up (" + followUp + ") should be <empty>, No, CohortEnd, or a positive integer in event definition " + eventDefinition);
								initOK = false;
							}
						}
						else {
							eventTypeFollowUp.put(eventDefinitionSplit[0], followUp);
						}
					}
				}
			}
		}
		else {
			// Get all event types found in the events file
			DualHashBidiMap availableEventTypes = InputFileUtilities.getEventTypes();
			for (Object item : availableEventTypes.keySet()){
				String eventType = availableEventTypes.get(item).toString();
				List<String> codeList = new ArrayList<String>();
				allEventTypes.put(eventType, codeList);
				codesSpecified.put(eventType, false);
				sortedEventTypes.add(eventType);
				eventTypeHistory.put(eventType, "");
				eventTypeFollowUp.put(eventType, "NO");
			}
			// Sort the event types
			Collections.sort(sortedEventTypes);
		}

		// Create the maps for storing the results
		//
		//   Map<eventType, Map<indexDate, Map<key, codeValue>>> = count
		//
		// where key is:
		//
		//   gender
		//   gender,ageGroup
		//
		eventsOverallMap = new HashMap<String, Map<Integer, Map<String, Integer>>>();
		eventsAgegroupMap = new HashMap<String, Map<Integer, Map<String, Integer>>>();

		return initOK;
	}


	@Override
	public Patient process(Patient patient) {
		if ((patient != null) && patient.isInCohort()) {

			if (patient.gender == DataDefinition.MALE_GENDER) {
				cohortMales++;
			}
			else if (patient.gender == DataDefinition.FEMALE_GENDER) {
				cohortFemales++;
			}

			// Map containing the list of events of each type/code combination of the patient.
			Map<String, Map<String, List<Event>>> eventTypeCodeEvents = new HashMap<String, Map<String, List<Event>>>();

			List<Event> events = patient.getEvents();
			for (Event event : events) {
				List<String> codeList = allEventTypes.get(event.getType());
				if (codeList != null) {
					if (!codeList.contains(event.getCode())) {
						if (!codesSpecified.get(event.getType())) {
							codeList.add(0, event.getCode());

							Map<String, List<Event>> codeEvents = eventTypeCodeEvents.get(event.getType());
							if (codeEvents == null) {
								codeEvents = new HashMap<String, List<Event>>();
								eventTypeCodeEvents.put(event.getType(), codeEvents);
							}
							List<Event> eventList = codeEvents.get(event.getCode());
							if (eventList == null) {
								eventList = new ArrayList<Event>();
								codeEvents.put(event.getCode(), eventList);
							}
							eventList.add(event);
						}
					}
					else {
						Map<String, List<Event>> codeEvents = eventTypeCodeEvents.get(event.getType());
						if (codeEvents == null) {
							codeEvents = new HashMap<String, List<Event>>();
							eventTypeCodeEvents.put(event.getType(), codeEvents);
						}
						List<Event> eventList = codeEvents.get(event.getCode());
						if (eventList == null) {
							eventList = new ArrayList<Event>();
							codeEvents.put(event.getCode(), eventList);
						}
						eventList.add(event);
					}
				}
			}

			// If no index dates are specified take the first of July of each year where the
			// patient is inside the cohort.
			if (indexDates.size() == 0) {
				int cohortStartYear = DateUtilities.getYearFromDays(patient.cohortStartDate);
				int cohortEndYear = DateUtilities.getYearFromDays(patient.cohortEndDate);
				for (int year = cohortStartYear; year <= cohortEndYear; year++) {
					String indexDate = Integer.toString(year) + "0701";
					int indexDay = DateUtilities.dateToDays(indexDate, DateUtilities.DATE_ON_YYYYMMDD);
					if (patient.dateInCohort(indexDay))
						indexDays.add(indexDay);
				}
			}
			else {
				for (String date : indexDates) {
					indexDays.add(DateUtilities.dateToDays(date, DateUtilities.DATE_ON_YYYYMMDD));
				}
			}

			// For each index date ount the subjects
			for (int date : indexDays) {
				if (allIndexDates.add(date)) {
					sortedIndexDates.add(date);
				}

				if (patient.dateInCohort(date)) {

					if (patient.gender == DataDefinition.MALE_GENDER) {
						if (!activeMales.containsKey(date)) {
							activeMales.put(date, 0);
						}
						activeMales.put(date, activeMales.get(date) + 1);
					}
					else if (patient.gender == DataDefinition.FEMALE_GENDER) {
						if (!activeFemales.containsKey(date)) {
							activeFemales.put(date, 0);
						}
						activeFemales.put(date, activeFemales.get(date) + 1);
					}

					// Get the age groups
					int age = -1;
					if (useRealAge) {
						age = patient.getAgeAtDateInYears(date);
					}
					else {
						// Age the way Rosa Gini computes it in Stata.
						age = (int) Math.floor((date - patient.getBirthDate())/365.25);
					}
					List<AgeGroup> ageGroups = ageGroupDefinition.getAgeGroups(age);

					// Count the subjects
					for (String eventType : allEventTypes.keySet()) {
						int historyStart = getHistoryStart(patient, date, eventType);
						int followUpEnd = getFollowUpEnd(patient, date, eventType);

						List<String> codeList = allEventTypes.get(eventType);
						if (codeList != null) {
							long codeValue = 0;
							for (String code : codeList) {
								List<Event> eventTypeCodeEventList = new ArrayList<Event>();
								if (eventTypeCodeEvents.containsKey(eventType)) {
									if (eventTypeCodeEvents.get(eventType).containsKey(code)) {
										eventTypeCodeEventList = eventTypeCodeEvents.get(eventType).get(code);
									}
								}

								codeValue = (codeValue * 2); // Shift left for history value
								if (eventTypeCodeEventList != null) {
									int eventNr = 0;

									// Get history event
									while (eventNr < eventTypeCodeEventList.size()) {
										Event event = eventTypeCodeEventList.get(eventNr);
										if (event.getDate() < historyStart) {
											eventNr++;
										}
										else if (event.getDate() < date) {
											codeValue += 1;
											eventNr++;
											break;
										}
										else {
											break;
										}
									}

									// Get follow up event if needed
									if (followUpEnd >= 0) {
										codeValue = (codeValue * 2); // Shift left for follow up value

										while (eventNr < eventTypeCodeEventList.size()) {
											Event event = eventTypeCodeEventList.get(eventNr);
											if (event.getDate() < date) {
												eventNr++;
											}
											else if (event.getDate() < followUpEnd) {
												codeValue += 1;
												break;
											}
											else {
												break;
											}
										}
									}

								}
								else {
									if (followUpEnd >= 0) {
										codeValue = (codeValue * 2); // Shift left for follow up value
									}
								}
							}

							addEventCodeValue(date, eventType, patient.gender, ageGroups, codeValue);

							if ((!Jerboa.unitTest) && intermediateFiles) {
								String fileName = intermediateFileName.substring(0, intermediateFileName.length() - 4) + "_" + eventType + ".csv";
								boolean fileExists = true;
								if (!intermediateFileNames.contains(fileName)) {
									fileExists = false;
									if (Jerboa.getOutputManager().addFile(fileName, 100)) {
										intermediateFileNames.add(fileName);
										String header = "Subject";
										header += "," + "EventType";
										header += "," + "IndexDate";
										header += "," + "Gender";
										header += "," + "AgeGroup";
										for (String code : codeList) {
											header += "," + code;
											if (!eventTypeFollowUp.get(eventType).equals("NO")) {
												header += "," + code + "_fup";
											}
										}
										Jerboa.getOutputManager().writeln(fileName, header, true);
										fileExists = true;
									}
								}

								if (fileExists) {
									String bitStringPrefix = "";
									for (int nr = 0; nr < (codeList.size() * (eventTypeFollowUp.get(eventType).equals("NO") ? 1 : 2)); nr++) {
										bitStringPrefix += "0";
									}

									String binaryCodeValue = bitStringPrefix + Long.toBinaryString(codeValue);
									String codeFields = "";
									for (int bitNr = 1; bitNr <= codeList.size() * (eventTypeFollowUp.get(eventType).equals("NO") ? 1 : 2); bitNr++) {
										String bit = binaryCodeValue.substring(binaryCodeValue.length() - bitNr, binaryCodeValue.length() - bitNr + 1);
										codeFields = "," + bit + codeFields;
									}
									for (AgeGroup ageGroup : ageGroups) {
										String record = patient.getPatientID();
										record += "," + eventType;
										record += "," + DateUtilities.daysToDate(date);
										record += "," + Patient.convertGender(patient.gender);
										record += "," + ageGroup.getLabel();
										record += codeFields;
										Jerboa.getOutputManager().writeln(fileName, record, true);
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


	private void addEventCodeValue(int date, String eventType, byte gender, List<AgeGroup> ageGroups, long codeValue) {
		String baseKey = Patient.convertGender(gender);
		String codeValueKey = "," + Long.toString(codeValue);

		Map<Integer, Map<String, Integer>> eventTypeMap = eventsOverallMap.get(eventType);
		if (eventTypeMap == null) {
			eventTypeMap = new HashMap<Integer, Map<String, Integer>>();
			eventsOverallMap.put(eventType, eventTypeMap);
		}
		Map<String, Integer> dateMap = eventTypeMap.get(date);
		if (dateMap == null) {
			dateMap = new HashMap<String, Integer>();
			eventTypeMap.put(date, dateMap);
		}
		String key = baseKey + codeValueKey;
		Integer count = dateMap.get(key);
		if (count == null) {
			count = 0;
		}
		dateMap.put(key, count + 1);


		eventTypeMap = eventsAgegroupMap.get(eventType);
		if (eventTypeMap == null) {
			eventTypeMap = new HashMap<Integer, Map<String, Integer>>();
			eventsAgegroupMap.put(eventType, eventTypeMap);
		}
		dateMap = eventTypeMap.get(date);
		if (dateMap == null) {
			dateMap = new HashMap<String, Integer>();
			eventTypeMap.put(date, dateMap);
		}
		for (AgeGroup ageGroup : ageGroups) {
			String ageGroupKey = baseKey + "," + ageGroup.getLabel() + codeValueKey;

			count = dateMap.get(ageGroupKey);
			if (count == null) {
				count = 0;
			}
			dateMap.put(ageGroupKey, count + 1);
		}
	}


	@Override
	public void outputResults() {

		Timer timer = new Timer();
		progress = new Progress();

		//start counter
		timer.start();
		progress.init((allEventTypes.size() * allIndexDates.size()), "Computing Prevalence Rates");

		// Sort the index dates
		Collections.sort(sortedIndexDates);

		for (String eventType : sortedEventTypes) {

			String[] outputFileNameSplit = (outputFileName + "_X").split("_");
			for (int partNr = outputFileNameSplit.length - 1; partNr > outputFileNameSplit.length - 4; partNr--) {
				outputFileNameSplit[partNr] = outputFileNameSplit[partNr - 1];
			}
			outputFileNameSplit[outputFileNameSplit.length - 4] = eventType;
			String fileName = StringUtils.join(outputFileNameSplit, "_");

			List<String> codeList = allEventTypes.get(eventType);

			String bitStringPrefix = "";
			for (int nr = 0; nr < (codeList.size() * (eventTypeFollowUp.get(eventType).equals("NO") ? 1 : 2)); nr++) {
				bitStringPrefix += "0";
			}

			Jerboa.getOutputManager().addFile(fileName);

			String record = "Database";
			record += "," + "EventType";
			record += "," + "IndexDate";
			record += "," + "Gender";
			record += "," + "AgeGroup";
			record += "," + "NumberOfSubjects";
			for (String code : codeList) {
				record += "," + code + "_bef";
				if (!eventTypeFollowUp.get(eventType).equals("NO")) {
					record += "," + code + "_fup";
				}
			}

			Jerboa.getOutputManager().writeln(fileName, record, true);

			for (int indexDay : sortedIndexDates) {
				String indexDate = DateUtilities.daysToDate(indexDay);

				if (minSubjectsPerRow > 0) {
					Map<Integer, Map<String, Integer>> eventTypeMap = eventsOverallMap.get(eventType);
					if (eventTypeMap != null) {
						Map<String, Integer> dateMap = eventTypeMap.get(indexDay);
						if (dateMap != null) {
							for (String key : dateMap.keySet()) {

								int eventCount = dateMap.get(key);
								if (eventCount >= minSubjectsPerRow) {
									int codeValuePosition = key.lastIndexOf(",") + 1;
									long codeValue = Long.parseLong(key.substring(codeValuePosition));

									String binaryCodeValue = bitStringPrefix + Long.toBinaryString(codeValue);
									String codeFields = "";
									for (int bitNr = 1; bitNr <= codeList.size() * (eventTypeFollowUp.get(eventType).equals("NO") ? 1 : 2); bitNr++) {
										String bit = binaryCodeValue.substring(binaryCodeValue.length() - bitNr, binaryCodeValue.length() - bitNr + 1);
										codeFields = "," + bit + codeFields;
									}

									record =        Parameters.DATABASE_NAME;
									record += "," + eventType;
									record += "," + indexDate;
									record += "," + key.substring(0, codeValuePosition - 1);
									record += ",";
									record += "," + eventCount;
									record += codeFields;
									Jerboa.getOutputManager().writeln(fileName, record, true);
								}
							}
						}
					}

					eventTypeMap = eventsAgegroupMap.get(eventType);
					if (eventTypeMap != null) {
						Map<String, Integer> dateMap = eventTypeMap.get(indexDay);
						if (dateMap != null) {
							for (String key : dateMap.keySet()) {

								int eventCount = dateMap.get(key);
								if (eventCount >= minSubjectsPerRow) {
									int codeValuePosition = key.lastIndexOf(",") + 1;
									long codeValue = Long.parseLong(key.substring(codeValuePosition));

									String binaryCodeValue = bitStringPrefix + Long.toBinaryString(codeValue);
									String codeFields = "";
									for (int bitNr = 1; bitNr <= codeList.size() * (eventTypeFollowUp.get(eventType).equals("NO") ? 1 : 2); bitNr++) {
										String bit = binaryCodeValue.substring(binaryCodeValue.length() - bitNr, binaryCodeValue.length() - bitNr + 1);
										codeFields = "," + bit + codeFields;
									}

									record =        Parameters.DATABASE_NAME;
									record += "," + eventType;
									record += "," + indexDate;
									record += "," + key.substring(0, codeValuePosition - 1);
									record += "," + eventCount;
									record += codeFields;
									Jerboa.getOutputManager().writeln(fileName, record, true);
								}
							}
						}
					}
				}
				else {
					// Generate all combinations
					for (long codeValue = 0; codeValue < Math.pow(2, codeList.size() * (eventTypeFollowUp.get(eventType).equals("NO") ? 1 : 2)); codeValue++) {
						String binaryCodeValue = bitStringPrefix + Long.toBinaryString(codeValue);
						String codeFields = "";
						for (int bitNr = 1; bitNr <= codeList.size() * (eventTypeFollowUp.get(eventType).equals("NO") ? 1 : 2); bitNr++) {
							String bit = binaryCodeValue.substring(binaryCodeValue.length() - bitNr, binaryCodeValue.length() - bitNr + 1);
							codeFields = "," + bit + codeFields;
						}

						// Date, Event, Gender, CodeValue

						// Write male counts
						int maleSubjects = 0;
						if (
								eventsOverallMap.containsKey(eventType) &&
								eventsOverallMap.get(eventType).containsKey(indexDays) &&
								eventsOverallMap.get(eventType).get(indexDays).containsKey("M," + codeValue)
						) {
							maleSubjects = eventsOverallMap.get(eventType).get(indexDays).get("M," + codeValue);
						}
						if (maleSubjects >= minSubjectsPerRow) {
							record =        Parameters.DATABASE_NAME;
							record += "," + eventType;
							record += "," + indexDate;
							record += ",M";
							record += ",";
							record += "," + maleSubjects;
							record += codeFields;
							Jerboa.getOutputManager().writeln(fileName, record, true);
						}

						// Write female counts
						int femaleSubjects = 0;
						if (
								eventsOverallMap.containsKey(eventType) &&
								eventsOverallMap.get(eventType).containsKey(indexDays) &&
								eventsOverallMap.get(eventType).get(indexDays).containsKey("F," + codeValue)
						) {
							femaleSubjects = eventsOverallMap.get(eventType).get(indexDays).get("F," + codeValue);
						}
						if (femaleSubjects >= minSubjectsPerRow) {
							record =        Parameters.DATABASE_NAME;
							record += "," + eventType;
							record += "," + indexDate;
							record += ",F";
							record += ",";
							record += "," + femaleSubjects;
							record += codeFields;
							Jerboa.getOutputManager().writeln(fileName, record, true);
						}
					}

					//refresh loop variable and progress
					progress.update();

					for (AgeGroup ageGroup : ageGroupDefinition.getAgeGroups()) {

						for (int codeValue = 0; codeValue < Math.pow(2, codeList.size() * (eventTypeFollowUp.get(eventType).equals("NO") ? 1 : 2)); codeValue++) {
							String binaryCodeValue = bitStringPrefix + Integer.toBinaryString(codeValue);
							String codeFields = "";
							for (int bitNr = 1; bitNr <= codeList.size() * (eventTypeFollowUp.get(eventType).equals("NO") ? 1 : 2); bitNr++) {
								String bit = binaryCodeValue.substring(binaryCodeValue.length() - bitNr, binaryCodeValue.length() - bitNr + 1);
								codeFields = "," + bit + codeFields;
							}

							// Date, Event, Gender, AgeGroup, CodeValue

							// Write male counts
							int maleSubjects = 0;
							if (
									eventsOverallMap.containsKey(eventType) &&
									eventsOverallMap.get(eventType).containsKey(indexDays) &&
									eventsOverallMap.get(eventType).get(indexDays).containsKey("M," + ageGroup.getLabel() + "," + codeValue)
							) {
								maleSubjects = eventsOverallMap.get(eventType).get(indexDays).get("M," + ageGroup.getLabel() + "," + codeValue);
							}
							if (maleSubjects >= minSubjectsPerRow) {
								record =        Parameters.DATABASE_NAME;
								record += "," + eventType;
								record += "," + indexDate;
								record += ",M";
								record += "," + ageGroup.getLabel();
								record += "," + maleSubjects;
								record += codeFields;
								Jerboa.getOutputManager().writeln(fileName, record, true);
							}

							// Write female counts
							int femaleSubjects = 0;
							if (
									eventsOverallMap.containsKey(eventType) &&
									eventsOverallMap.get(eventType).containsKey(indexDays) &&
									eventsOverallMap.get(eventType).get(indexDays).containsKey("F," + ageGroup.getLabel() + "," + codeValue)
							) {
								femaleSubjects = eventsOverallMap.get(eventType).get(indexDays).get("F," + ageGroup.getLabel() + "," + codeValue);
							}
							if (femaleSubjects >= minSubjectsPerRow) {
								record =        Parameters.DATABASE_NAME;
								record += "," + eventType;
								record += "," + indexDate;
								record += ",F";
								record += "," + ageGroup.getLabel();
								record += "," + femaleSubjects;
								record += codeFields;
								Jerboa.getOutputManager().writeln(fileName, record, true);
							}
						}

						//refresh loop variable and progress
						progress.update();
					}
				}
			}

			// Close the output file.
			Jerboa.getOutputManager().closeFile(fileName);
		}

		// Close the intermediate files.
		if ((!Jerboa.unitTest) && intermediateFiles) {
			for (String fileName : intermediateFileNames) {
				Jerboa.getOutputManager().closeFile(fileName);
			}
		}

		// Close the debug file.
		if (debug) {
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
	}


	private int getHistoryStart(Patient patient, int indexDate, String eventType) {
		int historyStart = 0;
		String history = eventTypeHistory.get(eventType);
		if (history.equals("COHORTSTART")) {
			historyStart = patient.getCohortStartDate();
		}
		else if (!history.equals("")) {
			historyStart = indexDate - Integer.parseInt(history);
		}
		return historyStart;
	}


	private int getFollowUpEnd(Patient patient, int indexDate, String eventType) {
		int followUpEnd = -1;
		String followUp = eventTypeFollowUp.get(eventType);
		if (followUp.equals("COHORTEND")) {
			followUpEnd = patient.getCohortStartDate();
		}
		else if (followUp.equals("")) {
			followUpEnd = Integer.MAX_VALUE;
		}
		else if (!followUp.equals("NO")) {
			followUpEnd = indexDate + Integer.parseInt(followUp);
		}
		return followUpEnd;
	}

}
