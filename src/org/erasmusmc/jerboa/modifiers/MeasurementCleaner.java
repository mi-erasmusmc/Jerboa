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
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.erasmusmc.jerboa.Jerboa;
import org.erasmusmc.jerboa.config.DataDefinition;
import org.erasmusmc.jerboa.dataClasses.Event;
import org.erasmusmc.jerboa.dataClasses.Measurement;
import org.erasmusmc.jerboa.dataClasses.Patient;
import org.erasmusmc.jerboa.engine.Modifier;
import org.erasmusmc.jerboa.utilities.DateUtilities;
import org.erasmusmc.jerboa.utilities.DelimitedStringBuilder;
import org.erasmusmc.jerboa.utilities.InputFileUtilities;
import org.erasmusmc.jerboa.utilities.Item;
import org.erasmusmc.jerboa.utilities.ItemList;
import org.erasmusmc.jerboa.utilities.Logging;

/**
 * This modifier cleans measurements. Multiple cleaning steps are implemented:
 * 1. selects measurement values if multiple of the same type are available on the same day
 * 2. removes outliers
 * 3. removes non-numeric values
 * 
 * @author Mees {@literal &} MG
 *
 */
public class MeasurementCleaner extends Modifier {
	
	/**
	 * List of measurements of interest to be cleaned.
	 * Reduces multiple measurements of the same type on the same day to the measurement
	 * with the highest priority value.<br> 
	 * <p>
	 * Format: {@code MeaurementType;Keep;Values}
	 * {@code MeasurementType}  - The variable name used in the measurement file.<br>
	 * (@code Keep}             - Specifies which values of measurements of the same type at the same date to keep.<br>
	 *                            Possible values are:
	 *                              KEEPALL       Keep all measurement values at the same date.
	 *                              KEEPFIRST     Keep only the first measurement value at the same date.
	 *                              KEEPLAST      Keep only the last measurement value at the same date.
	 *                              KEEPHIGHEST   Keep only the highest measurement value at the same date.
	 *                              KEEPLOWEST    Keep only the lowest measurement value at the same date.
	 *                              KEEPPRIORITY  Keep the value with the highest priority as specified in the Values parameter.
	 * {@code Values}           - When Keep = KEEPPRIORITY a semicolon-separated list of values in high to low priority.<br>
	 *                            When no values are specified it keeps the first one.
	 * <p>
	 * For example: {@code "COLON;POSITIVE;NEGATIVE;UNKNOWN"}
	 */	
	public List<String> measurements = new ArrayList<String>();
	
	/**
	 * List of events for which the specified measurements will be removed 
	 * if they appear in the specified window around that event
	 * <p>
	 * Format: {@code EventType;WindowStart;WindowEnd}
	 * {@code EventType}    - The type of event<br>
	 * {@code WindowStart}  - The start of the window, relative to the event and inclusive, where the measurements should be removed.<br>
	 *                        When empty it means ever before.<br>
	 * {@code WindowEnd}    - The end of the window, relative to the event and exclusive, where the measurements should be removed.<br>
	 *                        When empty it means ever after.
	 * <p>
	 * For example: {@code "PREGNANT;-365;365"}
	 */
	public List<String> eventWindows = new ArrayList<String>();
	
	/**List of outlier definitions. Will define the 
	 * valid intervals for a measurement value.
     * <p>
	 *	Format: {@code Measurement Type;Min value; Max value}<br>
	 *  Example: {@code outlierDefinitions = BMI;3,50}
	 */
	public List<String> outlierDefinitions = new ArrayList<String>();
	
	/**
	 * Removes all non-numeric values for the measurement types in this list.
	 */
	public List<String> removeNonNumeric = new ArrayList<String>();
	
	//PRIVATE PARAMETERS
	
	private static final int KEEPALL      = 0; 
	private static final int KEEPFIRST    = 1;
	private static final int KEEPLAST     = 2;
	private static final int KEEPHIGHEST  = 3;
	private static final int KEEPLOWEST   = 4;
	private static final int KEEPPRIORITY = 5;
	
	// File name variables
	private String removedMeasurementsFile;
	private String processLogFile;
	
	// The list with the measurement items as specified in the settings file
	private ItemList measurementsList = null;
	private Map<String, Integer> measurementKeep = null;
	private Map<String, List<String>> measurementValues = null;
	
	private Map<String, Integer> eventWindowStarts = null; 
	private Map<String, Integer> eventWindowEnds = null;
	
	//Keeps all the outlier definitions for each type of measurement
	private Map<Integer, Interval> outlierIntervals;
	
	private int statsPatientCount;
	private int statsPatientsWithMeasurements;
	private int statsMeasurementCount;
	private int statsRelevantMeasurementCount;
	private int statsMeasurementsNonNumericValueCount;
	
	private Map<Integer, Integer> statsMeasurementsFound = null;
	private Map<Integer, Integer> statsMeasurementsRemoved = null;
	
	private boolean alreadyCounted;
	private boolean alreadyRemoved;
	private Map<Measurement, String> reasonRemoved;
	
	// Variable to hold result for unit test
	private List<Measurement> unitTestRemoved;
	
	@Override
	public boolean init() {
		boolean initOK = true;
		
		//parse measurement definitions
		measurementsList = new ItemList(true,1);
		measurementsList.parseParamList(measurements);
		measurementKeep = new HashMap<String, Integer>();
		measurementValues = new HashMap<String, List<String>>();
		for (Item measurementItem : measurementsList.getItemList()) {
			int keep = -1;
			List<String> values = new ArrayList<String>();
			if (measurementItem.getParameters().size() > 1) {
				String keepParameter = measurementItem.getParameters().get(1).trim().toUpperCase();
				if (keepParameter.equals("KEEPALL"))           keep = KEEPALL;
				else if (keepParameter.equals("KEEPFIRST"))    keep = KEEPFIRST;
				else if (keepParameter.equals("KEEPLAST"))     keep = KEEPLAST;
				else if (keepParameter.equals("KEEPHIGHEST"))  keep = KEEPHIGHEST;
				else if (keepParameter.equals("KEEPLOWEST"))   keep = KEEPLOWEST;
				else if (keepParameter.equals("KEEPPRIORITY")) keep = KEEPPRIORITY;
				else {
					Logging.add("Illegal value (" + keepParameter + ") specified for parameter Keep in measurement definition \"" + measurementItem.getDescription() + "\"", Logging.ERROR, false);
					initOK = false;
				}
			}
			else {
				Logging.add("No value specified for parameter Keep in measurement definition \"" + measurementItem.getDescription() + "\"", Logging.ERROR, false);
				initOK = false;
			}
			if (keep == KEEPPRIORITY) {
				if (measurementItem.getParameters().size() > 2) {
					for (String value : measurementItem.getParameters().get(2).split(",")) {
						values.add(value);
					}
				}
				else {
					Logging.add("No value specified for parameter Values in measurement definition \"" + measurementItem.getDescription() + "\"", Logging.ERROR, false);
					initOK = false;
				}
			}
			//add to the list only if the type of measurement exists in the input data
			for (String measurementType : measurementItem.getLookup()) {
				Integer key = (Integer)(InputFileUtilities.measurementTypes.getKey(measurementType));
				if (key != null) {
					measurementKeep.put(measurementType, keep);
					measurementValues.put(measurementType, values);
				}
				else {
					Logging.add("No measurement type found for cleaning: " + measurementType, Logging.HINT, true);
				}
			}
		}
		
		//parse event window definitions
		eventWindowStarts = new HashMap<String, Integer>();
		eventWindowEnds = new HashMap<String, Integer>();
		for (String eventWindowDefinition : eventWindows) {
			String[] eventWindowDefinitionSplit = eventWindowDefinition.split(";");
			if (!eventWindowDefinitionSplit[0].equals("")) {
				String eventType = eventWindowDefinitionSplit[0]; 
				if (!eventWindowStarts.containsKey(eventType)) {
					Integer windowStart = null;
					if ((eventWindowDefinitionSplit.length > 1) && (!eventWindowDefinitionSplit[1].equals(""))) {
						try {
							windowStart = Integer.parseInt(eventWindowDefinitionSplit[1]);
						}
						catch (NumberFormatException e) {
							Logging.add("Illegal windowStart in eventWindow \"" + eventWindowDefinition + "\"", Logging.ERROR, false);
							initOK = false;
						}
					}
					Integer windowEnd = null;
					if ((eventWindowDefinitionSplit.length > 2) && (!eventWindowDefinitionSplit[2].equals(""))) {
						try {
							windowEnd = Integer.parseInt(eventWindowDefinitionSplit[2]);
						}
						catch (NumberFormatException e) {
							Logging.add("Illegal windowEnd in eventWindow \"" + eventWindowDefinition + "\"", Logging.ERROR, false);
							initOK = false;
						}
					}
					eventWindowStarts.put(eventType, windowStart);
					eventWindowEnds.put(eventType, windowEnd);
				}
				else {
					Logging.add("Duplicate eventType specified in eventWindow \"" + eventWindowDefinition + "\"", Logging.ERROR, false);
				}
			}
			else {
				Logging.add("No eventType specified in eventWindow \"" + eventWindowDefinition + "\"", Logging.ERROR, false);
			}
		}
		
		//parse outlier definitions
		outlierIntervals = new HashMap<Integer, Interval>();
		for (String outlierDefinition : outlierDefinitions) {
			try{
				String[] split = outlierDefinition.split(";");
				String[] interval = split[1].split(",");
				Integer key = (Integer)(InputFileUtilities.measurementTypes.getKey(split[0]));
				if (key != null)
					outlierIntervals.put(key, new Interval(Double.parseDouble(interval[0]), Double.parseDouble(interval[1])));
				else
					Logging.add("Measurement type not found for outlier definition: " + outlierDefinition, Logging.HINT, true);
			}catch(Exception e){
				Logging.add("Parsing of the outlier definition " + outlierDefinition + " was unsuccessful", Logging.ERROR);
				Logging.outputStackTrace(e);
				initOK = false;
			}
		}
		
		// Initialize stats
		statsPatientCount = 0;
		statsPatientsWithMeasurements = 0;
		statsMeasurementCount = 0;
		statsRelevantMeasurementCount = 0;
		statsMeasurementsNonNumericValueCount = 0;
		statsMeasurementsFound = new HashMap<Integer, Integer>();
		statsMeasurementsRemoved = new HashMap<Integer, Integer>();
		for (String measurementType : measurementValues.keySet()) {
			Integer key = (Integer)(InputFileUtilities.measurementTypes.getKey(measurementType));
			statsMeasurementsFound.put(key, 0);
			statsMeasurementsRemoved.put(key, 0);
		}
		
		//add to the measurement maps the types defined in outlier definition
		for (Integer key : outlierIntervals.keySet()){
			statsMeasurementsFound.put(key, 0);
			statsMeasurementsRemoved.put(key, 0);
		}	
		
		// Create the intermediate file if necessary. 
		if (intermediateFiles) {
			reasonRemoved = new HashMap<Measurement, String>();
			removedMeasurementsFile = intermediateFileName.replace(".csv", "_Removed.csv");
			initOK = initOK && Jerboa.getOutputManager().addFile(this.removedMeasurementsFile, 1);
			if (initOK) {
				DelimitedStringBuilder header = new DelimitedStringBuilder();
				header.append("PatientID");
				header.append("Date");
				header.append("MeasurementType");
				header.append("Value");
				header.append("Reason");
				Jerboa.getOutputManager().writeln(this.removedMeasurementsFile, header.toString(), false);
			}
			
			processLogFile = intermediateFileName.replace(".csv", "_ProcessLog.csv");
			initOK = initOK && Jerboa.getOutputManager().addFile(this.processLogFile, 1);
			if (initOK) {
				DelimitedStringBuilder header = new DelimitedStringBuilder();
				header.append("PatientID");
				header.append("Date");
				header.append("MeasurementType");
				header.append("Value");
				header.append("Action");
				Jerboa.getOutputManager().writeln(this.processLogFile, header.toString(), false);
			}
		}
		
		if (Jerboa.unitTest) {
			unitTestRemoved = new ArrayList<Measurement>();
		}
		
		return initOK;
	}
	

	@Override
	public Patient process(Patient patient) {
		statsPatientCount++;
		
		boolean patientWithMeasurements = false;
		List<Measurement> measurementsToRemove = new ArrayList<Measurement>();
		Map<String, Measurement> keep = null;
		Map<String, Measurement> first = null;
		Map<String, Measurement> last = null;
		Map<String, Measurement> highest = null;
		Map<String, Measurement> lowest = null;
		int lastDate = -1;
		
		if (patient.inPopulation){
			@SuppressWarnings("unused")
			int debug = 0;
		}
		for (Measurement measurement : patient.getMeasurements()) {
			patientWithMeasurements = true;
			statsMeasurementCount++;
			if (measurement.getDate() != lastDate) {
				keep = new HashMap<String, Measurement>();
				first = new HashMap<String, Measurement>();
				last = new HashMap<String, Measurement>();
				highest = new HashMap<String, Measurement>();
				lowest = new HashMap<String, Measurement>();
			}
			
			//removing measurements with no numeric value
			if (!alreadyRemoved && removeNonNumeric.contains(measurement.getType())) {
				boolean parseError = false;
				try {
					Double.parseDouble(measurement.getValue());
				}
				catch (NumberFormatException e) {
					parseError = true;
				}
				if (parseError || measurement.getValue().equals("NaN")) {
					statsMeasurementsNonNumericValueCount++;
					
					//TODO decide if wanted to keep track of measurements per type with non numeric values
					//		if (statsMeasurementsRemoved.get(measurement.type) == null)
					//				statsMeasurementsRemoved.put(measurement.type, 0);
					//		statsMeasurementsRemoved.put(measurement.type, statsMeasurementsRemoved.get(measurement.type) + 1);
					
					measurementsToRemove.add(measurement);
					if (intermediateFiles) 
						reasonRemoved.put(measurement, "Non-numeric value");
					alreadyRemoved = true; //just in case other operations will follow
				}
			}

			// Outlier identification and removal
			Interval allowedInterval = outlierIntervals.get(measurement.type);
			if (allowedInterval != null && allowedInterval.isOutsideInterval(measurement.getValue())) {
				measurementsToRemove.add(measurement);
				if (intermediateFiles) 
					reasonRemoved.put(measurement, "Outlier");
				statsMeasurementsRemoved.put(measurement.type, (statsMeasurementsRemoved.containsKey(measurement.type)  ? statsMeasurementsRemoved.get(measurement.type) : 0) + 1);
				alreadyRemoved = true;
			}
			
			//remove if in one of the event windows
			if (!alreadyRemoved) {
				for (Event event : patient.getEvents()) {
					if (eventWindowStarts.containsKey(event.getType())) {
						if (	(measurement.getDate() >= (eventWindowStarts.get(event.getType()) == null ? Integer.MIN_VALUE : (event.getDate() + eventWindowStarts.get(event.getType())))) &&
								(measurement.getDate() < (eventWindowEnds.get(event.getType()) == null ? Integer.MAX_VALUE : (event.getDate() + eventWindowEnds.get(event.getType()))))
							) {
							measurementsToRemove.add(measurement);
							if (intermediateFiles) 
								reasonRemoved.put(measurement, "Window " + event);
							statsMeasurementsRemoved.put(measurement.type, (statsMeasurementsRemoved.containsKey(measurement.type)  ? statsMeasurementsRemoved.get(measurement.type) : 0) + 1);
							alreadyRemoved = true;
						}
					}
				}
			}

			if (!alreadyRemoved) {
				for (Item measurementItem : measurementsList.getItemList()) {
					if (measurementItem.getLookup().contains(measurement.getType())) {
						statsRelevantMeasurementCount++;
						statsMeasurementsFound.put(measurement.type, statsMeasurementsFound.get(measurement.type) + 1);
						alreadyCounted = true;
						
						if (!alreadyRemoved) {
							boolean parseError = false;
							double value = 0;
							switch (measurementKeep.get(measurement.getType())) {
							case KEEPFIRST:
								if (!first.containsKey(measurement.getType())) {
									first.put(measurement.getType(), measurement);
								}
								else {
									measurementsToRemove.add(measurement);
									if (intermediateFiles) 
										reasonRemoved.put(measurement, "Not First");
									alreadyRemoved = true;
								}
								break;
							case KEEPLAST:
								if (last.containsKey(measurement.getType())) {
									if (intermediateFiles) 
										reasonRemoved.put(last.get(measurement.getType()), "Not Last");
									measurementsToRemove.add(last.get(measurement.getType()));
									last.put(measurement.getType(), measurement);
								}
								else {
									last.put(measurement.getType(), measurement);
								}
								break;
							case KEEPHIGHEST:
								try {
									value = Double.parseDouble(measurement.getValue());
								}
								catch (NumberFormatException e) {
									parseError = true;
								}
								if (parseError || measurement.getValue().equals("NaN")) {
									measurementsToRemove.add(measurement);
									if (intermediateFiles) 
										reasonRemoved.put(measurement, "Not Highest Error");
									alreadyRemoved = true;
								}
								else {
									if (!highest.containsKey(measurement.getType())) {
										highest.put(measurement.getType(), measurement);
									}
									else {
										double lastValue = Double.parseDouble(highest.get(measurement.getType()).getValue());
										if (value > lastValue) {
											measurementsToRemove.add(highest.get(measurement.getType()));
											if (intermediateFiles) 
												reasonRemoved.put(highest.get(measurement.getType()), "Not Highest");
											highest.put(measurement.getType(), measurement);
										}
										else {
											measurementsToRemove.add(measurement);
											if (intermediateFiles) 
												reasonRemoved.put(measurement, "Not Highest");
											alreadyRemoved = true;
										}
									}
								}
								break;
							case KEEPLOWEST:
								try {
									value = Double.parseDouble(measurement.getValue());
								}
								catch (NumberFormatException e) {
									parseError = true;
								}
								if (parseError || measurement.getValue().equals("NaN")) {
									measurementsToRemove.add(measurement);
									if (intermediateFiles) 
										reasonRemoved.put(measurement, "Not Lowest Error");
									alreadyRemoved = true;
								}
								else {
									if (!lowest.containsKey(measurement.getType())) {
										lowest.put(measurement.getType(), measurement);
									}
									else {
										double lastValue = Double.parseDouble(lowest.get(measurement.getType()).getValue());
										if (value < lastValue) {
											measurementsToRemove.add(lowest.get(measurement.getType()));
											if (intermediateFiles) 
												reasonRemoved.put(lowest.get(measurement.getType()), "Not Lowest");
											lowest.put(measurement.getType(), measurement);
										}
										else {
											measurementsToRemove.add(measurement);
											if (intermediateFiles) 
												reasonRemoved.put(measurement, "Not Lowest");
											alreadyRemoved = true;
										}
									}
								}
								break;
							case KEEPPRIORITY:
								if (keep.containsKey(measurement.getType())) {
									List<String> values = measurementValues.get(measurement.getType());
									if (values.size() > 0) {
										if (values.indexOf(measurement.getValue()) <= values.indexOf(keep.get(measurement.getType()).getValue())) {
											measurementsToRemove.add(keep.get(measurement.getType()));
											if (intermediateFiles) 
												reasonRemoved.put(keep.get(measurement.getType()), "Duplicate");
											keep.put(measurement.getType(), measurement);
										}
										else {
											measurementsToRemove.add(measurement);
											if (intermediateFiles) 
												reasonRemoved.put(measurement, "Duplicate");
											alreadyRemoved = true;
										}
									}
									else {
										measurementsToRemove.add(measurement);
										if (intermediateFiles) 
											reasonRemoved.put(measurement, "Duplicate");
										alreadyRemoved = true;
									}
								}
								else {
									keep.put(measurement.getType(), measurement);
								}
								break;
							default: // KEEPALL
								break;
							}
						}
						else {
							break;
						}
					}
				}
			}
			lastDate = measurement.getDate();
			
			// Outlier counting
			if (!alreadyCounted && outlierIntervals.get(measurement.type) != null) {
					statsMeasurementsFound.put(measurement.type, statsMeasurementsFound.get(measurement.type) + 1);
					statsRelevantMeasurementCount++;
					alreadyCounted = true;
			}
		
			// Output if necessary
			if (intermediateFiles) {
				DelimitedStringBuilder record = new DelimitedStringBuilder();
				record.append(measurement.patientID);
				record.append(DateUtilities.daysToDate(measurement.getDate()));
				record.append(measurement.getType());
				record.append(measurement.getValue());
				record.append(measurementsToRemove.contains(measurement) ? "REMOVED" : "KEPT");
				Jerboa.getOutputManager().writeln(this.processLogFile, record.toString(), true);
			}
			
			//reset flags
			alreadyCounted = false;
			alreadyRemoved = false;
		}
		 
		if (patientWithMeasurements) {
			statsPatientsWithMeasurements++;
		}

		//remove irrelevant measurements
		patient.getMeasurements().removeAll(measurementsToRemove);
		
		//output removed measurements
		if (intermediateFiles) {
			for (Measurement remove : measurementsToRemove) {
				DelimitedStringBuilder record = new DelimitedStringBuilder();
				record.append(remove.patientID);
				record.append(DateUtilities.daysToDate(remove.getDate()));
				record.append(remove.getType());
				record.append(remove.getValue());
				record.append(reasonRemoved.get(remove));
				Jerboa.getOutputManager().writeln(this.removedMeasurementsFile, record.toString(), true);
			}
		}
		
		if (Jerboa.unitTest) {
			unitTestRemoved.addAll(measurementsToRemove);
		}
		
		return patient;
	}
	
	@Override
	public void outputResults() {
		// Close intermediate files
		if (intermediateFiles) {
			Jerboa.getOutputManager().closeFile(removedMeasurementsFile);
			Jerboa.getOutputManager().closeFile(processLogFile);
		}
		
		Logging.addNewLine();
		Logging.add("Measurement cleaner results: ");
		Logging.add("------------------------------------");
		// Write stats to log
		int statsTotalRemoved = 0;
		Logging.add("Total number of patients: " + Integer.toString(statsPatientCount));
		Logging.add("Patients with measurements: " + Integer.toString(statsPatientsWithMeasurements));
		Logging.add("Total measurements found: " + Integer.toString(statsMeasurementCount));
		Logging.add("Relevant measurements found: " + Integer.toString(statsRelevantMeasurementCount));
		
		Logging.add("-------------");
		for (int measurementType : statsMeasurementsRemoved.keySet()) {
			Logging.add(InputFileUtilities.getMeasurementTypes().get(measurementType).toString() + " measurements found: " + 
					(statsMeasurementsFound.get(measurementType) == null ? "" : Integer.toString(statsMeasurementsFound.get(measurementType))) + ", removed: " +
					(statsMeasurementsRemoved.get(measurementType) == null ? "" : Integer.toString(statsMeasurementsRemoved.get(measurementType))));
			statsTotalRemoved += (statsMeasurementsRemoved.get(measurementType) == null ? 0 : statsMeasurementsRemoved.get(measurementType));
		}
		
		Logging.add("-------------");
		Logging.add("Total measurements removed: " + Integer.toString(statsTotalRemoved));
		if (removeNonNumeric.size() > 0)
			Logging.add("Total measurements with non numeric value removed: "+ statsMeasurementsNonNumericValueCount);
	}
	
	@Override
	public void setNeededFiles() {
		setRequiredFile(DataDefinition.PATIENTS_FILE);
		if (measurements.size() > 0)
			setRequiredFile(DataDefinition.MEASUREMENTS_FILE);
	}

	@Override
	public void setNeededExtendedColumns() {/* NO EXTENDED COLUMNS NEEDED */}

	@Override
	public void setNeededNumericColumns() {
		// TODO Auto-generated method stub
		
	}
	
	/**
	 * Getter for unitTestRemoved
	 */
	public List<Measurement> getUnitTestRemoved() {
		return unitTestRemoved;
	}
	
	/**
	 * Utility class for the outlier definition.
	 * 
	 * @author MG
	 *
	 */
	static class Interval {
		
		public double min = Double.MAX_VALUE;
		public double max = Double.MIN_VALUE;
		
		//CONSTRUCTORS
		public Interval(){}
		
		public Interval(double min, double max) {
			this.min = min;
			this.max = max;
		}
		
		/**
		 * Returns true if value is outside the interval (including borders).
		 * @param value - the value to be checked 
		 * @return true if the value represents an outlier of the interval; false otherwise
		 */
		public boolean isOutsideInterval(double value) {
			return value < min || value > max;
		}
		
		/**
		 * Returns true if value is outside the interval (including borders).
		 * @param value - the value to be checked 
		 * @return true if the value represents an outlier of the interval; false otherwise
		 */
		public boolean isOutsideInterval(String value) {
			try{
				return isOutsideInterval(Double.parseDouble(value));
			}
			catch(NumberFormatException e) {
				Logging.add("Unable to parse measurement value "+value, Logging.ERROR);
				return true;
			}
		}
	}

}
