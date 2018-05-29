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
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.commons.collections.bidimap.DualHashBidiMap;
import org.erasmusmc.jerboa.Jerboa;
import org.erasmusmc.jerboa.config.DataDefinition;
import org.erasmusmc.jerboa.dataClasses.Patient;
import org.erasmusmc.jerboa.dataClasses.Prescription;
import org.erasmusmc.jerboa.engine.Module;
import org.erasmusmc.jerboa.utilities.AgeGroupDefinition;
import org.erasmusmc.jerboa.utilities.AgeGroupDefinition.AgeGroup;
import org.erasmusmc.jerboa.utilities.DateUtilities;
import org.erasmusmc.jerboa.utilities.InputFileUtilities;
import org.erasmusmc.jerboa.utilities.Logging;
import org.erasmusmc.jerboa.utilities.StringUtilities;


/**
 * Calculates drug utilization such as incidence and prevalence at the population level.
 * Multiple aggregation levels can be specified.
 * @author Mees Mosseveld
 *
 */

public class DrugUtilizationPopulationLevel extends Module {

	/**
	 * The ATC-codes you are interested in.
	 */
	public List<String> atcOfInterest = new ArrayList<String>();

	/**
	 * The minimum number of subjects in a row of the resulting aggregated table. Rows with fewer subjects
	 * are deleted, and deletions will be summarized in the last line of the table.
	 */
	public int minSubjectsPerRow;

	/**
	 * The period (in days) before the first prescription where the drug is not used.
	 */
	public int naivePeriod;

	/**
	 * Flag to include terminators in the output
	 */
	public boolean includeTerminators = false;

	/**
	 * The period (in days) after a prescription where the drug is not used to be classified as a termination.<BR>
	 * If < 0 it defaults to the value of naivePeriod.
	 */
	public int terminationNonUsePeriod;

	/**
	 * Specifies the levels of aggregation. For instance: 'ATC;AgeRange;Gender' will generate a table aggregated by ATC, ageRange and gender.<BR>
	 * Each key should be specified using semicolon as seperator, using a selection of these fields:
	 *
	 * ATC
	 * ATC1
	 * ATC2
	 * ATC3
	 * ATC4
	 * ATC5
	 * ATC6
	 * ATC7
	 * ATC999 (use full ATC code)
	 * Dose
	 * AgeRange
	 * Gender
	 * Year
	 * Month
	 */
	public List<String> aggregationKeys = new ArrayList<String>();

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
	public List<String> ageCodes = new ArrayList<String>();

	/**
	 * For debugging only.
	 * <ATC>,<Dose>,<AgeRange>,<Gender>,<Year>,<Month>
	 */
	public List<String> trace = new ArrayList<String>();


	private static final int EXPOSEDDAYS                     = 0;
	private static final int NEW_USERS                       = 1;
	private static final int PRESCRIPTION_STARTS             = 2;
	private static final int TERMINATORS                     = 3;
	private static final int CENSORED_DAYS                   = 4;
	private static final int EXPOSED_INDIVIDUALS             = 5;
	private static final int NUMBER_OF_COUNTERS              = 6;

	private static final int PERSON_DAYS_BACKGROUND          = 0;
	private static final int EXPOSED_INDIVIDUALS_BACKGROUND  = 1;
	private static final int NUMBER_OF_BACKGROUND_COUNTERS   = 2;

	// Representation aggregation keys
	private List<KeyTemplate> keyTemplates;
	private Set<String> keyTemplateStrings;
	private boolean splitByMonth = false;
	private boolean keyTemplatesInitialized = false;
//	private boolean doseUsed = false;

	private int firstDate;
	private int lastDate;

	// Counters
//	private int countPatients = 0;
//	private int countPrescriptions = 0;

	// Age group definition
	private AgeGroupDefinition ageGroupDefinition = null;

	/* No intermediate files created
	private String intermediateFileName = FilePaths.WORKFLOW_PATH + "Intermediate/DrugUtilizationPopulation.csv";
	*/
	private int flushInterval = 100;	//flush output to file after this number of MB

//	private final int logDebugInfo = 0;

	private Map<String, long[]> stats = null;
	private Map<String, long[]> backgroundStats = null;
	private Map<String, String> statKeyBackground = null;

	private boolean doseUsed = false;
	private Map<Integer, Boolean> atcLevelsUsed = null;
	private Set<String> allATCDoseCombinations = null;
	private Map<Integer, Set<Integer>> allYearMonthCombinations = null;

	// Flag to mimic the behavior of the old Jerboa
	private boolean oldJerboa = false;


	@Override
	public void setNeededFiles() {
		setRequiredFile(DataDefinition.PATIENTS_FILE);
		setRequiredFile(DataDefinition.PRESCRIPTIONS_FILE);
	}


	@Override
	public void setNeededExtendedColumns() { /* No extended columns required */ }


	@Override
	public void setNeededNumericColumns() { /* No numeric columns required */ }


	@Override
	public boolean init() {
		boolean initOK = true;
		// No intermediate files created
		//if (intermediateFiles) {
		//	Logging.add("  Intermediate file: " + intermediateFileName);
		//	outputManager.addFile(intermediateFileName, flushInterval);
		//}

		// Set terminationNonUsePeriod if related to naivePeriod
		terminationNonUsePeriod = terminationNonUsePeriod == -1 ? naivePeriod : terminationNonUsePeriod;

		// Parse age group definition.
		// If no age group is specified create one overall age group.
		ageGroupDefinition = new AgeGroupDefinition(ageCodes, false);
		initOK = ageGroupDefinition.isOK();
		if (ageGroupDefinition.getAgeGroup("ALL") != null) {
			Logging.add("Age group with label ALL is not allowed. It's generated by default.", Logging.ERROR);
			initOK = false;
		}
		ageGroupDefinition.add("0;999;ALL");

		// Parse the aggregation keys
		doseUsed = false;
		keyTemplates = new ArrayList<KeyTemplate>();
		keyTemplateStrings = new HashSet<String>();
		atcLevelsUsed = new HashMap<Integer, Boolean>();
		parseAggregationKeys();

		allATCDoseCombinations = new HashSet<String>();
		allATCDoseCombinations.add("ALL,ALL");

		allYearMonthCombinations = new HashMap<Integer, Set<Integer>>();
		allYearMonthCombinations.put(-1, new HashSet<Integer>());
		allYearMonthCombinations.get(-1).add(-1);

		// Initialize stats
		stats = new HashMap<String , long[]>();
		backgroundStats = new HashMap<String , long[]>();
		statKeyBackground = new HashMap<String, String>();

		new HashMap<Integer, Integer>();

		if (oldJerboa) includeTerminators = true;

		firstDate = Integer.MAX_VALUE;
		lastDate = Integer.MIN_VALUE;

		return initOK;
	}


	@Override
	public Patient process(Patient patient) {

		// Initialization of allATCDoseCombinations
		if (!keyTemplatesInitialized) {
			for (KeyTemplate keyTemplate : keyTemplates) {
				keyTemplate.collectKeys();
			}
			keyTemplatesInitialized = true;
		}

		if (patient != null && patient.isInPopulation() && patient.isInCohort()) {
			firstDate = Math.min(firstDate, patient.getCohortStartDate());
			lastDate = Math.max(lastDate, patient.getCohortEndDate());

			// Get the prescriptions of interest
			List<Prescription> prescriptionsOfInterest = new ArrayList<Prescription>();
			Map<Prescription, Set<String>> prescriptionATCsOfInterest = new HashMap<Prescription, Set<String>>();
			for (Prescription prescription : patient.getPrescriptions()) {
				Set<String> matchingATCsOfInterest = getMatchingATCsOfInterest(prescription);
				if ((prescription.getEndDate() > patient.getCohortStartDate()) && (prescription.getDate() < patient.getCohortEndDate()) && (!matchingATCsOfInterest.isEmpty())) {
					prescriptionsOfInterest.add(prescription);
					matchingATCsOfInterest.add("ALL");
					prescriptionATCsOfInterest.put(prescription, matchingATCsOfInterest);
				}
			}

			// Collect all terminators per atc/dose combination
			Map<String, List<Integer>> terminationDates = new HashMap<String, List<Integer>>();
			if (includeTerminators) {
				Map<String, List<List<Integer>>> exposures = new HashMap<String, List<List<Integer>>>();
				for (Prescription prescription : prescriptionsOfInterest) {
					for (String atc : prescriptionATCsOfInterest.get(prescription)) {
						if (doseUsed) {
							String atcDose = atc + "," + prescription.getDose();
							addTime(prescription.getDate(), prescription.getEndDate(), atcDose, exposures);
						}
						String atcDose = atc + ",ALL";
						addTime(prescription.getDate(), prescription.getEndDate(), atcDose, exposures);
					}
				}
				for (String atcDose : exposures.keySet()) {
					int lastEnd = exposures.get(atcDose).get(0).get(1);
					int exposureNr = 1;
					while (exposureNr < exposures.get(atcDose).size()) {
						if ((exposures.get(atcDose).get(exposureNr).get(0) - lastEnd) > terminationNonUsePeriod) {
							List<Integer> atcDoseTerminationDates = terminationDates.get(atcDose);
							if (atcDoseTerminationDates == null) {
								atcDoseTerminationDates = new ArrayList<Integer>();
								terminationDates.put(atcDose, atcDoseTerminationDates);
							}
							atcDoseTerminationDates.add(lastEnd);
						}
						lastEnd = exposures.get(atcDose).get(exposureNr).get(1);
						exposureNr++;
					}
					if ((patient.getCohortEndDate() - lastEnd) > terminationNonUsePeriod) {
						List<Integer> atcDoseTerminationDates = terminationDates.get(atcDose);
						if (atcDoseTerminationDates == null) {
							atcDoseTerminationDates = new ArrayList<Integer>();
							terminationDates.put(atcDose, atcDoseTerminationDates);
						}
						atcDoseTerminationDates.add(lastEnd);
					}
				}
			}

			// Get the birth month and day
			int birthMonth = DateUtilities.getMonthFromDays(patient.getBirthDate());
			int birthDay = DateUtilities.getDayFromDays(patient.getBirthDate());

			Map<KeyTemplate, Map<String, Integer>> lastActiveDate = new HashMap<KeyTemplate, Map<String, Integer>>();
			Set<String> exposedIndividualKeys = new HashSet<String>();
			Set<String> personDaysKeys = new HashSet<String>();
			Set<String> background = new HashSet<String>();

			Integer currentDate = patient.getCohortStartDate();
			while (currentDate != null) {
				Integer nextDate = getNextDate(currentDate, birthMonth, birthDay, patient.getCohortEndDate(), splitByMonth);
				if (nextDate != null) {
					int personDays = nextDate - currentDate;

					for (KeyTemplate keyTemplate : keyTemplates) {
						Map<String, List<List<Integer>>> exposureTimes = new HashMap<String, List<List<Integer>>>();
						Map<String, List<List<Integer>>> censoredTimes = new HashMap<String, List<List<Integer>>>();
						Set<String> exposedIndividual = new HashSet<String>();
						Map<String, Integer> newUsers = new HashMap<String, Integer>();
						Map<String, Integer> prescriptionStarts = new HashMap<String, Integer>();
						if (!lastActiveDate.containsKey(keyTemplate)) {
							lastActiveDate.put(keyTemplate, new HashMap<String, Integer>());
						}

						List<Prescription> remainingPrescriptions = new ArrayList<Prescription>();
						for (Prescription prescription : prescriptionsOfInterest) {
							if (prescription.getEndDate() > currentDate) {
								remainingPrescriptions.add(prescription);
								if (prescription.getDate() < nextDate) {
									if ((prescription.getDate() >= currentDate) && (prescription.getDate() < nextDate)) {

										for (String atc : keyTemplate.atcSet) {
											if (atc.equals("ALL") || prescription.startsWith(atc)) {
												for (String dose : keyTemplate.doseSet) {
													String atcDose = atc + "," + dose;
													if (dose.equals("ALL") || prescription.getDose().equals(dose)) {
														exposedIndividual.add(atcDose);
														addTime(prescription.getDate(), prescription.getEndDate(), atcDose, exposureTimes);
														addTime(prescription.getDate(), prescription.getEndDate() + naivePeriod, atcDose, censoredTimes);
														newUsers.put(atcDose, (newUsers.containsKey(atcDose) ? newUsers.get(atcDose) : 0) + (((!lastActiveDate.get(keyTemplate).containsKey(atcDose)) || ((prescription.getDate() - lastActiveDate.get(keyTemplate).get(atcDose)) >= naivePeriod)) ? 1 : 0));
														prescriptionStarts.put(atcDose, prescriptionStarts.containsKey(atcDose) ? prescriptionStarts.get(atcDose) + 1 : 1);
														lastActiveDate.get(keyTemplate).put(atcDose, Math.max(lastActiveDate.get(keyTemplate).containsKey(atcDose) ? lastActiveDate.get(keyTemplate).get(atcDose) : 0, prescription.getEndDate()));
													}
												}
											}
										}
									}
								}
							}
						}
						prescriptionsOfInterest = remainingPrescriptions;

						// Get the terminators for this period.
						Map<String, Integer> terminators = new HashMap<String, Integer>();
						if (includeTerminators) {
							for (String atc : keyTemplate.atcSet) {
								for (String dose : keyTemplate.doseSet) {
									String atcDose = atc + "," + dose;

									if (terminationDates.containsKey(atcDose)) {
										for (int date : terminationDates.get(atcDose)) {
											if ((date >= currentDate) && (date < nextDate)) {
												terminators.put(atcDose, terminators.containsKey(atcDose) ? terminators.get(atcDose) + 1 : 1);
											}
											if (date > nextDate) {
												break;
											}
										}
									}
								}
							}
						}

						// Add the period characteristics to the overall characteristics.
						List<AgeGroup> ageGroups = oldJerboa ? ageGroupDefinition.getAgeGroupsEstimated(patient.getAgeAtDate(currentDate)) : ageGroupDefinition.getAgeGroups(patient.getAgeAtDateInYears(currentDate));
						for (AgeGroup ageGroup : ageGroups) {
							if (keyTemplate.ageGroupSet.contains(ageGroup)) {
								for (String gender : new String[] { Patient.convertGender(patient.gender), "ALL" }) {
									if (keyTemplate.genderSet.contains(gender)) {
										for (String year : new String[] { Integer.toString(DateUtilities.getYearFromDays(currentDate)), "ALL" }) {
											for (String month : ((splitByMonth && (!year.equals("ALL"))) ? new String[] { Integer.toString(DateUtilities.getMonthFromDays(currentDate)), "ALL" } : new String[] { "ALL" })) {

												String backgroundKey = ageGroup.getLabel() + "," + gender + "," + year + "," + month;

												long[] backgroundKeyStat = backgroundStats.get(backgroundKey);
												if (backgroundKeyStat == null) {
													backgroundKeyStat = new long[NUMBER_OF_BACKGROUND_COUNTERS];
													for (int statNr = 0; statNr < NUMBER_OF_BACKGROUND_COUNTERS; statNr++) {
														backgroundKeyStat[statNr] = 0;
													}
													backgroundStats.put(backgroundKey, backgroundKeyStat);
												}
												/*
												Logging.add("BACKGROUND,,,," + backgroundKey + ",PERSONDAYS," + Integer.toString(personDaysKeys.contains(backgroundKey + "," + currentDate) ? 0 : personDays) + "," + Long.toString(backgroundKeyStat[PERSON_DAYS_BACKGROUND] + (personDaysKeys.contains(backgroundKey + "," + currentDate) ? 0 : personDays)));
												Logging.add("BACKGROUND,,,," + backgroundKey + ",INDIVIDUALS," + Integer.toString(background.contains(backgroundKey) ? 0 : 1) + "," + Long.toString(backgroundKeyStat[EXPOSED_INDIVIDUALS_BACKGROUND] + (background.contains(backgroundKey) ? 0 : 1)));
												*/
												backgroundKeyStat[PERSON_DAYS_BACKGROUND]          += personDaysKeys.add(backgroundKey + "," + currentDate) ? personDays : 0;
												backgroundKeyStat[EXPOSED_INDIVIDUALS_BACKGROUND]  += background.add(backgroundKey) ? 1 : 0;

												for (String atc : keyTemplate.atcSet) {
													for (String dose : keyTemplate.doseSet) {
														String atcDose = atc + "," + dose;
														String key = keyTemplate.toString() + "," + atcDose + "," + ageGroup.getLabel() + "," + gender + "," + year + "," + month;

														long[] keyStat = stats.get(key);
														if (keyStat == null) {
															keyStat = new long[NUMBER_OF_COUNTERS];
															for (int statNr = 0; statNr < NUMBER_OF_COUNTERS; statNr++) {
																keyStat[statNr] = 0;
															}
															stats.put(key, keyStat);
														}
														/*
														Logging.add("STAT," + key + ",EXPOSEDDAYS," + Integer.toString(getTime(atcDose, currentDate, nextDate, exposureTimes)) + "," + Long.toString(keyStat[EXPOSEDDAYS] + getTime(atcDose, currentDate, nextDate, exposureTimes)));
														Logging.add("STAT," + key + ",CENSORED_DAYS," + Integer.toString(getTime(atcDose, currentDate, nextDate, censoredTimes)) + "," + Long.toString(keyStat[CENSORED_DAYS] + getTime(atcDose, currentDate, nextDate, censoredTimes)));
														Logging.add("STAT," + key + ",EXPOSED_INDIVIDUALS," + Integer.toString((exposedIndividual.contains(atcDose) && (!exposedIndividualKeys.contains(key))) ? 1 : 0) + "," + Long.toString(keyStat[EXPOSED_INDIVIDUALS] + (exposedIndividual ? 1 : 0)));
														Logging.add("STAT," + key + ",NEW_USERS," + Integer.toString(newUsers.containsKey(atcDose) ? newUsers.get(atcDose) : 0) + "," + Long.toString(keyStat[NEW_USERS] + (newUsers.containsKey(atcDose) ? newUsers.get(atcDose) : 0)));
														Logging.add("STAT," + key + ",PRESCRIPTION_STARTS," + Integer.toString(prescriptionStarts.containsKey(atcDose) ? prescriptionStarts.get(atcDose) : 0) + "," + Long.toString(keyStat[PRESCRIPTION_STARTS] + (prescriptionStarts.containsKey(atcDose) ? prescriptionStarts.get(atcDose) : 0)));
														Logging.add("STAT," + key + ",TERMINATORS," + Integer.toString(terminators.containsKey(atcDose) ? terminators.get(atcDose) : 0) + "," + Long.toString(keyStat[TERMINATORS] + (terminators.containsKey(atcDose) ? terminators.get(atcDose) : 0)));
														*/
														keyStat[EXPOSEDDAYS]         += getTime(atcDose, currentDate, nextDate, exposureTimes);
														keyStat[CENSORED_DAYS]       += getTime(atcDose, currentDate, nextDate, censoredTimes);
														keyStat[EXPOSED_INDIVIDUALS] += (exposedIndividual.contains(atcDose) && exposedIndividualKeys.add(key)) ? 1 : 0;
														keyStat[NEW_USERS]           += newUsers.containsKey(atcDose) ? newUsers.get(atcDose) : 0;
														keyStat[PRESCRIPTION_STARTS] += prescriptionStarts.containsKey(atcDose) ? prescriptionStarts.get(atcDose) : 0;
														keyStat[TERMINATORS]         += terminators.containsKey(atcDose) ? terminators.get(atcDose) : 0;
														statKeyBackground.put(key, backgroundKey);
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
				currentDate = nextDate;
			}
		}

		return patient;
	}


	@Override
	public void outputResults() {

		//Logging.add("Number of patients               : " + countPatients);
		//Logging.add("Number of prescriptions          : " + countPrescriptions);
		//Logging.add("Average prescriptions per patient: " + Double.toString((double)countPrescriptions/(double)countPatients));

		Jerboa.getOutputManager().addFile(outputFileName, flushInterval);

		String record = "ATC";
		record += "," + "Dose";
		record += "," + "AgeRange";
		record += "," + "Gender";
		record += "," + "Year";
		record += "," + "Month";
		record += "," + "ExposedDays";
		record += "," + "CensoredDays";
		record += "," + "PersonDays";
		if (oldJerboa) record += "," + "PersonDays_Background_Censored";
		record += "," + "ExposedIndividuals";
		record += "," + "Individuals";
		record += "," + "NewUsers";
		if (includeTerminators) record += "," + "Terminators";
		record += "," + "PrescriptionStarts";
		record += "," + "Prevalence (per 1000 PT years)";
		record += "," + "Incidence (per 1000 PT years)";
		record += "," + "Mean duration follow-up";
		record += "," + "Mean duration per prescription";
		record += "," + "Mean ExposedDays per exposed individual";
		record += "," + "Rx/PT (*1000)";
		record += "," + "Exposure Rate (*1000)";
		record += System.lineSeparator();

		Jerboa.getOutputManager().write(outputFileName, record, true);

		if (stats.size() > 0) {
			DecimalFormat precision = StringUtilities.setDecimalFormat(10, true);

			Set<String> atcSet = new HashSet<String>();
			Set<String> doseSet = new HashSet<String>();
			Set<AgeGroup> ageGroupSet = new HashSet<AgeGroup>();
			Set<String> genderSet = new HashSet<String>();

			for (KeyTemplate keyTemplate : keyTemplates) {
				atcSet.addAll(keyTemplate.atcSet);
				doseSet.addAll(keyTemplate.doseSet);
				ageGroupSet.addAll(keyTemplate.ageGroupSet);
				genderSet.addAll(keyTemplate.genderSet);
			}

			Set<String> keysWritten = new HashSet<String>();

			Integer currentDate = firstDate;
			while (currentDate != null) {
				String currentYear = currentDate == -1 ? "ALL" : Integer.toString(DateUtilities.getYearFromDays(currentDate));
				String currentMonth = (splitByMonth && (currentDate != -1))? Integer.toString(DateUtilities.getMonthFromDays(currentDate)) : "ALL";

				for (String atc : atcSet) {
					for (String dose : doseSet) {
						for (AgeGroup ageGroup : ageGroupSet) {
							for (String gender : genderSet) {
								for (KeyTemplate keyTemplate : keyTemplates) {
									if (keyTemplate.matches(atc, dose, ageGroup, gender)) {
										String key = keyTemplate.toString() + "," + atc + "," + dose + "," + ageGroup.getLabel() + "," + gender + "," + currentYear + "," + currentMonth;

										long totalExposure           = 0;
										long totalCensoredTime       = 0;
										long totalExposedIndividuals = 0;
										long totalNewUsers           = 0;
										long totalTerminators        = 0;
										long totalPrescriptionStarts = 0;

										long totalPersonDays         = 0;
										long totalIndividuals        = 0;

										if (stats.containsKey(key)) {
											long[] keyStats = stats.get(key);
											if (keyStats != null) {
												totalExposure           = keyStats[EXPOSEDDAYS];
												totalCensoredTime       = keyStats[CENSORED_DAYS];
												totalExposedIndividuals = keyStats[EXPOSED_INDIVIDUALS];
												totalNewUsers           = keyStats[NEW_USERS];
												totalPrescriptionStarts = keyStats[PRESCRIPTION_STARTS];
												totalTerminators        = keyStats[TERMINATORS];

												String backgroundKey = statKeyBackground.get(key);
												long[] backgroundKeyStats = backgroundStats.get(backgroundKey);
												if (backgroundKeyStats != null) {
													totalPersonDays  = backgroundKeyStats[PERSON_DAYS_BACKGROUND];
													totalIndividuals = backgroundKeyStats[EXPOSED_INDIVIDUALS_BACKGROUND];
												}
											}
										}

										long totalPersonDaysBackgroundCensored = totalPersonDays - totalCensoredTime;

										String outputKey = key.substring(key.indexOf(",") + 1);
										if (keysWritten.add(outputKey)) {
											record = outputKey;
											record += "," + totalExposure;
											record += "," + totalCensoredTime;
											record += "," + totalPersonDays;
											if (oldJerboa) record += "," + totalPersonDaysBackgroundCensored;
											record += "," + totalExposedIndividuals;
											record += "," + totalIndividuals;
											record += "," + totalNewUsers;
											if (includeTerminators) record += "," + totalTerminators;
											record += "," + totalPrescriptionStarts;

											// Add "Prevalence (per 1000 PT years)" to the record
											record += "," + (totalPersonDays == 0 ? "" : precision.format((double)totalExposedIndividuals * 1000d / ((double)totalPersonDays/365.25d)));
											// Add "Incidence (per 1000 PT years)" to the record
											record += "," + ((totalPersonDays - totalCensoredTime) == 0 ? "" : precision.format((double)totalNewUsers * 1000d / (((double)totalPersonDays - (double)totalCensoredTime)/365.25d)));
											// Add "Mean duration follow-up" to the record
											record += "," + (totalIndividuals == 0 ? "" : precision.format((double)totalPersonDays/(double)totalIndividuals));
											// Add "Mean duration per prescription" to the record
											record += "," + (totalPrescriptionStarts == 0 ? "" : precision.format((double)totalExposure/(double)totalPrescriptionStarts));
											// Add "Mean personDays per exposed individual" to the record
											record += "," + (totalExposedIndividuals == 0 ? "" : precision.format((double)totalExposure/(double)totalExposedIndividuals));
											// Add "Rx/PT (*1000)" to the record
											record += "," + (totalPersonDays == 0 ? "" : precision.format((double)totalPrescriptionStarts * 1000d / ((double)totalPersonDays/365.25d)));
											// Add "Exposure Rate (*1000)" to the record
											record += "," + (totalPersonDays == 0 ? "" : precision.format((double)totalExposure * 1000d / (double)totalPersonDays));
											record += System.lineSeparator();

											Jerboa.getOutputManager().write(outputFileName, record, true);
										}
									}
								}
							}
						}
					}
				}

				Integer nextDate = currentDate == -1 ? null : getNextDate(currentDate, 1, 1, lastDate, splitByMonth);
				currentDate = currentDate == -1 ? null : (nextDate == null ? -1 : nextDate);
			}
		}

		Jerboa.getOutputManager().closeFile(outputFileName);

	}


	private Integer getNextDate(int currentDate, int birthMonth, int birthDay, int endDate, boolean byMonth) {
		Integer nextDate = null;
		if (currentDate < endDate) {
			int currentMonth = DateUtilities.getMonthFromDays(currentDate);
			if (byMonth) {
				if (currentMonth == birthMonth) {
					int currentDay = DateUtilities.getDayFromDays(currentDate);
					if (currentDay < birthDay) {
						int[] nextBirthDay = new int[] { DateUtilities.getYearFromDays(currentDate), birthMonth, birthDay};
						if (DateUtilities.isDateCoherent(nextBirthDay)) { // Test for non-existing 29th February else move it to 1st March
							nextDate = DateUtilities.dateToDays(nextBirthDay);
						}
						else {
							nextDate = DateUtilities.dateToDays(new int[] { DateUtilities.getYearFromDays(currentDate), birthMonth + 1, 1});
						}
					}
					else {
						nextDate = DateUtilities.dateToDays(new int[] { DateUtilities.getYearFromDays(currentDate), currentMonth == 12 ? 1 : currentMonth + 1, 1});
					}
				}
				else {
					int currentYear = DateUtilities.getYearFromDays(currentDate);
					nextDate = DateUtilities.dateToDays(new int[] {currentMonth == 12 ? currentYear + 1 : currentYear, currentMonth == 12 ? 1 : currentMonth + 1, 1});
				}
			}
			else {
				if (currentMonth < birthMonth) {
					int[] nextBirthDay = new int[] { DateUtilities.getYearFromDays(currentDate), birthMonth, birthDay};
					if (DateUtilities.isDateCoherent(nextBirthDay)) { // Test for non-existing 29th February else move it to 1st March
						nextDate = DateUtilities.dateToDays(nextBirthDay);
					}
					else {
						nextDate = DateUtilities.dateToDays(new int[] { DateUtilities.getYearFromDays(currentDate), birthMonth + 1, 1});
					}
				}
				else if (currentMonth == birthMonth) {
					int currentDay = DateUtilities.getDayFromDays(currentDate);
					if (currentDay < birthDay) {
						nextDate = DateUtilities.dateToDays(new int[] { DateUtilities.getYearFromDays(currentDate), birthMonth, birthDay});
					}
					else {
						nextDate = DateUtilities.dateToDays(new int[] { DateUtilities.getYearFromDays(currentDate) + 1, 1, 1});
					}
				}
				else {
					nextDate = DateUtilities.dateToDays(new int[] { DateUtilities.getYearFromDays(currentDate) + 1, 1, 1});
				}
			}
			if (nextDate > endDate) {
				nextDate = endDate;
			}
		}
		return nextDate;
	}


	private Set<String> getMatchingATCsOfInterest(Prescription prescription) {
		Set<String> atcsOfInterest = new HashSet<String>();
		if (atcOfInterest.size() > 0) {
			for (String atc : atcOfInterest) {
				if (prescription.startsWith(atc)) {
					atcsOfInterest.add(atc);
				}
			}
		}
		else {
			atcsOfInterest.add(prescription.getATC());
		}

		return atcsOfInterest;
	}


	private void addTime(int startDate, int endDate, String atcDose, Map<String, List<List<Integer>>> times) {
		List<Integer> prescriptionCensorTime = new ArrayList<Integer>(Arrays.asList(startDate, endDate));
		if (times.containsKey(atcDose)) {
			List<List<Integer>> atcDoseCensorTimesList = times.get(atcDose);
			int censorNr = 0;
			while ((prescriptionCensorTime != null) && (censorNr < atcDoseCensorTimesList.size())) {
				if (prescriptionCensorTime.get(1) < atcDoseCensorTimesList.get(censorNr).get(0)) {
					atcDoseCensorTimesList.add(censorNr, prescriptionCensorTime);
					prescriptionCensorTime = null;
				}
				else if ((prescriptionCensorTime.get(0) <= atcDoseCensorTimesList.get(censorNr).get(1)) && (prescriptionCensorTime.get(1) >= atcDoseCensorTimesList.get(censorNr).get(0))) {
					prescriptionCensorTime = new ArrayList<Integer>(Arrays.asList(Math.min(prescriptionCensorTime.get(0), atcDoseCensorTimesList.get(censorNr).get(0)), Math.max(prescriptionCensorTime.get(1), atcDoseCensorTimesList.get(censorNr).get(1))));
					atcDoseCensorTimesList.remove(censorNr);
					censorNr--;
				}
				censorNr++;
			}
			if (prescriptionCensorTime != null) {
				atcDoseCensorTimesList.add(prescriptionCensorTime);
			}
		}
		else {
			times.put(atcDose, new ArrayList<List<Integer>>(Arrays.asList(prescriptionCensorTime)));
		}
	}


	private int getTime(String atcDose, int startDate, int endDate, Map<String, List<List<Integer>>> times) {
		int censoredTime = 0;
		List<List<Integer>> atcDoseTimesList = times.get(atcDose);
		if (atcDoseTimesList != null) {
			for (List<Integer> period : atcDoseTimesList) {
				int censorStart = period.get(0);
				int censorEnd = period.get(1);
				if (startDate > censorStart) {
					censorStart = Math.min(startDate, censorEnd);
				}
				if (endDate < censorEnd) {
					censorEnd = Math.max(endDate, censorStart);
				}
				censoredTime += (censorEnd - censorStart);
			}
		}
		return censoredTime;
	}


	private String timeToString(Map<String, List<List<Integer>>> times){
		String result = "";
		for (String key : times.keySet()) {
			result += key + ":";
			for (List<Integer> interval : times.get(key)) {
				result += " " + Integer.toString(interval.get(0)) + " |------| " + Integer.toString(interval.get(1)) + " ";
			}
			result += "\r\n";
		}
		return result;
	}


	private void parseAggregationKeys() {
		keyTemplatesInitialized = false;
		for (int atcLevel = 1; atcLevel < 9; atcLevel++) {
			atcLevelsUsed.put(atcLevel == 8 ? 999 : atcLevel, false);
		}
		keyTemplates = new ArrayList<KeyTemplate>();
		for (String aggregationKey : aggregationKeys){
			KeyTemplate keyTemplate = new KeyTemplate();
			for (String part : aggregationKey.split(";")){
				if (part.toLowerCase().equals("atc1"))
					keyTemplate.atc = 1;
				else if (part.toLowerCase().equals("atc3"))
					keyTemplate.atc = 3;
				else if (part.toLowerCase().equals("atc4"))
					keyTemplate.atc = 4;
				else if (part.toLowerCase().equals("atc5"))
					keyTemplate.atc = 5;
				else if (part.toLowerCase().equals("atc7") || part.toLowerCase().equals("atc"))
					keyTemplate.atc = 7;
				else if (part.toLowerCase().equals("atc999"))
					keyTemplate.atc = 8;
				else if (part.toLowerCase().equals("dose")) {
					keyTemplate.dose = true;
					doseUsed = true;
				}
				else if (part.toLowerCase().equals("year"))
					keyTemplate.year = true;
				else if (part.toLowerCase().equals("month")) {
					keyTemplate.month = true;
					splitByMonth = true;
				}
				else if (part.toLowerCase().equals("gender"))
					keyTemplate.gender = true;
				else if (part.toLowerCase().equals("agerange"))
					keyTemplate.ageRange = true;
			}
			if (keyTemplateStrings.add(keyTemplate.toString())) {
				keyTemplates.add(keyTemplate);
			}
			else {
				Logging.add("Duplicate aggregation definition: " + aggregationKey, Logging.HINT);
			}

			if (keyTemplate.atc != -1) {
				atcLevelsUsed.put(keyTemplate.atc == 8 ? 999 : keyTemplate.atc, true);
			}
		}
		if (keyTemplates.size() == 0) {
			keyTemplates.add(new KeyTemplate());
		}
	}


	private class KeyTemplate {
		int atc = -1;
		public boolean dose = false;
		public boolean year = false;
		public boolean month = false;
		public boolean gender = false;
		public boolean ageRange = false;

		public Set<String> atcSet = new HashSet<String>();
		public Set<String> doseSet = new HashSet<String>();
		public Set<AgeGroup> ageGroupSet = new HashSet<AgeGroup>();
		public Set<String> genderSet = new HashSet<String>();


		public String toString() {
			return Integer.toString(atc) + (dose ? "T" : "F") + (gender ? "T" : "F") + (ageRange ? "T" : "F") + (year ? "T" : "F") + (month ? "T" : "F");
		}


		public void collectKeys() {
			// IMPORTANT: This method should be called from the process method at the first patient.

			// Get the ATC codes for this template
			for (String matchATC : atcOfInterest) {
				atcSet.add(atc == 8 ? matchATC : matchATC.substring(0, atc));
			}
			atcSet.add("ALL");

			// Get the doses for this template
			if (dose) {
				DualHashBidiMap allDoses = InputFileUtilities.getPrescriptionDoses();
				for (Object doseKey : allDoses.keySet()) {
					doseSet.add(allDoses.get(doseKey).toString());
				}
			}
			doseSet.add("ALL");

			// Get the genders for this template
			if (gender) {
				genderSet.add("M");
				genderSet.add("F");
			}
			genderSet.add("ALL");

			// Get the age groups for this template
			if (ageRange) {
				for (AgeGroup ageGroup : ageGroupDefinition.getAgeGroups()) {
					ageGroupSet.add(ageGroup);
				}
			}
			else {
				ageGroupSet.add(ageGroupDefinition.getAgeGroup("ALL"));
			}
		}


		public boolean matches(String atc, String dose, AgeGroup ageGroup, String gender) {
			return (atcSet.contains(atc) && doseSet.contains(dose) && ageGroupSet.contains(ageGroup) && genderSet.contains(gender));
		}
	}


	//MAIN FOR TESTING AND DEBUGGING
		public static void main(String[] args) {
			DrugUtilizationPopulationLevel dusPop = new DrugUtilizationPopulationLevel();

			/* Test getNextDate
			int birthDate = DateUtilities.dateToDays("19640229", DateUtilities.DATE_ON_YYYYMMDD);
			int startDate = DateUtilities.dateToDays("19990703", DateUtilities.DATE_ON_YYYYMMDD);
			int endDate = DateUtilities.dateToDays("20050311", DateUtilities.DATE_ON_YYYYMMDD);

			int birthMonth = DateUtilities.getMonthFromDays(birthDate);
			int birthDay = DateUtilities.getDayFromDays(birthDate);

			Integer currentDate = startDate;
			System.out.println(DateUtilities.daysToDate(startDate));
			while (currentDate != null) {
				currentDate = dusPop.getNextDate(currentDate, birthMonth, birthDay, endDate, false);
				if (currentDate != null) {
					System.out.println(DateUtilities.daysToDate(currentDate));
				}
			}
			*/

			/* Test addTime and getTime */
			Map<String, List<List<Integer>>> testTimes = new HashMap<String, List<List<Integer>>>();
			dusPop.addTime(5, 7, "Test", testTimes);
			System.out.println(dusPop.timeToString(testTimes));
			dusPop.addTime(2, 4, "Test", testTimes);
			System.out.println(dusPop.timeToString(testTimes));
			dusPop.addTime(8, 10, "Test", testTimes);
			System.out.println(dusPop.timeToString(testTimes));
			System.out.println("Time 3-9 = " + Integer.toString(dusPop.getTime("Test", 3, 9, testTimes)));
			dusPop.addTime(3, 5, "Test", testTimes);
			System.out.println(dusPop.timeToString(testTimes));
			dusPop.addTime(4, 6, "Test", testTimes);
			System.out.println(dusPop.timeToString(testTimes));
			dusPop.addTime(7, 9, "Test", testTimes);
			System.out.println(dusPop.timeToString(testTimes));

			/*
			DrugUtilizationPopulationLevel dusPop = new DrugUtilizationPopulationLevel();
			dusPop.minSubjectsPerRow = 0;
			dusPop.naivePeriod = 365;
			dusPop.terminationNonUsePeriod = -1;
			dusPop.aggregationKeys.add("ATC7;AgeRange;Gender;Year;Month");
			dusPop.ageCodes.add("0;5;00-04");
			dusPop.ageCodes.add("5;10;05-09");
			dusPop.ageCodes.add("10;15;10-14");
			dusPop.ageCodes.add("15;20;15-19");
			dusPop.ageCodes.add("20;25;20-24");
			dusPop.ageCodes.add("25;30;25-29");
			dusPop.ageCodes.add("30;35;30-34");
			dusPop.ageCodes.add("35;40;35-39");
			dusPop.ageCodes.add("40;45;40-44");
			dusPop.ageCodes.add("45;50;45-49");
			dusPop.ageCodes.add("50;55;50-54");
			dusPop.ageCodes.add("55;60;55-59");
			dusPop.ageCodes.add("60;65;60-64");
			dusPop.ageCodes.add("65;70;65-69");
			dusPop.ageCodes.add("70;75;70-74");
			dusPop.ageCodes.add("75;80;75-79");
			dusPop.ageCodes.add("80;85;80-84");
			dusPop.ageCodes.add("85;999;85-");
			dusPop.init();

			Patient patient = new Patient();
			patient.birthDate = DateUtilities.dateToDays(new int[] { 1926, 1, 1 } );

			System.out.println("01/01/1996: " + dusPop.ageGroupDefinition.getAgeGroupsEstimated(patient.getAgeAtDate(DateUtilities.dateToDays(new int[] { 1996, 1, 1 } ))).get(0));
			System.out.println("02/01/1996: " + dusPop.ageGroupDefinition.getAgeGroupsEstimated(patient.getAgeAtDate(DateUtilities.dateToDays(new int[] { 1996, 1, 2 } ))).get(0));
			System.out.println("03/01/1996: " + dusPop.ageGroupDefinition.getAgeGroupsEstimated(patient.getAgeAtDate(DateUtilities.dateToDays(new int[] { 1996, 1, 3 } ))).get(0));
			*/
/*
			PatientObjectCreator poc = new PatientObjectCreator();

			String testDataPath = "D:/Temp/Jerboa/Drug Utilization/Jerboa All/";
			FilePaths.WORKFLOW_PATH = "D:/Temp/Jerboa/Drug Utilization/Jerboa All/";
			FilePaths.LOG_PATH = FilePaths.WORKFLOW_PATH + "Log/";
			String patientsFile = testDataPath + "Patients.txt";
			String prescriptionsFile = testDataPath + "Prescriptions.txt";
			Logging.prepareOutputLog();

			/* Just testing
			Patient p = new Patient();
			p.ID = "X";
			p.birthDate = DateUtilities.dateToDays(new int[] { 1964, 6, 30 });
			p.gender = DataDefinition.MALE_GENDER;
			DrugUtilizationPopulationLevel dusPop = new DrugUtilizationPopulationLevel();
			dusPop.drugUtilizationPopulationLevelMinSubjectsPerRow = 0;
			dusPop.drugUtilizationPopulationLevelNaivePeriod = 180;
			dusPop.drugUtilizationPopulationLevelTerminationNonUsePeriod = -1;
			dusPop.drugUtilizationPopulationLevelAggregationKeys.add("ATC7;AgeRange;Gender;Year;Month");
			dusPop.drugUtilizationPopulationLevelAggregationKeys.add("ATC5;AgeRange;Gender;Year;Month");
			dusPop.drugUtilizationPopulationLevelAggregationKeys.add("ATC3;AgeRange;Gender;Year;Month");
			dusPop.drugUtilizationPopulationLevelAgeCodes.add("0;5;00-04");
			dusPop.drugUtilizationPopulationLevelAgeCodes.add("5;10;05-09");
			dusPop.drugUtilizationPopulationLevelAgeCodes.add("10;15;10-14");
			dusPop.drugUtilizationPopulationLevelAgeCodes.add("15;20;15-19");
			dusPop.drugUtilizationPopulationLevelAgeCodes.add("20;25;20-24");
			dusPop.drugUtilizationPopulationLevelAgeCodes.add("25;30;25-29");
			dusPop.drugUtilizationPopulationLevelAgeCodes.add("30;35;30-34");
			dusPop.drugUtilizationPopulationLevelAgeCodes.add("35;40;35-39");
			dusPop.drugUtilizationPopulationLevelAgeCodes.add("40;45;40-44");
			dusPop.drugUtilizationPopulationLevelAgeCodes.add("45;50;45-49");
			dusPop.drugUtilizationPopulationLevelAgeCodes.add("50;55;50-54");
			dusPop.drugUtilizationPopulationLevelAgeCodes.add("55;60;55-59");
			dusPop.drugUtilizationPopulationLevelAgeCodes.add("60;65;60-64");
			dusPop.drugUtilizationPopulationLevelAgeCodes.add("65;70;65-69");
			dusPop.drugUtilizationPopulationLevelAgeCodes.add("70;75;70-74");
			dusPop.drugUtilizationPopulationLevelAgeCodes.add("75;80;75-79");
			dusPop.drugUtilizationPopulationLevelAgeCodes.add("80;85;80-84");
			dusPop.drugUtilizationPopulationLevelAgeCodes.add("85;999;85-");
			dusPop.intermediateFiles = true;
			dusPop.intermediateStats = true;
			dusPop.logDebugInfo = 0;

			if (dusPop.initModule("drugUtilizationPopulationLevel.csv")) {
				List<String> keys = dusPop.getKeys("ATC3;AgeRange;Gender;Year;Month", p, "R06AX28", "", DateUtilities.dateToDays(new int[] { 2014, 1, 16 }));
				for (String key : keys)
					System.out.println(key);
			}
			// End testing
			*/
			/*
			PopulationDefinition populationDefinition = new PopulationDefinition();
			populationDefinition.intermediateFiles    = true;
			populationDefinition.intermediateStats    = true;

			if (populationDefinition.init()) {

				populationDefinition.setIntermediateFileName(FilePaths.WORKFLOW_PATH + "Intermediate/populationDefinition.csv");

				CohortDefinition cohortDefinition = new CohortDefinition();
				cohortDefinition.intermediateFiles = true;
				cohortDefinition.intermediateStats = true;

				if (cohortDefinition.init()) {

					cohortDefinition.setIntermediateFileName(FilePaths.WORKFLOW_PATH + "Intermediate/cohortDefinition.csv");

					// Initialize the ExposureDefinition
					ExposureDefinition exposureDefinition = new ExposureDefinition();
					exposureDefinition.intermediateFiles     = true;
					exposureDefinition.intermediateStats     = true;

					if (exposureDefinition.init()) {

						exposureDefinition.setIntermediateFileName(FilePaths.WORKFLOW_PATH + "Intermediate/exposureDefinition.csv");

						DrugUtilizationPopulationLevel dusPop = new DrugUtilizationPopulationLevel();
						dusPop.minSubjectsPerRow = 0;
						dusPop.naivePeriod = 180;
						dusPop.terminationNonUsePeriod = -1;
						dusPop.aggregationKeys.add("ATC7;AgeRange;Gender;Year;Month");
						dusPop.aggregationKeys.add("ATC5;AgeRange;Gender;Year;Month");
						dusPop.aggregationKeys.add("ATC3;AgeRange;Gender;Year;Month");
						dusPop.ageCodes.add("0;5;00-04");
						dusPop.ageCodes.add("5;10;05-09");
						dusPop.ageCodes.add("10;15;10-14");
						dusPop.ageCodes.add("15;20;15-19");
						dusPop.ageCodes.add("20;25;20-24");
						dusPop.ageCodes.add("25;30;25-29");
						dusPop.ageCodes.add("30;35;30-34");
						dusPop.ageCodes.add("35;40;35-39");
						dusPop.ageCodes.add("40;45;40-44");
						dusPop.ageCodes.add("45;50;45-49");
						dusPop.ageCodes.add("50;55;50-54");
						dusPop.ageCodes.add("55;60;55-59");
						dusPop.ageCodes.add("60;65;60-64");
						dusPop.ageCodes.add("65;70;65-69");
						dusPop.ageCodes.add("70;75;70-74");
						dusPop.ageCodes.add("75;80;75-79");
						dusPop.ageCodes.add("80;85;80-84");
						dusPop.ageCodes.add("85;999;85-");
						dusPop.intermediateFiles = true;
						dusPop.intermediateStats = true;

						if (dusPop.init()) {

							Logging.add("");
							Logging.add("  Patient file       = " + patientsFile);
							Logging.add("  Prescriptions file = " + prescriptionsFile);

							try{
								List<Patient> patients = poc.createPatients(patientsFile, null, prescriptionsFile, null);

								if (patients != null && patients.size() > 0) {

									Timer timer = new Timer();
									timer.start();

									int patNr = 0;
									for (Patient patient : patients){
										Patient populationPatient = populationDefinition.process(patient);

										if (populationPatient.inPopulation) {
											Patient cohortPatient = cohortDefinition.process(populationPatient);

											if (cohortPatient.isInCohort()) {
												//Patient exposurePatient = exposureDefinition.processPatient(cohortPatient);
												//dusPop.processPatient(exposurePatient);
												dusPop.process(cohortPatient);
											}
										}

										patNr++;
										if (dusPop.logDebugInfo > 0) System.out.println(patNr + " / " + patients.size());
									}

									Logging.add("");

									populationDefinition.flushOutputBuffer();
									populationDefinition.outputResults();

									cohortDefinition.flushOutputBuffer();
									cohortDefinition.outputResults();

									dusPop.outputResults();

									timer.stop();

									System.out.println("Drug Utilization Population Level of "+ Integer.toString(patients.size()) +" patients run in: "+timer);
								}
							}
							catch(IOException e){
								System.out.println("Error while opening input files");
							}
						}
					}
				}
			}
			*/
		}


}
