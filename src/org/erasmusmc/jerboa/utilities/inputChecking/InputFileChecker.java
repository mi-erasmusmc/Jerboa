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
 * $Rev:: 3745              $:  Revision of last commit                                   *
 * $Author:: root           $:  Author of last commit                                     *
 * $Date:: 					$:  Date and time (CET) of last commit						  *
 ******************************************************************************************/

package org.erasmusmc.jerboa.utilities.inputChecking;

import java.io.BufferedReader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.erasmusmc.jerboa.Jerboa;
import org.erasmusmc.jerboa.utilities.FileUtilities;
import org.erasmusmc.jerboa.utilities.InputFileUtilities;
import org.erasmusmc.jerboa.utilities.Logging;
import org.erasmusmc.jerboa.utilities.DateUtilities;
import org.erasmusmc.jerboa.utilities.Progress;
import org.erasmusmc.jerboa.utilities.StringUtilities;
import org.erasmusmc.jerboa.utilities.Timer;
import org.erasmusmc.jerboa.config.DataDefinition;
import org.erasmusmc.jerboa.config.Parameters;
import org.erasmusmc.jerboa.engine.InputFile;

/**
 * This is a generic class containing all methods used to check an input file.
 * It is to be extended by any input file checker created to deal with a specific type of input file.
 * The input data is checked for integrity and coherence, record by record, field by field.
 * An error list keeps track of any data incoherence and an error log is output for each input file type.
 * In case the error number is higher than the set limit @see Parameters
 * the rest of the file is skipped and the current run is stopped.
 * If the data is not sorted and no errors were detected, the work flow is started.
 *
 * @author MG
 *
 */
public abstract class InputFileChecker{

	//data related
	protected InputFile file;
	protected short[] dataOrder;
	protected short expectedColumns;

	//progress related
	protected Progress progress;
	protected Timer timer;

	//error related
	protected List<String> errorMessages;
	protected Boolean showExecutionTime = false;

	/**
	 * Constructor accepting an input file.
	 * @param file - the input file to be checked
	 */
	public InputFileChecker(InputFile file){
		this.file = file;
		this.dataOrder = file.getDataOrder();
	}

	/**
	 * Performs a line by line attribute checking on the input data.
	 * If an error is found, it is added to the list and the current line is skipped.
	 * If a prior scan of the input file resulted in no errors and the data was found not
	 * to be sorted, the input data is split into subsets in order to ease subsequent manipulation.
	 * @param fileName - the name/type of the input file to be checked
	 * @throws Exception - if the file could not be opened.
	 */
	public void scan(String fileName) throws Exception{

		init(fileName);
		long line = 1; //loop variable
		boolean isSorted = true;

		if (isHeaderOK(file.getHeader())){

			BufferedReader br = FileUtilities.openFile(file.getName());
			String strLine = br.readLine(); //header
			String[] columns = null;

			//date format and sorted related
			byte referenceDateFormat = DateUtilities.DATE_INVALID;
			String referenceID = "";
			String referenceSubset = "";

			//progress related
			int eol = file.getEol() != null ? file.getEol().length() : 0;
			progress.update(strLine.length()+eol);
			Logging.addWithTimeStamp("Checking "+fileName+" file");

			while ((strLine = br.readLine()) != null) {
				if (errorMessages.size() < Parameters.MAX_ERRORS_INTEGRITY){

					line++;
					progress.update(strLine.length()+eol);

					if (!isLineEmpty(strLine, line)){
						columns = StringUtilities.splitLine(strLine, file.getDelimiter());
						if (isLineComplete(columns, line)){
							//check date format if valid and does not change for a number of lines
							if (line < Parameters.MAX_ERRORS_INTEGRITY){
								checkDateFormat(columns, line, referenceDateFormat);
								referenceDateFormat = file.getDateFormat();
							}
							if (columns.length != file.getHeader().length) {
								errorMessages.add("["+line+"]   Number of columns (" + columns.length + ") is not the same as in the header (" + file.getHeader().length + ") -- " + Arrays.toString(columns));
							}
							else {
								//check if all attributes are OK and if the file is sorted
								if (attributesOK(columns, line)) {

									if (line > 1 && isSorted)
										isSorted = checkIfSorted(columns[file.getPatientIDIndex()].trim(),referenceID,
												file.getSubsetIndex() != -1 ? columns[file.getSubsetIndex()].trim() : null, referenceSubset);
									referenceID = columns[file.getPatientIDIndex()].trim();
									referenceSubset = file.getSubsetIndex() != -1 ? columns[file.getSubsetIndex()].trim() : "";
								}
							}
						}
					}
				}else{
					Logging.add("Too many errors in the "+fileName+" file. Its integrity checking has stopped.", Logging.ERROR);
					break;
				}
			}//end while

			//check if the file contains only the header
			file.setEmpty(line == 1);
			if (file.isEmpty()){
				Logging.add("\nThe input file "+file.getName()+" does not contain any records.\n", Logging.ERROR);
				//or stop if the patients file is empty
				if (file.getType() == DataDefinition.PATIENTS_FILE){
					Logging.add("The population file cannot be empty.", Logging.ERROR);
					Jerboa.stop(true);
				}
			}

			file.setSorted(isSorted);

			//display execution time
			if (showExecutionTime)
				timer.stopAndDisplay(+(line-1)+" records in the "+fileName+" file");
			if (errorMessages.size()==0){
				Logging.add("\t\tNo errors found in the " +fileName+" file");
			}
			br.close();
			br = null;

		}//end check header

		progress.close();
		updateCounters(line-1); // -1 due to header

	}

	//METHODS TO BE IMPLEMENTED
	/**
	 * Checks each attribute of an object (e.g., event, prescription) for integrity and coherence.
	 * These checks include presence, valid value, coherence with respect to other attributes.
	 * @param columns - the data fields from a line in the input file
	 * @param line - the line number in the input file
	 * @return - true if all attributes are in order; false otherwise
	 */
	public abstract boolean attributesOK(String[] columns, long line);

	/**
	 * Reset all the parameters that are used for statistics or other counters.
	 */
	public abstract void resetCounters();

	/**
	 * Updates all the parameters that are used for statistics or other counters.
	 * Normally used to monitor the progress during input checking.
	 * @param value - the value with which to update the counters
	 */
	public abstract void updateCounters(long value);

	//SPECIFIC METHODS
	/**
	 * Initializes all the parameters used for progress, input errors and counters.
	 * @param fileName - the name of the input file
	 */
	protected void init(String fileName){

		errorMessages = new ArrayList<String>();

		resetCounters();
		setExpectedColumns();

		timer = new Timer();
		timer.start();

		progress = new Progress();
		progress.init(file.getSize(), "Checking "+fileName+" file");
	}

	/**
	 * Checks if a line from the input file is empty or contains a number of columns
	 * that is at less than the number of mandatory columns in a specific input file.
	 * If so, an error is added to the list and the line will be skipped.
	 * @param columns - the data fields present on a line in the input file
	 * @param line - the line number in the input file
	 * @return - true if the line does not contain the right number of columns; false otherwise
	 */
	protected boolean isLineComplete(String[] columns, long line){
		if (columns.length < expectedColumns){
			errorMessages.add("["+line+"]   Missing column -- "+Arrays.toString(columns));
			return false;
		}

		return true;
	}

	/**
	 * Checks if an input line is empty or null.
	 * @param strLine - a line from the input file
	 * @param line - the line number from the input file
	 * @return - true if the line is empty; false if not
	 */
	protected boolean isLineEmpty(String strLine, long line) {
		if (strLine == null || strLine.equals("")){
			errorMessages.add("["+line+"]   Empty line -- "+strLine);
			return true;
		}

		return false;
	}

	/**
	 * Checks if the input files are sorted by subset and patient ID.
	 * First a comparison of the subsets (if present) is performed,
	 * followed by a comparison of the length of the patient ID and of the reference ID if part of the same subset.
	 * If patient ID is longer than reference ID, the reference ID precedes the patient ID.
	 * If both have the same length, the compareTo method of the String object is called.
	 * The method has different behavior if the input file is a patient file. The patient IDs are not allowed to be duplicated,
	 * if having the same subset. This is not applicable for the rest of the input files (e.g., events, prescriptions).
	 * A patient is allowed to have multiple events and/or prescriptions, etc.
	 * @param patientID - the patient ID to be compared with the referenceID
	 * @param referenceID - the reference to compare the patient ID
	 * @param subset - the subset the patient is part of
	 * @param referenceSubset - the reference subset to compare to subset
	 * @return true if patientID is after (following the mentioned comparison rules) the referenceID; false otherwise
	 */
	protected boolean checkIfSorted(String patientID,String referenceID, String subset, String referenceSubset) {
		if (file.getSubsetIndex() != -1){
			if (subset.equals(referenceSubset))
				return (file.getType() == DataDefinition.PATIENTS_FILE) ?
						(patientID.length() == referenceID.length() ? patientID.compareTo(referenceID) > 0
								: patientID.length() - referenceID.length() > 0)
								: (patientID.length() == referenceID.length() ? patientID.compareTo(referenceID) >= 0
									: patientID.length() - referenceID.length() >= 0);
		}else{
			return (file.getType() == DataDefinition.PATIENTS_FILE) ?
					(patientID.length() == referenceID.length() ? patientID.compareTo(referenceID) > 0
							: patientID.length() - referenceID.length() > 0)
							: (patientID.length() == referenceID.length() ? patientID.compareTo(referenceID) >= 0
								: patientID.length() - referenceID.length() >= 0);
		}

		return true;
	}

	/**
	 * Checks if the date column in the input file has a valid format and does not change in the next lines.
	 * Different date formats in the same input file are not allowed.
	 * @param columns - a line from the input file split into its columns
	 * @param line - the number of the line
	 * @param referenceDateFormat - a reference date format to check if the format does not change
	 * @throws Exception - if different date formats are found in the same file
	 */
	protected void checkDateFormat(String[] columns, long line, byte referenceDateFormat) throws Exception{
		byte dateFormat = DateUtilities.DATE_INVALID;
		String fileType = InputFileUtilities.getEpisodeName(file.getType());
		//get the indexes of the columns that should contain date attributes for this input file type
		short[] columnIndexes = InputFileUtilities.getDateColumns(file.getType());
		for (int i = 0; i < columnIndexes.length; i++){
			String dateString = columns[file.getDataOrder()[columnIndexes[i]]].trim();
			if (dateString != null && !dateString.equals("")){
				dateFormat = DateUtilities.dateFormat(dateString);
				//check if format is valid - if not throw error and stop
				if (dateFormat == DateUtilities.DATE_INVALID){
					Logging.add("Invalid date format in " + fileType + " file -- "+Arrays.toString(columns), Logging.ERROR, true);
					errorMessages.add("["+line+"]   Invalid date format -- "+Arrays.toString(columns));
				}else{
					if (dateFormat != referenceDateFormat && referenceDateFormat != DateUtilities.DATE_INVALID){
						Logging.add("Different date format in " + fileType + " file -- "+Arrays.toString(columns), Logging.ERROR, true);
						throw new Exception("Different date format in " + fileType + " file");
					}
				}
			}
		}

		file.setDateFormat(dateFormat);
	}

	/**
	 * Generic check of the header. Checks if the header has the necessary length to contain at least the mandatory data.
	 * @param header - the header of the input file
	 * @return - true if the header has enough columns to contain the needed columns; false otherwise
	 */
	protected boolean isHeaderOK(String[] header) {
		return (header != null && header.length > 0
				&& header.length >= InputFileUtilities.getColumnNames(file.getType()).length);
	}

	/**
	 * Check if the required numeric column values are indeed numeric.
	 * @param type - the type (ATC, EventType, or MeasurmentType) of the record
	 * @param columns - the column values in the line
	 * @param line - the line number
	 * @return - true if all column values that should be numeric are indeed numeric, otherwise false
	 */
	protected boolean numericValuesOK(String type, String[] columns, long line) {
		boolean numericOK = true;
		HashMap<String, Set<String>> neededNumericColumns = Jerboa.getWorkFlow().neededNumericColumns.get((int) file.getType());
		if ((neededNumericColumns != null) && neededNumericColumns.containsKey(type)) {
			Map<String, Integer> columnIndex = file.getColumnIndex();
			Set<String> numericColumns = neededNumericColumns.get(type);
			for (String numericColumn : numericColumns) {
				if (columnIndex.containsKey(numericColumn)) {
					try {
						Float.parseFloat(columns[columnIndex.get(numericColumn)]);
					}
					catch (NumberFormatException e) {
						errorMessages.add("["+line+"]   " + numericColumn + " (" + columns[columnIndex.get(numericColumn)] + ") should be numeric -- "+Arrays.toString(columns));
						numericOK = false;
					}
				}
			}
		}
		return numericOK;
	}

	//GETTERS AND SETTERS
	public List<String> getErrorMessages() {
		return errorMessages;
	}
	public InputFile getInputFile(){
		return file;
	}

	/**
	 * Sets the number of expected columns on a line from the input file.
	 * First, the number of mandatory columns should be present, second the
	 * number of extended data columns should be present and last the subset.
	 * Note that it assumes that the extended data is allowed to be empty,
	 * at least the field delimiters are in place.
	 *
	 */
	private void setExpectedColumns(){
		this.expectedColumns = (short)(file.getDataOrder().length +
				(file.getNumberOfExtendedDataColumns()) +
				(file.hasSubset() ? 1 : 0));
	}

}
