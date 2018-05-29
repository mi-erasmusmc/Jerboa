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
import org.erasmusmc.jerboa.engine.Modifier;
import org.erasmusmc.jerboa.utilities.DateUtilities;
import org.erasmusmc.jerboa.utilities.InputFileUtilities;
import org.erasmusmc.jerboa.utilities.Item;
import org.erasmusmc.jerboa.utilities.ItemList;
import org.erasmusmc.jerboa.utilities.TimeUtilities;


/**
 * This modifier maps events to another label and adds to event to the event list.
 * The code of the new event is set to the mapped eventType
 * Optionally events in history can be defined that should not be present at that
 * time.
 */
public class EventMapping extends Modifier {

	/**
	 * The definitions of the mappings.
	 * <one or more eventTypes>;<new eventType>;<description>
	 * For example:
	 *
	 *   NAFLD_NASH;NAFLD_NASH_NO_ALC;NAFLD_NASH without alcohol abuse in the past
	 */
	public List<String> eventMapping = new ArrayList<String>();

	/**
	 * Defines which events should not appear before the event
	 * to do the mapping
	 * <one or more eventTypes>;<exclusion eventType>;<description>
	 * For example;
	 *
	 *   NAFLD_NASH;ALCABUSE;Exclude NAFLD_NASH when alcohol abuse in history
	 */
	public List<String> historyExclusion = new ArrayList<String>();

	/**
	 * If true an attrition filename will be created or appended on called Attrition.csv
	 */
	public boolean attritionFile;


	private ItemList eventMappingList = new ItemList(true,0);
	private ItemList exclusionMappingList = new ItemList(true,0);

	private String attritionFileName;   //used to save the attrition

	// Counters
	private Map<List<String>, Integer> countPatientsWithSourceEvent;
	private Map<String, Integer> countPatientsWithEventMapped;


	@Override
	public boolean init() {
		boolean initOK = true;

		eventMappingList.parse(eventMapping);
		exclusionMappingList.parse(historyExclusion);

		for (Item item : eventMappingList) {
			InputFileUtilities.addToLookup(InputFileUtilities.getEventTypes(), item.getLabel());
		}

		if (intermediateFiles){
			if (Jerboa.getOutputManager().addFile(this.intermediateFileName)) {
				String header = "PatientD";
				header += "," + "Date";
				header += "," + "EventType";
				header += "," + "Code";
				Jerboa.getOutputManager().writeln(this.intermediateFileName, header, false);
			}
			else {
				initOK = false;
			}
		}

		if (initOK && attritionFile && (!Jerboa.unitTest)) {
			attritionFileName = FilePaths.WORKFLOW_PATH+this.getParentModule()+"/"+
					Parameters.DATABASE_NAME+"_"+this.getParentModule()+
					"_"+TimeUtilities.TIME_STAMP+"_"+Parameters.VERSION+"_attrition.csv";
			Jerboa.getOutputManager().addFile(attritionFileName, 100);
		}

		// Initialize counters
		countPatientsWithSourceEvent = new HashMap<List<String>, Integer>();
		countPatientsWithEventMapped = new HashMap<String, Integer>();
		for (Item eventMappingItem : eventMappingList) {
			countPatientsWithSourceEvent.put(eventMappingItem.getLookup(), 0);
			countPatientsWithEventMapped.put(eventMappingItem.getLabel(), 0);
		}

		return initOK;
	}


	@Override
	public Patient process(Patient patient) {
		List<Event> newEvents = new ArrayList<Event>();
		HashMap<String,Integer> firstExclusionEvent = new HashMap<String,Integer>();
		if (patient.isInCohort()) {

			// determine first exclusion events
			if (exclusionMappingList.size()>0){
				for (Event event : patient.getEvents()) {
					for (Item item : exclusionMappingList){
						if (event.getType().equals(item.getLabel())) {
							for (String eventType : item.getLookup()) {
								if (!firstExclusionEvent.containsKey(eventType)) {
									firstExclusionEvent.put(eventType,event.date);
								}
							}
						}
					}
				}
			}

			Map<List<String>, Boolean> sourceEventsFound = new HashMap<List<String>, Boolean>();
			Map<String, Boolean> eventCreated = new HashMap<String, Boolean>();

			for (Item eventMappingItem : eventMappingList) {
				sourceEventsFound.put(eventMappingItem.getLookup(), false);
				eventCreated.put(eventMappingItem.getLabel(), false);
			}

			for (Event event : patient.getEvents()) {
				boolean eventInCohort = event.isInPeriod(patient.getCohortStartDate(), patient.getCohortEndDate(), true, false);

				for (Item item : eventMappingList) {

					if (event.inList(item.getLookup())) {
						sourceEventsFound.put(item.getLookup(), (!inPostProcessing) && eventInCohort);

						//skip if the there is an exclusion event before or at event date
						if (firstExclusionEvent.containsKey(event.getType()) && firstExclusionEvent.get(event.getType())<= event.date) {
							break;
						}
						Event newEvent = new Event(event);
						newEvent.setType(item.getLabel());
						newEvent.setCode(event.getType());
						newEvents.add(newEvent);

						eventCreated.put(item.getLabel(), (!inPostProcessing) && eventInCohort);

						if (intermediateFiles) {
							String record =  patient.ID;
							record += "," + DateUtilities.daysToDate(newEvent.getDate());
							record += "," + newEvent.getType();
							record += "," + newEvent.getCode();
							Jerboa.getOutputManager().writeln(this.intermediateFileName, record, true);
						}
					}
				}
			}

			patient.getEvents().addAll(newEvents);
			Collections.sort(patient.events);

			// Count patient if it has a mapped events
			for (Item eventMappingItem : eventMappingList) {
				if (sourceEventsFound.get(eventMappingItem.getLookup())) {
					countPatientsWithSourceEvent.put(eventMappingItem.getLookup(), countPatientsWithSourceEvent.get(eventMappingItem.getLookup()) + 1);
				}
				if (eventCreated.get(eventMappingItem.getLabel())) {
					countPatientsWithEventMapped.put(eventMappingItem.getLabel(), countPatientsWithEventMapped.get(eventMappingItem.getLabel()) + 1);
				}
			}
		}
		return patient;
	}


	@Override
	public void outputResults() {
		if (intermediateFiles) {
			Jerboa.getOutputManager().closeFile(intermediateFileName);
		}

		if ((!Jerboa.unitTest) && attritionFile) {
			Jerboa.getOutputManager().writeln(attritionFileName, title + " (EventMapping),", true);
			for (List<String> eventLookup : countPatientsWithSourceEvent.keySet()) {
				String eventTypes = "";
				for (String eventType : eventLookup) {
					eventTypes += " / " + eventType;
				}
				Jerboa.getOutputManager().writeln(attritionFileName, "Patients with one of " + eventTypes.substring(3) + " in cohort time," + countPatientsWithSourceEvent.get(eventLookup),true);
			}
			for (String eventType : countPatientsWithEventMapped.keySet()) {
				Jerboa.getOutputManager().writeln(attritionFileName, "Patients with mapped " + eventType + " in cohort time," + countPatientsWithEventMapped.get(eventType),true);
			}
			Jerboa.getOutputManager().writeln(attritionFileName, " ,",true);
			Jerboa.getOutputManager().flush(attritionFileName);
		}
	}


	@Override
	public void setNeededFiles() {
		setRequiredFile(DataDefinition.PATIENTS_FILE);
		setRequiredFile(DataDefinition.EVENTS_FILE);
	}


	@Override
	public void setNeededExtendedColumns() {
	}


	@Override
	public void setNeededNumericColumns() {
		// TODO Auto-generated method stub

	}

}



