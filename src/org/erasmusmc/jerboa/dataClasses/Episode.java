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

package org.erasmusmc.jerboa.dataClasses;

import java.util.Comparator;
import java.util.HashMap;

import org.apache.commons.collections.bidimap.DualHashBidiMap;
import org.erasmusmc.jerboa.config.DataDefinition;
import org.erasmusmc.jerboa.engine.InputFile;
import org.erasmusmc.jerboa.utilities.DateUtilities;
import org.erasmusmc.jerboa.utilities.ExtendedData;
import org.erasmusmc.jerboa.utilities.InputFileUtilities;

/**
 * This is a generic class that defines the attributes of an episode in the history of a patient.
 * An episode in the history of a patient can represent a period of time or a specific event.
 * It is to be extended by the different types of episodes that a patient might have
 * (e.g., events, prescriptions or measurements).
 * If its attributes are changed, modifications made should be motivated and their
 * propagation followed closely and documented.
 *
 * @author MG
 *
 */
public abstract class Episode implements Comparable<Episode> {

	//the mandatory column order - do not change unless highly motivated
	public static final short COLUMN_PATIENT_ID = 0;
	public static final short COLUMN_TYPE = 1;
	public static final short COLUMN_DATE = 2;

	//used when compressing data (subset + episode type flag)
	public static final short SUBSET_OFFSET = 1;
	public static final short COMPRESSION_OFFSET = SUBSET_OFFSET + 1;

	//episode types
	public static final short EPISODE_TYPE_EVENT = 0;
	public static final short EPISODE_TYPE_PRESCRIPTION = 1;
	public static final short EPISODE_TYPE_MEASUREMENT = 2;

	public final int NO_DATA = -1;

	// The type of episode
	public int episodeType = -1;

	//attributes
	public String patientID;
	public int type;
	public int date;
	public String subset;

	//keeps all eventual extended attributes
	public ExtendedData extended;

	//CONSTRUCTORS

	/**
	 * Basic constructor.
	 */
	public Episode(int type){
		super();
		this.episodeType = type;
		this.subset = DataDefinition.DEFAULT_SUBSET_ID;
		this.extended = new ExtendedData(getTypeOfEpisode());
	}

	/**
	 * Constructor accepting another episode as parameter.
	 * @param episode - the episode to be used to initialize this episode.
	 */
	public Episode(Episode episode) {
		this.episodeType = episode.episodeType;
		this.patientID = episode.patientID;
		this.type = episode.type;
		this.date = episode.date;
		this.subset = episode.subset;

		this.extended = new ExtendedData(episode.extended);
	}

	/**
	 * Constructor of an episode object from an input file line.
	 * Different behavior depending on the source of data (sorted or unsorted input file).
	 * If the file is sorted, the order of the data columns is used and the object attributes are compressed.
	 * Used in the PatientObjectCreator class.
	 * @param attributes - a line from the input file
	 * @param inputFile - the input file containing all details about formatting (e.g., data order, date format)
	 */
	public Episode(int type, String[] attributes, InputFile inputFile){

		this.episodeType = type;
		this.patientID = attributes[inputFile.getDataOrder()[COLUMN_PATIENT_ID]].trim();
		this.date = DateUtilities.dateToDays(attributes[inputFile.getDataOrder()[COLUMN_DATE]].trim(), inputFile.getDateFormat());
		this.subset = inputFile.getSubsetIndex() != -1 ?
				attributes[inputFile.getSubsetIndex()].trim() : DataDefinition.DEFAULT_SUBSET_ID;

		//extended data
		this.extended = new ExtendedData(getTypeOfEpisode());
		if (inputFile.hasExtendedData()){
			this.extended.setData(this.extended.setExtendedAttributesFromInputFile(attributes));
		}
	}

	/**
	 * Constructor of an episode object from a patient object file.
	 * Used in the PatientUtilities class. Note: +1 shift for columns due to subset ID.
	 * @param attributes - the attributes of the episode
	 */
	public Episode(int type, String[] attributes){
		this.episodeType = type;
		this.subset = attributes[0];
		this.patientID = attributes[COLUMN_PATIENT_ID + SUBSET_OFFSET];
		this.date = Integer.valueOf(attributes[COLUMN_DATE + SUBSET_OFFSET]);
		this.type = Integer.valueOf(attributes[COLUMN_TYPE + SUBSET_OFFSET]);

		//extended data
		if (this.extended == null)
			this.extended = new ExtendedData(getTypeOfEpisode());
		this.extended.setData(this.extended.setExtendedAttributesFromPOF(attributes));
	}

	//TO STRING METHODS
	/**
	 * Returns a character string representation of the
	 * common columns in all Episode type objects.
	 */
	@Override
	public String toString(){
		return (subset+","+patientID+","+type);
	}

	/**
	 * Retrieves the type of this episode as a string representation
	 * defined in the DataDefinition class. It is used to retrieve
	 * the different look-ups and ease up the manipulation of
	 * extended data columns (if any).
	 * @return - a string representation of the type of episode
	 */
	public abstract String getTypeOfEpisode();

	/**
	 * Return the character string representation of an Episode object with its specific flag.
	 * This method is used to output the compressed version of a patient and its history.
	 * @param flag - the episode specific flag (e.g., Event flag, Prescription flag)
	 * @return - this episode as string
	 */
	public abstract String toStringWithFlag(short flag);

	/**
	 * Return the character string representation of an Episode
	 *  object with any date under the format YYYYMMDD.
	 *  @return - this episode with a date converted to the YYYYMMDD format
	 */
	public abstract String toStringConvertedDate();

	// TO STRING FOR EXTENDED DATA
	/**
	 * Generates the part of string that will contain the extended data for this episodeType (if any) in its uncompressed form.
	 * This string is to be added to the first part containing the mandatory data. It is used in the toString methods.
	 * @return - a formatted string containing the extended data columns separated by comma
	 */
	protected String toStringExtendedData() {
		String s = "";
		String episodeTypeName = (episodeType == EPISODE_TYPE_EVENT ? DataDefinition.EPISODE_EVENT : (episodeType == EPISODE_TYPE_PRESCRIPTION ? DataDefinition.EPISODE_PRESCRIPTION : DataDefinition.EPISODE_MEASUREMENT));
		if (InputFileUtilities.getExtendedDataOrder(episodeTypeName) != null) {
			for (String column : (InputFileUtilities.getExtendedDataOrder(episodeTypeName).values()))
				s += (((InputFileUtilities.lookupsExtended != null) && (InputFileUtilities.lookupsExtended.get(episodeTypeName+"_"+column) != null) && (!InputFileUtilities.lookupsExtended.get(episodeTypeName+"_"+column).isEmpty()))) ?
						"," + this.getExtendedAttributeAsString(column) : "";
		}
		return s;
	}

	/**
	 * Generates the part of string that will contain the extended data for this episodeType (if any) in its uncompressed form.
	 * This string is to be added to the first part containing the mandatory data. It also creates empty values for the extended
	 * columns that are not present in the current episode.
	 * @return - a formatted string containing the extended data columns separated by comma
	 */
	protected String toStringExtendedData(String episodeType){
		String s = "";
		for (String column : (InputFileUtilities.getExtendedDataOrder(episodeType).values()))
			s+= ","+(((this.extended.getAttribute(column) != null) && (this.extended.getAttribute(column) != ExtendedData.NO_DATA)) ?
					(this.extended.getAttributeLookUp(this.extended.getAttribute(column)).get(this.extended.get(this.extended.getAttribute(column)))) : "");
		return s;
	}

	/**
	 * Generates the part of string that will contain the extended data for this episodeType (if any) in its compressed form.
	 * This string is to be added to the first part containing the mandatory data. It is used in the toString methods.
	 * Note that it makes use of the look-up tables created for the extended data.
	 * @return - a formatted string containing the extended data columns separated by comma
	 */
	protected String toStringExtendedDataCompressed(){
		String s = "";
		for (Integer extColumnIndex : this.extended.getKeySet())
			s+= ","+this.extended.get(extColumnIndex);
		return s;
	}

	/**
	 * Generates the part of string that will contain the extended data as it is. This means that no look-up tables
	 * are used and the data does not suffer any transformation.
	 * This string is to be added to the first part containing the mandatory data.
	 * @return - a formatted string containing the extended data columns separated by comma
	 */
	protected String toStringExtendedDataAsIs(){
		String s = "";
		if (this.extended.getDataAsIs() != null)
			for (String column : this.extended.getDataAsIs().keySet())
				s+= ","+(this.extended.getAsIs(column));
		return s;
	}

	//COMPARATORS
	/**
	 * Compares two episodes based on their date of occurrence.
	 * @param e - the episode to be compared with this episode
	 * @return - if the episode dates are equal returns 0; if this episode occurred
	 * at a later date than Episode e, then it is considered greater and return value is 1;
	 * This episode is considered smaller that Episode e if result is negative.
	 */
	@Override
	public int compareTo(Episode e) {
		return this.date - e.date;
	}

	/**
	 * Inner class used in order to perform sorting based on episode types.
	 *
	 * @author MG
	 *
	 */
	public static class CompareTypeCompressed implements Comparator<Episode>{

		@Override
		public int compare(Episode e1, Episode e2) {
			return e1.type - e2.type;
		}
	}

	/**
	 * Inner class used in order to perform sorting based on episode types in the uncompressed form.
	 *
	 * @author MG
	 *
	 */
	public static class CompareType implements Comparator<Episode>{

		@Override
		public int compare(Episode e1, Episode e2) {
			String type1 = e1.getType();
			String type2 = e2.getType();
				return type1.length() == type2.length() ? type1.compareTo(type2)
						: type1.length() - type2.length();
		}
	}

	/**
	 * Inner class used in order to perform sorting based on subset ID.
	 *
	 * @author MG
	 *
	 */
	public static class CompareSubset implements Comparator<Episode>{

		@Override
		public int compare(Episode e1, Episode e2) {
			return e1.subset.compareTo(e2.subset);
		}
	}

	/**
	 * Inner class used in order to perform sorting based on episode date and episode type.
	 * Note that the look-up table specific to the type of episode is to be passed as argument.
	 *
	 * @author MG
	 *
	 */
	public static class CompareByDateAndType implements Comparator<Episode>{

		@Override
		public int compare(Episode e1, Episode e2) {
			return  (e1.date == e2.date ?
				new Episode.CompareType().compare(e1, e2) :
				e1.date - e2.date);
		}
	}

	/**
	 * Inner class used in order to perform sorting based on episode type and episode date.
	 * Note that the look-up table specific to the type of episode is to be passed as argument.
	 *
	 * @author MG
	 *
	 */
	public static class CompareByTypeAndDate implements Comparator<Episode>{

		@Override
		public int compare(Episode e1, Episode e2) {
			return  new Episode.CompareType().compare(e1, e2) == 0 ?
				e1.date - e2.date : new Episode.CompareType().compare(e1, e2);
		}
	}

	//SPECIFIC METHODS
	/**
	 * Checks if the event occurs in this interval.
	 * @param startDate - start date (inclusive)
	 * @param endDate - end date (inclusive)
	 * @return - true if the event occurs between startDate and endDate; false otherwise
	 */
	public boolean isInPeriod(int startDate, int endDate){
		return (this.date >= startDate) && (this.date <= endDate);
	}

	/**
	 * Checks if the event occurs in this interval.
	 * @param startDate - start date
	 * @param endDate - end date
	 * @param includeStartDate - true if the day of start should be taken into consideration
	 * @param includeEndDate - true if the day of end should be taken into consideration
	 * @return - true if the event occurs between startDate and endDate with the specified
	 * conditions; false otherwise
	 */
	public boolean isInPeriod(int startDate, int endDate, boolean includeStartDate, boolean includeEndDate){
		return ((includeStartDate ? this.date >= startDate : this.date > startDate) &&
				(includeEndDate ? this.date <= endDate : this.date < endDate));
	}

	//GETTERS AND SETTERS
	public String getPatientID() {
		return patientID;
	}
	public void setPatientID(String patientID) {
		this.patientID = patientID;
	}

	/**
	 * Gets the type of this episode in String format
	 * using the lists created during data compression.
	 * @return - the uncompressed form of this episode type
	 */
	public abstract String getType();

	/**
	 * Returns the year of the event.
	 * @return - the event year
	 */
	public int getYear(){
		return DateUtilities.getYearFromDays(this.date);
	}

	/**
	 * Sets the type of this episode and eventually creates
	 * an entry in the look-up table of the episode types.
	 * @param type - the type of this episode
	 */
	public abstract void setType(String type);

	public int getDate() {
		return date;
	}
	public void setDate(int date) {
		this.date = date;
	}
	public String getSubset() {
		return subset;
	}
	public void setSubset(String subset) {
		this.subset = subset;
	}

	//WRAPPERS FOR EXTENDED DATA
	public boolean hasExtendedAttribute(int index){
		return this.extended.hasAttribute(index);
	}

	public boolean hasExtendedAttribute(String extendedAttribute){
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
		if (this.extended.getAttribute(attributeName) == null ||
				this.extended.getAttribute(attributeName) == NO_DATA)
			this.extended.setAsIs(attributeName, value);
		else {
			this.extended.setExtendedAttributeEpisode(this, attributeName, value);
		}
	}

	public Integer getExtendedAttribute(String attribute) {
		return this.extended.getAttribute(attribute);
	}

	public String getExtendedAttributeAsString(String attribute) {
		return (this.extended.getAttribute(attribute) != null &&
				!this.extended.getAttributeAsString(attribute).equals(DataDefinition.NO_DATA) ?
						this.extended.getAttributeAsString(attribute) :
				this.extended.getAsIs(attribute));
	}

	public String getExtendedAttributeAsString(int index){
		return this.extended.getAttributeAsString(index);
	}

	public Integer getIndexOfExtendedAttribute(String attribute){
		return this.extended.getIndexOfAttribute(attribute);
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
		return this.extended.getAttributeLookUp(attribute);
	}

	public HashMap<Integer, Integer> getExtendedData(){
		return this.extended.getData();
	}

	public String getExtendedAttributeName(Integer extendedColumnIndex){
		return this.extended.getAttributeName(extendedColumnIndex);
	}

}