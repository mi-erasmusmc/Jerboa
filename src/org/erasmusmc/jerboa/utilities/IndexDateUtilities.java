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

package org.erasmusmc.jerboa.utilities;

import java.security.InvalidParameterException;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.commons.collections.bag.HashBag;
import org.apache.commons.lang3.StringUtils;
import org.erasmusmc.jerboa.Jerboa;
import org.erasmusmc.jerboa.dataClasses.Event;
import org.erasmusmc.jerboa.dataClasses.Measurement;
import org.erasmusmc.jerboa.dataClasses.Patient;
import org.erasmusmc.jerboa.dataClasses.Prescription;

/**
 * Class use to find measurements, combormidities, prescriptions in windows around the index date
 * by default all boundaries are included in windows
 * @author Rijnbeek
 *
 */
public class IndexDateUtilities {
	DecimalFormat precisionFormat = StringUtilities.DECIMAL_FORMAT_FORCE_PRECISION;
	
	/**
	 * Determines if the patient has comorbidities in the user specified
	 * time window. Updates the comorbiditiesBag
	 * @param Events - all events of the patient
	 * @param indexDate - index date
	 * @return comma-separated string containing yes or no for each comborbidity
	 */
	public static String processComorbidities(Patient patient, int indexDate, ItemList comorbiditiesList, int timeWindowComorbidity, HashBag comorbiditiesBag){
		int windowStart;
		int windowEnd;
		Set<String> foundEvents = new HashSet<String>(); //only count the events once
		for (Item comorbidity : comorbiditiesList) {
			Jerboa.getResultSet().add(patient.getPatientID(), comorbidity.getDescription(), "0");
		}
		for (Event event : patient.getEvents()) {
			for (Item comorbidity : comorbiditiesList) {

				List<String> parameters = new ArrayList<String>(comorbidity.getParameters());
				
				//set default
				windowStart = indexDate+timeWindowComorbidity;
				windowEnd = indexDate;
				
				//parse windowStart and throw if needed
				try {

					if (parameters.size()>0 && !parameters.get(0).toLowerCase().equals("birth")) 
						windowStart = indexDate+Integer.valueOf(parameters.get(0));

					if (parameters.size()>0 && parameters.get(0).toLowerCase().equals("birth"))
						windowStart = patient.birthDate;
				}
				catch(NumberFormatException e)
				{
					throw new InvalidParameterException("Please check the script. comorbidity [" + comorbidity.getLookup() + "] should have a numeric window start");
				}

				//parse windowEnd and throw if needed
				try {
					if (parameters.size()>1) {
						if (parameters.get(1).toLowerCase().equals("cohortend"))
							windowEnd = patient.getCohortEndDate();
						else
							windowEnd = Integer.valueOf(parameters.get(1))+indexDate;
					}	
				}
				catch(NumberFormatException e)
				{
					throw new InvalidParameterException("Please check the script. comorbidity [" + comorbidity.getLookup() + "] should have a numeric window end or cohortend");
				}

				if (parameters.size()>2) {
					throw new InvalidParameterException("Please check the script. comorbidity [" + comorbidity.getLookup() + "] should have max four fields");
				}

				for (String lookup:comorbidity.getLookup()){
					if (StringUtils.upperCase(event.getType()).equals(StringUtils.upperCase(lookup)) && event.isInPeriod(windowStart,windowEnd,true,false)) {
						foundEvents.add(comorbidity.getLabel());
					}
				}					
			}
		}	
		// add events to bags

		for (String event: foundEvents){
			comorbiditiesBag.add(event);
			Jerboa.getResultSet().add(patient.getPatientID(), event.toString(), "1");
		}	
		DelimitedStringBuilder result = new DelimitedStringBuilder(",");
		for (Item comorbidity : comorbiditiesList) {
			if (foundEvents.contains(comorbidity.getLabel())){
				result.append("1");
			} else
				result.append("0");
		}
		return result.toString();
	}

	/**
	 * Determines the number of days to a next event
	 * The first parameter in the item list can define if this event
	 * is in cohort or not. If this is not set the dedault is true
	 * @param patient		- patient Object
	 * @param indexDate		- reference date
	 * @param itemList	 	- list of items to check
	 * @return comma-separated string containing time per item
	 */
	public static String processDaysToNextEvent(Patient patient, int indexDate, ItemList itemList){
		DelimitedStringBuilder result = new DelimitedStringBuilder(",");
	
		for (Item item: itemList){
			//defaults
			boolean inCohort = false;
			boolean includeIndexDate = false;
			if (item.getParameters().size()>0 && item.getParameters().get(0).toUpperCase().equals("TRUE"))
				inCohort = true;
			if (item.getParameters().size()>1 && item.getParameters().get(1).toUpperCase().equals("TRUE"))
				includeIndexDate = true;			
			int daysToNextEvent = IndexDateUtilities.getTimeToNextEvent(patient, indexDate, item.getLookup(), inCohort, includeIndexDate);
			if (daysToNextEvent>-1)
				result.append(String.valueOf(daysToNextEvent));
			else	
				result.append(" ");
		}
		return result.toString();
	}
	
	/**
	 * Determines the number of days to a previous event
	 * @param patient		- patient Object
	 * @param indexDate		- reference date
	 * @param itemList	 	- list of items to check
	 * @return comma-separated string containing the time per item
	 */
	public static String processDaysToPreviousEvent(Patient patient, int indexDate, ItemList itemList){
		DelimitedStringBuilder result = new DelimitedStringBuilder(",");
	
		for (Item item: itemList){
			//defaults
			boolean inCohort = false;
			boolean includeIndexDate = false;
			if (item.getParameters().size()>0 && item.getParameters().get(0).toUpperCase().equals("TRUE"))
				inCohort = true;
			if (item.getParameters().size()>1 && item.getParameters().get(1).toUpperCase().equals("TRUE"))
				includeIndexDate = true;
			int daysToPrevious = IndexDateUtilities.getTimeToPreviousEvent(patient, indexDate, item.getLookup(), inCohort, includeIndexDate);
			if (daysToPrevious>-1)
				result.append(String.valueOf(daysToPrevious));
			else	
				result.append(" ");
		}
		return result.toString();
	}
	
	/**
	 * Determines the number of days to a previous prescription
	 * @param patient		- patient Object
	 * @param indexDate		- reference date
	 * @param itemList	 	- list of items to check
	 * @return comma-separated string containing the time per item
	 */
	public static String processDaysToPreviousPrescription(Patient patient, int indexDate, ItemList itemList){
		DelimitedStringBuilder result = new DelimitedStringBuilder(",");
	
		for (Item item: itemList){
			//defaults
			boolean inCohort = false;
			boolean includeIndexDate = false;
			int windowStart = patient.birthDate;
			if (item.getParameters().size()>0 && item.getParameters().get(0).toUpperCase().equals("TRUE"))
				inCohort = true;
			if (item.getParameters().size()>1 && item.getParameters().get(1).toUpperCase().equals("TRUE"))
				includeIndexDate = true;
			if (item.getParameters().size()>2)
				try {
					if (item.getParameters().size()>2 && !item.getParameters().get(2).toLowerCase().equals("birth")) 
						windowStart = indexDate+Integer.valueOf(item.getParameters().get(2));

					if (item.getParameters().size()>2 && item.getParameters().get(2).toLowerCase().equals("birth"))
						windowStart = patient.birthDate;
				}
				catch(NumberFormatException e)
				{
					throw new InvalidParameterException("Please check the script. Prescription [" +item.getLookup() + "] should have a numeric window start");
				}
			int daysToPrevious = IndexDateUtilities.getTimeSincePreviousPrescription(patient, indexDate, item.getLookup(), inCohort, includeIndexDate, windowStart);
			if (daysToPrevious>-1)
				result.append(String.valueOf(daysToPrevious));
			else	
				result.append(" ");
		}
		return result.toString();
	}
	
	/**
	 * Determines the number of days to a previous prescription
	 * @param patient		- patient Object
	 * @param indexDate		- reference date
	 * @param itemList	 	- list of items to check
	 * @return comma-separated string containing the time per item
	 */
	public static String processDaysToNextPrescription(Patient patient, int indexDate, ItemList itemList){
		DelimitedStringBuilder result = new DelimitedStringBuilder(",");
	
		for (Item item: itemList){
			//defaults
			boolean inCohort = false;
			boolean includeIndexDate = false;
			int windowEnd = patient.birthDate;
			if (item.getParameters().size()>0 && item.getParameters().get(0).toUpperCase().equals("TRUE"))
				inCohort = true;
			if (item.getParameters().size()>1 && item.getParameters().get(1).toUpperCase().equals("TRUE"))
				includeIndexDate = true;
			if (item.getParameters().size()>2)
				try {
					if (item.getParameters().size()>2 && !item.getParameters().get(2).toLowerCase().equals("cohortend")) 
						windowEnd = indexDate+Integer.valueOf(item.getParameters().get(2));

					if (item.getParameters().size()>2 && item.getParameters().get(2).toLowerCase().equals("cohortend"))
						windowEnd = patient.getCohortEndDate();
				}
				catch(NumberFormatException e)
				{
					throw new InvalidParameterException("Please check the script. Prescription [" +item.getLookup() + "] should have a numeric window end");
				}
			int daysToPrevious = IndexDateUtilities.getTimeToNextPrescription(patient, indexDate, item.getLookup(), inCohort, includeIndexDate, windowEnd);
			if (daysToPrevious>-1)
				result.append(String.valueOf(daysToPrevious));
			else	
				result.append(" ");
		}
		return result.toString();
	}
	
	/**
	 * Determines the number of days to a next event
	 * @param patient		- patient Object
	 * @param indexDate		- reference date
	 * @param itemList	 	- list of items to check
	 * @return comma-separated string containing time per item
	 */
	public static String processDaysToNextMeasurement(Patient patient, int indexDate, ItemList itemList){
		DelimitedStringBuilder result = new DelimitedStringBuilder(",");
	
		for (Item item: itemList){
			
			//defaults
			boolean inCohort = false;
			boolean includeIndexDate = false;
			if (item.getParameters().size()>0 && item.getParameters().get(0).toUpperCase().equals("TRUE"))
				inCohort = true;
			if (item.getParameters().size()>1 && item.getParameters().get(1).toUpperCase().equals("TRUE"))
				includeIndexDate = true;
			int daysToNext = IndexDateUtilities.getTimeToNextMeasurement(patient, indexDate, item, inCohort, includeIndexDate);
			if (daysToNext>-1)
				result.append(String.valueOf(daysToNext));
			else	
				result.append(" ");
		}
		return result.toString();
	}
	
	/**
	 * Determines the number of days to a previous event
	 * @param patient		- patient Object
	 * @param indexDate		- reference date
	 * @param itemList	 	- list of items to check
	 * @return comma-separated string containing the time per item
	 */
	public static String processDaysToPreviousMeasurement(Patient patient, int indexDate, ItemList itemList){
		DelimitedStringBuilder result = new DelimitedStringBuilder(",");
	
		for (Item item: itemList){
			//defaults
			boolean inCohort = false;
			boolean includeIndexDate = false;
			if (item.getParameters().size()>0 && item.getParameters().get(0).toUpperCase().equals("TRUE"))
				inCohort = true;
			if (item.getParameters().size()>1 && item.getParameters().get(1).toUpperCase().equals("TRUE"))
				includeIndexDate = true;			
			int daysToPrevious = IndexDateUtilities.getTimeToPreviousMeasurement(patient, indexDate, item, inCohort, includeIndexDate);
			if (daysToPrevious>-1)
				result.append(String.valueOf(daysToPrevious));
			else	
				result.append(" ");
		}
		return result.toString();
	}
	

	/**
	 * Determines if the patient has prescriptions in the user specified
	 * time window. Updates the prescriptionBag
	 * @param patient					- patient Object
	 * @param indexDate					- reference date
	 * @param prescriptionsList			- list of items to check
	 * @param timeWindowPrescriptions	- default look back period (should be negative)
	 * @param prescriptionsBag			- bag to contain total counts
	 * @return
	 */
	public static String processPrescriptions(Patient patient, int indexDate, ItemList prescriptionsList, int timeWindowPrescriptions, HashBag prescriptionsBag){
		int windowStart;
		int windowEnd;
		Set<String> foundPrescriptions = new HashSet<String>(); //only count the prescriptions once
		for (Item item : prescriptionsList) {
			Jerboa.getResultSet().add(patient.getPatientID(), item.getDescription(), "0");
		}
		for (Prescription prescription : patient.getPrescriptions()) {
			for (Item item : prescriptionsList) {

				List<String> parameters = new ArrayList<String>(item.getParameters());
				windowStart = indexDate+timeWindowPrescriptions;
				windowEnd = indexDate; 
				
				//parse windowStart and throw if needed
				try {

					if (parameters.size()>0 && !parameters.get(0).toLowerCase().equals("birth")) 
						windowStart = indexDate+Integer.valueOf(parameters.get(0));

					if (parameters.size()>0 && parameters.get(0).toLowerCase().equals("birth"))
						windowStart = patient.birthDate;
				}
				catch(NumberFormatException e)
				{
					throw new InvalidParameterException("Please check the script. prescription [" + item.getLookup() + "] should have a numeric window start");
				}

				try {
					if (parameters.size()>1) {
						if (parameters.get(1).toLowerCase().equals("cohortend"))
							windowEnd = patient.getCohortEndDate();
						else
							windowEnd = Integer.valueOf(parameters.get(1))+indexDate;
					}	
				}
				catch(NumberFormatException e)
				{
					throw new InvalidParameterException("Please check the script. prescription [" + item.getLookup() + "] should have a numeric window end or cohortend");
				}

				if (parameters.size()>2) {
					throw new InvalidParameterException("Please check the script. prescription [" + item.getLookup() + "] should have max four fields");
				}

				//windowStart is full history

				if (prescription.startsWith(item.getLookup()) && prescription.isInPeriod(windowStart,windowEnd,true,false)) {
					foundPrescriptions.add(item.getLabel());
				}

			}
		}	
		// add prescription to bags

		for (String prescription: foundPrescriptions){
			prescriptionsBag.add(prescription);
			Jerboa.getResultSet().add(patient.getPatientID(), prescription.toString(), "1");
		}	
		
		DelimitedStringBuilder result = new DelimitedStringBuilder(",");
		for (Item item : prescriptionsList) {
			if (foundPrescriptions.contains(item.getLabel())){
				result.append("1");
			} else
				result.append("0");
		}
		return result.toString();
	}
	
	
	/**
	 * Select the measurement closest to the index date in the user specified
	 * time window. Updates the measurementsBag. Measurements should be sorted by date in the
	 * patient object!
	 * @param patient - a patient object
	 * @throws IllegalArgumentException if patient measurements are not sorted
	 */
	public static String processMeasurements(Patient patient, int indexDate, ItemList measurementsList, int timeWindowMeasurement, MultiKeyBag measContinuesBag , MultiKeyBag measCategoryBag, boolean addUnitColumn) throws IllegalArgumentException {

		//use map to only retain the last measurement in the timeWindowMeasurement
		Map<String,String> selectedMeasurements = new HashMap<String,String>();
		Map<String,String> selectedUnits = new HashMap<String,String>();
		Map<String,Integer> closestMeasurements = new HashMap<String,Integer>();
		Map<String,Double> highestMeasurements = new HashMap<String,Double>();
		Map<String,Double> lowestMeasurements = new HashMap<String,Double>();
		Map<String,Integer> firstMeasurements = new HashMap<String,Integer>();
		Map<String,Integer> lastMeasurements = new HashMap<String,Integer>();
		Map<String,String> unknownValues = new HashMap<String,String>();

		//initialize map with unique set of measurements empty values
		for (Item measurementItem : measurementsList) {
			selectedMeasurements.put(measurementItem.getLabel(),"");
			selectedUnits.put(measurementItem.getLabel(),"");
			if (measurementItem.getParameters().size()>3) {
				unknownValues.put(measurementItem.getLabel(),measurementItem.getParameters().get(3));
			}
		}

		//populate the map with the last value of the measurement
		int date = 0;
		for (Measurement measurement : patient.getMeasurements()) {

			//check measurements are still sorted		
			if (measurement.date<date) {
				Logging.add("Descriptives:Measurements were not sorted by date!");
				throw new IllegalArgumentException("Measurements should be sorted by date!");
			} else
				date = measurement.date;
			// Check if measurement of interest
			int windowStart = indexDate + timeWindowMeasurement;
			int windowEnd = indexDate;
			
			for (Item measurementItem : measurementsList) {
				List<String> parameters = new ArrayList<String>(measurementItem.getParameters());
	
				windowStart = indexDate + timeWindowMeasurement;
				windowEnd = indexDate; 
				
				//parse windowStart and throw if needed
				try {

					if (parameters.size()>0 && !parameters.get(0).toLowerCase().equals("birth")) 
						windowStart = indexDate+Integer.valueOf(parameters.get(0));

					if (parameters.size()>0 && parameters.get(0).toLowerCase().equals("birth"))
						windowStart = patient.birthDate;
				}
				catch(NumberFormatException e)
				{
					throw new InvalidParameterException("Please check the script. measurement [" +measurementItem.getLookup() + "] with value should have a numeric window start");
				}

				try {
					if (parameters.size()>1) {
						if (parameters.get(1).toLowerCase().equals("cohortend"))
							windowEnd = patient.getCohortEndDate();
						else
							windowEnd = Integer.valueOf(parameters.get(1))+indexDate;
					}	
				}
				catch(NumberFormatException e)
				{
					throw new InvalidParameterException("Please check the script. measurement" + measurementItem.getLookup() + "] should have a numeric window end or cohortend");
				}

				// Closest, Highest, Lowest, First or Last defined
				
				String type = "CLOSEST";
				if (parameters.size()>2) {
					if (parameters.get(2).toLowerCase().equals("closest"))
						type = "CLOSEST";
					else
						if (parameters.get(2).toLowerCase().equals("highest"))
							type = "HIGHEST";		
						else
							if (parameters.get(2).toLowerCase().equals("lowest"))
								type = "LOWEST";		
							else
								if (parameters.get(2).toLowerCase().equals("first"))
									type = "FIRST";		
								else
									if (parameters.get(2).toLowerCase().equals("last"))
										type = "LAST";		
									else
									throw new InvalidParameterException("Please check the script. measurement" + measurementItem.getLookup() + "] should have as type Closest, Highest, Lowest, First, or Last");
				}
				if (StringUtils.upperCase(measurement.getType()).equals(StringUtils.upperCase(measurementItem.getLookup().get(0))) && 
						measurement.isInPeriod(windowStart,windowEnd,true,false)) {
					
					if (type.equals("CLOSEST")) {

						if (!closestMeasurements.containsKey(measurementItem.getLabel()) ||
								closestMeasurements.get(measurementItem.getLabel())>Math.abs(indexDate-measurement.date)){
							selectedMeasurements.put(measurementItem.getLabel(),measurement.getValue());
							if (measurement.hasUnit()) {
								selectedUnits.put(measurementItem.getLabel(),measurement.getUnit());
							}
							else {
								if (unknownValues.containsKey(measurementItem.getLabel()))
									selectedUnits.put(measurementItem.getLabel(),unknownValues.get(measurementItem.getLabel()));
								else
									selectedUnits.put(measurementItem.getLabel()," ");
							}
							closestMeasurements.put(measurementItem.getLabel(),Math.abs(indexDate-measurement.date));
							break;
						}
					}
					
					else if (type.equals("HIGHEST")) {
						try {
							if (!highestMeasurements.containsKey(measurementItem.getLabel()) ||
									highestMeasurements.get(measurementItem.getLabel())<Double.valueOf(measurement.getValue())){
								selectedMeasurements.put(measurementItem.getLabel(),measurement.getValue());
								if (measurement.hasUnit()) {
									selectedUnits.put(measurementItem.getLabel(),measurement.getUnit());
								}
								else {
									if (unknownValues.containsKey(measurementItem.getLabel()))
										selectedUnits.put(measurementItem.getLabel(),unknownValues.get(measurementItem.getLabel()));
									else
										selectedUnits.put(measurementItem.getLabel()," ");
								}
								highestMeasurements.put(measurementItem.getLabel(),Double.valueOf(measurement.getValue()));
								break;
							}
						}
						catch(NumberFormatException e)
						{
							throw new InvalidParameterException("Please check the script. measurement" + measurementItem.getLookup() + "] should have numeric values");
						}
					}
					
					else if (type.equals("LOWEST")) {
						try {
							if (!lowestMeasurements.containsKey(measurementItem.getLabel()) ||
									lowestMeasurements.get(measurementItem.getLabel())>Double.valueOf(measurement.getValue())){
								selectedMeasurements.put(measurementItem.getLabel(),measurement.getValue());
								if (measurement.hasUnit()) {
									selectedUnits.put(measurementItem.getLabel(),measurement.getUnit());
								}
								else {
									if (unknownValues.containsKey(measurementItem.getLabel()))
										selectedUnits.put(measurementItem.getLabel(),unknownValues.get(measurementItem.getLabel()));
									else
										selectedUnits.put(measurementItem.getLabel()," ");
								}
								lowestMeasurements.put(measurementItem.getLabel(),Double.valueOf(measurement.getValue()));
								break;
							}
						}
						catch(NumberFormatException e)
						{
							throw new InvalidParameterException("Please check the script. measurement" + measurementItem.getLookup() + "] should have numeric values");
						}
					}
					
					else if (type.equals("FIRST")) {

						if (!firstMeasurements.containsKey(measurementItem.getLabel()) ||
								firstMeasurements.get(measurementItem.getLabel())>measurement.date){
							selectedMeasurements.put(measurementItem.getLabel(),measurement.getValue());
							if (measurement.hasUnit()) {
								selectedUnits.put(measurementItem.getLabel(),measurement.getUnit());
							}
							else {
								if (unknownValues.containsKey(measurementItem.getLabel()))
									selectedUnits.put(measurementItem.getLabel(),unknownValues.get(measurementItem.getLabel()));
								else
									selectedUnits.put(measurementItem.getLabel()," ");
							}
							firstMeasurements.put(measurementItem.getLabel(),measurement.date);
							break;
						}
					}
					
					else if (type.equals("LAST")) {

						if (!lastMeasurements.containsKey(measurementItem.getLabel()) ||
								lastMeasurements.get(measurementItem.getLabel())<=measurement.date){
							selectedMeasurements.put(measurementItem.getLabel(),measurement.getValue());
							if (measurement.hasUnit()) {
								selectedUnits.put(measurementItem.getLabel(),measurement.getUnit());
							}
							else {
								if (unknownValues.containsKey(measurementItem.getLabel()))
									selectedUnits.put(measurementItem.getLabel(),unknownValues.get(measurementItem.getLabel()));
								else
									selectedUnits.put(measurementItem.getLabel()," ");
							}
							lastMeasurements.put(measurementItem.getLabel(),measurement.date);
							break;
						}
					}
					else {
						// Do nothing
					}
				}
			}
		}	
		
		DecimalFormat precisionFormat = StringUtilities.DECIMAL_FORMAT_FORCE_PRECISION;
		// add measurements to bags
		for (String key : selectedMeasurements.keySet()){

			//Add the measurement to the measurementBag or the unknownMeasurement Bag.
			if (!selectedMeasurements.get(key).isEmpty()){
				// find the corresponding item
				for (Item measurementItem : measurementsList) {
					if (measurementItem.getLabel().equals(key)){
						if (measurementItem.getValue().equals("CONTINUOUS")) {
							try {
								measContinuesBag.add(new ExtendedMultiKey(key,  precisionFormat.format(Double.valueOf(selectedMeasurements.get(key)))));
							} catch (NumberFormatException e) {
								Logging.add("Patient: "+patient.getPatientID()+" has a non numeric value (" + selectedMeasurements.get(key)+ " for measurement "+ key);
								throw new NumberFormatException("Patient: "+patient.getPatientID()+" has a non numeric value (" + selectedMeasurements.get(key)+ " for measurement "+ key);
							}
						}
						else {
							measCategoryBag.add(new ExtendedMultiKey(key, StringUtils.upperCase(selectedMeasurements.get(key))));
						}						

						break;
					}
				}

			} else {
				if (unknownValues.containsKey(key))
					measCategoryBag.add(new ExtendedMultiKey(key, unknownValues.get(key)));
				else
					measCategoryBag.add(new ExtendedMultiKey(key, "UNKNOWN"));
			}
		}
		
		//return comma-delimited set of results of found measurements
		DelimitedStringBuilder result = new DelimitedStringBuilder(",");
		String lastLabel = "";
		for (Item measurement : measurementsList) {
			if (measurement.getLabel()!=lastLabel) {
				if (!selectedMeasurements.get(measurement.getLabel()).isEmpty()){
					if (measurement.getValue().equals("CONTINUOUS")) {
					  result.append(precisionFormat.format(Double.valueOf(selectedMeasurements.get(measurement.getLabel()))));	
					} else
						result.append(selectedMeasurements.get(measurement.getLabel()));
				} else
					if (unknownValues.containsKey(measurement.getLabel()))
						result.append(unknownValues.get(measurement.getLabel()));
					else
						result.append(" ");
				lastLabel = measurement.getLabel();
				if (addUnitColumn) {
					if (!selectedUnits.get(measurement.getLabel()).isEmpty()){
						result.append(selectedUnits.get(measurement.getLabel()));
					} else
						if (unknownValues.containsKey(measurement.getLabel()))
							result.append(unknownValues.get(measurement.getLabel()));
						else
							result.append(" ");
				}
			}
		}
		return result.toString();
	}
	
	/**
	 * Count the number of measurements in the user specified time window that have
	 * a value within the specified value range. Updates the measurementsBag.
	 * Measurements should be sorted by date in the patient object!
	 * @param patient - a patient object
	 * @throws IllegalArgumentException if patient measurements are not sorted
	 */
	public static String processMeasurementCounts(Patient patient, int indexDate, ItemList measurementsList, int timeWindowMeasurement, MultiKeyBag measurementCountBag) throws IllegalArgumentException { 

		//populate the map with the last value of the measurement
		int date = 0;
		Double min = Double.MIN_VALUE;
		Double max = Double.MAX_VALUE;
		HashBag measurementFreq = new HashBag();
		
		for (Measurement measurement : patient.getMeasurements()) {

			//check measurements are still sorted		
			if (measurement.date<date) {
				Logging.add("Descriptives:Measurements were not sorted by date!");
				throw new IllegalArgumentException("Measurements should be sorted by date!");
			} else
				date = measurement.date;
			// Check if measurement of interest
			int windowStart = indexDate + timeWindowMeasurement;
			int windowEnd = indexDate;
			
			for (Item measurementItem : measurementsList) {
				List<String> parameters = new ArrayList<String>(measurementItem.getParameters());
				
				//parse min and max value
				try {
					if (parameters.size()>0) {
						min = Double.valueOf(parameters.get(0));
					}
				}
				catch(NumberFormatException e)
				{
					throw new InvalidParameterException("Please check the script. measurement min " + measurementItem.getLookup() + "] should have a numeric value");
				}
				
				try {
					if (parameters.size()>1) {
						max = Double.valueOf(parameters.get(1));
					}
				}
				catch(NumberFormatException e)
				{
					throw new InvalidParameterException("Please check the script. measurement max " + measurementItem.getLookup() + "] should have a numeric value");
				}
				
				//parse windowStart and throw if needed
				
				windowStart = indexDate + timeWindowMeasurement;
				windowEnd = indexDate; 
				try {

					if (parameters.size()>2 && !parameters.get(2).toLowerCase().equals("birth")) 
						windowStart = indexDate+Integer.valueOf(parameters.get(2));

					if (parameters.size()>2 && parameters.get(2).toLowerCase().equals("birth"))
						windowStart = patient.birthDate;
				}
				catch(NumberFormatException e)
				{
					throw new InvalidParameterException("Please check the script. measurement [" +measurementItem.getLookup() + "] should have a numeric window start");
				}

				try {
					if (parameters.size()>3) {
						if (parameters.get(3).toLowerCase().equals("cohortend"))
							windowEnd = patient.getCohortEndDate();
						else
							windowEnd = Integer.valueOf(parameters.get(3))+indexDate;
					}	
				}
				catch(NumberFormatException e)
				{
					throw new InvalidParameterException("Please check the script. measurement" + measurementItem.getLookup() + "] should have a numeric window end or cohortend");
				}
				

				if (StringUtils.upperCase(measurement.getType()).equals(StringUtils.upperCase(measurementItem.getLookup().get(0))) && 
						measurement.isInPeriod(windowStart,windowEnd,true,false) && 
						measurement.isInRange(min, max)) {
					measurementFreq.add(measurementItem.getLabel());
				}
			}
		}	
		
		//return comma-delimited set of results of found measurements
		DelimitedStringBuilder result = new DelimitedStringBuilder(",");
		String lastLabel = "";
		for (Item measurement : measurementsList) {
			if (measurement.getLabel()!=lastLabel) {
					int count = measurementFreq.getCount(measurement.getLabel());
					result.append(Integer.toString(count));
					measurementCountBag.add(new ExtendedMultiKey(measurement.getLabel(),count));
				lastLabel = measurement.getLabel();
			}
		}
		return result.toString();
	}
	
	/**
	 * Select the events closest to the index date in the user specified
	 * time window. Updates the eventsBag. Events should be sorted by date in the
	 * patient object!
	 * @param patient - a patient object
	 * @throws IllegalArgumentException if patient measurements are not sorted
	 */
	public static String processEvents(Patient patient, int indexDate, ItemList eventsList, int timeWindowEvent, MultiKeyBag eventContinuesBag, MultiKeyBag eventCategoryBag) throws IllegalArgumentException {

		//use map to only retain the last event in the timeWindowEvent
		Map<String,String> selectedEvents = new HashMap<String,String>();
		Map<String,Integer> closestEvents = new HashMap<String,Integer>();
		Map<String,String> unknownValues = new HashMap<String,String>();

		//initialize map with unique set of measurements empty values
		for (Item eventItem : eventsList) {
			selectedEvents.put(eventItem.getLabel(),"");
			if (eventItem.getParameters().size()>2) {
				unknownValues.put(eventItem.getLabel(),eventItem.getParameters().get(2));
			}
		}

		//populate the map with the last event
		int date = 0;
		for (Event event : patient.getEvents()) {

			//check events are still sorted		
			if (event.date<date) {
				Logging.add("Descriptives:Events were not sorted by date!");
				throw new IllegalArgumentException("Events should be sorted by date!");
			} else
				date = event.date;
			// Check if event of interest
			int windowStart = indexDate + timeWindowEvent;
			int windowEnd = indexDate;
			
			for (Item eventItem : eventsList) {
				List<String> parameters = new ArrayList<String>(eventItem.getParameters());
	
				windowStart = indexDate + timeWindowEvent;
				windowEnd = indexDate; 
				
				//parse windowStart and throw if needed
				try {

					if (parameters.size()>0 && !parameters.get(0).toLowerCase().equals("birth")) 
						windowStart = indexDate+Integer.valueOf(parameters.get(0));

					if (parameters.size()>0 && parameters.get(0).toLowerCase().equals("birth"))
						windowStart = patient.birthDate;
				}
				catch(NumberFormatException e)
				{
					throw new InvalidParameterException("Please check the script. event [" +eventItem.getLookup() + "] should have a numeric window start");
				}

				try {
					if (parameters.size()>1) {
						if (parameters.get(1).toLowerCase().equals("cohortend"))
							windowEnd = patient.getCohortEndDate();
						else
							windowEnd = Integer.valueOf(parameters.get(1))+indexDate;
					}	
				}
				catch(NumberFormatException e)
				{
					throw new InvalidParameterException("Please check the script. event" + eventItem.getLookup() + "] should have a numeric window end or cohortend");
				}

				if (StringUtils.upperCase(event.getType()).equals(StringUtils.upperCase(eventItem.getLookup().get(0))) && 
						event.isInPeriod(windowStart,windowEnd)) {
					if (!closestEvents.containsKey(eventItem.getLabel()) ||
						closestEvents.get(eventItem.getLabel())>Math.abs(indexDate-event.date)){
						selectedEvents.put(eventItem.getLabel(),"1");
						closestEvents.put(eventItem.getLabel(),Math.abs(indexDate-event.date));
						break;
					}
				}
			}
		}	

		// add events to bags
		for (String key : selectedEvents.keySet()){

			//Add the event to the eventBag or the unknownEvent Bag.
			if (!selectedEvents.get(key).isEmpty()){
				// find the corresponding item
				for (Item eventItem : eventsList) {
					//TODO: I think this will never happen for an event. Check
					if (eventItem.getLabel().equals(key)){
						if (eventItem.getValue().equals("CONTINUOUS")) {
							try {
								eventContinuesBag.add(new ExtendedMultiKey(key,  Double.valueOf(selectedEvents.get(key))));
							} catch (NumberFormatException e) {
								Logging.add("Patient: "+patient.getPatientID()+" has a non numeric value for event "+ key);
								throw new NumberFormatException("Patient: "+patient.getPatientID()+" has a non numeric value for event "+ key);
							}
						}
						else {
							eventCategoryBag.add(new ExtendedMultiKey(key, StringUtils.upperCase(selectedEvents.get(key))));
						}						

						break;
					}
				}

			} else {
				if (unknownValues.containsKey(key))
					eventCategoryBag.add(new ExtendedMultiKey(key, unknownValues.get(key)));
				else
					eventCategoryBag.add(new ExtendedMultiKey(key, "UNKNOWN"));
			}
		}
		
		//return comma-delimited set of results of found events
		DelimitedStringBuilder result = new DelimitedStringBuilder(",");
		String lastLabel = "";
		for (Item event : eventsList) {
			if (event.getLabel()!=lastLabel) {
				if (!selectedEvents.get(event.getLabel()).isEmpty()){
					result.append(selectedEvents.get(event.getLabel()));
				} else
					if (unknownValues.containsKey(event.getLabel()))
						result.append(unknownValues.get(event.getLabel()));
					else
						result.append("UNKNOWN");
				lastLabel = event.getLabel();
			}
		}
		return result.toString();
	}
	
	/**
	 * Counts the comorbidity (event) occurrence in a time window defined in the parameters of the item
	 * and only includes events that are in a certain age range in years (boundaries include)
	 * @param patient						- patient object
	 * @param indexDate						- the reference date
	 * @param eventsList					- item list with events of interest
	 * @param timeWindowEvent				- default time window if not defined
	 * @param eventCountBag					- bag with final counts
	 * @return
	 * @throws IllegalArgumentException
	 * 
	 * For example: "MI;NMI;Myocardial Infarction"				  	: Look for MI in the window  indexDate-timeWindowComoorbidities< X <=indexDate
	 * 				"MI;NMI;Myocardial Infarction;50;999;-365" 	  	: Look for MI in the window  indexDate-365< X <=indexDate if age >= 50
	 * 				"MI;NMI;Myocardial Infarction;;;-365" 		  	: Look for MI in the window  indexDate-365< X <=indexDate
	 * 				"MI;NMI;Myocardial Infarction;;;-365;10" 		: Look for MI in the window  indexDate-365< X <indexDate + 10
	 * 				"MI;NMI;Myocardial Infarction;;;-365;cohortEnd" : Look for MI in the window  indexDate-365< X <cohortEnd
	 * 
	 */
	public static String processEventCounts(Patient patient, int indexDate, ItemList eventsList, int timeWindowEvent, MultiKeyBag eventCountBag) throws IllegalArgumentException { 

		//populate the map with the last value of the measurement
		int date = 0;
		Double minAge = Double.MIN_VALUE;
		Double maxAge = Double.MAX_VALUE;
		HashBag eventFreq = new HashBag();
		
		for (Event event : patient.getEvents()) {

			//check measurements are still sorted		
			if (event.date<date) {
				Logging.add("Descriptives:Events were not sorted by date!");
				throw new IllegalArgumentException("Events should be sorted by date!");
			} else
				date = event.date;
			// Check if measurement of interest
			int windowStart = indexDate + timeWindowEvent;
			int windowEnd = indexDate;
			
			for (Item eventItem : eventsList) {
				List<String> parameters = new ArrayList<String>(eventItem.getParameters());
				
				//parse min and max age value
				try {
					if (parameters.size()>0) {
						minAge = parameters.get(0).trim().equals("") ? 0 : Double.valueOf(parameters.get(0));
					}
				}
				catch(NumberFormatException e)
				{
					throw new InvalidParameterException("Please check the script. minAge [" + eventItem.getLookup() + "] should have a numeric value");
				}
				
				try {
					if (parameters.size()>1) {
						maxAge = parameters.get(1).trim().equals("") ? 999 : Double.valueOf(parameters.get(1));
					}
				}
				catch(NumberFormatException e)
				{
					throw new InvalidParameterException("Please check the script. maxAge [" + eventItem.getLookup() + "] should have a numeric value");
				}
				
				//parse windowStart and throw if needed
				
				windowStart = indexDate + timeWindowEvent;
				windowEnd = indexDate; 
				try {

					if (parameters.size()>2 && !parameters.get(2).toLowerCase().equals("birth")) 
						windowStart = indexDate+Integer.valueOf(parameters.get(2));

					if (parameters.size()>2 && parameters.get(2).toLowerCase().equals("birth"))
						windowStart = patient.birthDate;
				}
				catch(NumberFormatException e)
				{
					throw new InvalidParameterException("Please check the script. measurement [" +eventItem.getLookup() + "] should have a numeric window start");
				}

				try {
					if (parameters.size()>3) {
						if (parameters.get(3).toLowerCase().equals("cohortend"))
							windowEnd = patient.getCohortEndDate();
						else
							windowEnd = Integer.valueOf(parameters.get(3))+indexDate;
					}	
				}
				catch(NumberFormatException e)
				{
					throw new InvalidParameterException("Please check the script. measurement" + eventItem.getLookup() + "] should have a numeric window end or cohortend");
				}
				

				if (StringUtils.upperCase(event.getType()).equals(StringUtils.upperCase(eventItem.getLookup().get(0))) && 
						event.isInPeriod(windowStart,windowEnd) && 
						(patient.getAge(event.date) >= minAge) && (patient.getAge(event.date) <= maxAge)) {
					eventFreq.add(eventItem.getLabel());
				}
			}
		}	
		
		//return comma-delimited set of results of found events
		DelimitedStringBuilder result = new DelimitedStringBuilder(",");
		String lastLabel = "";
		for (Item event : eventsList) {
			if (event.getLabel()!=lastLabel) {
					int count = eventFreq.getCount(event.getLabel());
					result.append(Integer.toString(count));
					eventCountBag.add(new ExtendedMultiKey(event.getLabel(),count));
				lastLabel = event.getLabel();
			}
		}
		return result.toString();
	}

	/**
	 * Counts the number of distinct ATC codes in a time window from a list or all possible types
	 * @param patient						- patient object
	 * @param indexDate						- the reference date
	 * @param prescriptionList				- item list with events of interest
	 * @param timeWindowEvent				- default time window if not defined
	 * @param prescritionCountBag			- bag with final counts
	 * @return
	 * @throws IllegalArgumentException
	 * 
	 * For example: "ATC1, ATC2;SET1"				  		  : Count prescriptions in the window  indexDate-timeWindowComoorbidities< X <=indexDate
	 * 				"ATC1, ATC2;SET1;-365" 		  			  : Count prescriptions in the window  indexDate-365< X <=indexDate
	 * 				"ATC1, ATC2;SET1;-365;10"  		  		  : Count prescriptions in the window  indexDate-365< X <indexDate + 10
	 * 				"ATC1, ATC2;SET1;-365;cohortEnd"		  : Count prescriptions in the window  indexDate-365< X <cohortEnd
	 * 				"ATC1, ATC2;SET1;birth"		  			  : Count prescriptions in the window  birth < X <= indexDate
	 * 
	 * If instead of ATC codes "*" is added all atc codes found are counted
	 * 
	 */
	public static String processPrescriptionCounts(Patient patient, int indexDate, ItemList prescriptionList, int timeWindowPrescriptionCount, MultiKeyBag prescriptionCountBag) throws IllegalArgumentException { 

		//populate the map with the last value of the prescription
		int date = 0;
		MultiKeyBag atc7Freq = new MultiKeyBag(); 
		
		for (Prescription prescription : patient.getPrescriptions()) {

			//check prescriptions are still sorted		
			if (prescription.date<date) {
				Logging.add("Descriptives:Prescription were not sorted by date!");
				throw new IllegalArgumentException("Prescriptions should be sorted by date!");
			} else
				date = prescription.date;
			// Check if prescription of interest
			int windowStart = indexDate + timeWindowPrescriptionCount;
			int windowEnd = indexDate;
			
			for (Item prescritionItem : prescriptionList) {
				List<String> parameters = new ArrayList<String>(prescritionItem.getParameters());
				

				
				//parse windowStart and throw if needed
				
				windowStart = indexDate + timeWindowPrescriptionCount;
				windowEnd = indexDate; 
				try {

					if (parameters.size()>1 && !parameters.get(0).toLowerCase().equals("birth")) 
						windowStart = indexDate+Integer.valueOf(parameters.get(0));

					if (parameters.size()>1 && parameters.get(0).toLowerCase().equals("birth"))
						windowStart = patient.birthDate;
				}
				catch(NumberFormatException e)
				{
					throw new InvalidParameterException("Please check the script. prescripton count [" +prescritionItem.getLookup() + "] should have a numeric window start");
				}

				try {
					if (parameters.size()>2) {
						if (parameters.get(1).toLowerCase().equals("cohortend"))
							windowEnd = patient.getCohortEndDate();
						else
							windowEnd = Integer.valueOf(parameters.get(1))+indexDate;
					}	
				}
				catch(NumberFormatException e)
				{
					throw new InvalidParameterException("Please check the script. prescripton count" + prescritionItem.getLookup() + "] should have a numeric window end or cohortend");
				}
				

				if ((prescritionItem.getLookup().contains("*") || prescription.startsWith(prescritionItem.getLookup())) && 
						prescription.isInPeriod(windowStart,windowEnd) && !prescription.startsWith("_")){
					// only count distinct atc code (using bag as map)
					ExtendedMultiKey key = new ExtendedMultiKey(prescritionItem.getLabel(), prescription.getATC());
					if (!atc7Freq.contains(key))
						atc7Freq.add(key);
				}
			}
		}	
		
		
		//return comma-delimited set of results of found events
		DelimitedStringBuilder result = new DelimitedStringBuilder(",");
		String lastLabel = "";
		for (Item prescription : prescriptionList) {
			if (prescription.getLabel()!=lastLabel) {
					long count = atc7Freq.getSize();
					result.append(Long.toString(count));
					prescriptionCountBag.add(new ExtendedMultiKey(prescription.getLabel(),count));
				lastLabel = prescription.getLabel();
			} 
		}
		return result.toString();
	}	
	
	/**
	 * returns the first occurrence of any of the events in the list
	 * -1 if note found
	 * @param patient
	 * @param eventLabels
	 * @return date
	 */
	public static int getFirstEventDate(Patient patient, List<String> eventLabels, boolean inCohort){
		int date = -1;
		for (Event event : patient.getEvents()){
			if (event.inList(eventLabels) && (inCohort ? patient.dateInCohort(event.getDate(),true): true)){
				date = event.date;
				break;
			}
		}
		return date;
	}
	
	/**
	 * returns the first occurrence of this event. -1 if not found
	 * @param patient
	 * @param eventLabel
	 * @return date
	 */
	public static int getFirstEventDate(Patient patient, String eventLabel, boolean inCohort){
		int date = -1;
		for (Event event : patient.getEvents()){
			if (event.getType().equals(eventLabel)  && (inCohort ? patient.dateInCohort(event.getDate(),true): true)){
				date = event.date;
				break;
			}
		}
		return date;
	}
	
	/**
	 * returns the first occurrence of any of the events in the list
	 * -1 if note found
	 * @param patient
	 * @param eventLabels
	 * @return date
	 */
	public static String getFirstEventType(Patient patient, List<String> eventLabels, boolean inCohort){
		String eventStr = "";
		for (Event event : patient.getEvents()){
			if (event.inList(eventLabels) && (inCohort ? patient.dateInCohort(event.getDate(),true): true)){
				eventStr = event.getType();
				break;
			}
		}
		return eventStr;
	}
	
	/**
	 * returns the first occurrence of this event. -1 if not found
	 * @param patient
	 * @param eventLabel
	 * @return date
	 */
	public static String getFirstEventType(Patient patient, String eventLabel, boolean inCohort){
		String eventStr = "";
		for (Event event : patient.getEvents()){
			if (event.getType().equals(eventLabel)  && (inCohort ? patient.dateInCohort(event.getDate(),true): true)){
				eventStr = event.getType();
				break;
			}
		}
		return eventStr;
	}
	
	public static int  getTimeToNextEvent(Patient patient, int indexDate, List<String> eventLabels, boolean inCohort, boolean includeIndexDate){
		int date = -1;
		for (Event event : patient.getEvents()){
			if ((includeIndexDate ? event.getDate()>=indexDate : event.getDate()>indexDate) &&
					event.inList(eventLabels) &&
					(inCohort ? patient.dateInCohort(event.getDate(),true): true)){
				date = event.date;
				break;
			}
		}
		if (date!=-1)
			return date-indexDate;
		else return -1;
	}
	
	public static int  getTimeToNextEvent(Patient patient, int indexDate, String eventLabel, boolean inCohort, boolean includeIndexDate){
		int date = -1;
		for (Event event : patient.getEvents()){
			if ((includeIndexDate ? event.getDate()>=indexDate : event.getDate()>indexDate) &&
					event.getType().equals(eventLabel) &&
					(inCohort ? patient.dateInCohort(event.getDate(),true): true)){
				date = event.date;
				break;
			}
		}
		if (date!=-1)
			return date-indexDate;
		else return -1;
	}
	
	public static int  getTimeToPreviousEvent(Patient patient, int indexDate, List<String> eventLabels, boolean inCohort, boolean includeIndexDate){
		int date = -1;
		for (Event event : patient.getEvents()){
			if ((includeIndexDate ? event.getDate()<=indexDate : event.getDate()<indexDate) &&
					event.inList(eventLabels) &&
					(inCohort ? patient.dateInCohort(event.getDate(),true): true)){
				date = event.date;
			}
		}
		if (date!=-1)
			return indexDate-date;
		else return -1;
	}
	
	
	public static int  getTimeToPreviousEvent(Patient patient, int indexDate, String eventLabel, boolean inCohort, boolean includeIndexDate){
		int date = -1;
		for (Event event : patient.getEvents()){
			if ((includeIndexDate ? event.getDate()<=indexDate : event.getDate()<indexDate) && 
					event.getType().equals(eventLabel) && 
					(inCohort ? patient.dateInCohort(event.getDate(),true): true)){
				date = event.date;
			}
		}
		if (date!=-1)
			return indexDate-date;
		else return -1;
	}
	
	public static int  getEventCount(Patient patient, int indexDate, String eventLabel, boolean inCohort, Integer windowStart, Integer windowEnd){
		int count = 0;
		for (Event event : patient.getEvents()) {
			if (
					event.getType().equals(eventLabel) && 
					(inCohort ? patient.dateInCohort(event.getDate(),true): true) && 
					((windowStart == null ? 0 : (indexDate + windowStart)) <= event.getDate()) && 
					(event.getDate() < (windowEnd == null ? Integer.MAX_VALUE : (indexDate + windowEnd)))
				) {
				count++;
			}
		}
		return count;
	}
	
	/**
	 * Gets the number of days to the next measurement with a certain value
	 * if valueLabel = "*" any value will match
	 * @param patient			- patient object
	 * @param indexDate			- reference date
	 * @param measurementLabel	- label of the measurement
	 * @param valueLabel		- label of the value
	 * @param inCohort			- search only in cohort time?
	 * @return
	 */
	public static int  getTimeToNextMeasurement(Patient patient, int indexDate, String measurementLabel, String valueLabel, boolean inCohort, boolean includeIndexDate){
		int date = -1;
		for (Measurement measurement : patient.getMeasurements()){
			if ((includeIndexDate ? measurement.getDate()>=indexDate : measurement.getDate()>indexDate) && 
					measurement.equals(measurementLabel.toUpperCase()) && 
					(valueLabel.equals("*") ? true  :measurement.getValue().equals(valueLabel.toUpperCase())) &&
					(inCohort ? patient.dateInCohort(measurement.getDate(),true): true)){
				date = measurement.date;
				break;
			}
		}
		if (date!=-1)
			return date-indexDate;
		else return -1;
	}
	
	/**
	 * Gets the number of days to the next measurement with a certain value
	 * based on the item lookup and value
	 * if valueLabel = "*" any value will match
	 * @param patient			- patient object
	 * @param indexDate			- reference date
	 * @param measurementLabel	- label of the measurement
	 * @param valueLabel		- label of the value
	 * @param inCohort			- search only in cohort time?
	 * @return
	 */
	public static int  getTimeToNextMeasurement(Patient patient, int indexDate, Item item, boolean inCohort, boolean includeIndexDate){
		int date = -1;
		for (Measurement measurement : patient.getMeasurements()){
			if ((includeIndexDate ? measurement.getDate()>=indexDate : measurement.getDate()>indexDate) && 
					measurement.inList(item.getLookup()) && 
					(item.getValue().equals("*") ? true : measurement.getValue().equals(item.getValue())) &&
					(inCohort ? patient.dateInCohort(measurement.getDate(),true): true)){
				date = measurement.date;
				break;
			}
		}
		if (date!=-1)
			return date-indexDate;
		else return -1;
	}
	
	/**
	 * Gets the number of days to the last measurement with a certain value
	 * before the index date. 
	 * if valueLabel = "*" any value will match
	 * @param patient			- patient object
	 * @param indexDate			- reference date
	 * @param item				- item Object
	 * @param valueLabel		- label of the value
	 * @param inCohort			- search only in cohort time?
	 * @return
	 */
	public static int  getTimeToPreviousMeasurement(Patient patient, int indexDate, String measurementLabel, String valueLabel, boolean inCohort, boolean includeIndexDate){
		int date = -1;
		for (Measurement measurement : patient.getMeasurements()){
			if ((includeIndexDate ? measurement.getDate()<=indexDate : measurement.getDate()<indexDate) && 
					measurement.getType().equals(measurementLabel.toUpperCase()) && 
					(valueLabel.equals("*") ? true  : measurement.getValue().equals(valueLabel.toUpperCase())) &&
					(inCohort ? patient.dateInCohort(measurement.getDate(),true): true)){
				date = measurement.date;
			}
		}
		if (date!=-1)
			return indexDate-date;
		else return -1;
	}
	
	/**
	 * Gets the number of days to the last measurement with a certain value
	 * before the index date. Any measurement in the item.lookup will match
	 * if valueLabel = "*" any value will match
	 * @param patient			- patient object
	 * @param indexDate			- reference date
	 * @param item				- item Object
	 * @param valueLabel		- label of the value
	 * @param inCohort			- search only in cohort time?
	 * @return
	 */
	public static int  getTimeToPreviousMeasurement(Patient patient, int indexDate, Item item, boolean inCohort, boolean includeIndexDate){
		int date = -1;
		for (Measurement measurement : patient.getMeasurements()){
			if ((includeIndexDate ? measurement.getDate()<=indexDate : measurement.getDate()<indexDate) && 
					measurement.inList(item.getLookup()) && 
					(item.getValue().equals("*") ? true  : measurement.getValue().equals(item.getValue())) &&
					(inCohort ? patient.dateInCohort(measurement.getDate(),true): true)){
				date = measurement.date;
			}
		}
		if (date!=-1)
			return indexDate-date;
		else return -1;
	}
	
	public static int  getMeasurementCount(Patient patient, int indexDate, String eventLabel, boolean inCohort, Integer windowStart, Integer windowEnd){
		int count = 0;
		for (Measurement measurement : patient.getMeasurements()) {
			if (
					measurement.getType().equals(eventLabel) && 
					(inCohort ? patient.dateInCohort(measurement.getDate(),true): true) && 
					((windowStart == null ? 0 : (indexDate + windowStart)) <= measurement.getDate()) && 
					(measurement.getDate() < (windowEnd == null ? Integer.MAX_VALUE : (indexDate + windowEnd)))
				) {
				count++;
			}
		}
		return count;
	}
	
	/**
	 * return the time since the last end of the prescription. 
	 * 
	 * @param patient			- patient object
	 * @param indexDate			- date to look back from
	 * @param atcLabels			- atc codes to find
	 * @param inCohort			- only look within cohort time
	 * @param includeIndexDate	- include atcs that start at index date?
	 * @param windowStart		- windowStart relative to index date. Prescription end date >= windowStart. null is beginning of time.
	 * 
	 * @return time since the end of the last prescription in atcLabel. -1 if not found, 0 if still running.
	 */
	public static int  getTimeSincePreviousPrescription(Patient patient, int indexDate, List<String> atcLabels, boolean inCohort, boolean includeIndexDate, int windowStart) {
		int date = -1;
		for (Prescription prescription : patient.getPrescriptions()){
			if (
					prescription.startsWith(atcLabels) &&
					(includeIndexDate ? prescription.getDate()<=indexDate : prescription.getDate()<indexDate) &&
					(prescription.getEndDate() >= windowStart) &&
					(inCohort ? patient.dateInCohort(prescription.getDate(),true): true)
				) {
				date = prescription.getEndDate();
			}
		}
		if (date!=-1)
			return Math.max(0,indexDate - date); // return 0 if still running
		else return -1;
	}
	
	/**
	 * return the time since the last end of the prescription. 
	 * 
	 * @param patient			- patient object
	 * @param indexDate			- date to look back from
	 * @param atcLabels			- atc codes to find
	 * @param inCohort			- only look within cohort time
	 * @param includeIndexDate	- include atcs that start at index date?
	 * @param windowEnd			- windowEnd relative to index date. Prescription end date < windowEnd. null is end of time.
	 * 
	 * @return time to the start of the next prescription in atcLabel. -1 if not found, 0 if still running.
	 */
	public static int  getTimeToNextPrescription(Patient patient, int indexDate, List<String> atcLabels, boolean inCohort, boolean includeIndexDate, int windowEnd) {
		int date = -1;
		for (Prescription prescription : patient.getPrescriptions()){
			if (
					prescription.startsWith(atcLabels) &&
					(includeIndexDate ? prescription.getDate()>=indexDate : prescription.getDate()>indexDate) &&
					(inCohort ? patient.dateInCohort(prescription.getDate(),true): true) &&  
					(prescription.getDate() < windowEnd)
				) {
				date = prescription.getDate();
			}
		}
		if (date!=-1)
			return Math.max(0,date - indexDate); // return 0 if still running
		else return -1;
	}
	
	

}
