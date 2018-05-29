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
 * $Rev:: 4773              $:  Revision of last commit                                   *
 * $Author:: root           $:  Author of last commit                                     *
 * $Date:: 					$:  Date and time (CET) of last commit						  *
 ******************************************************************************************/

package org.erasmusmc.jerboa.engine;

import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import org.apache.commons.lang3.text.StrMatcher;
import org.erasmusmc.jerboa.config.DataDefinition;
import org.erasmusmc.jerboa.utilities.DateUtilities;
import org.erasmusmc.jerboa.utilities.FileUtilities;
import org.erasmusmc.jerboa.utilities.InputFileUtilities;
import org.erasmusmc.jerboa.utilities.Logging;
import org.erasmusmc.jerboa.utilities.StringUtilities;

/**
 * This is an input file object. It contains file specific details (e.g., name, size, header, column order, delimiter)
 * about an input data file present in the working folder of the current run. Its attributes are to be used to determine
 * which type of input file we are dealing with (e.g., patients file, prescriptions, etc.). If attributes are changed or added,
 * modifications made should be motivated, documented and their impact closely followed.
 *
 * @author MG
 *
 */
public class InputFile{

	private String name; 			    		// the name of the input file
	private byte type; 							// the type of the input file (e.g., patients file, prescriptions file)
	private long size;							// the size of the input file
	private String checksum;					// the MD5 checksum of the input file
	private String dates;						// the date at which this file was created, last modified and last accessed
	private StrMatcher delimiter;	    		// the field delimiter in the input file
	private String eol;							// the end of line (eol) character string used in the file

	private String[] header;					// the header of the file
	private Map<String, Integer> columnIndex; 	// an index of the columns of the file
	private short[] dataOrder;					// the column order in the data file
	private Map<Integer, String> extendedDataOrder;  // the extended column names and order in the file
	private short subsetIndex;					// the column holding the subset ID
	private short patientIDIndex;	    		// the column holding the patient ID
	private byte dateFormat;		    		// the format of the date in the file - to be set during the file checking
	private boolean isSorted;					// true if the data in the input file is ordered ascending by patient ID
	private boolean isEmpty;					// true if the input file contains only the header

	//contains the array of supported field delimiters already prepared to be used by the tokenizer
	public static final StrMatcher[] DELIMITERS =
		{StrMatcher.charMatcher(DataDefinition.COMMA_DELIMITER),StrMatcher.charMatcher(DataDefinition.TAB_DELIMITER),
		StrMatcher.charMatcher(DataDefinition.SEMICOLON_DELIMITER), StrMatcher.stringMatcher(DataDefinition.STRING_DELIMITER)};

	//CONSTRUCTORS

	/**
	 * Constructor which creates and populates the input file object attributes using the file fileName.
	 * @param fileName - the name of the file that represents an input
	 */
	public InputFile(String fileName){
		this.name = fileName;
		getFileHeaderAndDelimiter();
		this.type = getFileType();
		this.size = FileUtilities.getFileSize(this.name);
		this.dates = FileUtilities.getFileModificationAttributes(fileName);
		this.checksum = null;
		this.eol = getLineSeparator();
		this.dataOrder = getMandatoryColumnOrder();
		this.dateFormat = DateUtilities.DATE_INVALID;
		this.extendedDataOrder = getExtendedColumns();
		this.patientIDIndex = getIndexPatientID();
		this.subsetIndex = getIndexSubset();
	}

	/**
	 * Constructor which populates/removes attributes of an input file object from another input file object passed as parameter.
	 * @param file - an InputFile object from which details are to be extracted
	 */
	public InputFile(InputFile file){

		this.name = file.getName();
		this.type = file.getType();
		this.header = file.getHeader();
		this.size = file.getSize();
		this.dates = file.getDates();
		this.checksum = file.getChecksum();
		this.eol = file.getEol();
		this.delimiter = file.getDelimiter();
		this.dateFormat = file.getDateFormat() != DateUtilities.DATE_INVALID ? file.getDateFormat() : null;
		this.dataOrder = file.getDataOrder();
		this.extendedDataOrder = file.getExtendedColumns();
		this.patientIDIndex = file.getPatientIDIndex();
		this.subsetIndex = file.getSubsetIndex();
	}

	//SPECIFIC METHODS
	/**
	 * Finds the index of column in the header of the input file.
	 * @param column - the column to search the index for
	 * @param headerColumns - the array of columns specific to the input file
	 * @return - the index of the column in the header; -1 if the column was not found
	 */
	private short findIndex(String column, String[] headerColumns) {
		for (short i = 0; i < headerColumns.length; i++){
			if (headerColumns[i].trim().toLowerCase().equals(column) ||
					column.equals(DataDefinition.COLUMN_SUBSET_ID))
				return i;
		}
		//not found
		return -1;
	}

	/**
	 * Tries to split the header in fields based on the delimiter
	 * passed as parameter. If more than one column is obtained,
	 * the delimiter is considered the right one and set as attribute
	 * of the input file. The header fields are also set for this input file.
	 * @param header - the string tokenizer containing the raw header
	 * @param delimiter - the character to be tested if it is a field separator
	 * @return - true if this delimiter split the header into more than one column; false otherwise
	 */
	private boolean parseHeader(String header, StrMatcher delimiter){
		String[] columns = StringUtilities.splitLine(header, delimiter);
		if (columns != null && columns.length > 1){
			this.delimiter = delimiter;
			this.header = StringUtilities.trim(columns);
			this.columnIndex = new HashMap<String, Integer>();
			for (int columnNr = 0; columnNr < columns.length; columnNr++) {
				columnIndex.put(columns[columnNr].toLowerCase(),columnNr);
			}
			return true;
		}

		return false;
	}

	//GETTERS AND SETTERS
	/**
	 * Find the order of the mandatory data columns in an input file.
	 * @return - an array containing the order of the columns in the input file
	 */
	private short[] getMandatoryColumnOrder(){

		short[] columnOrder = null;
		if (this.header != null && this.header.length > 0 && this.type != DataDefinition.NO_FILE){
			String[] columns = InputFileUtilities.getColumnNames(this.type);
			columnOrder = new short[columns.length];
			for (int i = 0; i < columns.length; i++){
				short index = findIndex(columns[i], this.header);
				//column missing, return null
				if (index == -1) {
					Logging.add("The mandatory data column "+columns[i]+" is missing in the "+this.name+" file.", Logging.ERROR);
					return null;
				}
				//or place the right index
				columnOrder[i] = index;
			}
		}

		return columnOrder;
	}

	/**
	 * Maps the extended data column indexes to their name as they appear in the input file header.
	 * Note that all extended column names are added to the map in lower case.
	 * @return - a map with extended column indexes as keys and extended column names as values
	 */
	private TreeMap<Integer, String> getExtendedColumns(){
		TreeMap<Integer, String> extendedColumns = new TreeMap<Integer, String>();
		if ((this.type != DataDefinition.NO_FILE && this.header != null) &&
				(this.header.length > InputFileUtilities.getColumnNames(this.type).length)){
			for (int i = 0; i < this.header.length; i ++){
				short index = findIndex(this.header[i], InputFileUtilities.getColumnNames(this.type));
				if (index == -1)
					extendedColumns.put(i, this.header[i].trim().toLowerCase());
			}
		}

		return extendedColumns;
	}

	/**
	 * Retrieves the header of this input file and the delimiter used between its fields
	 * and sets them as attributes of this input file.
	 */
	private void getFileHeaderAndDelimiter(){
		try{
			if (this != null && !this.name.equals("")){
				BufferedReader br = FileUtilities.openFile(this.getName());
				try{
					//get header
					String strLine = br.readLine();
					if (strLine != null && !strLine.equals("")){
						strLine = strLine.replaceAll("\"", "").replaceAll("\'", "").trim();
						//check if field separator is among the supported ones
						for (int i = 0; i < DELIMITERS.length; i++){
							if (parseHeader(strLine, DELIMITERS[i]))
								return;
						}
						//or set an unknown delimiter
						this.delimiter = null;
						this.header = null;
					}
				}finally{
					br.close();
				}
			}
		}catch(IOException e){
			Logging.add("Unable to open file "+this.name, Logging.ERROR);
		}
	}

	/**
	 * This method determines the type of an input file based on file specific
	 * columns that are found in the header.
	 * @return - the file type as a constant defined in the DataDefinition class;
	 *  -1 if the type of the input file is not one of the defined types
	 */
	private byte getFileType(){

		byte type = DataDefinition.NO_FILE;

		if (this.header != null && this.header.length > 0){
			//check if patients file
			if (checkHeaderColumns(header, DataDefinition.PATIENT_COLUMNS))
				type = DataDefinition.PATIENTS_FILE;
				//check if events file
			else if (checkHeaderColumns(header, DataDefinition.EVENT_COLUMNS))
				type = DataDefinition.EVENTS_FILE;
				//check if prescriptions file
			else if (checkHeaderColumns(header, DataDefinition.PRESCRIPTION_COLUMNS))
				type = DataDefinition.PRESCRIPTIONS_FILE;
				//check if measurements file
			else if (checkHeaderColumns(header, DataDefinition.MEASUREMENT_COLUMNS))
				type = DataDefinition.MEASUREMENTS_FILE;
		}

		return type;
	}

	/**
	 * Checks if all columns are present in header and returns true
	 * if this is the case. It converts each element of the header
	 * into a lower case string in order to compare.
	 * @param header - the header of an input file
	 * @param columns - the mandatory columns of an input file type.
	 * @return - true if all columns are present in the header; false otherwise
	 */
	private boolean checkHeaderColumns(String[] header, String[] columns){
		boolean ok = true;
		for (int i = 0; i < header.length; i ++)
			header[i] = header[i].toLowerCase();
		List<String> headerAsList = Arrays.asList(header);
		for (String column : columns)
			ok = ok && headerAsList.contains(column);

		return ok;
	}

	/**
	 * Retrieves the line separator used in the input file.
	 * It is handy for an accurate progress update while reading the file.
	 * @return - the line separator depending on the operating system
	 */
	private String getLineSeparator(){
		try{
			FileInputStream fis = new FileInputStream(this.name);
			char current;
			String lineSeparator ="";
			while (fis.available() > 0) {
				current = (char) fis.read();
				if ((current == '\n') || (current == '\r')) {
					lineSeparator+=current;
					if (fis.available() > 0) {
						char next = (char) fis.read();
						if ((next=='\r') || (next=='\n')) {
							lineSeparator+=next;
						}
					}
					fis.close();
					return lineSeparator;
				}
			}
			fis.close();
			return null;
		}catch(IOException e){
			return null;
		}
	}

	/**
	 * Retrieves the index of the column containing the subset ID in the input file.
	 * It makes use of the header of this input file in order to determine the subset index.
	 * @return - the index of the column containing the subset ID; -1 if not found
	 */
	private short getIndexSubset() {
		if (header != null && header.length > 0){
			for (int i = 0; i < header.length; i++)
				if (header[i].toLowerCase().equals(DataDefinition.COLUMN_SUBSET_ID))
					return (short)i;
		}

		return -1;
	}

	/**
	 * Retrieves the index of the column containing the patient ID in this input file.
	 * It makes use of the header of the file in order to determine the subset index.
	 * @return - the index of the column containing the patient ID; -1 if not found
	 */
	private short getIndexPatientID() {
		if (this.header != null && this.header.length > 0){
			for (int i = 0; i < this.header.length; i++)
				if (this.header[i].toLowerCase().equals(DataDefinition.PATIENTS_ID))
					return (short)i;
		}

		return -1;
	}

	/**
	 * Returns the number of extended data columns
	 * that are present in the input file.
	 * @return - the amount of extended columns
	 */
	public int getNumberOfExtendedDataColumns() {
		return (this.extendedDataOrder != null ?
				this.extendedDataOrder.size() : 0);
	}

	//GETTERS AND SETTERS FOR THE OBJECT ATTRIBUTES
	public byte getType() {
		return type;
	}
	public StrMatcher getDelimiter() {
		return delimiter;
	}
	public String[] getHeader() {
		return (String[])header;
	}
	public String getName() {
		return name;
	}
	public void setName(String name) {
		this.name = name;
	}
	public void setType(byte type) {
		this.type = type;
	}
	public void setDelimiter(StrMatcher delimiter) {
		this.delimiter = delimiter;
	}
	public void setHeader(String[] header) {
		this.header = header;
	}
	public Map<String, Integer> getColumnIndex() {
		return columnIndex;
	}
	public long getSize() {
		return size;
	}
	public void setSize(long size) {
		this.size = size;
	}
	public String getChecksum() {
		return checksum == null ?
			FileUtilities.checksumMD5(this.name) : checksum;
	}
	public short[] getDataOrder() {
		return dataOrder;
	}
	public void setDataOrder(short[] dataOrder) {
		this.dataOrder = dataOrder;
	}
	public Map<Integer, String> getExtendedDataOrder() {
		return extendedDataOrder;
	}
	public void setExtendedDataOrder(Map<Integer, String> extendedDataOrder) {
		this.extendedDataOrder = extendedDataOrder;
	}
	public String getEol() {
		return eol;
	}
	public void setEol(String eol) {
		this.eol = eol;
	}
	public short getSubsetIndex() {
		return subsetIndex;
	}
	public void setSubsetIndex(short subsetIndex) {
		this.subsetIndex = subsetIndex;
	}
	public byte getDateFormat() {
		return dateFormat;
	}
	public void setDateFormat(byte dateFormat) {
		this.dateFormat = dateFormat;
	}
	public boolean hasExtendedData(){
		return (dataOrder != null && header != null)
				&& (header.length > dataOrder.length);
	}
	public boolean hasSubset(){
		return subsetIndex != -1;
	}
	public boolean isSorted() {
		return isSorted;
	}
	public void setSorted(boolean isSorted) {
		this.isSorted = isSorted;
	}
	public short getPatientIDIndex() {
		return patientIDIndex;
	}
	public void setPatientIDIndex(short patientIDIndex) {
		this.patientIDIndex = patientIDIndex;
	}
	public boolean isEmpty() {
		return isEmpty;
	}
	public void setEmpty(boolean isEmpty) {
		this.isEmpty = isEmpty;
	}
	public String getDates() {
		return dates;
	}
	public void setDates(String dates) {
		this.dates = dates;
	}

}
