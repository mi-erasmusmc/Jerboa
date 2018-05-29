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

package org.erasmusmc.jerboa.dataClasses;



/******************************************************************************************
 * Jerboa software version 3.0 - Copyright Erasmus University Medical Center, Rotterdam   *
 *																				          * 	
 * Author: Marius Gheorghe (MG) - department of Medical Informatics						  *			
 * 																						  *	
 * $Rev:: 4851              $:  Revision of last commit                                   *
 * $Author:: bmosseveld     $:  Author of last commit                                     *
 * $Date:: 					$:  Date and time (CET) of last commit						  *
 ******************************************************************************************/

import java.util.Comparator;
import java.util.List;

import org.apache.commons.lang3.StringUtils;
import org.erasmusmc.jerboa.Jerboa;
import org.erasmusmc.jerboa.config.DataDefinition;
import org.erasmusmc.jerboa.engine.InputFile;
import org.erasmusmc.jerboa.utilities.DateUtilities;
import org.erasmusmc.jerboa.utilities.InputFileUtilities;

/**
 * This is a prescription object. It should contain the ID of the patient, a date, a valid type (ATC) and a duration.
 * Other extended attributes are added if they are present in the input file.
 * 
 * @author MG
 *
 */
public class Prescription extends Episode{	

	//prescription specific column
	public static final short COLUMN_DURATION = 3;
	private int duration;
	
	//indexes of the extended data  
	public final int indexDose = getIndexOfExtendedAttribute(DataDefinition.PRESCRIPTION_DOSE);
	public final int indexIndication = getIndexOfExtendedAttribute(DataDefinition.PRESCRIPTION_INDICATION);
	public final int indexFormulation = getIndexOfExtendedAttribute(DataDefinition.PRESCRIPTION_FORMULATION);
	public final int indexStrength = getIndexOfExtendedAttribute(DataDefinition.PRESCRIPTION_STRENGTH);
	public final int indexVolume = getIndexOfExtendedAttribute(DataDefinition.PRESCRIPTION_VOLUME);
	public final int indexPrescriberID = getIndexOfExtendedAttribute(DataDefinition.PRESCRIPTION_PRESCRIBER_ID);
	public final int indexPrescriberType = getIndexOfExtendedAttribute(DataDefinition.PRESCRIPTION_PRESCRIBER_TYPE);
	
	//specific attributes
	private int combinationStartDate = -1;
	private boolean allComponentsStart = true;
	
	
	//CONSTRUCTORS
	/**
	 * Basic constructor
	 */
	public Prescription(){
		super(Episode.EPISODE_TYPE_PRESCRIPTION);
	}
	
	/**
	 * Constructor accepting a prescription object.
	 * @param p - the prescription used to initialize this prescription
	 */
	public Prescription(Prescription p){
		super(p);

		this.duration = p.duration;
		this.combinationStartDate = p.combinationStartDate;
		this.allComponentsStart = p.allComponentsStart;
		if (p.hasDose()) this.setDose(p.getDose());
		if (p.hasFormulation()) this.setFormulation(p.getFormulation());
		if (p.hasStrength()) this.setStrength(p.getStrength());
		if (p.hasVolume()) this.setVolume(p.getVolume());
		if (p.hasIndication()) this.setIndication(p.getIndication());
		if (p.hasPrescriberId()) this.setPrescriberId(p.getPrescriberId());
		if (p.hasPrescriberType()) this.setPrescriberType(p.getPrescriberType());
	}

	/**
	 * Constructor of a prescription object from an input file line.
	 * Each attribute of the object is brought to a compressed form,
	 * making use of conversion methods and/or look-up tables.
	 * Used in the PatientObjectCreator class.
	 * @param attributes - a line from the prescription input file 
	 * @param prescriptionsFile - the prescriptions input file containing all formatting details (e.g., data order, date format)
	 */
	public Prescription(String [] attributes, InputFile prescriptionsFile){
		super(Episode.EPISODE_TYPE_PRESCRIPTION, attributes, prescriptionsFile);
		
		String atc = attributes[prescriptionsFile.getDataOrder()[COLUMN_TYPE]].trim().toUpperCase();
		InputFileUtilities.addToList(InputFileUtilities.getPrescriptionAtcs(), atc);
		this.type = getIndex(InputFileUtilities.getPrescriptionAtcs(), atc);
		this.duration = attributes[prescriptionsFile.getDataOrder()[Prescription.COLUMN_DURATION]].trim().equals("") ?
				0 : (int)Double.parseDouble(attributes[prescriptionsFile.getDataOrder()[Prescription.COLUMN_DURATION]].trim());

		this.combinationStartDate = this.date;
	}
	
	/**
	 * Constructor of a prescription object from a patient object file.
	 * Used in the PatientUtilities class. Note: +1 shift of columns due to subset ID.
	 * @param attributes - the attributes of the prescription object 
	 */
	public Prescription(String[] attributes){
		super(Episode.EPISODE_TYPE_PRESCRIPTION, attributes);

		this.duration = attributes[COLUMN_DURATION + SUBSET_OFFSET].equals("") ? 
				NO_DATA : Integer.valueOf(attributes[COLUMN_DURATION + SUBSET_OFFSET]);
		this.combinationStartDate = this.date;
	}
	
	//COMPARATORS
	/**
	 * Comparator for prescriptions to sort them on date and then on duration (long to short).
	 */
	public static Comparator<Prescription> DateUpDurationDownComparator = new Comparator<Prescription>() {
		@Override
		public int compare(Prescription p1, Prescription p2) {
			int result = p1.date - p2.date;
			if (result == 0)
				result = p2.getDuration() - p1.getDuration();
			return result;
		}
	};
	
	/**
	 * Comparator for prescriptions to sort them on date and then on duration (long to short).
	 */
	public static Comparator<Prescription> DateUpATCUpDurationUpComparator = new Comparator<Prescription>() {
		@Override
		public int compare(Prescription p1, Prescription p2) {
			int result = p1.date - p2.date;
			if (result == 0)
				result = p1.getATC().compareTo(p2.getATC());
			if (result == 0)
				result = p1.getDuration() - p2.getDuration();
			return result;
		}
	};

	/**
	 * Sort by date and then by duration (longest first). 
	 */
	public static Comparator<Prescription> sortByDateAndDuration = new Comparator<Prescription>() {
			
			public int compare (Prescription p1, Prescription p2) {
				if (p1.date < p2.date) {
					return -1;
				}
				else if (p1.date > p2.date) {
					return 1;
				}
				else if (p1.duration > p2.duration) {
					return -1;
				}
				else if (p1.duration < p2.duration) {
					return 1;
				}
				else {
					return 0;
				}
			}
	};
	
	/**
	 * Compares two prescriptions based on their date of occurrence.
	 * @param p - the prescription to compare this episode with
	 * @return - if the prescription dates are equal compare the durations. The one with the longest
	 * duration comes first.
	 * If this prescription occurred at a later date than p, then it is considered greater
	 * and return value is 1; this prescription is considered smaller than p if result is negative;
	 * if equal the result is 0.
	 */
	public int compareTo(Prescription p) {
		int result = this.date - p.date;
		if (result == 0)
			result = p.duration - this.duration;
		return result;
	}

	//TO STRING METHODS
	/**
	 * Returns the character string representation of this prescription in its uncompressed form.
	 * The date is in YYYYMMDD format and the ATC in its original form.
	 * Note that if the look-up tables are not present for this run, the compressed form of the ATC will be used.
	 * @return - the string representation with all details of this prescription in an uncompressed form.
	 */
	public String toStringUncompressed(){
		return (subset+","+patientID+
				(InputFileUtilities.getPrescriptionAtcs() != null ?
						","+InputFileUtilities.getPrescriptionAtcs().get(Integer.valueOf(type)) : ","+type)+","+
						DateUtilities.daysToDate(date)+"-"+(duration != NO_DATA ? DateUtilities.daysToDate(date+duration) : "")+","+duration+
						(toStringExtendedData().equals("") ? toStringExtendedDataAsIs() : toStringExtendedData())); 
	}

	/**
	 * Returns the character string representation of this prescription in its uncompressed form.
	 * @return - the string representation with all details of this measurement
	 */
	public String toString(){
		return toStringUncompressed();
	}	

	/**Return the character string representation of the compressed version of this Measurement object
	 * to be written to a patient object file (.pof).
	 * It makes use of the look-up tables created for the measurement attributes and adds a flag at 
	 * the end based on the episode type. The extended data is also taken into consideration.
	 * @param flag - the episode type specific flag as defined in DataDefinition
	 * @return - a string representation of this prescription in its compressed form
	 * 
	 */
	public String toStringWithFlag(short flag){
		return (subset+","+patientID+","+type+","+date+","+duration+
				toStringExtendedDataCompressed()+
				(flag != NO_DATA ? ","+flag : ""));
	}

	/**
	 * Return the character string representation of a Prescription
	 * object with the date under the format YYYYMMDD.
	 * The extended data is also taken into consideration (if any).
	 */
	public String toStringConvertedDate(){
		return (subset+","+patientID+","+type+","+
				DateUtilities.daysToDate(date)+","+duration+
				toStringExtendedData());
	}

	/**
	 * Return the character string representation of this Prescription
	 * object with any date under the format YYYYMMDD without leading subset.
	 * The extended data is also taken into consideration (if any).
	 * @return - this prescription with the date on YYYYMMDD format and no subset added 
	 */
	public String toStringConvertedDateNoSubset(){
		return (patientID+","+type+","+
				DateUtilities.daysToDate(date)+","+duration+
				toStringExtendedData());
	}

	/**
	 * Returns the string representation of this prescription formatted for 
	 * data export to CSV file. The available extended data is also exported.
	 * Note that the export order should be the same as in data definition.
	 * 
	 * @see DataDefinition 
	 * @return - a string representation of this prescription
	 */
	public String toStringForExport(){
		return patientID +
				"," + getType() + 
				"," + DateUtilities.daysToDate(date) +
				"," + getDuration() + 
				toStringExtendedData(); 
	}

	//GETTERS AND SETTERS
	/**
	 * Returns the date when the prescription ends.
	 * @return - the date of the prescription plus the prescription duration
	 */
	public int getEndDate() {
		return date + (duration != NO_DATA ? duration : 0) ;
	}

	/**
	 * Returns the date when the prescription starts.
	 * @return - the date attribute of the prescription
	 */
	public int getStartDate() {
		return date;
	}

	/**
	 * Returns the year of this prescription.
	 * @return - the year component of this date
	 */
	public int getPrescriptionYear(){
		return DateUtilities.daysToDateComponents(this.date)[0];
	}
	
	@Override
	public String getTypeOfEpisode() {
		return DataDefinition.EPISODE_PRESCRIPTION;
	}
	
	/**
	 * Return the uncompressed value of the ATC if information is available.
	 * @return - the uncompressed String representation of the ATC code
	 */
	@Override
	public String getType() {
		return getValue(InputFileUtilities.getPrescriptionAtcs(), type);
	}	
	
	/**
	 * Sets the type of this prescription and ads it into the look-up table if not present.
	 * @param type - the new type of the prescription
	 */
	@Override
	public void setType(String type) {
		InputFileUtilities.addToPrescriptionLookup(InputFileUtilities.getPrescriptionAtcs(), type);
		this.type = getIndex(InputFileUtilities.getPrescriptionAtcs(), type);
	}
	
	/**
	 * Wrapper for setType.. for some reason.
	 * @param atc - the new type of the prescription
	 */
	public void setATC(String atc) {
		this.setType(atc);
	}
	
	public String getATC() {
		return this.getType();
	}
	
	public int getDuration() {
		return duration;
	}

	public void setDuration(int duration) {
		this.duration = duration;
	}

	//EXTENDED DATA
	/**
	 * Flag to check if the prescription dose exists or not (as an extended column)
	 * or if it has a value set as it is.
	 * @return - true if the prescription dose column exists in the input 
	 * file and the value is not empty or if the extended column does not exist,
	 * but the attribute was set as it is; false otherwise
	 */
	public boolean hasDose(){
		return (this.extended.hasAttribute(indexDose) ||
				this.extended.hasAttribute(DataDefinition.PRESCRIPTION_DOSE));
	}
	
	/**
	 * Flag to check if the prescription formulation exists or not (as an extended column)
	 * or if it has a value set as it is.
	 * @return - true if the prescription formulation column exists in the input 
	 * file and the value is not empty or if the extended column does not exist,
	 * but the attribute was set as it is; false otherwise
	 */
	public boolean hasFormulation(){
		return (this.extended.hasAttribute(indexFormulation) ||
				this.extended.hasAttribute(DataDefinition.PRESCRIPTION_FORMULATION));
	}
	
	/**
	 * Checks if this prescription has a formulation in the list of formulations.
	 * @param formulations - list of formulations
	 * @return - true is this prescription has its formulation in the formulation list; false otherwise
	 */
	public boolean hasFormulation(List<String> formulations){
		for (String formulation : formulations) 
			if (this.extended.hasAttribute(formulation))
				return true;
		
		return false;
	}

	/**
	 * Checks if this prescription has its formulation in the list of formulations.
	 * @param formulations - list of formulations
	 * @return - true if this prescription has its formulation in the formulation list; false otherwise
	 */
	public boolean hasFormulation(String[] formulations){
		for (int i=0;i<formulations.length;i++) 
			if (this.extended.hasAttribute(formulations[i]))
				return true;

		return false;
	}
	
	/**
	 * Flag to check if the prescription strength exists or not (as an extended column)
	 * or if it has a value set as it is.
	 * @return - true if the prescription strength column exists in the input 
	 * file and the value is not empty or if the extended column does not exist,
	 * but the attribute was set as it is; false otherwise
	 */
	public boolean hasStrength(){
		return (this.extended.hasAttribute(indexStrength) ||
				this.extended.hasAttribute(DataDefinition.PRESCRIPTION_STRENGTH));
	}
	
	/**
	 * Flag to check if the prescription volume exists or not (as an extended column)
	 * or if it has a value set as it is.
	 * @return - true if the prescription volume column exists in the input 
	 * file and the value is not empty or if the extended column does not exist,
	 * but the attribute was set as it is; false otherwise
	 */
	public boolean hasVolume(){
		return (this.extended.hasAttribute(indexVolume) ||
				this.extended.hasAttribute(DataDefinition.PRESCRIPTION_VOLUME));
	}
	
	/**
	 * Flag to check if the prescriber ID exists or not (as an extended column)
	 * or if it has a value set as it is.
	 * @return - true if the prescriber ID column exists in the input 
	 * file and the value is not empty or if the extended column does not exist,
	 * but the attribute was set as it is; false otherwise
	 */
	public boolean hasPrescriberId(){
		return (this.extended.hasAttribute(indexPrescriberID) ||
				this.extended.hasAttribute(DataDefinition.PRESCRIPTION_PRESCRIBER_ID));
	}
	
	/**
	 * Flag to check if the prescriber type exists or not (as an extended column)
	 * or if it has a value set as it is.
	 * @return - true if the prescriber type column exists in the input 
	 * file and the value is not empty or if the extended column does not exist,
	 * but the attribute was set as it is; false otherwise
	 */
	public boolean hasPrescriberType(){
		return (this.extended.hasAttribute(indexPrescriberType) ||
				this.extended.hasAttribute(DataDefinition.PRESCRIPTION_PRESCRIBER_TYPE));
	}

	/**
	 * Flag to check if the prescription indication exists or not (as an extended column)
	 * or if it has a value set as it is.
	 * @return - true if the prescription indication column exists in the input 
	 * file and the value is not empty or if the extended column does not exist,
	 * but the attribute was set as it is; false otherwise
	 */
	public boolean hasIndication(){
		return (this.extended.hasAttribute(indexIndication) ||
				this.extended.hasAttribute(DataDefinition.PRESCRIPTION_INDICATION));
	}
	
	/**
	 * Checks if this prescription has a certain indication.
	 * @param indication - the indication of interest
	 * @return - true if this prescription has this indication; false otherwise
	 */
	public boolean hasIndication(String indication) {
		return (this.getIndication() == null ? false :
			this.getIndication().equals(indication));
	}

	/**
	 * Checks if this prescription has an indication in a list of indications.
	 * @param indications - the list of indications to be checked
	 * @return - true is this prescription has an indication in the indications; false otherwise
	 */
	public boolean hasIndication(List<String> indications){
		for (String indication: indications) 
			if (this.hasIndication(indication))
				return true;
		
		return false;
	}

	/**
	 * Checks if this prescription has an indication in a list of indications.
	 * @param indications - an array of indications
	 * @return - true is this prescription has an indication in the indications array; false otherwise
	 */
	public boolean hasIndication(String[] indications){
		for (int i=0;i<indications.length;i++) 
			if (this.hasIndication(indications[i]))
				return true;
		
		return false;
	}
	
	/**
	 * Gets the uncompressed version of the prescription dose if information is available.
	 * If there is no extended data column in the input files, it will check if the 
	 * attribute was just set as it is and return that value.
	 * @return - the prescription dose string
	 */	
	public String getDose(){
		return (this.extended.getAttributeAsString(indexDose) != DataDefinition.NO_DATA ?
				getExtendedAttributeAsString(indexDose) : 
					this.extended.getAsIs(DataDefinition.PRESCRIPTION_DOSE));
	}

	/**
	 * Returns the compressed form of this prescription's dose.
	 * Note that if there is no extended dose column and the 
	 * attribute is set via setAsIs(), it will still return NO_DATA
	 * as there is no use of the look-up tables if set as it is.  
	 * @return - the index of the prescription dose in the look-up table
	 * of NO_DATA (i.e., -1) if not existing
	 */
	public Integer getDoseCompressed() {
		return this.extended.getAttribute(indexDose);
	}
	
	/**
	 * Maps the dose to its look-up table and sets its corresponding
	 * index in this prescription. if the extended dose column is not 
	 * present in the input file, then the value will be set as it is
	 * in this object.
	 * @param dose - the prescription dose value to be set
	 */
	public void setDose(String dose) {
		if (indexDose != NO_DATA){
			if (dose == null){
				this.extended.put(indexDose, NO_DATA);
			}else {
				int orgSize = this.getExtendedAttributeLookUp(indexDose).size();
				int indexInLookUp = InputFileUtilities.addToList(this.getExtendedAttributeLookUp(indexDose), dose);
				if ((!Jerboa.unitTest) && (this.getExtendedAttributeLookUp(indexDose).size() > orgSize)) {
					InputFileUtilities.outputPrescriptionLookUpTables(Jerboa.getInputFileSet(), true);
				}
				this.extended.put(indexDose, indexInLookUp);
			}
		}else{
			this.extended.setAsIs(DataDefinition.PRESCRIPTION_DOSE, dose);
		}
	}
	
	/**
	 * Gets the uncompressed version of the prescription indication if information is available.
	 * If there is no extended data column in the input files, it will check if the 
	 * attribute was just set as it is and return that value.
	 * @return - the prescription indication string
	 */	
	public String getIndication(){
		return (this.extended.getAttributeAsString(indexIndication) != DataDefinition.NO_DATA ?
				this.extended.getAttributeAsString(indexIndication) : 
					this.extended.getAsIs(DataDefinition.PRESCRIPTION_INDICATION));
	}

	/**
	 * Returns the compressed form of this prescription's indication.
	 * Note that if there is no extended indication column and the 
	 * attribute is set via setAsIs(), it will still return NO_DATA
	 * as there is no use of the look-up tables if set as it is.  
	 * @return - the index of the prescription indication in the look-up table
	 * of NO_DATA (i.e., -1) if not existing
	 */
	public Integer getIndicationCompressed() {
		return this.extended.getAttribute(indexIndication);
	}

	/**
	 * Maps the indication to its look-up table and sets its corresponding
	 * index in this prescription. if the extended indication column is not 
	 * present in the input file, then the value will be set as it is
	 * in this object.
	 * @param indication - the prescription indication value to be set
	 */
	public void setIndication(String indication) {
		if (indexIndication != NO_DATA){
			if (indication == null){
				this.extended.put(indexIndication, NO_DATA);
			}else {
				int orgSize = this.getExtendedAttributeLookUp(indexIndication).size();
				int indexInLookUp = InputFileUtilities.addToList(this.getExtendedAttributeLookUp(indexIndication), indication);
				if ((!Jerboa.unitTest) && (this.getExtendedAttributeLookUp(indexIndication).size() > orgSize)) {
					InputFileUtilities.outputPrescriptionLookUpTables(Jerboa.getInputFileSet(), true);
				}
				this.extended.put(indexIndication, indexInLookUp);
			}
		}else{
			this.extended.setAsIs(DataDefinition.PRESCRIPTION_INDICATION, indication);
		}
	}

	/**
	 * Gets the uncompressed version of the prescription formulation if information is available.
	 * If there is no extended data column in the input files, it will check if the 
	 * attribute was just set as it is and return that value.
	 * @return - the prescription formulation string
	 */	
	public String getFormulation(){
		return (this.extended.getAttributeAsString(indexFormulation) != DataDefinition.NO_DATA ?
				this.extended.getAttributeAsString(indexFormulation) : 
					this.extended.getAsIs(DataDefinition.PRESCRIPTION_FORMULATION));
	}

	/**
	 * Returns the compressed form of this prescription's formulation.
	 * Note that if there is no extended formulation column and the 
	 * attribute is set via setAsIs(), it will still return NO_DATA
	 * as there is no use of the look-up tables if set as it is.  
	 * @return - the index of the prescription formulation in the look-up table
	 * of NO_DATA (i.e., -1) if not existing
	 */
	public Integer getFormulationCompressed() {
		return this.extended.getAttribute(indexFormulation);
	}

	/**
	 * Maps the formulation to its look-up table and sets its corresponding
	 * index in this prescription. if the extended formulation column is not 
	 * present in the input file, then the value will be set as it is
	 * in this object.
	 * @param formulation - the prescription formulation value to be set
	 */
	public void setFormulation(String formulation) {
		if (indexFormulation != NO_DATA){
			if (formulation == null){
				this.extended.put(indexFormulation, NO_DATA);
			}else {
				int orgSize = this.getExtendedAttributeLookUp(indexFormulation).size();
				int indexInLookUp = InputFileUtilities.addToList(this.getExtendedAttributeLookUp(indexFormulation), formulation);
				if ((!Jerboa.unitTest) && (this.getExtendedAttributeLookUp(indexFormulation).size() > orgSize)) {
					InputFileUtilities.outputPrescriptionLookUpTables(Jerboa.getInputFileSet(), true);
				}
				this.extended.put(indexFormulation, indexInLookUp);
			}
		}else{
			this.extended.setAsIs(DataDefinition.PRESCRIPTION_FORMULATION, formulation);
		}
	}
	
	/**
	 * Gets the uncompressed version of the prescription strength if information is available.
	 * If there is no extended data column in the input files, it will check if the 
	 * attribute was just set as it is and return that value.
	 * @return - the prescription strength string
	 */	
	public String getStrength(){
		return (this.extended.getAttributeAsString(indexStrength) != DataDefinition.NO_DATA ?
				this.extended.getAttributeAsString(indexStrength) : 
					this.extended.getAsIs(DataDefinition.PRESCRIPTION_STRENGTH));
	}

	/**
	 * Returns the compressed form of this prescription's strength.
	 * Note that if there is no extended strength column and the 
	 * attribute is set via setAsIs(), it will still return NO_DATA
	 * as there is no use of the look-up tables if set as it is.  
	 * @return - the index of the prescription strength in the look-up table
	 * of NO_DATA (i.e., -1) if not existing
	 */
	public Integer getStrengthCompressed() {
		return this.extended.getAttribute(indexStrength);
	}

	/**
	 * Maps the strength to its look-up table and sets its corresponding
	 * index in this prescription. if the extended strength column is not 
	 * present in the input file, then the value will be set as it is
	 * in this object.
	 * @param strength - the prescription strength value to be set
	 */
	public void setStrength(String strength) {
		if (indexStrength != NO_DATA){
			if (strength == null){
				this.extended.put(indexStrength, NO_DATA);
			}else {
				int orgSize = this.getExtendedAttributeLookUp(indexStrength).size();
				int indexInLookUp = InputFileUtilities.addToList(this.getExtendedAttributeLookUp(indexStrength), strength);
				if ((!Jerboa.unitTest) && (this.getExtendedAttributeLookUp(indexStrength).size() > orgSize)) {
					InputFileUtilities.outputPrescriptionLookUpTables(Jerboa.getInputFileSet(), true);
				}
				this.extended.put(indexStrength, indexInLookUp);
			}
		}else{
			this.extended.setAsIs(DataDefinition.PRESCRIPTION_STRENGTH, strength);
		}
	}
	
	/**
	 * Gets the uncompressed version of the prescription volume if information is available.
	 * If there is no extended data column in the input files, it will check if the 
	 * attribute was just set as it is and return that value.
	 * @return - the prescription volume string
	 */	
	public String getVolume(){
		return (this.extended.getAttributeAsString(indexVolume) != DataDefinition.NO_DATA ?
				this.extended.getAttributeAsString(indexVolume) : 
					this.extended.getAsIs(DataDefinition.PRESCRIPTION_VOLUME));
	}

	/**
	 * Returns the compressed form of this prescription's volume.
	 * Note that if there is no extended volume column and the 
	 * attribute is set via setAsIs(), it will still return NO_DATA
	 * as there is no use of the look-up tables if set as it is.  
	 * @return - the index of the prescription volume in the look-up table
	 * of NO_DATA (i.e., -1) if not existing
	 */
	public Integer getVolumeCompressed() {
		return this.extended.getAttribute(indexVolume);
	}

	/**
	 * Maps the volume to its look-up table and sets its corresponding
	 * index in this prescription. if the extended volume column is not 
	 * present in the input file, then the value will be set as it is
	 * in this object.
	 * @param volume - the prescription volume value to be set
	 */
	public void setVolume(String volume) {
		if (indexVolume != NO_DATA){
			if (volume == null){
				this.extended.put(indexVolume, NO_DATA);
			}else {
				int orgSize = this.getExtendedAttributeLookUp(indexVolume).size();
				int indexInLookUp = InputFileUtilities.addToList(this.getExtendedAttributeLookUp(indexVolume), volume);
				if ((!Jerboa.unitTest) && (this.getExtendedAttributeLookUp(indexVolume).size() > orgSize)) {
					InputFileUtilities.outputPrescriptionLookUpTables(Jerboa.getInputFileSet(), true);
				}
				this.extended.put(indexVolume, indexInLookUp);
			}
		}else{
			this.extended.setAsIs(DataDefinition.PRESCRIPTION_VOLUME, volume);
		}
	}
	
	/**
	 * Gets the uncompressed version of the prescriber ID if information is available.
	 * If there is no extended data column in the input files, it will check if the 
	 * attribute was just set as it is and return that value.
	 * @return - the prescriber ID string
	 */	
	public String getPrescriberId(){
		return (this.extended.getAttributeAsString(indexPrescriberID) != DataDefinition.NO_DATA ?
				this.extended.getAttributeAsString(indexPrescriberID) : 
					this.extended.getAsIs(DataDefinition.PRESCRIPTION_PRESCRIBER_ID));
	}

	/**
	 * Returns the compressed form of this prescription's prescriber ID.
	 * Note that if there is no extended prescriber ID column and the 
	 * attribute is set via setAsIs(), it will still return NO_DATA
	 * as there is no use of the look-up tables if set as it is.  
	 * @return - the index of the prescriber ID in the look-up table
	 * of NO_DATA (i.e., -1) if not existing
	 */
	public Integer getPrescriberIdCompressed() {
		return this.extended.getAttribute(indexPrescriberID);
	}

	/**
	 * Maps the prescriber ID to its look-up table and sets its corresponding
	 * index in this prescription. if the extended volume column is not 
	 * present in the input file, then the value will be set as it is
	 * in this object.
	 * @param prescriberId - the prescriber ID value to be set
	 */
	public void setPrescriberId(String prescriberId) {
		if (indexPrescriberID != NO_DATA){
			if (prescriberId == null){
				this.extended.put(indexPrescriberID, NO_DATA);
			}else {
				int orgSize = this.getExtendedAttributeLookUp(indexPrescriberID).size();
				int indexInLookUp = InputFileUtilities.addToList(this.getExtendedAttributeLookUp(indexPrescriberID), prescriberId);
				if ((!Jerboa.unitTest) && (this.getExtendedAttributeLookUp(indexPrescriberID).size() > orgSize)) {
					InputFileUtilities.outputPrescriptionLookUpTables(Jerboa.getInputFileSet(), true);
				}
				this.extended.put(indexPrescriberID, indexInLookUp);
			}
		}else{
			this.extended.setAsIs(DataDefinition.PRESCRIPTION_PRESCRIBER_ID, prescriberId);
		}
	}
	
	/**
	 * Gets the uncompressed version of the prescriber type if information is available.
	 * If there is no extended data column in the input files, it will check if the 
	 * attribute was just set as it is and return that value.
	 * @return - the prescriber type string
	 */	
	public String getPrescriberType(){
		return (this.extended.getAttributeAsString(indexPrescriberType) != DataDefinition.NO_DATA ?
				this.extended.getAttributeAsString(indexPrescriberType) : 
					this.extended.getAsIs(DataDefinition.PRESCRIPTION_PRESCRIBER_TYPE));
	}

	/**
	 * Returns the compressed form of this prescription's prescriber type.
	 * Note that if there is no extended prescriber type column and the 
	 * attribute is set via setAsIs(), it will still return NO_DATA
	 * as there is no use of the look-up tables if set as it is.  
	 * @return - the index of the prescriber type in the look-up table
	 * of NO_DATA (i.e., -1) if not existing
	 */
	public Integer getPrescriberTypeCompressed() {
		return this.extended.getAttribute(indexPrescriberType);
	}

	/**
	 * Maps the prescriber type to its look-up table and sets its corresponding
	 * index in this prescription. if the extended volume column is not 
	 * present in the input file, then the value will be set as it is
	 * in this object.
	 * @param prescriberType - the prescriber type value to be set
	 */
	public void setPrescriberType(String prescriberType) {
		if (indexPrescriberType != NO_DATA){
			if (prescriberType == null){
				this.extended.put(indexPrescriberType, NO_DATA);
			}else {
				int orgSize = this.getExtendedAttributeLookUp(indexPrescriberType).size();
				int indexInLookUp = InputFileUtilities.addToList(this.getExtendedAttributeLookUp(indexPrescriberType), prescriberType);
				if ((!Jerboa.unitTest) && (this.getExtendedAttributeLookUp(indexPrescriberType).size() > orgSize)) {
					InputFileUtilities.outputPrescriptionLookUpTables(Jerboa.getInputFileSet(), true);
				}
				this.extended.put(indexPrescriberType, indexInLookUp);
			}
		}else{
			this.extended.setAsIs(DataDefinition.PRESCRIPTION_PRESCRIBER_TYPE, prescriberType);
		}
	}
	
	//END OF EXTENDED DATA
	
	/**
	 * Checks if the duration of this prescription is between startDate and endDate.
	 * By default the actual start date and the actual end date are not included but the 
	 * startInclusive and endInclusive flags allow customization. 
	 * @param startDate - the start date of interest
	 * @param endDate - the end date of interest
	 * @param startInclusive - true if startDate should be included; false otherwise
	 * @param endInclusive - true if endDate should be included; false otherwise
	 * @return - true if the duration of this prescription is between startDate and endDate
	 */
	@Override
	public boolean isInPeriod(int startDate, int endDate, boolean startInclusive, boolean endInclusive){
		return ((startInclusive ? this.getEndDate() >= startDate : this.getEndDate() > startDate) &&
				(endInclusive ? this.getStartDate() <= endDate : this.getStartDate() < endDate));
	}
	
	/**
	 * Checks if the duration of this prescription is between startDate (inclusive)
	 * and endDate (inclusive). It is just a wrapper of the isInPeriod(startDate,
	 * endDate, startInclusive, endInclusive) method.
	 * @param startDate - the starting date of interest
	 * @param endDate - the end date of interest
	 * @return - true if the duration of this prescription is between startDate (inclusive) and endDate (inclusive)
	 */
	@Override
	public boolean isInPeriod(int startDate, int endDate){
		return isInPeriod(startDate, endDate, true, true);
	}
	
	/**
	 * Checks if the start date of this prescription is between startDate and endDate.
	 * The startInclusive and endInclusive flags allow customization of the inclusion of the
	 * start and/or end dates. 
	 * @param startDate - the start date of interest
	 * @param endDate - the end date of interest
	 * @param startInclusive - true if startDate should be included; false otherwise
	 * @param endInclusive - true if endDate should be included; false otherwise
	 * @return - true if the duration of this prescription is between startDate and endDate
	 */
	public boolean startsInPeriod(int startDate, int endDate, boolean startInclusive, boolean endInclusive){
		return ((startInclusive ? this.getStartDate() >= startDate : this.getStartDate() > startDate) &&
				(endInclusive ? this.getStartDate() <= endDate : this.getStartDate() < endDate));
	}

	/**
	 * Return true if the prescription is active on the specified date. 
	 * Note that start date is included, end date is excluded.
	 * @param date - date of interest as integer
	 * @return - true if this prescription is active on date; false otherwise
	 */
	public boolean onDate(int date){
		return (this.getEndDate() > date) && (this.getStartDate() <= date);
	}	
	
	/**
	 * Checks if this ATC is in a certain group, e.g. "A10E"
	 * If the group starts with "_" it is a generated ATC code and then the ATC code
	 * of the prescription should be equal to the group.
	 * @param group - string containing the higher level ATC code
	 * @return - true is this prescription is part of this ATC group; false otherwise
	 */
	public boolean startsWith(String group){
		return group.substring(0, 1).equals("_") ? StringUtils.upperCase(this.getATC()).equals(StringUtils.upperCase(group)) : StringUtils.startsWith(StringUtils.upperCase(this.getATC()),StringUtils.upperCase(group));
	}

	/**
	 * Checks if this ATC is in a list of ATC groups, e.g. "A10E"
	 * @param groups - a list of strings containing the higher level ATC code
	 * @return - true is this prescription is part of this ATC group; false otherwise
	 */
	public boolean startsWith(List<String> groups){
		for (String group: groups) 
			if (this.startsWith(group))
				return true;
		
		return false;
	}
	
	/**
	 * Checks if this ATC is in a list of ATC groups, e.g. "A10E".
	 * If the item contains an indication, this is tested as well:
	 * ATC;indication.
	 * @param groups - a list of strings containing the higher level ATC code
	 * @return - true is this prescription is part of this ATC group; false otherwise
	 */
	public boolean startsWithIncludeIndication(List<String> groups){
		for (String group: groups) {
			String parts[] = group.split(";");
			if (parts.length==1){
				if (this.startsWith(group))
					return true;
			} else {
				if (this.startsWith(parts[0]) && this.getIndication().toUpperCase().equals(StringUtils.upperCase(parts[1])))
					return true;
			}
		}
		
		return false;
	}

	/**
	 * Checks if this ATC is in a list of ATC groups, e.g. "A10E".
	 * @param groups - a list of strings containing the higher level ATC code
	 * @return - true is this prescription is part of this ATC group; false otherwise
	 */
	public boolean startsWith(String[] groups){
		for (int i=0;i<groups.length;i++) 
			if (this.startsWith(groups[i]))
				return true;

		return false;
	}
	
	/**
	 * In case drug combination episodes are created and if
	 * all the drugs in the combination start simultaneously,
	 * this date is not equal to -1 (its default value).
	 * @return - combinationStartDate
	 */
	public int getCombinationStartDate() {
		return combinationStartDate;
	}
	
	/**
	 * Used by prescriptionCombination modifier.
	 * If all the drugs in the combination start simultaneously the date is filled in,
	 * otherwise the value is -1.
	 * @param date - the new date of the combination start
	 */
	public void setCombinationStartDate(int date) {
		combinationStartDate = date;
	}

	/**
	 * Used by prescriptionCombination modifier
	 * In case drug combination episodes are created this is true if
	 * all the components start at prescription start
	 * @return - combinationStartDate
	 */
	public boolean getAllComponentsStart() {
		return allComponentsStart;
	}
	
	/**
	 * Used by prescriptionCombination modifier
	 * In case drug combination episodes are created this is true if
	 * all the components start at prescription start
	 * @return - combinationStartDate
	 */
	public void setAllComponentsStart(boolean value) {
		allComponentsStart = value;
	}
}