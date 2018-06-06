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


package org.erasmusmc.jerboa.modules;

import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.commons.collections.bag.HashBag;
import org.erasmusmc.jerboa.Jerboa;
import org.erasmusmc.jerboa.config.DataDefinition;
import org.erasmusmc.jerboa.config.Parameters;
import org.erasmusmc.jerboa.dataClasses.Patient;
import org.erasmusmc.jerboa.dataClasses.Prescription;
import org.erasmusmc.jerboa.engine.Module;
import org.erasmusmc.jerboa.modifiers.PopulationDefinition;
import org.erasmusmc.jerboa.utilities.AgeGroupDefinition;
import org.erasmusmc.jerboa.utilities.AgeGroupDefinition.AgeGroup;
import org.erasmusmc.jerboa.utilities.stats.formulas.GarvanRiskFactor;
import org.erasmusmc.jerboa.utilities.DateUtilities;
import org.erasmusmc.jerboa.utilities.DelimitedStringBuilder;
import org.erasmusmc.jerboa.utilities.IndexDateUtilities;
import org.erasmusmc.jerboa.utilities.Item;
import org.erasmusmc.jerboa.utilities.ItemList;
import org.erasmusmc.jerboa.utilities.Logging;
import org.erasmusmc.jerboa.utilities.MultiKeyBag;
import org.erasmusmc.jerboa.utilities.StringUtilities;

/**
 * Shows the drug utilization at prescription level.
 * @author bmosseveld
 *
 */

public class DrugUtilizationPrescriptionLevel extends Module {

	/**
	 * The list of ATC-codes of interest.
	 */
	public List<String> atcOfInterest = new ArrayList<String>();
	
	
	/**
	 * Age group definition used for the Age at cohort start categories
	 * Format: Min;Max;Label
	 * Min    - Minimum age in years (inclusive).
	 * Max    - Maximum age in years (inclusive).
	 * Label  - Label used in output.
	 * 
	 * Example: "0;5;0-5"
	 */
	public List<String> ageGroups = new ArrayList<String>();
	
	/**
	 * A date at which the age of the patient has to be determined.
	 * When empty the column is left out.
	 * Format:
	 * 
	 *   indexDate;unit
	 * 
	 * where
	 * 
	 *   indexDate = A date as yyyymmdd
	 *               PopulationStart
	 *               PopulationEnd
	 *               CohortStart
	 *               CohortEnd
	 *               PrescriptionStart
	 *               PrescriptionEnd
	 * 
	 *   unit      = Not specified        The official age (changes at birthday) for backwards compatibility
	 *               Years                The official age (changes at birthday)
	 *               YearsFraction        (date - indexDate) / 365.25
	 *               Months               (date - indexDate) / (365.25 / 12)
	 *               Days                 date - indexDate
	 */
	public String ageAtDate = "";
	
	
	/**
	 * Default number of days relative to prescription start to search for events.
	 * 
	 * For example 365 means 365 days before prescription start
	 * Can be overruled by event specific definitions
	 */
	public int defaultWindowStartEvent;
	
	
	/**
	 * List of events of interest checked for their presence in a time window (inclusive) around prescription start.
	 * Format: Event types;Label;Description;TimeWindoStart;TimeWindowEnd
	 * Event types       - A comma separated list of event types.
	 * Label            - The label used in the result tables.
	 * Description      - The description.
	 * TimewindowStart  - Optional parameter that overrule the defaultWindowStartEvent setting.
	 *                    Start of time window before index date. Start day is included.
	 *                    birth = All history
	 * TimewindowEnd    - Optional parameter that overrules the index date at TimeWindowEnd. Note that here the end day is not included!
	 *                    End of time window relative to index date. End day is included.
	 *                    cohortEnd = end of cohort
	 * 
	 * For example: "MI,AP;IHD;Ischemic Heart Disease"                : Presence of events in the window  prescriptionStartDate-defaultWindowStartEvent< X <=prescriptionStartDate
	 *              "MI,AP;IHD;Ischemic Heart Disease;-365"           : Presence of events in the window  prescriptionStartDate-365< X <=prescriptionStartDate
	 *              "MI,AP;IHD;Ischemic Heart Disease;-365,10"        : Presence of events in the window  prescriptionStartDate-365< X <prescriptionStartDate + 10
	 *              "MI,AP;IHD;Ischemic Heart Disease;-365,cohortEnd" : Presence of events in the window  prescriptionStartDate-365< X <cohortEnd
	 */	
	public List<String> eventOfInterest = new ArrayList<String>();

	/**
	 * List of events of interest checked to count the number of occurrences in a time window (inclusive) around prescription start.
	 * Format: Event types;Label;Description;MinAge;MaxAge;TimeWindoStart;TimeWindowEnd
	 * Event types       - A comma separated list of event types.
	 * Label             - The label used in the result tables.
	 * Description       - The description.
	 * MinAge            - The minimum age value (inclusive), -1 means no minimum.
	 * MaxAge            - The maximum age value (exclusive), -1 means no maximum.
	 * TimeWindowStart   - Start of time window before index date. Start day is included.
	 *                     birth = All history 
	 * TimeWindowEnd     - Optional parameter that overrules the index date as TimeWindowEnd. Note that here the end day is included!
	 *                     End of time window relative to index date. End day is not included.
	 *                     cohortEnd = end of cohort
	 * 
	 * For example: "MI;MI_15-40;Body Mass Index;15;40;-365"           : The number of MI events at an age in the range 15 to 40 in the window prescriptionStartDate-365< X <=prescriptionStartDate
	 *              "MI;MI_15-40;Body Mass Index;15;40;-365,10"        : The number of MI events at an age in the range 15 to 40 in the window prescriptionStartDate-365< X <prescriptionStartDate + 10
	 *              "MI;MI_15-40;Body Mass Index;15;40;-365,cohortEnd" : The number of MI events at an age in the range 15 to 40 in the window prescriptionStartDate-365< X <cohortEnd
	 */	
	public List<String> eventCount = new ArrayList<String>();

	/**
	 * List of events for which the time from prescription start to the date of the next event has to be calculated.
	 * Format: EventType;Label;Description
	 * EventType		    - The name of the event in the event file.
	 * Label                - The label used in the result tables.
	 * Description			- The description.
	 * 
	 * Example: "DEATH;Death;Time to Death"
	 */
	public List<String> eventTimeTo = new ArrayList<String>();
		
	/**
	 * Default number of days relative to prescription start to search for measurements.
	 * 
	 * For example -365 means 365 days before prescription start
	 * Can be overruled by measurement specific definitions
	 */
	public int defaultWindowStartMeasurement;
		
	/**
	 * List of measurements of interest checked for their presence in a time window (inclusive) around prescription start.
	 * Format:
	 * 
	 *   Measurement types;Label;Description;TimeWindoStart;TimeWindowEnd;Type
	 *   
	 * Measurement types - A comma separated list of measurement types.
	 * Label             - The label used in the result tables.
	 * Description       - The description.
	 * TimewindowStart   - Optional parameter that overrules the defaultWindowStartMeasurement setting.
	 *                     Start of time window before index date. Start day is included.
	 *                     birth = All history 
	 * TimewindowEnd     - Optional parameter that overrules the index date as TimeWindowEnd. Note that here the end day is included!
	 *                     End of time window relative to index date. End day is not included.
	 *                     cohortEnd = end of cohort
	 * Type              - Optional parameter that specifies which measurement value should be retrieved. Possible values are:
	 * 
	 *                       Not specified = check for presence
	 *                       Closest
	 *                       First
	 *                       Last
	 *                       Highest
	 *                       Lowest
	 * 
	 * For example: "BMI;BMI;Body Mass Index"                        : Presence of BMI in the window  prescriptionStartDate-defaultWindowStartMeasurement< X <=prescriptionStartDate
	 *              "BMI;BMI;Body Mass Index;-365"                   : Presence of BMI in the window  prescriptionStartDate-365< X <=prescriptionStartDate
	 *              "BMI;BMI;Body Mass Index;-365;10"                : Presence of BMI in the window  prescriptionStartDate-365< X <prescriptionStartDate + 10
	 *              "BMI;BMI;Body Mass Index;-365;cohortEnd"         : Presence of BMI in the window  prescriptionStartDate-365< X <cohortEnd
	 *              "BMI;BMI;Body Mass Index;;;Closest"              : BMI value closest to prescription start in the window  prescriptionStartDate-defaultWindowStartMeasurement< X <=prescriptionStartDate
	 *              "BMI;BMI;Body Mass Index;-365;;First"            : Value of the first BMI in the window  prescriptionStartDate-365< X <=prescriptionStartDate
	 *              "BMI;BMI;Body Mass Index;-365;10;Last"           : Value of the last BMI in the window  prescriptionStartDate-365< X <prescriptionStartDate + 10
	 *              "BMI;BMI;Body Mass Index;-365;cohortEnd;Highest" : Highest BMI value in the window  prescriptionStartDate-365< X <cohortEnd
	 */	
	public List<String> measurementOfInterest = new ArrayList<String>();
	
	/**
	 * If true for each measurementOfInterest an extra unit column is added in the output
	 */
	public boolean addUnitColumn = false;
	
	/**
	 * List of measurements of interest checked to count the number of occurences in a time window (inclusive) around prescription start.
	 * Format: Measurement types;Label;Description;Min;Max;TimeWindoStart;TimeWindowEnd
	 * Measurement types - A comma separated list of measurement types.
	 * Label             - The label used in the result tables.
	 * Description       - The description.
	 * Min               - The minimum value (inclusive), -1 means no minimum.
	 * Max               - The maximum value (exclusive), -1 means no maximum.
	 * TimeWindowStart   - Optional parameter that overrules the defaultWindowStartMeasurement setting.
	 *                     Start of time window before index date. Start day is included.
	 *                     birth = All history 
	 * TimeWindowEnd     - Optional parameter that overrules the index date as TimeWindowEnd. Note that here the end day is included!
	 *                     End of time window relative to index date. End day is not included.
	 *                     cohortEnd = end of cohort
	 * 
	 * For example: "BMI;BMI_15-40;Body Mass Index;15;40"                : The number of BMI measurements with a value in the range 15 to 40 in the window prescriptionStartDate-defaultWindowStartMeasurement< X <=prescriptionStartDate
	 *              "BMI;BMI_15-40;Body Mass Index;15;40;-365"           : The number of BMI measurements with a value in the range 15 to 40 in the window prescriptionStartDate-365< X <=prescriptionStartDate
	 *              "BMI;BMI_15-40;Body Mass Index;15;40;-365,10"        : The number of BMI measurements with a value in the range 15 to 40 in the window prescriptionStartDate-365< X <prescriptionStartDate + 10
	 *              "BMI;BMI_15-40;Body Mass Index;15;40;-365,cohortEnd" : The number of BMI measurements with a value in the range 15 to 40 in the window prescriptionStartDate-365< X <cohortEnd
	 */	
	public List<String> measurementCount = new ArrayList<String>();

	/**
	 * List of measurements for which the time since needs to be counted relative to index date.
	 * Format: MeasurmentType;Label;Description
	 * MeasurementType		- The name of the measurement in the measurement file.
	 * Label                - The label used in the result tables.
	 * Description			- The description.
	 * 
	 * Example: "BMI;BMI;Body Mass Index"
	 */
	public List<String> measurementTimeSince = new ArrayList<String>();
	
	/**
	 * Default number of days relative to prescription start to search for measurements.
	 * 
	 * For example -365 means 365 days before prescription start
	 * Can be overruled by prescription specific definitions
	 */
	public int defaultWindowStartPrescription;
	
	/**
	 * List of prescriptions of interest checked for their presence in a time window (inclusive) around prescription start
	 * Format: ATC codes;Label;TimeWindowStart;TimeWindowEnd
	 * ATC codes         - A comma separated list of ATC codes, higher level allowed.
	 * Label             - The label used in the result tables.
	 * Description       - The description.
	 * TimewindowStart   - Optional parameter that overrules the defaultWindowStartPrescription setting.
	 *                     Start of time window before index date. Start day is included.
	 *                     birth = All history 
	 * TimewindowEnd     - Optional parameter that overrules the index date as TimeWindowEnd. Note that here the end day is included!
	 *                     End of time window relative to index date. End day is not included.
	 *                     cohortEnd = end of cohort
	 * 
	 * For example: "C07,C09;HISTANTIHYP;Anti-Hypertensive Drugs"                : Presence of prescriptions in the window  prescriptionStartDate-defaultWindowStartPrescriptiont< X <=prescriptionStartDate
	 *              "C07,C09;HISTANTIHYP;Anti-Hypertensive Drugs;-365"           : Presence of prescriptions in the window  prescriptionStartDate-365< X <=prescriptionStartDate
	 *              "C07,C09;HISTANTIHYP;Anti-Hypertensive Drugs;-365,10"        : Presence of prescriptions in the window  prescriptionStartDate-365< X <prescriptionStartDate + 10
	 *              "C07,C09;HISTANTIHYP;Anti-Hypertensive Drugs;-365,cohortEnd" : Presence of prescriptions in the window  prescriptionStartDate-365< X <cohortEnd
	 */	
	public List<String> prescriptionOfInterest = new ArrayList<String>();
		
	/**
	 * List of columns from prescriptions file to copy to the output.
	 */
	public List<String> outputPrescriptionFields = new ArrayList<String>();
	
	/**
	 * Allow to add the value of an extended column in the prescription file to be added a column
	 * in the output file.
	 * Format: Column name;Label;Description;Datatype;Valuetype
	 * 
	 *   Column name    The name of the extended column.
	 *   Label          The label used as header of the column.
	 *   Description    A description.
	 *   Datatype       Specifies if the extended column comes from the patients file or the prescriptions file.
	 *   
	 *                    Patient
	 *                    Prescription
	 *                    
	 *   Valuetype      The type of value to put in the column:
	 *  
	 *                    Value        The original value is used.
	 *                    TimeTo       The time from prescription start to the value of the extended column.
	 */
	public List<String> extendedColumnOfInterest = new ArrayList<String>();
	
	/**
	 * If specified columns will be added containing the number of days until the
	 * specified date and the age in years at the specified date.
	 * Format:
	 * 
	 *   <label>;<date as yyyymmdd>
	 *   
	 * Creates columns TimeTo<label> and AgeAt<label>.
	 */
	public List<String> referenceDates = new ArrayList<String>();
	
	/**
	 * If true columns are added for GarvanRisks Calculation
	 */
	public boolean addGarvanRisk;
	
	/**
	 * This parameter determines if the Garvan risk factor 
	 * is estimated for hip fractures or any fracture.
	 */
	public boolean forHipFractures = false;
	
	/**
	 * This parameter determines if the index date should be included
	 * in the calculation of the risk factor or not. 
	 */
	public boolean includingIndexDateGarvan = true;
		
	/**
	 * This parameter will hold a list of measurement types
	 * that represent the weight measurements.
	 */
	public List<String> weightLabels = new ArrayList<String>();
	
	/**
	 * This parameter will hold a list of measurement types
	 * that represent the bone mineral density measurements.
	 */
	public List<String> bmdLabels = new ArrayList<String>();
	
	/**
	 * This parameter will hold a list of event types
	 * that represent falls.
	 */
	public List<String> fallLabels = new ArrayList<String>();
	
	/**
	 * This parameter will hold a list of event types
	 * that represent fractures.
	 */
	public List<String> fractureLabels = new ArrayList<String>();
	
	/**
	 * If true the real patient ID is written to the output file.
	 * FOR DEBUGGING ONLY!
	 */
	public boolean showRealPatientID = false;
	
	
	
	
	private AgeGroupDefinition ageGroupDefinition = null;
	private String ageAtDateIndexDate = "";
	private String ageAtDateUnit = "";
	private int ageAtDateValue = -1;
	private ItemList eventsList = null;	
	private ItemList eventCountsList = null;
	private ItemList eventTimeToList = null;	
	private ItemList measurementsList = null;
	private ItemList measurementCountsList = null;
	private ItemList measurementTimeSinceList = null;
	private ItemList prescriptionsList = null;
	private ItemList extendedColumnsList = null;
	private List<String> referenceLabels = null;
	private List<Integer> referenceDateList = null;
	
	private String resultsFile = ""; 

	private MultiKeyBag eventContinuesBag = new MultiKeyBag();
	private MultiKeyBag eventCategoryBag = new MultiKeyBag();
	private MultiKeyBag measContinuesBag = new MultiKeyBag();
	private MultiKeyBag measCategoryBag = new MultiKeyBag();
	private MultiKeyBag measCountBag = new MultiKeyBag();
	private MultiKeyBag eventCountBag = new MultiKeyBag();
	private HashBag 	prescriptionBag = new HashBag();
	
	private String intermediateAnonymizationFile;
	
	DecimalFormat precisionFormat = StringUtilities.DECIMAL_FORMAT_FORCE_PRECISION;
	
	//Hook to modifiers that need to be accessed
	private PopulationDefinition population;

	@Override
	public boolean init() {
		boolean initOK = true;

		//get the needed modifiers
		population = (PopulationDefinition)this.getModifier(PopulationDefinition.class);
		
		// Parse the age groups
		ageGroupDefinition = new AgeGroupDefinition(ageGroups);	
		if (ageGroupDefinition.hasGaps()) {
			Logging.add("Note: for module " + this.title +" age group definitions in the script have gaps!");
			Logging.add("");
		}
		
		ageAtDate = ageAtDate.trim();
		String[] ageAtDateSplit = ageAtDate.split(";");
		ageAtDateIndexDate = ageAtDateSplit[0].trim();
		if (	(!ageAtDateIndexDate.equals("")) &&
				(!ageAtDateIndexDate.equals("POPULATIONSTART")) &&
				(!ageAtDateIndexDate.equals("POPULATIONEND")) &&
				(!ageAtDateIndexDate.equals("COHORTSTART")) &&
				(!ageAtDateIndexDate.equals("COHORTEND")) &&
				(!ageAtDateIndexDate.equals("PRESCRIPTIONSTART")) &&
				(!ageAtDateIndexDate.equals("PRESCRIPTIONEND"))) {
			if (!DateUtilities.isValidDate(ageAtDate)) {
				Logging.add("Illegal value for ageAtDate indexDate!");
				initOK = false;
			}
			else if (!ageAtDateIndexDate.equals("")) {
				ageAtDateValue = DateUtilities.dateToDays(ageAtDateIndexDate, DateUtilities.DATE_ON_YYYYMMDD);
			}
		}
		if (ageAtDateSplit.length > 1) {
			ageAtDateUnit = ageAtDateSplit[1].trim();
			if (ageAtDateUnit.equals("")) {
				ageAtDateUnit = "YEARS";
			}
			if (	(!ageAtDateUnit.equals("YEARS")) &&
					(!ageAtDateUnit.equals("YEARSFRACTION")) &&
					(!ageAtDateUnit.equals("MONTHS")) &&
					(!ageAtDateUnit.equals("DAYS"))) {
				Logging.add("Illegal value for ageAtDate unit!");
				initOK = false;
			}
		}
		else {
			ageAtDateUnit = "YEARS";
		}
		
		eventsList = new ItemList(true,0);
		eventsList.parse(eventOfInterest);
		eventCountsList = new ItemList(true,0);
		eventCountsList.parse(eventCount);
		eventTimeToList = new ItemList(true,0);
		eventTimeToList.parse(eventTimeTo);
		measurementsList = new ItemList(true,0);
		measurementsList.parse(measurementOfInterest);
		measurementCountsList = new ItemList(true,0);
		measurementCountsList.parse(measurementCount);
		measurementTimeSinceList = new ItemList(true,0);
		measurementTimeSinceList.parseWithValue(measurementTimeSince);
		prescriptionsList = new ItemList(true,0);
		prescriptionsList.parse(prescriptionOfInterest);
		extendedColumnsList = new ItemList(true,2);
		extendedColumnsList.parse(extendedColumnOfInterest);
		
		// Check the reference dates
		referenceLabels = new ArrayList<String>();
		referenceDateList = new ArrayList<Integer>();
		for (String referenceDate : referenceDates) {
			String[] referenceDateSplit = referenceDate.split(";");
			if (referenceDateSplit.length == 2) {
				String label = referenceDateSplit[0].trim();
				Integer date = DateUtilities.dateToDays(referenceDateSplit[1], DateUtilities.DATE_ON_YYYYMMDD);
				if (label.equals("")) {
					Logging.add("Error empty label in reference date definition: " + referenceDate);
					initOK = false;
				}
				if (date == null) {
					Logging.add("Error illegal date in reference date definition: " + referenceDate);
					initOK = false;
				}
				if (initOK) {
					referenceLabels.add(label);
					referenceDateList.add(date);
				}
			}
			else {
				Logging.add("Error in reference date definition: " + referenceDate);
				initOK = false;
			}
		}
				
		
		// Write the header of the result file based on parameters
		resultsFile = outputFileName;
		DelimitedStringBuilder header = new DelimitedStringBuilder();
		header.append("DatabaseID");
		header.append("PatientID");
		header.append("ATC");
		header.append("SeqNr");
		header.append("Age");
		if (ageGroups.size() > 0) {
			header.append("AgeGroup");
		}
		if (!ageAtDate.equals("")) {
			if (ageAtDateValue != -1) {
				header.append("AgeAt_" + ageAtDateValue + "_" + ageAtDateUnit);
			}
			else {
				header.append("AgeAt_" + ageAtDateIndexDate + "_" + ageAtDateUnit);
			}
		}
		header.append("Year");
		header.append("Month");
		header.append("EndYear");   // End followup
		header.append("EndMonth");  // End followup
		header.append("Gender");
		for (String prescriptionField : outputPrescriptionFields) {
			header.append(prescriptionField);
		}
		header.append("Duration");
		header.append("TimeSincePrevious");
		header.append("DurationPrevious");
		header.append("TimeSinceStart");
		header.append("TimeToEnd");
		header.append("TimeSincePopulationStart");
		header.append("ActiveInCohortTime");
		for (String referenceLabel : referenceLabels) {
			header.append("TimeTo" + referenceLabel);
			header.append("AgeAt" + referenceLabel);
		}
		for (Item eventOfInterest : eventsList) {
			header.append("HIST" + eventOfInterest.getLabel());
		}
		for (Item eventOfInterest : eventCountsList) {
			header.append("N" + eventOfInterest.getLabel());
		}
		for (Item eventOfInterest : eventTimeToList) {
			header.append("TimeTo" + eventOfInterest.getLabel());
		}
		for (Item measurementOfInterest : measurementsList) {
			header.append("HIST" + measurementOfInterest.getLabel());
			if (addUnitColumn)
				header.append("UNIT" + measurementOfInterest.getLabel());
		}
		for (Item measurementOfInterest : measurementCountsList) {
			header.append("N" + measurementOfInterest.getLabel());
		}
		for (Item measurementOfInterest : measurementTimeSinceList) {
			header.append("T" + measurementOfInterest.getLabel());
		}
		for (Item prescriptionOfInterest : prescriptionsList) {
			header.append("HIST" + prescriptionOfInterest.getLabel());
		}
		for (Item extendedColumnOfInterest : extendedColumnsList) {
			header.append(extendedColumnOfInterest.getLabel());
		}
		if (addGarvanRisk) {
			//header.append("GARVAN_WEIGHT_coh,GARVAN_BMD_coh,Weighteb_coh,TWeighteb_coh,BMDeb_coh,UNITBMDeb_coh,TBMDeb_coh,NFalls_coh,NFract50_coh");
			header.append("GARVAN_WEIGHT_coh");
			//initialize the risk estimator
			new GarvanRiskFactor.Builder().
			bmdLabels(bmdLabels).
			weightLabels(weightLabels).
			fallLabels(fallLabels).
			fractureLabels(fractureLabels).
			build();
		}
		Jerboa.getOutputManager().addFile(resultsFile);
		Jerboa.getOutputManager().writeln(resultsFile,header.toString(),false);

		if (!showRealPatientID) {
			intermediateAnonymizationFile = StringUtilities.addSuffixToFileName(intermediateFileName, "_anonymization");
			initOK &= Jerboa.getOutputManager().addFile(intermediateAnonymizationFile);
			Jerboa.getOutputManager().writeln(intermediateAnonymizationFile, "AnonymousID,PatientID", false);
		}
				
		return initOK;
	}
	

	@Override
	public Patient process(Patient patient) {
		if (!showRealPatientID) {
			Jerboa.getOutputManager().writeln(intermediateAnonymizationFile, patient.getAnonymizedPatientId() + "," + patient.getPatientID(), true);
		}

		if (patient.inCohort) {
			
			if (addGarvanRisk)
				GarvanRiskFactor.init(patient);
			
			// Order the prescriptions by date and duration all ascending
			List<Prescription> orderedPrescriptions = new ArrayList<Prescription>();
			for (Prescription prescription : patient.getPrescriptions()) {
				orderedPrescriptions.add(prescription);
			}
			orderedPrescriptions.sort(Prescription.DateUpATCUpDurationUpComparator);
			
			Map<String, Prescription> lastPrecscriptions = new HashMap<String, Prescription>();
			int prescriptionSequenceNr = 0;
			for (Prescription prescription : orderedPrescriptions) {
				if (prescription.startsWith(atcOfInterest)) {
					
					int age = patient.getAgeAtDateInYears(prescription.getDate());
					
					// Create the result file
					DelimitedStringBuilder results = new DelimitedStringBuilder();

					// DatabaseID
					results.append(Parameters.DATABASE_NAME);
					// PatientID
					if (showRealPatientID) {
						results.append(patient.getPatientID());
					}
					else {
						results.append(String.valueOf(patient.getAnonymizedPatientId()));
					}
					// ATC
					results.append(prescription.getATC());
					// SeqNr
					results.append(String.valueOf(prescriptionSequenceNr));
					// Age
					results.append(String.valueOf(age));
					// AgeGroup
					if (ageGroups.size() > 0) {
						String ageGroups =  "";
						for (AgeGroup ageGroup : ageGroupDefinition.getAgeGroups(age)) {
							if (!ageGroups.equals("")) {
								ageGroups += ";";
							}
							ageGroups += ageGroup.getLabel();
						}
						results.append(ageGroups);
					}
					// AgeAtDate
					if (!ageAtDate.equals("")) {
						int indexDate = ageAtDateIndexDate.equals("POPULATIONSTART") ? patient.getPopulationStartDate() : 
							(ageAtDateIndexDate.equals("POPULATIONEND") ? patient.getPopulationEndDate() : 
							(ageAtDateIndexDate.equals("COHORTSTART") ? patient.getCohortStartDate() : 
							(ageAtDateIndexDate.equals("COHORTEND") ? patient.getCohortEndDate() : 
							(ageAtDateIndexDate.equals("PRESCRIPTIONSTART") ? prescription.getDate() : 
							(ageAtDateIndexDate.equals("PRESCRIPTIONEND") ? prescription.getEndDate() : 
							ageAtDateValue)))));
						if (ageAtDateUnit.equals("YEARSFRACTION")) {
							results.append(precisionFormat.format((indexDate - patient.getBirthDate())/365.25));
						}
						else if (ageAtDateUnit.equals("MONTHS")) {
							results.append(precisionFormat.format((indexDate - patient.getBirthDate())/(365.25 / 12)));
						}
						else if (ageAtDateUnit.equals("DAYS")) {
							results.append(Integer.toString(indexDate - patient.getBirthDate()));
						}
						else { // ageAtDateUnit.equals("YEARS")
							results.append(Integer.toString(patient.getAgeAtDateInYears(indexDate)));
						}
					}
					// Year
					results.append(String.valueOf(DateUtilities.getYearFromDays(prescription.getDate())));
					// Month
					results.append(String.valueOf(DateUtilities.getMonthFromDays(prescription.getDate())));
					
					int followUpEnd = patient.getCohortEndDate() + (population == null ? 0 : ((population.includePopulationEnd && (patient.getPopulationEndDate() == patient.getCohortEndDate())) ? -1 : 0));
					// EndYear
					results.append(String.valueOf(DateUtilities.getYearFromDays(followUpEnd)));
					// EndMonth
					results.append(String.valueOf(DateUtilities.getMonthFromDays(followUpEnd)));
					// Gender
					results.append(patient.getGender());
					// Prescription Fields
					for (String prescriptionField : outputPrescriptionFields) {
						results.append(prescription.getExtendedAttributeAsString(prescriptionField));
					}
					// Duration
					results.append(String.valueOf(prescription.getDuration()));
					// TimeSincePrevious
					Prescription lastPrescription = lastPrecscriptions.get(prescription.getATC());
					results.append(lastPrescription == null ? "" : String.valueOf(prescription.getDate() - lastPrescription.getEndDate()));
					// DurationPrevious
					results.append(lastPrescription == null ? "" : String.valueOf(lastPrescription.getDuration()));
					// TimeSinceStart
					results.append(String.valueOf(prescription.getDate() - patient.getCohortStartDate()));
					// TimeToEnd
					results.append(String.valueOf(patient.getCohortEndDate() - prescription.getDate()));
					// TimeSincePopulationStart
					results.append(String.valueOf(prescription.getDate() - patient.getPopulationStartDate()));
					// ActiveInCohortTime
					results.append(prescription.isInPeriod(patient.getCohortStartDate(), patient.getCohortEndDate(), false, false) ? "1" : "0");
					// Reference Dates
					for (int referenceDateNr = 0; referenceDateNr < referenceDateList.size(); referenceDateNr++) {
						results.append(String.valueOf(referenceDateList.get(referenceDateNr) - prescription.getDate()));
						results.append(Integer.toString(patient.getAgeAtDateInYears(referenceDateList.get(referenceDateNr))));
					}
					
					// Note workaround since negative int values are not allowed anymore? -defaultWindowStartEvent etc.
					
					// Events of interest
					if (eventsList.size() > 0)
						results.append(IndexDateUtilities.processEvents(patient, prescription.getDate(), eventsList, -defaultWindowStartEvent, eventContinuesBag, eventCategoryBag));

					// Events of interest counts
					if (eventCountsList.size() > 0)
						results.append(IndexDateUtilities.processEventCounts(patient, prescription.getDate(), eventCountsList, -defaultWindowStartEvent, eventCountBag));

					// Events of interest time to
					if (eventTimeToList.size() > 0)
						results.append(IndexDateUtilities.processDaysToNextEvent(patient, prescription.getDate(), eventTimeToList));
					
					// Measurements of interest
					if (measurementsList.size() > 0)
						results.append(IndexDateUtilities.processMeasurements(patient, prescription.getDate(), measurementsList, -defaultWindowStartMeasurement, measContinuesBag, measCategoryBag, addUnitColumn));

					// Measurements of interest counts
					if (measurementCountsList.size() > 0)
						results.append(IndexDateUtilities.processMeasurementCounts(patient, prescription.getDate(), measurementCountsList, -defaultWindowStartMeasurement, measCountBag));

					// Measurements time since index date
					if (measurementTimeSinceList.size() > 0)
						results.append(IndexDateUtilities.processDaysToPreviousMeasurement(patient, prescription.getDate(), measurementTimeSinceList));
					
					// Prescriptions of interest
					if (prescriptionsList.size() > 0)
						results.append(IndexDateUtilities.processPrescriptions(patient, prescription.getDate(), prescriptionsList, -defaultWindowStartPrescription, prescriptionBag));
					
					// Prescriptions extended columns
					if (extendedColumnsList.size() >0) {
						for (Item extendedColumn : extendedColumnsList) {
							String dataType = extendedColumn.getParameters().get(0);
							String valueType = extendedColumn.getParameters().get(1);
							for (String label : extendedColumn.getLookup()){
								String value = "";
								if (dataType.equals("PRESCRIPTION")) {
									value = prescription.getExtendedAttributeAsString(label);
								}
								else { // dataType.equals("PATIENT")
									value = patient.getExtendedAttributeAsString(label);
								}
								value = value.trim();
								if (valueType.equals("VALUE")) {
									results.append(value.equals("") ? " " : value);
								}
								else { // valueType.equals("TIMETO")
									if (value != null) {
										Integer valueDate = DateUtilities.dateToDays(value, DateUtilities.DATE_ON_YYYYMMDD);
										results.append(valueDate == null ? " " : Integer.toString(valueDate - prescription.getDate()));
									}
									else {
										results.append(" ");
									}
								}
							}
						}
					}
					
					// Garvan risk
					if (addGarvanRisk) {					
						String riskBasedOnWeight5 = GarvanRiskFactor.calculateRisk(5, prescription.getDate(), 1825, includingIndexDateGarvan, forHipFractures, false);
						
						if (riskBasedOnWeight5.equals(GarvanRiskFactor.NO_RISK_CALCULATED)){
							results.append(" ");
						}else{
							results.append(riskBasedOnWeight5);
						}
					}
					Jerboa.getOutputManager().writeln(resultsFile,results.toString(),true);
					
					prescriptionSequenceNr++;
				}
				
				lastPrecscriptions.put(prescription.getATC(), prescription);
			}
		}
		
		return patient;
	}
	

	@Override
	public void outputResults() {
		// Close all files
		Jerboa.getOutputManager().closeAll();
	}
	

	@Override
	public void setNeededFiles() {
		setRequiredFile(DataDefinition.PATIENTS_FILE);
		setRequiredFile(DataDefinition.PRESCRIPTIONS_FILE);
		if (eventOfInterest.size() > 0)
			setRequiredFile(DataDefinition.EVENTS_FILE);
		if (measurementOfInterest.size() > 0)
			setRequiredFile(DataDefinition.MEASUREMENTS_FILE);
		if (measurementCount.size() > 0)
			setRequiredFile(DataDefinition.MEASUREMENTS_FILE);
	}
	

	@Override
	public void setNeededExtendedColumns() {
		for (String extendedColumnDefinition : extendedColumnOfInterest) {
			String[] extendedColumnDefinitionSplit = extendedColumnDefinition.split(";");
			if (extendedColumnDefinitionSplit.length > 4) {
				String column = extendedColumnDefinitionSplit[0].trim();
				String file = extendedColumnDefinitionSplit[3].trim();
				if ((!column.equals("")) && (!file.equals(""))) {
					if (file.equals("PATIENT")) {
						setRequiredExtendedColumn(DataDefinition.PATIENTS_FILE, column);
					}
					else if (file.equals("PRESCRIPTION")) {
						setRequiredExtendedColumn(DataDefinition.PRESCRIPTIONS_FILE, column);
					}
				}
			}
		}
	}


	@Override
	public void setNeededNumericColumns() {
		// TODO Auto-generated method stub
		
	}
	
	
	@Override
	public boolean checkParameters(){
		List<String> exceptions = new ArrayList<String>();
		// The following integer parameters can be negative
		exceptions.add("defaultWindowStartEvent");
		exceptions.add("defaultWindowStartMeasurement");
		exceptions.add("defaultWindowStartPrescription");
		return checkParameterValues(exceptions);
	}

}
