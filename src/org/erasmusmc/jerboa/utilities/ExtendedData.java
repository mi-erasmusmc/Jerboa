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

import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

import org.apache.commons.collections.bidimap.DualHashBidiMap;
import org.erasmusmc.jerboa.Jerboa;
import org.erasmusmc.jerboa.config.DataDefinition;
import org.erasmusmc.jerboa.dataClasses.Episode;
import org.erasmusmc.jerboa.dataClasses.Patient;

public class ExtendedData {

	// will contain pairs <column_index_in_input_file, extended_column_name>
	public Map<Integer, String> dataOrder;

	// extended attributes (not mandatory) will contain pairs of <ext_col_index,
	// mapped_val>
	public HashMap<Integer, Integer> data;

	//extended attributes without passing through look-ups or other mappings
	public HashMap<String, String> dataAsIs;

	public final String typeOfEpisode;

	// flag for no data
	public final static int NO_DATA = -1;

	/**
	 * Constructor receiving the type of episode for which the extended data is.
	 * @param typeOfEpisode - the type of episode
	 */
	public ExtendedData(String typeOfEpisode) {
		this.typeOfEpisode = typeOfEpisode;
		this.dataOrder = InputFileUtilities
				.getExtendedDataOrder(this.typeOfEpisode);
		this.data = new HashMap<Integer, Integer>();
	}

	/**
	 * Constructor based on other extendedData object
	 * @param extendedData
	 */
	public ExtendedData(ExtendedData extendedData){
		this.typeOfEpisode = extendedData.typeOfEpisode;
		if (extendedData.data != null)
			this.data = new HashMap<Integer, Integer>(extendedData.data);
		if (extendedData.dataOrder != null)
			this.dataOrder = new HashMap<Integer, String>(extendedData.dataOrder);
		if (extendedData.dataAsIs != null)
			this.dataAsIs = new HashMap<String, String>(extendedData.dataAsIs);
	}

	/**
	 * Sets all the extended data attributes for this episode type as pairs of
	 * {@literal <mapped_value, extended_column_index>}.
	 * @param attributes - the list of attributes from the input file (i.e., a row)
	 * @return - a map of all the extended attributes for this episode type
	 */
	public HashMap<Integer, Integer> setExtendedAttributesFromInputFile(String[] attributes) {
		HashMap<Integer, Integer> extended = new HashMap<Integer, Integer>();
		for (Integer index : dataOrder.keySet()) {
			if (index != null && attributes.length > index) {
				String attribute = attributes[index].trim().toUpperCase();
				int indexInLookUp = noData(attribute) ? NO_DATA
						: InputFileUtilities.addToList(
								getAttributeLookUp(index), attribute);
				extended.put(index, indexInLookUp);
			}
		}

		return extended;
	}

	/**
	 * Sets all the extended data attributes for this episode type.
	 * @param attributes - the list of attributes from the input file (i.e., a row)
	 * @return - a list of all the extended attributes for this episode type
	 */
	public HashMap<Integer, Integer> setExtendedAttributesFromPOF(String[] attributes) {
		HashMap<Integer, Integer> extended = new HashMap<Integer, Integer>();
		int nbColumns = InputFileUtilities
				.getNumberOfMandatoryColumns(this.typeOfEpisode);
		short entryIndex = 0;
		for (Integer index : dataOrder.keySet()) {
			if (attributes.length > nbColumns + entryIndex
					+ Episode.COMPRESSION_OFFSET)
				extended.put(
						index,
						Integer.valueOf(attributes[nbColumns + entryIndex
								+ Episode.SUBSET_OFFSET]));
			entryIndex++;
		}

		return extended;
	}

	/**
	 * Set the specified extended data attribute of a patient to the specified value.
	 * @param patient - the episode object for which an extended data attribute has to be set
	 * @param attribute - the name of the extended data attribute
	 * @param value - the new value of the specified extended data attribute
	 */
	public int setExtendedAttributePatient(Patient patient, String attribute, String value) {
		attribute = attribute.toLowerCase();
		int attributeIndex = patient.getIndexOfExtendedAttribute(attribute);
		int orgSize = patient.getExtendedAttributeLookUp(attributeIndex).size();
		int indexInLookUp = InputFileUtilities.addToList(patient.getExtendedAttributeLookUp(attributeIndex), value);
		if ((!Jerboa.unitTest) && (patient.getExtendedAttributeLookUp(attributeIndex).size() > orgSize)) {
			InputFileUtilities.outputPatientLookUpTables(Jerboa.getInputFileSet(), true);
			patient.extended.put(attribute, value);
		}
		return indexInLookUp;
	}

	/**
	 * Set the specified extended data attribute of an episode to the specified value.
	 * @param episode - the episode object for which an extended data attribute has to be set
	 * @param attribute - the name of the extended data attribute
	 * @param value - the new value of the specified extended data attribute
	 */
	public int setExtendedAttributeEpisode(Episode episode, String attribute, String value) {
		int attributeIndex = episode.getIndexOfExtendedAttribute(attribute);
		int orgSize = episode.getExtendedAttributeLookUp(attributeIndex).size();
		int indexInLookUp = InputFileUtilities.addToList(episode.getExtendedAttributeLookUp(attributeIndex), value);
		if ((!Jerboa.unitTest) && (episode.getExtendedAttributeLookUp(attributeIndex).size() > orgSize)) {
			if (episode.episodeType == Episode.EPISODE_TYPE_EVENT) {
				InputFileUtilities.outputEventLookUpTables(Jerboa.getInputFileSet(), true);
			}
			else if (episode.episodeType == Episode.EPISODE_TYPE_PRESCRIPTION) {
				InputFileUtilities.outputPrescriptionLookUpTables(Jerboa.getInputFileSet(), true);
			}
			else if (episode.episodeType == Episode.EPISODE_TYPE_MEASUREMENT) {
				InputFileUtilities.outputMeasurementLookUpTables(Jerboa.getInputFileSet(), true);
			}
			episode.extended.put(attribute, value);
		}
		return indexInLookUp;
	}

	/**
	 * Checks if attribute is empty or it contains only quotes. if so, then
	 * false is returned.
	 * @param attribute - the attribute to be checked
	 * @return - true if attribute different than empty string; false otherwise.
	 */
	public boolean noData(String attribute) {
		return (attribute.equals("") || attribute.equals("\"\""));
	}

	/**
	 * Checks if the episodeType has the attribute among its extended data
	 * columns (not mandatory) and if it has an actual value.
	 * @param extendedColumnIndex - the index of the extended element (either as column or array index)
	 * @return - true if this episode has the extended attribute at extendedColumnIndex
	 * and if it actually has a value; false otherwise
	 */
	public boolean hasAttribute(int extendedColumnIndex) {
		return this.data.get(extendedColumnIndex) != null
				&& this.data.get(extendedColumnIndex) != NO_DATA;
	}

	/**
	 * Checks if this episode has attribute as extended data (not mandatory).
	 * @param extendedAttribute - the string representation of the extended attribute
	 * @return - true if this episode has the extended attribute; false otherwise
	 */
	public boolean hasAttribute(String extendedAttribute) {
		String lowercased = extendedAttribute.toLowerCase();
		boolean found = false;
		if (this.dataOrder != null && this.dataOrder.values().contains(lowercased))
			  for (Entry<Integer, String> entry : dataOrder.entrySet())
				if (entry.getValue().equals(lowercased))
		 			found = hasAttribute(entry.getKey());
		//Check if added on the fly
		 if (this.getAsIs(lowercased) != null && getAsIs(lowercased) != DataDefinition.NO_DATA)
			 found = true;
		 return found;
	}

	/**
	 * Returns the mapped value (compressed) of an extended data attribute based
	 * on the extended data column index. Will return null in case of faulty
	 * index. extended data columns (not mandatory).
	 * @param extendedColumnIndex - the index of the extended data column (as in dataOrder)
	 * @return - the mapped integer value (compressed) of the extended element at extendedColumnIndex;
	 */
	public Integer getAttribute(Integer extendedColumnIndex) {
		return extendedColumnIndex != NO_DATA ?
				this.data.get(extendedColumnIndex) : NO_DATA;
	}

	/**
	 * Returns an extended data attribute under its Integer representation
	 * (compressed) based on its name. Note that the extended data columns
	 * are not mandatory.
	 * @param attribute - the string representation of the attribute of interest
	 * @return - the integer value (compressed) of the extended element;
	 */
	public Integer getAttribute(String attribute) {
		return this.data.get(getIndexOfAttribute(attribute.toLowerCase()));
	}

	/**
	 * Returns an extended data attribute under its String representation
	 * (uncompressed) based on its mapped index in the look-up table.
	 * @param extendedColumnIndex - the index of the extended element as
	 * it appears in extended data order
	 * @return - the String value (uncompressed) of the extended element;
	 */
	public String getAttributeAsString(Integer extendedColumnIndex) {
		return (extendedColumnIndex != NO_DATA && this.data.get(extendedColumnIndex) != NO_DATA) ?
				getValue(getAttributeLookUp(this.dataOrder.get(extendedColumnIndex)),
				this.data.get(extendedColumnIndex)) : DataDefinition.NO_DATA;
	}

	/**
	 * Returns an extended data attribute under its String representation
	 * (uncompressed) based on its name.
	 * It searches in the extended columns in the file and extended columns
	 * added during execution
	 * @param attribute - the string representation of the attribute of interest
	 * @return - the value (uncompressed) of the extended attribute;
	 */
	public String getAttributeAsString(String attribute) {
		attribute = attribute.toLowerCase();
		int index = getIndexOfAttribute(attribute);
		String value = DataDefinition.NO_DATA;
		if (index != -1)
			value = getValue(getAttributeLookUp(attribute), data.get(index));
		else {
			if (getDataAsIs().containsKey(attribute)) {
				value = getDataAsIs().get(attribute);
			}
		}
		return value;
	}

	/**
	 * Returns the name of the column of an extended data attribute based on the
	 * order of the extended data.
	 * @param extendedColumnIndex - the index of the extended column as it appears in data order
	 * @return - the String value (uncompressed) of the extended element;
	 */
	public String getAttributeName(Integer extendedColumnIndex) {
		return this.dataOrder.get(extendedColumnIndex);
	}

	/**
	 * Returns the look-up table for this attribute from this episodeType.
	 * @param attribute - the extended data attribute for which the table is needed
	 * @return - the look-up table for attribute
	 */
	public DualHashBidiMap getAttributeLookUp(String attribute) {
		return InputFileUtilities.getLookUpExtended(this.typeOfEpisode, attribute.toLowerCase());
	}

	/**
	 * Returns the look-up table for the extended attribute at index
	 * extendedColumnIndex for this episodeType.
	 * @param extendedColumnIndex - the key in the extended data structure
	 * @return - the look-up table for the attribute at extendedColumnIndex
	 */
	public DualHashBidiMap getAttributeLookUp(Integer extendedColumnIndex) {
		return this.dataOrder != null ? InputFileUtilities.getLookUpExtended(this.typeOfEpisode,
				this.dataOrder.get(extendedColumnIndex)) : null;
	}

	/**
	 * Returns the index of the attribute in the extended attribute list or -1
	 * (i.e., flag for no data) if the attribute is empty.
	 * @param attribute - the extended attribute of interest
	 * @return - the value to which the attribute is mapped in the extended data
	 */
	public Integer getIndexOfAttribute(String attribute) {
		attribute = attribute.toLowerCase();
		if (this.dataOrder != null)
			for (Entry<Integer, String> entry : this.dataOrder.entrySet())
				if (entry.getValue().equals(attribute))
					return entry.getKey();
		return NO_DATA;
	}

	/**
	 * Returns the mapping of value in the look-up table list.
	 * @param list - the look up table as a bidirectional map
	 * @param value - the value mapped
	 * @return - the integer key associated to value in the list
	 */
	public int getIndex(DualHashBidiMap list, String value) {
		return (list != null && list.getKey(value) != null) ?
				(int) list.getKey(value) : NO_DATA;
	}

	/**
	 * Returns the value based on the mapping index from the look-up table.
	 * @param list - the look up table as a bidirectional map
	 * @param key - the key in the map for the value to be retrieved
	 * @return - the string representation of value
	 */
	public String getValue(DualHashBidiMap list, int key) {
		return (list != null && list.get(key) != null) ?
				list.get(key).toString() : DataDefinition.NO_DATA;
	}

	/**
	 * Returns the set of mapped extended attributes.
	 * @return - the key set of the extended data map
	 */
	public Set<Integer> getKeySet() {
		return this.data.keySet();
	}

	/**
	 * Returns the extended attribute that is found at extColumnIndex.
	 * @param extColumnIndex - the index of the extended attribute
	 * @return - the value mapped at extColumnIndex.
	 */
	public Integer get(Integer extColumnIndex) {
		return this.data.get(extColumnIndex);
	}

	/**
	 * Wrapper for the native method of the HashMap.
	 * @param key - the key at which to insert the value in the map
	 * @param value - the value to be put in the map
	 * @return - the previous value that was associated with key
	 */
	public Integer put(Integer key, Integer value) {
		return this.data.put(key, value);
	}

	/**
	 * Wrapper for the native method of the HashMap.
	 * @param key - the string representation of the extended column
	 * @param value - the string representation of the value to be associated with key
	 * @return - the previous value that was associated with key
	 */
	public Integer put(String key, String value) {
		int indexInLookUp = InputFileUtilities.addToList(
				this.getAttributeLookUp(key), value);
		return this.data.put(this.getIndexOfAttribute(key), indexInLookUp);
	}

	/**
	 * Wrapper for the native method of the HashMap.
	 * @param key - the index of the extended data column
	 * @param value - the string representation of the value to be associated with key
	 * @return - the previous value that was associated with key
	 */
	public Integer put(Integer key, String value) {
		return this.put(this.getAttributeAsString(key), value);
	}

	// GETTERS AND SETTERS FOR OBJECT ATTRIBUTES
	public HashMap<Integer, Integer> getData() {
		return data;
	}

	public HashMap<Integer, Integer> setData(HashMap<Integer, Integer> data) {
		this.data = data;
		return this.data;
	}

	public HashMap<String, String> getDataAsIs() {
		return dataAsIs;
	}

	public void setDataAsIs(HashMap<String, String> dataAsIs) {
		this.dataAsIs = dataAsIs;
	}

	/**
	 * Returns the value of the extended attribute as it was set.
	 * This does not require that attribute is an extended data
	 * column in the input files. The value is just set as it is
	 * in the object, without making use of the look-up tables.
	 * Note: null is returned instead of NO_DATA as it may be
	 * possible that the value of attribute is equal to NO_DATA.
	 * @param attribute - the extended attribute for which the value is requested
	 * @return - the value of attribute as is (no look-up tables used) or null if the attribute is not set
	 */
	public String getAsIs(String attribute){
		return this.dataAsIs != null ?
				this.dataAsIs.get(attribute.toLowerCase()) : DataDefinition.NO_DATA;
	}

	/**
	 * Sets value for the extended attribute without making use
	 * of the look-up tables used in the compression phase.
	 * This attribute can be set to the object without being
	 * necessary to have the equivalent extended data column
	 * in the input file.
	 * @param attribute - the extended attribute to be set
	 * @param value - the value of the attribute
	 */
	public void setAsIs(String attribute, String value){
		if (this.dataAsIs == null)
			this.dataAsIs = new HashMap<String, String>();
		this.dataAsIs.put(attribute.toLowerCase(), value);
	}

}
