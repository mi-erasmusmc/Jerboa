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
 * Author: Peter Rijnbeek (PR) - department of Medical Informatics						  *
 * 																						  *
 * $Rev:: 3961              $:  Revision of last commit                                   *
 * $Author:: root           $:  Author of last commit                                     *
 * $Date:: 2013-05#			$:  Date and time (CET) of last commit						  *
 ******************************************************************************************/
package org.erasmusmc.jerboa.utilities;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;

import org.apache.commons.collections.keyvalue.MultiKey;
import org.apache.commons.lang3.StringUtils;
import org.erasmusmc.jerboa.Jerboa;

/**
 * Class to create a table of results.
 * It allows to add any variable and its value for a patient.
 * The toString method can be used to retrieve a table format of the data.
 * Subset and order of variables can be defined using the variablesOrder setter.
 * If no variable order is defined all variables will ordered as inserted.
 *
 * @author PR
 *
 */
public class ResultSet {

	private MultiKeyMap data;

	//order of the variables or subset
	private List<String> variableOrder;
	private String missingValue = "";
	private String delimiter = ",";
	private boolean onlyInCohort = true;
	private boolean hasTwoKeys = false;
	private boolean active = true; //if true data will be added otherwise ignored

	//save the original insertion order
	private LinkedHashSet<String> originalOrder = new LinkedHashSet<String>();

	//CONSTRUCTORS
	/**
	 * Constructor receiving a flag for the presence of two keys.
	 * @param  hasTwoKeys - result set with patientID, second Key as multiKey
	 */
	public ResultSet(boolean hasTwoKeys) {
		super();
	    data = new MultiKeyMap();
	    variableOrder = new ArrayList<String>();
	    this.hasTwoKeys = hasTwoKeys;
	}

	/**
	 * Constructor of single Keyed result set.
	 */
	public ResultSet() {
		super();
	    data = new MultiKeyMap();
	    variableOrder = new ArrayList<String>();
	}

	/**
	 * Constructor overriding the default delimiter and missing value
	 * @param delimiter - the delimiter to be used between values
	 * @param missingValue - the character string to be used for missing values
	 */
	public ResultSet(String delimiter, String missingValue) {
		super();
	    data = new MultiKeyMap();
	    variableOrder = new ArrayList<String>();
	    this.delimiter = delimiter;
	    this.missingValue = missingValue;
	}

	/**
	 * Constructor overriding the default delimiter and missing value
	 * including option to set to two keys.
	 * @param hasTwoKeys - true if the result set has patientID, second Key as multiKey
	 * @param delimiter - the delimiter to be used between values
	 * @param missingValue - the character string to be used for missing values
	 */
	public ResultSet(boolean hasTwoKeys, String delimiter, String missingValue) {
		super();
	    data = new MultiKeyMap();
	    variableOrder = new ArrayList<String>();
	    this.delimiter = delimiter;
	    this.missingValue = missingValue;
	    this.hasTwoKeys = hasTwoKeys;
	}

	@Override
	/**
	 * Returns a comma-delimited table representation.
	 */
	public String toString() {
		if (active){
			DelimitedStringBuilder table = new DelimitedStringBuilder(delimiter);
			table.set(getHeader());
			if (!hasTwoKeys){
				for (String patientID : getPatientIDs()){
					table.appendWithoutDelimiter(patientID);
					for (String variable : getVariables()){
						MultiKey key = new MultiKey(patientID, variable);
						table.append(data.get(key) != null ? data.get(key).toString():missingValue);
					}
					table.addEol();
				}
			} else {
				for (int i = 0; i<getBothKeys().size(); i++){
					String patientID = getBothKeys().get(i).getKey(0).toString();
					String secondKey = getBothKeys().get(i).getKey(1).toString();
					table.appendWithoutDelimiter(patientID + delimiter + secondKey);
					for (String variable : getVariables()){
						MultiKey key = new MultiKey(patientID, secondKey, variable);
						table.append(data.get(key) != null ? data.get(key).toString():missingValue);
					}
					table.addEol();
				}
			}
			return table.toString();
		}else return "";
	}

	/**
	 * Output the table to file.
	 * @param fileName - the output file name
	 * @return true if file was created  or result set not active; false otherwise
	 */
	public boolean outputToFile(String fileName){
		boolean result = true;
		if (active) {
			result = Jerboa.getOutputManager().addFile(fileName);
			if (result) {
				Jerboa.getOutputManager().write(fileName, getHeader(), false);

				if (!hasTwoKeys){
					for (String patientID : getPatientIDs()){
						DelimitedStringBuilder row = new DelimitedStringBuilder(delimiter);

						row.appendWithoutDelimiter(patientID);
						for (String variable : getVariables()){
							MultiKey key = new MultiKey(patientID, variable);
							row.append(data.get(key) != null ? data.get(key).toString():missingValue);
						}
						row.addEol();
						Jerboa.getOutputManager().write(fileName,row.toString(),true);
					}
				} else {
					DelimitedStringBuilder row = new DelimitedStringBuilder(delimiter);
					for (int i = 0; i<getBothKeys().size(); i++){

						row.set("");
						Object patientID = getBothKeys().get(i).getKey(0);
						Object secondKey = getBothKeys().get(i).getKey(1);
						row.appendWithoutDelimiter(patientID.toString());
						for (String variable : getVariables()){
							MultiKey key = new MultiKey(patientID, secondKey, variable);
							row.append(data.get(key) != null ? data.get(key).toString():missingValue);
						}
						row.addEol();
						Jerboa.getOutputManager().write(fileName,row.toString(),true);
					}
				}
			}
		}

		Jerboa.getOutputManager().closeFile(fileName);
		return result;
	}

	/**
	 * Adds a cell in the table.
	 * @param patientID - the patient identifier
	 * @param variable - the variable to be added
	 * @param value - the value
	 */
	@SuppressWarnings("unchecked")
	public void add(String patientID, String variable, String value){
		if (active){
			MultiKey multiKey = new MultiKey(patientID, variable);
			data.put(multiKey, value);
			originalOrder.add((String) variable);
		}
	}

	/**
	 * Adds a cell in the table.
	 * @param patientID - the patient identifier
	 * @param secondKey - the second key to be added besides the patient ID
	 * @param variable - the variable to be added
	 * @param value - the value of the variable
	 */
	@SuppressWarnings("unchecked")
	public void add(String patientID, String secondKey,String variable, String value){
		if (active){
			MultiKey multiKey = new MultiKey(patientID, secondKey, variable);
			data.put(multiKey, value);
			originalOrder.add((String) variable);
		}
	}

	/**
	 * Free memory.
	 */
	public void clear(){
		data.clear();
	}


	//GETTERS AND SETTERS
	/**
	 * Returns the header containing PatientID,[variables].
	 * @return - a comma-delimited String representing the header
	 */
	private String getHeader(){
		DelimitedStringBuilder header = new DelimitedStringBuilder(delimiter);
		header.append("PatientID");
		header.append(StringUtils.join(getVariables(), delimiter));
		header.addEol();

		return header.toString();
	}

	/**
	 * Returns the variables selected by the variableOrder of all.
	 * @return - the variables as List
	 */
	private List<String> getVariables(){
		if (!variableOrder.isEmpty())
			return variableOrder;
		else {
			List<String> variables = new ArrayList<String>();
			for (String variable: originalOrder){
				variables.add(variable);
			}

			return variables;
		}
	}

	/**
	 * Returns all patient IDs.
	 * @return list of patientID as strings of characters
	 */
	private List<String> getPatientIDs(){
		List<String> patientIDs = new ArrayList<String>();
		for (Object key: data.getKeyComponentValues(0)){
			patientIDs.add(key.toString());
		}

		return patientIDs;
	}

	/**
	 * Returns both of the keys (if applicable) used: the patient ID and the multikey.
	 * @return list of patientID Strings
	 */
	private List<MultiKey> getBothKeys(){
		if (hasTwoKeys){
			HashSet<MultiKey> allKeys = new HashSet<MultiKey>();
			for (Object key: data.keySet()){
				MultiKey theKey = (MultiKey) key;
				allKeys.add(new MultiKey(theKey.getKey(0),theKey.getKey(1)));
			}
			List<MultiKey> list = new ArrayList<MultiKey>(allKeys);
			return list;
		}else{
			return null;
		}
	}

	/**
	 * Sets the order of the variables (can be a subset).
	 * @param variableOrder - the order of the variables in the output
	 */
	public void setVariableOrder(String variableOrder){
		this.variableOrder = Arrays.asList(variableOrder.split(","));
	}

	/**
	 * Set the representation of the missing value (Default = "")
	 * @param missingValue - the character(s) replacing missing data cells
	 */
	public void setMissingValue(String missingValue){
		this.missingValue = missingValue;
	}

	/**
	 * Set the value delimiter (Default = ",").
	 * @param delimiter - the character(s) representing the delimiter between variables
	 */
	public void setDelimiter(String delimiter){
		this.delimiter = delimiter;
	}

	/**
	 * Determines if this result set will be active or not.
	 * @param active - true if this result set is active;false otherwise
	 */
	public void setActive(boolean active){
		this.active = active;
	}

	/**
	 * Check if this result set is active.
	 * @return - true if this result set is active; false otherwise
	 */
	public boolean isActive(){
		return active;
	}

	/**
	 * Only create result set for patients in the cohort? (Default = true)
	 * @param onlyInCohort - true if the result set is only for patients in cohort; false otherwise
	 */
	public void setOnlyInCohort(boolean onlyInCohort){
		this.onlyInCohort = onlyInCohort;
	}

	/**
	 * Check if the result set is only for patients in the cohort?
	 * @return - true if the onlyInCohort flag is set to true; false otherwise
	 */
	public boolean isOnlyInCohort(){
		return onlyInCohort;
	}

	/**
	 * Retrieves the value of a certain variable from the result set.
	 * @param patientID - the patient identifier
	 * @param variable - the name of the variable
	 * @return - the value of the variable
	 */
	public Object getValue(String patientID, String variable){
		 // later retrieve the localized text
		 MultiKey multiKey = new MultiKey(patientID, variable);
		 return data.get(multiKey);
	}

	/**
	 * Retrieves the value of a certain variable from the result set
	 * @param patientID - the patient identifier
	 * @param secondKey - the multikey besides the patient ID
	 * @param variable - the name of the variable
	 * @return - the value of the variable
	 */
	public Object getValue(String patientID, String secondKey, String variable){
		 // later retrieve the localized text
		 MultiKey multiKey = new MultiKey(patientID, secondKey, variable);
		 return data.get(multiKey);
	}

	/**
	 * Get the missing value set.
	 * @return - missing value
	 */
	public String getMissingValue(){
		return this.missingValue;
	}

	//MAIN METHOD FOR TESTING OR DEBUGGING
	public static void main(String[] args) {

		ResultSet results = new ResultSet(",","?");
		results.add("100001","Smoking","PAST");
		results.add("100002","Smoking","RECENT");
		results.add("100002","BMI","25");
		results.add("100002","Length","1.75");

		System.out.println(results);

		results.setVariableOrder("Smoking,BMI");
		System.out.println(results);

		ResultSet twoKeyedSet = new ResultSet(true,",","?");
		twoKeyedSet.add("100001","Row1","Smoking","PAST");
		twoKeyedSet.add("100002","Row1","Smoking","RECENT");
		twoKeyedSet.add("100002","Row2","BMI","25");
		System.out.println(twoKeyedSet);
	}

}
