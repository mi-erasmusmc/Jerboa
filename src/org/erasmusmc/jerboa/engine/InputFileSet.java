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
 * $Rev:: 4805              $:  Revision of last commit                                   *
 * $Author:: root           $:  Author of last commit                                     *
 * $Date:: 					$:  Date and time (CET) of last commit						  *
 ******************************************************************************************/

package org.erasmusmc.jerboa.engine;

import java.io.File;
import java.util.ArrayList;
import java.util.BitSet;
import java.util.List;

import org.apache.commons.io.FilenameUtils;
import org.apache.commons.lang3.text.StrBuilder;
import org.erasmusmc.jerboa.Jerboa;
import org.erasmusmc.jerboa.config.DataDefinition;
import org.erasmusmc.jerboa.utilities.ErrorHandler;
import org.erasmusmc.jerboa.utilities.InputFileUtilities;
import org.erasmusmc.jerboa.utilities.Logging;
import org.erasmusmc.jerboa.utilities.StringUtilities;

/**
 * This class contains all the methods needed to retrieve and classify the files from a folder considered as working space.
 * It provides methods to create the input file list, distribute them by their type and creates/updates the list of the files
 * which will be used in the processing.
 *
 * @author MG
 *
 */
public class InputFileSet {

	//the list of all files in the chosen folder
	public List<InputFile> inputFileList;

	//the input file lists per type
	private List<InputFile> patientFiles;
	private List<InputFile> eventFiles;
	private List<InputFile> prescriptionFiles;
	private List<InputFile> measurementFiles;

	//the list of unique input files per type to be used in a work flow
	private List<InputFile> selectedFiles;

	//the script files
	private List<String> scriptFiles;
	private String selectedScriptFile;
	private List<String> scriptFilesNames;

	//accept only one file of a certain type
	public boolean gotPatients = false;
	public boolean gotEvents = false;
	public boolean gotPrescriptions = false;
	public boolean gotMeasurements = false;

	//flags
	public boolean noInputFiles = false;

	//summary of files in working folder
	private StrBuilder fileSummary;

	//holds the total size of the input file set in KB
	private long totalSize;

	//indexes of the selected input files
	public static final int NO_FILE = -1;
	public static final int PATIENTS_FILE_INDEX = 0;
	public static final int EVENTS_FILE_INDEX = 1;
	public static final int PRESCRIPTIONS_FILE_INDEX = 2;
	public static final int MEASUREMENTS_FILE_INDEX = 3;

	//CONSTRUCTORS
	/**
	 * Constructor of a set of input files receiving a path to the selected workspace.
	 * The input files are retrieved from path and distributed to their designated list according to their type.
	 * (e.g., patients file, prescriptions file, script files, etc.).
	 * A set of files is then selected as the input file set to be used for processing. This is achieved either
	 * based on user input or in case of multiple input files per type, the first ones in the list.
	 * @param path - the path to the workspace
	 */
	public InputFileSet(String path){

		//initialize lists
		patientFiles = new ArrayList<InputFile>();
		eventFiles = new ArrayList<InputFile>();
		prescriptionFiles = new ArrayList<InputFile>();
		measurementFiles = new ArrayList<InputFile>();

		scriptFiles = new ArrayList<String>();
		scriptFilesNames = new ArrayList<String>();

		initFileSummary(path);
		initSelectedFiles();
		retrieveFiles(path);
		distributeFilesByType();
		setTotalSize(InputFileUtilities.getInputFileSetSize(getSelectedFiles()));
	}

	/**
	 * Constructor of a set of input files receiving a list of file names.
	 * The input files are retrieved individually using their path
	 *  and distributed to their designated list according to their type
	 * (e.g., patients file, prescriptions file, etc.).
	 * @param files - a list of file names representing the input files
	 */
	public InputFileSet(List<String> files){

		if (files != null && files.size() > 0){

			//initialize lists
			patientFiles = new ArrayList<InputFile>();
			eventFiles = new ArrayList<InputFile>();
			prescriptionFiles = new ArrayList<InputFile>();
			measurementFiles = new ArrayList<InputFile>();

			scriptFiles = new ArrayList<String>();
			scriptFilesNames = new ArrayList<String>();

			initFileSummary(FilenameUtils.getFullPath(files.get(0)));

			inputFileList = new ArrayList<InputFile>();
			for (int i = 0; i < files.size(); i++){
				if (files.get(i) != null){
					files.get(i).replace("\\","/");
					inputFileList.add(new InputFile(files.get(i)));
				}
			}

			initSelectedFiles();
			distributeFilesByType();
			setTotalSize(InputFileUtilities.getInputFileSetSize(getSelectedFiles()));
		}
	}

	/**
	 * Retrieves the files present in path which represents the workspace location.
	 * If script files are found among the files, they are added to their specific list.
	 * @param path - the path to the folder from which the files are to be loaded
	 */
	private void retrieveFiles(String path){

		if (path != null && !path.equals("")){

			File f = new File( path.replace("\\","/") );
			inputFileList = new ArrayList<InputFile>();

			File[] currentFile = f.listFiles();
			if (currentFile != null && currentFile.length > 0)
				for (int i = 0; i < currentFile.length; i++){
					//check if not a folder
					if( !currentFile[i].isDirectory()){
						//check for script files
						if (currentFile[i].getName().endsWith(DataDefinition.SCRIPT_FILE_EXTENSION)){
							scriptFiles.add(currentFile[i].getAbsolutePath().replace("\\","/"));
							scriptFilesNames.add(FilenameUtils.getName(currentFile[i].getAbsolutePath()));
							//or input files
						}else{
							inputFileList.add(new InputFile(currentFile[i].getAbsolutePath().replace("\\","/")));
						}
					}
				}
			//if only one script file set it as selected
			if (scriptFiles != null && scriptFiles.size() == 1)
				selectedScriptFile = scriptFiles.get(0);
		}else{
			if (!Jerboa.inConsoleMode)
				new ErrorHandler("No workspace selected.");
		}

		if (inputFileList == null || inputFileList.size() == 0){
			noInputFiles = true;
		}
	}

	/**
	 * Assigns the files present in the workspace folder to their designated lists, based on their type.
	 * If invalid files are encountered, they are discarded. A logging message is output for each file
	 * in the file list with information regarding its type and size.
	 */
	private void distributeFilesByType(){
		if (inputFileList != null && inputFileList.size() > 0){
			int kb = 1024;
			for (InputFile file : inputFileList){
				if (file != null){
					switch (file.getType()){
					case DataDefinition.PATIENTS_FILE :
						patientFiles.add(file);
						fileSummary.appendln(" * Patients file: " + FilenameUtils.getName(file.getName()) +" - size: "
								+(StringUtilities.format(file.getSize() / kb) + " KB"));
						break;
					case DataDefinition.EVENTS_FILE :
						eventFiles.add(file);
						fileSummary.appendln(" * Events file: " + FilenameUtils.getName(file.getName()) +" - size: "
								+(StringUtilities.format(file.getSize() / kb) + " KB"));
						break;
					case DataDefinition.PRESCRIPTIONS_FILE :
						prescriptionFiles.add(file);
						fileSummary.appendln(" * Prescriptions file: " + FilenameUtils.getName(file.getName()) +" - size: "
								+(StringUtilities.format(file.getSize() / kb) + " KB"));
						break;
					case DataDefinition.MEASUREMENTS_FILE :
						measurementFiles.add(file);
						fileSummary.appendln(" * Measurements file: " + FilenameUtils.getName(file.getName()) +" - size: "
								+(StringUtilities.format(file.getSize() / kb) + " KB"));
						break;
					case DataDefinition.NO_FILE :
						fileSummary.appendln(" - "+ FilenameUtils.getName(file.getName()) +" - size: "
								+(StringUtilities.format(file.getSize() / kb) + " KB"));
						break;
					}
				}
			}
			//set the flags
			setFlags();
			setDefaultSelectedFiles();
		}
	}

	/**
	 * Creates a textual table with details regarding
	 * the selected input file for each type and writes it
	 * to the application log file.
	 */
	public void outputSelectedFiles(){

		fileSummary = new StrBuilder();
		fileSummary.appendln(" ");
		fileSummary.appendln("Selected input files:");
		fileSummary.appendln("-----------------------------------------------------------------------------------------------------------------------------------------------");
		fileSummary.appendln("Legend:   File type  |    Creation date    |    Last modified    |     Last access     |       File size     |   Absolute path  ");
		fileSummary.appendln("-----------------------------------------------------------------------------------------------------------------------------------------------");

		InputFile file = null;
		if (gotPatients){
			file = selectedFiles.get(PATIENTS_FILE_INDEX);
			fileSummary.appendln(StringUtilities.padToRight("Patients file", 20)+" | "+	file.getDates()+" | "+
					StringUtilities.padToRight(InputFileUtilities.formatFileSize(file.getSize()), 15)+" | "+	file.getName());
		}
		if (gotEvents){
			file = selectedFiles.get(EVENTS_FILE_INDEX);
			fileSummary.appendln(StringUtilities.padToRight("Events file", 20)+" | "+ file.getDates()+" | "+
					StringUtilities.padToRight(InputFileUtilities.formatFileSize(file.getSize()), 15)+" | "+
					eventFiles.get(0).getName());
		}
		if (gotPrescriptions){
			file = selectedFiles.get(PRESCRIPTIONS_FILE_INDEX);
			fileSummary.appendln(StringUtilities.padToRight("Prescriptions file", 20)+" | "+ file.getDates()+" | "+
					StringUtilities.padToRight(InputFileUtilities.formatFileSize(file.getSize()), 15)+" | "+ file.getName());
		}
		if (gotMeasurements){
			file = selectedFiles.get(MEASUREMENTS_FILE_INDEX);
			fileSummary.appendln(StringUtilities.padToRight("Measurements file", 20)+" | "+ file.getDates()+" | "+
					StringUtilities.padToRight(InputFileUtilities.formatFileSize(file.getSize()), 15)+" | "+ file.getName());
		}
		fileSummary.appendln("-----------------------------------------------------------------------------------------------------------------------------------------------");
		Logging.add(fileSummary.toString(), Logging.HINT, true);
	}

	/**
	 * Removes the files that are not needed for the current run from the list of selected files.
	 * This is done based on the list of files that are requested by the modules needed during the run.
	 * @param neededFiles - a bit set of input files requested by the workers in the work flow.
	 */
	public void removeUneededFiles(BitSet neededFiles){
		if (selectedFiles != null && selectedFiles.size() > 0){
			for (int i = selectedFiles.size()-1; i >= 0; i--){
				if (selectedFiles.get(i) != null &&
						!neededFiles.get(selectedFiles.get(i).getType())){
					clearFlag(selectedFiles.get(i).getType());
				}
			}
		}
	}

	/**
	 * Updates an input file based on its type. If the user wants to select a file via the GUI, this method is
	 * called in order to verify if a valid file was selected and update the input fileList.
	 * @param fileName - the name of the new input file
	 * @param type - the type of the file that is to be user loaded, as defined in DataDefinition
	 * @return - the updated input file object with all attributes assigned; null if failed to open
	 */
	public InputFile updateInputFile(String fileName, byte type){

		clearFlag(type);

		if (fileName != null && !fileName.equals("")){

			//correct path and retrieve input file details
			InputFile newFile = new InputFile(fileName.replace("\\","/"));
			if(type == newFile.getType()){
				selectedFiles.set(type, newFile);
				addInputFile(newFile);

				return newFile;
			}
		}

		//no file path
		return null;
	}

	/**
	 * Updates the selected file of this type with the input file present at fileIndex in
	 * the list of input files of this type. It is used when selecting an input file
	 * via the GUI and several files of the same type are present in the working folder.
	 * @param fileIndex - the index of the input file in the list of files of its own type (implies multiple
	 * input files of the same type are present in the workspace)
	 * @param type - the type of the file that is to be user loaded
	 * @return - the input file object with all attributes assigned; null if failed to open
	 */
	public InputFile updateInputFile(int fileIndex, byte type){

		clearFlag(type);
		InputFile file = getInputFile(fileIndex, type);

		if (file != null){
			selectedFiles.set(type,file);
			updateFlag(file.getType());
			return file;
		}

		//no file
		return null;
	}

	/**
	 * Adds an input file selected by the user in the list of input files for that type.
	 * @param newFile - the new input file chosen by the user.
	 */
	private void addInputFile(InputFile newFile){

		if (newFile != null){
			switch (newFile.getType()){
			case DataDefinition.PATIENTS_FILE:
				patientFiles.add(newFile);
				gotPatients = true;
				break;
			case DataDefinition.EVENTS_FILE:
				eventFiles.add(newFile);
				gotEvents = true;
				break;
			case DataDefinition.PRESCRIPTIONS_FILE:
				prescriptionFiles.add(newFile);
				gotPrescriptions = true;
				break;
			case DataDefinition.MEASUREMENTS_FILE:
				measurementFiles.add(newFile);
				gotMeasurements = true;
				break;
			}
		}
	}

	/**
	 * Sets the index of each input file in the selected files list.
	 * It is used to retrieve individual input files, regardless their order in the list.
	 */
	private void initSelectedFiles(){
		if (selectedFiles == null){
			selectedFiles = new ArrayList<InputFile>();
			for (int i = 0; i < DataDefinition.INPUT_FILE_TYPES.length; i++)
				selectedFiles.add(null);
		}
	}

	/**
	 * Initializes the summary of the contents of the working folder.
	 * It writes to the log the list of files, marking them as used of discarded.
	 * @param workingFolder - the chosen folder for the current run
	 */
	private void initFileSummary(String workingFolder){
		fileSummary = new StrBuilder("Contents of the working folder " + workingFolder);
		fileSummary.appendln(" ");
		fileSummary.appendln("Legend: * Candidate input file | - Discarded file");
		fileSummary.appendln("-------------------------------------------------");
	}

	//GETTERS AND SETTERS
	/**
	 * Sets the presence flags for different input file types.
	 */
	private void setFlags(){
		//set data presence flags
		gotPatients = patientFiles.size() > 0;
		gotEvents = eventFiles.size() > 0;
		gotPrescriptions = prescriptionFiles.size() > 0;
		gotMeasurements = measurementFiles.size() > 0;
	}

	/**
	 * Sets the default selected input files as the first
	 * ones in the list per input file type.
	 */
	private void setDefaultSelectedFiles(){
		if (gotPatients)
			selectedFiles.set(PATIENTS_FILE_INDEX, patientFiles.get(0));
		if (gotEvents)
			selectedFiles.set(EVENTS_FILE_INDEX, eventFiles.get(0));
		if (gotPrescriptions)
			selectedFiles.set(PRESCRIPTIONS_FILE_INDEX, prescriptionFiles.get(0));
		if (gotMeasurements)
			selectedFiles.set(MEASUREMENTS_FILE_INDEX, measurementFiles.get(0));
	}

	/**
	 * Sets an input file flag to false if it is not needed.
	 * @param fileType - the type of the input file to be set to false
	 */
	private void clearFlag(byte fileType){
		switch (fileType){
		case DataDefinition.PATIENTS_FILE:
			gotPatients = false;
			break;
		case DataDefinition.EVENTS_FILE:
			gotEvents = false;
			break;
		case DataDefinition.PRESCRIPTIONS_FILE:
			gotPrescriptions = false;
			break;
		case DataDefinition.MEASUREMENTS_FILE:
			gotMeasurements = false;
		}
	}

	/**
	 * Sets the presence flag for an input file type.
	 * @param fileType - the type of the input file
	 */
	private void updateFlag(byte fileType){

		switch (fileType){
		case DataDefinition.PATIENTS_FILE:
			gotPatients = true;
			break;
		case DataDefinition.EVENTS_FILE:
			gotEvents = true;
			break;
		case DataDefinition.PRESCRIPTIONS_FILE:
			gotPrescriptions = true;
			break;
		case DataDefinition.MEASUREMENTS_FILE:
			gotMeasurements = true;
			break;
		}
	}

	/**
	 * Returns the input file of type found at fileIndex
	 * in the list of input files of this type.
	 * @param fileIndex - the index of the input file in the list
	 * @param type - the type of input file
	 * @return - the input file if found; null otherwise
	 */
	private InputFile getInputFile(int fileIndex, byte type){
		InputFile file = null;
		switch (type){
		case DataDefinition.PATIENTS_FILE:
			if (fileIndex != NO_FILE)
				file = patientFiles.get(fileIndex);
			break;
		case DataDefinition.EVENTS_FILE:
			if (fileIndex != NO_FILE)
				file = eventFiles.get(fileIndex);
			break;
		case DataDefinition.PRESCRIPTIONS_FILE:
			if (fileIndex != NO_FILE)
				file = prescriptionFiles.get(fileIndex);
			break;
		case DataDefinition.MEASUREMENTS_FILE:
			if (fileIndex != NO_FILE)
				file = measurementFiles.get(fileIndex);
			break;
		}

		return file;
	}

	/**
	 * Returns a binary representation of the input files provided in the working folder.
	 * @return - a set of bits representing the input file types present in the working folder.
	 */
	public BitSet getProvidedFiles(){
		BitSet providedFiles = new BitSet();
		int index = 0;
		providedFiles.set(index++, gotPatients);
		providedFiles.set(index++, gotEvents);
		providedFiles.set(index++, gotPrescriptions);
		providedFiles.set(index, gotMeasurements);

		return providedFiles;
	}

	//GETTERS AND SETTERS FOR ATTRIBUTES
	public List<InputFile> getFileList() {
		return inputFileList;
	}
	public void setFileList(List<InputFile> fileList) {
		this.inputFileList = fileList;
	}
	public List<InputFile> getPatientFiles() {
		return patientFiles;
	}
	public void setPatientFiles(List<InputFile> patientFiles) {
		this.patientFiles = patientFiles;
	}
	public List<InputFile> getEventFiles() {
		return eventFiles;
	}
	public void setEventFiles(List<InputFile> eventFiles) {
		this.eventFiles = eventFiles;
	}
	public List<InputFile> getPrescriptionFiles() {
		return prescriptionFiles;
	}
	public void setPrescriptionFiles(List<InputFile> prescriptionFiles) {
		this.prescriptionFiles = prescriptionFiles;
	}
	public List<InputFile> getMeasurementFiles() {
		return measurementFiles;
	}
	public void setMeasurementFiles(List<InputFile> measurementFiles) {
		this.measurementFiles = measurementFiles;
	}
	public List<InputFile> getSelectedFiles() {
		return selectedFiles;
	}
	public void setSelectedFiles(List<InputFile> selectedFiles) {
		this.selectedFiles = selectedFiles;
	}
	public List<String> getScriptFiles() {
		return scriptFiles;
	}
	public void setScriptFiles(List<String> scriptFiles) {
		this.scriptFiles = scriptFiles;
	}
	public InputFile getPatientsFile() {
		return selectedFiles.get(PATIENTS_FILE_INDEX);
	}
	public InputFile getEventsFile() {
		return 	selectedFiles.get(EVENTS_FILE_INDEX);
	}
	public InputFile getPrescriptionsFile() {
		return 	selectedFiles.get(PRESCRIPTIONS_FILE_INDEX);
	}
	public InputFile getMeasurementsFile() {
		return 	selectedFiles.get(MEASUREMENTS_FILE_INDEX);
	}
	public String getScriptFile() {
		return selectedScriptFile;
	}
	public void setScriptFile(String scriptFile) {
		if (scriptFile != null &&
				scriptFiles != null && scriptFiles.size() > 0){
			for (String s : scriptFiles){
				if (s != null && s.contains(scriptFile)){
					this.selectedScriptFile = s;
					break;
				}
			}
		}
		this.selectedScriptFile = scriptFile;
	}
	public int getNumberOfSelectedFiles(){
		return selectedFiles.size();
	}
	public List<String> getScriptFilesNames() {
		return scriptFilesNames;
	}
	public void setScriptFilesNames(List<String> scriptFilesNames) {
		this.scriptFilesNames = scriptFilesNames;
	}

	public long getTotalSize() {
		return totalSize;
	}

	public void setTotalSize(long totalSize) {
		this.totalSize = totalSize;
	}

}
