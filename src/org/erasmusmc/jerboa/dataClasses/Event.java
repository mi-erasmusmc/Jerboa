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
 * $Rev:: 4816              $:  Revision of last commit                                   *
 * $Author:: root           $:  Author of last commit                                     *
 * $Date:: 2013-11-20 14:32#$:  Date and time (CET) of last commit						  *
 ******************************************************************************************/

import java.util.List;

import org.erasmusmc.jerboa.Jerboa;
import org.erasmusmc.jerboa.config.DataDefinition;
import org.erasmusmc.jerboa.engine.InputFile;
import org.erasmusmc.jerboa.utilities.DateUtilities;
import org.erasmusmc.jerboa.utilities.InputFileUtilities;

/**
 * This is an event object. It should contain the ID of the patient, a date, and a type.
 * Other extended attributes are added if they are present in the input file.
 *
 * @author MG
 */
public class Event extends Episode{

	//index of the extended attribute "code" in the extended data
	public final int indexCode = getIndexOfExtendedAttribute(DataDefinition.EVENT_CODE);

	/**
	 * Basic constructor
	 */
	public Event(){
		super(Episode.EPISODE_TYPE_EVENT);
	}

	/**
	 * Constructor accepting an event object.
	 * @param e - the event used to initialize this event
	 */
	public Event(Event e){
		super(e);
	}

	/**
	 * Constructor of a event object from an input file line.
	 * Different behavior depending on the source of data (sorted or unsorted file).
	 * Used in the PatientObjectCreator class.
	 * @param attributes - a line from the events input file
	 * @param eventsFile - the events input file containing all details about formatting (e.g., data order, date format)
	 */
	public Event(String[] attributes, InputFile eventsFile){
		super(Episode.EPISODE_TYPE_EVENT, attributes, eventsFile);
		String type = attributes[eventsFile.getDataOrder()[COLUMN_TYPE]].trim().toUpperCase();
		InputFileUtilities.addToList(InputFileUtilities.getEventTypes(), type);
		this.type = getIndex(InputFileUtilities.getEventTypes(), type);
	}

	/**
	 * Constructor of an event object from a patient object file.
	 * Used in the PatientUtilities class. Note: +1 shift of the columns due to subset ID.
	 * @param attributes - the attributes of the event object
	 */
	public Event(String[] attributes){
		super(Episode.EPISODE_TYPE_EVENT, attributes);
	}

	/**
	 * Default toString returns uncompressed version of the Event object.
	 * @return - a character string representation of this event.
	 */
	public String toString(){
		return toStringUncompressed();
	}

	/**
	 * Returns the character string representation of this event in its uncompressed form.
	 * The date is in YYYYMMDD format and the type in its original form.
	 * Note that if the look-up tables are not present for this specific run,
	 * the compressed form of the type will be used.
	 * @return - the string representation with all details of this event
	 */
	public String toStringUncompressed(){
			return (subset+","+patientID+
					(InputFileUtilities.getEventTypes() != null ?
							","+InputFileUtilities.getEventTypes().get(Integer.valueOf(type)) : ","+type)+","+
					DateUtilities.daysToDate(date)+
					(toStringExtendedData().equals("") ? toStringExtendedDataAsIs() : toStringExtendedData()));
	}

	/**
	 * Return the character string representation of the compressed version
	 * of this Event object to be written to a patient object file (.pof).
	 * It makes use of the look-up tables created for the Event attributes
	 * and adds a flag at the end based on the episode type.
	 * @param flag - the episode type specific flag as defined in DataDefinition
	 * @return - the string representation of this event in its compressed form
	 */
	public String toStringWithFlag(short flag){
		return (subset+","+patientID+","+type+","+date+
				toStringExtendedDataCompressed()+
				(flag != NO_DATA ? ","+flag : ""));
	}

	/**
	 * Return the character string representation of an Event
	 * object with any date under the format YYYYMMDD.
	 * @return - the string representation of this event with its
	 * dates formatted as YYYYMMDD
	 */
	public String toStringConvertedDate(){
		return (subset+","+patientID+","+type+","+DateUtilities.daysToDate(date)+
				toStringExtendedData());
	}

	/**
	 * Returns the string representation of this event formatted for
	 * data export to CSV file. The available extended data is also exported.
	 * Note that the export order should be the same as in data definition.
	 *
	 * @see DataDefinition
	 * @return - a string representation of this event with its attributes
	 * being comma separated
	 */
	public String toStringForExport(){
		return patientID +
				"," + getType() +
				"," + DateUtilities.daysToDate(date) +
				toStringExtendedData(DataDefinition.EPISODE_EVENT);
	}

	/**
	 * Checks if the type of this event is in the list of strings.
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

	//GETTERS AND SETTERS
	@Override
	public String getTypeOfEpisode() {
		return DataDefinition.EPISODE_EVENT;
	}

	/**
	 * Gets the uncompressed version of this event's type if information is available.
	 * @return - the event type string
	 */
	@Override
	public String getType(){
		return extended.getValue(InputFileUtilities.getEventTypes(), type);
	}

	/**
	 * Sets this event type and adds it to the look-up table.
	 * @param type - the new type of this Event object
	 */
	@Override
	public void setType(String type) {
		InputFileUtilities.addToEventLookup(InputFileUtilities.getEventTypes(), type);
		this.type = extended.getIndex(InputFileUtilities.getEventTypes(), type);
	}

	//EXTENDED DATA
	/**
	 * Flag to check if the measurement code exists or not (as an extended column)
	 * or if it has a value set as it is.
	 * @return - true if the event code column exists in the input
	 * file and the value is not empty or if the extended column does not exist,
	 * but the attribute was set as it is; false otherwise
	 */
	public boolean hasCode(){
		return (this.extended.hasAttribute(indexCode) ||
				this.extended.hasAttribute(DataDefinition.EVENT_CODE));
	}

	/**
	 * Gets the uncompressed version of the event code if information is available.
	 * If there is no extended data column in the input files, it will check if the
	 * attribute was just set as it is and return that value.
	 * @return - the event code string
	 */
	public String getCode(){
		return (this.extended.getAttributeAsString(indexCode) != DataDefinition.NO_DATA ?
				this.extended.getAttributeAsString(indexCode) :
					this.extended.getAsIs(DataDefinition.EVENT_CODE));
	}

	/**
	 * Returns the compressed form of this event's code.
	 * Note that if there is no extended unit column and the
	 * attribute is set via setAsIs(), it will still return NO_DATA
	 * as there is no use of the look-up tables if set as it is.
	 * @return - the index of the event code in the look-up table
	 * of NO_DATA (i.e., -1) if not existing
	 */
	public Integer getCodeCompressed() {
		return this.extended.getAttribute(indexCode);
	}

	/**
	 * Maps the code to its look-up table and sets its corresponding
	 * index in this event. if the extended code column is not
	 * present in the input file, then the value will be set as it is
	 * in this object.
	 * @param code - the event code value to be set
	 */
	public void setCode(String code) {
		if (indexCode != NO_DATA){
			if (code == null){
				this.extended.put(indexCode, NO_DATA);
			}else {
				int orgSize = this.getExtendedAttributeLookUp(indexCode).size();
				int indexInLookUp = InputFileUtilities.addToList(this.getExtendedAttributeLookUp(indexCode), code);
				if ((!Jerboa.unitTest) && (this.getExtendedAttributeLookUp(indexCode).size() > orgSize)) {
					InputFileUtilities.outputEventLookUpTables(Jerboa.getInputFileSet(), true);
				}
				this.extended.put(indexCode, indexInLookUp);
			}
		}else{
			this.extended.setAsIs(DataDefinition.EVENT_CODE, code);
		}
	}

}
