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
 * $Rev:: 4794              $:  Revision of last commit                                   *
 * $Author:: root           $:  Author of last commit                                     *
 * $Date:: 					$:  Date and time (CET) of last commit						  *
 ******************************************************************************************/

package org.erasmusmc.jerboa.utilities;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.commons.collections.bidimap.DualHashBidiMap;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.text.StrBuilder;
import org.erasmusmc.jerboa.Jerboa;
import org.erasmusmc.jerboa.config.DataDefinition;
import org.erasmusmc.jerboa.config.FilePaths;
import org.erasmusmc.jerboa.config.PropertiesManager;
import org.erasmusmc.jerboa.config.Parameters;
import org.erasmusmc.jerboa.dataClasses.Event;
import org.erasmusmc.jerboa.dataClasses.Measurement;
import org.erasmusmc.jerboa.dataClasses.Patient;
import org.erasmusmc.jerboa.dataClasses.Prescription;
import org.erasmusmc.jerboa.engine.InputFile;
import org.erasmusmc.jerboa.engine.InputFileSet;
import org.erasmusmc.jerboa.engine.InputFileSplitter;
import org.erasmusmc.jerboa.utilities.stats.Stats;


/**
 * This class contains a collection of methods assisting in the input
 * file manipulation, creating the necessary folder structure and keeping
 * the look-up tables used in the data compression step of the run.
 *
 * @author MG
 *
 */
public class InputFileUtilities {

	//look-up tables for compression
	public static DualHashBidiMap eventTypes = new DualHashBidiMap();
	public static DualHashBidiMap prescriptionATCS = new DualHashBidiMap();
	public static DualHashBidiMap measurementTypes = new DualHashBidiMap();
	public static DualHashBidiMap measurementValues = new DualHashBidiMap();

	//look-ups for the extended columns - not mandatory data
	public static HashMap<String, DualHashBidiMap> lookupsExtended; // = initLookupExtendedColumns(FilePaths.LOOKUPS_PATH);

	//COMPRESSION RELATED
	/**
	 * Creates a list of look-up tables of most values found in an input file containing patient history.
	 * It makes use of the file containing all the codes found in the input file which was output during the data integrity checking.
	 * @param file - the input file containing the list of codes
	 * @return - a bidirectional map with the codes found in file
	 */
	public static DualHashBidiMap createCodeList(String file){
		try{
			//open the file containing the mapping
			BufferedReader br  = new BufferedReader(new InputStreamReader(
					new FileInputStream(file)));

			String strLine = "";
			DualHashBidiMap list = new DualHashBidiMap();
			while ((strLine = br.readLine()) != null){
				String[] split = strLine.split(Character.toString(DataDefinition.TAB_DELIMITER),2);
				Integer index = Integer.valueOf(split[0]);
				String item = split[1];
				list.put(index,item);
			}
			br.close();
			return list;

		}catch(IOException e){
			return new DualHashBidiMap();
		}
	}

	/**
	 * Loads the look-up tables created from the patient history input files for the current dataset.
	 * It uses the files containing the different codes that were created during the data compression step.
	 */
	public static void loadAllCodeLists(){

		String filePath = FilePaths.LOOKUPS_PATH;
		eventTypes = createCodeList(filePath+FilePaths.FILE_EVENT_TYPES);
		prescriptionATCS = createCodeList(filePath+FilePaths.FILE_PRESCRIPTION_TYPES);
		measurementTypes = createCodeList(filePath+FilePaths.FILE_MEASUREMENT_TYPES);
		measurementValues = createCodeList(filePath+FilePaths.FILE_MEASUREMENT_VALUES);

		//extended (not mandatory) columns
		lookupsExtended = initLookupExtendedColumns(filePath);
	}

	/**
	 * Loads the look-up tables created from the patient history input files for the current dataset.
	 * It uses the files containing the different codes that were created during the data compression step.
	 * @param lookUpsPath - the path towards the look up tables
	 */
	public static void loadAllCodeLists(String lookUpsPath){

		if (lookUpsPath != null && !lookUpsPath.equals("")){
			loadEventCodeLists(lookUpsPath);
			loadPrescriptionCodeLists(lookUpsPath);
			loadMeasurementCodeLists(lookUpsPath);
			loadPatientCodeLists(lookUpsPath);
		}else{
			Logging.add("Invalid path for look-up tables.", Logging.ERROR);
			Logging.add("Please check the look-up tables are in the same working folder.");
		}
	}

	/**
	 * Loads the look-up tables created from the patient history input files for the current dataset.
	 * It uses the files containing the different codes that were created during the data compression step.
	 * @param lookUpsPath - the path towards the look up tables
	 */
	public static void loadEventCodeLists(String lookUpsPath){

		if (lookUpsPath != null && !lookUpsPath.equals("")){
			eventTypes = createCodeList(lookUpsPath+FilePaths.FILE_EVENT_TYPES);
		}else{
			Logging.add("Invalid path for look-up tables.", Logging.ERROR);
			Logging.add("Please check the look-up tables are in the same working folder.");
		}
	}

	/**
	 * Loads the look-up tables created from the patient history input files for the current dataset.
	 * It uses the files containing the different codes that were created during the data compression step.
	 * @param lookUpsPath - the path towards the look up tables
	 */
	public static void loadPrescriptionCodeLists(String lookUpsPath){

		if (lookUpsPath != null && !lookUpsPath.equals("")){
			prescriptionATCS = createCodeList(lookUpsPath+FilePaths.FILE_PRESCRIPTION_TYPES);
		}else{
			Logging.add("Invalid path for look-up tables.", Logging.ERROR);
			Logging.add("Please check the look-up tables are in the same working folder.");
		}
	}

	/**
	 * Loads the look-up tables created from the patient history input files for the current dataset.
	 * It uses the files containing the different codes that were created during the data compression step.
	 * @param lookUpsPath - the path towards the look up tables
	 */
	public static void loadMeasurementCodeLists(String lookUpsPath){

		if (lookUpsPath != null && !lookUpsPath.equals("")){
			measurementTypes = createCodeList(lookUpsPath+FilePaths.FILE_MEASUREMENT_TYPES);
			measurementValues = createCodeList(lookUpsPath+FilePaths.FILE_MEASUREMENT_VALUES);
		}else{
			Logging.add("Invalid path for look-up tables.", Logging.ERROR);
			Logging.add("Please check the look-up tables are in the same working folder.");
		}
	}

	/**
	 * Loads the look-up tables created from the patient history input files for the current dataset.
	 * It uses the files containing the different codes that were created during the data compression step.
	 * @param lookUpsPath - the path towards the look up tables
	 */
	public static void loadPatientCodeLists(String lookUpsPath){

		if (lookUpsPath != null && !lookUpsPath.equals("")){
			//extended (not mandatory) columns
			lookupsExtended = initLookupExtendedColumns(lookUpsPath);
		}else{
			Logging.add("Invalid path for look-up tables.", Logging.ERROR);
			Logging.add("Please check the look-up tables are in the same working folder.");
		}
	}

	public static void addToLookup(DualHashBidiMap lookup, String value) {
		int orgSize = lookup.size();
		InputFileUtilities.addToList(lookup, value);
		if ((!Jerboa.unitTest) && (lookup.size() > orgSize)) {
			InputFileUtilities.outputLookUpTables(Jerboa.getInputFileSet(), true);
		}
	}

	public static void addToPatientLookup(DualHashBidiMap lookup, String value) {
		int orgSize = lookup.size();
		InputFileUtilities.addToList(lookup, value);
		if ((!Jerboa.unitTest) && (lookup.size() > orgSize)) {
			InputFileUtilities.outputPatientLookUpTables(Jerboa.getInputFileSet(), true);
		}
	}

	public static void addToEventLookup(DualHashBidiMap lookup, String value) {
		int orgSize = lookup.size();
		InputFileUtilities.addToList(lookup, value);
		if ((!Jerboa.unitTest) && (lookup.size() > orgSize)) {
			InputFileUtilities.outputEventLookUpTables(Jerboa.getInputFileSet(), true);
		}
	}

	public static void addToPrescriptionLookup(DualHashBidiMap lookup, String value) {
		int orgSize = lookup.size();
		InputFileUtilities.addToList(lookup, value);
		if ((!Jerboa.unitTest) && (lookup.size() > orgSize)) {
			InputFileUtilities.outputPrescriptionLookUpTables(Jerboa.getInputFileSet(), true);
		}
	}

	public static void addToMeasurementLookup(DualHashBidiMap lookup, String value) {
		int orgSize = lookup.size();
		InputFileUtilities.addToList(lookup, value);
		if ((!Jerboa.unitTest) && (lookup.size() > orgSize)) {
			InputFileUtilities.outputMeasurementLookUpTables(Jerboa.getInputFileSet(), true);
		}
	}

	/**
	 * Adds value to the list only if not present.
	 * Makes sure the list does not contain duplicates.
	 * Note that the values will be lower cased.
	 * @param list - the list in which to add
	 * @param value - the value to add
	 * @return - the index in the map
	 */
	public static int addToList(DualHashBidiMap list, String value){
		if (list == null)
			list = new DualHashBidiMap();
		if (!list.containsValue(value))
			list.put(list.size(),value);
		return (int)list.getKey(value);
	}

	/**
	 * Outputs to file the content of the look-up tables used during data compression.
	 * @param inputSet - the dataset serving as input for the application run
	 * @param removeQuotes - true if the string delimiter quotes should be removed before output; false otherwise
	 */
	public static void outputLookUpTables(InputFileSet inputSet, boolean removeQuotes){
		outputPatientLookUpTables(inputSet, removeQuotes);
		outputEventLookUpTables(inputSet, removeQuotes);
		outputPrescriptionLookUpTables(inputSet, removeQuotes);
		outputMeasurementLookUpTables(inputSet, removeQuotes);
	}

	/**
	 * Outputs to file the content of the patient look-up tables used during data compression.
	 * @param inputSet - the dataset serving as input for the application run
	 * @param removeQuotes - true if the string delimiter quotes should be removed before output; false otherwise
	 */
	public static void outputPatientLookUpTables(InputFileSet inputSet, boolean removeQuotes){
		if ((!Jerboa.unitTest) && (inputSet.gotPatients)) { //should always be true
			if (Stats.nbPatients != 0) {
				File dir = new File(FilePaths.LOOKUPS_PATH);
				dir.mkdirs();
				outputLookUpTablesExtended(DataDefinition.PATIENT, removeQuotes);
			}
			else {
				Logging.add("\nNo patients found", Logging.ERROR);
			}
		}
	}

	/**
	 * Outputs to file the content of the event look-up tables used during data compression.
	 * @param inputSet - the dataset serving as input for the application run
	 * @param removeQuotes - true if the string delimiter quotes should be removed before output; false otherwise
	 */
	public static void outputEventLookUpTables(InputFileSet inputSet, boolean removeQuotes){
		if ((!Jerboa.unitTest) && (inputSet.gotEvents)) {
			if (Stats.nbEvents != 0) {
				File dir = new File(FilePaths.LOOKUPS_PATH);
				dir.mkdirs();
				FileUtilities.outputData(FilePaths.EVENT_TYPES_PATH, InputFileUtilities.getEventTypes(), removeQuotes);
				outputLookUpTablesExtended(DataDefinition.EPISODE_EVENT, removeQuotes);
			}
			else {
				Logging.add("\nNo events could be associated with the patients", Logging.ERROR);
			}
		}
	}

	/**
	 * Outputs to file the content of the prescription look-up tables used during data compression.
	 * @param inputSet - the dataset serving as input for the application run
	 * @param removeQuotes - true if the string delimiter quotes should be removed before output; false otherwise
	 */
	public static void outputPrescriptionLookUpTables(InputFileSet inputSet, boolean removeQuotes){
		if ((!Jerboa.unitTest) && (inputSet.gotPrescriptions)) {
			if (Stats.nbPrescriptions != 0) {
				File dir = new File(FilePaths.LOOKUPS_PATH);
				dir.mkdirs();
				FileUtilities.outputData(FilePaths.PRESCRIPTION_TYPES_PATH, InputFileUtilities.getPrescriptionAtcs(), removeQuotes);
				outputLookUpTablesExtended(DataDefinition.EPISODE_PRESCRIPTION, removeQuotes);
			}
			else {
				Logging.add("\nNo prescriptions could be associated with the patients", Logging.ERROR);
			}
		}
	}

	/**
	 * Outputs to file the content of the measurement look-up tables used during data compression.
	 * @param inputSet - the dataset serving as input for the application run
	 * @param removeQuotes - true if the string delimiter quotes should be removed before output; false otherwise
	 */
	public static void outputMeasurementLookUpTables(InputFileSet inputSet, boolean removeQuotes){
		if ((!Jerboa.unitTest) && (inputSet.gotMeasurements)) {
			if (Stats.nbMeasurements != 0) {
				File dir = new File(FilePaths.LOOKUPS_PATH);
				dir.mkdirs();
				FileUtilities.outputData(FilePaths.MEASUREMENT_TYPES_PATH, InputFileUtilities.getMeasurementTypes(), removeQuotes);
				if (InputFileUtilities.getMeasurementValues() != null && !InputFileUtilities.getMeasurementValues().isEmpty())
					FileUtilities.outputData(FilePaths.MEASUREMENT_VALUES_PATH, InputFileUtilities.getMeasurementValues(), removeQuotes);
				outputLookUpTablesExtended(DataDefinition.EPISODE_MEASUREMENT, removeQuotes);
			}
			else {
				Logging.add("\nNo measurements could be associated with the patients", Logging.ERROR);
			}
		}
	}

	/**
	 * Outputs to outputFile the fileErrors found in the input file fileName.
	 * @param outputFile - the name of the file that is checked for errors
	 * @param fromFile - the source file of the errors
	 * @param fileErrors - the errors found in the file
	 */
	public static void outputErrors(String outputFile, String fromFile, List<String> fileErrors){
		// > 1 not only header
		if (fileErrors != null && fileErrors.size() > 0){

			//check if the folder for error logs was not already created
			if (!new File(FilePaths.ERRORS_PATH).exists())
				new File(FilePaths.ERRORS_PATH).mkdirs();

			//set output file name
			outputFile = FilePaths.ERRORS_PATH+Parameters.DATABASE_NAME+"_"+
					TimeUtilities.TIME_STAMP+"_"+outputFile;
			//initialize the error log
			StrBuilder errors = new StrBuilder();
			errors.appendln("Errors in the "+fromFile);
			errors.appendln("------------------------------------------------------------------");
			for (int i = 0; i < fileErrors.size(); i++)
				errors.appendln(fileErrors.get(i));
			//write to file
			try{
				BufferedWriter out = new BufferedWriter(new FileWriter(outputFile));
				out.write(errors.toString());
				out.flush();
				out.close();
			}catch (Exception e){
				Logging.add("Unable to output error log for the file "+fromFile, Logging.ERROR);
			}
		}else{
			Logging.add("The attributes in the "+fromFile+" seem to be OK", Logging.HINT);
		}
	}

	/**
	 * Prints to the log file the checksums of the selected input files.
	 * If the checksums are not yet calculated, it will calculate them
	 * for each input file.
	 * @param inputSet - the set of input files for the current run
	 * @param onlyToLog - true if the checksums should be output only to the log file;
	 * if false, the checksums are output to the console and log file
	 */
	public static void outputChecksums(InputFileSet inputSet, boolean onlyToLog){

		Progress progress = new Progress(Jerboa.getWorkFlow().getNeededInputFiles().length()
				, "Calculating checksums", Jerboa.inConsoleMode);
		progress.show();

		Logging.add("Checksums of selected input files: ", Logging.HINT, onlyToLog);
		Logging.add("-----------------------------------------------------", onlyToLog);
		if (Jerboa.getWorkFlow().getNeededInputFiles().get(DataDefinition.PATIENTS_FILE) &&
				inputSet.getProvidedFiles().get(DataDefinition.PATIENTS_FILE))
			Logging.add("Patient file      : "+inputSet.getPatientsFile().getChecksum()+
					inputSet.getPatientsFile().getName(), onlyToLog);
		progress.update();
		if (Jerboa.getWorkFlow().getNeededInputFiles().get(DataDefinition.EVENTS_FILE) &&
				inputSet.getProvidedFiles().get(DataDefinition.EVENTS_FILE))
			Logging.add("Event file        : "+inputSet.getEventsFile().getChecksum()+
					inputSet.getEventsFile().getName(), onlyToLog);
		progress.update();
		if (Jerboa.getWorkFlow().getNeededInputFiles().get(DataDefinition.PRESCRIPTIONS_FILE) &&
				inputSet.getProvidedFiles().get(DataDefinition.PRESCRIPTIONS_FILE))
			Logging.add("Prescriptions file: "+inputSet.getPrescriptionsFile().getChecksum()+
					inputSet.getPrescriptionsFile().getName(), onlyToLog);
		progress.update();
		if (Jerboa.getWorkFlow().getNeededInputFiles().get(DataDefinition.MEASUREMENTS_FILE) &&
				inputSet.getProvidedFiles().get(DataDefinition.MEASUREMENTS_FILE))
			Logging.add("Measurement file  : "+inputSet.getMeasurementsFile().getChecksum()+
					inputSet.getMeasurementsFile().getName(), onlyToLog);
		Logging.addNewLine();

		progress.update();
		progress.close();
	}

	//FILE STRUCTURE RELATED
	/**
	 * Creates the necessary folder structure for the current run of the application.
	 * It makes use of the properties file in order to affect the folder name
	 * based on the current date and the current run number.
	 * @param pm - the properties manager containing the application properties of interest
	 */
	public static void createFolderStructure(PropertiesManager pm){

		try{
			//creating necessary folder structure
			File dir = new File(FilePaths.DATA_PATH);
			FileUtilities.forceMkdir(dir);

			//retrieve the API daily launch index
			int runIndex = Integer.parseInt(PropertiesManager.listProperties.get("runIndex"));
			dir = new File(FilePaths.DAILY_DATA_PATH);

			//see if running in debug mode and put stamp on output files
			String runMode = PropertiesManager.listProperties.get("runMode");
			if (runMode != null && runMode.contains("debug"))
				TimeUtilities.TIME_STAMP = "debug";

			//see if the folder is created
			if (!dir.mkdir()){
				//or delete the previous files
				FileUtilities.delete(dir);
				//and create the folder
				FileUtilities.forceMkdir(dir);
			}

			//create folder for the API log
			dir = new File(FilePaths.LOG_PATH);
			FileUtilities.forceMkdir(dir);

			//prepare the API log
			Logging.prepareOutputLog();

			//update daily launch index
			pm.updateProperty("runIndex", String.valueOf(++runIndex));
		}catch (Exception e){
			if (Jerboa.inConsoleMode)
				System.out.println("ERROR: Unable to create folder structure.");
			else
				new ErrorHandler("Error while creating folder structure." +
						"\nThe application will not continue.");
			Jerboa.stop(true);
		}
	}

	/**
	 * Creates the necessary folder structure for the debug run of the application.
	 */
	public static void createFolderStructureDebug(){
		try{
			File dir = new File(FilePaths.DATA_PATH);
			if (!dir.exists())
				FileUtilities.forceMkdir(dir);

			dir = new File(FilePaths.DAILY_DATA_PATH);
			//see if the folder is created
			if (!dir.mkdir()){
				//or delete the previous files
				FileUtilities.delete(dir);
				//and create the folder
				FileUtilities.forceMkdir(dir);
			}

			//create folder for the API log
			dir = new File(FilePaths.LOG_PATH);
			FileUtilities.forceMkdir(dir);

			//prepare the API log
			Logging.prepareOutputLog();
		}catch(Exception e){
			Logging.add("Unable to create folder structure for debug.", Logging.ERROR);
			Jerboa.stop(true);
		}
	}

	/**
	 * Creates the necessary folder structure to output patient object files.
	 * If subset ID is present in the input files, the folder structure is created accordingly.
	 * @param splitter - the object used during the splitting of the input data into subsets
	 */
	public static void createPOCFolderStructure(InputFileSplitter splitter){

		try {
			//create folder for patient object files
			FileUtilities.forceMkdir(new File(FilePaths.PATIENTS_PATH));

			//check if there are subset IDs present in the input files
			if (splitter != null && splitter.patientSubsetIDs != null && splitter.patientSubsetIDs.size() > 1){
				Set<String> subsets = splitter.patientSubsetIDs.keySet();
				for (String s : subsets)
					FileUtilities.forceMkdir(new File(FilePaths.PATIENTS_PATH+s+"/"));
			}
		} catch (IOException e) {
			Logging.add("Cannot create folder structure for patient objects", Logging.ERROR);
		}
	}

	/**
	 * Deletes one subset created during the split of the unsorted files.
	 * The subsets are created in the data/split/ folder of each run.
	 * The folder is deleted only if the application is not running in debug mode.
	 * @param split - the number of the split subset (i.e., the name of the folder to be deleted)
	 */
	public static void deleteSubset(int split){
		if (!Jerboa.isInDebugMode){
			try {
				FileUtilities.deleteDirectory(new File(FilePaths.SPLIT_PATH+split));
			} catch (IOException e) {
				Logging.outputStackTrace(e, true);
			}
		}
	}

	/**
	 * Deletes the split folder recursively only if the application is not running
	 * in debug mode.
	 */
	public static void deleteSplitFolder(){
		if (!Jerboa.isInDebugMode){
			try {
				FileUtilities.deleteDirectory(new File(FilePaths.SPLIT_PATH));
			} catch (IOException e) {
				Logging.outputStackTrace(e, true);
			}
		}
	}

	/**
	 * Deletes the whole folder structure and patient object files
	 * created during the compression step, if and only if the application
	 * is not running in debug mode or the reusePatients flag in the script
	 * metadata is set to false.
	 */
	public static void deletePOF(){
		if (!Jerboa.isInDebugMode && !Jerboa.getScriptParser().reusePatients()){
			try {
				FileUtilities.deleteDirectory(new File(FilePaths.PATIENTS_PATH));
			} catch (IOException e) {
				Logging.outputStackTrace(e, true);
			}

			InputFileUtilities.deleteDataFolder(true);
		}
	}

	/**
	 * Deletes the data folder. If onlyIfEmpty flag is set,
	 * then the folder is deleted only if there aren't any
	 * other files inside. If the flag is not set, then everything
	 * inside the folder is deleted recursively
	 * @param onlyIfEmpty - true if the folder should be deleted only if empty; false otherwise
	 */
	public static void deleteDataFolder(boolean onlyIfEmpty){
		File dataFolder = new File(FilePaths.DAILY_DATA_PATH+"/data");
		if(onlyIfEmpty && dataFolder.list().length <= 0){
			try {
				FileUtilities.deleteDirectory(dataFolder);
			} catch (IOException e) {
				Logging.outputStackTrace(e, true);
			}
		}
	}

	/**
	 * Retrieves the date format found in the different selected input files.
	 * it will read the first line after the header and try to parse a date column
	 * with a date format supported. If this results in an invalid date format, the user is warned.
	 * @param inputSet - the input file set containing the selected input files.
	 */
	public static void getDateFormatInDebug(InputFileSet inputSet){

		try{
//			int nbLine = 0;
			BufferedReader patientReader = null;
			BufferedReader eventReader = null;
			BufferedReader prescriptionReader = null;
			BufferedReader measurementReader = null;

			//initialize readers
			if (inputSet != null){
				if (inputSet.getPatientsFile() != null && inputSet.getPatientsFile().getDateFormat() == DateUtilities.DATE_INVALID){
					patientReader = FileUtilities.openFile(inputSet.getPatientsFile().getName());
					patientReader.readLine(); //lose the header
				}
				if (inputSet.getEventsFile() != null && inputSet.getEventsFile().getDateFormat() == DateUtilities.DATE_INVALID){
					eventReader = FileUtilities.openFile(inputSet.getEventsFile().getName());
					eventReader.readLine(); //lose the header
				}
				if (inputSet.getPrescriptionsFile() != null && inputSet.getPrescriptionsFile().getDateFormat() == DateUtilities.DATE_INVALID){
					prescriptionReader = FileUtilities.openFile(inputSet.getPrescriptionsFile().getName());
					prescriptionReader.readLine(); //lose the header
				}
				if (inputSet.getMeasurementsFile() != null && inputSet.getMeasurementsFile().getDateFormat() == DateUtilities.DATE_INVALID){
					measurementReader = FileUtilities.openFile(inputSet.getMeasurementsFile().getName());
					measurementReader.readLine(); //lose the header
				}
				//get date format from first 10 lines
				String[] attributes = null;
//				while (nbLine < 10){
					if (patientReader != null){
						 attributes = StringUtilities.splitLine(patientReader.readLine(),
								inputSet.getPatientsFile().getDelimiter());
						 inputSet.getPatientsFile().setDateFormat(DateUtilities.dateFormat(attributes[inputSet.getPatientsFile().getDataOrder()[Patient.COLUMN_BIRTHDATE]]));
					}
					if (eventReader != null){
						 attributes = StringUtilities.splitLine(eventReader.readLine(),
								inputSet.getEventsFile().getDelimiter());
						 inputSet.getEventsFile().setDateFormat(DateUtilities.dateFormat(attributes[inputSet.getEventsFile().getDataOrder()[Event.COLUMN_DATE]]));
					}
					if (prescriptionReader != null){
						 attributes = StringUtilities.splitLine(prescriptionReader.readLine(),
								inputSet.getPrescriptionsFile().getDelimiter());
						 inputSet.getPrescriptionsFile().setDateFormat(DateUtilities.dateFormat(attributes[inputSet.getPrescriptionsFile().getDataOrder()[Prescription.COLUMN_DATE]]));
					}
					if (measurementReader != null){
						 attributes = StringUtilities.splitLine(measurementReader.readLine(),
								inputSet.getMeasurementsFile().getDelimiter());
						 inputSet.getMeasurementsFile().setDateFormat(DateUtilities.dateFormat(attributes[inputSet.getMeasurementsFile().getDataOrder()[Measurement.COLUMN_DATE]]));
					}
//					nbLine ++;
//				}

				//close readers
				if (patientReader != null){ patientReader.close(); patientReader = null; }
				if (eventReader != null){ eventReader.close(); eventReader = null; }
				if (prescriptionReader != null){ prescriptionReader.close(); prescriptionReader = null; }
				if (measurementReader != null){ measurementReader.close(); measurementReader = null; }

				if (inputSet.getPatientsFile() != null && inputSet.getPatientsFile().getDateFormat() == DateUtilities.DATE_INVALID)
					System.out.println("Invalid date format in patients file");
				if (inputSet.getEventsFile() != null && inputSet.getEventsFile().getDateFormat() == DateUtilities.DATE_INVALID)
					System.out.println("Invalid date format in events file");
				if (inputSet.getPrescriptionsFile() != null && inputSet.getPrescriptionsFile().getDateFormat() == DateUtilities.DATE_INVALID)
					System.out.println("Invalid date format in prescriptions file");
				if (inputSet.getMeasurementsFile() != null && inputSet.getMeasurementsFile().getDateFormat() == DateUtilities.DATE_INVALID)
					System.out.println("Invalid date format in measurements file");

			}
		}catch(IOException e){
			System.out.println("Unable to read input files");
		}
	}

	//SPECIFIC FOR INITIALIZATION AND OUTPUT
	/**
	 * Initializes for every episode type the look-up tables for the
	 * extended columns in the input file. Note that the extended data
	 * columns are not mandatory and their presence not necessarily predictable.
	 * @param lookupPath - the path for the look-up export
	 * @return - the initialized map of look-up tables for the extended data
	 */
	protected static HashMap<String, DualHashBidiMap> initLookupExtendedColumns(String lookupPath){

		HashMap<String, DualHashBidiMap> lookupsExtended = new HashMap<String, DualHashBidiMap>();

		if (getExtendedDataOrder(DataDefinition.PATIENTS_FILE) != null){
			Set<String> extendedColumns = new HashSet<String>(getExtendedDataOrder(DataDefinition.PATIENTS_FILE).values());
			for (String column : extendedColumns)
				lookupsExtended.put(DataDefinition.PATIENT+"_"+column,
						createCodeList(lookupPath+DataDefinition.PATIENT+"_"+column+".txt"));
		}

		if (getExtendedDataOrder(DataDefinition.EVENTS_FILE) != null){
			Set<String> extendedColumns = new HashSet<String>(getExtendedDataOrder(DataDefinition.EVENTS_FILE).values());
			for (String column : extendedColumns)
				lookupsExtended.put(DataDefinition.EPISODE_EVENT+"_"+column,
						createCodeList(lookupPath+DataDefinition.EPISODE_EVENT+"_"+column+".txt"));
		}

		if (getExtendedDataOrder(DataDefinition.PRESCRIPTIONS_FILE) != null){
			Set<String> extendedColumns = new HashSet<String>(getExtendedDataOrder(DataDefinition.PRESCRIPTIONS_FILE).values());
			for (String column : extendedColumns)
				lookupsExtended.put(DataDefinition.EPISODE_PRESCRIPTION+"_"+column,
						createCodeList(lookupPath+DataDefinition.EPISODE_PRESCRIPTION+"_"+column+".txt"));
		}

		if (getExtendedDataOrder(DataDefinition.MEASUREMENTS_FILE) != null){
			Set<String> extendedColumns = new HashSet<String>(getExtendedDataOrder(DataDefinition.MEASUREMENTS_FILE).values());
			for (String column : extendedColumns)
				lookupsExtended.put(DataDefinition.EPISODE_MEASUREMENT+"_"+column,
						createCodeList(lookupPath+DataDefinition.EPISODE_MEASUREMENT+"_"+column+".txt"));
		}

		return lookupsExtended;
	}

	/**
	 * Outputs all the look-up tables for the extended data columns for this episodeType.
	 * @param episodeType - the type of episode for which the look-ups are to be exported
	 * @param removeQuotes - flag to export strings with quotes or not
	 */
	protected static void outputLookUpTablesExtended(String episodeType, boolean removeQuotes){
		Set<String> values = new HashSet<String>(getExtendedDataOrder(episodeType).values());
		for (String value : values) {
			getLookUpExtended(episodeType, value);
			if (lookupsExtended.get(episodeType+"_"+value) != null &&
				!lookupsExtended.get(episodeType+"_"+value).isEmpty())
				FileUtilities.outputData(FilePaths.LOOKUPS_PATH+episodeType+"_"+value+".txt",
						lookupsExtended.get(episodeType+"_"+value), removeQuotes);
		}
	}

	/**
	 * Adds the extended data columns that are present in the input file
	 * to the header for file export. Note that if the look-up table for
	 * a certain extended column is empty, then it is considered that the
	 * input file does not contain that column.
	 * @param episodeType - the type of episode
	 * @return - a string which represents the part of the header for extended data columns
	 */
	protected static String getExtendedColumnsForHeaderExport(String episodeType){
		String s = "";
		for (String column : (getExtendedDataOrder(episodeType).values()))
			s += (lookupsExtended != null && !lookupsExtended.get(episodeType+"_"+column).isEmpty()) ?
					"," + StringUtilities.capitalize(column) : "";
		return s;
	}

	/**
	 * Calculates the total size (in KB) of the input file set.
	 * @param files - the files forming the input set
	 * @return - the total size of the files in KB
	 */
	public static long getInputFileSetSize(List<InputFile> files){
		long totalSize = 0;
		if (files != null && files.size() > 0)
			for (InputFile f : files)
				if (f != null)
					totalSize += f.getSize();

		return totalSize;
	}

	/**
	 * Formats the string that will contain the size of an input file,
	 * in order to display it in the log file of the application.
	 * @param sizeInBytes - the size of the input file in number of bytes
	 * @return - a prettified string containing the size of the input file
	 */
	public static String formatFileSize(long sizeInBytes){
		int kb = 1024;
		int mb = kb * kb;
		int gb = mb * kb;

		String formatedSize = "";

		formatedSize = (sizeInBytes/kb < 1) ? sizeInBytes+ " B" :
			(sizeInBytes/mb < 1) ? StringUtilities.format(sizeInBytes/(double)kb)+" KB" :
				(sizeInBytes/gb < 1) ?  StringUtilities.format(sizeInBytes/(double)mb)+" MB" :
					 StringUtilities.format(sizeInBytes/(double)gb)+" GB";
		return formatedSize;
	}

	/**
	 * Returns the index of this episodeType in the lookUp passed as argument.
	 * @param episodeType - the episode of interest
	 * @param lookUp - the look-up table containing the episode types
	 * @return - the position in the list of the episodeType
	 */
	public static int getIndexOf(String episodeType, DualHashBidiMap lookUp){
		return (int)lookUp.getKey(episodeType);
	}

	public static DualHashBidiMap getEventTypes() {
		return eventTypes;
	}

	public static DualHashBidiMap getPrescriptionAtcs() {
		return prescriptionATCS;
	}

	public static DualHashBidiMap getMeasurementTypes() {
		return measurementTypes;
	}

	public static DualHashBidiMap getMeasurementValues() {
		return measurementValues;
	}

	/**
	 * Returns a look-up table used for any of the extended data columns
	 * for an episode type. It first checks if the look-up exists and if not
	 * it initializes it.
	 * @param episodeType - the type of episode
	 * @param extendedColumn - the extended data column of interest
	 * @return - the look-up containing the mapping for the extendedColumn
	 */
	public static DualHashBidiMap getLookUpExtended(String episodeType, String extendedColumn){
		if (lookupsExtended == null)
			lookupsExtended = new HashMap<String, DualHashBidiMap>();
		if (lookupsExtended.get(episodeType+"_"+extendedColumn) == null)
			lookupsExtended.put(episodeType+"_"+extendedColumn, new DualHashBidiMap());
		return lookupsExtended.get(episodeType+"_"+extendedColumn);
	}

	public static DualHashBidiMap getPatientLookUpExtended(String extendedColumn){
		return getLookUpExtended(DataDefinition.PATIENT, extendedColumn);
	}

	public static DualHashBidiMap getEventLookUpExtended(String extendedColumn){
		return getLookUpExtended(DataDefinition.EPISODE_EVENT, extendedColumn);
	}

	public static DualHashBidiMap getPrescriptionLookUpExtended(String extendedColumn){
		return getLookUpExtended(DataDefinition.EPISODE_PRESCRIPTION, extendedColumn);
	}

	public static DualHashBidiMap getMeasurementLookUpExtended(String extendedColumn){
		return getLookUpExtended(DataDefinition.EPISODE_MEASUREMENT, extendedColumn);
	}

	//what is so far known - hard coded just to ease up manipulation
	public static DualHashBidiMap getPrescriptionDoses() {
		return getLookUpExtended(DataDefinition.EPISODE_PRESCRIPTION, "dose");
	}

	public static DualHashBidiMap getPrescriptionIndications() {
		return getLookUpExtended(DataDefinition.EPISODE_PRESCRIPTION, "indication");
	}

	public static DualHashBidiMap getPrescriptionFormulations() {
		return getLookUpExtended(DataDefinition.EPISODE_PRESCRIPTION, "formulation");
	}

	public static DualHashBidiMap getPrescriptionStrengths() {
		return getLookUpExtended(DataDefinition.EPISODE_PRESCRIPTION, "strength");
	}

	public static DualHashBidiMap getPrescriptionVolumes() {
		return getLookUpExtended(DataDefinition.EPISODE_PRESCRIPTION, "volume");
	}

	public static DualHashBidiMap getPrescriptionPrescriberIds() {
		return getLookUpExtended(DataDefinition.EPISODE_PRESCRIPTION, "prescriber_id");
	}

	public static DualHashBidiMap getPrescriptionPrescriberTypes() {
		return getLookUpExtended(DataDefinition.EPISODE_PRESCRIPTION, "prescriber_type");
	}

	public static DualHashBidiMap getEventCodes() {
		return getLookUpExtended(DataDefinition.EPISODE_EVENT, "code");
	}

	public static DualHashBidiMap getMeasurementUnits() {
		return getLookUpExtended(DataDefinition.EPISODE_MEASUREMENT, "unit");
	}

	/**
	 * Checks if the main look-up tables (mandatory data) are empty.
	 * @return - true if the look-ups for the episode types are empty; false otherwise
	 */
	public static boolean isLookUpsEmpty(){
		return prescriptionATCS == null || eventTypes == null ||
				measurementTypes == null;
	}

	//GETTERS FOR INPUT FILE HEADERS
	public static String getPatientsFileHeaderForExport(){
		return StringUtils.join(StringUtilities.capitalize(DataDefinition.PATIENT_COLUMNS), ",") +
				getExtendedColumnsForHeaderExport(DataDefinition.PATIENT);
	}

	public static String getPatientsFileHeaderForExportLong(boolean withID){
		String header = InputFileUtilities.getPatientsFileHeaderForExport() +
				"," + "PopulationStartDate" +
				"," + "PopulationEndDate" +
				"," + "CohortStartDate" +
				"," + "CohortEndDate";
		if (!withID) {
			header = header.substring(header.indexOf(",") + 1);
		}
		return header;
	}

	public static String getEventsFileHeaderForExport(){
		return StringUtils.join(StringUtilities.capitalize(DataDefinition.EVENT_COLUMNS), ",") +
				getExtendedColumnsForHeaderExport(DataDefinition.EPISODE_EVENT);
	}

	public static String getPrescriptionsFileHeaderForExport(){
		return StringUtils.join(StringUtilities.capitalize(DataDefinition.PRESCRIPTION_COLUMNS), ",") +
				getExtendedColumnsForHeaderExport(DataDefinition.EPISODE_PRESCRIPTION);
	}

	public static String getMeasurementsFileHeaderForExport(){
		return StringUtils.join(StringUtilities.capitalize(DataDefinition.MEASUREMENT_COLUMNS), ",")+
				getExtendedColumnsForHeaderExport(DataDefinition.EPISODE_MEASUREMENT);
	}

	//INPUT DATA COLUMNS RELATED
	/**
	 * Returns an array of the date columns from an input file
	 * based on the input fileType.
	 * @param fileType - the type of the input file
	 * @return - an array of the columns containing a date attribute; null if fileType not
	 * one of the ones defined in DataDefinition
	 */
	public static short[] getDateColumns(byte fileType){
		short[] columns = null;
		if (fileType != DataDefinition.NO_FILE){
			switch (fileType) {
			case DataDefinition.PATIENTS_FILE :
				columns = new short[]{Patient.COLUMN_BIRTHDATE, Patient.COLUMN_START_DATE, Patient.COLUMN_END_DATE};
				break;
			case DataDefinition.EVENTS_FILE :
				columns = new short[]{Event.COLUMN_DATE};
				break;
			case DataDefinition.PRESCRIPTIONS_FILE :
				columns = new short[]{Prescription.COLUMN_DATE};
				break;
			case DataDefinition.MEASUREMENTS_FILE :
				columns = new short[]{Measurement.COLUMN_DATE};
				break;
			}
		}

		return columns;
	}

	/**
	 * Returns the name of an episode type as defined in this
	 * class, based on the fileType parameter.
	 * @param fileType - the type of input file as defined in this class.
	 * @return - the string representation of the episode name corresponding to fileType.
	 */
	public static String getEpisodeName(byte fileType){
		String name = null;
		if (fileType != DataDefinition.NO_FILE){
			switch (fileType) {
			case DataDefinition.PATIENTS_FILE :
				name = DataDefinition.PATIENT;
				break;
			case DataDefinition.EVENTS_FILE :
				name = DataDefinition.EPISODE_EVENT;
				break;
			case DataDefinition.PRESCRIPTIONS_FILE :
				name = DataDefinition.EPISODE_PRESCRIPTION;
				break;
			case DataDefinition.MEASUREMENTS_FILE :
				name = DataDefinition.EPISODE_MEASUREMENT;
				break;
			}
		}

		return name;
	}

	/**
	 * Checks if episodeName is one of the supported episode types
	 * as defined in DataDefinition.
	 * @param episodeName - the name of the episode to be checked
	 * @return - true if episodeName is a valid type of episode; false otherwise
	 */
	public static boolean isValidEpisodeName(String episodeName){
		if (episodeName != null && !episodeName.equals("")){
			switch (episodeName) {
			case DataDefinition.PATIENT :
				return true;
			case DataDefinition.EPISODE_EVENT :
				return true;
			case DataDefinition.EPISODE_PRESCRIPTION :
				return true;
			case DataDefinition.EPISODE_MEASUREMENT :
				return true;
			}
		}

		return false;
	}

	/**
	 * Returns the number of mandatory columns for the fileType.
	 * If the fileType is not defined the function returns 0.
	 * @param fileType - the type of the input file
	 * @return - the number of extended columns for this fileType
	 */
	public static int getNumberOfMandatoryColumns(byte fileType){
		int nbColumns = 0;
		if (fileType != DataDefinition.NO_FILE){
			switch (fileType){
			case DataDefinition.PATIENTS_FILE:
				nbColumns = DataDefinition.PATIENT_COLUMNS.length;
				break;
			case DataDefinition.EVENTS_FILE:
				nbColumns = DataDefinition.EVENT_COLUMNS.length;
				break;
			case DataDefinition.PRESCRIPTIONS_FILE:
				nbColumns = DataDefinition.PRESCRIPTION_COLUMNS.length;
				break;
			case DataDefinition.MEASUREMENTS_FILE:
				nbColumns = DataDefinition.MEASUREMENT_COLUMNS.length;
				break;
			}
		}

		return nbColumns;
	}

	/**
	 * Retrieve the column names specific to an input file type.
	 * If the fileType is not defined the function returns NULL.
	 * @param fileType - the type of the input file
	 * @return - an array of strings containing the mandatory column
	 * names for the fileType; or null for invalid fileType
	 */
	public static String[] getColumnNames(short fileType){

		String[] columns = null;
		if (fileType != DataDefinition.NO_FILE){
			switch (fileType) {
			case DataDefinition.PATIENTS_FILE :
				columns = DataDefinition.PATIENT_COLUMNS;
				break;
			case DataDefinition.EVENTS_FILE :
				columns = DataDefinition.EVENT_COLUMNS;
				break;
			case DataDefinition.PRESCRIPTIONS_FILE :
				columns = DataDefinition.PRESCRIPTION_COLUMNS;
				break;
			case DataDefinition.MEASUREMENTS_FILE :
				columns = DataDefinition.MEASUREMENT_COLUMNS;
				break;
			}
		}

		return columns;
	}
	/**
	 * Returns the extended data columns present in the input file for the inputFileType.
	 * If the inputFileType is not defined the function returns null.
	 * @param inputFileType - the type of the input file
	 * @return - the extended data columns under a map representation for this inputFileType
	 */
	public static Map<Integer, String> getExtendedDataOrder(byte inputFileType){
		if (Jerboa.getInputFileSet() != null){
			switch (inputFileType){
			case DataDefinition.PATIENTS_FILE :
				return Jerboa.getInputFileSet().getPatientsFile().getExtendedDataOrder();
			case DataDefinition.EVENTS_FILE :
				return (Jerboa.getInputFileSet().getEventsFile() != null ?
						Jerboa.getInputFileSet().getEventsFile().getExtendedDataOrder() : null);
			case DataDefinition.PRESCRIPTIONS_FILE :
				return (Jerboa.getInputFileSet().getPrescriptionsFile() != null ?
						Jerboa.getInputFileSet().getPrescriptionsFile().getExtendedDataOrder() : null);
			case DataDefinition.MEASUREMENTS_FILE :
				return (Jerboa.getInputFileSet().getMeasurementsFile() != null ?
						Jerboa.getInputFileSet().getMeasurementsFile().getExtendedDataOrder() : null);
			}
		}

		return null;
	}

	/**
	 * Returns the extended data columns present in the input file for the episodeType.
	 * If the episodeType is not defined the function returns null.
	 * @param episodeType - the type of the episode
	 * @return - the extended data columns under a map representation for this episodeType
	 */
	public static Map<Integer, String> getExtendedDataOrder(String episodeType){
		if (Jerboa.getInputFileSet() != null){
			switch (episodeType){
			case DataDefinition.PATIENT :
				return Jerboa.getInputFileSet().getPatientsFile().getExtendedDataOrder();
			case DataDefinition.EPISODE_EVENT :
				return (Jerboa.getInputFileSet().getEventsFile() != null ?
						Jerboa.getInputFileSet().getEventsFile().getExtendedDataOrder() : null);
			case DataDefinition.EPISODE_PRESCRIPTION :
				return (Jerboa.getInputFileSet().getPrescriptionsFile() != null ?
						Jerboa.getInputFileSet().getPrescriptionsFile().getExtendedDataOrder() : null);
			case DataDefinition.EPISODE_MEASUREMENT :
				return (Jerboa.getInputFileSet().getMeasurementsFile() != null ?
						Jerboa.getInputFileSet().getMeasurementsFile().getExtendedDataOrder() : null);
			}
		}

		return null;
	}

	/**
	 * Returns the number of mandatory columns for the episodeType.
	 * If the fileType is not defined the function returns 0.
	 * @param episodeType - the type of the input file
	 * @return - the number of extended columns for the episodeType
	 */
	public static int getNumberOfMandatoryColumns(String episodeType){
		switch (episodeType){
		case DataDefinition.PATIENT :
			return getNumberOfMandatoryColumns(DataDefinition.PATIENTS_FILE);
		case DataDefinition.EPISODE_EVENT :
			return getNumberOfMandatoryColumns(DataDefinition.EVENTS_FILE);
		case DataDefinition.EPISODE_PRESCRIPTION :
			return getNumberOfMandatoryColumns(DataDefinition.PRESCRIPTIONS_FILE);
		case DataDefinition.EPISODE_MEASUREMENT :
			return getNumberOfMandatoryColumns(DataDefinition.MEASUREMENTS_FILE);
		}

		return 0;
	}

	/**
	 * Returns the number of extended columns for the fileType.
	 * If the fileType is not defined the function returns 0.
	 * @param fileType - the type of the input file
	 * @return - the number of extended columns for the fileType
	 */
	public static int getNumberOfExtenedColumns(byte fileType){
		if (fileType != DataDefinition.NO_FILE && Jerboa.getInputFileSet() != null){
			switch (fileType){
			case DataDefinition.PATIENTS_FILE:
				return Jerboa.getInputFileSet().getPatientsFile().getExtendedDataOrder().size();
			case DataDefinition.EVENTS_FILE:
				return (Jerboa.getInputFileSet().getEventsFile() != null ?
						Jerboa.getInputFileSet().getEventsFile().getExtendedDataOrder().size() : 0);
			case DataDefinition.PRESCRIPTIONS_FILE:
				return (Jerboa.getInputFileSet().getPrescriptionsFile() != null ?
						Jerboa.getInputFileSet().getPrescriptionsFile().getExtendedDataOrder().size() : 0);
			case DataDefinition.MEASUREMENTS_FILE:
				return (Jerboa.getInputFileSet().getMeasurementsFile() != null ?
						Jerboa.getInputFileSet().getMeasurementsFile().getExtendedDataOrder().size() : 0);
			}
		}

		return 0;
	}

	/**
	 * Returns the number of extended columns for this episodeType.
	 * If the episodeType is not defined the function returns 0.
	 * @param episodeType - the type of the input file
	 * @return - the number of extended columns for the episodeType
	 */
	public static int getNumberOfExtenedColumns(String episodeType){
		if (Jerboa.getInputFileSet() != null){
			switch (episodeType){
			case DataDefinition.PATIENT:
				return Jerboa.getInputFileSet().getPatientsFile().getExtendedDataOrder().size();
			case DataDefinition.EPISODE_EVENT:
				return (Jerboa.getInputFileSet().getEventsFile() != null ?
						Jerboa.getInputFileSet().getEventsFile().getExtendedDataOrder().size() : 0);
			case DataDefinition.EPISODE_PRESCRIPTION:
				return (Jerboa.getInputFileSet().getPrescriptionsFile() != null ?
						Jerboa.getInputFileSet().getPrescriptionsFile().getExtendedDataOrder().size() : 0);
			case DataDefinition.EPISODE_MEASUREMENT:
				return (Jerboa.getInputFileSet().getMeasurementsFile() != null ?
						Jerboa.getInputFileSet().getMeasurementsFile().getExtendedDataOrder().size() : 0);
			}
		}

		return 0;
	}

}
