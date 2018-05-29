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
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

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
import org.erasmusmc.jerboa.utilities.Item;
import org.erasmusmc.jerboa.utilities.ItemList;
import org.erasmusmc.jerboa.utilities.Logging;
import org.erasmusmc.jerboa.utilities.MultiKeyBag;
import org.erasmusmc.jerboa.utilities.StringUtilities;
import org.erasmusmc.jerboa.utilities.TimeUtilities;
import org.erasmusmc.jerboa.utilities.Wildcard;
import org.erasmusmc.jerboa.utilities.stats.HistogramStats;
import org.erasmusmc.jerboa.utilities.stats.Stats;

/**
 * This module defines the cohort start date and the cohort end date of a patient based
 * on the date of an event
 *
 * @see org.erasmusmc.jerboa.dataClasses.Patient
 *
 * @author MG
 *
 */
public class EventCohortDefinition extends Modifier{

	/**
	 * If true the cohort start and end as defined by a previous cohort definition modifier
	 * is used, otherwise population start and end are used.
	 */
	public boolean chainCohortDefinitions;

	/**
	 * The date of the first occurrence of any of the events in this list will be used as
	 * cohort start date (index date). <br>
	 * Format:
	 *
	 *   eventType;cohortStart;cohortEnd
	 *
	 * where:
	 *
	 *   eventType   = The type of event.
	 *   cohortStart = The start of the cohort relative to the event date in days (negative is before, positive is after).
	 *                 When empty or not present the cohort starts at the event date.
	 *   cohortEnd   = The end of the cohort relative to the event date in days (negative is before, positive is after).
	 *                 When empty or not present the cohort ends at the population end date.
	 */
	public List<String> eventsOfInterest = new ArrayList<String>();

	/**
	 * If true the event is also search for in the full history of the patient
	 * An extra field is added to the patient object called isPrevalent if the first
	 * occurence of the event with the right drugInclusion is before population start
	 */
	public boolean allHistory;

	/**
	 * If true the cohort start is set to population start in case of a prevalent case
	 */
	public boolean prevalentCaseAtPopulationStart;

	/**
	 * List of ATC codes used as inclusion criterion. Higher level ATC codes are allowed.
	 */
	public List<String> drugsInclusion = new ArrayList<String>();

	/**
	 * The cohort start will be moved to the first moment one of these drugs are used if this is
	 * later than the cohort start based on the event.
	 * <p>
	 * {@code ATC;dose;minduration}<br>
	 * {@code dose} -  if empty it is ignored
	 */
	public List<String> drugDose = new ArrayList<String>();

	/**
	 * Defines the minimum duration of the episode. If not equal to zero the cohort start is moved
	 * to prescription start + minimum duration.<br>
	 * Default = 0;
	 */
	public int minDurationDose;

	/**
	 * The number of days before and after the event this drug should be present
	 * for example:
	 * <p>
	 * {@code -90;90} means {@code eventDate-90 <= precrptionDate <= eventDate+90}
	 * <p>
	 * empty fields mean no restriction<br>
	 * for example:<br>
	 * {@code ;90} means full history
	 */
	public String drugsInclusionTimeWindow;

	/**
	 * Patients do not enter the cohort when these endpoints occur in the defined
	 * window around cohortStart as defined by the eventsOfInterest and drugsInclusion steps.<br>
	 * <p>
	 * {@code Eventtype;windowStart;windowEnd;}
	 * <p>
	 * Default: none
	 */
	public List<String> eventsExclusion = new ArrayList<String>();

	/**
	 * The minimum amount of days that the patient has in the cohort to be included.
	 */
	public int minimumDaysOfCohortTime;

	/**
	 * If this event1 is present before the first event2 then case is Prevalent and the CohortStart is equal to
	 * Population Start even if event1 is after population start
	 * event1;event2;
	 */
	public List<String> setPrevalent = new ArrayList<String>();

	/**
	 * If true an attrition filename will be created or appended on called Attrition.csv
	 */
	public boolean attritionFile;

	/**
	 * If true only incident cases are allowed in the cohort
	 */
	public boolean onlyIncidentCases;

	//events timewindow limits
	private int inclusionDrugsTimewindowStart = Integer.MIN_VALUE;
	private int inclusionDrugsTimewindowEnd   = Integer.MAX_VALUE;

	//cohort windows of the event types
	private List<String> eventTypesOfInterest;
	private Map<String, Integer> eventCohortWindowStart;
	private Map<String, Integer> eventCohortWindowEnd;

	//counters
	private int countRemoved;                     // keeps track of the patients that are not in the cohort
	private int prevalentCount;
	private int incidentCount;
	private int originalCount;                    // keeps track of records in the intermediate file
	private int includedPatientsCount;
	private int eventOfInterestCount;
	private int inclusionDrugCount;
	private int drugDoseCount;
	private int eventTypeNoExclusionCount;
	private int minimumDaysExclusionCount;

	private MultiKeyBag patientTime;                  // keeps track of the total patient time

	private Map<String, String> drugsInclusionDose;
	private List<Patient> debugPatients = new ArrayList<Patient>();

	private boolean debug = false;					  // if true debug information is added to the console
	int topCount = 0;

	private String attritionFileName;   //used to save the attrition

	private ItemList eventsEndPointList = new ItemList(3);
	private ItemList setPrevalentList = new ItemList(2);

	private String indexEventType = "";

	@Override
	public void setNeededFiles() {
		setRequiredFile(DataDefinition.PATIENTS_FILE);
		setRequiredFile(DataDefinition.EVENTS_FILE);
		if ((drugsInclusion.size() > 0))
			setRequiredFile(DataDefinition.PRESCRIPTIONS_FILE);
	}

	@Override
	public void setNeededExtendedColumns() { /* NOTHING TO ADD */ }

	@Override
	public void setNeededNumericColumns() {
		// TODO Auto-generated method stub

	}

	@Override
	public boolean init() {

		patientTime = new MultiKeyBag();

		boolean initOK = true;

		eventTypesOfInterest = new ArrayList<String>();;
		eventCohortWindowStart = new HashMap<String, Integer>();
		eventCohortWindowEnd = new HashMap<String, Integer>();
		for (String eventOfInterest : eventsOfInterest) {
			String[] eventOfInterestSplit = eventOfInterest.trim().split(";");
			String eventType = eventOfInterestSplit[0].trim();
			if (eventType.equals("")) {
				Logging.add("Empty event type in eventsOfInterest: " + eventOfInterest);
				initOK = false;
			}
			else {
				eventTypesOfInterest.add(eventType);
				Integer cohortStart = null;
				Integer cohortEnd = null;
				if ((eventOfInterestSplit.length > 1) && (!eventOfInterestSplit[1].trim().equals(""))) {
					try {
						cohortStart = Integer.parseInt(eventOfInterestSplit[1].trim());
						eventCohortWindowStart.put(eventType, cohortStart);
					}
					catch (NumberFormatException e) {
						Logging.add("Illegal cohort start (" + eventOfInterestSplit[1] + ") in eventsOfInterest: " + eventOfInterest);
						initOK = false;
					}
				}
				if ((eventOfInterestSplit.length > 2) && (!eventOfInterestSplit[2].trim().equals(""))) {
					try {
						cohortEnd = Integer.parseInt(eventOfInterestSplit[2].trim());
						eventCohortWindowEnd.put(eventType, cohortEnd);
					}
					catch (NumberFormatException e) {
						Logging.add("Illegal cohort start (" + eventOfInterestSplit[2] + ") in eventsOfInterest: " + eventOfInterest);
						initOK = false;
					}
				}
				if ((cohortStart != null) && (cohortEnd != null) && (cohortEnd <= cohortStart)) {
					Logging.add("Cohort start and cohort end may not be the same in eventsOfInterest: " + eventOfInterest);
					initOK = false;
				}
			}
		}

		//parse the drugsInclusionTimeWindow
		if (!drugsInclusionTimeWindow.equals("")) {
			String[] drugsTimewindowSplit = drugsInclusionTimeWindow.split(";");
			if ((drugsTimewindowSplit.length > 0) && (!drugsTimewindowSplit[0].equals(""))) {
				try {
					inclusionDrugsTimewindowStart = Integer.parseInt(drugsTimewindowSplit[0]);
				} catch (NumberFormatException e) {
					Logging.add("Illegal value for start eventsTimewindow: " + drugsTimewindowSplit[0]);
					initOK = false;
				}
			}
			if ((drugsTimewindowSplit.length > 1) && (!drugsTimewindowSplit[1].equals(""))) {
				try {
					inclusionDrugsTimewindowEnd = Integer.parseInt(drugsTimewindowSplit[1]);
				} catch (NumberFormatException e) {
					Logging.add("Illegal value for end eventsTimewindow: " + drugsTimewindowSplit[1]);
					initOK = false;
				}
			}
		}

		drugsInclusionDose = new HashMap<String, String>();
		for (String drugOfInterest: drugDose){
			String[] drugOfInterestSplit = drugOfInterest.split(";");
			String atc =  drugOfInterestSplit[0];
			String dose = "";
			if (drugOfInterestSplit.length > 1)
				dose =  drugOfInterestSplit[1];
			drugsInclusionDose.put(atc, dose);
		}

		if (intermediateFiles && (!Jerboa.unitTest)) {
			initOK &= Jerboa.getOutputManager().addFile(this.intermediateFileName);
			Jerboa.getOutputManager().writeln(this.intermediateFileName, "PatientID,Gender,Birthdate,Startdate," +
						"Enddate,PopulationStart,PopulationEnd,CohortStart,PrevInc,CohortEnd,EventDate,SevereDate,Duration", false);
		}

		if (attritionFile && (!Jerboa.unitTest)) {
			attritionFileName = FilePaths.WORKFLOW_PATH+this.getParentModule()+"/"+
								Parameters.DATABASE_NAME+"_"+this.getParentModule()+
								"_"+TimeUtilities.TIME_STAMP+"_"+Parameters.VERSION+"_attrition.csv";
			Jerboa.getOutputManager().addFile(attritionFileName, 100);
		}

		eventsEndPointList.parseParamList(eventsExclusion);
		setPrevalentList.parseParamList(setPrevalent);

		//Initialize counters
		countRemoved = 0;                     // keeps track of the patients that are not in the cohort
		prevalentCount = 0;
		incidentCount = 0;
		originalCount = 0;                    // keeps track of records in the intermediate file
		includedPatientsCount = 0;
		eventOfInterestCount = 0;
		inclusionDrugCount = 0;
		drugDoseCount = 0;
		eventTypeNoExclusionCount = 0;
		minimumDaysExclusionCount = 0;

		return initOK;
	}

	/**
	 * Sets the cohort start date and cohort end date based on the first occurence of one of the
	 * events of interest.
	 * @param patient - the patient to be checked if it will be part of the cohort or not
	 * @return patient - the patient object with a defined cohort start date and cohort end date
	 */
	@Override
	public Patient process(Patient patient) {

		boolean isPrevalent = false;
		indexEventType = "";

		if (debugPatientIDs.contains(patient.getPatientID())){
			debug = true;
		} else
			debug = false;

		if (patient != null && (chainCohortDefinitions ? patient.isInCohort() : patient.isInPopulation())) {

			if (debug) {
				Logging.add("");
				Logging.add("  Patient " + patient.ID);
				Logging.add("    StartDate           = " + DateUtilities.daysToDate(patient.startDate));
				Logging.add("    EndDate             = " + DateUtilities.daysToDate(patient.endDate));
				Logging.add("    PopulationStartDate = " + DateUtilities.daysToDate(patient.populationStartDate));
				Logging.add("    PopulationEndDate   = " + DateUtilities.daysToDate(patient.populationEndDate));
			}

			int startBase = patient.getPopulationStartDate();
			int endBase   = patient.getPopulationEndDate();
			int start     = -1;
			int end       = patient.getPopulationEndDate();

			if (chainCohortDefinitions) {
				startBase = patient.getCohortStartDate();
				endBase   = patient.getCohortEndDate();
				start     = -1;
				end       = patient.getCohortEndDate();
			}

			int prescriptionStart = -1;
			int eventDate = -1;

			// Update patient counter
			if (!inPostProcessing) originalCount++;

			if (eventTypesOfInterest.size() > 0) {

				// Determine cohort start based on event
				for (Event event : patient.getEvents()) {

					if (allHistory || event.isInPeriod(startBase, endBase)){

						boolean eventOfInterestFound = false;
						boolean inclusionDrugFound = false;
						Prescription inclusionDrug = null;

						//Check for event of interest
						if (event.inList(eventTypesOfInterest)){
							eventOfInterestFound = true;

							//Check if a inclusionDrug is in the window around the event
							if (!drugsInclusion.isEmpty()){
								for (Prescription prescription : patient.getPrescriptions()){
									if (prescription.startsWithIncludeIndication(drugsInclusion) &&
											prescription.isInPeriod(event.getDate()+inclusionDrugsTimewindowStart, event.getDate()+inclusionDrugsTimewindowEnd,false,true)){
										inclusionDrug = prescription;
										inclusionDrugFound = true;
										if (!inPostProcessing) inclusionDrugCount++;
										break;
									}
								}
							} else
								inclusionDrugFound = true;

						}
						if (eventOfInterestFound && inclusionDrugFound){
							start = event.getDate() + (eventCohortWindowStart.get(event.getType()) == null ? 0 : eventCohortWindowStart.get(event.getType()));
							if (eventCohortWindowEnd.get(event.getType()) != null) {
								end = Math.min(event.getDate() + eventCohortWindowEnd.get(event.getType()), patient.getPopulationEndDate());
							}
							if (debug) {
								Logging.add("");
								Logging.add("    Based on event of interest " + event );
								Logging.add("      StartDate         = " + DateUtilities.daysToDate(start));
								Logging.add("      EndDate           = " + DateUtilities.daysToDate(end));
								if (!drugsInclusion.isEmpty()){
									Logging.add("      Based on drug in window: " + inclusionDrug );
								}
							}
							eventDate = event.getDate();
							indexEventType = event.getType();
							if (!inPostProcessing) eventOfInterestCount++;
							break; //only first event
						}
					}
				}

				// move cohort start to population start if needed
				patient.getExtended().put("PREVALENTDATE", String.valueOf(start));

				//TODO COMPLHTN now hardcoded!! make dynamic on script
				if ((start<startBase || indexEventType.equals("COMPLHTN")) && start != -1) {
					isPrevalent = true;
					patient.getExtended().put("ISPREVALENT", "YES");
					if (prevalentCaseAtPopulationStart ) {
						start = startBase;
					}
					if (debug) {
						Logging.add("");
						Logging.add("    Prevalent Case ");
					}

				} else
					patient.getExtended().put("ISPREVALENT", "NO");

				if (isPrevalent) {
					if (!inPostProcessing) prevalentCount++;
				}
				else {
					if (!inPostProcessing) incidentCount++;
				}

				if (onlyIncidentCases && isPrevalent) {
					start = -1;
					if (debug)
						Logging.add("      Not incident");
				}

				// additional drug inclusion criterium

				if (start!=-1 && drugsInclusionDose.size()>0){
					boolean prescriptionFound = false;
					for (Prescription prescription : patient.getPrescriptions()){
						for (int i=0; i<drugsInclusionDose.size(); i++){
							String dose = drugsInclusionDose.get(prescription.getATC());
							if (dose!=null){
								if ((dose.isEmpty() || prescription.getDose().equals(dose)) &&
									prescription.getDuration()>=minDurationDose){
									prescriptionStart = prescription.getDate() + minDurationDose;
									prescriptionFound = true;
									break;
								}
							}
						}

						if (prescriptionFound){
							if (!inPostProcessing) drugDoseCount++;
							if (debug) {
								Logging.add("");
								Logging.add("    Prescription drugDose found: " + prescription);
							}

							break;
						}
					}

					// move the start to this date only if it is after the event base cohort start
					if (prescriptionFound)
						start = Math.max(start,prescriptionStart);
					else
						start = -1;
				}

				// apply eventExclusion
				if (start!=-1 && eventsExclusion.size()>0 && start < end){
					boolean exclusionEventFound = false;
					String eventTypeExclusion = "";
					int eventExclusionDate = -1;
					for (Event event : patient.getEvents()) {
						for (Item eventExclusion : eventsEndPointList){
							if (event.inList(eventExclusion.getLookup())){
								if (eventExclusion.getParameters().get(1).equals("")){
									int endDate = start + Integer.valueOf(eventExclusion.getParameters().get(2));
									if (event.getDate() <= endDate) {
										exclusionEventFound = true;
										eventTypeExclusion = event.getType();
										eventExclusionDate = event.getDate();
										break;
									}
								} else {
									int startDate = start + Integer.valueOf(eventExclusion.getParameters().get(1));
									int endDate = start = Integer.valueOf(eventExclusion.getParameters().get(2));
									if (event.getDate() >= startDate && event.getDate() <= endDate) {
										exclusionEventFound = true;
										eventTypeExclusion = event.getType();
										eventExclusionDate = event.getDate();
										break;
									}
								}
							}
						}
						if (exclusionEventFound) {
							if (debug) {
								Logging.add("");
								Logging.add("    Exclusion event found: " + eventTypeExclusion +
										" on " + DateUtilities.daysToDate(eventExclusionDate));
							}
							start = -1;
							end = -1;
							break;
						}
					}
					if (!exclusionEventFound) {
						if (!inPostProcessing) eventTypeNoExclusionCount++;
					}
				}

				//applysetPrevalent
//				if (setPrevalent.size()>0){
//					HashSet<String> firstEvent = new HashSet();
//					for (Item item: setPrevalentList){
//						if (!firstEvent.contains(item.getLookUp()))
//
//					}
//					for (Event event : patient.getEvents()) {
//
//					}
//				}

				//apply minimumDaysOfCohortTime
				if ((start != -1) && (end != -1) && end-start < minimumDaysOfCohortTime){
					if (!inPostProcessing) minimumDaysExclusionCount++;
					if (debug) {
						Logging.add("");
						Logging.add("    Exclusion based on minimum cohort time: " + (end-start));
					}
					start = -1;
					end = -1;
				}

				// set cohort start and end in patient object
				if ((start != -1) && (end != -1) && (start < end)) {
					// Set cohort start and end
					patient.setCohortStartDate(start);
					patient.setCohortEndDate(end);
					patient.setInCohort(true);
					if (!inPostProcessing) includedPatientsCount++;

					if (debug) {
						Logging.add("    Patient in cohort:");
						Logging.add("      StartDate         = " + DateUtilities.daysToDate(start));
						Logging.add("      EndDate           = " + DateUtilities.daysToDate(end));
					}

					// update patient time
					if (intermediateStats)
						patientTime.add(new ExtendedMultiKey("ALL", patient.cohortEndDate - patient.cohortStartDate));
				}
				else {
					patient.setCohortStartDate(-1);
					patient.setCohortEndDate(-1);
					patient.setInCohort(false);
					if (!inPostProcessing) countRemoved++;

					if (debug) {
						Logging.add("    Patient NOT in cohort:");
					}
				}

				// add patient to output file
				if (patient.inCohort)
					addToOutputBuffer(patient,prescriptionStart,eventDate,isPrevalent);
			}
			else {
				patient.setInCohort(false);
			}

			updateResultSet(patient);

			if (debugPatient(patient))
				debugPatients.add(patient);

		}
		return patient;
	}

	private void updateResultSet(Patient patient){
		if (patient.inCohort) {
			if (Jerboa.getResultSet().isOnlyInCohort()){
				Jerboa.getResultSet().add(patient.getPatientID(),"PopulationStart", (DateUtilities.daysToDate(patient.getPopulationStartDate())));
				Jerboa.getResultSet().add(patient.getPatientID(),"PopulationEnd", (DateUtilities.daysToDate(patient.getPopulationEndDate())));
			}
			Jerboa.getResultSet().add(patient.getPatientID(),"CohortStart", (DateUtilities.daysToDate(patient.getCohortStartDate())));
			Jerboa.getResultSet().add(patient.getPatientID(),"CohortEnd", (DateUtilities.daysToDate(patient.getCohortEndDate())));
			Jerboa.getResultSet().add(patient.getPatientID(),"CohortTime", String.valueOf(patient.getCohortTime()));
		} else {
			if (!Jerboa.getResultSet().isOnlyInCohort()){
				Jerboa.getResultSet().add(patient.getPatientID(),"CohortStart", Jerboa.getResultSet().getMissingValue());
				Jerboa.getResultSet().add(patient.getPatientID(),"CohortEnd", Jerboa.getResultSet().getMissingValue());
				Jerboa.getResultSet().add(patient.getPatientID(),"CohortTime", "0");
			}
		}
	}

	private boolean debugPatient (Patient patient){
		if (debugPatientIDs.contains(patient.getPatientID()) ||debugPatientIDs.contains("All"))
			return true;
		if	((debugPatientIDs.contains("InCohort") && patient.inCohort && !debugPatientIDs.contains("Top")))
			return true;
		if	((debugPatientIDs.contains("InCohort") && patient.inCohort && debugPatientIDs.contains("Top") && (topCount<100))){
			topCount++;
			return true;
		}
		if	(!debugPatientIDs.contains("InCohort") && debugPatientIDs.contains("Top") && topCount<100){
			topCount++;
			return true;
		}

		return false;
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
		else if (intermediateStats) {
			HistogramStats stats = patientTime.getHistogramStats(new ExtendedMultiKey("ALL", Wildcard.INTEGER()));
			Logging.add("Population size: " + originalCount);
			Logging.add("Cohort size: "+ (originalCount - countRemoved));
			Logging.add("Prevalent cases: "+ prevalentCount);
			Logging.add("Total patient time in cohort: " +StringUtilities.format((float)stats.getSum()/(float)DateUtilities.daysPerYear)+" years ");
			Logging.addNewLine();
		}

		if (intermediateFiles && (!Jerboa.unitTest)) {
			Jerboa.getOutputManager().closeFile(intermediateFileName);
		}

		if (attritionFile && (!Jerboa.unitTest)) {
			Jerboa.getOutputManager().writeln(attritionFileName,title + " (EventCohortDefinition),",true);
			if (eventTypesOfInterest.size()>0)
				Jerboa.getOutputManager().writeln(attritionFileName, "  Patients with event of interest," + eventOfInterestCount,true);
			if (drugsInclusion.size()>0)
				Jerboa.getOutputManager().writeln(attritionFileName, "  Patients with drug of interest," + inclusionDrugCount,true);

			if (drugDose.size()>0)
				Jerboa.getOutputManager().writeln(attritionFileName, "  Patients with drug dose combination," + drugDoseCount,true);

			if (eventsExclusion.size()>0)
				Jerboa.getOutputManager().writeln(attritionFileName, "  Patients without exclusion events," + eventTypeNoExclusionCount,true);
			Jerboa.getOutputManager().writeln(attritionFileName, "  Patients excluded minimum days," + minimumDaysExclusionCount,true);
			if (onlyIncidentCases) {
				Jerboa.getOutputManager().writeln(attritionFileName, "  Patients not incident," + prevalentCount,true);
			}
			Jerboa.getOutputManager().writeln(attritionFileName, "Patients included," + includedPatientsCount,true);
			if (!onlyIncidentCases) {
				Jerboa.getOutputManager().writeln(attritionFileName, "  Prevalent," + prevalentCount,true);
				Jerboa.getOutputManager().writeln(attritionFileName, "  Incident," + incidentCount,true);
			}
			Jerboa.getOutputManager().writeln(attritionFileName, " ,",true);
			Jerboa.getOutputManager().flush(attritionFileName);
		}
	}

	/**
	 * Converts all the information in patient into a string and adds
	 * it to the buffer. If the buffer threshold is reached, it writes to file.
	 * @param patient - a patient object to be output
	 * @param prescriptionStart - the date in days when the prescription starts
	 * @param eventDate - the date of the event
	 * @param isPrevalent - true if the patient is prevalent; false otherwise
	 */
	public void addToOutputBuffer(Patient patient, int prescriptionStart, int eventDate, boolean isPrevalent){
		if (patient != null && intermediateFiles && (!Jerboa.unitTest)) {
			String data  = patient.getPatientID() + "," +
				patient.getGender() + "," +
				 DateUtilities.daysToDate(patient.getBirthDate()) + "," +
				 DateUtilities.daysToDate(patient.startDate) + "," +
				 DateUtilities.daysToDate(patient.endDate) + "," +
				(patient.getPopulationStartDate() == -1 ? "" : DateUtilities.daysToDate(patient.getPopulationStartDate())) + "," +
				(patient.getPopulationEndDate() == -1 ? "" : DateUtilities.daysToDate(patient.getPopulationEndDate())) + "," +
				(patient.getCohortStartDate() == -1 ? "" : DateUtilities.daysToDate(patient.getCohortStartDate())) + "," +
				(isPrevalent ? "Prevalent" : "Incident") +"," +
				(patient.getCohortEndDate() == -1 ? "" : DateUtilities.daysToDate(patient.getCohortEndDate())) +"," +
				 DateUtilities.daysToDate(eventDate) + "," +
				 DateUtilities.daysToDate(prescriptionStart) + "," +
				 String.valueOf(patient.getCohortTime());
			addToOutputBuffer(data);
		}
	}

	/**
	 * Method for retrieving the eventType of the index event.
	 */
	public String getIndexEventType() {
		return indexEventType;
	}
}