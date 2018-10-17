/***********************************************************************************
 *                                                                                 *
 * Copyright (C) 2017  Erasmus MC, Rotterdam, The Netherlands                      *
 *                                                                                 *
 * This file is part of JerboaReloaded.                                            *
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

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.SortedSet;
import java.util.TreeMap;
import java.util.TreeSet;

import org.apache.commons.collections.bag.HashBag;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.math3.linear.RealMatrix;
import org.erasmusmc.jerboa.Jerboa;
import org.erasmusmc.jerboa.config.DataDefinition;
import org.erasmusmc.jerboa.config.FilePaths;
import org.erasmusmc.jerboa.config.Parameters;
import org.erasmusmc.jerboa.dataClasses.Measurement;
import org.erasmusmc.jerboa.dataClasses.Patient;
import org.erasmusmc.jerboa.engine.Modifier;
import org.erasmusmc.jerboa.engine.PatientObjectCreator;
import org.erasmusmc.jerboa.gui.graphs.BMIPlot;
import org.erasmusmc.jerboa.gui.graphs.BarPlotDS;
import org.erasmusmc.jerboa.gui.graphs.Graphs;
import org.erasmusmc.jerboa.gui.graphs.LinePlotDS;
import org.erasmusmc.jerboa.gui.graphs.PieChart;
import org.erasmusmc.jerboa.gui.graphs.Plot;
import org.erasmusmc.jerboa.gui.graphs.StackedBarPlot;
import org.erasmusmc.jerboa.utilities.AgeGroupDefinition;
import org.erasmusmc.jerboa.utilities.DateUtilities;
import org.erasmusmc.jerboa.utilities.ExtendedMultiKey;
import org.erasmusmc.jerboa.utilities.InputFileUtilities;
import org.erasmusmc.jerboa.utilities.Logging;
import org.erasmusmc.jerboa.utilities.MultiKeyBag;
import org.erasmusmc.jerboa.utilities.OutputBagStats;
import org.erasmusmc.jerboa.utilities.OutputManager;
import org.erasmusmc.jerboa.utilities.Progress;
import org.erasmusmc.jerboa.utilities.StringUtilities;
import org.erasmusmc.jerboa.utilities.Timer;
import org.erasmusmc.jerboa.utilities.Wildcard;
import org.erasmusmc.jerboa.utilities.AgeGroupDefinition.AgeGroup;
import org.erasmusmc.jerboa.utilities.stats.HistogramStats;
import org.erasmusmc.jerboa.utilities.stats.LMSReference;
import org.erasmusmc.jerboa.utilities.stats.formulas.LMS;

/**
 * This modifier defines BMI (Body Mass Index) measurements of a patient based on the history of 
 * its weight, height and even BMI measurements.
 * The BMI is defined as the ratio between the weight (in kilograms) and the height squared
 * (in meters), (e.g., a patient with a height of {@code 1.90m} and a weight of {@code 80 kg} would have
 * a {@code BMI = 80/(1.90)^2 = 22.16}. If a BMI value is provided in the measurements of a patient,
 * this value will be used; otherwise it will be calculated using the above formula with the 
 * condition that the time distance between a weight and a height measurement does not exceed the 
 * maximum set time span, with different values for children as their height and weight is highly variable.   
 * 
 * @author MG
 *
 */
public class BMICalculation extends Modifier{
	
	/**
	 * The labels assigned for weight measurements.
	 */
	public String weightLabel;
	
	/**
	 * Factor to convert weight measurements to kilograms.
	 */
	public double weightFactor;

	/**
	 * The label assigned for height measurements.
	 */
	public String heightLabel;
	
	/**
	 * Factor to convert height measurements to meters.
	 */
	public double heightFactor;

	/**
	 * The label assigned for the BMI measurements.
	 */
	public String BMILabel;

	/**
	 * The label assigned for the new BMI category measurements based on the BMI values.
	 */
	public String BMICategoryLabel;

	/**
	 * The label assigned for the new calculated BMI measurements.
	 */
	public String calcBMILabel;

	/**
	 * The label assigned for the newly calculated BMI Category measurements based on the calculated BMI values.
	 */
	public String calcBMICategoryLabel;

	/**
	 * The label assigned for the new caculated BSA measurements.
	 */
	public String calcBSALabel;

	/**
	 * The minimum age of a patient to be considered in the population.
	 */
	public int minAge;
	
	/**
	 * The maximum age of a patient to be considered in the population.
	 */
	public int maxAge;
	
	/**
	 * The age in years when a patient is considered it reached adulthood
	 * and its height is considered constant over time.
	 */
	public int adultAge;

	/**
	 * The list of age ranges used when coding patient age. Each range should be represented as 
	 * three semicolon separated values:
	 * <p>
	 * - The start year since birth<br>
	 * - The end year since birth (exclusive)<br>
	 * - The label of age range<br>
	 * <p>
	 * {@code For example: 0;5;0-4}
	 */
	public List<String> ageGroups = new ArrayList<String>();

	/**
	 * Specifies any subgroups to be identified for the calculation additional standardized rates.
	 * Each code should be represented as two semicolon separated values:
	 * <p>
	 * - The label of the subgroup (the same label can be used in multiple rows)<br>
	 * - The age range<br>
	 * <p>
	 * {@code For example: children;0-4}
	 */
	public List<String> populationSubgroups = new ArrayList<String>();

	/**
	 * The maximum time difference in days between a weight and height 
	 * measurement in order to define a valid BMI calculation.
	 */
	public int maxTimeSpan;

	/**
	 * The maximum time difference in days between a weight and height 
	 * measurement for children in order to define a valid BMI calculation.
	 * In children, the weight and height vary much more than in adults,
	 * thus this parameter should have a value inferior to maxTimeSpan.
	 */
	public int maxTimeSpanChildren;

	/**
	 * The lower threshold limit of what is considered to be a valid BMI value.
	 */
	public double minBMIValue;

	/**
	 * The upper threshold limit of what is considered to be a valid BMI value.
	 */
	public double maxBMIValue;
	
	/**
	 * The minimum number of patients for a value in the percentile graphs to be shown.
	 */
	public int percentilesMinimumCount;

	/**
	 * The reference LMS cut-offs to be used in the comparison of the BMI values. 
	 */
	public String BMIReference;
	
	/**
	 * This parameter will be set to true if the user wants the measurements of a patient to be replaced
	 * with the list of newly defined measurements. If set to false, the newly defined measurements will be
	 * added to the existing ones.
	 */
	public boolean replaceMeasurements;

	/**
	 * This parameter allows the user to choose if the measurements in the history of the patient
	 * occur outside patient time should be used or not.
	 */
	public boolean useMeasurementsOutsidePatientTime;
	
	/**
	 * Limit of the higher than count in the pie chart with number of measurements per patient.
	 */
	public int pieChartUpperLimit;
	
	/**
	 * Label used for the BMI viewer. If left empty "BMI Viewer" is used.
	 */
	public String viewerLabel;

	/**
	 * If false no result files are created
	 */
	public boolean outputResults;
	
	//PRIVATE MEMBERS

	//will hold occurrences of patients adults/children, gender, label of BMI (existing or calculated), age at measurement, value
	private MultiKeyBag BMIBag;
	private MultiKeyBag BMIPercentilesBag;
	private MultiKeyBag BMIBagTable;
	
	//will hold counters for: BMI categories, child/adult, year, gender 
	private MultiKeyBag BMIYearsCategoriesBag;
	private MultiKeyBag BMIYearsCategoriesBagTable;
	
	//will hold counters for: BMI categories, child/adult, age group, gender 
	private MultiKeyBag BMIAgeGroupsCategoriesBag;
	private MultiKeyBag BMIAgeGroupsCategoriesBagTable;

	// Year, Age, BMI
	private Map<Integer, List<Measurement> > patientYearBMIMap;
	private Map<AgeGroup, List<Measurement> > patientAgeGroupBMIMap;

	//will count the number of BMI measurements a patient has (existing + calculated) 
	private HashBag BMICounts;
	private MultiKeyBag BMICountsBagTable;

	//will hold counters for the adults/children, gender, patient time before and patient time after the first BMI measurement (existent or calculated)
	private MultiKeyBag BMIPatientTimeBefore;
	private MultiKeyBag BMIPatientTimeAfter;

	//will count the BMI measurements (existent vs. calculated) for adults/children having the same date but with different values  
	private MultiKeyBag ambiguousBMI;
	
	//will count the number of patients with a first/last BMI for: gender, age group
	private MultiKeyBag firstBMIBag;
	private MultiKeyBag lastBMIBag;

	//counts occurrences of faulty measurements based on the measurement types of interest 
	private HashBag faultyMeasurements;
	
	//will contain the newly defined measurements
	private List<Measurement> derivedMeasurements;

	// Age group definition
	private AgeGroupDefinition ageGroupDefinition = null;

	// Population subgroups definition
	private Map<String, Set<String>> populationSubgroupsDefinition = null;
	private List<String> populationSubgroupList = null;
	
	private Patient currentPatient;
	private boolean firstBMI;
	private Measurement lastBMI;

	//holds the CDC LMS reference
	private LMS lms;

	//counters
	private int nbBMIPerPatient;
	
	//LABELS
	private static final String DEFAULT_BMI_CATEGORY_LABEL = "BMI_CATEGORY";
	private static final String DEFAULT_BMI_CALC_LABEL = "BMI_CALC";
	private static final String DEFAULT_BMI_CALC_CATEGORY_LABEL = "BMI_CALC_CATEGORY";
	private static final String DEFAULT_BSA_CALC_LABEL = "BSA_CALC";
	
	//measurement categories
	private static final String MEASUREMENT_OUTSIDE_PATIENT_TIME = " OUTSIDE";
	private static final String MEASUREMENT_BEFORE_BIRTH = " UNBORN";
	private static final String MEASUREMENT_NO_VALUE = " NO VALUE";
	private static final String MEASUREMENT_NEGATIVE_VALUE = " NEGATIVE";
	private static final String MEASUREMENT_ZERO_VALUE = " ZERO";
	private static final String MEASUREMENT_NON_NUMERIC = " NAN";
	
	//population categories
	private static final String ADULT_LABEL = "ADULT";
	private static final String PAEDIATRIC_LABEL = "CHILD";
	
	//BMI categories
	private static final String BMI_NORMAL = " NORMAL";
	private static final String BMI_OVERWEIGHT = " OVERWEIGHT";
	private static final String BMI_OBESE = "OBESE";
	private static final String BMI_OUTLIER = " OUTLIER";
	
	
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
			Jerboa.getOutputManager().writeln(this.intermediateFileName, "SubsetID,PatientID,MeasurementDate, MeasurementType,MeasurementValue", false);
		}

		if (initOK){

			if (BMICategoryLabel == null || BMICategoryLabel.equals("")) //should never occur
				BMICategoryLabel = DEFAULT_BMI_CATEGORY_LABEL;

			if (calcBMILabel == null || calcBMILabel.equals("")) //should never occur
				calcBMILabel = DEFAULT_BMI_CALC_LABEL;

			if (calcBMICategoryLabel == null || calcBMICategoryLabel.equals("")) //should never occur
				calcBMICategoryLabel = DEFAULT_BMI_CALC_CATEGORY_LABEL;

			if (calcBSALabel == null || calcBSALabel.equals("")) //should never occur
				calcBSALabel = DEFAULT_BSA_CALC_LABEL;

			InputFileUtilities.addToLookup(InputFileUtilities.getMeasurementTypes(), BMICategoryLabel);
			InputFileUtilities.addToLookup(InputFileUtilities.getMeasurementTypes(), calcBMILabel);
			InputFileUtilities.addToLookup(InputFileUtilities.getMeasurementTypes(), calcBMICategoryLabel);
			
			// Parse age group definition
			ageGroupDefinition = new AgeGroupDefinition(ageGroups);
			
			// Parse population subgroups definition
			populationSubgroupsDefinition = new HashMap<String, Set<String>>();
			if (populationSubgroups.size() > 0) {
				Set<String> subGroupAgeGroups = new HashSet<String>();
				populationSubgroupsDefinition.put("Standardized: ", subGroupAgeGroups);
				for (AgeGroup ageGroup : ageGroupDefinition.getAgeGroups())
					subGroupAgeGroups.add(ageGroup.getLabel());
				
				for (String subgroup : populationSubgroups) {
					String[] subgroupSplit = subgroup.split(";");
					String subGroupId = "Standardized: " + subgroupSplit[0];
					if ((subGroupAgeGroups = populationSubgroupsDefinition.get(subGroupId)) == null) {
						subGroupAgeGroups = new HashSet<String>();
						populationSubgroupsDefinition.put(subGroupId, subGroupAgeGroups);
					}
					subGroupAgeGroups.add(subgroupSplit[1]);
				}
				populationSubgroupList = new ArrayList<String>();
				for (String subGroup : populationSubgroupsDefinition.keySet())
					populationSubgroupList.add(subGroup);
			}
			
			//initialize reference
			if (BMIReference.equals(LMSReference.CDC_REFERENCE))
				lms = new LMS(LMSReference.setCDCReference());
			else if (BMIReference.equals(LMSReference.IOFT_REFERENCE))
				lms = new LMS(LMSReference.setIOFTReference());
			else if (BMIReference.equals(LMSReference.WHO_REFERENCE))
				lms = new LMS(LMSReference.setWHOReference());
			
			//initialize counters
			BMICounts = new HashBag();
			BMIPatientTimeBefore = new MultiKeyBag();
			BMIPatientTimeAfter = new MultiKeyBag();
			BMIBag = new MultiKeyBag();
			BMIPercentilesBag = new MultiKeyBag();
			BMIYearsCategoriesBag = new MultiKeyBag();
			BMIAgeGroupsCategoriesBag = new MultiKeyBag();
			
			//TODO: Used now to create the tables should also be used to create the graphs if possible
			BMICountsBagTable = new MultiKeyBag();
			BMIBagTable = new MultiKeyBag();
			BMIYearsCategoriesBagTable = new MultiKeyBag();
			BMIAgeGroupsCategoriesBagTable = new MultiKeyBag();
			
			//First/last BMI for: gender, age group 
			firstBMIBag = new MultiKeyBag();
			lastBMIBag = new MultiKeyBag();
			
			ambiguousBMI = new MultiKeyBag();
			faultyMeasurements = new HashBag();

			nbBMIPerPatient = 0;
			
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

		if (patient != null) {
		
			currentPatient = patient;
			firstBMI = true;
			lastBMI = null;
			
			// Year, BMI
			patientYearBMIMap = new HashMap<Integer, List<Measurement> >();
			// Year, BMI
			patientAgeGroupBMIMap = new HashMap<AgeGroup, List<Measurement> >();
		
			//see if the patient has measurements
			List<Measurement> measurements = patient.getMeasurements();

			//number of BMIs
			nbBMIPerPatient = 0;

			if (measurements != null && measurements.size() > 0) {

				int lastDate = -1;
				Measurement referenceBMI = null;
				Measurement calculatedBMI = null;
				double measurementValue = -1;
				
				derivedMeasurements = new ArrayList<Measurement>();
				List<Measurement> BMIList = new ArrayList<Measurement>();
				List<Measurement> weightList = new ArrayList<Measurement>();
				List<Measurement> heightList = new ArrayList<Measurement>();
				Map<Measurement, Map<Measurement, Integer>> weightHeightDistanceMaps = new TreeMap<Measurement, Map<Measurement, Integer>>();
				Map<Measurement, List<Measurement>> heightWeightLists = new TreeMap<Measurement, List<Measurement>>();
				Map<Measurement, Double> calcBMIWeight = new HashMap<Measurement, Double>();
				Map<Measurement, Double> calcBMIHeight = new HashMap<Measurement, Double>();

				// Collect all the BMI, Height and Weight measurements
				for (Measurement measurement : measurements) {
					
					//inside patient time 
					if (!useMeasurementsOutsidePatientTime && (measurement.getDate()<patient.startDate || measurement.getDate()>patient.endDate)){
						faultyMeasurements.add(measurement.getType()+MEASUREMENT_OUTSIDE_PATIENT_TIME);
						continue;						
					}

					//check presence of a value
					if (measurement.getValue() == null || measurement.getValue().equals("")){
						faultyMeasurements.add(measurement.getType()+MEASUREMENT_NO_VALUE);
						continue;
					}
					
					//check if measurement is prior to patient birth
					if (measurement.getDate() - patient.birthDate < 0){
						faultyMeasurements.add(measurement.getType()+MEASUREMENT_BEFORE_BIRTH);
						continue;
					}
					
					try{
						measurementValue = Double.valueOf(measurement.getValue());
						
						//check for negative value
						if (measurementValue < 0){
							faultyMeasurements.add(measurement.getType()+MEASUREMENT_NEGATIVE_VALUE);
							continue;
						}
						//check for zero values
						if (measurementValue == 0){
							faultyMeasurements.add(measurement.getType()+MEASUREMENT_ZERO_VALUE);
							continue;
						}
					}catch (NumberFormatException e){
						//the value is not a number	
						faultyMeasurements.add(measurement.getType()+MEASUREMENT_NON_NUMERIC);
						continue;
					}
					
					if (measurement.getType().equals(BMILabel)) {
						BMIList.add(measurement);

						Measurement bmiCategory = new Measurement();
						bmiCategory.setPatientID(measurement.getPatientID());
						bmiCategory.setDate(measurement.getDate());
						bmiCategory.setValue(getBMIValueCategory(patient.getAgeAtDate(measurement.getDate()), Double.parseDouble(measurement.getValue())));
						bmiCategory.setType(BMICategoryLabel);
						derivedMeasurements.add(bmiCategory);
					} else if (measurement.getType().equals(weightLabel)){
						weightList.add(measurement);
						weightHeightDistanceMaps.put(measurement, new HashMap<Measurement, Integer>());
					} else if (measurement.getType().equals(heightLabel)){
						heightList.add(measurement);
						heightWeightLists.put(measurement, new ArrayList<Measurement>());
					}
				}
				
				// Add the weights to the height / weight trees
				for (Measurement weight : weightList) {
					boolean isAnAdult = isAdult(patient, weight.getDate());
					for (Measurement height : heightList) {
						if (isAnAdult == isAdult(patient, height.getDate())) {
							int distance = Math.abs(weight.getDate() - height.getDate()); 
							if (distance <= (isAnAdult ? maxTimeSpan : maxTimeSpanChildren)) {
								heightWeightLists.get(height).add(weight);
								weightHeightDistanceMaps.get(weight).put(height, distance);
							}
						}
					}
				}
				
				//TODO decide if it is to be kept or not
//				List<String> sortedWeights = new ArrayList<String>();
//				Map<String, Measurement> weightsMapping = new HashMap<String, Measurement>(); 
//				for (Measurement weight : weightHeightDistanceMaps.keySet()) {
//					sortedWeights.add(weight.toString());
//					weightsMapping.put(weight.toString(), weight);
//				}
//				Collections.sort(sortedWeights);
//				for (String strWeight : sortedWeights) {
//					Measurement weight = weightsMapping.get(strWeight);
//					List<String> sortedHeights = new ArrayList<String>();
//					Map<String, Measurement> heightsMapping = new HashMap<String, Measurement>(); 
//					for (Measurement height : weightHeightDistanceMaps.get(weight).keySet()) {
//						sortedHeights.add(height.toString());
//						heightsMapping.put(height.toString(), height);
//					}
//					Collections.sort(sortedHeights);
//					for (String strHeight : sortedHeights) {
//						Measurement height = heightsMapping.get(strHeight);
//						int distance = weightHeightDistanceMaps.get(weight).get(height);
//						Logging.add("weightHeightDistanceMaps( " + strWeight + " , " + strHeight + " = " + distance);
//					}
//				}
				
				// Keep only the nearest height for each weight.
				// When equal distance the height before the weight is taken.
				for (Measurement weight : weightList) {
					int minimumDistance = Integer.MAX_VALUE;
					Measurement nearestHeight = null;
					for (Measurement height : weightHeightDistanceMaps.get(weight).keySet()) {
						int distance = Math.abs(weight.getDate() - height.getDate());
						if (distance < minimumDistance) {
							minimumDistance = distance;
							nearestHeight = height;
						}
						else  if (distance == minimumDistance) {
							// Equal distance -> take oldest (is the one before)
							if ((nearestHeight == null) || (height.getDate() < nearestHeight.getDate())) {
								nearestHeight = height;
							}
						}
					}
					if (nearestHeight != null) {
						for (Measurement height : weightHeightDistanceMaps.get(weight).keySet()) {
							if (height != nearestHeight) {
								heightWeightLists.get(height).remove(weight);
							}
						}
					}
				}

				//TODO decide if it is to be kept or not
//				List<String> sortedHeights = new ArrayList<String>();
//				Map<String, Measurement> heightsMapping = new HashMap<String, Measurement>(); 
//				for (Measurement height : heightWeightLists.keySet()) {
//					sortedHeights.add(height.toString());
//					heightsMapping.put(height.toString(), height);
//				}
//				Collections.sort(sortedHeights);
//				for (String strHeight : sortedHeights) {
//					Measurement height = heightsMapping.get(strHeight);
//					for (Measurement weight : heightWeightLists.get(height)) {
//						Logging.add("heightWeightLists( " + height + " , " + weight);
//					}
//				}
			
				// Calculate BMI values based on weight and height and add them sorted by date
				// to the list of BMI measurements 
				for (Measurement height : heightList) {
					double heightValue = Double.valueOf(height.getValue());
					for (Measurement weight : heightWeightLists.get(height)) {
						
						double weightValue = Double.valueOf(weight.getValue());
						boolean isAnAdult = isAdult(patient, weight.getDate());
						
						//create new BMI measurement
						calculatedBMI = new Measurement();
						calculatedBMI.patientID = patient.ID;
						
						//TODO : to discuss with Lara
						//the date of the calculated BMI measurement is based on the the weight date in adults and
						//on the last date of either the weight or height in children
						calculatedBMI.setDate(isAnAdult ? weight.getDate() : Math.max(weight.getDate(), height.getDate()));
						
						// Type is set to "" to make sure it cannot come from an input file.
						calculatedBMI.setType(""); 
						double value = ((weightValue * weightFactor) / (heightValue * heightFactor * heightValue * heightFactor));
						calculatedBMI.setValue(StringUtilities.formatOneDec(value));
						calcBMIWeight.put(calculatedBMI, weightValue);
						calcBMIHeight.put(calculatedBMI, heightValue);
						
						if (BMIList.size() == 0) {
							BMIList.add(calculatedBMI);
						}
						else {
							for (int bmiIndex = 0; bmiIndex < BMIList.size(); bmiIndex++) {
								if (calculatedBMI.getDate() <= BMIList.get(bmiIndex).getDate()) {
									BMIList.add(bmiIndex, calculatedBMI);
									calculatedBMI = null;
									break;
								}
							}
							if (calculatedBMI != null) {
								BMIList.add(calculatedBMI);
							}
						}
					}
				}
				
				//for (Measurement bmi : BMIList) {
				//	Logging.add("BMI\t" + bmi);
				//}
				
				if (BMIList!=null && BMIList.size()>0){
					lastDate = BMIList.get(0).getDate();
				}
				
				for (Measurement bmi : BMIList) {
					//check if there were BMI measures on the last day and not first ever! ADD
					if (bmi.getDate() != lastDate){
						if (addBMIMeasurements(referenceBMI,calculatedBMI) == 2) {
							Measurement bmiCategory = new Measurement();
							bmiCategory.setPatientID(calculatedBMI.getPatientID());
							bmiCategory.setDate(calculatedBMI.getDate());
							bmiCategory.setValue(getBMIValueCategory(patient.getAgeAtDate(calculatedBMI.getDate()), Double.parseDouble(calculatedBMI.getValue())));
							bmiCategory.setType(calcBMICategoryLabel);
							derivedMeasurements.add(bmiCategory);

							Measurement calculatedBSA = new Measurement();
							calculatedBSA.setPatientID(calculatedBMI.getPatientID());
							calculatedBSA.setDate(calculatedBMI.getDate());
							calculatedBSA.setType(calcBSALabel);
							calculatedBSA.setValue(Double.toString(0.20247 * Math.pow(calcBMIWeight.get(calculatedBMI) * weightFactor, 0.425) * Math.pow(calcBMIHeight.get(calculatedBMI) * heightFactor, 0.725)));
							derivedMeasurements.add(calculatedBSA);
						}
						lastDate = bmi.getDate();
						referenceBMI = null;
						calculatedBMI = null;
					}
					
					if (bmi.getType().equals(BMILabel)){
						//get it as a reference
						referenceBMI = new Measurement(bmi);
						//round the BMI
						referenceBMI.setValue(StringUtilities.formatOneDec(Double.parseDouble(bmi.getValue())));
					}
					else {
						// Correct the type of the calculated BMI.
						bmi.setType(calcBMILabel);
						calculatedBMI = bmi;
					}
				}
				//Add last BMI
				if (addBMIMeasurements(referenceBMI,calculatedBMI) == 2) {
					Measurement bmiCategory = new Measurement();
					bmiCategory.setPatientID(calculatedBMI.getPatientID());
					bmiCategory.setDate(calculatedBMI.getDate());
					bmiCategory.setValue(getBMIValueCategory(patient.getAgeAtDate(calculatedBMI.getDate()), Double.parseDouble(calculatedBMI.getValue())));
					bmiCategory.setType(calcBMICategoryLabel);
					derivedMeasurements.add(bmiCategory);

					Measurement calculatedBSA = new Measurement();
					calculatedBSA.setPatientID(calculatedBMI.getPatientID());
					calculatedBSA.setDate(calculatedBMI.getDate());
					calculatedBSA.setType(calcBSALabel);
					calculatedBSA.setValue(Double.toString(0.20247 * Math.pow(calcBMIWeight.get(calculatedBMI) * weightFactor, 0.425) * Math.pow(calcBMIHeight.get(calculatedBMI) * heightFactor, 0.725)));
					derivedMeasurements.add(calculatedBSA);
				}
				
				//add to or replace the measurement list of the patient
				if (derivedMeasurements != null && derivedMeasurements.size() > 0){
					if (replaceMeasurements)
						patient.setMeasurements(derivedMeasurements);
					else
						patient.getMeasurements().addAll(derivedMeasurements);
					// Sort the measurements
					Collections.sort(patient.getMeasurements());
				}
			} //end if has measurements

			if (lastBMI != null) {
				//Gender, Year, AgeGroup, BMI 
				//lastBMIBag.add(ageGroupDefinition.getAgeGroups(patient.getAgeAtDateInYears(lastBMI.getDate())).get(0).getLabel());
				lastBMIBag.add(new ExtendedMultiKey(Patient.convertGender(currentPatient.gender), 
						ageGroupDefinition.getAgeGroups(patient.getAgeAtDateInYears(lastBMI.getDate())).get(0).getLabel()));
			}

			BMICounts.add(nbBMIPerPatient);
			BMICountsBagTable.add(new ExtendedMultiKey(Patient.convertGender(patient.gender), nbBMIPerPatient));

			//add to the output  
			addToOutputBuffer(patient);
		}
		
		// Average BMI per year

		Map<Integer, List<Measurement> > patientAgeBMIMap = new HashMap<Integer, List<Measurement> >();
		for (int year : patientYearBMIMap.keySet()) {
			double ageSum = 0.0;
			double bmiSum = 0.0;
			double bmiCount = 0.0;
			if (patientYearBMIMap.get(year).size() > 0) {
				for (Measurement bmi : patientYearBMIMap.get(year)) {
					int age = currentPatient.getAgeAtDate(bmi.getDate());
					
					//Add the BMIs per month for the percentile plots (Average BMI per age)
					//TODO: CHECK in Years.
					List<Measurement> ageBMIList = null;
					int ageYears = (int) Math.round(age/DateUtilities.daysPerYear);
					if (!patientAgeBMIMap.containsKey(ageYears)) {
						ageBMIList = new ArrayList<Measurement>(); 
						patientAgeBMIMap.put(ageYears, ageBMIList);
					}
					else {
						ageBMIList = patientAgeBMIMap.get(ageYears);
					}
					ageBMIList.add(bmi);
					
					ageSum += age;
					bmiSum += Double.parseDouble(bmi.getValue());
					bmiCount++;
				}

				int averageAge = (int) Math.round(ageSum / bmiCount);
				double averageBMI = bmiSum / bmiCount;

				if ((averageAge/DateUtilities.daysPerYear >= minAge) && (averageAge/DateUtilities.daysPerYear <= maxAge)) {
					//add it to the bag per category
					BMIYearsCategoriesBag.add(new ExtendedMultiKey(getBMIValueCategory(averageAge, averageBMI), 
							(averageAge/DateUtilities.daysPerYear < adultAge ? PAEDIATRIC_LABEL : ADULT_LABEL), year, currentPatient.gender));
					
					//Gender, Year, BMI category
					BMIYearsCategoriesBagTable.add(new ExtendedMultiKey(Patient.convertGender(currentPatient.gender), 
							year, getBMIValueCategory(averageAge, averageBMI)));
				}
			}
		}

		// Average BMI per age
		for (int age : patientAgeBMIMap.keySet()) {
			double bmiSum = 0.0;
			double bmiCount = 0.0;
			if (patientAgeBMIMap.get(age).size() > 0) {
				String bmiType = "";
				for (Measurement bmi : patientAgeBMIMap.get(age)) {
					bmiType = bmi.getType();
					bmiSum += Double.parseDouble(bmi.getValue());
					bmiCount++;
				}

				double averageBMI = bmiSum / bmiCount;
				BMIPercentilesBag.add(new ExtendedMultiKey((age >= adultAge ? ADULT_LABEL : PAEDIATRIC_LABEL), (int)currentPatient.gender,
						bmiType, age, averageBMI));

				//Fill bag for table output Gender, Age, value
				BMIBagTable.add(new ExtendedMultiKey(Patient.convertGender(currentPatient.gender),age,averageBMI));
			}
		}
		
		// Average BMI per age group
		for (AgeGroup ageGroup: patientAgeGroupBMIMap.keySet()) {
			double ageSum = 0.0;//MEES: was outside the loop!!
			double bmiSum = 0.0;
			double bmiCount = 0.0;
			if (patientAgeGroupBMIMap.get(ageGroup).size() > 0) {
				for (Measurement bmi : patientAgeGroupBMIMap.get(ageGroup)) {
					//TODO: Mees: why twice?
					//ageSum += currentPatient.getAgeAtDateInYears(bmi.getDate());
					ageSum += currentPatient.getAgeAtDate(bmi.getDate());
					bmiSum += Double.parseDouble(bmi.getValue());
					bmiCount++;
				}

				int averageAge = (int) Math.round(ageSum / bmiCount);
				double averageBMI = bmiSum / bmiCount;

				if ((averageAge/DateUtilities.daysPerYear >= minAge) && (averageAge/DateUtilities.daysPerYear <= maxAge)) {
					//add it to the bag per category 
					BMIAgeGroupsCategoriesBag.add(new ExtendedMultiKey(getBMIValueCategory(averageAge, averageBMI), (averageAge/DateUtilities.daysPerYear < adultAge ? PAEDIATRIC_LABEL : ADULT_LABEL), ageGroup.getLabel(), currentPatient.gender));
					
					//Gender, Age group, BMI category
					BMIAgeGroupsCategoriesBagTable.add(new ExtendedMultiKey(Patient.convertGender(currentPatient.gender), ageGroup.getLabel(), getBMIValueCategory(averageAge, averageBMI)));
				}
			}
		}

		return patient;
	}
	
	//****************************OUTPUT AND FORMATTING*****************************//

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

			Logging.add("Ambiguous BMI:");
			long nbAmbiguousBMI = 0;
			if ((nbAmbiguousBMI = ambiguousBMI.getCount(new ExtendedMultiKey(Wildcard.INTEGER(), Wildcard.STRING(), Wildcard.DOUBLE()))) != 0)
				Logging.add("There "+(nbAmbiguousBMI == 1 ? "was " : "were ")+nbAmbiguousBMI+" dates on which the calculated BMI and measured BMI were different. \n" +
						ambiguousBMI.getCount(new ExtendedMultiKey(Wildcard.INTEGER(),ADULT_LABEL, Wildcard.DOUBLE()))+" in adults and "+
						ambiguousBMI.getCount(new ExtendedMultiKey(Wildcard.INTEGER(),PAEDIATRIC_LABEL, Wildcard.DOUBLE()))+" in children.\n" +
						"The measured BMI was kept.");
			
			//TODO decide if it is to be kept or not
			//						if (ambiguousBMI.getHistogram(new ExtendedMultiKey(1,Wildcard.STRING())) != null){
			//							Logging.add(Parameters.NEW_LINE+"Ambiguous BMIs: ");
			//							HistogramStats adultStats = ambiguousBMI.getHistogramStats(new ExtendedMultiKey(1,"ADULT"));
			//							HistogramStats childrenStats = ambiguousBMI.getHistogramStats(new ExtendedMultiKey(1,"CHILD"));
			//			
			//							Logging.add("Children: "+childrenStats.toString());
			//							Logging.add("Adults: "+adultStats.toString());
			//						}        

			Logging.addNewLine();
			
			if (faultyMeasurements.getCount(BMILabel+BMI_OUTLIER) != 0){
				Logging.add("There were "+faultyMeasurements.getCount(BMILabel+BMI_OUTLIER)+" BMI measurements (existent and/or calculated) lower than "+minBMIValue+" or higher than "+maxBMIValue);
				Logging.addNewLine();
			}
		}
		
		if (outputResults) {
			//output results to file
			String filename = StringUtilities.addSuffixToFileName(outputFileName, "_Frequency.csv", true);
			OutputBagStats freqOutput = new OutputBagStats(filename,Parameters.DATABASE_NAME);
			freqOutput.addHeaderCount("Database,Gender,Frequency");
			freqOutput.outputCount(BMICountsBagTable, new ExtendedMultiKey(Wildcard.STRING(),Wildcard.INTEGER()));
			freqOutput.close();

			OutputManager outputManager = new OutputManager();

			String BMIYearsCategoriesFilename = StringUtilities.addSuffixToFileName(outputFileName, "_YearCategories.csv", true);
			outputManager.addFile(BMIYearsCategoriesFilename);
			outputManager.writeln(BMIYearsCategoriesFilename, "Database,Gender,Year,BMI,Count,Percentage", false);
			for (String genderString : BMIYearsCategoriesBagTable.getKeyValuesAsString(0)) {
				for (String yearString : BMIYearsCategoriesBagTable.getKeyValuesAsString(1)) {
					int year = Integer.parseInt(yearString);
					long total = 0;
					for (String bmi : BMIYearsCategoriesBagTable.getKeyValuesAsString(2)) {
						total += BMIYearsCategoriesBagTable.getCount(new ExtendedMultiKey(genderString,year,bmi));
					}
					for (String bmi : BMIYearsCategoriesBagTable.getKeyValuesAsString(2)) {
						long patientCount = BMIYearsCategoriesBagTable.getCount(new ExtendedMultiKey(genderString,year,bmi));
						String percentage = total == 0 ? "0.00" : StringUtilities.format(((double)patientCount / (double)total) * 100);
						outputManager.writeln(BMIYearsCategoriesFilename, Parameters.DATABASE_NAME + "," +
								genderString + "," +
								yearString + "," +
								StringUtils.trim(bmi) + "," +
								patientCount + "," +
								percentage, true);
					}
				}
			}
			outputManager.closeFile(BMIYearsCategoriesFilename);

			String BMIAgeGroupCategoriesFilename = StringUtilities.addSuffixToFileName(outputFileName, "_AgeGroupCategories.csv", true);
			outputManager.addFile(BMIAgeGroupCategoriesFilename);
			outputManager.writeln(BMIAgeGroupCategoriesFilename, "Database,Gender,Agegroup,BMI,Count,Percentage", false);
			for (String genderString : BMIAgeGroupsCategoriesBagTable.getKeyValuesAsString(0)) {
				for (String ageGroup : BMIAgeGroupsCategoriesBagTable.getKeyValuesAsString(1)) {
					long total = 0;
					for (String bmi : BMIAgeGroupsCategoriesBagTable.getKeyValuesAsString(2)) {
						total += BMIAgeGroupsCategoriesBagTable.getCount(new ExtendedMultiKey(genderString,ageGroup,bmi));
					}
					for (String bmi : BMIAgeGroupsCategoriesBagTable.getKeyValuesAsString(2)) {
						long patientCount = BMIAgeGroupsCategoriesBagTable.getCount(new ExtendedMultiKey(genderString,ageGroup,bmi));
						String percentage = total == 0 ? "0.00" : StringUtilities.format(((double)patientCount / (double)total) * 100);
						outputManager.writeln(BMIAgeGroupCategoriesFilename, Parameters.DATABASE_NAME + "," +
								genderString + "," +
								ageGroup + "," +
								StringUtils.trim(bmi) + "," +
								patientCount + "," +
								percentage, true);
					}
				}
			}
			outputManager.closeFile(BMIAgeGroupCategoriesFilename);

			filename = StringUtilities.addSuffixToFileName(outputFileName, "_TimeBeforeFirst.csv", true);
			OutputBagStats timeBeforeOutput = new OutputBagStats(filename,Parameters.DATABASE_NAME);
			timeBeforeOutput.addHeaderCount("Database,Population,Gender,Frequency");
			timeBeforeOutput.outputCount(BMIPatientTimeBefore, new ExtendedMultiKey(Wildcard.STRING(),Wildcard.STRING(),Wildcard.INTEGER()));
			timeBeforeOutput.close();

			filename = StringUtilities.addSuffixToFileName(outputFileName, "_TimeAfterFirst.csv", true);
			OutputBagStats timeAfterOutput = new OutputBagStats(filename,Parameters.DATABASE_NAME);
			timeAfterOutput.addHeaderCount("Database,Population,Gender,Frequency");
			timeAfterOutput.outputCount(BMIPatientTimeAfter, new ExtendedMultiKey(Wildcard.STRING(),Wildcard.STRING(),Wildcard.INTEGER()));
			timeAfterOutput.close();

			filename = StringUtilities.addSuffixToFileName(outputFileName, "_FirstBMI.csv", true);
			OutputBagStats firstBMIOutput = new OutputBagStats(filename,Parameters.DATABASE_NAME);
			firstBMIOutput.addHeaderCount("Database,Gender,Agegroup");
			firstBMIOutput.outputCount(firstBMIBag, new ExtendedMultiKey(Wildcard.STRING(),Wildcard.STRING()));
			firstBMIOutput.close();

			filename = StringUtilities.addSuffixToFileName(outputFileName, "_LastBMI.csv", true);
			OutputBagStats lastBMIOutput = new OutputBagStats(filename,Parameters.DATABASE_NAME);
			lastBMIOutput.addHeaderCount("Database,Gender,Agegroup");
			lastBMIOutput.outputCount(lastBMIBag, new ExtendedMultiKey(Wildcard.STRING(),Wildcard.STRING()));
			lastBMIOutput.close();


			filename = StringUtilities.addSuffixToFileName(outputFileName, "_Stats.csv", true);
			OutputBagStats statsOutput = new OutputBagStats(filename,Parameters.DATABASE_NAME);
			statsOutput.addHeaderStats("Database,Gender,Age");
			statsOutput.outputStats(BMIBagTable, new ExtendedMultiKey(Wildcard.STRING(),Wildcard.INTEGER()));
			statsOutput.close();
		}

		if (!Jerboa.inConsoleMode && this.createGraphs)
				displayGraphs();
	}

	@Override
	public void addToOutputBuffer(Patient patient){
		if (intermediateFiles) {
			if (patient.getMeasurements() != null && patient.getMeasurements().size() > 0){
				for (Measurement m : patient.getMeasurements()){
					String s = patient.subset + "," + patient.ID + "," +
							DateUtilities.daysToDate(m.date) + "," + m.getType() + "," +
							m.getValue();
					addToOutputBuffer(s);
				}
			
			}
		}
	}
	
	@Override
	public void calcStats(){/*TODO if needed */}

	//SPECIFIC METHODS
	/**
	 * Adds a BMI or calculated BMI measurement in the following scenarios:
	 * 1. if either one is present it will be added,
	 * 2. if both are present on different days they are added.
	 * 3. if both are present on the same day the calculated is taken if it is not an outlier, 
	 * 	  otherwise the BMI measurement is added.
	 * 
	 * ReferenceBMI and calculatedBMI are set to null after the addition.
	 * 
	 * @param referenceBMI - the BMI measurement taken as the reference
	 * @param calculatedBMI - the calculated BMI measurement
	 * @return - 0 = no BMI is added; 1 = reference BMI is added; 2 = calculated BMI is added;   
	 */
	private int addBMIMeasurements(Measurement referenceBMI, Measurement calculatedBMI){
		
		//Logging.add("addBMIMeasurements( " + referenceBMI + " , " + calculatedBMI + " )");

		int valueAdded = 0;
		if ((referenceBMI!=null) || (calculatedBMI!=null)){

			//only reference BMI
			if ((referenceBMI != null) && (calculatedBMI == null)){
				if (addBMI(referenceBMI)) {
					lastBMI = referenceBMI;
					valueAdded = 1; 
				}
			}

			//only calculated BMI
			if ((referenceBMI == null) && (calculatedBMI != null)){
				if (addBMI(calculatedBMI)) {
					derivedMeasurements.add(calculatedBMI);
					lastBMI = calculatedBMI;
					valueAdded = 2;
				}	
			}
			
			//both on different days
			//TODO: check if this will ever happen because of run on date change - MG: it does; put breakpoint on first patient in IPCI.
			if ((referenceBMI != null) && (calculatedBMI != null) && referenceBMI.getDate()!=calculatedBMI.getDate()) {
				if (addBMI(referenceBMI)) {
					lastBMI = referenceBMI;
					valueAdded = 1; 
				}
				if (addBMI(calculatedBMI)) {
					derivedMeasurements.add(calculatedBMI);
					lastBMI = calculatedBMI;
					valueAdded = 2;
				}	
			}

			//both are present on same day
			if ((referenceBMI != null) && (calculatedBMI != null) && referenceBMI.getDate()==calculatedBMI.getDate()){
				//different values: choose the one that is possible
				if (!referenceBMI.getValue().equals(calculatedBMI.getValue())){
					if (addBMI(referenceBMI)) { 
						lastBMI = referenceBMI;
						valueAdded = 1;
					} else {
						if (addBMI(calculatedBMI)) {
							lastBMI = calculatedBMI;
							valueAdded = 2;
						}
					}
					ambiguousBMI.add(new ExtendedMultiKey(1,(isAdult(currentPatient,calculatedBMI.getDate()) ? ADULT_LABEL : PAEDIATRIC_LABEL),
							//should raise no error. value checked before
							Double.parseDouble(referenceBMI.getValue()) - Double.parseDouble(calculatedBMI.getValue())));
				} else{ //identical values: add only one	
					if (addBMI(referenceBMI)) {
						lastBMI = referenceBMI;
						valueAdded = 1; 
					}
				}
			}
		}
		
		return valueAdded;
	}
	
	/**
	 * Adds a BMI measurement to the Bag if it is not an 
	 * outlier as defined by minBMIValue and maxBMIValue.
	 * @param BMI - the BMI measurement
	 * @return - true if the value was not an outlier; false otherwise
	 */
	private boolean addBMI(Measurement BMI){		
		
		//Logging.add("addBMI( " + BMI + ")");
		
		double value = Double.valueOf(BMI.getValue());
		//add it to the bag (only if relevant)
		if (value >= minBMIValue && value <= maxBMIValue) {
			BMIBag.add(new ExtendedMultiKey((isAdult(currentPatient, BMI.getDate()) ? ADULT_LABEL : PAEDIATRIC_LABEL), (int)currentPatient.gender,
					BMI.getType(), (int)(currentPatient.getAgeAtDateInYears(BMI.getDate())),
					value));

			// Save Year, Age, BMI
			int age = currentPatient.getAgeAtDateInYears(BMI.getDate());
			AgeGroup ageGroup = ageGroupDefinition.getAgeGroups(age).get(0);
			int year = DateUtilities.getYearFromDays(BMI.getDate());
			List<Measurement> yearBMIList = null;
			if (!patientYearBMIMap.containsKey(year)) {
				yearBMIList = new ArrayList<Measurement>(); 
				patientYearBMIMap.put(year, yearBMIList);
			}
			else {
				yearBMIList = patientYearBMIMap.get(year);
			}
			yearBMIList.add(BMI);
			List<Measurement> ageGroupBMIList = null;
			if (!patientAgeGroupBMIMap.containsKey(ageGroup)) {
				ageGroupBMIList = new ArrayList<Measurement>(); 
				patientAgeGroupBMIMap.put(ageGroup, ageGroupBMIList);
			}
			else {
				ageGroupBMIList = patientAgeGroupBMIMap.get(ageGroup);
			}
			ageGroupBMIList.add(BMI);

			
			if (firstBMI){
				BMIPatientTimeBefore.add(new ExtendedMultiKey((age < adultAge ? PAEDIATRIC_LABEL : ADULT_LABEL), Patient.convertGender(currentPatient.gender), 
						(int)currentPatient.getPatientTimeBeforeDateInMonths(BMI.getDate())));
				BMIPatientTimeAfter.add(new ExtendedMultiKey((age < adultAge ? PAEDIATRIC_LABEL : ADULT_LABEL), Patient.convertGender(currentPatient.gender), 
						(int)currentPatient.getPatientTimeAfterDateInMonths(BMI.getDate())));
				firstBMI = false;

				//AgeGroup, BMI 
				//firstBMIBag.add(ageGroupDefinition.getAgeGroups(age).get(0).getLabel());
				firstBMIBag.add(new ExtendedMultiKey(Patient.convertGender(currentPatient.gender), ageGroup.getLabel()));
			}
			
			++nbBMIPerPatient;
			return true;
			
		} else {
			faultyMeasurements.add(BMILabel+BMI_OUTLIER);
			return false;
		}	
	}
	
	/**
	 * Returns the category of a BMI Value. 
	 * @param age - age in days
	 * @param value - BMI value
	 * @return - one of the three categories: NORMAL, OVERWEIGHT, OBESE
	 */
	private String getBMIValueCategory(int age, double value) {
		if (age/DateUtilities.daysPerYear >= adultAge) {
			return (value < 25 ? BMI_NORMAL : (value > 30 ? BMI_OBESE : BMI_OVERWEIGHT));
		}
		else {
			double percentile = lms.getPercentile((double)age/DateUtilities.daysPerMonth, 
				(currentPatient.gender == DataDefinition.FEMALE_GENDER ? false : true), value);  
		return (percentile <= LMSReference.getOverWeightPercentile() ? BMI_NORMAL : (percentile > LMSReference.getObesePercentile() ? BMI_OBESE : BMI_OVERWEIGHT));
		}
	}
	
	/**
	 * Returns true if the patient p is considered as adult at the date 
	 * of the measurement m based on the adultAge parameter.
	 * Note that it is assumed the adultAge parameter has a value of years and not days.
	 * @param p - the patient in cause
	 * @param date - the date of the measurement
	 * @return - true if the patient is adult at the time of the measurement; false otherwise.
	 */
	private boolean isAdult(Patient p, int date) {
		return (date - p.birthDate >= adultAge * DateUtilities.daysPerYear);
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
		if ((count = faultyMeasurements.getCount(measurementType+MEASUREMENT_OUTSIDE_PATIENT_TIME)) != 0)
			Logging.add(oneOrMany(count)+"outside patient time");
		if ((count = faultyMeasurements.getCount(measurementType+MEASUREMENT_BEFORE_BIRTH)) != 0)
			Logging.add(oneOrMany(count)+"discarded for occurring before patient birth");
		if ((count = faultyMeasurements.getCount(measurementType+MEASUREMENT_NO_VALUE)) != 0)
			Logging.add(oneOrMany(count)+"discarded for having no value");
		if ((count = faultyMeasurements.getCount(measurementType+MEASUREMENT_ZERO_VALUE)) != 0)
			Logging.add(oneOrMany(count)+"discarded for a value of zero");
		if ((count = faultyMeasurements.getCount(measurementType+MEASUREMENT_NEGATIVE_VALUE)) != 0)
			Logging.add(oneOrMany(count)+"discarded for a negative value");
		if ((count = faultyMeasurements.getCount(measurementType+MEASUREMENT_NON_NUMERIC)) != 0)
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
	
	//*********************************GRAPH GENERATION****************************//
	@Override
	public void displayGraphs() {

		Timer timer = new Timer();
		timer.start();
		Progress progress = new Progress();
		progress.init(24, "Creating BMI plots");
		Logging.addWithTimeStamp("Creating BMI graphs");

		//reference values females
		TreeMap<Object, Object> referenceFemales = new TreeMap<Object, Object>();
		RealMatrix values = lms.getPercentileMatrix(new double[]{LMSReference.getOverWeightPercentile(), LMSReference.getObesePercentile()}, LMSReference.getMinimumAge(),
				LMSReference.getMaximumAge(), 12, false);  
		referenceFemales.put("age", values.getColumn(0));
		referenceFemales.put(" Normal", values.getColumn(1));
		referenceFemales.put(" Overweight", values.getColumn(2));
		
		//reference values males
		TreeMap<Object, Object> referenceMales = new TreeMap<Object, Object>();
		values = lms.getPercentileMatrix(new double[]{LMSReference.getOverWeightPercentile(), LMSReference.getObesePercentile()}, LMSReference.getMinimumAge(),
				LMSReference.getMaximumAge(), 12, true);  
		referenceMales.put("age", values.getColumn(0));
		referenceMales.put(" Normal", values.getColumn(1));
		referenceMales.put(" Overweight", values.getColumn(2));
				
		if (viewerLabel.isEmpty())
			viewerLabel = "BMI Viewer";


		List<Plot> list = new ArrayList<Plot>();
		
		Logging.addWithTimeStamp("Pie Chart number of BMI measurements started");
		Plot plot = new PieChart.Builder("Pie Chart Number of BMI measurements").data(getBMICounts(BMICounts, pieChartUpperLimit))
						.showLegend(true).build();
		Graphs.addPlot(viewerLabel, "BMIs per patient", plot);progress.update();
		
		Logging.addWithTimeStamp("Histogram of number of BMI measurements started");
		plot = new BarPlotDS.Builder("Number of BMI measurements").data(BMICounts)
				.XLabel("Measurement Count").YLabel("Number of patients").build();
		Graphs.addPlot(viewerLabel, "BMIs per patient", plot);progress.update();
		
		//BMI overall and per adults and children
		Logging.addWithTimeStamp("Histogram BMI measurements started");
		
		list = new ArrayList<Plot>();
		plot = new BarPlotDS.Builder("Histogram of BMI values - total population")
			.data(BMIBag.getHistogram(new ExtendedMultiKey(Wildcard.STRING(), Wildcard.STRING(), Wildcard.INTEGER())))
			.XLabel("BMI (kg/m\u00B2)").YLabel("Number of measurements").build();
		list.add(plot);progress.update();
		
		plot = new BarPlotDS.Builder("Histogram of BMI values - children and adolescents")
			.data(BMIBag.getHistogram(new ExtendedMultiKey(PAEDIATRIC_LABEL, Wildcard.STRING(), Wildcard.INTEGER())))
			.XLabel("BMI (kg/m\u00B2)").YLabel("Number of measurements").build();
		list.add(plot);progress.update();
		
		plot = new BarPlotDS.Builder("Histogram of BMI values - adults")
			.data(BMIBag.getHistogram(new ExtendedMultiKey(ADULT_LABEL, Wildcard.STRING(), Wildcard.INTEGER())))
			.XLabel("BMI (kg/m\u00B2)").YLabel("Number of measurements").build();
		list.add(plot);progress.update();

		Graphs.addPlots(viewerLabel, "Histogram", list);

		Logging.addWithTimeStamp("Percentile plots started");

		//percentiles - whole population
		Graphs.addPlots(viewerLabel, "Percentiles", 
			getPercentilePlots(BMIPercentilesBag, null, "Percentiles", "Age", "BMI (kg/m\u00B2)"));
		progress.update();
			
		//percentiles - kids and adolescents
		Graphs.addPlots(viewerLabel, "Percentiles", 
				getPercentilePlots(BMIPercentilesBag, PAEDIATRIC_LABEL, "Percentiles children and adolescents", "Age", "BMI (kg/m\u00B2)"));
		progress.update();
		
		//percentiles - adults
		Graphs.addPlots(viewerLabel, "Percentiles", 
				getPercentilePlots(BMIPercentilesBag, ADULT_LABEL, "Percentiles adults", "Age", "BMI (kg/m\u00B2)"));
		progress.update();
		
		//BMI growth curve plot - only for kids for now
		int minBMI=12;
		int maxBMI=40;
		Logging.addWithTimeStamp(LMSReference.getName()+" BMI charts started");
		list = new ArrayList<Plot>();
		plot = new BMIPlot.Builder(LMSReference.getName()+" BMI chart - Girls").referenceData(referenceFemales)
				.percentiles(new int[]{5,25,50,75,95}).minRange(minBMI).maxRange(maxBMI)
				.data(getStatsMap(BMIPercentilesBag, DataDefinition.FEMALE_GENDER, PAEDIATRIC_LABEL, true))
				.XLabel("Age (months)").YLabel("BMI (kg/m\u00B2)").showLegend(true).build();
		list.add(plot);progress.update();
		plot = new BMIPlot.Builder(LMSReference.getName()+" BMI chart - Boys").referenceData(referenceMales)
				.percentiles(new int[]{5,25,50,75,95}).minRange(minBMI).maxRange(maxBMI)
				.data(getStatsMap(BMIPercentilesBag, DataDefinition.MALE_GENDER, PAEDIATRIC_LABEL, true))
				.XLabel("Age (months)").YLabel("BMI (kg/m\u00B2)").showLegend(true).build();
		list.add(plot);progress.update();
		Graphs.addPlots(viewerLabel, LMSReference.getName()+" BMI chart", list);
		
		//per year
		Logging.addWithTimeStamp(LMSReference.getName()+" year plots started");
		list = new ArrayList<Plot>();
		//all population
		TreeSet<String> categories = BMIYearsCategoriesBag.getKeyValuesAsString(0);
		TreeSet<Object> years = BMIYearsCategoriesBag.getKeyValuesAsObject(2);
		TreeMap<Object, Object> plotDataAll = new TreeMap<Object, Object>();
		TreeMap<Object, Object> plotDataChildren = new TreeMap<Object, Object>();
		TreeMap<Object, Object> plotDataAdults = new TreeMap<Object, Object>();
		for (String category : categories){
			TreeMap<Object, Object> yearDataAll = new TreeMap<Object, Object>();
			TreeMap<Object, Object> yearDataChildren = new TreeMap<Object, Object>();
			TreeMap<Object, Object> yearDataAdults = new TreeMap<Object, Object>();
			for (Object year : years){
				yearDataAll.put(year, BMIYearsCategoriesBag.getCount(new ExtendedMultiKey(category, Wildcard.STRING(), (int)year, Wildcard.BYTE())));
				yearDataChildren.put(year, BMIYearsCategoriesBag.getCount(new ExtendedMultiKey(category, PAEDIATRIC_LABEL, (int)year, Wildcard.BYTE())));
				yearDataAdults.put(year, BMIYearsCategoriesBag.getCount(new ExtendedMultiKey(category, ADULT_LABEL, (int)year, Wildcard.BYTE())));
			}
			plotDataAll.put(category, yearDataAll);
			plotDataChildren.put(category, yearDataChildren);
			plotDataAdults.put(category, yearDataAdults);
		}
		plot = new StackedBarPlot.Builder(LMSReference.getName()+" BMI categories - total population")
			.data(plotDataAll).XLabel("Calendar year").YLabel("Number of patients").showLegend(true).build();
		list.add(plot);progress.update();
		plot = new StackedBarPlot.Builder(LMSReference.getName()+" BMI categories - children and adolescents")
			.data(plotDataChildren).XLabel("Calendar year").YLabel("Number of patients").showLegend(true).build();
		list.add(plot);progress.update();
		plot = new StackedBarPlot.Builder(LMSReference.getName()+" BMI categories - adults")
			.data(plotDataAdults).XLabel("Calendar year").YLabel("Number of patients").showLegend(true).build();
		list.add(plot);progress.update();
		
		Graphs.addPlots(viewerLabel, LMSReference.getName()+" years", list);
			
		//PER AGE GROUP
		Logging.addWithTimeStamp(LMSReference.getName()+" agegroup plots started");
		list = new ArrayList<Plot>();
		categories = BMIAgeGroupsCategoriesBag.getKeyValuesAsString(0);
		TreeSet<Object> ageGroups = BMIAgeGroupsCategoriesBag.getKeyValuesAsObject(2);
		plotDataAll = new TreeMap<Object, Object>();
		plotDataChildren = new TreeMap<Object, Object>();
		plotDataAdults = new TreeMap<Object, Object>();
		for (String category : categories){
			TreeMap<Object, Object> ageGroupDataAll = new TreeMap<Object, Object>();
			TreeMap<Object, Object> ageGroupDataChildren = new TreeMap<Object, Object>();
			TreeMap<Object, Object> ageGroupDataAdults = new TreeMap<Object, Object>();
			for (Object ageGroup : ageGroups){
				String[] split = ageGroup.toString().split("-");
				if (split.length>1 && Double.parseDouble(split[0])<=adultAge){
					ageGroupDataChildren.put(ageGroup, BMIAgeGroupsCategoriesBag.getCount(new ExtendedMultiKey(category, PAEDIATRIC_LABEL,  ageGroup,  Wildcard.BYTE())));
				} else
					ageGroupDataAdults.put(ageGroup, BMIAgeGroupsCategoriesBag.getCount(new ExtendedMultiKey(category, ADULT_LABEL,  ageGroup,   Wildcard.BYTE())));
				ageGroupDataAll.put(ageGroup,BMIAgeGroupsCategoriesBag.getCount(new ExtendedMultiKey(category, Wildcard.STRING(), ageGroup, Wildcard.BYTE())));
			}
			plotDataAll.put(category, ageGroupDataAll);
			plotDataChildren.put(category, ageGroupDataChildren);
			plotDataAdults.put(category, ageGroupDataAdults);
		}
		plot = new StackedBarPlot.Builder(LMSReference.getName()+" BMI categories - all population").data(plotDataAll).XLabel("Age group").YLabel("Number of patients").showLegend(true).build();
		list.add(plot);progress.update();
		plot = new StackedBarPlot.Builder(LMSReference.getName()+" BMI categories - children and adolescents").data(plotDataChildren).XLabel("Age group").YLabel("Number of patients").showLegend(true).build();
		list.add(plot);progress.update();
		plot = new StackedBarPlot.Builder(LMSReference.getName()+" BMI categories - adults").data(plotDataAdults).XLabel("Age group").YLabel("Number of patients").showLegend(true).build();
		list.add(plot);progress.update();
		
		Graphs.addPlots(viewerLabel,LMSReference.getName()+" age groups", list);
			
		//patient time before first BMI
		Logging.addWithTimeStamp("Histogram patient time before first BMI started");
		list = new ArrayList<Plot>();
		plot = new BarPlotDS.Builder("Patient time before first BMI - total population")
				.data(BMIPatientTimeBefore.getHistogram(new ExtendedMultiKey(Wildcard.STRING(), Wildcard.BYTE())))
				.XLabel("Patient time (months)").YLabel("Number of patients").build();
		list.add(plot);progress.update();

		plot = new BarPlotDS.Builder("Patient time before first BMI - children and adolescents")
				.data(BMIPatientTimeBefore.getHistogram(new ExtendedMultiKey(PAEDIATRIC_LABEL, Wildcard.BYTE())))
				.XLabel("Patient time (months)").YLabel("Number of patients").build();
		list.add(plot);progress.update();
		
		plot = new BarPlotDS.Builder("Patient time before first BMI - adults")
		.data(BMIPatientTimeBefore.getHistogram(new ExtendedMultiKey(ADULT_LABEL, Wildcard.BYTE())))
		.XLabel("Patient time (months)").YLabel("Number of patients").build();
		list.add(plot);progress.update();
		
		Graphs.addPlots(viewerLabel,"Patient time before first BMI", list);
		
		//patient time after
		Logging.addWithTimeStamp("Histogram patient time after first BMI started");
		list = new ArrayList<Plot>();
		plot = new BarPlotDS.Builder("Patient time after first BMI - total population")
		.data(BMIPatientTimeAfter.getHistogram(new ExtendedMultiKey(Wildcard.STRING(), Wildcard.BYTE())))
		.XLabel("Patient time (months)").YLabel("Number of patients").build();
		list.add(plot);progress.update();	
		
		plot = new BarPlotDS.Builder("Patient time after first BMI - children and adolescents")
				.data(BMIPatientTimeAfter.getHistogram(new ExtendedMultiKey(PAEDIATRIC_LABEL, Wildcard.BYTE())))
				.XLabel("Patient time (months)").YLabel("Number of patients").build();
		list.add(plot);progress.update();
		
		plot = new BarPlotDS.Builder("Patient time after first BMI - adults")
		.data(BMIPatientTimeAfter.getHistogram(new ExtendedMultiKey(ADULT_LABEL, Wildcard.BYTE())))
		.XLabel("Patient time (months)").YLabel("Number of patients").build();
		list.add(plot);progress.update();
			
		Graphs.addPlots(viewerLabel, "Patient time after first BMI", list);
			
		//patient first BMI
		Logging.addWithTimeStamp("Histogram agegroup first BMI started");
		
		categories = new TreeSet<String>();
		categories.add("First BMI");
		ageGroups = firstBMIBag.getKeyValuesAsObject(1);
		TreeMap<Object, Object> firstBMIData = new TreeMap<Object, Object>();
		for (String category : categories){
			TreeMap<Object, Object> ageGroupBMIData = new TreeMap<Object, Object>();
			for (Object ageGroup : ageGroups){
				ageGroupBMIData.put(ageGroup, firstBMIBag.getCount(new ExtendedMultiKey(Wildcard.STRING(), ageGroup)));
			}
			firstBMIData.put(category, ageGroupBMIData);
		}
		plot = new StackedBarPlot.Builder("First BMI").data(firstBMIData).XLabel("Age group").YLabel("Number of patients").showLegend(true).build();
		Graphs.addPlot(viewerLabel, "First BMI", plot);
		progress.update();
		
		//patient last BMI
		Logging.addWithTimeStamp("Histogram agegroup last BMI started");
		list = new ArrayList<Plot>();
		categories = new TreeSet<String>();
		categories.add("Last BMI");
		ageGroups = lastBMIBag.getKeyValuesAsObject(1);
		TreeMap<Object, Object> lastBMIData = new TreeMap<Object, Object>();
		for (String category : categories){
			TreeMap<Object, Object> ageGroupBMIData = new TreeMap<Object, Object>();
			for (Object ageGroup : ageGroups){
				ageGroupBMIData.put(ageGroup, lastBMIBag.getCount(new ExtendedMultiKey(Wildcard.STRING(), ageGroup)));
			}
			lastBMIData.put(category, ageGroupBMIData);
		}
		plot = new StackedBarPlot.Builder("Last BMI").data(lastBMIData).XLabel("Age group").YLabel("Number of patients").showLegend(true).build();
		Graphs.addPlot(viewerLabel, "Last BMI", plot);
		progress.update();
		
		
		//display execution timers
		timer.stop();
		timer.displayTotal("BMI graphs done in");
		progress.close();
		
		
	}
	
	//**************************************SPECIFIC METHODS**************************************//
	/**
	 * Creates a list of line plots representing the data distribution
	 * for the total population and per gender.
	 * @param bag - the bag containing the data
	 * @param ageCategory - one of the two: CHILD or ADULT; if left null a wild card is used
	 * @param title - the title of the plot
	 * @param XLabel - the X axis label
	 * @param YLabel - the Y axis label
	 * @return - the list of percentile plots
	 */
	private List<Plot> getPercentilePlots(MultiKeyBag bag, String ageCategory, String title, String XLabel, String YLabel){
		List<Plot> list = new ArrayList<Plot>();
		if (bag != null){
			list.add(createPercentilePlot(bag, DataDefinition.INVALID_GENDER, ageCategory, title + " - Total population", XLabel, YLabel));
			list.add(createPercentilePlot(bag, DataDefinition.FEMALE_GENDER, ageCategory, title + " - Female population", XLabel, YLabel));
			list.add(createPercentilePlot(bag, DataDefinition.MALE_GENDER, ageCategory, title + " - Male population", XLabel, YLabel));
		}
		
		return list;
	}
	
	/**
	 * Creates a line plot with different series for the first,
	 * second, third quartile and the median of the data.
	 * @param bag - the bag containing the data
	 * @param gender - the gender of the patients of interest
	 * @param ageCategory - one of the two: CHILD or ADULT; if left null a wild card is used
	 * @param title - the title of the plot
	 * @param XLabel - the X axis label
	 * @param YLabel - the Y axis label
	 * @return - the distribution plot
	 */
	private Plot createPercentilePlot(MultiKeyBag bag, byte gender, String ageCategory, String title, String XLabel, String YLabel){
		TreeMap<Object, Object> statsMap = getStatsMap(bag, gender, ageCategory, false);
		Plot plot = new LinePlotDS.Builder(title).XLabel(XLabel).YLabel(YLabel).showLegend(true).build();
		plot.addSeriesToDataset(getPercentile(statsMap, 25), "1st quartile");
		plot.addSeriesToDataset(getPercentile(statsMap, 50), "Median");
		plot.addSeriesToDataset(getPercentile(statsMap, 999), "Mean");
		plot.addSeriesToDataset(getPercentile(statsMap, 75), "3rd quartile");
		
		return plot;
	}
	
	/**
	 * Retrieves the value of a certain percentile from a HistogramStats object
	 * and replaces it in the map containing HistogramStats object. 
	 * @param stats - the map of HistogramStats objects
	 * @param percentile - the percentile of interest
	 * @return - a sorted map of percentile values per calendar year
	 */
	private TreeMap<Object, Object> getPercentile(TreeMap<Object, Object> stats, int percentile){
		TreeMap<Object, Object> map = new TreeMap<Object, Object>();
		for (Object year : stats.keySet()){
			double value = 	(percentile == 999 ? ((HistogramStats)stats.get(year)).getMean() 
						: ((HistogramStats)stats.get(year)).getPercentile(percentile));
				map.put((int)year, value);					
		}
		
		return map;
	}

	/**
	 * Creates a map of HistogramStats objects per calendar year from the bag per year.
	 * @param bag - the data
	 * @param gender - the gender for which the statistics are to be computed
	 * @param ageCategory - one of the two: CHILD or ADULT; if left null a wild card is used
	 * @param ageInMonths - true if the age should be displayed in number of months; false if it is to be displayed in years.
	 * @return - a sorted list (by year) of summary statistics
	 */
	private TreeMap<Object, Object> getStatsMap(MultiKeyBag bag, byte gender, String ageCategory, boolean ageInMonths){
		TreeMap<Object, Object> stats = new TreeMap<Object, Object>();
		TreeSet<ExtendedMultiKey> keySet = bag.getSortedKeySet();
		if (keySet != null && keySet.size() > 0){
			SortedSet<Object> ages = bag.getKeyValuesAsObject(3);
			if (ages != null && ages.size() > 0){
				if (ageCategory != null){
					if (ageCategory.equals(PAEDIATRIC_LABEL) && (int)ages.first() <= (adultAge - 1))
						ages = ages.subSet(((int)ages.first() < minAge ? minAge : ages.first()), (adultAge));
					else if (ageCategory.equals(ADULT_LABEL))
						ages = ages.tailSet(adultAge);
				}
				for (Object age : ages){
					//the gender is put as integer in the bag. why ?
					if (bag.getCount(new ExtendedMultiKey((ageCategory == null ? Wildcard.STRING() : ageCategory),
							(gender != -1 ? (int)gender : Wildcard.INTEGER()), Wildcard.STRING(), age, Wildcard.DOUBLE())) >= percentilesMinimumCount){
						HistogramStats hs = new HistogramStats(bag.getHistogramRec
								(new ExtendedMultiKey((ageCategory != null ? ageCategory : Wildcard.STRING()), 
										(gender != -1 ? Integer.valueOf(gender) : Wildcard.INTEGER()),  Wildcard.STRING(),(int)age)));
					stats.put(ageInMonths ? (int)age * (int)DateUtilities.monthsPerYear : (int)age, hs);					
					}
				}
			}
		}
		
		return stats;
	}
	
	/**
	 * Creates a sorted map from a hash bag containing the number of BMI measurements of patients.
	 * The values of the entries containing the counts for the number of measurements that are superior to 
	 * the aggregationValue threshold (e.g., patients having more than 5 BMI measurements) are aggregated into one count.
	 * @param bmiCounts - the hash bag containing the frequency of the number of BMI measurements
	 * @param aggregationValue - the threshold for aggregating the counts
	 * @return - a sorted map with the counts of the number of BMI measurements
	 */
	private TreeMap<Object, Object> getBMICounts(HashBag bmiCounts, int aggregationValue){
		TreeMap<Object, Object> map = null;
		if (bmiCounts != null && bmiCounts.size() > 0){
			map = new TreeMap<Object, Object>();
			Object[] keySet = bmiCounts.uniqueSet().toArray();
			Arrays.sort(keySet);
			int aggregatedCount = 0;
			for (Object key : keySet){
				if ((int)key <= aggregationValue)
					map.put(key.toString(), (Number)(bmiCounts.getCount(key)));
				else
					aggregatedCount += (int)bmiCounts.getCount(key);
			}
			
			if (aggregatedCount != 0)
				map.put("> "+aggregationValue, aggregatedCount);
		}
		
		return map;
	}
	
	//MAIN METHOD USED FOR TESTING AND/OR DEBUGGING PURPOSES
	public static void main(String[] args) {

		PatientObjectCreator poc = new PatientObjectCreator();
		
		String testDataPath = "/Users/Rijnbeek/Desktop/BMI/";
		FilePaths.WORKFLOW_PATH = "/Users/Rijnbeek/Desktop/BMI/Debug/";
		FilePaths.LOG_PATH = FilePaths.WORKFLOW_PATH + "Log/";
		String patientsFile = testDataPath + "IPCI_Patients_clean.csv";
		String prescriptionsFile = testDataPath + "IPCI_BMI_clean.csv";
		Logging.prepareOutputLog();

		BMICalculation BMICalc = new BMICalculation();

		BMICalc.weightLabel = "WEIGHT";
		BMICalc.heightLabel = "HEIGHT";
		BMICalc.BMILabel = "BMI";
		BMICalc.calcBMILabel = "BMI";
		BMICalc.adultAge = 20;
		BMICalc.maxTimeSpan = 365;
		BMICalc.maxTimeSpanChildren = 30;
		BMICalc.minBMIValue = 10;
		BMICalc.maxBMIValue = 60;
		BMICalc.replaceMeasurements = true;
		BMICalc.useMeasurementsOutsidePatientTime = true;
		BMICalc.intermediateFiles = true;
		BMICalc.intermediateFileName = FilePaths.WORKFLOW_PATH + "BMICalculation.csv";
		BMICalc.intermediateStats = true;
		BMICalc.createGraphs = false;
		BMICalc.ageGroups.add("0;5;00-04");
		BMICalc.ageGroups.add("5;10;05-09");
		BMICalc.ageGroups.add("10;15;10-14");
		BMICalc.ageGroups.add("15;20;15-19");
		BMICalc.ageGroups.add("20;25;20-24");
		BMICalc.ageGroups.add("25;30;25-29");
		BMICalc.ageGroups.add("30;35;30-34");
		BMICalc.ageGroups.add("35;40;35-39");
		BMICalc.ageGroups.add("40;45;40-44");
		BMICalc.ageGroups.add("45;50;45-49");
		BMICalc.ageGroups.add("50;55;50-54");
		BMICalc.ageGroups.add("55;60;55-59");
		BMICalc.ageGroups.add("60;65;60-64");
		BMICalc.ageGroups.add("65;70;65-69");
		BMICalc.ageGroups.add("70;75;70-74");
		BMICalc.ageGroups.add("75;80;75-79");
		BMICalc.ageGroups.add("80;85;80-84");
		BMICalc.ageGroups.add("85;999;85-");
		BMICalc.populationSubgroups.add("Children;00-04 years");
		BMICalc.populationSubgroups.add("Children;05-09");
		BMICalc.populationSubgroups.add("Children;10-14");
		BMICalc.populationSubgroups.add("Adults;15-19");
		BMICalc.populationSubgroups.add("Adults;20-24");
		BMICalc.populationSubgroups.add("Adults;25-29");
		BMICalc.populationSubgroups.add("Adults;30-34");
		BMICalc.populationSubgroups.add("Adults;35-39");
		BMICalc.populationSubgroups.add("Adults;40-44");
		BMICalc.populationSubgroups.add("Adults;45-49");
		BMICalc.populationSubgroups.add("Adults;50-54");
		BMICalc.populationSubgroups.add("Adults;55-59");
		BMICalc.populationSubgroups.add("Adults;60-64");
		BMICalc.populationSubgroups.add("Adults;65-69");
		BMICalc.populationSubgroups.add("Adults;70-74");
		BMICalc.populationSubgroups.add("Adults;75-79");
		BMICalc.populationSubgroups.add("Adults;80-84");
		BMICalc.populationSubgroups.add("Adults;85-");
		BMICalc.outputFileName = FilePaths.WORKFLOW_PATH + "BMI";

		if (BMICalc.init()) {

			try{
				List<Patient> patients = poc.createPatients(patientsFile, null, prescriptionsFile, null);
			//	new PatientViewer(patients,null,"Before BMI Calculation");
				if (patients != null && patients.size() > 0){

					Timer timer = new Timer();
					timer.start();				


					for (Patient patient : patients){
						patient = BMICalc.process(patient);
					}				


					timer.stop();

					// Output

					BMICalc.outputResults();

					System.out.println("BMI Calculation run in: "+timer);
				}
			//new PatientViewer(patients,null,"After BMI Calculation");
			} catch(IOException e){
				System.out.println("Error while opening input files");
			}
		}
	}

}
