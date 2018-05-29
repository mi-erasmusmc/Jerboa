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
package org.erasmusmc.jerboa.modifiers;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
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
import org.erasmusmc.jerboa.engine.Modifier;
import org.erasmusmc.jerboa.modules.viewers.PatientViewer;
import org.erasmusmc.jerboa.utilities.DateUtilities;
import org.erasmusmc.jerboa.utilities.ExtendedMultiKey;
import org.erasmusmc.jerboa.utilities.Logging;
import org.erasmusmc.jerboa.utilities.MultiKeyBag;
import org.erasmusmc.jerboa.utilities.StringUtilities;
import org.erasmusmc.jerboa.utilities.TimeUtilities;
import org.erasmusmc.jerboa.utilities.Wildcard;
import org.erasmusmc.jerboa.utilities.stats.Stats;

/**
 * This module defines the cohort start date and the cohort end date of a patient.
 * It is based on the parameters of the module that are passed in the script file
 * and it serves as a modifier for defining the population that will take part in the study
 * by setting the cohortStartDate and cohortEndDate attributes in the patient object.
 * Note that this modifier is considered to be applied after the PopulationDefinition modifier.
 * It will only check if the patient can be part of the cohort, if it is declared in the population.
 *
 * @see org.erasmusmc.jerboa.dataClasses.Patient
 *
 * @author MG
 *
 */
public class PrescriptionCohortDefinition extends Modifier {

	/**
	 * If true the cohort start and end as defined by a previous cohort definition modifier
	 * is used, otherwise population start and end are used.
	 */
	public boolean chainCohortDefinitions;

	/**
	 * Patient should be naive (not use) for this amount of time (days)
	 * to be included in the study.
	 */
	public int naivePeriod;

	/**
	 * If true the patient should be naive (not use) the drug of interest only.
	 * If false the patient should be naive (not use) the drug of interest and also the drugsEndpoint drugs.
	 */
	public boolean naiveForDrugsOfInterestOnly = true;

	/**
	 * The date of the first occurrence of any of the ATC codes in this list will be used as
	 * cohort start date (index date). Higher level ATC codes are allowed.
	 * If no drugs of interest are specified the population start date will be used as cohort start date (index date).
	 */
	public List<String> drugsOfInterest = new ArrayList<String>();

	/**
	 * List of ATC codes used as exclusion criterion. Higher level ATC codes are allowed.<br>
	 * Format:
	 *
	 *   {@code <ATC>;<window start>;<window end>}<br>
	 *
	 * Patient is excluded when the ATC is used in the period from {@code <window start>} till
	 * {@code <window end>} ({@code <window start>} and {@code <window end>} included).}
	 *  <p>
	 * Examples:<br>
	 *
	 *   {@code R03BB06}          = Patient is excluded when R03BB06 is used anywhere in time<br>
	 *   {@code R03BB06;0;0}      = Patient is excluded when R03BB06 is used at cohort start<br>
	 *   {@code R03BB06;-100;200} = Patient is excluded when R03BB06 is used in the period from
	 *                      100 days before cohort start till 200 days after cohort start.
	 */
	public List<String> drugsExclusion = new ArrayList<String>();

	/**
	 * Patients are included in the cohort only if they have events contained in this list in
	 * the eventsTimewindow interval around the index date.<br>
	 * If no events are specified no patients are excluded based on the presence of events.
	 */
	public List<String> eventsInclusion = new ArrayList<String>();

	/**
	 * The period around the index date to look for events.
	 * Format:
	 * <p>
	 * {@code <start period>;<end period>}
	 * <p>
	 * Start period and end period are defined in days relative to the index date.
	 * A negative number means before the index date and a positive number means after the index date.
	 * A value of {@code ";"} means from any time before to any time after the index date.<br>
	 * Default: {@code ";0"} = all time before the index date.
	 */
	public String eventsTimewindow = ";0";

	/**
	 * Patients exit the cohort when these endpoints occur.
	 * Format:
	 *
	 * <event type> or <event type>;
	 *   The patient exits the cohort when he/she encounters the event but events
	 *   before cohort start are not taken into account.
	 *
	 * <event type>;Incident
	 *   The patient exits the cohort when he/she encounters the event and if the
	 *   event occurs before cohort start the patient is excluded from the cohort.
	 */
	public List<String> eventsEndpoint = new ArrayList<String>();

	/**
	 * List of ATC codes used to define the end of the cohort (switch).
	 * Higher level ATC codes are allowed.<br>
	 */
	public List<String> drugsEndpoint = new ArrayList<String>();

	/**
	 * If true the cohort will end if a drug in the list drugsEndpoint starts.
	 * If false a running drugsEndpoint drug will stop the cohort.
	 */
	public boolean drugsEndpointStartOnly;

	/**
	 * Allow this number of days to overlap as a grace period for switching to the drug of interest.
	 * Effectively this shortens the endpoint drug prescriptions with this number of days.
	 */
	public int drugsEndpointOverlapAllowed;

	/**
	 * Patients who initiate these drugs at the same time as an drug of interest will be excluded.
	 */
	public List<String> drugsConcomittantExclusion = new ArrayList<String>();

	/**
	 * Allow this number of days to overlap as a grace period for switching to the drug of interest.
	 * Effectively this shortens the exclusion drug prescriptions with this number of days.
	 */
	public int drugsExclusionOverlapAllowed;

	/**
	 * Minimum number of days a free combination component should continue in a free combination
	 * drug of interest
	 */
	public int minFreeOverlap;

	/**
	 * The Cohorttime based on a drug of interest is extended with this value.
	 * All end definitions will dominated over this extension, i.e.
	 * if an end drug is found the extended time is trimmed at that date.
	 */
	public int extendCohortTimeDrugsOfInterest;

	/**
	 * Extension is only applied if these components stop (use only for free combination cohorts)
	 */
	public List<String> extensionStopAtcs = new ArrayList<String>();

	/**
	 * If true the module continues searching until a cohort is found that fulfills all criteria,
	 * otherwise it stops at the first naive drug of interest.
	 * For cohort definitions from before Jerboa version 2.7.5.1 this option should be set to false.
	 */
	public boolean extendedCohortSearch;

	/**
	 * If true prescriptions that start at the same date are sorted with longest duration first (independent of ATC).
	 * For cohort definitions from before Jerboa version 2.7.5.1 this option should be set to false.
	 */
	public boolean sortPrescriptionsByDuration;

	/**
	 * If true an attrition filename will be created or appended on called Attrition.csv
	 */
	public boolean attritionFile;


	//PRIVATE PARAMETERS
	//events timewindow limits
	private int eventsTimewindowStart = Integer.MIN_VALUE;
	private int eventsTimewindowEnd   = Integer.MAX_VALUE;

	//event endpoints
	private Set<String> eventEndPoints = null;
	private Set<String> incidentEventEndPoints = null;

	//counters
	private int countRemoved;                           // keeps track of the patients that are not in the cohort
	private int originalCount;                          // keeps track of records in the intermediate file
	private int countEndpointDrugOfInterest;            // keeps track of all patients having the endpoint drug
	private int countNaiveEndpointDrugOfInterest;       // keeps track of all patients having the endpoint drug with the required naive period
	private int countDrugOfInterest;                    // keeps track of all patients having the drug of interest
	private int countNaiveDrugOfInterest;               // keeps track of all patients having the drug of interest with the required naive period
	private int countEventOfInterest;			        // keeps track of all patients having the event of interest
	private MultiKeyBag patientTime;                    // keeps track of the total patient time
	private Map<String, Integer> drugsExclusionStarts;
	private Map<String, Integer> drugsExclusionEnds;
	private int cohortEndUnCensored = -1;

	private String attritionFileName;   //used to save the attrition

	private List<Patient> debugPatients = new ArrayList<Patient>();

	private boolean runAsApplication = false;


	private List<Prescription> patientPrescriptions;
	private int start;
	private int end;
	private String reasonCohortFound;
	private String reasonCohortEnd;
	private int cohortCount;
	private int lastEndDate;
	private int lastEndDateDrugOfInterest;
	private boolean freeDrugOfInterest;
	private HashSet<String> componentStops;

	@Override
	public void setNeededFiles() {
		setRequiredFile(DataDefinition.PATIENTS_FILE);
		if ((eventsEndpoint.size() > 0) || (eventsInclusion.size() > 0))
			setRequiredFile(DataDefinition.EVENTS_FILE);
		if ((drugsOfInterest.size() > 0) || (drugsEndpoint.size() > 0) || (drugsExclusion.size() > 0) || (drugsConcomittantExclusion.size() > 0))
			setRequiredFile(DataDefinition.PRESCRIPTIONS_FILE);
	}

	@Override
	public void setNeededExtendedColumns() { /* NOTHING TO ADD */ }

	@Override
	public void setNeededNumericColumns() { /* NOTHING TO ADD */ }

	@Override
	public boolean init() {
		boolean initOK = true;

		eventsTimewindowStart = Integer.MIN_VALUE;
		eventsTimewindowEnd   = Integer.MAX_VALUE;

		if (!eventsTimewindow.equals("")) {
			String[] eventsTimewindowSplit = eventsTimewindow.split(";");
			if ((eventsTimewindowSplit.length > 0) && (!eventsTimewindowSplit[0].equals(""))) {
				try {
					eventsTimewindowStart = Integer.parseInt(eventsTimewindowSplit[0]);
				} catch (NumberFormatException e) {
					Logging.add("Illegal value for start eventsTimewindow: " + eventsTimewindowSplit[0]);
					initOK = false;
				}
			}
			if ((eventsTimewindowSplit.length > 1) && (!eventsTimewindowSplit[1].equals(""))) {
				try {
					eventsTimewindowEnd = Integer.parseInt(eventsTimewindowSplit[1]);
				} catch (NumberFormatException e) {
					Logging.add("Illegal value for end eventsTimewindow: " + eventsTimewindowSplit[1]);
					initOK = false;
				}
			}
		}

		eventEndPoints = new HashSet<String>();
		incidentEventEndPoints = new HashSet<String>();
		for (String eventEndPoint : eventsEndpoint) {
			String[] eventEndPointSplit = eventEndPoint.split(";");
			if (eventEndPointSplit.length > 0) {
				String eventType = eventEndPointSplit[0];
				if (eventEndPoints.add(eventType)) {
					if ((eventEndPointSplit.length > 1) && (eventEndPointSplit[1].toLowerCase().equals("incident"))) {
						incidentEventEndPoints.add(eventType);
					}
				}
				else {
					Logging.add("Multiple evenEndPoint definitions for event type " + eventType);
					initOK = false;
				}
			}
		}

		drugsExclusionStarts = new HashMap<String, Integer>();
		drugsExclusionEnds = new HashMap<String, Integer>();
		if (drugsExclusion.size() > 0) {
			for (String exclusionDefinition : drugsExclusion) {
				String[] exclusionDefinitionSplit = exclusionDefinition.split(";");
				int windowStart = Integer.MIN_VALUE;
				int windowEnd = Integer.MAX_VALUE;
				if (!exclusionDefinitionSplit[0].equals("")) {
					if ((exclusionDefinitionSplit.length > 1) && (!exclusionDefinitionSplit[1].equals(""))) {
						try {
							windowStart = Integer.parseInt(exclusionDefinitionSplit[1]);
						} catch (NumberFormatException e) {
							Logging.add("Illegal window start in drugsExclusion: " + exclusionDefinition);
							initOK = false;
						}
					}
					if ((exclusionDefinitionSplit.length > 2) && (!exclusionDefinitionSplit[2].equals(""))) {
						try {
							windowEnd = Integer.parseInt(exclusionDefinitionSplit[2]);
						} catch (NumberFormatException e) {
							Logging.add("Illegal window end in drugsExclusion: " + exclusionDefinition);
							initOK = false;
						}
					}
					if (windowStart > windowEnd) {
						Logging.add("Window start after window end in drugsExclusion: " + exclusionDefinition);
					}
					else {
						drugsExclusionStarts.put(exclusionDefinitionSplit[0], windowStart);
						drugsExclusionEnds.put(exclusionDefinitionSplit[0], windowEnd);
					}
				}
				else {
					Logging.add("Mising ATC-code in drugsExclusion: " + exclusionDefinition);
					initOK = false;
				}
			}
		}

		if (eventsTimewindowStart >= eventsTimewindowEnd)
			initOK = false;

		patientTime = new MultiKeyBag();

		if ((!Jerboa.unitTest) && intermediateFiles) {
			String header = "SubsetID";
			header += "," + "PatientID";
			header += "," + "Birthdate";
			header += "," + "Gender";
			header += "," + "Startdate";
			header += "," + "Enddate";
			header += "," + "PopulationStart";
			header += "," + "PopulationEnd";
			header += "," + "CohortStart";
			header += "," + "CohortEnd";
			header += "," + "ReasonInCohort";
			header += "," + "ReasonCohortEnd";
			initOK &= Jerboa.getOutputManager().addFile(this.intermediateFileName);
			Jerboa.getOutputManager().writeln(this.intermediateFileName, header, false);
		}

		if (initOK && attritionFile && (!Jerboa.unitTest)) {
			attritionFileName = FilePaths.WORKFLOW_PATH+this.getParentModule()+"/"+
					Parameters.DATABASE_NAME+"_"+this.getParentModule()+
					"_"+TimeUtilities.TIME_STAMP+"_"+Parameters.VERSION+"_attrition.csv";
			Jerboa.getOutputManager().addFile(attritionFileName, 100);
		}

		// Initialize counters
		countRemoved = 0;                     // keeps track of the patients that are not in the cohort
		originalCount = 0;                    // keeps track of records in the intermediate file
		countEndpointDrugOfInterest = 0;      // keeps track of all patients having the endpoint drug
		countNaiveEndpointDrugOfInterest = 0; // keeps track of all patients having the endpoint drug with the required naive period
		countDrugOfInterest = 0;              // keeps track of all patients having the drug of interest
		countNaiveDrugOfInterest = 0;         // keeps track of all patients having the drug of interest with the required naive period
		countEventOfInterest = 0;			  // keeps track of all patients having the event of interest

		return initOK;
	}


	/**
	 * Sets the cohort start date and cohort end date for the patient passed as argument
	 * based on the parameter settings present in the script file.
	 * The cohort belonging is defined based on several parameters, including drugs of interest or exclusion
	 * as well as events of interest or exclusion.
	 * @param patient - the patient to be checked if it will be part of the cohort or not
	 * @return patient - the patient object with a defined cohort start date and cohort end date
	 */
	@Override
	public Patient process(Patient patient) {
		cohortEndUnCensored = -1;
		reasonCohortFound = "";
		reasonCohortEnd = "";
		cohortCount = 0;

		patientPrescriptions = new ArrayList<Prescription>();
		for (Prescription prescription : patient.getPrescriptions()) {
			patientPrescriptions.add(prescription);
		}

		if (sortPrescriptionsByDuration) {
			// Sort the prescriptions on date with longest duration on same date first.
			Collections.sort(patientPrescriptions, Prescription.sortByDateAndDuration);
		}

		if (patient != null && (chainCohortDefinitions ? patient.isInCohort() : patient.isInPopulation())) {
			if (!inPostProcessing) originalCount++;

			boolean cohortFound = false;

			int startBase = patient.getPopulationStartDate();
			int endBase   = patient.getPopulationEndDate();
			start     = -1;
			end       = -1;

			if (chainCohortDefinitions) {
				startBase = patient.getCohortStartDate();
				endBase   = patient.getCohortEndDate();
				start     = -1;
				end       = -1;
			}

			int currentPrescriptionNr = 0;
			lastEndDate = 0;
			lastEndDateDrugOfInterest = 0;
			componentStops = new HashSet<String>();

			if (drugsOfInterest.size() > 0) {
				//TODO: only the first free combination is checked. To decide and fix
				freeDrugOfInterest = false;
				if (drugsOfInterest.get(0).substring(0, 1).equals("_") && drugsOfInterest.get(0).contains("/"))
					freeDrugOfInterest = true;

				while ((!cohortFound) && (currentPrescriptionNr < patientPrescriptions.size())) {
					currentPrescriptionNr = getNextNaiveDrugOfInterest(patient, currentPrescriptionNr, startBase, endBase);
					if (currentPrescriptionNr < patientPrescriptions.size()) { // Naive drug of interest found
						Prescription currentPrescription = patientPrescriptions.get(currentPrescriptionNr);

						if (eventInclusion(patient)) {
							start = currentPrescription.getStartDate();
							end = currentPrescription.getEndDate();
							cohortEndUnCensored = end;
							if (patient.getPopulationEndDate() < end) {
								end = patient.getPopulationEndDate();
								reasonCohortEnd += cohortCount + ": PopulationEnd  ";
							}

							if (start < end) {
								//Extend cohort here not above to have correct naive check
								if (!freeDrugOfInterest)
									end += extendCohortTimeDrugsOfInterest;
								else {
									//check which drug need to stop at the same time to have an extension
									boolean extend = true;
									for (String atc : extensionStopAtcs) {
										if (!componentStops.contains(atc)) {
											extend = false;
											break;
										}
									}

									if (extend)
										end += extendCohortTimeDrugsOfInterest;
								}

								if (end > endBase) {
									end = endBase;
									reasonCohortEnd += cohortCount + ": PopulationEnd  ";
								}

								checkDrugEndPoints(patient);

								if (start < end) {
									checkEventEndPoints(patient);

									if (start < end) {
										if (!drugExclusion(patient)) {
											if (start < end) {
												cohortFound = true;
											}
										}
									}
								}
							}
						}

						if (!extendedCohortSearch) {
							break;
						}

						currentPrescriptionNr++;
					}
					else {
						if (reasonCohortEnd.equals("")) {
							reasonCohortEnd = "No naive drug of interest found";
						}
					}
				}
			}
			else {
				// No drugs of interest -> use population start and end
				start = startBase;
				end   = endBase;

				if (eventInclusion(patient)) {
					cohortCount++;
					reasonCohortFound += cohortCount + ": No DrugOfInterest specified  ";
					if (patient.getPopulationEndDate() < end) {
						end = patient.getPopulationEndDate();
						reasonCohortEnd += cohortCount + ": PopulationEnd  ";
					}

					if (start < end) {
						//Extend cohort here not above to have correct naive check
						if (!freeDrugOfInterest)
							end += extendCohortTimeDrugsOfInterest;
						else {
							//check which drug need to stop at the same time to have an extension
							boolean extend = true;
							for (String atc : extensionStopAtcs) {
								if (!componentStops.contains(atc)) {
									extend = false;
									break;
								}
							}

							if (extend)
								end += extendCohortTimeDrugsOfInterest;
						}

						if (end > endBase) {
							end = endBase;
							reasonCohortEnd += cohortCount + ": PopulationEnd  ";
						}

						checkDrugEndPoints(patient);

						if (start < end) {
							checkEventEndPoints(patient);

							if (start < end) {
								if (!drugExclusion(patient)) {
									if (start < end) {
										cohortFound = true;
									}
								}
							}
						}
					}
				}
			}

			if (cohortFound) {
				// Set cohort start and end
				patient.setCohortStartDate(start);
				patient.setCohortEndDate(end);
				patient.setInCohort(true);

				//update patient time
				if (intermediateStats)
					patientTime.add(new ExtendedMultiKey("ALL", patient.cohortEndDate - patient.cohortStartDate));
			}
			else {
				if (reasonCohortEnd.equals("")) {
					reasonCohortEnd = "No cohort found";
				}
				patient.setCohortStartDate(-1);
				patient.setCohortEndDate(-1);
				patient.setInCohort(false);
				if (!inPostProcessing) countRemoved++;
			}
		}
		else {
			reasonCohortEnd = "Not in original cohort";
		}

		if (!Jerboa.unitTest) {
			// Add patient in the cohort to output file
			if (intermediateFiles) {
				addToOutputBuffer(this.intermediateFileName, patient, reasonCohortFound + "," + reasonCohortEnd);
			}

			if (patient.inCohort) {
				if (Jerboa.getResultSet().isOnlyInCohort()) {
					Jerboa.getResultSet().add(patient.getPatientID(),"PopulationStart", (DateUtilities.daysToDate(patient.getPopulationStartDate())));
					Jerboa.getResultSet().add(patient.getPatientID(),"PopulationEnd", (DateUtilities.daysToDate(patient.getPopulationEndDate())));
				}
				Jerboa.getResultSet().add(patient.getPatientID(),"CohortStart", (DateUtilities.daysToDate(patient.getCohortStartDate())));
				Jerboa.getResultSet().add(patient.getPatientID(),"CohortEnd", (DateUtilities.daysToDate(patient.getCohortEndDate())));
				Jerboa.getResultSet().add(patient.getPatientID(),"CohortTime", String.valueOf(patient.getCohortTime()));
			}
			else {
				if (!Jerboa.getResultSet().isOnlyInCohort()) {
					Jerboa.getResultSet().add(patient.getPatientID(),"CohortStart", Jerboa.getResultSet().getMissingValue());
					Jerboa.getResultSet().add(patient.getPatientID(),"CohortEnd", Jerboa.getResultSet().getMissingValue());
					Jerboa.getResultSet().add(patient.getPatientID(),"CohortTime", "0");
				}
			}
		}

		return patient;
	}


	private int getNextNaiveDrugOfInterest(Patient patient, int currentIndex, int startBase, int endBase) {
		int index = currentIndex;
		while (index < patientPrescriptions.size()) {
			Prescription prescription = patientPrescriptions.get(index);
			int prescriptionEnd = prescription.getEndDate();
			String prescriptionAtc = prescription.getATC();

			// Check if drug of interest
			boolean found = false;

			for (String atc : drugsOfInterest) {
				if (((!atc.substring(0, 1).equals("_")) && prescription.startsWith(atc)) || ((atc.substring(0, 1).equals("_")) && prescription.getATC().equals(atc))) {
					found = true;
					if (!inPostProcessing) countDrugOfInterest++;
					break;
				}
			}

		    //Check if this is a generated combination ("_") and check if it contains free combination components ("/")
			if (freeDrugOfInterest && prescription.getATC().equals(drugsOfInterest.get(0))){

				//if this episode is shorter than the minFreeOverlap period we need to check if the free combination criterium is met:
				//1. all start
				//2. those that did not start still continue for at least minFreeOverlap days


				//TODO: it would be a lot easier if the prescriptionCombination would create these numbers already!! discuss
				int drugsCont  = 0;
				int drugsStart = 0;

				//check if all of the components either start or continue for at least minFreeDays
				List<String> components = Arrays.asList(prescription.getATC().substring(1).split("/"));
				componentStops.clear();
				for (Prescription overlapPrescription : patientPrescriptions){
					//count the number of components that are starting and continuing.
					if (components.contains(overlapPrescription.getATC().substring(1)) &&
							overlapPrescription.getStartDate() < prescription.getStartDate() &&
							(overlapPrescription.getEndDate() > (prescription.getStartDate() + minFreeOverlap))){
						drugsCont ++;
					}
					if (components.contains(overlapPrescription.getATC().substring(1)) &&
							overlapPrescription.getStartDate() == prescription.getStartDate()){
						drugsStart ++;
					}
					//determine which ATCs stop at the end of this episode
					if (components.contains(overlapPrescription.getATC().substring(1)) &&
							overlapPrescription.getEndDate() == prescription.getEndDate())
						componentStops.add(overlapPrescription.getATC());
				}

				if (!(drugsStart + drugsCont == components.size())){
					found = false;
				}
			}

			// If drug of interest determine start and end
			if (found) {  // (found && (start == -1))
				int prescriptionStart = prescription.date;
				if (prescriptionStart < startBase) {
					lastEndDate = prescriptionEnd;
					lastEndDateDrugOfInterest = Math.max(lastEndDateDrugOfInterest,prescriptionEnd); //earlier prescriptions can end later!

					if (runAsApplication) {
						Logging.add("    Drug of interest " + prescriptionAtc + " " + DateUtilities.daysToDate(prescription.date) + " - " + DateUtilities.daysToDate(prescription.getEndDate()) + " before population start");
					}
				}
				else {
					if ((prescriptionStart < endBase) && (prescriptionEnd > startBase)) {
						// 16/02/2017 Mees Mosseveld
						// Moved test naive for non-drugOfInterest here.
						if (((prescriptionStart - lastEndDateDrugOfInterest) >= naivePeriod) && (naiveForDrugsOfInterestOnly || ((prescriptionStart - lastEndDate) >= naivePeriod))) {
							start = prescriptionStart;
							end = prescriptionEnd;
							cohortCount++;
							reasonCohortFound += cohortCount + ": DrugOfInterest: " + prescription.toString().replaceAll(",", " ") + "  ";
							if (end > endBase) {
								end = endBase;
								reasonCohortEnd += cohortCount + ": PopulationEnd  ";
							}

							if ((prescriptionStart < end) && (prescriptionEnd > start)) {
								if (!inPostProcessing) countNaiveDrugOfInterest++;
							}
							lastEndDateDrugOfInterest = Math.max(lastEndDateDrugOfInterest,prescriptionEnd); //earlier prescriptions can end later!
							break;
						}
						else {
							lastEndDateDrugOfInterest = Math.max(lastEndDateDrugOfInterest, prescriptionEnd);
						}
					}
				}
			}

			lastEndDate = Math.max(lastEndDate, prescriptionEnd);

			index++;
		}
		return index;
	}


	private boolean eventInclusion(Patient patient) {
		boolean include = true;

		if (eventsInclusion.size() > 0) {
			include = false;
			for (Event event : patient.getEvents()) {
				String type = event.getType();

				// Check for inclusion events.
				if (
						(!eventsTimewindow.equals("")) &&
						((eventsTimewindowStart == Integer.MIN_VALUE) || (event.date >= (start + eventsTimewindowStart))) &&
						((eventsTimewindowEnd == Integer.MAX_VALUE) || (event.date <= (start + eventsTimewindowEnd)))
					) {
					for (String eventType : eventsInclusion) {
						if (type.equals(eventType)) {
							include = true;
							if (runAsApplication) {
								Logging.add("      Inclusion event " + type + " " + DateUtilities.daysToDate(event.date));
							}
							if (!inPostProcessing) countEventOfInterest++;
							break;
						}
					}
				}

				if (include)
					break;
			}
		}
		else {
			if (!inPostProcessing) countEventOfInterest++;
		}
		if (!include) {
			reasonCohortEnd += cohortCount + ": No InclusionEvent  ";
		}

		return include;
	}


	private void checkDrugEndPoints(Patient patient) {
		for (Prescription prescription : patientPrescriptions) {

			for (String atc : drugsEndpoint) {
				if (((!atc.substring(0, 1).equals("_")) && prescription.startsWith(atc)) || ((atc.substring(0, 1).equals("_")) && prescription.getATC().equals(atc))) {
					if (prescription.getStartDate() >= start) {
						if (end > prescription.getStartDate()) {
							end = prescription.getStartDate();
							reasonCohortEnd += cohortCount + ": DrugEndPoint: " + prescription.toString().replaceAll(",", " ") + "  ";
						}
					}
					else if ((!drugsEndpointStartOnly) && ((prescription.getEndDate() - drugsEndpointOverlapAllowed) >= start)) {
						end = start;
						reasonCohortEnd += cohortCount + ": DrugEndPoint overlap: " + prescription.toString().replaceAll(",", " ") + "  ";
					}
					if (end <= start) {
						break;
					}
				}
			}
			if (end <= start) {
				break;
			}
		}
	}


	private void checkEventEndPoints(Patient patient) {
		for (Event event : patient.getEvents()) {
			String type = event.getType();

			// Adjust end point based on event.
			if (eventEndPoints.contains(type)) {
				if (incidentEventEndPoints.contains(type) && (event.date < start)) {
					// Event excluded. Is before cohort start
					start = -1;
					end = -1;
					reasonCohortEnd += cohortCount + ": EventEndPoint: " + event.toString().replaceAll(",", " ") + "  ";
					break;
				}
				else if (event.date >= start) {
					// Cohort end adjusted
					if (event.date < end) {
						end = event.getDate();
						reasonCohortEnd += cohortCount + ": EventEndPoint: " + event.toString().replaceAll(",", " ") + "  ";
					}
				}
			}
		}
	}


	private boolean drugExclusion(Patient patient) {
		boolean exclude = false;

		for (Prescription prescription : patientPrescriptions) {
			Prescription testPrescription = new Prescription(prescription);

			//Only allow overlap if this drug was not started on the same day as the drug of interest.

			int drugsCont = 0;
			int drugsStart = 0;
			if (drugsExclusionStarts.containsKey(testPrescription.getATC())){

				//Need to check these criteria:
				//We need to distinguish fixed combinations from free combinations
				//Fixed:
				//1. if it started before it is allowed to overlap for drugsExclusionOverlapAllowed
				//2. if it starts at the current cohort start the patient should not enter

				//Free:
				//1. if it started before it is allowed to overlap for drugsExclusionOverlapAllowed
				//2. if one of the components (other then the current cohort drug) starts and the others
				//   continue for at least X days patient should not enter


				if (testPrescription.getCombinationStartDate() != start)
					if (!testPrescription.getATC().contains("/"))
						testPrescription.setDuration(Math.max(0, prescription.getDuration() - drugsExclusionOverlapAllowed));
					else {

						//Only necessary to check if this prescription starts at the current Cohort Start
						if (testPrescription.getStartDate() == start && testPrescription.getDuration() <= minFreeOverlap){
							//check if all of the components either start or continue for at least X days
							List<String> components = Arrays.asList(testPrescription.getATC().substring(1).split("/"));

							for (Prescription overlapPrescription : patientPrescriptions){
								//count the number of components that are starting and continuing.
									if (components.contains(overlapPrescription.getATC().substring(1)) &&
											overlapPrescription.getStartDate() < start &&
											(overlapPrescription.getEndDate() > (start + minFreeOverlap))){
										drugsCont ++;
									}
									if (components.contains(overlapPrescription.getATC().substring(1)) &&
											overlapPrescription.getStartDate() == start){
										drugsStart ++;
									}
							}
							if (!(drugsStart + drugsCont == components.size()))
								testPrescription.setDuration(Math.max(0, prescription.getDuration() - drugsExclusionOverlapAllowed));
						} else
							testPrescription.setDuration(Math.max(0, prescription.getDuration() - drugsExclusionOverlapAllowed));
					}
			}

			// Check for exclusion based on drugs.
			for (String atc : drugsExclusionStarts.keySet()) {
				if (	(((!atc.substring(0, 1).equals("_")) && testPrescription.startsWith(atc)) || ((atc.substring(0, 1).equals("_")) && testPrescription.getATC().equals(atc))) &&
						(testPrescription.getStartDate() <= (start + drugsExclusionEnds.get(atc))) &&
						(testPrescription.getEndDate() > (start + drugsExclusionStarts.get(atc))) &&
						testPrescription.getDuration()>0
					) {
					exclude = true;
					reasonCohortEnd += cohortCount + ": DrugExclusion: " + prescription.toString().replaceAll(",", " ") + "  ";
					break;
				}
			}

			// Exclude patients based on concomittant drugs.
			for (String atc : drugsConcomittantExclusion) {
				if ((prescription.date <= end) && (prescription.getEndDate() >= start) && (((!atc.substring(0, 1).equals("_")) && prescription.startsWith(atc)) || ((atc.substring(0, 1).equals("_")) && prescription.getATC().equals(atc)))) {
					exclude = true;
					reasonCohortEnd += cohortCount + ": ConcomittantDrugExclusion: " + prescription.toString().replaceAll(",", " ") + "  ";
					break;
				}
			}
		}

		return exclude;
	}


	@Override
	public void outputResults(){

		flushRemainingData();

		if (!debugPatientIDs.isEmpty()){
			Collections.sort(debugPatients);
			new PatientViewer(debugPatients,null, this.getTitle() + " ("+debugPatients.size()+" patients)");
		}

		if (countRemoved == Stats.nbPatients){
			Logging.add("There are no patients left in the cohort.");
		}
		else if (intermediateStats){
			Logging.add("Population size: " + originalCount);
			Logging.add("Cohort size: "+ (originalCount - countRemoved));
			Logging.add("Total patient time in cohort: " +StringUtilities.format((float)patientTime.getHistogramStats(new ExtendedMultiKey("ALL", Wildcard.INTEGER())).getSum()/(float)DateUtilities.daysPerYear)+" years ");
			Logging.addNewLine();
		}

		if ((!Jerboa.unitTest) && attritionFile) {
			Jerboa.getOutputManager().writeln(attritionFileName, title + " (CohortDefinition),", true);
			Jerboa.getOutputManager().writeln(attritionFileName, "Included patients," + (originalCount - countRemoved),true);
			Jerboa.getOutputManager().writeln(attritionFileName, " ,",true);
			Jerboa.getOutputManager().flush(attritionFileName);
		}

		if ((!Jerboa.unitTest) && intermediateFiles) {
			Jerboa.getOutputManager().closeFile(intermediateFileName);
		}
	}

	//GETTERS
	public int getCountDrugOfInterest() {
		return countDrugOfInterest;
	}

	public int getCountNaiveDrugOfInterest() {
		return countNaiveDrugOfInterest;
	}

	public int getCountEndpointDrugOfInterest() {
		return countEndpointDrugOfInterest;
	}

	public int getCountNaiveEndpointDrugOfInterest() {
		return countNaiveEndpointDrugOfInterest;
	}

	public int getCountEventOfInterest() {
		return countEventOfInterest;
	}

	public int getCohortEndUnCensored() {
		// Return the uncensored cohort end of the current patient.
		return cohortEndUnCensored;
	}

}
