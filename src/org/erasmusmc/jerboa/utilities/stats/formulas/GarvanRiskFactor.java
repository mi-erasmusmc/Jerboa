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
 * $Rev:: 4544              $:  Revision of last commit                                   *
 * $Author:: root           $:  Author of last commit                                     *
 * $Date:: 2013-09-24 16:26#$:  Date and time (CET) of last commit						  *
 ******************************************************************************************/
package org.erasmusmc.jerboa.utilities.stats.formulas;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.TreeMap;
import java.util.TreeSet;

import org.erasmusmc.jerboa.config.DataDefinition;
import org.erasmusmc.jerboa.config.FilePaths;
import org.erasmusmc.jerboa.dataClasses.Event;
import org.erasmusmc.jerboa.dataClasses.Measurement;
import org.erasmusmc.jerboa.dataClasses.Patient;
import org.erasmusmc.jerboa.engine.PatientObjectCreator;
import org.erasmusmc.jerboa.utilities.DateUtilities;
import org.erasmusmc.jerboa.utilities.InputFileUtilities;
import org.erasmusmc.jerboa.utilities.Logging;
import org.erasmusmc.jerboa.utilities.StringUtilities;

/**
 * This class contains all the necessary methods and reference values used in the
 * calculation of the Garvan risk factor for fractures. The risk can be estimated
 * for a period of five years or for a period of ten years, for hip fractures only
 * or for any fracture, separately for females and males. Two different models can
 * be used for the risk estimation depending on the available data: either the model
 * using Bone Mineral Density measurements or the model using the weight of the patient.
 *
 * @author MG
 *
 */
public class GarvanRiskFactor {

	//risk estimation period
	public static final int FIVE_YEARS = 5;
	public static final int TEN_YEARS = 10;

	//risk calculation model
	public static boolean BMD_MODEL;

	//unable to calculate
	public static final String NO_RISK_CALCULATED = "";

	//the operators used in the formula
	private static Double bmd = Double.MIN_VALUE;
	private static Double weight = Double.MIN_VALUE;
	private static int Pfx = 0;
	private static int nbFalls = 0;

	//keeps a list of dates from the events sorted from newest to oldest
	private static TreeSet<Integer> falls = new TreeSet<Integer>(Collections.reverseOrder());
	private static TreeSet<Integer> fractures = new TreeSet<Integer>(Collections.reverseOrder());
	//keeps a list of measurements ordered from newest to oldest
	private static TreeMap<Integer, Measurement> bmdMeasurements = new TreeMap<Integer, Measurement>(Collections.reverseOrder());
	private static TreeMap<Integer, Measurement> weightMeasurements = new TreeMap<Integer, Measurement>(Collections.reverseOrder());

	//patient details
	private static byte PATIENT_GENDER = DataDefinition.INVALID_GENDER;
	private static int PATIENT_BIRTH_DATE = Integer.MIN_VALUE;
	private static int PATIENT_50_BIRTHDAY = Integer.MIN_VALUE;
	public static final double NO_RESULT = -1;

	//the labels of the events or measurements of interest (indexes from look-ups)
	private static List<Integer> bmdIndexes = new ArrayList<Integer>();
	private static List<Integer> weightIndexes = new ArrayList<Integer>();
	private static List<Integer> fallIndexes = new ArrayList<Integer>();
	private static List<Integer> fractureIndexes = new ArrayList<Integer>();

	private static Integer bmdIndex = null;
	private static Integer weightIndex = null;
	private static Integer fallIndex = null;
	private static Integer fractureIndex = null;

	//keeps the measurements used in the calculation
	private static Measurement bmdMeasurement = null;
	private static Measurement weightMeasurement = null;

	//keeps the difference (in days) between the index date and the measurement used
	private static Integer indexDateToWeight = null;
	private static Integer indexDateToBMD = null;

	/**
	 * Will populate the list of falls and retrieve the latest BMD and weight measurements
	 * for this patient based on the labels of the event(s) and measurement(s) of interest
	 * defined by the user and passed as arguments via the constructor of this object.
	 * @param patient - the patient to be processed and its events extracted
	 */
	public static void init(Patient patient){

		clear();

		PATIENT_GENDER = patient.gender;
		PATIENT_50_BIRTHDAY = patient.getBirthdayInYear(patient.getBirthYear()+50);
		PATIENT_BIRTH_DATE = patient.birthDate;

		//process events
		if (patient.hasEvents()){
			for (Event e : patient.getEvents()){
				if ((fallIndexes != null && fallIndexes.indexOf(e.type) != -1)
						|| (fallIndex != null && e.type == fallIndex))
					falls.add(e.date);
				//get only fractures that occurred after 50 years of age (inclusive)
				if (((fractureIndexes != null && fractureIndexes.indexOf(e.type) != -1)
						|| (fractureIndex != null && e.type == fractureIndex))
							&& e.date >= PATIENT_50_BIRTHDAY)
					fractures.add(e.date);
			}
		}

		//process measurements
		if (patient.hasMeasurements()){
			//sort measurements from newest to oldest and keep the most recent for each type of interest
			Collections.sort(patient.getMeasurements(), Collections.reverseOrder());
			for (Measurement m : patient.getMeasurements()){
				//check if numeric value - if not discard it already
				try{
					Double.valueOf(m.getValue());
				}catch(NumberFormatException e){
					continue;
				}
				//check if measurement of interest
				if ((bmdIndexes != null && bmdIndexes.indexOf(m.type) != -1)
						|| (bmdIndex != null && m.type == bmdIndex)){
						bmdMeasurements.put(m.date, m);
				}else if ((weightIndexes != null && weightIndexes.indexOf(m.type) != -1)
						|| (weightIndex != null && m.type == weightIndex)){
						weightMeasurements.put(m.date, m);
				}
			}
			Collections.sort(patient.getMeasurements());
		}

	}

	/**
	 * Calculates and returns the Garvan risk for fractures as a percentage.
	 * The risk estimation is for a specific period (either 5 or 10 years)
	 * at a certain indexDate, which can be included or excluded depending on
	 * the inclusive flag settings and it can be estimated for hip fractures only or
	 * for any kind of fractures, using the BMD model for calculation or the weight model.
	 * If there are no measurements available at index date, then the ones prior to it are considered (if any).
	 * @param period - the period for the estimated risk (5 or 10 years)
	 * @param indexDate - the date at which the risk is to be estimated
	 * @param inclusive - true if the indexDate itself should be included; false otherwise
	 * @param hipFracture - true if the risk should be calculated only for hip fractures; false otherwise
	 * @param bmdModel - true if the BMD model should be used; false if the weight model should be used
	 * @return - the Garvan estimated risk factor for fractures
	 */
	public static String calculateRisk(int period, int indexDate, boolean inclusive, boolean hipFracture, boolean bmdModel){
		return calculateRisk(period, indexDate, -1, inclusive, hipFracture, bmdModel);
	}

	/**
	 * Calculates and returns the Garvan risk for fractures as a percentage.
	 * The risk estimation is for a specific period (either 5 or 10 years)
	 * at a certain indexDate, which can be included or excluded depending on
	 * the inclusive flag settings and it can be estimated for hip fractures only or
	 * for any kind of fractures, using the BMD model for calculation or the weight model.
	 * If there are no measurements available at index date, then the ones prior to it are considered (if any).
	 * @param period - the period for the estimated risk (5 or 10 years)
	 * @param indexDate - the date at which the risk is to be estimated
	 * @param indexPeriod - the period before the indexDate in which the measurement should be or -1 if any measurement is ok.
	 * @param inclusive - true if the indexDate itself should be included; false otherwise
	 * @param hipFracture - true if the risk should be calculated only for hip fractures; false otherwise
	 * @param bmdModel - true if the BMD model should be used; false if the weight model should be used
	 * @return - the Garvan estimated risk factor for fractures
	 */
	public static String calculateRisk(int period, int indexDate, int indexPeriod, boolean inclusive, boolean hipFracture, boolean bmdModel){

		//NOTE: maps are reversed so the use of lowerEntry and higherEntry should be reversed
		bmdMeasurement = (bmdMeasurements.get(indexDate) != null ?
				bmdMeasurements.get(indexDate) : (((bmdMeasurements.higherEntry(indexDate) != null) && ((indexPeriod == -1) || ((indexDate - bmdMeasurements.higherEntry(indexDate).getValue().getDate()) <= indexPeriod))) ?
				bmdMeasurements.higherEntry(indexDate).getValue() : null));
		weightMeasurement = (weightMeasurements.get(indexDate) != null ?
				weightMeasurements.get(indexDate) : (((weightMeasurements.higherEntry(indexDate) != null) && ((indexPeriod == -1) || ((indexDate - weightMeasurements.higherEntry(indexDate).getValue().getDate()) <= indexPeriod))) ?
				weightMeasurements.higherEntry(indexDate).getValue() : null));

		//get the values of the measurements
		bmd = bmdMeasurement != null ? Double.valueOf(bmdMeasurement.getValue()) : null;
		weight = weightMeasurement != null ? Double.valueOf(weightMeasurement.getValue()) : null;

		//keep the time difference between index date and measurement used
		indexDateToBMD = bmdMeasurement != null ? (indexDate - bmdMeasurement.date) : null;
		indexDateToWeight = weightMeasurement != null ? (indexDate - weightMeasurement.date) : null;

		//we do not have enough data at indexDate to estimate the risk
		if ((bmdModel && bmd == null) || (!bmdModel && weight == null))
			return NO_RISK_CALCULATED;

		//get number of fractures above 50 years of age and make sure it is between 0 and 3
		if (indexDate >= PATIENT_50_BIRTHDAY){
			Pfx = (fractures.tailSet(indexDate, inclusive) != null ?
					(fractures.tailSet(indexDate, inclusive).size()) : 0);
		}

		int age = getAgeAtDateInYears(indexDate, PATIENT_BIRTH_DATE);

		//get all falls before the index date
		TreeSet<Integer> subset = new TreeSet<Integer>(falls.tailSet(indexDate, inclusive));
		//subtract a year to the index date
		int[] dateComponents = DateUtilities.daysToDateComponents(indexDate);
		dateComponents[0] = --dateComponents[0];
		indexDate = DateUtilities.correctForLeapDay(dateComponents);

		//get number of falls in last year
		nbFalls = (subset.headSet(indexDate, inclusive) != null ?
				subset.headSet(indexDate, inclusive).size() : 0);

		return getRiskFactorAsPercentage(period, PATIENT_GENDER, age, bmdModel, hipFracture);

	}

	/**
	 * Clear all the components used in the formula.
	 */
	private static void clear(){

		bmd = Double.MIN_VALUE;
		weight = Double.MIN_VALUE;
		Pfx = 0;
		nbFalls = 0;

		falls.clear();
		fractures.clear();
		bmdMeasurements.clear();
		weightMeasurements.clear();

		bmdMeasurement = null;
		weightMeasurement = null;

		indexDateToWeight = null;
		indexDateToBMD = null;
	}

	//GETTERS AND SETTERS
	/**
	 * Returns the Garvan risk factor for a certain estimationPeriod based on the
	 * gender of the patient, its age, BMD value or weight for hip fractures or any
	 * fracture. The user should specify which model to be used (one of the
	 * two: either using BMD measurements or weight measurements).
	 * @param estimationPeriod - either 5 or 10 years estimated risk
	 * @param gender - the gender of the patient
	 * @param age - the age of the patient
	 * @param bmdModel - true if the BMD based model should be used; false if the weight model should be used
	 * @param hipFracture - true if the estimated risk is for hip fractures; false if the risk estimation should be for any fracture
	 * @return - the estimated Garvan risk for fractures as a percentage
	 */
	public static double getRiskFactor(int estimationPeriod, byte gender, int age, boolean bmdModel, boolean hipFracture){

		//generate linear function
		double[] coeffs = bmdModel ? CoefficientsBMD.get(gender, hipFracture) :
				CoefficientsWeight.get(gender, hipFracture);
		double gamma =
				coeffs[0] * age +
				coeffs[1] * (bmdModel ? bmd : weight) +
				coeffs[2] * Math.min(3,Pfx) +
				coeffs[3] * Math.min(3,nbFalls);

		//retrieve the baseline probability
		double prob = BaselineProbability.get(estimationPeriod, gender, hipFracture);

		return 1 - Math.pow(prob, Math.exp(gamma));
	}

	/**
	 * Returns the Garvan risk factor as a percentage for a certain estimationPeriod based on the
	 * gender of the patient, its age, BMD value or weight for hip fractures or any
	 * fracture. The user should specify which model to be used (one of the
	 * two: either using BMD measurements or weight measurements).
	 * @param estimationPeriod - either 5 or 10 years estimated risk
	 * @param gender - the gender of the patient
	 * @param age - the age of the patient
	 * @param bmdModel - true if the BMD based model should be used; false if the weight model should be used
	 * @param hipFracture - true if the estimated risk is for hip fractures; false if the risk estimation should be for any fracture
	 * @return - the estimated Garvan risk for fractures as a percentage with two decimals as precision
	 */
	public static String getRiskFactorAsPercentage(int estimationPeriod, byte gender, int age, boolean bmdModel, boolean hipFracture){
		return StringUtilities.format(getRiskFactor(estimationPeriod, gender, age, bmdModel, hipFracture) * 100);
	}

	/**
	 * Returns the Garvan risk factor as a percentage for a certain estimationPeriod based on the
	 * gender of the patient, its age, BMD value or weight for hip fractures or any
	 * fracture. The user should specify which model to be used (one of the
	 * two: either using BMD measurements or weight measurements).
	 * @param estimationPeriod - either 5 or 10 years estimated risk
	 * @param gender - the gender of the patient
	 * @param age - the age of the patient
	 * @param bmdOrWeight - the T-score or the weight of the patient
	 * @param nbFractures - the number of fractures from age 50 on
	 * @param nbFalls - the number of falls in the last 12 months
	 * @param bmdModel - true if the BMD based model should be used; false if the weight model should be used
	 * @param hipFracture - true if the estimated risk is for hip fractures; false if the risk estimation should be for any fracture
	 * @return - the estimated Garvan risk for fractures
	 */
	public static double getRiskFactor(int estimationPeriod, byte gender, int age, double bmdOrWeight, int nbFractures, int nbFalls, boolean bmdModel, boolean hipFracture){

		//generate linear function
		double[] coeffs = bmdModel ? CoefficientsBMD.get(gender, hipFracture) :
				CoefficientsWeight.get(gender, hipFracture);
		double gamma =
				coeffs[0] * age +
			    coeffs[1] * bmdOrWeight +
			    coeffs[2] * Math.min(3,nbFractures) +
			    coeffs[3] * Math.min(3,nbFalls);

		//retrieve the baseline probability
		double prob = BaselineProbability.get(estimationPeriod, gender, hipFracture);

		return 1 - Math.pow(prob, Math.exp(gamma));
	}

	/**
	 * Returns the Garvan risk factor as a percentage for a certain estimationPeriod based on the
	 * gender of the patient, its age, BMD value or weight for hip fractures or any
	 * fracture. The user should specify which model to be used (one of the
	 * two: either using BMD measurements or weight measurements).
	 * @param estimationPeriod - either 5 or 10 years estimated risk
	 * @param gender - the gender of the patient
	 * @param age - the age of the patient
	 * @param bmdOrWeight - the T-score or the weight of the patient
	 * @param nbFractures - the number of fractures from age 50 on
	 * @param nbFalls - the number of falls in the last 12 months
	 * @param bmdModel - true if the BMD based model should be used; false if the weight model should be used
	 * @param hipFracture - true if the estimated risk is for hip fractures; false if the risk estimation should be for any fracture
	 * @return - the estimated Garvan risk for fractures as a percentage with two decimals precision
	 */
	public static String getRiskFactorAsPercentage(int estimationPeriod, byte gender, int age, double bmdOrWeight, int nbFractures, int nbFalls, boolean bmdModel, boolean hipFracture){
		return StringUtilities.format(getRiskFactor(estimationPeriod, gender, age, bmdOrWeight, nbFractures, nbFalls, bmdModel, hipFracture) * 100);
	}

	/**
	 * Returns the age of a patient in years at a certain date.
	 * It takes into consideration if date is prior or post the birthday celebration.
	 * @param date - the date of interest
	 * @param birthDate - the date of birth of the patient
	 * @return - the age of this patient in years;
	 * if negative, -1 is returned
	 *
	 * COPIED FROM THE PATIENT OBJECT TO AVOID CARRYING A PATIENT AROUND
	 */
	public static int getAgeAtDateInYears(int date, int birthDate){

		//split the birth date and date into components
		int[] birthDateComponents = DateUtilities.daysToDateComponents(birthDate);
		int[] dateComponents = DateUtilities.daysToDateComponents(date);

		//get the number of years
		int age = dateComponents[0] - birthDateComponents[0];
		//check if the month from date is passed the celebration month
		if (dateComponents[1] < birthDateComponents[1])
			//then subtract one year
			age --;
		//or if the same month but not year reached the celebration day
		else if (dateComponents[1] == birthDateComponents[1])
			if (dateComponents[2] < birthDateComponents[2])
				//then subtract one year
				age -- ;

		return age < 0 ? -1 : age;
	}

	/**
	 * Will retrieve and set the indexes found in the
	 * look-up table for the fall label(s).
	 * @param fallLabels - the list of fall labels
	 * @param fallLabel - in case there is only one fall label
	 */
	public static void setFallIndexes(List<String> fallLabels, String fallLabel){
		if ((fallLabels == null || fallLabels.isEmpty()) &&
				(fallLabel == null || fallLabel.equals("")))
				throw new IllegalArgumentException("No fall label(s) provided");

		if (fallLabels != null && !fallLabels.isEmpty())
			for (String eventType : fallLabels)
				if (InputFileUtilities.eventTypes.containsValue(eventType))
					fallIndexes.add(InputFileUtilities.getIndexOf(eventType, InputFileUtilities.eventTypes));
		if (fallLabel != null)
			fallIndex = InputFileUtilities.getIndexOf(fallLabel, InputFileUtilities.eventTypes);
	}

	/**
	 * Will retrieve and set the indexes found in the
	 * look-up table for the fracture label(s).
	 * @param fractureLabels - the list of fracture labels
	 * @param fractureLabel - in case there is only one fracture label
	 */
	public static void setFractureIndexes(List<String> fractureLabels, String fractureLabel){
		if ((fractureLabels == null || fractureLabels.isEmpty()) &&
				(fractureLabel == null || fractureLabel.equals("")))
				throw new IllegalArgumentException("No fracture label(s) provided");

		if (fractureLabels != null && !fractureLabels.isEmpty())
			for (String eventType : fractureLabels)
				if (InputFileUtilities.eventTypes.containsValue(eventType))
					fractureIndexes.add(InputFileUtilities.getIndexOf(eventType, InputFileUtilities.eventTypes));
		if (fractureLabel != null)
			fractureIndex = InputFileUtilities.getIndexOf(fractureLabel, InputFileUtilities.eventTypes);
	}

	/**
	 * Will retrieve and set the indexes found in the
	 * look-up table for the BMD label(s).
	 * @param bmdLabels - the list of fall labels
	 * @param bmdLabel - in case there is only one BMD label
	 */
	public static void setBMDIndexes(List<String> bmdLabels, String bmdLabel){
		if ((bmdLabels == null || bmdLabels.isEmpty()) &&
				(bmdLabel == null || bmdLabel.equals("")))
				throw new IllegalArgumentException("No BMD label(s) provided");

		if (bmdLabels != null && !bmdLabels.isEmpty())
			for (String measurementType : bmdLabels)
				if (InputFileUtilities.measurementTypes.containsValue(measurementType))
					bmdIndexes.add(InputFileUtilities.getIndexOf(measurementType, InputFileUtilities.measurementTypes));
		if ((bmdLabel != null) && !bmdLabel.equals("") && InputFileUtilities.measurementTypes.containsValue(bmdLabel)) {
			bmdIndex = InputFileUtilities.getIndexOf(bmdLabel, InputFileUtilities.measurementTypes);
		}
	}
	/**
	 * Will retrieve and set the indexes found in the
	 * look-up table for the weight label(s).
	 * @param weightLabels - the list of fall labels
	 * @param weightLabel - in case there is only one weight label
	 */
	public static void setWeightIndexes(List<String> weightLabels, String weightLabel){
		if ((weightLabels == null || weightLabels.isEmpty()) &&
				(weightLabel == null || weightLabel.equals("")))
				throw new IllegalArgumentException("No weight label(s) provided");

		if (weightLabels != null && !weightLabels.isEmpty())
			for (String measurementType : weightLabels)
				if (InputFileUtilities.measurementTypes.containsValue(measurementType))
					weightIndexes.add(InputFileUtilities.getIndexOf(measurementType, InputFileUtilities.measurementTypes));
		if (weightLabel != null && !weightLabel.equals("") && InputFileUtilities.measurementTypes.containsValue(weightLabel))
			weightIndex = InputFileUtilities.getIndexOf(weightLabel, InputFileUtilities.measurementTypes);
	}

	/* ------------------------ UTILITY CLASSES HOLDING THE CONSTANTS -------------------------*/

	/**
	 * This class contains the constants defining the baseline probability
	 * used in the calculation of the Garvan risk factor.
	 *
	 * @author MG
	 *
	 */
	static class BaselineProbability{

		//5 years risk
		public static final double FIVE_YEARS_FEMALE_HIP = 0.999896685;
		public static final double FIVE_YEARS_FEMALE_ANY = 0.995750118;
		public static final double FIVE_YEARS_MALE_HIP = 0.999998862;
		public static final double FIVE_YEARS_MALE_ANY = 0.999921076;

		//10 years risk
		public static final double TEN_YEARS_FEMALE_HIP = 0.999793070;
		public static final double TEN_YEARS_FEMALE_ANY = 0.990905138;
		public static final double TEN_YEARS_MALE_HIP = 0.999997778;
		public static final double TEN_YEARS_MALE_ANY = 0.999848975;


		public static double get(int time, byte gender, boolean hipFracture){
			switch (time){
			case GarvanRiskFactor.FIVE_YEARS:
				switch (gender){
				case DataDefinition.FEMALE_GENDER:
					return hipFracture ?
							FIVE_YEARS_FEMALE_HIP : FIVE_YEARS_FEMALE_ANY;
				case DataDefinition.MALE_GENDER:
					return hipFracture ?
							FIVE_YEARS_MALE_HIP : FIVE_YEARS_MALE_ANY;
				}
			case GarvanRiskFactor.TEN_YEARS:
				switch (gender){
				case DataDefinition.FEMALE_GENDER:
					return hipFracture ?
							TEN_YEARS_FEMALE_HIP : TEN_YEARS_FEMALE_ANY;
				case DataDefinition.MALE_GENDER:
					return hipFracture ?
							TEN_YEARS_MALE_HIP : TEN_YEARS_MALE_ANY;
				}
			}

			return NO_RESULT;
		}
	}

	/**
	 * This class contains the constants defining the coefficients
	 * associated with each factor by fracture type and gender for the
	 * models with femoral neck BMD.
	 * <p>
	 * Each array contains the following coefficients in a fixed order:<br>
	 * {@code b1} (Age, years)<br>
	 * {@code b2} (FNBMD T-score)<br>
	 * {@code b3} (Prior fx)<br>
	 * {@code b4} (Falls) <br>
	 *
	 * @author MG
	 *
	 */
	static class CoefficientsBMD{

		public static final double[] FEMALE_HIP = new double[]{0.0507, -0.8417, 0.8127, 0.3614};
		public static final double[] FEMALE_ANY = new double[]{0.0321, -0.4022, 0.5691, 0.2038};

		public static final double[] MALE_HIP = new double[]{0.107, -1.007, 0.599, 0.212};
		public static final double[] MALE_ANY = new double[]{0.0883, -0.2986, 0.8454, 0.0981};

		public static double[] get(byte gender, boolean hipFracture){
			switch (gender){
			case DataDefinition.FEMALE_GENDER:
				return hipFracture ? FEMALE_HIP : FEMALE_ANY;
			case DataDefinition.MALE_GENDER:
				return hipFracture ? MALE_HIP : MALE_ANY;
			}

			return null;
		}
	}

	/**
	 * This class contains the constants defining the coefficients
	 * associated with each factor by fracture type and gender for the
	 * models with body weight.
	 * <p>
	 * Each array contains the following coefficients in a fixed order:<br>
	 * {@code b1} (Age, years)<br>
	 * {@code b2} (Weight, kg)<br>
	 * {@code b3} (Prior fx)<br>
	 * {@code b4} (Falls)
	 *
	 * @author MG
	 *
	 */
	static class CoefficientsWeight{

		public static final double[] FEMALE_HIP = new double[]{0.0866, -0.0444, 1.1916, 0.2946};
		public static final double[] FEMALE_ANY = new double[]{0.0511, -0.0099, 0.6709, 0.1985};

		public static final double[] MALE_HIP = new double[]{0.138, -0.008, 0.854, 0.233};
		public static final double[] MALE_ANY = new double[]{0.09071, -0.00765, 1.00818, 0.12797};

		public static double[] get(byte gender, boolean hipFracture){
			switch (gender){
			case DataDefinition.FEMALE_GENDER:
				return hipFracture ? FEMALE_HIP : FEMALE_ANY;
			case DataDefinition.MALE_GENDER:
				return hipFracture ? MALE_HIP : MALE_ANY;
			}

			return null;
		}
	}

	/* ----------------------------- END OF UTILITY CLASSES -----------------------------------*/

	//BUILDER
	public static class Builder{

		private List<String> fallLabels;
		private List<String> fractureLabels;
		private List<String> bmdLabels;
		private List<String> weightLabels;

		private String fallLabel = null;
		private String fractureLabel = null;
		private String bmdLabel = null;
		private String weightLabel = null;

		public Builder fallLabels(List<String> value) { fallLabels = new ArrayList<String>(value); return this;}
		public Builder fractureLabels(List<String> value) { fractureLabels = new ArrayList<String>(value); return this;}
		public Builder bmdLabels(List<String> value) { bmdLabels = new ArrayList<String>(value); return this;}
		public Builder weightLabels(List<String> value) { weightLabels = new ArrayList<String>(value); return this;}

		public Builder fallLabel(String value) { fallLabel = value; return this;}
		public Builder fractureLabel(String value) { fractureLabel = value; return this;}
		public Builder bmdLabel(String value) { bmdLabel = value; return this;}
		public Builder weightLabel(String value) { weightLabel = value; return this;}

		public GarvanRiskFactor build() {return new GarvanRiskFactor(this);}
	}

	public GarvanRiskFactor(Builder builder) {

		setFallIndexes(builder.fallLabels, builder.fallLabel);
		setFractureIndexes(builder.fractureLabels, builder.fractureLabel);
		setBMDIndexes(builder.bmdLabels, builder.bmdLabel);
		setWeightIndexes(builder.weightLabels, builder.weightLabel);

	}

	//GETTTERS
	public static Integer getDaysFromIndexDateToWeight() {
		return indexDateToWeight;
	}

	public static Integer getDaysFromIndexDateToBMD() {
		return indexDateToBMD;
	}

	public static Measurement getBmdMeasurement() {
		return bmdMeasurement;
	}

	public static Measurement getWeightMeasurement() {
		return weightMeasurement;
	}

	public static int getNumberOfFalls(){
		return nbFalls;
	}


	public static int getNumberOfFractures(){
		return Pfx;
	}

	/**
	 * Main method for debug and testing
	 * @param args- none
	 */
	public static void main(String[] args){

		TreeMap<Integer, Double> map = new TreeMap<Integer, Double>(Collections.reverseOrder());
	    map.put(123, 0.5);
	    map.put(678, 1.0);
	    map.put(567, 0.1);
//
//	    TreeSet<Integer> set = new TreeSet<Integer>(Collections.reverseOrder());
//	    set.add(123);
//	    set.add(678);
//	    set.add(567);
//
//	    int key = 200;
//	    System.out.println("Lower than "+key+ " is "+set.tailSet(key));


//		String risk = GarvanRiskFactor.getRiskFactorAsPercentage(5, DataDefinition.FEMALE_GENDER, 60, -2.5, 1,1,true, true);
//		System.out.println("The risk factor for 5 years is "+risk);
		//The risk factor for 5 years is 0.055804543811765694

//		risk = GarvanRiskFactor.getRiskFactorAsPercentage(10, DataDefinition.FEMALE_GENDER, 60, -2.5, 1,1,true, true);
//		System.out.println("The risk factor for 10 years is "+risk);
		//The risk factor for 10 years is 0.10864888854670751


		//input files
		PatientObjectCreator poc = new PatientObjectCreator();

		String testDataPath = "D:/Work/TestData/garvan/";
		FilePaths.WORKFLOW_PATH = testDataPath;
		FilePaths.INTERMEDIATE_PATH = testDataPath+"/Intermediate/";
		FilePaths.LOG_PATH = FilePaths.WORKFLOW_PATH + "Log/";

		String patientsFile = testDataPath + "Patients.txt";
		String eventsFile = testDataPath + "Events.txt";
		String measurementsFile = testDataPath + "Measurements.txt";

		Logging.prepareOutputLog();

		//Garvan parameters
		List<String> fallLabels = new ArrayList<String>();
		fallLabels.add("FALL");
		fallLabels.add("FALLS");

		List<String> fractureLabels = new ArrayList<String>();
		fractureLabels.add("FRAC");
		fractureLabels.add("FRACTURE");

		try{
			List<Patient> patients = poc.createPatients(patientsFile, eventsFile, null, measurementsFile);

			int indexDate = DateUtilities.dateToDaysUnitTest("20070505");
			int period = GarvanRiskFactor.FIVE_YEARS;

			new GarvanRiskFactor.Builder().bmdLabel("BMD").fallLabels(fallLabels).fractureLabels(fractureLabels).weightLabel("WEIGHT").build();



			if (patients != null && patients.size() > 0){

				for (Patient patient : patients){
					GarvanRiskFactor.init(patient);

					System.out.println("The risk for "+period+" years is: "+GarvanRiskFactor.calculateRisk(period, indexDate, true, true, true)+" percent");

				}
			}
		}catch(Exception e){
			Logging.outputStackTrace(e);
		}
	}

}
