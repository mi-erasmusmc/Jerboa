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
 * $Rev:: 4780              $:  Revision of last commit                                   *
 * $Author:: bmosseveld     $:  Author of last commit                                     *
 * $Date:: 					$:  Date and time (CET) of last commit						  *
 ******************************************************************************************/

package org.erasmusmc.jerboa.dataClasses;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;

import org.apache.commons.collections.bidimap.DualHashBidiMap;
import org.apache.commons.lang3.text.StrBuilder;
import org.erasmusmc.jerboa.Jerboa;
import org.erasmusmc.jerboa.config.DataDefinition;
import org.erasmusmc.jerboa.engine.InputFile;
import org.erasmusmc.jerboa.utilities.DateUtilities;
import org.erasmusmc.jerboa.utilities.ExtendedData;
import org.erasmusmc.jerboa.utilities.inputChecking.AttributeChecker;

/**
 * This is a patient object. Contains the patient details and its history.
 * The history contains its possible events, prescriptions and measurements.

 * @author MG
 *
 */
public class Patient implements Comparable<Patient>,Cloneable{

	//the mandatory column indexes
	public static final short COLUMN_PATIENT_ID = 0;
	public static final short COLUMN_BIRTHDATE = 1;
	public static final short COLUMN_GENDER = 2;
	public static final short COLUMN_START_DATE = 3;
	public static final short COLUMN_END_DATE = 4;

	public final int NO_DATA = -1;

	//patient details
	public String ID;
	public int birthDate;
	public byte gender;
	public int startDate;
	public int endDate;
	public String subset;

	//keeps all possible extended attributes
	public ExtendedData extended = new ExtendedData(DataDefinition.PATIENT);

	//population definition
	public int populationStartDate;
	public int populationEndDate;
	public boolean inPopulation;

	//cohort definition
	public int cohortStartDate;
	public int cohortEndDate;
	public boolean inCohort;
	public List<Cohort> cohorts;

	//patient history
	public List<Event> events;
	public List<Prescription> prescriptions;
	public List<Measurement> measurements;
	public List<Prescription> originalPrescriptions;

	//all the column indexes for the extended data
	public int indexPracticeId = ExtendedData.NO_DATA;


	private int anonymizedPatientID;

	/**
	 * Retrieves and sets the indexes of all eventual
	 * extended data columns for this patient. This is
	 * used in order to manipulate the extended data based
	 * on indexes and not hard coded strings.
	 */
	private void setExtendedDataIndexes(){
		this.indexPracticeId = this.extended.getIndexOfAttribute(DataDefinition.PATIENT_PRACTICE_ID);
	}

	//CONSTRUCTORS
	/**
	 * Basic constructor.
	 */
	public Patient(){
		this.subset = DataDefinition.DEFAULT_SUBSET_ID;
		this.events = new ArrayList<Event>();
		this.prescriptions = new ArrayList<Prescription>();
		this.measurements = new ArrayList<Measurement>();
		this.originalPrescriptions = new ArrayList<Prescription>();
		this.cohorts = new ArrayList<Cohort>();

		if (this.extended == null)
			this.extended = new ExtendedData(DataDefinition.PATIENT);

		setExtendedDataIndexes();
	}

	/**
	 * Constructor initializing a patient object with an input patient object.
	 * The patient history is allowed to be null. It is to be used in order to
	 * produce copies of a patient.
	 * @param patient - the patient to process
	 */
	public Patient(Patient patient){

		//copy patient details
		this.ID = patient.ID;
		this.birthDate = patient.birthDate;
		this.gender = patient.gender;
		this.startDate = patient.startDate;
		this.endDate = patient.endDate;
		this.subset = patient.subset;

		this.extended = new ExtendedData(patient.extended);

		//copy patient history
		if (patient.getEvents() != null) {
			this.events = new ArrayList<Event>();
			for (Event event : patient.getEvents())
				this.events.add(new Event(event));
		}
		else {
			this.events = null;
		}

		if (patient.getPrescriptions() != null) {
			this.prescriptions = new ArrayList<Prescription>();
			for (Prescription prescription : patient.getPrescriptions())
				this.prescriptions.add(new Prescription(prescription));
		}
		else {
			this.prescriptions = null;
		}

		if (patient.getMeasurements() != null) {
			this.measurements = new ArrayList<Measurement>();
			for (Measurement measurement : patient.getMeasurements())
				this.measurements.add(new Measurement(measurement));
		}
		else {
			this.measurements = null;
		}

		if (patient.getOriginalPrescriptions() != null) {
			this.originalPrescriptions = new ArrayList<Prescription>();
			for (Prescription prescription : patient.getPrescriptions())
				this.originalPrescriptions.add(new Prescription(prescription));
		}
		else {
			this.originalPrescriptions = null;
		}

		if (patient.getCohorts() != null) {
			this.cohorts = new ArrayList<Cohort>();
			for (Cohort cohort: patient.getCohorts())
				this.cohorts.add(new Cohort(cohort));
		}
		else {
			this.cohorts = null;
		}

		setModifierDefaults();
		setExtendedDataIndexes();
	}

	/**
	 * Constructor initializing the patient details and its history.
	 * The list of events, prescriptions or measurements is allowed to be null.
	 * @param patient - the patient to process
	 * @param events - the list of events for this patient; null allowed
	 * @param prescriptions - the list of prescriptions for this patient; null allowed
	 * @param measurements - the list of measurements for this patient; null allowed
	 */
	public Patient(Patient patient, List<Event> events, List<Prescription> prescriptions, List<Measurement> measurements){
		this.ID = patient.ID;
		this.birthDate = patient.birthDate;
		this.gender = patient.gender;
		this.startDate = patient.startDate;
		this.endDate = patient.endDate;
		this.subset = patient.subset;

		this.extended = patient.extended;

		this.events = events != null && events.size() > 0 ? events : null;
		this.prescriptions = prescriptions != null && prescriptions.size() > 0 ? prescriptions : null;
		this.measurements = measurements != null && measurements.size() > 0 ? measurements : null;
		this.originalPrescriptions = prescriptions != null ? new ArrayList<Prescription>() : null;
		// Copy the prescriptions to the original prescriptions
		if (this.prescriptions != null) {
			for (Prescription prescription : this.prescriptions)
				this.originalPrescriptions.add(new Prescription(prescription));
		}

		this.cohorts = new ArrayList<Cohort>();
		setModifierDefaults();
		setExtendedDataIndexes();
	}

	/**
	 * Constructor of a patient object from an input file line.
	 * Each attribute of the object is brought to a compressed form,
	 * making use of conversion methods and/or look-up tables.
	 * Used in the PatientObjectCreator class.
	 * @param attributes - a line from the patient input file
	 * @param patientsFile - the patient input file containing all formatting details (e.g., data order, date format)
	 */
	public Patient(String [] attributes, InputFile patientsFile){

		this.ID =  attributes[patientsFile.getDataOrder()[Patient.COLUMN_PATIENT_ID]];
		this.gender = AttributeChecker.checkGender(attributes[patientsFile.getDataOrder()[Patient.COLUMN_GENDER]].trim());
		this.birthDate = DateUtilities.dateToDays(attributes[patientsFile.getDataOrder()[Patient.COLUMN_BIRTHDATE]].trim(), patientsFile.getDateFormat());
		this.startDate = DateUtilities.dateToDays(attributes[patientsFile.getDataOrder()[Patient.COLUMN_START_DATE]].trim(), patientsFile.getDateFormat());
		this.endDate = DateUtilities.dateToDays(attributes[patientsFile.getDataOrder()[Patient.COLUMN_END_DATE]].trim(), patientsFile.getDateFormat());

		//extended data
		if (this.extended == null)
			this.extended = new ExtendedData(DataDefinition.PATIENT);
		if (patientsFile.hasExtendedData()){
			this.extended.setData(this.extended.setExtendedAttributesFromInputFile(attributes));
		}

		this.subset = patientsFile.getSubsetIndex() != -1 ? attributes[patientsFile.getSubsetIndex()].trim() : DataDefinition.DEFAULT_SUBSET_ID;

		this.events = new ArrayList<Event>();
		this.prescriptions = new ArrayList<Prescription>();
		this.measurements = new ArrayList<Measurement>();
		this.originalPrescriptions = new ArrayList<Prescription>();
		this.cohorts = new ArrayList<Cohort>();

		setModifierDefaults();
		setExtendedDataIndexes();
	}

	/**
	 * Constructor of a patient object from a patient object file.
	 * Used in the PatientUtilities class. Note: +1 shift of columns due to subset ID.
	 * @param attributes - the attributes of the patient object
	 */
	public Patient(String[] attributes){

		this.subset = attributes[0];
		this.ID = attributes[COLUMN_PATIENT_ID + 1];
		this.gender = Byte.valueOf(attributes[COLUMN_GENDER + 1].trim());
		this.birthDate = Integer.valueOf(attributes[COLUMN_BIRTHDATE + 1]);
		this.startDate = Integer.valueOf(attributes[COLUMN_START_DATE + 1]);
		this.endDate = Integer.valueOf(attributes[COLUMN_END_DATE + 1]);

		//extended data
		if (this.extended == null)
			this.extended = new ExtendedData(DataDefinition.PATIENT);
		this.extended.setData(this.extended.setExtendedAttributesFromPOF(attributes));

		this.events = new ArrayList<Event>();
		this.prescriptions = new ArrayList<Prescription>();
		this.measurements = new ArrayList<Measurement>();
		this.originalPrescriptions = new ArrayList<Prescription>();
		this.cohorts = new ArrayList<Cohort>();

		setModifierDefaults();
		setExtendedDataIndexes();
	}

	//TO STRING METHODS
	@Override
	/**
	 * Returns a string representation of the patient details,
	 * having its gender under a string representation.
	 * @return - the patient details separated by comma and
	 * a newline character appended.
	 */
	public String toString(){
		return (subset+","+this.getPracticeIDAsString()+","+ID+","+birthDate+","+
				convertGender(gender)+","+startDate+","+endDate+System.lineSeparator());
	}

	/**
	 * Returns a string representation of the patient details with the eventual
	 * extended data in its compressed form.
	 * @return - the patient details separated by comma and its extended data, with a newline character appended
	 */
	public String toStringWithExtendedData(){
		return (subset+","+ID+","+birthDate+","+convertGender(gender)+","+startDate+","+endDate+
				toStringExtendedDataCompressed()+System.lineSeparator());
	}

	/**
	 * Returns the patient details with or without newline character.
	 * @param newLine - true if a new line character should be appended at the end
	 * @return - a string representation of the patient details separated by comma,
	 * with or without a new line character appended.
	 */
	public String toString(boolean newLine){
		if (newLine)
			return this.toString();
		else
			return (subset+","+ID+","+birthDate+","+convertGender(gender)+","+startDate+","+endDate);
	}

	/**
	 * Returns the patient details and its extended data with or without newline character.
	 * @param newLine - true if a new line character should be appended at the end
	 * @return - a string representation of the patient details and its extended
	 * data, separated by comma, with or without a new line character appended.
	 */
	public String toStringWithExtendedData(boolean newLine){
		if (newLine)
			return (subset+","+ID+","+birthDate+","+convertGender(gender)+","+startDate+","+endDate+
					toStringExtendedDataCompressed()+System.lineSeparator());
		else
			return (subset+","+ID+","+birthDate+","+convertGender(gender)+","+startDate+","+endDate+
					toStringExtendedDataCompressed());
	}

	/**
	 * Used to output patient details under a compressed form to file
	 * during the compression step.
	 * @param flag - an indication about the type of the data, as it appears in DataDefinition.
	 * @return - a compressed string representation of the patient details
	 */
	public String toStringWithFlag(short flag){
		return (subset+","+ID+","+birthDate+","+gender+","+startDate+","+endDate+
				toStringExtendedDataCompressed()+
				(flag != -1 ? ","+flag : ""));
	}

	/**
	 * Returns the patient details under a string representation
	 * with all the dates in YYYYMMDD format.
	 * @return - a string representation of the patient details separated by comma, including
	 * its extended data.
	 */
	public String toStringConvertedDate(){

		return (subset+","+ID+","+DateUtilities.daysToDate(birthDate)+","+convertGender(gender)+","
				+DateUtilities.daysToDate(startDate)+","+
				DateUtilities.daysToDate(endDate))+
				toStringExtendedData();
	}

	/**
	 * Return a string with all details of the patient, including its history:
	 * patient attributes and extended data, events, prescriptions, measurements.
	 * @return - a string representation of the patient details for debug purposes
	 */
	public String toStringDetails(){

		StrBuilder s = new StrBuilder();

		s.appendln("Patient");
		s.appendln("---------------");
		s.appendln("Id:\t\t\t\t\t"+ID);
		s.appendln("Birth date:\t\t\t"+DateUtilities.daysToDate(birthDate));
		s.appendln("Gender:\t\t\t\t"+convertGender(gender));
		s.appendln("Patient start:\t\t"+DateUtilities.daysToDate(startDate));
		s.appendln("Patient end:\t\t"+DateUtilities.daysToDate(endDate));
		s.appendln("Population start:\t"+DateUtilities.daysToDate(populationStartDate));
		s.appendln("Population end:\t\t"+DateUtilities.daysToDate(populationEndDate));
		s.appendln("In Population:\t\t"+inPopulation);
		s.appendln("Cohort start:\t\t"+DateUtilities.daysToDate(cohortStartDate));
		s.appendln("Cohort end:\t\t\t"+DateUtilities.daysToDate(cohortEndDate));
		s.appendln("In cohort:\t\t\t"+inCohort);
		s.appendln("");

		if (hasExtended()){
			s.appendln("Extended");
			s.appendln("---------------");
			for (Integer extendedColumn : this.extended.getData().keySet())
				s.appendln(this.extended.getAttributeName(extendedColumn)+"\t\t"+
						this.extended.getAttributeAsString(extendedColumn));
			s.appendln("");
		}

		if (hasEvents()){
			s.appendln("Events");
			s.appendln("---------------");
			for (Event e : this.getEvents())
				s.appendln(e.toString());
			s.appendln("");
		}

		s.appendln("");
		if (hasPrescriptions()){
			s.appendln("Prescriptions");
			s.appendln("---------------");
			for (Prescription p : this.getPrescriptions())
				s.appendln(p.toString());
			s.appendln("");
		}

		s.appendln("");
		if (hasPrescriptions()){
			s.appendln("Original Prescriptions");
			s.appendln("---------------");
			for (Prescription p : this.getOriginalPrescriptions())
				s.appendln(p.toString());
			s.appendln("");
		}

		if (hasMeasurements()){
			s.appendln("Measurements");
			s.appendln("---------------");
			for (Measurement m : this.getMeasurements())
				s.appendln(m.toString());
			s.appendln("");
		}

		if (hasCohorts()){
			s.appendln("Cohort");
			s.appendln("---------------");
			for (Cohort c : this.getCohorts())
				s.appendln(c.toString());
			s.appendln("");
		}

		return s.toString();
	}

	/**
	 * Returns the patient details with the dates in YYYYMMDD format.
	 * @param newLine - true if a newline character should be appended
	 * @return - a string representation of the patient details, with or without
	 * a new line character appended
	 */
	public String toStringConvertedDate(boolean newLine){
		if (newLine){
		return (subset+","+ID+","+DateUtilities.daysToDate(birthDate)+","+convertGender(gender)+","
				+DateUtilities.daysToDate(startDate)+","+
				DateUtilities.daysToDate(endDate)+
				toStringExtendedData()+System.lineSeparator());
		} else {
			return (subset+","+ID+","+DateUtilities.daysToDate(birthDate)+","+convertGender(gender)+","
					+DateUtilities.daysToDate(startDate)+","+
					DateUtilities.daysToDate(endDate))+
					toStringExtendedData();
		}
	}

	/**
	 * Returns the string representation of this patient formatted for
	 * data export to CSV file, including its extended data (if any)
	 * Note that the export order should be the same as in data definition.
	 *
	 * @see DataDefinition
	 * @return - a string representation of this patient's attributes separated by comma.
	 */
	public String toStringForExport(){
		return ID +
				"," + DateUtilities.daysToDate(birthDate) +
				"," + convertGender(gender) +
				"," + DateUtilities.daysToDate(startDate) +
				"," + DateUtilities.daysToDate(endDate) +
				toStringExtendedData();
	}

	/**
	 * Returns the string representation of this patient formatted for
	 * data export to CSV file, including its extended data (if any) and
	 * population and cohort start and end dates.
	 * Note that the export order should be the same as in data definition.
	 *
	 * @see DataDefinition
	 * @return - a string representation of this patient's attributes separated by comma.
	 */
	public String toStringForExportLong(boolean withID){
		String record = toStringForExport() +
				"," + (inPopulation ? DateUtilities.daysToDate(getPopulationStartDate()) : "") +
				"," + (inPopulation ? DateUtilities.daysToDate(getPopulationEndDate()) : "") +
				"," + (inCohort ? DateUtilities.daysToDate(getCohortStartDate()) : "") +
				"," + (inCohort ? DateUtilities.daysToDate(getCohortEndDate()) : "");
		if (!withID) {
			record = record.substring(record.indexOf(",") + 1);
		}
		return record;
	}

	/**
	 * Returns the patient details with spaces in order to be aligned
	 * if multiple patients output. Note that the extended data is not included.
	 * @return - a string representation of the patient details
	 */
	public String toStringAligned(){
		return  ID+"  "+
				DateUtilities.daysToDate(birthDate)+"  "+
				(convertGender(gender).equals("F") ? convertGender(gender)+" " : convertGender(gender))+"  "+
				DateUtilities.daysToDate(startDate)+"  "+
				DateUtilities.daysToDate(endDate)+"  ";
	}

	/**
	 * Returns the part of string that will contain the extended data (if any) for this patient in its uncompressed form.
	 * This string is to be added to the first part containing the mandatory data. It is used in the toString methods.
	 * @return - a formatted string containing the extended data columns separated by comma
	 */
	protected String toStringExtendedData(){
		String s = "";
		for (Integer extColumnIndex : this.extended.getKeySet())
			s+= ","+(this.extended.get(extColumnIndex) != ExtendedData.NO_DATA ?
					(this.extended.getAttributeLookUp(extColumnIndex).get(this.extended.get(extColumnIndex))) : "");
		return s;
	}

	/**
	 * Returns the part of string that will contain the extended data for this episodeType (if any) in its compressed form.
	 * This string is to be added to the first part containing the mandatory data. It is used in the toString methods.
	 * Note that it makes use of the look-up tables created for the extended data.
	 * @return - a formatted string containing the extended data columns separated by comma
	 */
	protected String toStringExtendedDataCompressed(){
		String s = "";
		for (Integer extColumnIndex : this.extended.getKeySet())
			s+= ","+this.extended.getData().get(extColumnIndex);
		return s;
	}

	//COMPARATORS
	@Override
	/**
	 * Basic comparator on the patient identifier.
	 * @param patient - the patient to compare this patient with.
	 * @return - 0 if both patient IDs are the same; 1 if this patient ID is superior;
	 *  -1 if this patient ID is inferior to the parameter patient
	 */
	public int compareTo(Patient patient) {
		return this.ID.compareTo(patient.ID);
	}

	/**
	 * Inner class used in order to perform sorting based on subset ID.
	 *
	 * @author MG
	 *
	 */
	public static class CompareSubset implements Comparator<Patient>{

		@Override
		public int compare(Patient p1, Patient p2) {
			return p1.subset.compareTo(p2.subset);
		}
	}

	//GETTERS AND SETTERS
	 /**
     * Forces population and cohort start and end dates to patient start and end.
     * The patient is considered by default in the population and in the cohort
     * until the modifiers are run.
     */
    private void setModifierDefaults(){

    	//set defaults for modifier routines
		this.populationStartDate = startDate;
		this.populationEndDate = endDate;
		this.inPopulation = true;

		this.cohortStartDate = startDate;
		this.cohortEndDate = endDate;
		this.inCohort = true;
    }

	/**
	 * Returns the age of the patient in the beginning of the observation period.
	 * @return - the age of the patient in days as difference between the start date and birth date
	 */
	public int getAgeAtStartDate(){
		return (this.startDate - this.birthDate);
	}

	/**
	 * Returns the age of this patient at the end of the observation period.
	 * @return - the age of the patient in days as difference between the end date and birth date
	 */
	public int getAgeAtEndDate(){
		return (this.endDate - this.birthDate);
	}

	/**
	 * Returns the age of the patient in the beginning of the cohort.
	 * @return - the age of the patient in days as difference between the cohort start date and patient birth date
	 */
	public int getAgeAtCohortStartDate(){
		return (this.cohortStartDate - this.birthDate);
	}

	/**
	 * Returns the age of this patient at the end of the cohort.
	 * @return - the age of the patient in days as difference between the cohort end date and patient birth date
	 */
	public int getAgeAtCohortEndDate(){
		return (this.cohortEndDate - this.birthDate);
	}

	/**
	 * Returns the age of this patient in the beginning of a calendar year.
	 * It assumes that the patient is active during that year.
	 * @param year - the year of reference
	 * @return - the age of this patient in days as difference between the
	 * 1st of January of year and patient birthDate; if negative, -1 is returned
	 */
	public int getAgeInBeginningOfYear(int year){
		int nbDays = DateUtilities.dateToDays(year+"0101",DateUtilities.DATE_ON_YYYYMMDD) - this.birthDate;
		return (nbDays < 0 ? -1 : nbDays);
	}

	/**
	 * Returns the age of this patient at a certain date.
	 * @param date - the date of interest
	 * @return - the age of the patient in days as difference between the patient birth date and date;
	 * if negative, -1 is returned
	 */
	public int getAgeAtDate(int date){
		return (date - birthDate > 0 ? date - birthDate : -1);
	}

	/**
	 * Returns the age of this patient in years at a certain date.
	 * It takes into consideration if date is prior or post the birthday celebration.
	 * @param date - the date of interest
	 * @return - the age of this patient in years;
	 * if negative, -1 is returned
	 */
	public int getAgeAtDateInYears(int date){

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
	 * Returns the age as double taking into account the leap years using the SAS method:
	 *
	 * if n365 equals the number of days between the start and end dates in a 365 day year,
	 * and n366 equals the number of days between the start and end dates in a 366 day year,
	 * the YRDIF calculation is computed as YRDIF=n365/365.0 + n366/366.0.
	 * This calculation corresponds to the commonly understood ACT/ACT day count basis that is
	 * documented in the financial literature.
	 *
	 * double checked with SAS YRDIF function
	 * @param date - the date of interest
	 * @return - the age of this patient at date, under a double representation
	 */
	public double getAge(int date){
		int[] birthDateComponents = DateUtilities.daysToDateComponents(birthDate);
		int[] dateComponents = DateUtilities.daysToDateComponents(date);
		int n365 = 0;
		int n366 = 0;

		// Determine number of days in first year
		int nrDaysFirstYear = 0;
		if (dateComponents[0]>birthDateComponents[0])
			nrDaysFirstYear = DateUtilities.dateToDays(birthDateComponents[0]+"1231",DateUtilities.DATE_ON_YYYYMMDD) -
						      DateUtilities.dateToDays(birthDateComponents)+1;
		else
			nrDaysFirstYear = date - DateUtilities.dateToDays(birthDateComponents);

		if (DateUtilities.isLeapYear(birthDateComponents[0]))
			n366 = nrDaysFirstYear;
		else
			n365 = nrDaysFirstYear;

		// Add all the remaining day
		for (int i=birthDateComponents[0]+1;i<=dateComponents[0];i++){
			//not at the last year yet then add full year
			if (i!=dateComponents[0]){
				if (DateUtilities.isLeapYear(i))
					n366 = n366 + 366;
				else
					n365 = n365 + 365;
			} else {
				int nrDaysLastYear =  date - DateUtilities.dateToDays(dateComponents[0]+"0101",DateUtilities.DATE_ON_YYYYMMDD);
				if (DateUtilities.isLeapYear(dateComponents[0]))
					n366 = n366 + nrDaysLastYear;
				else
					n365 = n365 + nrDaysLastYear;
			}

		}

		return n365/365.0 + n366/366.0;
	}

	/**
	 * Returns the length of the observation period of this patient.
	 * @return - the number of days between startDate and endDate
	 */
	public int getPatientTime(){
		return (this.endDate - this.startDate);
	}

	/**
	 * Returns the length of the period this patient is in population.
	 * @return - the number of days between the population start date and population end date
	 */
	public int getPopulationTime(){
		return (this.populationEndDate - this.populationStartDate);
	}

	/**
	 * Returns the length of the cohort default period of this patient.
	 * @return - the number of days between start date and end date of the cohort period
	 */
	public int getCohortTime(){
		return (this.cohortEndDate - this.cohortStartDate);
	}

	/**
	 * Returns the length of the cohort default period of this patient.
	 * @param  - the number of the cohrt
	 * @return - the number of days between start date and end date of the cohort period.
	 * 			 if the cohortnr does not exist -1 is returned
	 */
	public int getCohortTime(int nr){
		int result = -1;
		if (cohorts.size()>= nr){
			Cohort cohort = cohorts.get(nr);
			result = cohort.cohortEndDate - cohort.cohortStartDate;
		}
		return result;
	}

	/**
	 * Returns the length of the observation period of this patient before the start of a certain year.
	 * It checks if the endDate is not prior to the beginning of the year.
	 * @param year - the calendar year of interest
	 * @return - the number of days between the start date of the patient and 1st of January of year;
	 * if it results in a negative value, the length of the observation period is considered 0 days
	 */
	public int getPatientTimeBeforeStartOfYear(int year){
		int patientTime = Math.min(this.endDate, DateUtilities.dateToDays(year+"0101",DateUtilities.DATE_ON_YYYYMMDD)) - this.startDate;
		return patientTime < 0 ? 0 : patientTime;
	}

	/**
	 * Returns the length of the observation period of this patient after the start of a certain year,
	 * if active at the 1st of January, if not, it's start date is considered.
	 * @param year - the calendar year of interest
	 * @return - the number of days between the 1st of January of the specified year and the end date of the patient;
	 * if it results in a negative value, the length of the observation period is considered 0 days
	 */
	public int getPatientTimeAfterStartOfYear(int year){
		int patientTime = this.endDate - Math.max(this.startDate, DateUtilities.dateToDays(year+"0101",DateUtilities.DATE_ON_YYYYMMDD));
		return patientTime < 0 ? 0 : patientTime;
	}

	/**
	 * Returns the length of the observation period of the patient in a certain calendar year.
	 * If the start date of the patient is superior to 1st of January of year, then the difference between the start date
	 * and the last day of year is considered.
	 * @param year - the calendar year for which the patient time is to be retrieved
	 * @return - the number of days between the 1st of January of the specified year and the 31st of December of the same year;
	 * If somehow a negative value represents the result, 0 is returned
	 *
	 */
	public int getPatientTimeInYear(int year){
		int nbDaysAtStartOfYear = DateUtilities.dateToDays(year+"0101",DateUtilities.DATE_ON_YYYYMMDD);
		int nbDaysAtEndOfYear = DateUtilities.dateToDays((year+1)+"0101",DateUtilities.DATE_ON_YYYYMMDD);
		int patientTime =  (Math.min(endDate,  nbDaysAtEndOfYear) - Math.max(startDate,  nbDaysAtStartOfYear));
		return patientTime < 0 ? 0 : patientTime;
	}

	/**
	 * Returns the amount of patient time in days between the
	 * 1st of January of year and the birthday of this patient.
	 * @param year - the year of interest
	 * @return - the patient time until the birthday of the patient that year.
	 * If it results in a negative value, then zero is returned
	 */
	public int getPatientTimeInYearBeforeBirthday(int year){
		int patientTime  = Math.min(getBirthdayInYear(year), this.endDate) -
				Math.max(this.startDate, DateUtilities.dateToDays(year+"0101",DateUtilities.DATE_ON_YYYYMMDD));
		return patientTime < 0 ? 0 : patientTime;
	}

	/**
	 * Returns the amount of patient time in days between the
	 * 1st of January of year and the birthday of this patient in that year.
	 * Note that the purpose of this method as opposed to getPatientTimeInYearBeforeBirthday(year)
	 * is purely optimization. It receives the birthday as parameter and does not have to calculate it at
	 * each method call.
	 * @param year - the year of interest
	 * @param birthday - the number of days from the first legal date and the birthday celebration that year
	 * @return - the patient time until the birthday of the patient that year.
	 * If it results in a negative value, then zero is returned
	 */
	public int getPatientTimeInYearBeforeBirthday(int year, int birthday){
		int patientTime  = Math.min(birthday, this.endDate) -
				Math.max(this.startDate, DateUtilities.dateToDays(year+"0101",DateUtilities.DATE_ON_YYYYMMDD));
		return patientTime < 0 ? 0 : patientTime;
	}

	/**
	 * Returns the amount of patient time in days between the
	 * the birthday of this patient and the end of year.
	 * @param year - the year of interest
	 * @return - the patient time from birthday of the patient that year and end of year.
	 * If it results in a negative value, then zero is returned.
	 * Note that the actual birthday is added in this interval and NOT in the getPatientTimeInYearBeforeBirthday(year)
	 */
	public int getPatientTimeInYearAfterBirthday(int year){
		int patientTime = (Math.min(this.endDate, DateUtilities.dateToDays(year+"1231",DateUtilities.DATE_ON_YYYYMMDD)) -
				Math.min(this.endDate, Math.max(getBirthdayInYear(year), this.startDate))) + 1;
		return patientTime < 0 ? 0 : patientTime;
	}

	/**
	 * Returns the amount of patient time in days between the
	 * the birthday of this patient during this year and the end of this year.
	 * Note that the purpose of this method as opposed to getPatientTimeInYearBeforeBirthday(year)
	 * is purely optimization. It receives the birthday as parameter and does not have to calculate it at
	 * each method call.
	 * @param year - the year of interest
	 * @param birthday - the number of days from the first legal date and the birthday celebration that year
	 * If it results in a negative value, then zero is returned.
	 * Note that the actual birthday is added in this interval and NOT in the getPatientTimeInYearBeforeBirthday(year)
	 * @return - the patient time from birthday of the patient that year and end of year.
	 */
	public int getPatientTimeInYearAfterBirthday(int year, int birthday){
		int patientTime = (Math.min(this.endDate, DateUtilities.dateToDays(year+"1231",DateUtilities.DATE_ON_YYYYMMDD)) -
				Math.min(this.endDate, Math.max(birthday, this.startDate))) + 1;
		return patientTime < 0 ? 0 : patientTime;
	}

	/**
	 * Returns the two (if applicable) different age values for this patient during a certain calendar year
	 * and the observation time for each one of the two ages. The result is pair-based. First age with
	 * its observation time (e.g., result[0] and result[1]) and second age with its observation time
	 * (e.g., result[2] and result[3]).
	 * @param year - the calendar year for which the observation time and ages are requested
	 * @return - an array containing two pairs of possible ages during year
	 */
	//NOT USED
	public int[] getPatientTimePerAgesInCalendarYear(int year){
		int[] result = new int[4];

		//get nb days for first day of year and last day of year
		int nbDaysAtStartOfYear = DateUtilities.dateToDays(year+"0101",DateUtilities.DATE_ON_YYYYMMDD);
		int nbDaysAtEndOfYear = DateUtilities.dateToDays((year+1)+"0101",DateUtilities.DATE_ON_YYYYMMDD);

		//get ages of patient during that year
		result[0] = (nbDaysAtStartOfYear - birthDate);
		result[2] = (nbDaysAtEndOfYear - birthDate);

		//get the observation time range for year
		int observationStart = Math.max(this.startDate, nbDaysAtEndOfYear);
		int observationEnd = Math.max(this.startDate, nbDaysAtEndOfYear);

		//compute the celebration day of the birth date
		int[] birthdateComponents = DateUtilities.daysToDateComponents(this.birthDate);
		birthdateComponents[0] = year;
		int birthdayCelebration = DateUtilities.dateToDays(birthdateComponents);

		//put observation times per age during year; iff > 0;
		int patientTimeBeforeCelebration, patientTimeAfterCelebration;
		patientTimeBeforeCelebration = (birthdayCelebration - observationStart);
		patientTimeAfterCelebration = (observationEnd - birthdayCelebration);
		result[1] =  patientTimeBeforeCelebration > 0 ? patientTimeBeforeCelebration : 0;
		result[3] =  patientTimeAfterCelebration > 0 ? patientTimeAfterCelebration : 0;

		return result;
	}

	/**
	 * Returns the patient time in days before a certain date
	 * as the difference between the date and the patient start date.
	 * If the result is negative, then zero is returned.
	 * @param date - the date of interest
	 * @return - the patient time in days before date
	 */
	public int getPatientTimeBeforeDateInDays(int date){
		return (date - this.startDate < 0 ? 0 : date - this.startDate);
	}

	/**
	 * Returns the patient time in days from the cohort start until date.
	 * If the result is negative, then zero is returned.
	 * @param date - the date of interest
	 * @return - the patient time in days from cohort start to date
	 */
	public int getPatientTimeFromCohortStartInDays(int date){
		return (date - this.cohortStartDate < 0 ? 0 : date - this.cohortStartDate);
	}

	/**
	 * Returns the patient time in months before a certain date
	 * as a difference between the date and the patient start date
	 * divided by the average number of days per month.
	 * If the result is negative, then zero is returned.
	 * @param date - the date of interest
	 * @return - the patient time in months before the date
	 */
	public double getPatientTimeBeforeDateInMonths(int date){
		return (date - this.startDate < 0 ? 0 :
			(date - this.startDate)/DateUtilities.daysPerMonth);
	}

	/**
	 * Returns the patient time in days after a certain date
	 * as a difference between the patient end date and the date.
	 * If the result is negative, then zero is returned.
	 * @param date - the date of interest
	 * @return - the patient time in days after the date
	 */
	public int getPatientTimeAfterDateInDays(int date){
		return (this.endDate - date < 0 ? 0 : this.endDate - date);
	}

	/**
	 * Returns the patient time in days after a certain date
	 * and the end of the cohort.
	 * If the result is negative, then zero is return.
	 * @param date - the date of interest
	 * @return - the patient time in days after date and until cohort end.
	 */
	public int getPatientTimeUntilCohortEndInDays(int date){
		return (this.cohortEndDate - date < 0 ? 0 : this.cohortEndDate - date);
	}


	/**
	 * Returns the patient time in months after a certain date
	 * as a difference between the date and the patient end date,
	 * divided by the average number of days per month.
	 * If the result is negative, then zero is return.
	 * @param date - the date of interest
	 * @return - the patient time in months after date
	 */
	public double getPatientTimeAfterDateInMonths(int date){
		return (this.endDate - date < 0 ? 0 :
			(this.endDate - date)/DateUtilities.daysPerMonth);
	}

	/**
	 * Returns the number of days from the first legal date until the
	 * birthday "celebration" of this patient in the year passed as argument.
	 * If year is not a leap year and the patient is born in a leap year,
	 * then the birthday is set to the 1st of March of year.
	 * @param year - the year of interest
	 * @return - the number of days representing the birthday celebration in year
	 */
	public int getBirthdayInYear(int year){
		int[] birthdateComponents = DateUtilities.daysToDateComponents(this.birthDate);
		boolean bornOnLeapDay = DateUtilities.isLeapYear(birthdateComponents[0])
				&& (birthdateComponents[1] == 2 && birthdateComponents[2] == 29);
		birthdateComponents[0] = year;
		//set the birthday as 1st of march if patient born in leap year and current year is not a leap year
		if (bornOnLeapDay && !DateUtilities.isLeapYear(year)){
			birthdateComponents[1] = 3;
			birthdateComponents[2] = 1;
		}
		return DateUtilities.dateToDays(birthdateComponents);
	}


	//SPECIFIC METHODS
	/**
	 * Converts the gender of a patient from a byte form to a string representation.
	 * It is based on constant values stored in the DataDefinition class.
	 * @param gender - the gender of this patient under byte representation
	 * @return - a corresponding String representation of the gender
	 */
	public static String convertGender(byte gender){
		switch (gender){
		case DataDefinition.FEMALE_GENDER :
			return "F";
		case DataDefinition.MALE_GENDER :
			return "M";
		}
		return "U";
	}

	/**
	 * Checks if the patient is born on a leap day (i.e., 29th of February).
	 * @return - true if the patient is born on a leap day; false otherwise
	 */
	public boolean isBornOnLeapDay(){
		int[] birthdateComponents = DateUtilities.daysToDateComponents(this.birthDate);
		return DateUtilities.isLeapYear(birthdateComponents[0])
				&& (birthdateComponents[1] == 2 && birthdateComponents[2] == 29);
	}

	/**
	 * Sorts the measurements of this patient by date.
	 */
	public void sortMeasurements(){
		Collections.sort(this.measurements);
	}

	/**
	 * Sorts the events of this patient by date.
	 */
	public void sortEvents(){
		Collections.sort(this.events);
	}

	/**
	 * Sorts the prescriptions of this patient by date.
	 */
	public void sortPrescriptions(){
		Collections.sort(this.prescriptions);
	}

	/**
	 * Sorts the original prescriptions of this patient by date.
	 */
	public void sortOriginalPrescriptions(){
		Collections.sort(this.originalPrescriptions);
	}

	/**
	 * Sorts the cohorts of this patient by cohort startdate.
	 */
	public void sortCohort(){
		Collections.sort(this.cohorts);
	}

	/**
	 * Verifies if date is between the cohort start and the cohort end.
	 * @param date - the date to be checked
	 * @return - true if the date is in the cohort; false otherwise
	 */
	public boolean dateInCohort(int date) {
		return (date >= cohortStartDate) && (date < cohortEndDate);
	}

	/**
	 * Verifies if date is between the cohort start and the cohort end
	 * for a selected cohort
	 * @param date - the date to be checked. End date us not included
	 * @return - true if the date is in the cohort; false otherwise
	 * 			 returns false if the cohort does not exists
	 */
	public boolean dateInCohort(int date, int nr) {
		boolean result = false;
		if (nr >= cohorts.size()) {
			Cohort cohort = cohorts.get(nr);
			result =  (date >= cohort.cohortStartDate) && (date < cohort.cohortEndDate);
		}
		return result;
	}

	/*
	 * Checks if this the first cohort of this type.
	 * @param type	- type of cohort
	 * @return 		- true if first, false otherwise.
	 * 				  It there is no cohort defined for that date false is returned
	 */
	public boolean isInFirstCohortOfType(int date) {
		boolean result = true;
		String type = getCohortType(date);
		if (type != "None") {
			for (Cohort cohort : getCohorts()) {
				if (cohort.isInCohort(date))
					break;

				if (cohort.getType().equals(type)){
					result = false;
					break;
				}
			}
		} else
			result = false;
		return result;
	}

	/**
	 * Returns the type label of the cohort the patient is in at date
	 * @param date - the date to be checked. End date is not included
	 * @return - label of the cohort type, "None" otherwise
	 * 			 returns false if the cohort does not exists
	 */
	public String getCohortType(int date) {
		String result = "None";
		for (Cohort cohort : this.getCohorts()) {
			if ((date >= cohort.cohortStartDate) && (date < cohort.cohortEndDate)) {
				result = cohort.getType();
				break;
			}
		}
		return result;
	}

	/**
	 * Returns the start date of the cohort the patient is in at date
	 * @param date - the date to be checked. End date is not included
	 * @return - data as int or -1 if no cohort is found
	 */
	public int getCohortStartDate(int date) {
		int result = -1;
		for (Cohort cohort : this.getCohorts()) {
			if ((date >= cohort.cohortStartDate) && (date < cohort.cohortEndDate)) {
				result = cohort.getCohortStartDate();
				break;
			}
		}
		return result;
	}

	/**
	 * Returns the start date of the cohort the patient is in at date
	 * @param date - the date to be checked. End date is not included
	 * @return - data as int or -1 if no cohort is found
	 */
	public int getCohortEndDate(int date) {
		int result = -1;
		for (Cohort cohort : this.getCohorts()) {
			if ((date >= cohort.cohortStartDate) && (date < cohort.cohortEndDate)) {
				result = cohort.getCohortEndDate();
				break;
			}
		}
		return result;
	}

	/**
	 * Returns the sequence number of the cohort the patient is in at date
	 * @param date - the date to be checked. End date is not included
	 * @return - sequence number as int starting at 0 or -1 if no cohort is found
	 */
	public int getCohortSequenceNr(int date) {
		int result = -1;
		int sequenceNr = 1;
		for (Cohort cohort : this.getCohorts()) {
			if ((date >= cohort.cohortStartDate) && (date < cohort.cohortEndDate)) {
				result = sequenceNr;
				break;
			}
			sequenceNr++;
		}
		return result;
	}

	/**
	 * Verifies if date is between the cohort start and the cohort end,
	 * optionally with the cohort end date inclusive.
	 * @param date - the date to be checked
	 * @param endInclusive - true if the actual end day of the cohort should be included; false otherwise
	 * @return - true if the date is in the cohort; false otherwise
	 */
	public boolean dateInCohort(int date,  boolean endInclusive) {
		if (endInclusive)
			return (date >= cohortStartDate) && (date <= cohortEndDate);
		else
			return (date >= cohortStartDate) && (date < cohortEndDate);
	}

	/**
	 * Verifies if date is between the population start and the population end.
	 * Note that population start date is inclusive.
	 * @param date - the date to be checked
	 * @return - true if the date is in the population; false otherwise
	 */
	public boolean dateInPopulation(int date) {
		return (date >= populationStartDate) && (date < populationEndDate);
	}

	/**
	 * Checks if the patient is active at the specified date.
	 * It is not the same as isInPopulation() which is specific for
	 * the PopulationDefinition modifier.
	 * @param days - the number of days from the first legal date until the date of interest
	 * @return - true if the difference between the start date
	 *  of the patient and the number of days is positive; false otherwise
	 */
	public boolean isActive(int days){
		return days - this.startDate >= 0;
	}

	//GETTERS AND SETTERS FOR OBJECT ATTRIBUTES
	public List<Event> getEvents() {
		return events;
	}

	public List<Prescription> getPrescriptions() {
		return prescriptions;
	}

	public List<Measurement> getMeasurements() {
		return measurements;
	}

	public List<Prescription> getOriginalPrescriptions() {
		return originalPrescriptions;
	}

	public ExtendedData getExtended() {
		return extended;
	}

	public List<Cohort> getCohorts() {
		return cohorts;
	}

	public void setAnonymizedPatientId(int anonymizedId) {
		this.anonymizedPatientID = anonymizedId;
	}

	public void setEvents(List<Event> events) {
		this.events = events;
	}

	public void setPrescriptions(List<Prescription> prescriptions) {
		this.prescriptions = prescriptions;
	}

	public void setMeasurements(List<Measurement> measurements) {
		this.measurements = measurements;
	}

	public void setOriginalPrescriptions(List<Prescription> prescriptions) {
		this.originalPrescriptions = prescriptions;
	}

	public void setCohorts(List<Cohort> cohorts) {
		this.cohorts = cohorts;
	}

	public void setExtended(ExtendedData extended) {
		this.extended = extended;
	}

	public boolean hasEvents(){
		return this.events != null && this.events.size() > 0;
	}

	public boolean hasPrescriptions(){
		return this.prescriptions != null && this.prescriptions.size() > 0;
	}

	public boolean hasMeasurements(){
		return this.measurements != null && this.measurements.size() > 0;
	}

	public boolean hasOriginalPrescriptions(){
		return this.originalPrescriptions != null && this.originalPrescriptions.size() > 0;
	}

	public boolean hasCohorts(){
		return this.cohorts != null && this.cohorts.size() > 0;
	}

	public boolean hasExtended(){
		return this.extended != null && this.extended.getData() != null
				&& this.extended.getData().size() > 0;
	}

	public String getAnonymizedPatientId() {
		return Jerboa.unitTest ? ID : Integer.toString(anonymizedPatientID);
	}

	public String getGender(){
		return convertGender(gender);
	}

	public int getBirthYear(){
		return DateUtilities.getYearFromDays(birthDate);
	}

	public int getBirthMonth(){
		return DateUtilities.getMonthFromDays(birthDate);
	}

	public int getBirthDate(){
		return birthDate;
	}

	public int getStartYear(){
		return DateUtilities.getYearFromDays(startDate);
	}

	public int getEndYear(){
		return DateUtilities.getYearFromDays(endDate);
	}

	public int getPopulationStartDate() {
		return populationStartDate;
	}

	public void setPopulationStartDate(int populationStartDate) {
		this.populationStartDate = populationStartDate;
	}

	public int getPopulationEndDate() {
		return populationEndDate;
	}

	public void setPopulationEndDate(int populationEndDate) {
		this.populationEndDate = populationEndDate;
	}

	public boolean isInPopulation() {
		return inPopulation;
	}

	public void setInPopulation(boolean inPopulation) {
		this.inPopulation = inPopulation;
	}

	public int getCohortStartDate() {
		return cohortStartDate;
	}

	public void setCohortStartDate(int cohortStartDate) {
		this.cohortStartDate = cohortStartDate;
	}

	public int getCohortEndDate() {
		return cohortEndDate;
	}

	public void setCohortEndDate(int cohortEndDate) {
		this.cohortEndDate = cohortEndDate;
	}

	public String getPatientID(){
		return ID;
	}

	public boolean isInCohort() {
		return inCohort;
	}

	public void setInCohort(boolean inCohort) {
		this.inCohort = inCohort;
	}

	/**
	 * Returns the difference in days between the
	 * end date and start date of this patient.
	 * @return - the patient time in days; 0 if result is negative
	 */
	public int getPatientTimeInDays(){
		return this.endDate > this.startDate ?
				this.endDate - this.startDate : 0;
	}

	/**
	 * Returns the difference in months between the
	 * end date and start date of this patient. Rounded down.
	 * @return - the patient time in months; 0 if negative value
	 */
	public int getPatientTimeInMonths(){
		return this.endDate > this.startDate ?
				(int)((this.endDate - this.startDate)/DateUtilities.daysPerMonth) : 0;
	}

	/**
	 * Returns the difference in years between the
	 * end date and start date of this patient. Rounded down.
	 * @return - the patient time in years; 0 if negative
	 */
	public int getPatientTimeInYears(){
		return this.endDate > this.startDate ?
				(int)((this.endDate - this.startDate)/DateUtilities.daysPerYear) : 0;
	}

	//GETTERS AND SETTERS FOR EXTENDED DATA
	/**
	 * Returns the practice ID (if any) for this patient under a string representation.
	 * Note that the practice ID is considered to be extended data.
	 * @return - the practice ID as string
	 */
	public String getPracticeIDAsString(){
		String practiceID = this.extended.getAttributeAsString(indexPracticeId);
		return practiceID.equals(DataDefinition.NO_DATA) ? "" : practiceID;
	}

	/**
	 * Returns the practice ID (if any) for this patient in its compressed form.
	 * Note that the practice ID is considered to be extended data.
	 * @return - the practice ID as the index in the look-up table
	 */
	public int getPracticeID(){
		return this.extended.getAttribute(indexPracticeId);
	}

	/**
	 * Returns the anonymized practice ID (if any) for this patient in its compressed form.
	 * Note that the practice ID is considered to be extended data.
	 * @return - the practice ID as the index in the look-up table
	 */
	public int getAnonymizedPracticeID(){
		return this.extended.getAttribute(indexPracticeId);
	}

	//MAIN METHOD FOR TESTING
	public static void main(String[] args) {
		Patient patient = new Patient();
		patient.birthDate = DateUtilities.dateToDays("19441201",DateUtilities.DATE_ON_YYYYMMDD);
		int date =  DateUtilities.dateToDays("20091201",DateUtilities.DATE_ON_YYYYMMDD);
		double age = patient.getAge(date);
		System.out.println("Age: " + age); //Age: 68.833565
	}

	//WRAPPERS FOR EXTENDED DATA

	public boolean hasExtendedAttribute(int index){
		return this.extended.hasAttribute(index);
	}

	public boolean hasExtendedAttribute(String extendedAttribute) {
		extendedAttribute = extendedAttribute.toLowerCase();
		return (this.extended.hasAttribute(extendedAttribute) ||
				this.extended.getAsIs(extendedAttribute) != null);
	}

	public Integer getExtendedAttribute(int index) {
		return this.extended.getAttribute(index);
	}

	public void setExtendedAttribute(int attributeIndex, int value) {
		if (this.extended.getAttribute(attributeIndex) != NO_DATA)
			this.extended.put(attributeIndex, value);
	}

	public void setExtendedAttribute(int attributeIndex, String value) {
		if (this.extended.getAttribute(attributeIndex) != NO_DATA)
			this.extended.put(attributeIndex, value);
	}

	public void setExtendedAttribute(String attributeName, String value) {
		attributeName = attributeName.toLowerCase();
		if (this.extended.getAttribute(attributeName) == null || this.extended.getAttribute(attributeName) == NO_DATA)
			this.extended.setAsIs(attributeName, value);
		else {
			this.extended.setExtendedAttributePatient(this, attributeName, value);
		}
	}

	public Integer getExtendedAttribute(String attribute) {
		return this.extended.getAttribute(attribute.toLowerCase());
	}

	public String getExtendedAttributeAsString(String attribute) {
		return extended.getAttributeAsString(attribute.toLowerCase());
	}

	public String getExtendedAttributeAsString(int index){
		return this.extended.getAttributeAsString(index);
	}

	public Integer getIndexOfExtendedAttribute(String attribute){
		return this.extended.getIndexOfAttribute(attribute.toLowerCase());
	}

	public int getIndex(DualHashBidiMap list, String value){
		return this.extended.getIndex(list, value);
	}

	public String getValue(DualHashBidiMap list, int key){
		return this.extended.getValue(list, key);
	}

	public DualHashBidiMap getExtendedAttributeLookUp(Integer extendedColumnIndex){
		return this.extended.getAttributeLookUp(extendedColumnIndex);
	}

	public DualHashBidiMap getExtendedAttributeLookUp(String attribute){
		return this.extended.getAttributeLookUp(attribute.toLowerCase());
	}

	public HashMap<Integer, Integer> getExtendedData(){
		return this.extended.getData();
	}

	public String getExtendedAttributeName(Integer extendedColumnIndex){
		return this.extended.getAttributeName(extendedColumnIndex);
	}

}
