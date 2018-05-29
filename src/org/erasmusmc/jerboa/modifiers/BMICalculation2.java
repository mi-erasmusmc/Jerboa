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
import org.apache.commons.collections.bag.HashBag;
import org.erasmusmc.jerboa.Jerboa;
import org.erasmusmc.jerboa.config.DataDefinition;
import org.erasmusmc.jerboa.dataClasses.Measurement;
import org.erasmusmc.jerboa.dataClasses.Patient;
import org.erasmusmc.jerboa.engine.Modifier;
import org.erasmusmc.jerboa.utilities.DateUtilities;
import org.erasmusmc.jerboa.utilities.Logging;
import org.erasmusmc.jerboa.utilities.StringUtilities;

/**
 * This modifier calculates BMI measurements from weight and height using the highest height measurement during adulthood.
 * If multiple BMI measurements are on the same date the highest value is kept. BMI has priority over Calculated BMI values.
 * For children this modifier is not calculating BMI measurements.
 *
 * @author PR
 *
 */
public class BMICalculation2 extends Modifier{

	/**
	 * The labels assigned for weight measurements.
	 */
	public String weightLabel;

	/**
	 * The label assigned for height measurements.
	 */
	public String heightLabel;

	/**
	 * The label assigned for the BMI measurements.
	 */
	public String BMILabel;

	/**
	 * The age in years when a patient is considered it reached adulthood
	 * and its height is considered constant over time.
	 */
	public int adultAge;


	/**
	 * This parameter allows the user to choose if the measurements in the history of the patient
	 * occur outside patient time should be used or not.
	 */
	public boolean useMeasurementsOutsidePatientTime;


	//PRIVATE PARAMETERS

	//counts occurrences of faulty measurements based on the measurement types of interest
	private HashBag faultyMeasurements;


	@SuppressWarnings("unused")
	private int nbPatients;

	//set the needed input files
	@Override
	public void setNeededFiles(){
		setRequiredFile(DataDefinition.MEASUREMENTS_FILE);
	}

	@Override
	public void setNeededExtendedColumns() { /* NOTHING	TO ADD */ }

	@Override
	public void setNeededNumericColumns() {
		setRequiredNumericColumn(DataDefinition.MEASUREMENTS_FILE, "Weight", "Value");
		setRequiredNumericColumn(DataDefinition.MEASUREMENTS_FILE, "Height", "Value");
	}

	@Override
	public boolean init() {

		//check if the list of weight labels is not empty
		boolean initOK = (weightLabel != null && !weightLabel.equals(""));

		//check if the list of height labels is not empty
		initOK &= (heightLabel != null && !heightLabel.equals(""));

		if (intermediateFiles){
			initOK &= Jerboa.getOutputManager().addFile(this.intermediateFileName);
			Jerboa.getOutputManager().writeln(this.intermediateFileName,"SubsetID,PatientID,MeasurementDate, MeasurementType,MeasurementValue", false);
		}

		if (initOK){
			faultyMeasurements = new HashBag();
			nbPatients = 0;
		}

		return initOK;
	}

	/**
	 *  Defines BMI measurements for the patient if during its history there were weight
	 *  and height measurements. Note that the measurements of the patient are considered
	 *  to  be sorted by date.
	 *  @param patient - the patient for which the BMI has to be calculated
	 *  @return patient - the same patient object with BMI values calculated
	 */
	@Override
	public Patient process(Patient patient) {
		nbPatients++;
		if (patient != null) {
			//see if the patient has measurements
			List<Measurement> measurements = patient.getMeasurements();
			double measurementValue;

			if (measurements != null && measurements.size() > 0) {
				List<Measurement> BMIList = new ArrayList<Measurement>();
				List<Measurement> BMICalcList = new ArrayList<Measurement>();
				List<Measurement> weightList = new ArrayList<Measurement>();
				List<Measurement> heightList = new ArrayList<Measurement>();
				List<Measurement> finalList = new ArrayList<Measurement>();

				// Collect all the BMI, Height and Weight and other measurements
				for (Measurement measurement : measurements) {
					if (measurement.getType().equals(BMILabel)) {
						BMIList.add(measurement);
					} else if (measurement.getType().equals(weightLabel)){
						weightList.add(measurement);
					} else if (measurement.getType().equals(heightLabel)){
						heightList.add(measurement);
					}  else
						finalList.add(measurement); //store all other measurementTypes
				}

				//find highest height during adultness
				double highestHeight = -1;
				for (Measurement height : heightList) {
					measurementValue = Double.valueOf(height.getValue());
					if (patient.getAgeAtDateInYears(height.date)>=adultAge){
						if (measurementValue>highestHeight)
							highestHeight = measurementValue;
					}

				}

				//BMI is only calculated if the weight is during adultness
				if (highestHeight >0){
					for (Measurement weight : weightList) {
						measurementValue = Double.valueOf(weight.getValue());
						if (patient.getAgeAtDate(weight.date)>=adultAge){
							Measurement BMICalc = new Measurement();
							BMICalc.date = weight.date;
							BMICalc.setType(BMILabel);
							BMICalc.setPatientID(weight.patientID);
							BMICalc.setValue(StringUtilities.format(measurementValue/(highestHeight*highestHeight)));
							BMICalc.setUnit("kg/m2");
							BMICalcList.add(BMICalc);
						}
					}
				}

				//first get per date the highest value of BMI and BMICalc
				HashMap<Integer,Double> BMIHighest = new HashMap<Integer,Double>();
				for (Measurement BMI : BMIList){
					double bmiValue = Double.valueOf(BMI.getValue());
					if (!BMIHighest.containsKey(BMI.date) || bmiValue>BMIHighest.get(BMI.date)){
						BMIHighest.put(BMI.date, bmiValue);
					}
				}

				HashMap<Integer,Double> BMICalcHighest = new HashMap<Integer,Double>();
				for (Measurement BMI : BMICalcList){
					double bmiValue = Double.valueOf(BMI.getValue());
					if (!BMICalcHighest.containsKey(BMI.date) || bmiValue>BMICalcHighest.get(BMI.date)){
						BMICalcHighest.put(BMI.date, bmiValue);
					}
				}

				// add all the BMI value
				for (Integer date : BMIHighest.keySet()){
					Measurement BMI = new Measurement();
					BMI.date = date;
					BMI.setType(BMILabel);
					BMI.setPatientID(patient.ID);
					BMI.setValue(StringUtilities.format((BMIHighest.get(date))));
					BMI.setUnit("kg/m2");
					finalList.add(BMI);

					if ((!Jerboa.unitTest) && intermediateFiles) {
						Jerboa.getOutputManager().writeln(this.intermediateFileName,patient.subset + "," + patient.getPatientID() + "," + DateUtilities.daysToDate(BMI.getDate()) + "," + BMI.getType() + "," + BMI.getValue(), true);
					}
				}

				// add all the BMICalc values if there was not a BMI on that date
				for (Integer date : BMICalcHighest.keySet()){
					if (!BMIHighest.containsKey(date)){
						Measurement BMI = new Measurement();
						BMI.date = date;
						BMI.setType(BMILabel);
						BMI.setPatientID(patient.ID);
						BMI.setValue(StringUtilities.format(BMICalcHighest.get(date)));
						BMI.setUnit("kg/m2");
						finalList.add(BMI);

						if ((!Jerboa.unitTest) && intermediateFiles) {
							Jerboa.getOutputManager().writeln(this.intermediateFileName,patient.subset + "," + patient.getPatientID() + "," + DateUtilities.daysToDate(BMI.getDate()) + "," + BMI.getType() + "," + BMI.getValue(), true);
						}
					}
				}

				//all weight and height back in the final list
				finalList.addAll(heightList);
				finalList.addAll(weightList);
				Collections.sort(finalList);

				patient.setMeasurements(finalList);
			} //end if has measurements

		}

		return patient;
	}

	@Override
	public void outputResults(){

		flushRemainingData();

		if (intermediateStats){
			Logging.add("");
			Logging.add("BMI definition - counters");
			Logging.add("------------------------------------------------");
			Logging.add("!NOTE: the measurements outside patient time were "+(!useMeasurementsOutsidePatientTime ? "not" : "")+" used!");
			printCounters(BMILabel);
			printCounters(weightLabel);
			printCounters(heightLabel);
		}
	}

	/**
	 * Prints to the console the counter values for a measurement type.
	 * If other counters are added in the process() function of this modifier
	 * and it is desired to print them to the console then this method is to
	 * be modified. Note that it assumes all counters are present in the
	 * faultyMeasurements bag.
	 * @param measurementType - the type of the measurement
	 */
	private void printCounters(String measurementType) {
		Logging.add("Counters for "+measurementType+": ");
		int count = 0;
		if ((count = faultyMeasurements.getCount(measurementType+" OUTSIDE")) != 0)
			Logging.add(oneOrMany(count)+"outside patient time");
		if ((count = faultyMeasurements.getCount(measurementType+" UNBORN")) != 0)
			Logging.add(oneOrMany(count)+"discarded for occurring before patient birth");
		if ((count = faultyMeasurements.getCount(measurementType+" NO VALUE")) != 0)
			Logging.add(oneOrMany(count)+"discarded for having no value");
		if ((count = faultyMeasurements.getCount(measurementType+" ZERO")) != 0)
			Logging.add(oneOrMany(count)+"discarded for a value of zero");
		if ((count = faultyMeasurements.getCount(measurementType+" NEGATIVE")) != 0)
			Logging.add(oneOrMany(count)+"discarded for a negative value");
		if ((count = faultyMeasurements.getCount(measurementType+" NAN")) != 0)
			Logging.add(oneOrMany(count)+"discarded for a non numeric value");
		Logging.add("");
	}

	/**
	 * Returns the appropriate grammatical form depending on the count.
	 * @param count - the number of measurements
	 * @return - the text in a correct grammatical form
	 */
	private String oneOrMany(int count) {
		return (count == 1 ? "There was 1 measurement " :
			"There were "+count+" measurements ");
	}

	@Override
	public void displayGraphs() {}

	@Override
	public void calcStats(){/*TODO if needed */}

}

