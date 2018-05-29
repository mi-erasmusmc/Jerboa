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
 * $Rev:: 4816              $:  Revision of last commit                                   *
 * $Author:: root           $:  Author of last commit                                     *
 * $Date:: 					$:  Date and time (CET) of last commit						  *
 ******************************************************************************************/

package org.erasmusmc.jerboa.dataClasses;

import java.security.InvalidParameterException;
import java.util.List;

import org.erasmusmc.jerboa.Jerboa;
import org.erasmusmc.jerboa.config.DataDefinition;
import org.erasmusmc.jerboa.engine.InputFile;
import org.erasmusmc.jerboa.utilities.DateUtilities;
import org.erasmusmc.jerboa.utilities.InputFileUtilities;

/**
 * This is a measurement object. It should contain the ID of the patient, a date, a type and a value.
 * Other extended attributes are added if they are present in the input file.
 *
 * @author MG
 *
 */
public class Measurement extends Episode{

	//measurement specific columns
	private int value;
	public static final short COLUMN_VALUE = 3;

	//index of the extended attribute "unit" in the extended data
	public final int indexUnit = getIndexOfExtendedAttribute(DataDefinition.MEASUREMENT_UNIT);

	/**
	 * Basic constructor
	 */
	public Measurement(){
		super(Episode.EPISODE_TYPE_MEASUREMENT);
	}

	/**
	 * Constructor accepting a measurement object.
	 * @param m - the measurement used to initialize this measurement
	 */
	public Measurement(Measurement m){
		super(m);
		this.value = m.value;
	}

	/**
	 * Constructor of a measurement object from an input file line.
	 * Each attribute of the object is brought to a compressed form,
	 * making use of conversion methods and/or look-up tables.
	 * Used in the PatientObjectCreator class.
	 * @param attributes - a line from the measurement input file
	 * @param measurementsFile - the measurement input file containing all formatting details (e.g., data order, date format)
	 */
	public Measurement(String[] attributes, InputFile measurementsFile){
		super(Episode.EPISODE_TYPE_MEASUREMENT, attributes, measurementsFile);

		String type = attributes[measurementsFile.getDataOrder()[COLUMN_TYPE]].trim().toUpperCase();
		InputFileUtilities.addToList(InputFileUtilities.getMeasurementTypes(), type);
		this.type = getIndex(InputFileUtilities.getMeasurementTypes(), type);
		String value = attributes[measurementsFile.getDataOrder()[COLUMN_VALUE]].trim().toUpperCase();
		this.value = value.equals("") || value.equals("\"\"") ? NO_DATA :
			InputFileUtilities.addToList(InputFileUtilities.getMeasurementValues(), value);
	}

	/**
	 * Constructor of a measurement object from a patient object file.
	 * Used in the PatientUtilities class. Note: +1 shift of columns due to subset ID.
	 * @param attributes - the attributes of the measurement object
	 */
	public Measurement(String[] attributes){
		super(Episode.EPISODE_TYPE_MEASUREMENT, attributes);
		this.value = Integer.valueOf(attributes[COLUMN_VALUE+SUBSET_OFFSET]);
	}

	/**
	 * Return the character string representation of the compressed version
	 * of this Measurement object to be written to a patient object file (.pof).
	 * It makes use of the look-up tables created for the Measurement attributes
	 * and adds a flag at the end based on the episode type.
	 * @param flag - the episode type specific flag as defined in DataDefinition
	 * @return - the string representation of this event in its compressed form
	 *
	 */
	public String toStringWithFlag(short flag){
		return (subset+","+patientID+","+type+","+date+","+value+
				toStringExtendedDataCompressed()+
				(flag != NO_DATA ? ","+flag : ""));
	}

	/**
	 * Returns the character string representation of this measurement in its uncompressed form.
	 * The date is in YYYYMMDD format and its type its original form.
	 * Note that if the look-up tables are not present for this run, the uncompressed
	 * forms of the attributes are used.
	 * @return - the string representation with all details of this measurement
	 */
	public String toStringUncompressed(){
		return (subset+","+patientID+","+getType()+","+
				DateUtilities.daysToDate(date)+
				(value != NO_DATA ? ","+getValue() : ","+DataDefinition.NO_DATA)+
				(toStringExtendedData().equals("") ? toStringExtendedDataAsIs() : toStringExtendedData()));
	}

	/**
	 * Returns the character string representation of this measurement in its uncompressed form.
	 * @return - the string representation with all details of this measurement
	 */
	public String toString(){
		return toStringUncompressed();
	}

	/**
	 * Return the character string representation of a Measurement
	 * object with any date under the format YYYYMMDD.
	 * @return - the string representation of this measurement with its
	 * dates formatted as YYYYMMDD
	 */
	public String toStringConvertedDate(){
		return (subset+","+patientID+","+type+
				","+DateUtilities.daysToDate(date)+
				(value != NO_DATA ? ","+value : "")+
				toStringExtendedDataCompressed());
	}

	/**
	 * Returns the string representation of this measurement formatted for
	 * data export to CSV file. The available extended data is also exported.
 	 * Note that the export order should be the same as in data definition.
	 *
	 * @see DataDefinition
	 * @return - a string representation of this measurement with its attributes being comma separated
	 */
	public String toStringForExport(){
		return patientID +
				"," + getType() +
				"," + DateUtilities.daysToDate(date)+
				(getValue() == null ? "" : "," + getValue())+
				toStringExtendedData();
	}

	/**
	 * Checks if the type of this measurement is in this list of strings.
	 * The match should be exact. The strings in the list are upper cased.
	 * @param list - an array of strings containing the items to check
	 * @return - true is this episode type is in the list; false otherwise
	 */
	public boolean inList(List<String> list){
		for (String item: list) {
			if (this.getType().toUpperCase().equals(item.toUpperCase())){
				return true;
			}
		}

		return false;
	}


	/**
	 * Checks if the value is in this range.
	 * @param min - minimum value (inclusive); -1 means no minimum
	 * @param max - maximum value (inclusive); -1 means no maximum
	 * @return - true if the measurement is in the range; false otherwise
	 */
	public boolean isInRange(double min, double max){

		// defaults
		if (min==-1) min = Double.MIN_VALUE;
		if (max==-1) max = Double.MAX_VALUE;

		// check if measurement is numeric
		try {
			Double.valueOf(this.getValue());
		}catch(NumberFormatException e){
			throw new InvalidParameterException("Value " + this.getValue()+ " of " + this.getType() + " is non-numeric");
		}

		return (Double.valueOf(this.getValue()) >= min) && (Double.valueOf(this.getValue()) <= max);
	}

	//GETTERS AND SETTERS

	@Override
	public String getTypeOfEpisode() {
		return DataDefinition.EPISODE_MEASUREMENT;
	}

	/**
	 * Gets the uncompressed version of this measurement's type.
	 * @return - the measurement type as string
	 */
	@Override
	public String getType(){
		return getValue(InputFileUtilities.getMeasurementTypes(), type);
	}

	/**
	 * Sets the type for this Measurement.
	 * @param type - the new type of this measurement
	 */
	@Override
	public void setType(String type) {
		InputFileUtilities.addToMeasurementLookup(InputFileUtilities.getMeasurementTypes(), type);
		this.type = getIndex(InputFileUtilities.getMeasurementTypes(), type);
	}

	/**
	 * Gets the uncompressed version of this measurement's value.
	 * @return - the measurement value string
	 */
	public String getValue() {
		return getValue(InputFileUtilities.getMeasurementValues(), value);
	}

	/**
	 * Sets the value for this Measurement object.
	 * @param value - the new value of this measurement object
	 */
	public void setValue(String value) {
		InputFileUtilities.addToMeasurementLookup(InputFileUtilities.getMeasurementValues(), value);
		this.value = getIndex(InputFileUtilities.getMeasurementValues(), value);
	}

	//EXTENDED DATA
	/**
	 * Flag to check if the measurement unit exists or not (as an extended column)
	 * or if it has a value set as it is.
	 * @return - true if the measurement unit column exists in the input
	 * file and the value is not empty or if the extended column does not exist,
	 * but the attribute was set as it is; false otherwise
	 */
	public boolean hasUnit(){
		return (this.extended.hasAttribute(indexUnit) ||
				this.extended.hasAttribute(DataDefinition.MEASUREMENT_UNIT));
	}

	/**
	 * Gets the uncompressed version of the measurement unit if information is available.
	 * If there is no extended data column in the input files, it will check if the
	 * attribute was just set as it is and return that value.
	 * @return - the measurement unit string
	 */
	public String getUnit(){
		return (this.extended.getAttributeAsString(indexUnit) != DataDefinition.NO_DATA ?
				this.extended.getAttributeAsString(indexUnit) :
					this.extended.getAsIs(DataDefinition.MEASUREMENT_UNIT));
	}

	/**
	 * Returns the compressed form of this measurement's unit.
	 * Note that if there is no extended unit column and the
	 * attribute is set via setAsIs(), it will still return NO_DATA
	 * as there is no use of the look-up tables if set as it is.
	 * @return - the index of the measurement unit in the look-up table
	 * of NO_DATA (i.e., -1) if not existing
	 */
	public Integer getUnitCompressed() {
		return this.extended.getAttribute(indexUnit);
	}

	/**
	 * Maps the unit to its look-up table and sets its corresponding
	 * index in this measurement. if the extended unit column is not
	 * present in the input file, then the value will be set as it is
	 * in this object.
	 * @param unit - the measurement unit value to be set
	 */
	public void setUnit(String unit) {
		if (indexUnit != NO_DATA){
			if (unit == null){
				this.extended.put(indexUnit, NO_DATA);
			}else {
				int orgSize = this.getExtendedAttributeLookUp(indexUnit).size();
				int indexInLookUp = InputFileUtilities.addToList(this.getExtendedAttributeLookUp(indexUnit), unit);
				if ((!Jerboa.unitTest) && (this.getExtendedAttributeLookUp(indexUnit).size() > orgSize)) {
					InputFileUtilities.outputMeasurementLookUpTables(Jerboa.getInputFileSet(), true);
				}
				this.extended.put(indexUnit, indexInLookUp);
			}
		}else{
			this.extended.setAsIs(DataDefinition.MEASUREMENT_UNIT, unit);
		}
	}

}
