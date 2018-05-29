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

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.text.StrBuilder;
import org.erasmusmc.jerboa.dataClasses.Event;
import org.erasmusmc.jerboa.dataClasses.Patient;
import org.erasmusmc.jerboa.engine.Module;
import org.erasmusmc.jerboa.gui.graphs.Graphs;
import org.erasmusmc.jerboa.gui.graphs.BarPlotCategory;
import org.erasmusmc.jerboa.gui.graphs.BarPlotLayered;
import org.erasmusmc.jerboa.gui.graphs.Plot;
import org.erasmusmc.jerboa.config.DataDefinition;
import org.erasmusmc.jerboa.config.Parameters;
import org.erasmusmc.jerboa.utilities.ExtendedMultiKey;
import org.erasmusmc.jerboa.utilities.FileUtilities;
import org.erasmusmc.jerboa.utilities.Logging;
import org.erasmusmc.jerboa.utilities.MultiKeyBag;
import org.erasmusmc.jerboa.utilities.Progress;
import org.erasmusmc.jerboa.utilities.Timer;
import org.erasmusmc.jerboa.utilities.Wildcard;


/**
 * This module counts the event types and codes. It outputs a total count and a
 * first count.The total count includes all events of a patient, while the firstCount only counts
 * the events that occur for the first time in patient history.
 *  and are in the cohort time.
 *
 * @author MG {@literal &} PR
 *
 */
public class CodeCounting extends Module{

	//bags
	private MultiKeyBag firstCountBag;
	private MultiKeyBag totalCountBag;

	//counters
	private int numberOfEventTypes = 0;
	private int numberOfEventCodes = 0;
	private double averageNrCodes = 0;
	private int patientsWithoutEvents = 0;
	private int eventsOutsidePatientTime = 0;
	private int eventsInPatientTime = 0;
	private int nrPatients = 0;
	private int eventsWithNoCode=0;

	//set the needed input files
	@Override
	public void setNeededFiles(){
		setRequiredFile(DataDefinition.EVENTS_FILE);
	}

	@Override
	public void setNeededExtendedColumns() {
		setRequiredExtendedColumn(DataDefinition.EVENTS_FILE, "code");
	}

	@Override
	public void setNeededNumericColumns() {
		// TODO Auto-generated method stub

	}

	@Override
	public boolean init(){

		//initialize statistics profile and key(s)
		this.firstCountBag = new MultiKeyBag(); 	// type, code
		this.totalCountBag = new MultiKeyBag();     // gender, type, code

		return true;
	}

	/**
	 * Count the first and total number of event type, code combinations in the input population.
	 * @param patient - the patient to be processed
	 * @return - the same patient without any alteration
	 *
	 */
	@Override
	public Patient process(Patient patient){

		if (patient != null && patient.isInCohort()){
			//events
			List<Event> events = patient.getEvents();
			if (events != null && events.size() > 0){
				Set<Integer> types = new HashSet<Integer>();
				for (Event e : events){
					if (!e.hasCode() || e.getCode().equals("")){
						eventsWithNoCode++;
					}
					boolean first = types.add(e.type);
					if (patient.dateInCohort(e.date)){
						//first count
						if (first){
							firstCountBag.add(new ExtendedMultiKey(e.getType(),e.getCode()));
						}

						//overall count
						totalCountBag.add(new ExtendedMultiKey(patient.gender,e.getType(),e.getCode() == null ? "" : e.getCode()));
						eventsInPatientTime++;

					}else{
						eventsOutsidePatientTime++;
					}
				}
			} else
				patientsWithoutEvents++;
			nrPatients++;
		}

		return patient;
	}

	@Override
	public void displayGraphs() {

		Timer timer = new Timer();
		Progress progress = new Progress();

		timer.start();

		List<Plot> plotList = new ArrayList<Plot>();
		Set<String> types = totalCountBag.getKeyValuesAsString(1);
		TreeMap<String, Integer> overallCounts = new TreeMap<String, Integer>();

		progress.init(types.size(), "Creating Code Counting Graphs..");
		progress.show();

		for (String type : types){

			//overall
			overallCounts.put(type, (int)(totalCountBag.getCount(new ExtendedMultiKey(Wildcard.BYTE(), type, Wildcard.STRING()))));

			//layered - first count / total count
			TreeMap<String, TreeMap<String, Integer>> bothCounts = new TreeMap<String, TreeMap<String,Integer>>();
			Plot plot = new BarPlotLayered.Builder("Code counts for event "+type).sortByValue(true).showLegend(true).XLabel("Count").YLabel("Code").build();
			TreeMap<String, Integer> countPerType = new TreeMap<String, Integer>();

			for (String code : totalCountBag.getSubBag(1, type).getKeyValuesAsString(2))
				countPerType.put(code, (int)firstCountBag.getCount(new ExtendedMultiKey(type, code)));
			bothCounts.put(type, countPerType);
			plot.addSeriesToDataset(bothCounts, "First count");

			countPerType = new TreeMap<String, Integer>();
			for (String code : totalCountBag.getSubBag(1, type).getKeyValuesAsString(2))
				countPerType.put(code, (int)totalCountBag.getCount(new ExtendedMultiKey(Wildcard.BYTE(),type, code)));
			bothCounts.put(type, countPerType);
			plot.addSeriesToDataset(bothCounts, "Overall count");

			plotList.add(plot);

			progress.update();

		}

		//add them to the viewer
		Graphs.addPlot(this.title, "Event Count Per Type",
				new BarPlotCategory.Builder("Event Count Per Type").sortByValue(true)
				.XLabel("Count").YLabel("Code").data(overallCounts).build());

		Graphs.addPlots(this.title, "Event Code Count Per Type", plotList);

		//events per type
		for (String type : types){

			MultiKeyBag data = totalCountBag.getSubBag(1, type);

			Plot plot = new BarPlotCategory.Builder("Code counts for event "+type)
			.verticalLabels(true).sortByValue(true).forceVertical(true).maxEntriesToDisplay(50)
			.showLegend(true)
			.XLabel("Value").YLabel("Count")
			.build();

			ExtendedMultiKey multiKey = new ExtendedMultiKey(Wildcard.BYTE(), type, Wildcard.STRING());
			multiKey = multiKey.setKeyComponent(0, DataDefinition.FEMALE_GENDER);
			plot.addSeriesToDataset(data.getSubBagAsMap(2,multiKey), "Females");
			multiKey = multiKey.setKeyComponent(0, DataDefinition.MALE_GENDER);
			plot.addSeriesToDataset(data.getSubBagAsMap(2,multiKey), "Males");

			Graphs.addPlot(this.title, "Event "+type+" code count", plot);
		}

		//events per type
		for (String type : types){

			MultiKeyBag data = totalCountBag.getSubBag(1, type);

			Plot plot = new BarPlotCategory.Builder("Code counts for event "+type)
			.verticalLabels(true).sortByValue(true).forceVertical(true).maxEntriesToDisplay(50)
			.showLegend(true)
			.XLabel("Value").YLabel("Count")
			.build();

			ExtendedMultiKey multiKey = new ExtendedMultiKey(Wildcard.BYTE(), type, Wildcard.STRING());
			multiKey = multiKey.setKeyComponent(0, DataDefinition.FEMALE_GENDER);
			plot.addSeriesToDataset(data.getSubBagAsMap(2,multiKey), "Females");
			multiKey = multiKey.setKeyComponent(0, DataDefinition.MALE_GENDER);
			plot.addSeriesToDataset(data.getSubBagAsMap(2,multiKey), "Males");

			Graphs.addPlot(this.title, "Event "+type+" code count", plot);
		}

		//make sure the progress bar is closed
		progress.close();
		progress = null;

		timer.stopAndDisplay("Graphs created in:");
	}

	/**
	 * Counts the number of Events, Codes and average number of codes per event.
	 */
	@Override
	public void calcStats(){
		if (totalCountBag != null){
			numberOfEventTypes = totalCountBag.getKeyValuesAsString(1).size();
			numberOfEventCodes = totalCountBag.getKeyValuesAsString(2).size();
		}
		if (numberOfEventTypes!=0)
			averageNrCodes = numberOfEventCodes/numberOfEventTypes;
	}

	@Override
	public void outputResults(){
		calcStats();

		//Output statistics to log/console
		Logging.addNewLine();
		Logging.add("Code counting module results:");
		Logging.add("------------------------------------");
		Logging.add("Patients: " + nrPatients);
		Logging.add("Patients without events: " + patientsWithoutEvents);
		Logging.add("Number of events in patient time: " + eventsInPatientTime);
		Logging.add("Number of events outside patient time: " + eventsOutsidePatientTime);
		Logging.add("Number of event types: " + numberOfEventTypes);
		Logging.add("Number of event codes (including missing): " + numberOfEventCodes);
		Logging.add("Average number of codes per event: " + averageNrCodes);
		Logging.add("Number of events without a code: " + eventsWithNoCode);

		//hold data to be output
		StrBuilder out = new StrBuilder();

		//retrieve the entry set from the overall count
		TreeSet<ExtendedMultiKey> overallSet = firstCountBag.getSortedKeySet();

		//put header
		if (overallSet != null && !overallSet.isEmpty())
			out.appendln(StringUtils.join(new String[]{"Database", "EventType", "EventCode", "Count", "FirstCount"},','));

		//retrieve data from the bag
		for (ExtendedMultiKey key : overallSet)
			out.appendln(Parameters.DATABASE_NAME+","+StringUtils.join(key.getKeys(),',') +","+
					totalCountBag.getCount(new ExtendedMultiKey(Wildcard.BYTE(),key.getKey(0), key.getKey(1)))+","+
					firstCountBag.getCount(key));

		FileUtilities.outputData(this.outputFileName, out, false);

	}


	// Getters for unit tests

	public MultiKeyBag getFirstCountBag() {
		return firstCountBag;
	}

	public MultiKeyBag getTotalCountBag() {
		return totalCountBag;
	}

	public int getNumberOfEventTypes() {
		return numberOfEventTypes;
	}

	public int getNumberOfEventCodes() {
		return numberOfEventCodes;
	}

	public double getAverageNrCodes() {
		return averageNrCodes;
	}

	public int getPatientsWithoutEvents() {
		return patientsWithoutEvents;
	}

	public int getEventsOutsidePatientTime() {
		return eventsOutsidePatientTime;
	}

	public int getEventsInPatientTime() {
		return eventsInPatientTime;
	}

	public int getNrPatients() {
		return nrPatients;
	}

	public int getEventsWithNoCode() {
		return eventsWithNoCode;
	}

}