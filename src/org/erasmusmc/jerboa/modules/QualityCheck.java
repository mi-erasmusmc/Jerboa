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
 * $Rev:: 4429               $:  Revision of last commit                                  *
 * $Author:: root            $:  Author of last commit                                    *
 * $Date:: 2013-09-10 14:16:#$:  Date and time (CET) of last commit						  *
 ******************************************************************************************/
package org.erasmusmc.jerboa.modules;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

import org.apache.commons.collections.bag.HashBag;
import org.erasmusmc.jerboa.Jerboa;
import org.erasmusmc.jerboa.config.DataDefinition;
import org.erasmusmc.jerboa.dataClasses.Event;
import org.erasmusmc.jerboa.dataClasses.Measurement;
import org.erasmusmc.jerboa.dataClasses.Patient;
import org.erasmusmc.jerboa.dataClasses.Prescription;
import org.erasmusmc.jerboa.engine.Module;
import org.erasmusmc.jerboa.gui.graphs.BarPlotCategory;
import org.erasmusmc.jerboa.gui.graphs.BarPlotDS;
import org.erasmusmc.jerboa.gui.graphs.Graphs;
import org.erasmusmc.jerboa.gui.graphs.Plot;
import org.erasmusmc.jerboa.utilities.ExtendedMultiKey;
import org.erasmusmc.jerboa.utilities.InputFileUtilities;
import org.erasmusmc.jerboa.utilities.Logging;
import org.erasmusmc.jerboa.utilities.MultiKeyBag;
import org.erasmusmc.jerboa.utilities.Progress;
import org.erasmusmc.jerboa.utilities.StringUtilities;
import org.erasmusmc.jerboa.utilities.Timer;
import org.erasmusmc.jerboa.utilities.Wildcard;
import org.erasmusmc.jerboa.utilities.stats.HistogramStats;

/**
 * This module performs a quality check of the input data.
 * Depending on what type of input files are present in the working folder,
 * different counters/bags are put in place to keep track/group the available data
 * and then export it as graphs and tables.
 *
 * @author MG
 *
 */
public class QualityCheck extends Module{

	/**
	 * This flag if set to true allows also
	 * the episodes that are outside patient
	 * time to be included in the same bag
	 * as the ones occurring during patient time.
	 */
	public boolean includeAll;

	/**
	 * This parameter will hold a list of the extended data
	 * columns that are to be ignored when calculating the
	 * descriptive statistics. The list of extended data
	 * columns should be per input file type.
	 * <p>
	 * {@code Definition ignoredColumnsForStats = INPUT_FILE_TYPE;EXT_COL1, EXT_COL2, ..}
	 * <p>
	 * where {@code INPUT_FILE_TYPE} is the type of the input file @see {@link DataDefinition},
	 * {@code EXT_COL1} is the name of an extended data column to be ignored from statistics calculations,
	 * {@code EXT_COL2} is another extended data column to be ignored from statistics calculations,
	 * <p>
	 * {@code Example ignoredColumnsForStats = PATIENTS;DEATHDATE}
	 * {@code Example ignoredColumnsForStats = PRESCRIPTIONS;PRESCRIBERID}
	 */
	public List<String> ignoredColumnsForStats = new ArrayList<String>();

	/**
	 * This parameter will hold a list of the data
	 * columns that are to be ignored during the processing.
	 * The list of data columns should be per input file type.
	 * <p>
	 * {@code Definition excludedColumns = INPUT_FILE_TYPE;COL1, COL2, ..}
	 * <p>
	 * where {@code INPUT_FILE_TYPE} is the type of the input file @see {@link DataDefinition},
	 * {@code COL1} is the name of a data column to be ignored from processing,
	 * {@code COL2} is another data column from the same input file to be ignored from processing,
	 * <p>
	 * {@code Example excludedColumns = PATIENTS;PATIENTID}
	 * {@code Example excludedColumns = PRESCRIPTIONS;PRESCRIBERID}
	 */
	public List<String> excludedColumns = new ArrayList<String>();

	//PRIVATE PARAMETERS
	//contains counts of data per episode type
	private HashMap<ExtendedMultiKey, MultiKeyBag> bags;
	//contains counts of data per episode type that are outside patient time
	private HashMap<ExtendedMultiKey, MultiKeyBag> bagsOutside;

	//contains counts of extended data per episode type
	private HashMap<ExtendedMultiKey, MultiKeyBag> bagsExtended;
	//contains counts of extended data per episode type that are outside patient time
	private HashMap<ExtendedMultiKey, MultiKeyBag> bagsExtendedOutside;

	private MultiKeyBag patientsWithMissingInfo;

	//bags containing episodes with no specific attribute
	private MultiKeyBag eventsWithMissingInfo;
	private MultiKeyBag prescriptionsWithMissingInfo;
	private MultiKeyBag measurementsWithMissingInfo;

	//keeps counts for basic statistics for the missing extended data per episode type
	private HashMap<ExtendedMultiKey, Integer> countersMissingExtended;

	//will hold the parsed information regarding the extended data columns to ignore for stats calculation
	private HashMap<String, List<String>> extendedColumnsToIgnoreFromStatsCalculation;

	//will hold the parsed information regarding the extended data columns to ignore for stats calculation
	private HashMap<String, List<String>> columnsToIgnoreFromProcessing;

	//counters for console display
	private int nbPatients = 0;
	private int nbEvents = 0;
	private int nbPrescriptions = 0;
	private int nbMeasurements = 0;

	private int patientsWithoutEvents = 0;
	private int patientsWithoutPrescriptions = 0;
	private int patientsWithoutMeasurements = 0;

	private int eventsOutsidePatientTime = 0;
	private int prescriptionsOutsidePatientTime = 0;
	private int measurementsOutsidePatientTime = 0;

	private int eventsInPatientTime = 0;
	private int prescriptionsInPatientTime = 0;
	private int measurementsInPatientTime = 0;

	private int prescriptionsWithNoDuration = 0;
	private int measurementsWithNoValue = 0;

	//set the needed input files
	@Override
	public void setNeededFiles(){
		if (Jerboa.getInputFileSet().gotEvents)
			setRequiredFile(DataDefinition.EVENTS_FILE);
		if (Jerboa.getInputFileSet().gotPrescriptions)
			setRequiredFile(DataDefinition.PRESCRIPTIONS_FILE);
		if (Jerboa.getInputFileSet().gotMeasurements)
			setRequiredFile(DataDefinition.MEASUREMENTS_FILE);
	}

	@Override
	public void setNeededExtendedColumns() { /* NOTHING TO ADD */ }

	@Override
	public void setNeededNumericColumns() {
		// TODO Auto-generated method stub

	}

	@Override
	public boolean init(){

		//holding all data per episode type
		this.bags = new HashMap<ExtendedMultiKey, MultiKeyBag>();
			if (!includeAll)
				this.bagsOutside = new HashMap<ExtendedMultiKey, MultiKeyBag>();

		//holding all extended data per episode type and for patient extended data
		this.bagsExtended = new HashMap<ExtendedMultiKey, MultiKeyBag>();
		if (!includeAll)
			this.bagsExtendedOutside = new HashMap<ExtendedMultiKey, MultiKeyBag>();

		//holding counters for the missing extended attributes per episode type
		this.countersMissingExtended = new HashMap<ExtendedMultiKey, Integer>();

		//check and parse if there are extended data columns to ignore while calculating the stats
		if (!parseColumnsToIgnore(true))
			return false;

		//check and parse if there are columns to ignore from processing
		if (!parseColumnsToIgnore(false))
					return false;

		initBagsExtendedData(DataDefinition.PATIENTS_FILE);

		if (neededFiles.get(DataDefinition.EVENTS_FILE)){
			this.bags.put(new ExtendedMultiKey(DataDefinition.EPISODE_EVENT,
					"overall"), new MultiKeyBag());									    //patient gender, type
			if (!includeAll)
				this.bagsOutside.put(new ExtendedMultiKey(DataDefinition.EPISODE_EVENT,
						"overall"), new MultiKeyBag());								    //patient gender, type

			initBagsExtendedData(DataDefinition.EVENTS_FILE);       //patient gender, type, extended data column
		}

		if (neededFiles.get(DataDefinition.PRESCRIPTIONS_FILE)){
			this.bags.put(new ExtendedMultiKey(DataDefinition.EPISODE_PRESCRIPTION,
					"overall"), new MultiKeyBag());					 					//patient gender, atc
			this.bags.put(new ExtendedMultiKey(DataDefinition.EPISODE_PRESCRIPTION,
					DataDefinition.PRESCRIPTION_DURATION), new MultiKeyBag());			//patient gender, atc, duration
			if (!includeAll){
				this.bagsOutside.put(new ExtendedMultiKey(DataDefinition.EPISODE_PRESCRIPTION,
						"overall"), new MultiKeyBag());
				this.bagsOutside.put(new ExtendedMultiKey(DataDefinition.EPISODE_PRESCRIPTION,
						DataDefinition.PRESCRIPTION_DURATION), new MultiKeyBag());
			}

			initBagsExtendedData(DataDefinition.PRESCRIPTIONS_FILE);//patient gender, atc, extended data column
		}

		if (neededFiles.get(DataDefinition.MEASUREMENTS_FILE)){
			this.bags.put(new ExtendedMultiKey(DataDefinition.EPISODE_MEASUREMENT,
					"overall"), new MultiKeyBag());					 					//patient gender, type
			this.bags.put(new ExtendedMultiKey(DataDefinition.EPISODE_MEASUREMENT,
					DataDefinition.MEASUREMENT_VALUE), new MultiKeyBag());			//patient gender, type, value
			if (!includeAll){
				this.bagsOutside.put(new ExtendedMultiKey(DataDefinition.EPISODE_MEASUREMENT,
						"overall"), new MultiKeyBag());
				this.bagsOutside.put(new ExtendedMultiKey(DataDefinition.EPISODE_MEASUREMENT,
						DataDefinition.MEASUREMENT_VALUE), new MultiKeyBag());
			}
			initBagsExtendedData(DataDefinition.MEASUREMENTS_FILE); //patient gender, type, extended data column
		}

		this.patientsWithMissingInfo = new MultiKeyBag();			//patient gender, missing attribute
		this.eventsWithMissingInfo = new MultiKeyBag();				//patient gender, event type, missing attribute
		this.prescriptionsWithMissingInfo = new MultiKeyBag(); 		//patient gender, atc, missing attribute
		this.measurementsWithMissingInfo = new MultiKeyBag();		//patient gender, measurement type, missing attribute

		return true;

	}

	/**
	 * Initializes for the inputFileType all the bags that will hold counters
	 * of the extended data. Note that the extended data columns are not mandatory
	 * and their presence not necessarily predictable.
	 * @param inputFileType - the type of the input file as defined in DataDefinition
	 */
	protected void initBagsExtendedData(byte inputFileType){
		if (InputFileUtilities.getExtendedDataOrder(inputFileType) != null){
			Set<String> extendedColumns = new HashSet<String>(InputFileUtilities.getExtendedDataOrder(inputFileType).values());
			for (String column : extendedColumns){
				ExtendedMultiKey key = new ExtendedMultiKey(InputFileUtilities.getEpisodeName(inputFileType), column);
				this.bagsExtended.put(key, new MultiKeyBag());
				this.countersMissingExtended.put(key, 0);
				if (!includeAll)
					this.bagsExtendedOutside.put(key, new MultiKeyBag());
			}
		}
	}

	@Override
	public Patient process(Patient patient) {
		if (patient != null && patient.isInPopulation() && patient.isInCohort()){

			//PATIENT EXTENDED DATA
			for (int extended : patient.getExtendedData().keySet()){
				String extendedAttribute = patient.getExtended().getAttributeName(extended);
				if (includeColumnForProcessing(DataDefinition.PATIENT, extendedAttribute)){
					ExtendedMultiKey key = new ExtendedMultiKey(DataDefinition.PATIENT, extendedAttribute);
					if (patient.getExtended().hasAttribute(extended)){
						bagsExtended.get(key).add(new ExtendedMultiKey(patient.gender,patient.getExtended().getAttributeAsString(extended)));
					}else{
						countersMissingExtended.put(key, countersMissingExtended.get(key)+1);
						patientsWithMissingInfo.add(new ExtendedMultiKey(patient.gender, extendedAttribute));
					}
				}
			}

			//EVENTS
			List<Event> events = patient.getEvents();
			if (events != null && events.size() > 0){
				for (Event e : events){

					ExtendedMultiKey key = new ExtendedMultiKey(DataDefinition.EPISODE_EVENT, "overall");
					boolean insidePatientTime = patient.dateInPopulation(e.date);

					//update log counters
					if (insidePatientTime)
						eventsInPatientTime++;
					else
						eventsOutsidePatientTime++;

					//overall counts
					if (includeColumnForProcessing(DataDefinition.EPISODE_EVENT, DataDefinition.EVENT_TYPE)){
						if (insidePatientTime || includeAll)
							bags.get(key).add(new ExtendedMultiKey(patient.gender, e.getType()));
						else
							bagsOutside.get(key).add(new ExtendedMultiKey(patient.gender, e.getType()));
					}

					//extended data
					for (int extended : e.getExtendedData().keySet()){
						String extendedAttribute = e.getExtendedAttributeName(extended);
						if (includeColumnForProcessing(DataDefinition.EPISODE_EVENT, extendedAttribute)){
							key = new ExtendedMultiKey(DataDefinition.EPISODE_EVENT, extendedAttribute);
							if (e.hasExtendedAttribute(extended)){
								if (insidePatientTime || includeAll)
									bagsExtended.get(key).add(new ExtendedMultiKey(patient.gender, e.getType(), e.getExtendedAttributeAsString(extended)));
								else
									bagsExtendedOutside.get(key).add(new ExtendedMultiKey(patient.gender, e.getType(), e.getExtendedAttributeAsString(extended)));
							}else{
								countersMissingExtended.put(key, countersMissingExtended.get(key)+1);
								eventsWithMissingInfo.add(new ExtendedMultiKey(patient.gender, e.getType(), extendedAttribute));
							}
						}
					}
					nbEvents++;
				}//end loop over events
			}else{
				patientsWithoutEvents++;
			}

			//PRESCRIPTIONS
			List<Prescription> prescriptions = patient.getPrescriptions();
			if (prescriptions != null && prescriptions.size() > 0){
				for (Prescription p : prescriptions){

					ExtendedMultiKey key = new ExtendedMultiKey(DataDefinition.EPISODE_PRESCRIPTION, "overall");
					boolean insidePatientTime = patient.dateInPopulation(p.date);

					//update log counters
					if (insidePatientTime)
						prescriptionsInPatientTime++;
					else
						prescriptionsOutsidePatientTime++;

					//overall counts
					if (includeColumnForProcessing(DataDefinition.EPISODE_PRESCRIPTION, DataDefinition.PRESCRIPTION_ATC_CODE)){
						if (insidePatientTime || includeAll)
							bags.get(key).add(new ExtendedMultiKey(patient.gender, p.getATC()));
						else
							bagsOutside.get(key).add(new ExtendedMultiKey(patient.gender, p.getATC()));
					}

					//duration - still mandatory
					if (includeColumnForProcessing(DataDefinition.EPISODE_PRESCRIPTION, DataDefinition.PRESCRIPTION_DURATION)){
						key = new ExtendedMultiKey(DataDefinition.EPISODE_PRESCRIPTION,	DataDefinition.PRESCRIPTION_DURATION);
						if (p.getDuration() != p.NO_DATA){
							if (insidePatientTime || includeAll){
								bags.get(key).add(new ExtendedMultiKey(patient.gender, p.getATC(), p.getDuration()));
							}else{
								bagsOutside.get(new ExtendedMultiKey(DataDefinition.EPISODE_PRESCRIPTION,
										DataDefinition.PRESCRIPTION_DURATION)).add(new ExtendedMultiKey(patient.gender, p.getATC(), p.getDuration()));
							}
						}else{
							prescriptionsWithNoDuration++;
							prescriptionsWithMissingInfo.add(new ExtendedMultiKey(patient.gender, p.getATC(), DataDefinition.PRESCRIPTION_DURATION));
						}
					}

					//extended data
					for (int extended : p.getExtendedData().keySet()){
						String extendedAttribute = p.getExtendedAttributeName(extended);
						if (includeColumnForProcessing(DataDefinition.EPISODE_PRESCRIPTION, extendedAttribute)){
							key = new ExtendedMultiKey(DataDefinition.EPISODE_PRESCRIPTION, extendedAttribute);
							if (p.hasExtendedAttribute(extended)){
								if (insidePatientTime || includeAll)
									bagsExtended.get(key).add(new ExtendedMultiKey(patient.gender, p.getATC(), p.getExtendedAttributeAsString(extended)));
								else
									bagsExtendedOutside.get(key).add(new ExtendedMultiKey(patient.gender, p.getATC(), p.getExtendedAttributeAsString(extended)));
							}else{
								countersMissingExtended.put(key, countersMissingExtended.get(key)+1);
								prescriptionsWithMissingInfo.add(new ExtendedMultiKey(patient.gender, p.getATC(), extendedAttribute));
							}
						}
					}
				nbPrescriptions++;
				}//end loop over prescriptions
			}else{
				patientsWithoutPrescriptions++;
			}

			//MEASUREMENTS
			List<Measurement> measurements = patient.getMeasurements();
			if (measurements != null && measurements.size() > 0){
				for (Measurement m : measurements){

					ExtendedMultiKey key = new ExtendedMultiKey(DataDefinition.EPISODE_MEASUREMENT, "overall");
					boolean insidePatientTime = patient.dateInPopulation(m.date);

					//update log counters
					if (insidePatientTime)
						measurementsInPatientTime++;
					else
						measurementsOutsidePatientTime++;

					//overall counts
					if (includeColumnForProcessing(DataDefinition.EPISODE_MEASUREMENT, DataDefinition.MEASUREMENT_TYPE)){
						if (insidePatientTime || includeAll)
							bags.get(key).add(new ExtendedMultiKey(patient.gender, m.getType()));
						else
							bagsOutside.get(key).add(new ExtendedMultiKey(patient.gender, m.getType()));
					}

					//value - still mandatory
					if (includeColumnForProcessing(DataDefinition.EPISODE_MEASUREMENT, DataDefinition.MEASUREMENT_VALUE)){
						key = new ExtendedMultiKey(DataDefinition.EPISODE_MEASUREMENT,	DataDefinition.MEASUREMENT_VALUE);
						if (m.getValue().equals(DataDefinition.NO_DATA) || m.getValue().equals("")){
							measurementsWithNoValue++;
							measurementsWithMissingInfo.add(new ExtendedMultiKey(patient.gender, m.getType(), DataDefinition.MEASUREMENT_VALUE));
						}else{
							if (insidePatientTime || includeAll)
								bags.get(key).add(new ExtendedMultiKey(patient.gender, m.getType(), m.getValue()));
							else
								bagsOutside.get(key).add(new ExtendedMultiKey(patient.gender, m.getType(), m.getValue()));
						}
					}

					//extended
					for (int extended : m.getExtendedData().keySet()){
						String extendedAttribute = m.getExtendedAttributeName(extended);
						if (includeColumnForProcessing(DataDefinition.EPISODE_MEASUREMENT, extendedAttribute)){
							key = new ExtendedMultiKey(DataDefinition.EPISODE_MEASUREMENT, extendedAttribute);
							if (m.hasExtendedAttribute(extended)){
								if (insidePatientTime || includeAll)
									bagsExtended.get(key).add(new ExtendedMultiKey(patient.gender, m.getType(), m.getExtendedAttributeAsString(extended)));
								else
									bagsExtendedOutside.get(key).add(new ExtendedMultiKey(patient.gender, m.getType(), m.getExtendedAttributeAsString(extended)));
							}else{
								countersMissingExtended.put(key, countersMissingExtended.get(key)+1);
								measurementsWithMissingInfo.add(new ExtendedMultiKey(patient.gender, m.getType(), extendedAttribute));
							}
						}
					}
					nbMeasurements++;
				}//end loop over measurements
			}else{
				patientsWithoutMeasurements++;
			}

			nbPatients++;
		}

		return patient;
	}

	@Override
	public void displayGraphs() {

		Timer timer = new Timer();
		Progress progress = new Progress();
		Logging.add("Creating graphs", Logging.HINT);

		Set<String> eventTypes = new HashSet<String>();
		Set<String> prescriptionTypes = new HashSet<String>();
		Set<String> measurementTypes = new HashSet<String>();

		//compute limit for progress bar
		if (neededFiles.get(DataDefinition.EVENTS_FILE))
			eventTypes = bags.get(new ExtendedMultiKey(DataDefinition.EPISODE_EVENT, "overall")).getKeyValuesAsString(1);
		if (neededFiles.get(DataDefinition.PRESCRIPTIONS_FILE))
			prescriptionTypes = bags.get(new ExtendedMultiKey(DataDefinition.EPISODE_PRESCRIPTION, "overall")).getKeyValuesAsString(1);
		if (neededFiles.get(DataDefinition.MEASUREMENTS_FILE))
			measurementTypes = bags.get(new ExtendedMultiKey(DataDefinition.EPISODE_MEASUREMENT, "overall")).getKeyValuesAsString(1);

		timer.start();
		progress.init(eventTypes.size() + prescriptionTypes.size() + measurementTypes.size() + 1, "Creating graphs..");
		progress.show();

		//overall count per type
		if (neededFiles.get(DataDefinition.EVENTS_FILE)){
			Graphs.addPlot(this.title, "Event count per type", getPlot(bags.get(new ExtendedMultiKey(DataDefinition.EPISODE_EVENT, "overall")),
				new ExtendedMultiKey(Wildcard.BYTE(), Wildcard.STRING()), 1, "Event count per type", true, false));
		}

		if (neededFiles.get(DataDefinition.PRESCRIPTIONS_FILE)){
			Graphs.addPlot(this.title, "Prescription Count Per ATC", getPlot(bags.get(new ExtendedMultiKey(DataDefinition.EPISODE_PRESCRIPTION, "overall")),
				new ExtendedMultiKey(Wildcard.BYTE(), Wildcard.STRING()), 1, "Prescription count per ATC", true, false));
		}

		if (neededFiles.get(DataDefinition.MEASUREMENTS_FILE)){
			Graphs.addPlot(this.title, "Measurement Count Per Type", getPlot(bags.get(new ExtendedMultiKey(DataDefinition.EPISODE_MEASUREMENT, "overall")),
				new ExtendedMultiKey(Wildcard.BYTE(), Wildcard.STRING()), 1, "Measurement count per type", true, false));
		}
		progress.update();

		//events
		if (InputFileUtilities.getExtendedDataOrder(DataDefinition.EVENTS_FILE) != null){
			Set<String> extendedColumns = new HashSet<String>(InputFileUtilities.getExtendedDataOrder(DataDefinition.EVENTS_FILE).values());
			for (String type : eventTypes){
				for (String extended : extendedColumns){
					ExtendedMultiKey key = new ExtendedMultiKey(DataDefinition.EPISODE_EVENT, extended);
					MultiKeyBag bag = bagsExtended.get(key);
					if (bag != null && bag.getSize() > 0){
						Graphs.addPlot(this.title, "Event "+type+" "+extended+" count",
								getPlot(bag.getSubBag(1, type),
										new ExtendedMultiKey(Wildcard.BYTE(), type, Wildcard.STRING()), 2, StringUtilities.capitalize(extended)+" counts for event "+type, true, false));
					}
				}
				progress.update();
			}
		}

		//prescriptions
		if (InputFileUtilities.getExtendedDataOrder(DataDefinition.PRESCRIPTIONS_FILE) != null){
			Set<String> extendedColumns = new HashSet<String>(InputFileUtilities.getExtendedDataOrder(DataDefinition.PRESCRIPTIONS_FILE).values());
			for (String type : prescriptionTypes){
				Graphs.addPlot(this.title, "Durations for "+type,
						getPlot(bags.get(new ExtendedMultiKey(DataDefinition.EPISODE_PRESCRIPTION, DataDefinition.PRESCRIPTION_DURATION)).getSubBag(1, type),
								new ExtendedMultiKey(Wildcard.BYTE(), type, Wildcard.INTEGER()), 2, "Durations for ATC "+type, false, true));
				for (String extended : extendedColumns){
					ExtendedMultiKey key = new ExtendedMultiKey(DataDefinition.EPISODE_PRESCRIPTION, extended);
					MultiKeyBag bag = bagsExtended.get(key);
					if (bag != null && bag.getSize() > 0){
						Graphs.addPlot(this.title, StringUtilities.capitalize(extended)+"s for "+type,
								getPlot(bag.getSubBag(1, type),
										new ExtendedMultiKey(Wildcard.BYTE(), type, Wildcard.STRING()), 2, StringUtilities.capitalize(extended)+"s for ATC "+type, false, true));
					}
				}
				progress.update();
			}
		}

		//measurements
		if (InputFileUtilities.getExtendedDataOrder(DataDefinition.MEASUREMENTS_FILE) != null){
			Set<String> extendedColumns = new HashSet<String>(InputFileUtilities.getExtendedDataOrder(DataDefinition.MEASUREMENTS_FILE).values());
			for (String type : measurementTypes){
				Graphs.addPlot(this.title, "Values for "+type,
					getPlot(bags.get(new ExtendedMultiKey(DataDefinition.EPISODE_MEASUREMENT, DataDefinition.MEASUREMENT_VALUE)).getSubBag(1, type),
							new ExtendedMultiKey(Wildcard.BYTE(), type, Wildcard.STRING()), 2, "Value counts for measurement "+type, false, true));
				for (String extended : extendedColumns){
					ExtendedMultiKey key = new ExtendedMultiKey(DataDefinition.EPISODE_MEASUREMENT, extended);
					MultiKeyBag bag = bagsExtended.get(key);
					if (bag != null && bag.getSize() > 0){
						Graphs.addPlot(this.title, StringUtilities.capitalize(extended)+"s for "+type,
							getPlot(bag.getSubBag(1, type),
									new ExtendedMultiKey(Wildcard.BYTE(), type, Wildcard.STRING()), 2, StringUtilities.capitalize(extended)+"s for measurement "+type, false, true));
					}
				}
			}
			progress.update();
		}

		//make sure the progress bar is closed
		progress.close();
		progress = null;

		timer.stopAndDisplay("Graphs created in:");

	}

	@Override
	public void outputResults() {

		//output statistics to log/console
		if (neededFiles.get(DataDefinition.EVENTS_FILE)){
			Logging.addNewLine();
			Logging.add("Quality check - events:");
			Logging.add("------------------------------");
			Logging.add("Patients: " + nbPatients);
			Logging.add("Patients without events: " + patientsWithoutEvents);
			Logging.add("Number of events: "+nbEvents);
			Logging.add("Number of events in patient time: " + eventsInPatientTime);
			Logging.add("Number of events outside patient time: " + eventsOutsidePatientTime);
			logMissingCountsExtended(DataDefinition.EVENTS_FILE);

		}
		if (neededFiles.get(DataDefinition.PRESCRIPTIONS_FILE)){
			Logging.addNewLine();
			Logging.add("Quality check - prescriptions:");
			Logging.add("---------------------------------------");
			Logging.add("Patients: " + nbPatients);
			Logging.add("Patients without prescriptions: " + patientsWithoutPrescriptions);
			Logging.add("Number of prescriptions: "+nbPrescriptions);
			Logging.add("Number of prescriptions in patient time: " + prescriptionsInPatientTime);
			Logging.add("Number of prescriptions outside patient time: " + prescriptionsOutsidePatientTime);
			Logging.add("Number of prescriptions without a duration: " + prescriptionsWithNoDuration);
			logMissingCountsExtended(DataDefinition.PRESCRIPTIONS_FILE);
		}
		if (neededFiles.get(DataDefinition.MEASUREMENTS_FILE)){
			Logging.addNewLine();
			Logging.add("Quality check - measurements:");
			Logging.add("------------------------------------------");
			Logging.add("Patients: " + nbPatients);
			Logging.add("Patients without measurements: " + patientsWithoutMeasurements);
			Logging.add("Number of measurements: "+nbMeasurements);
			Logging.add("Number of measurements in patient time: " + measurementsInPatientTime);
			Logging.add("Number of measurements outside patient time: " + measurementsOutsidePatientTime);
			Logging.add("Number of measurements without a value: " + measurementsWithNoValue);
			logMissingCountsExtended(DataDefinition.MEASUREMENTS_FILE);
		}

		//output content of bags
		Logging.add("Calculating stats and output of results");
		outputBags("", false);
		outputBagsMissingInfo();
		if (!includeAll)
			outputBags("Outside",true);
		calcStats();
	}

	/**
	 * Adds to the log/console the counts for missing extended
	 * data columns for this episodeType (if existing).
	 * @param episodeType - the episode type of interest
	 */
	private void logMissingCountsExtended(byte episodeType){
		if (InputFileUtilities.getExtendedDataOrder(episodeType) != null){
			Set<String> extendedColumns = new HashSet<String>(InputFileUtilities.getExtendedDataOrder(episodeType).values());
			String episodeName = InputFileUtilities.getEpisodeName(episodeType);
			for (String extended : extendedColumns){
				ExtendedMultiKey key = new ExtendedMultiKey(episodeName, extended);
				Integer count = countersMissingExtended.get(key);
				if (count != null)
					Logging.add("Number of "+episodeName+"s without a " + extended + ": " + count);
			}
		}
	}

	/**
	 * Writes to file the contents of the bags passed as parameters.
	 * A header is created for each type of file, based on the contents of the bag.
	 * @param suffix - what should be added to the file name
	 * @param outsidePatientTime - true if the bags containing episodes outside patient time should be output
	 */
	private void outputBags(String suffix, boolean outsidePatientTime){

		//OUTPUT EVENTS
		if (neededFiles.get(DataDefinition.EVENTS_FILE)){

			//overall counts
			ExtendedMultiKey key = new ExtendedMultiKey(DataDefinition.EPISODE_EVENT, "overall");
			MultiKeyBag bag = outsidePatientTime ? bagsOutside.get(key) : bags.get(key);
			if (bag.getSize() > 0){
				String eventsFile = StringUtilities.addSuffixToFileName(outputFileName, "_Events"+(suffix != null && !suffix.equals("") ? "_"+suffix : "")+ ".csv", true);
				Jerboa.getOutputManager().addFile(eventsFile);
				String record = "PatientGender";
				record += "," + "EventType";
				record += "," + "Count";
				Jerboa.getOutputManager().writeln(eventsFile, record, true);
				outputBag(bag, eventsFile);
				Jerboa.getOutputManager().closeFile(eventsFile);
			}

			//extended
			if (InputFileUtilities.getExtendedDataOrder(DataDefinition.EVENTS_FILE) != null){
				Set<String> extendedColumns = new HashSet<String>(InputFileUtilities.getExtendedDataOrder(DataDefinition.EVENTS_FILE).values());
				for (String extended : extendedColumns){
					key = new ExtendedMultiKey(DataDefinition.EPISODE_EVENT, extended);
					bag = outsidePatientTime ? bagsExtendedOutside.get(key) : bagsExtended.get(key);
					if (bag != null && bag.getSize() > 0){
						//prepare file name and header
						String eventsFile = StringUtilities.addSuffixToFileName(outputFileName, "_Events_"+StringUtilities.capitalize(extended)+"s"+(suffix != null && !suffix.equals("") ? "_"+suffix : "") + ".csv", true);
						Jerboa.getOutputManager().addFile(eventsFile);
						String record = "PatientGender";
						record += "," + "EventType";
						record += "," + StringUtilities.capitalize(extended);
						record += "," + "Count";
						Jerboa.getOutputManager().writeln(eventsFile, record, true);
						outputBag(bag, eventsFile);
						Jerboa.getOutputManager().closeFile(eventsFile);
					}
				}
			}
		}

		//OUTPUT PRESCRIPTIONS
		if (neededFiles.get(DataDefinition.PRESCRIPTIONS_FILE)){

			//overall counts
			ExtendedMultiKey key = new ExtendedMultiKey(DataDefinition.EPISODE_PRESCRIPTION, "overall");
			MultiKeyBag bag = outsidePatientTime ? bagsOutside.get(key) : bags.get(key);
			if (bag.getSize() > 0){
				String prescriptionsFile = StringUtilities.addSuffixToFileName(outputFileName, "_Prescriptions.csv", true);
				Jerboa.getOutputManager().addFile(prescriptionsFile);
				String record = "PatientGender";
				record += "," + "ATC";
				record += "," + "Count";
				Jerboa.getOutputManager().writeln(prescriptionsFile, record, true);
				outputBag(bag, prescriptionsFile);
				Jerboa.getOutputManager().closeFile(prescriptionsFile);
			}

			//duration - still mandatory
			key = new ExtendedMultiKey(DataDefinition.EPISODE_PRESCRIPTION, DataDefinition.PRESCRIPTION_DURATION);
			bag = outsidePatientTime ? bagsOutside.get(key) : bags.get(key);
			if (bag.getSize() > 0){
				String prescriptionsFile = StringUtilities.addSuffixToFileName(outputFileName, "_Prescriptions_Durations"+(suffix != null && !suffix.equals("") ? "_"+suffix : "") + ".csv", true);
				Jerboa.getOutputManager().addFile(prescriptionsFile);
				String record = "PatientGender";
				record += "," + "ATC";
				record += "," + "Duration";
				record += "," + "Count";
				Jerboa.getOutputManager().writeln(prescriptionsFile, record, true);
				outputBag(bag, prescriptionsFile);
				Jerboa.getOutputManager().closeFile(prescriptionsFile);
			}

			//extended
			if (InputFileUtilities.getExtendedDataOrder(DataDefinition.PRESCRIPTIONS_FILE) != null){
				Set<String> extendedColumns = new HashSet<String>(InputFileUtilities.getExtendedDataOrder(DataDefinition.PRESCRIPTIONS_FILE).values());
				for (String extended : extendedColumns){
					key = new ExtendedMultiKey(DataDefinition.EPISODE_PRESCRIPTION, extended);
					bag = outsidePatientTime ? bagsExtendedOutside.get(key) : bagsExtended.get(key);
					if (bag != null && bag.getSize() > 0){
						//prepare file name and header
						String prescriptionsFile = StringUtilities.addSuffixToFileName(outputFileName, "_Prescriptions_"+StringUtilities.capitalize(extended)+"s"+(suffix != null && !suffix.equals("") ? "_"+suffix : "") + ".csv", true);
						Jerboa.getOutputManager().addFile(prescriptionsFile);
						String record = "PatientGender";
						record += "," + "ATC";
						record += "," + StringUtilities.capitalize(extended);
						record += "," + "Count";
						Jerboa.getOutputManager().writeln(prescriptionsFile, record, true);
						outputBag(bag, prescriptionsFile);
						Jerboa.getOutputManager().closeFile(prescriptionsFile);
					}
				}
			}
		}

		//OUTPUT MEASUREMENTS
		if (neededFiles.get(DataDefinition.MEASUREMENTS_FILE)){

			//overall counts
			ExtendedMultiKey key = new ExtendedMultiKey(DataDefinition.EPISODE_MEASUREMENT, "overall");
			MultiKeyBag bag = outsidePatientTime ? bagsOutside.get(key) : bags.get(key);
			if (bag.getSize() > 0){
				String measurementsFile = StringUtilities.addSuffixToFileName(outputFileName, "_Measurements"+(suffix != null && !suffix.equals("") ? "_"+suffix : "")+ ".csv", true);
				Jerboa.getOutputManager().addFile(measurementsFile);
				String record = "PatientGender";
				record += "," + "MeasurementType";
				record += "," + "Count";
				Jerboa.getOutputManager().writeln(measurementsFile, record, true);
				outputBag(bag, measurementsFile);
				Jerboa.getOutputManager().closeFile(measurementsFile);
			}

			//value - still mandatory
			key = new ExtendedMultiKey(DataDefinition.EPISODE_MEASUREMENT, DataDefinition.MEASUREMENT_VALUE);
			bag = outsidePatientTime ? bagsOutside.get(key) : bags.get(key);
			if (bag.getSize() > 0){
				String measurementsFile = StringUtilities.addSuffixToFileName(outputFileName, "_Measurements_Values"+(suffix != null && !suffix.equals("") ? "_"+suffix : "") + ".csv", true);
				Jerboa.getOutputManager().addFile(measurementsFile);
				String record = "PatientGender";
				record += "," + "MeasurementType";
				record += "," + "Value";
				record += "," + "Count";
				Jerboa.getOutputManager().writeln(measurementsFile, record, true);
				outputBag(bag, measurementsFile);
				Jerboa.getOutputManager().closeFile(measurementsFile);
			}

			//extended
			if (InputFileUtilities.getExtendedDataOrder(DataDefinition.MEASUREMENTS_FILE) != null){
				Set<String> extendedColumns = new HashSet<String>(InputFileUtilities.getExtendedDataOrder(DataDefinition.MEASUREMENTS_FILE).values());
				for (String extended : extendedColumns){
					key = new ExtendedMultiKey(DataDefinition.EPISODE_MEASUREMENT, extended);
					bag = outsidePatientTime ? bagsExtendedOutside.get(key) : bagsExtended.get(key);
					if (bag != null && bag.getSize() > 0){
						//prepare file name and header
						String measurementsFile = StringUtilities.addSuffixToFileName(outputFileName, "_Measurements_"+StringUtilities.capitalize(extended)+"s"+(suffix != null && !suffix.equals("") ? "_"+suffix : "") + ".csv", true);
						Jerboa.getOutputManager().addFile(measurementsFile);
						String record = "PatientGender";
						record += "," + "MeasurementType";
						record += "," + StringUtilities.capitalize(extended);
						record += "," + "Count";
						Jerboa.getOutputManager().writeln(measurementsFile, record, true);
						outputBag(bag, measurementsFile);
						Jerboa.getOutputManager().closeFile(measurementsFile);
					}
				}
			}
		}

	}

	@Override
	public void calcStats(){

		//patient stats for extended data columns
		String patientsStatsFile = StringUtilities.addSuffixToFileName(outputFileName, "_Patient_Stats.csv", true);
		outputPatientStats(patientsStatsFile);
		Jerboa.getOutputManager().closeFile(patientsStatsFile);

		//output event stats (only if extended numeric columns are present)
		if (neededFiles.get(DataDefinition.EVENTS_FILE)){
			String eventsStatsFile = StringUtilities.addSuffixToFileName(outputFileName, "_Event_Stats.csv", true);
			outputEventStats(eventsStatsFile);
			Jerboa.getOutputManager().closeFile(eventsStatsFile);
		}

		//output prescriptions stats
		if (neededFiles.get(DataDefinition.PRESCRIPTIONS_FILE)){
			String prescriptionsStatsFile = StringUtilities.addSuffixToFileName(outputFileName, "_Prescriptions_Stats.csv", true);
			outputPrescriptionStats(prescriptionsStatsFile);
			Jerboa.getOutputManager().closeFile(prescriptionsStatsFile);
		}

		//output measurement stats
		if (neededFiles.get(DataDefinition.MEASUREMENTS_FILE)){
			String measurementsStatsFile = StringUtilities.addSuffixToFileName(outputFileName, "_Measurements_Stats.csv", true);
			outputMeasurementStats(measurementsStatsFile);
			Jerboa.getOutputManager().closeFile(measurementsStatsFile);
		}
	}

	@Override
	public void clearMemory(){
		super.clearMemory();

		//bags containing episodes in patient time
		this.bags = null;
		this.bagsOutside = null;
		this.bagsExtended = null;
		this.bagsExtendedOutside = null;

		//bags containing counts of episodes with missing attributes
		this.eventsWithMissingInfo = new MultiKeyBag();
		this.prescriptionsWithMissingInfo = new MultiKeyBag();
		this.measurementsWithMissingInfo = new MultiKeyBag();

		this.countersMissingExtended = null;

	}

	//--------------------------SPECIFIC METHODS--------------------//
		/**
	 * Parses the parameters dealing with the columns to be ignored,
	 * either from the calculation of statistics only (fromStatsOnly = true)
	 * or from the whole processing step (fromStatsOnly == false) and puts the result
	 * into a map structure. Each episode type (e.g., patient, event, prescription)
	 * will have a list of columns that are to be ignored.
	 * @param fromStatsOnly - true if the parameter to be processed is ignoredColumnsFromStats;
	 * false if excludedColumns is processed
	 * @return - true if the parameter setting was successfully parsed; false otherwise
	 */
	private boolean parseColumnsToIgnore(boolean fromStatsOnly){

		if (fromStatsOnly)
			extendedColumnsToIgnoreFromStatsCalculation = new HashMap<String, List<String>>();
		else
			columnsToIgnoreFromProcessing = new HashMap<String, List<String>>();

		List<String> list = fromStatsOnly ? ignoredColumnsForStats : excludedColumns;
		if (list != null && list.size() > 0){
			for (String s : list){
				String[] parts = s.split(";");
				if (parts == null || parts.length < 2){
					Logging.add("Incorrect parameter setting for " +
							(fromStatsOnly ? "ignoredColumnsForStats" : "excludedColumns") + ": " + s, Logging.ERROR);
					return false;
				}

				//make sure the episode type does not end in S (as in plural) and it is a valid episode name
				String episodeName = parts[0].trim().toLowerCase();
				episodeName = episodeName.endsWith("s") ? episodeName.substring(0, episodeName.length()-1) : episodeName;
				if (!InputFileUtilities.isValidEpisodeName(episodeName)){
					Logging.add("Incorrect episode name for the " +
							(fromStatsOnly ? "ignoredColumnsForStats" : "excludedColumns") + ": " + episodeName, Logging.ERROR);
					return false;
				}

				//make sure there is at least one extended data column passed as parameter for this episode name
				String[] columns = parts[1].split(",");
				if (columns == null || columns.length == 0){
					Logging.add("No extended data columns specified for "+episodeName+" for the " +
							(fromStatsOnly ? "ignoredColumnsForStats" : "excludedColumns"), Logging.ERROR);
					return false;
				}

				columns = StringUtilities.trim(columns);
				columns = StringUtilities.toLowerCase(columns);
				if (fromStatsOnly)
					extendedColumnsToIgnoreFromStatsCalculation.put(episodeName, Arrays.asList(columns));
				else
					columnsToIgnoreFromProcessing.put(episodeName, Arrays.asList(columns));
			}
		}

		return true;
	}

	/**
	 * Writes the header of the stats output file per episode type and/or
	 * the patients file (if it contains numeric extended data columns).
	 * The header is input file type specific .
	 * @param fileType - the type of the input file
	 * @param outputFileName - the name of the stats output file
	 */
	private void writeHeaderStatsFile(byte fileType, String outputFileName){

		String record = "";
		boolean newFile = !Jerboa.getOutputManager().hasFile(outputFileName);
		if (newFile){
			Jerboa.getOutputManager().addFile(outputFileName);
			switch (fileType){
			case DataDefinition.PATIENTS_FILE:
				record = "PatientGender";
				break;
			case DataDefinition.EVENTS_FILE:
				record = "EventType";
				record += "," + "PatientGender";
				break;
			case DataDefinition.PRESCRIPTIONS_FILE:
				record = "ATC";
				record += "," + "PatientGender";
				break;
			case DataDefinition.MEASUREMENTS_FILE:
				record = "MeasurementType";
				record += "," + "PatientGender";
				break;

			}

			//the rest of the header
			record += "," + "Attribute";
			record += "," + "Min";
			record += "," + "Max";
			record += "," + "Count";
			record += "," + "Mean";
			record += "," + "1stQuartile";
			record += "," + "2ndQuartile";
			record += "," + "3rdQuartile";
			record += "," + "StdDev";
			record += "," + "NoValue";
			Jerboa.getOutputManager().writeln(outputFileName, record, false);
		}

	}

	/**
	 * Creates and returns a single clustered bar plot
	 * representing the counts per category for females and males.
	 * @param data - the bag containing the data to be plotted
	 * @param multiKey - the key dictating the data of interest
	 * @param multiKeyComponent - the index of the key component of interest
	 * @param title - title of the plot
	 * @param vertical - plot orientation
	 * @param checkIfNumericKeys - flag to check if values are numeric
	 * @return - a clustered bar plot
	 */
	private Plot getPlot(MultiKeyBag data, ExtendedMultiKey multiKey, int multiKeyComponent, String title, boolean vertical, boolean checkIfNumericKeys){

		boolean hasNumericKeys = false;
		if (checkIfNumericKeys)
			hasNumericKeys = hasNumericKeys(data.getSubBagAsMap(multiKeyComponent,multiKey));

		Plot plot = null;
		if (data != null && data.getSize() > 0){
			if (hasNumericKeys){
				plot = new BarPlotDS.Builder(title)
				.clustered(false)
				.showLegend(false)
				.XLabel("Value").YLabel("Count")
				.data(convertKeysToNumeric(data.getSubBagAsHashBag(multiKeyComponent,multiKey)))
				.build();
			}else{
				plot = new BarPlotCategory.Builder(title)
				.verticalLabels(true).sortByValue(true).forceVertical(vertical).maxEntriesToDisplay(50)
				.showLegend(true)
				.XLabel("Value").YLabel("Count")
				.build();
			}

			if (!hasNumericKeys){
				multiKey = multiKey.setKeyComponent(0, DataDefinition.FEMALE_GENDER);
				plot.addSeriesToDataset(data.getSubBagAsMap(multiKeyComponent,multiKey), "Females");
				multiKey = multiKey.setKeyComponent(0, DataDefinition.MALE_GENDER);
				plot.addSeriesToDataset(data.getSubBagAsMap(multiKeyComponent,multiKey), "Males");
			}
		}

		return plot;

	}

	/**
	 * Creates and returns three bar plots.
	 * First one represents the total counts (females+males) and the other
	 * two represent the counts per gender. It can be specified to check if
	 * the values for which the counts are plotted are numeric so that a
	 * discrete series bar plot object is invoked instead of a category bar plot.
	 * @param data - the bag containing the data to be plotted
	 * @param multiKey - the key dictating the data of interest
	 * @param multiKeyComponent - the index of the key component of interest
	 * @param title - title of the plot
	 * @param checkIfNumericKeys - flag to check if values are numeric
	 * @return - a list of plots
	 */
	@SuppressWarnings("unused")
	private List<Plot> getPlotsPerGender(MultiKeyBag data, ExtendedMultiKey multiKey, int multiKeyComponent, String title, boolean checkIfNumericKeys){

		List<Plot> plotList = new ArrayList<Plot>();
		boolean hasNumericKeys = false;
		if (checkIfNumericKeys)
			hasNumericKeys = hasNumericKeys(data.getSubBagAsMap(multiKeyComponent,multiKey));

		//total
		if (hasNumericKeys)
			plotList.add(new BarPlotDS.Builder(title+" - total")
			.XLabel("Value").YLabel("Count")
			.data(convertKeysToNumeric(data.getSubBagAsHashBag(multiKeyComponent,multiKey))).build());
		else
			plotList.add(new BarPlotCategory.Builder(title+" - total")
			.verticalLabels(true).sortByValue(true).forceVertical(true).maxEntriesToDisplay(50)
			.XLabel("Value").YLabel("Count")
			.data(data.getSubBagAsMap(multiKeyComponent,multiKey)).build());

		//females
		multiKey = multiKey.setKeyComponent(0, DataDefinition.FEMALE_GENDER);
		if (hasNumericKeys)
			plotList.add(new BarPlotDS.Builder(title+" - females")
			.XLabel("Value").YLabel("Count")
			.data(convertKeysToNumeric(data.getSubBagAsHashBag(multiKeyComponent, multiKey))).build());
		else
			plotList.add(new BarPlotCategory.Builder(title+" - females")
			.verticalLabels(true).sortByValue(true).forceVertical(true).maxEntriesToDisplay(50)
			.XLabel("Value").YLabel("Count")
			.data(data.getSubBagAsMap(multiKeyComponent, multiKey)).build());

		//males
		multiKey = multiKey.setKeyComponent(0, DataDefinition.MALE_GENDER);
		if (hasNumericKeys)
			plotList.add(new BarPlotDS.Builder(title+" - males")
			.XLabel("Value").YLabel("Count")
			.data(convertKeysToNumeric(data.getSubBagAsHashBag(multiKeyComponent, multiKey))).build());
		else
			plotList.add(new BarPlotCategory.Builder(title+" - males")
			.verticalLabels(true).sortByValue(true).forceVertical(true).maxEntriesToDisplay(50)
			.XLabel("Value").YLabel("Count")
			.data(data.getSubBagAsMap(multiKeyComponent, multiKey)).build());

		return plotList;

	}

	/**
	 * Checks if all the keys of map are numeric.
	 * @param map - the data to be checked
	 * @return - true if all keys of map are numeric; false otherwise
	 */
	private boolean hasNumericKeys(TreeMap<Object, Object> map){
		if (map != null && map.size() > 0)
			for (Object key : map.keySet())
				if (!isNumeric(key))
					return false;
		return true;
	}

	/**
	 * Checks if value object is numeric.
	 * This method checks if value is an instance of Integer
	 * or Double or Long. If not, it tries to parse value to one of those three types.
	 * @param value - the value to be checked if numeric
	 * @return - true if value is numeric; false otherwise
	 */
	private boolean isNumeric(Object value) {

		if ((value instanceof Integer) ||
				(value instanceof Double) ||
				(value instanceof Long))
			return true;

		boolean parsed = false;
		try {
			Integer.parseInt(value.toString());
			parsed = true;
		} catch(NumberFormatException e) {}
		try {
			Long.parseLong(value.toString());
			parsed = true;
		} catch(NumberFormatException e) {}
		try {
			Double.parseDouble(value.toString());
			parsed = true;
		} catch(NumberFormatException e) {}

		return parsed;
	}

	/**
	 * Converts the set of keys from data to Number objects
	 * for later use in the plot objects. Note that the order
	 * of the conversions (try/catch) is important
	 * @param data - the HashBag with the keys to be converted
	 * @return - a new HashBag with keys as Number objects
	 */
	private HashBag convertKeysToNumeric(HashBag data){
		HashBag newBag = new HashBag();
		if (data != null && data.size() > 0){
			@SuppressWarnings("unchecked")
			Set<Object> set = data.uniqueSet();
			for (Object key: set){
				Number newKey = null;
				try {
					newKey = Integer.parseInt(key.toString());
				} catch(NumberFormatException e) {}
				try {
					newKey = Long.parseLong(key.toString());
				} catch(NumberFormatException e) {}
				try {
					newKey = Double.parseDouble(key.toString());
				} catch(NumberFormatException e) {}

				newBag.add(newKey, data.getCount(key));
			}
		}

		return newBag;
	}

	/**
	 * Converts the set of keys from data to Number objects
	 * for later use in the histogram stats objects. Note that the order
	 * of the conversions (try/catch) is important
	 * @param data - the HashBag with the keys to be converted
	 * @return - a TreeMap with keys as Number objects
	 */
	private TreeMap<Object, Integer> convertKeysToNumericAsMap(HashBag data){
		TreeMap<Object, Integer> newBag = new TreeMap<Object, Integer>();
		if (data != null && data.size() > 0){
			@SuppressWarnings("unchecked")
			Set<Object> set = data.uniqueSet();
			for (Object key: set){
				Number newKey = null;
				try {
					newKey = Integer.parseInt(key.toString());
				} catch(NumberFormatException e) {}
				try {
					newKey = Long.parseLong(key.toString());
				} catch(NumberFormatException e) {}
				try {
					newKey = Double.parseDouble(key.toString());
				} catch(NumberFormatException e) {}

				newBag.put(newKey, data.getCount(key));
			}
		}

		return newBag;
	}

	/**
	 * Converts the set of keys from data to Number objects
	 * for later use in the plot objects. Note that the order
	 * of the conversions (try/catch) is important.
	 * @param data - the HashBag with the keys to be converted
	 * @return - a new HashBag with keys as Number objects
	 */
	@SuppressWarnings("unused")
	private TreeMap<Object, Integer> convertKeysToNumeric(TreeMap<Object, Integer> data){
		TreeMap<Object, Integer> newMap = new TreeMap<Object, Integer>();
		if (data != null && data.size() > 0){
			for (Object key: data.keySet()){
				Number newKey = null;
				try {
					newKey = Integer.parseInt(key.toString());
				} catch(NumberFormatException e) {}
				try {
					newKey = Long.parseLong(key.toString());
				} catch(NumberFormatException e) {}
				try {
					newKey = Double.parseDouble(key.toString());
				} catch(NumberFormatException e) {}

				newMap.put(newKey, data.get(key));
			}
		}

		return newMap;
	}

	//OUTPUT RELATED
	/**
	 * Writes the contents of the bag to a delimited file.
	 * The key set of the bag is sorted and the numeric values of the
	 * gender are replaced with characters (e.g., from '0' to 'F').
	 * The overall counts (females + males) are also calculated.
	 * Note that it assumes the gender is the first element of the multi key.
	 * @param bag - the data
	 * @param fileName - name of the output file
	 */
	private void outputBag(MultiKeyBag bag, String fileName){
		//totals
		TreeSet<ExtendedMultiKey> keySet = bag.getSortedKeySet(1);
		List<String> usedKeys = new ArrayList<String>();
		for (ExtendedMultiKey key : keySet){
			String oneKey = key.setKeyComponent(0, (byte)-1).toString();
			if (!usedKeys.contains(oneKey)){
				key = key.setKeyComponent(0, Wildcard.BYTE());
				int count = 0;
				count += bag.getCount(key);

				String record = "T";
				Object[] keyComponents = key.getKeys();
				for (int i = 1; i < keyComponents.length; i ++)
					record += "," + keyComponents[i];

				record += "," + count;
				Jerboa.getOutputManager().writeln(fileName, record, true);

				usedKeys.add(oneKey);
			}
		}
		//females and males
		keySet = bag.getSortedKeySet();
		for (ExtendedMultiKey key: keySet){
			Object[] keyComponents = key.getKeys();
			String record = keyComponents[0].toString().equals("0") ? "F" : "M";
			for (int i = 1; i < keyComponents.length; i ++){
				record += "," + keyComponents[i];
			}
			record += "," + bag.getCount(key);
			Jerboa.getOutputManager().writeln(fileName, record, true);
		}
	}

	/**
	 * Writes to file the statistical summary of the extended data columns
	 * of the patients file, if all the values are numeric.
	 * It will calculate the statistics per patient gender.
	 * Note that it assumes that the gender is the first component of the multi key.
	 * @param outputFile - the name of the output file
	 */
	private void outputPatientStats(String outputFile){

		//and map genders
		Object[] gender = new Object[]{DataDefinition.FEMALE_GENDER, DataDefinition.MALE_GENDER, Wildcard.BYTE()};
		Object[] genderString = new Object[]{"F", "M", "T"};

		for (int i = 0; i < gender.length; i++){
			//extended
			ExtendedMultiKey multiKey = new ExtendedMultiKey(gender[i], Wildcard.STRING());
			if (InputFileUtilities.getExtendedDataOrder(DataDefinition.PATIENTS_FILE) != null){
				Set<String> extendedColumns = new HashSet<String>(InputFileUtilities.getExtendedDataOrder(DataDefinition.PATIENTS_FILE).values());
				for (String extended : extendedColumns){
					if (includeColumnForStats(DataDefinition.PATIENT, extended)){
						ExtendedMultiKey key = new ExtendedMultiKey(DataDefinition.PATIENT, extended);
						MultiKeyBag bag = bagsExtended.get(key);
						if (bag != null && bag.getSize() > 0){
							if (hasNumericKeys(bag.getSubBagAsMap(1, multiKey))){
								writeHeaderStatsFile(DataDefinition.PATIENTS_FILE, outputFile);
								HistogramStats hs = new HistogramStats(convertKeysToNumericAsMap(bag.getSubBagAsHashBag(1,multiKey)));
								if (hs.hasData()){
									String record = genderString[i].toString() + "," + StringUtilities.capitalize(extended);
									record += "," + statsToString(hs, true);
									record += "," + patientsWithMissingInfo.getCount(new ExtendedMultiKey(gender[i], extended));
									Jerboa.getOutputManager().writeln(outputFile,record, true);
								}
							}
						}
					}
				}
			}
		}//end for over genders
	}

	/**
	 * Writes to file the statistical summary of the extended data columns
	 * of the events file, if all the values are numeric.
	 * It will calculate the statistics for each event type and per patient gender.
	 * Note that it assumes the event type info is the second component in the multi key
	 * and the gender is the first component of the multi key.
	 * @param outputFile - the name of the output file
	 */
	private void outputEventStats(String outputFile){

		//get all ATCs
		MultiKeyBag bag = bags.get(new ExtendedMultiKey(DataDefinition.EPISODE_EVENT, "overall"));
		Set<String> types = bag.getKeyValuesAsString(1);

		//and map genders
		Object[] gender = new Object[]{DataDefinition.FEMALE_GENDER, DataDefinition.MALE_GENDER, Wildcard.BYTE()};
		Object[] genderString = new Object[]{"F", "M", "T"};

		for (int i = 0; i < gender.length; i++){
			for (String type : types){

				//extended
				ExtendedMultiKey multiKey = new ExtendedMultiKey(gender[i], type, Wildcard.STRING());
				if (InputFileUtilities.getExtendedDataOrder(DataDefinition.EVENTS_FILE) != null){
					Set<String> extendedColumns = new HashSet<String>(InputFileUtilities.getExtendedDataOrder(DataDefinition.EVENTS_FILE).values());
					for (String extended : extendedColumns){
						if (includeColumnForStats(DataDefinition.EPISODE_EVENT, extended)){
							ExtendedMultiKey key = new ExtendedMultiKey(DataDefinition.EPISODE_EVENT, extended);
							bag = bagsExtended.get(key);
							if (bag != null && bag.getSize() > 0){
								if (hasNumericKeys(bag.getSubBagAsMap(2, multiKey))){
									writeHeaderStatsFile(DataDefinition.EVENTS_FILE, outputFile);
									HistogramStats hs = new HistogramStats(convertKeysToNumericAsMap(bag.getSubBagAsHashBag(2,multiKey)));
									if (hs.hasData()){
										String record = type + "," + genderString[i].toString() + "," + StringUtilities.capitalize(extended);
										record += "," + statsToString(hs, true);
										record += "," + eventsWithMissingInfo.getCount(new ExtendedMultiKey(gender[i], type, extended));
										Jerboa.getOutputManager().writeln(outputFile,record, true);
									}
								}
							}
						}
					}
				}
			}//end for over types
		}//end for over genders
	}


	/**
	 * Writes to file the statistical summary of the prescriptions
	 * durations and extended data columns if all the values are numeric.
	 * It will calculate the statistics for each ATC code and per patient gender.
	 * Note that it assumes the ATC info is the second component in the multi key
	 * and the gender is the first component of the multi key.
	 * @param outputFile - the name of the output file
	 */
	private void outputPrescriptionStats(String outputFile){

		//get all ATCs
		MultiKeyBag bag = bags.get(new ExtendedMultiKey(DataDefinition.EPISODE_PRESCRIPTION, "overall"));
		Set<String> types = bag.getKeyValuesAsString(1);

		//and map genders
		Object[] gender = new Object[]{DataDefinition.FEMALE_GENDER, DataDefinition.MALE_GENDER, Wildcard.BYTE()};
		Object[] genderString = new Object[]{"F", "M", "T"};

		//get the duration bag only once
		MultiKeyBag durationBag = bags.get(new ExtendedMultiKey(DataDefinition.EPISODE_PRESCRIPTION, DataDefinition.PRESCRIPTION_DURATION));

		for (int i = 0; i < gender.length; i++){
			for (String type : types){

				//duration - still mandatory
				if (durationBag != null){
					if (includeColumnForStats(DataDefinition.EPISODE_PRESCRIPTION, DataDefinition.PRESCRIPTION_DURATION)){
						ExtendedMultiKey multiKey = new ExtendedMultiKey(gender[i], type, Wildcard.INTEGER());
						if (hasNumericKeys(durationBag.getSubBagAsMap(2, multiKey))){
							writeHeaderStatsFile(DataDefinition.PRESCRIPTIONS_FILE, outputFile);
							HistogramStats hs = durationBag.getHistogramStats(multiKey);
							if (hs.hasData()){
								String record = type + "," + genderString[i].toString() + "," + "Duration";
								record += "," + statsToString(hs, true);
								record += "," + prescriptionsWithMissingInfo.getCount(new ExtendedMultiKey(gender[i], type, DataDefinition.PRESCRIPTION_DURATION));
								Jerboa.getOutputManager().writeln(outputFile,record, true);
							}
						}
					}
				}

				//extended
				ExtendedMultiKey multiKey = new ExtendedMultiKey(gender[i], type, Wildcard.STRING());
				if (InputFileUtilities.getExtendedDataOrder(DataDefinition.PRESCRIPTIONS_FILE) != null){
					Set<String> extendedColumns = new HashSet<String>(InputFileUtilities.getExtendedDataOrder(DataDefinition.PRESCRIPTIONS_FILE).values());
					for (String extended : extendedColumns){
						if (includeColumnForStats(DataDefinition.EPISODE_PRESCRIPTION, extended)){
							ExtendedMultiKey key = new ExtendedMultiKey(DataDefinition.EPISODE_PRESCRIPTION, extended);
							bag = bagsExtended.get(key);
							if (bag != null && bag.getSize() > 0){
								if (hasNumericKeys(bag.getSubBagAsMap(2, multiKey))){
									writeHeaderStatsFile(DataDefinition.PRESCRIPTIONS_FILE, outputFile);
									HistogramStats hs = new HistogramStats(convertKeysToNumericAsMap(bag.getSubBagAsHashBag(2,multiKey)));
									if (hs.hasData()){
										String record = type + "," + genderString[i].toString() + "," + StringUtilities.capitalize(extended);
										record += "," + statsToString(hs, true);
										record += "," + prescriptionsWithMissingInfo.getCount(new ExtendedMultiKey(gender[i], type, extended));
										Jerboa.getOutputManager().writeln(outputFile,record, true);
									}
								}
							}
						}
					}
				}
			}//end for over types
		}//end for over genders
	}

	/**
	 * Writes to file the statistical summary of the measurements.
	 * It will calculate the statistics for each measurement type, if and only
	 * if, the values or extended data columns of the measurement type can be converted to numeric values.
	 * Note that it assumes the measurement type is the second component in the multi key
	 * and the gender is the first component of the multi key.
	 * @param outputFile - the name of the output file
	 */
	private void outputMeasurementStats(String outputFile){

		//get all measurement types
		MultiKeyBag bag = bags.get(new ExtendedMultiKey(DataDefinition.EPISODE_MEASUREMENT, "overall"));
		Set<String> types = bag.getKeyValuesAsString(1);

		//and map genders
		Object[] gender = new Object[]{DataDefinition.FEMALE_GENDER, DataDefinition.MALE_GENDER, Wildcard.BYTE()};
		Object[] genderString = new Object[]{"F", "M", "T"};

		//get the values bag only once
		MultiKeyBag valueBag = bags.get(new ExtendedMultiKey(DataDefinition.EPISODE_MEASUREMENT, DataDefinition.MEASUREMENT_VALUE));

		for (int i = 0; i < gender.length; i++){
			for (String type : types){
				ExtendedMultiKey multiKey = new ExtendedMultiKey(gender[i], type, Wildcard.STRING());

				//value - still mandatory
				if (valueBag != null){
					if (includeColumnForStats(DataDefinition.EPISODE_MEASUREMENT, DataDefinition.MEASUREMENT_VALUE)){
						if (hasNumericKeys(valueBag.getSubBagAsMap(2, multiKey))){
							writeHeaderStatsFile(DataDefinition.MEASUREMENTS_FILE, outputFile);
							HistogramStats hs = new HistogramStats(convertKeysToNumericAsMap(valueBag.getSubBagAsHashBag(2,multiKey)));
							if (hs.hasData()){
								String record = type + "," + genderString[i].toString() + "," + "Value";
								record += "," + statsToString(hs, true);
								record += "," + measurementsWithMissingInfo.getCount(new ExtendedMultiKey(gender[i], type, DataDefinition.MEASUREMENT_VALUE));
								Jerboa.getOutputManager().writeln(outputFile,record, true);
							}
						}
					}
				}

				//extended
				if (InputFileUtilities.getExtendedDataOrder(DataDefinition.MEASUREMENTS_FILE) != null){
					Set<String> extendedColumns = new HashSet<String>(InputFileUtilities.getExtendedDataOrder(DataDefinition.MEASUREMENTS_FILE).values());
					for (String extended : extendedColumns){
						if (includeColumnForStats(DataDefinition.EPISODE_MEASUREMENT, extended)){
							ExtendedMultiKey key = new ExtendedMultiKey(DataDefinition.EPISODE_MEASUREMENT, extended);
							bag = bagsExtended.get(key);
							if (bag != null && bag.getSize() > 0){
								if (hasNumericKeys(bag.getSubBagAsMap(2, multiKey))){
									writeHeaderStatsFile(DataDefinition.MEASUREMENTS_FILE, outputFile);
									HistogramStats hs = new HistogramStats(convertKeysToNumericAsMap(bag.getSubBagAsHashBag(2,multiKey)));
									if (hs.hasData()){
										String record = type + "," + genderString[i].toString() + "," + StringUtilities.capitalize(extended);
										record += "," + statsToString(hs, true);
										record += "," + measurementsWithMissingInfo.getCount(new ExtendedMultiKey(gender[i], type, extended));
										Jerboa.getOutputManager().writeln(outputFile,record, true);
									}
								}
							}
						}
					}
				}
			}//end loop over types
		}//end loop over gender
	}

	/**
	 * Checks if column should be included in the calculation of the
	 * descriptive statistics for this episodeType.
	 * @param episodeType - the type of the episode we refer to
	 * @param column - the column from the episodeType to be checked
	 * @return - true if column should be included in the calculation of stats.
	 */
	private boolean includeColumnForStats(String episodeType, String column){
		return extendedColumnsToIgnoreFromStatsCalculation == null ||
				extendedColumnsToIgnoreFromStatsCalculation.get(episodeType) == null ||
				!extendedColumnsToIgnoreFromStatsCalculation.get(episodeType).contains(column);
	}

	/**
	 * Checks if column should be included in the processing for this episodeType.
	 * @param episodeType - the type of the episode we refer to
	 * @param column - the column from the episodeType to be checked
	 * @return - true if column should be included in the processing.
	 */
	private boolean includeColumnForProcessing(String episodeType, String column){
		return columnsToIgnoreFromProcessing == null ||
				columnsToIgnoreFromProcessing.get(episodeType) == null ||
				!columnsToIgnoreFromProcessing.get(episodeType).contains(column);
	}

	/**
	 * Writes to file the contents of the bags containing counts
	 * of the missing attributes of different episode types.
	 * A header is created for each type of file, based on the contents of the bag.
	 */
	private void outputBagsMissingInfo(){

		if (neededFiles.get(DataDefinition.EVENTS_FILE) && eventsWithMissingInfo.getSize() > 0){
			String eventsMissingInfoFile = StringUtilities.addSuffixToFileName(outputFileName, "_Events_Missing.csv", true);
			Jerboa.getOutputManager().addFile(eventsMissingInfoFile);
			outputBagMissingInfo(eventsWithMissingInfo, bags.get(new ExtendedMultiKey(DataDefinition.EPISODE_EVENT, "overall")), eventsMissingInfoFile);
			Jerboa.getOutputManager().closeFile(eventsMissingInfoFile);
		}

		if (neededFiles.get(DataDefinition.PRESCRIPTIONS_FILE) && prescriptionsWithMissingInfo.getSize() > 0){
			String prescriptionsMissingInfoFile = StringUtilities.addSuffixToFileName(outputFileName, "_Prescriptions_Missing.csv", true);
			Jerboa.getOutputManager().addFile(prescriptionsMissingInfoFile);
			outputBagMissingInfo(prescriptionsWithMissingInfo, bags.get(new ExtendedMultiKey(DataDefinition.EPISODE_PRESCRIPTION, "overall")), prescriptionsMissingInfoFile);
			Jerboa.getOutputManager().closeFile(prescriptionsMissingInfoFile);
		}

		if (neededFiles.get(DataDefinition.MEASUREMENTS_FILE) && measurementsWithMissingInfo.getSize() > 0){
			String measurementsMissingInfoFile = StringUtilities.addSuffixToFileName(outputFileName, "_Measurments_Missing.csv", true);
			Jerboa.getOutputManager().addFile(measurementsMissingInfoFile);
			outputBagMissingInfo(measurementsWithMissingInfo, bags.get(new ExtendedMultiKey(DataDefinition.EPISODE_MEASUREMENT, "overall")), measurementsMissingInfoFile);
			Jerboa.getOutputManager().closeFile(measurementsMissingInfoFile);
		}
	}

	/**
	 * Writes the contents of the bag to a delimited file.
	 * The key set of the bag is sorted and the numeric values of the
	 * gender are replaced with characters (e.g., from '0' to 'F').
	 * The overall counts (females + males) are also calculated.
	 * Note that it assumes the gender is the first element of the multi key,
	 * second is the episode type and third the missing attribute for that episode.
	 * @param bagMissing - the counts for data with missing information
	 * @param bagWith - the counts for data with complete information - used to calculate percentages
	 * @param fileName - name of the output file
	 */
	private void outputBagMissingInfo(MultiKeyBag bagMissing, MultiKeyBag bagWith, String fileName){
		Set<String> missingAttributes = bagMissing.getKeyValuesAsString(2);
		Set<String> types = bagMissing.getKeyValuesAsString(1);

		//TODO: optimize eventually to loop only once through events. You'll end up having TFMTFM instead of TTTTFFFFFMMMM

		long countTotal = 0;
		long countMissingPerColumn = 0;

		//header
		String record = "PatientGender";
		record += "," + "EpisodeType";
		for (String missing : missingAttributes){
			record += "," + "No"+StringUtilities.capitalize(missing);
			record += "," + "PercNo"+StringUtilities.capitalize(missing);
		}
		Jerboa.getOutputManager().writeln(fileName, record, false);

		//totals
		for (String type : types){
			record = "T";
			record += "," + type;
			countTotal = bagWith.getCount(new ExtendedMultiKey(Wildcard.BYTE(), type));
			for (String missing : missingAttributes){
				countMissingPerColumn = bagMissing.getCount(new ExtendedMultiKey(Wildcard.BYTE(), type, missing));
				record += "," + countMissingPerColumn;
				record += "," + (countTotal > 0 ? StringUtilities.format((countMissingPerColumn*100)/(double)(countTotal)) : "NA");
			}
			Jerboa.getOutputManager().writeln(fileName, record, true);
		}

		//females
		for (String type : types){
			record = "F";
			record += "," + type;
			countTotal = bagWith.getCount(new ExtendedMultiKey(Wildcard.BYTE(), type));
			for (String missing : missingAttributes){
				countMissingPerColumn = bagMissing.getCount(new ExtendedMultiKey(DataDefinition.FEMALE_GENDER, type, missing));
				record += "," + countMissingPerColumn;
				record += "," + (countTotal > 0 ? StringUtilities.format((countMissingPerColumn*100)/(double)(countTotal)) : "NA");
			}
			Jerboa.getOutputManager().writeln(fileName, record, true);
		}

		//males
		for (String type : types){
			record = "M";
			record += "," + type;
			countTotal = bagWith.getCount(new ExtendedMultiKey(Wildcard.BYTE(), type));
			for (String missing : missingAttributes){
				countMissingPerColumn = bagMissing.getCount(new ExtendedMultiKey(DataDefinition.MALE_GENDER, type, missing));
				record += "," + countMissingPerColumn;
				record += "," + (countTotal > 0 ? StringUtilities.format((countMissingPerColumn*100)/(double)(countTotal)) : "NA");
			}
			Jerboa.getOutputManager().writeln(fileName, record, true);
		}
	}

	/**
	 * Returns a String representation of the statistics
	 * @param stats - statistics of a bag
	 * @param outputCount - if true the count is added in the total column, otherwise the sum
	 * @return - String representation or empty string if there is no data
	 * @see HistogramStats
	 */
	private String statsToString(HistogramStats stats, boolean outputCount){
		if (stats != null){
			if (stats.getCount()>0) {
				return 	format(stats.getMin()) +","+
						format(stats.getMax()) +","+
						(outputCount ? format(stats.getCount()) : format(stats.getSum()))+","+
						format(stats.getMean())+","+
						format(stats.getPercentile(25))+","+
						format(stats.getPercentile(50))+","+
						format(stats.getPercentile(75))+","+
						format(stats.getStdDev());
			}
		}

		return ",,,,,,,";
	}

	/**
	 * The short form of the Parameters.DECIMAL_FORMAT.format
	 * @param value - the value to be formatted
	 * @return - the formatted double value
	 */
	private String format(double value){
		return StringUtilities.format(value);
	}

}

