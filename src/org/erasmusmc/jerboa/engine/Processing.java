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
 * $Rev:: 4809              $:  Revision of last commit                                   *
 * $Author:: root           $:  Author of last commit                                     *
 * $Date:: 					$:  Date and time (CET) of last commit						  *
 ******************************************************************************************/

package org.erasmusmc.jerboa.engine;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.erasmusmc.jerboa.Jerboa;
import org.erasmusmc.jerboa.utilities.inputChecking.EventsFileChecker;
import org.erasmusmc.jerboa.utilities.inputChecking.MeasurementsFileChecker;
import org.erasmusmc.jerboa.utilities.inputChecking.PatientsFileChecker;
import org.erasmusmc.jerboa.utilities.inputChecking.PrescriptionsFileChecker;
import org.erasmusmc.jerboa.utilities.stats.Stats;

import org.erasmusmc.jerboa.utilities.Logging;
import org.erasmusmc.jerboa.utilities.InputFileUtilities;
import org.erasmusmc.jerboa.utilities.MemoryBenchmark;
import org.erasmusmc.jerboa.utilities.Timer;

import org.erasmusmc.jerboa.gui.graphs.ActivePatientsPlot;
import org.erasmusmc.jerboa.gui.graphs.Graphs;
import org.erasmusmc.jerboa.gui.JerboaGUI;

import org.erasmusmc.jerboa.config.DataDefinition;
import org.erasmusmc.jerboa.config.FilePaths;
import org.erasmusmc.jerboa.config.PropertiesManager;

import org.erasmusmc.jerboa.utilities.ErrorHandler;

/**
 * This is the launching thread of the software. Starts the input data checking
 * and continues with the work flow if the input data meets the necessary requirements.
 * Optionally, data can be extracted via SQL queries presented under the form of a script.
 *
 * @author MG
 *
 */
public class Processing extends Thread {

	//input checking flags
	public static boolean checkOnly;
	public static boolean filesSorted;
	public static boolean noInputErrors;
	public static boolean allRequiredFilesPresent;

	//the folder for the current run
	public static String workingFolder;

	//dataset
	private InputFileSet inputSet;

	//error related
	private static List<List<String>> inputErrors;
	private static List<String> patientErrors;

	private Timer timer = new Timer();
	public static Timer overallTimer = new Timer();

	//CONSTRUCTOR
	/**
	 * Constructor receiving an inputSet as parameter.
	 * @param inputSet - the dataset formed from the working folder
	 */
	public Processing(InputFileSet inputSet){
		this.inputSet = inputSet;
		inputErrors = new ArrayList<List<String>>();
		patientErrors = new ArrayList<String>();
		filesSorted = true;
		noInputErrors = false;
	}

	/**
	 * Method launching the processing thread.
	 */
	public void run() {

		overallTimer.start();
		displayVersionInfo();

		if (Jerboa.getScriptParser().reusePatients() && Jerboa.hasDebugFolder){

			Logging.add("Reusing patient object files.", Logging.HINT);
			InputFileUtilities.loadAllCodeLists();

			// Restore input statistics
			new Stats();
			Stats.restoreStats(FilePaths.DATA_PATH + FilePaths.RUN_FOLDER);

			startWorkflow();
			exportGraphs(); //if needed
			packResults(); //if needed

		}else{

			extractData(); //if needed
			displayNeededFiles();
			logChecksums(true);//can be turned off via script

			//check if the needed files are present in the working folder
			if (isInputOK()){
				//remove files that are not needed for this run
				busy();
				try{
					//check input files
					timer.start();
					checkInputFiles();
					//display timers
					timer.stop();
					timer.displayTotal("Input file(s) checked in");

					//check if input really sorted; if not restart and split
					if (noInputErrors){
						if (!checkOnly){
							InputFileSplitter splitter = null;
							if (!(filesSorted = inputSorted())){

								Logging.addWithTimeStamp("Sorting input files");

								timer.resetTotal();
								timer.start();
								MemoryBenchmark.Automated.putMarker("Sorting data");
								//divide files into subsets
								splitter = new InputFileSplitter(inputSet);
								splitInputFiles(splitter);

								timer.stopAndDisplay("Input files(s) sorted in");
							}

							//loading patient objects
							Logging.addWithTimeStamp("Compressing data");
							//launch the patient object loader
							MemoryBenchmark.Automated.putMarker("Creating patient objects");
							PatientObjectCreator poc = new PatientObjectCreator(inputSet, filesSorted, splitter);

							//display the active patients chart
							if (!Jerboa.inConsoleMode)
								JerboaGUI.addTab(new ActivePatientsPlot(Stats.getActivePatients(), "Active population").panel, "Active patients");

							//check if there were errors in the patient objects
							patientErrors = poc.getErrors();

							if (Jerboa.getScriptParser().reusePatients()) {
								Stats.saveStats(FilePaths.DATA_PATH + FilePaths.RUN_FOLDER);
							}

							//do the work
							startWorkflow();
							exportGraphs(); //if needed
							packResults(); //if needed

						}
						//there are input errors
					}else{

						if (Jerboa.inConsoleMode){
							Logging.add("The input contains errors. Check the log files.", Logging.HINT);
						}else{
							Logging.add("\nThe input contains errors. Check the errors using View->File Errors", Logging.HINT);
						}

						//enable if necessary the error view menu
						if (!Jerboa.inConsoleMode)
							JerboaGUI.setErrorMenu();

						Jerboa.stop(true);
					}

				}catch (Throwable e){
					Logging.outputStackTrace(e);
					if (!Jerboa.inConsoleMode)
						JerboaGUI.done();
					else
						System.exit(1);
				}
				//end if (inputOK)
			}else{
				if (Jerboa.getWorkFlow().noValidModule())
					Logging.add("No valid module found in the script", Logging.HINT);
				else
					Logging.add(!allRequiredFilesPresent ? "Not all required input files were selected." +
						"\nPlease make sure that you have selected the right input files." :
							(Jerboa.getWorkFlow() == null && !checkOnly) ?	"There is no script file present in the working folder." +
									"\nThe application will not continue." : "The input files do not contain (all) required extended data columns.Check View -> Log\n" +
										"Please make sure the input files are according to the specifications.", Logging.ERROR);
				if (!Jerboa.inConsoleMode)
					JerboaGUI.done();
				Jerboa.stop(true);
			}
		}//end if reusePatients
	}//end run()

	/*----------------------END OF THREAD------------------------*/

	/**
	 * Displays the version information found in the script file on the console.
	 */
	private void displayVersionInfo(){
		//set module parameters and display version info
		if (!checkOnly && Jerboa.getWorkFlow() != null){
			Jerboa.getWorkFlow().printScriptVersion();
		}
	}

	/**
	 * Displays the list of needed input files based on the modules present in the script file.
	 */
	private void displayNeededFiles(){
		if (Jerboa.getWorkFlow() != null && !Jerboa.getWorkFlow().noValidModule())
			Jerboa.getWorkFlow().printNeededInputFiles(inputSet.getProvidedFiles());
	}

	/**
	 * Logs the checksums of each needed input file.
	 * @param onlyToLog - true if the output should be only to the log file;
	 * if false, the checksums are output also in the console
	 */
	private void logChecksums(Boolean onlyToLog){
		if (Jerboa.getScriptParser().isChecksumRequired()){
			MemoryBenchmark.Automated.putMarker("Calculating checksums");
			InputFileUtilities.outputChecksums(inputSet, onlyToLog);
		}
	}

	/**
	 * To be called if another thread is to be started
	 * at the end of the processing.
	 * Note! that if new threads are added in the run() method
	 * they should be present here also.
	 */
	//NOT USED
	public static void waitToJoin(){
		try {
			//wait for threads to finish
			if (Jerboa.getWorkFlow() != null)
				Jerboa.getWorkFlow().join();
			if (Graphs.getExportThread() != null)
				Graphs.getExportThread().join();
		}catch (InterruptedException e) {
			Logging.outputStackTrace(e);
		}
	}

	/**
	 * Will run the data extraction part of the script (based on SQL queries)
	 * and reinitialize the input file set.
	 */
	private void extractData(){

		if (Jerboa.getWorkFlow() != null &&
				Jerboa.getWorkFlow().getDataExtraction() != null){
			try{
				MemoryBenchmark.Automated.putMarker("Extracting data");
				Jerboa.getWorkFlow().getDataExtraction().run();
				inputSet = new InputFileSet(FilePaths.WORKING_PATH);
			}catch(Exception e){
				Logging.add("Unable to extract data");
				Logging.outputStackTrace(e);
				Jerboa.stop(true);
			}
		}
	}

	/**
	 * Will launch the workflow thread.
	 */
	private void startWorkflow(){
		if (Jerboa.getWorkFlow() != null)
			Jerboa.getWorkFlow().start();
		else
			Logging.add("The workflow was not initialized", Logging.ERROR);
	}

	/**
	 * Will encrypt and zip the result files.
	 */
	private void packResults(){
		if (Jerboa.getWorkFlow() != null &&
				Jerboa.getWorkFlow().getResultPacking() != null)
			Jerboa.getWorkFlow().getResultPacking().run();
		else
			Logging.add("Result packing was not initialized", Logging.HINT, true);
	}

	/**
	 * Will export the eventual created graphs to PDF.
	 */
	private void exportGraphs(){
		Graphs.exportGraphs();
	}

	/**
	 * Checks if the list of input files contains at least the files needed by the modules
	 * in order to start processing and that each of the input files has the required
	 * extended data columns. If there is no script file found, check if there are
	 * input files in the working folder.
	 * @return - true if the above mentioned conditions are met; false otherwise
	 */
	private boolean isInputOK(){
		if (Jerboa.getWorkFlow() != null && !Jerboa.getWorkFlow().noValidModule()){
			allRequiredFilesPresent = checkRequiredInputFiles();
			return allRequiredFilesPresent && checkRequiredExtendedData();
		}else if (checkOnly && inputSet.getSelectedFiles() != null){
			return inputSet.getSelectedFiles().size() > 0;
		}else{
			return false;
		}
	}

	/**
	 * Checks if all the required input files that are required by the modules
	 * are provided in the chosen workspace.
	 * @return - true if all needed input files are present; false otherwise
	 */
	private boolean checkRequiredInputFiles(){
		String providedFiles = inputSet.getProvidedFiles().
				toString().replace("{", "").replace("}", "");
		String neededFiles = Jerboa.getWorkFlow().getNeededInputFiles().
				toString().replace("{", "").replace("}", "");
		return Jerboa.getWorkFlow().getNeededInputFiles().equals(inputSet.getProvidedFiles()) ||
				providedFiles.contains(neededFiles);
	}

	/**
	 * Checks if all the required extended data columns that are required by the modules
	 * are provided in the input files.
	 * @return - true if all needed extended data columns are present; false otherwise
	 */
	private boolean checkRequiredExtendedData(){
		boolean isOK = true;
		for (int i = 0; i < inputSet.getSelectedFiles().size(); i ++){
			if (inputSet.getProvidedFiles().get(i)){
				InputFile file = inputSet.getSelectedFiles().get(i);
				Set<String> needed = Jerboa.getWorkFlow().getNeededExtendedColumns().get((int)file.getType());
				Set<String> provided = new HashSet<String>(file.getExtendedDataOrder().values());
				isOK &= (needed == null ? true : provided.containsAll(needed));
				//log the missing extended data columns per input file type
				if (!isOK && needed != null){
					needed.removeAll(provided);
					if (needed.size() > 0)
						Logging.add("Missing extended data column for the "+
								InputFileUtilities.getEpisodeName(file.getType())+
								" input file: "+needed.toString(), Logging.ERROR, true);
				}
			}
		}

		return isOK;
	}

	/**
	 * Retrieves the isSorted flags from the input files in order to
	 * verify if all the input files are indeed sorted.
	 * @return - true if the input files are sorted; false otherwise
	 */
	private boolean inputSorted(){

		return inputSet.getPatientsFile().isSorted() &&
				(inputSet.gotEvents ? inputSet.getEventsFile().isSorted() : true) &&
						(inputSet.gotPrescriptions ? inputSet.getPrescriptionsFile().isSorted() : true) &&
								(inputSet.gotMeasurements ? inputSet.getMeasurementsFile().isSorted() : true);
	}

	/**
	 * Performs attribute checking on each one of the input files
	 * and outputs eventual errors.
	 * @throws Exception if the file could not be read
	 */
	private void checkInputFiles() throws Exception{
		MemoryBenchmark.Automated.putMarker("Checking input");
		inputErrors = new ArrayList<List<String>>();
		for (short i = 0; i< inputSet.getSelectedFiles().size(); i++){
			//assign each file to its checker and launch the checking
			inputErrors.add(i,null);
			InputFile file = inputSet.getSelectedFiles().get(i);
			if (file != null){
				List<String> errors = new ArrayList<String>();
				switch (file.getType()){
				case (DataDefinition.PATIENTS_FILE):
					checkPatients(file, errors);
				break;
				case (DataDefinition.EVENTS_FILE):
					checkEvents(file, errors);
				break;
				case (DataDefinition.PRESCRIPTIONS_FILE):
					checkPrescriptions(file, errors);
				break;
				case (DataDefinition.MEASUREMENTS_FILE):
					checkMeasurements(file, errors);
				break;
				}
			}
		}

		//set the flag for input errors
		noInputErrors = true;
		for (int i = 0; i < inputErrors.size(); i++)
			noInputErrors = ((inputErrors.get(i) == null ||
			inputErrors.get(i).isEmpty()) ||
			inputErrors.size() == 0) && noInputErrors;
	}

	/**
	 * Checks the patients input file for integrity errors.
	 * If errors are found, they are added to the existing list.
	 * @param file - the patients input file
	 * @param errors - the list of errors
	 * @throws Exception - if input file cannot be open or read from
	 */
	private void checkPatients(InputFile file, List<String> errors) throws Exception{
		if (Jerboa.getWorkFlow().getNeededInputFiles().get(file.getType())){
			PatientsFileChecker pfc = new PatientsFileChecker(inputSet.getPatientsFile());
			pfc.scan("patient");
			errors = pfc.getErrorMessages();
			if (errors != null && errors.size() > 0){
				errors.add(0,"Line    Error     --  "
						+Arrays.toString(file.getHeader()).replace("[",  "").replace("]", ""));
				inputErrors.set(InputFileSet.PATIENTS_FILE_INDEX, errors);
				InputFileUtilities.outputErrors(FilePaths.ERROR_LOG_PATIENTS,
						"Patients file "+ file.getName(), inputErrors.get(InputFileSet.PATIENTS_FILE_INDEX));
			}
		}else if (!PropertiesManager.isInDebugMode()){
			Logging.add("No patients file", Logging.ERROR);
			if (!Jerboa.inConsoleMode)
				new ErrorHandler("Please select at least a population file");
			Jerboa.stop(true);
		}
	}

	/**
	 * Checks the events input file for integrity errors.
	 * @param file - the events input file
	 * @param errors - the list of errors
	 * @throws Exception - if input file cannot be open or read from
	 */
	private void checkEvents(InputFile file, List<String> errors) throws Exception{
		if (Jerboa.getWorkFlow().getNeededInputFiles().get(file.getType())){
			EventsFileChecker efc = new EventsFileChecker(inputSet.getEventsFile());
			efc.scan("event");
			errors = efc.getErrorMessages();
			if (errors != null && errors.size() > 0){
				errors.add(0,"Line    Error     --  "
						+Arrays.toString(file.getHeader()).replace("[",  "").replace("]", ""));
				inputErrors.set(InputFileSet.EVENTS_FILE_INDEX,errors);
				InputFileUtilities.outputErrors(FilePaths.ERROR_LOG_EVENTS,
						"Events file "+ file.getName(), inputErrors.get(InputFileSet.EVENTS_FILE_INDEX));
			}
		}
	}

	/**
	 * Checks the prescriptions input file for integrity errors.
	 * @param file - the prescriptions input file
	 * @param errors - the list of errors
	 * @throws Exception - if input file cannot be open or read from
	 */
	private void checkPrescriptions(InputFile file, List<String> errors) throws Exception{
		if (Jerboa.getWorkFlow().getNeededInputFiles().get(file.getType())){
			PrescriptionsFileChecker prfc = new PrescriptionsFileChecker(inputSet.getPrescriptionsFile());
			prfc.scan("prescription");
			errors = prfc.getErrorMessages();
			if (errors != null && errors.size() > 0){
				errors.add(0,"Line    Error     --  "
						+Arrays.toString(file.getHeader()).replace("[",  "").replace("]", ""));
				inputErrors.set(InputFileSet.PRESCRIPTIONS_FILE_INDEX, errors);
				InputFileUtilities.outputErrors(FilePaths.ERROR_LOG_PRESCRIPTIONS,
						"Prescriptions file "+ file.getName(), inputErrors.get(InputFileSet.PRESCRIPTIONS_FILE_INDEX));
			}
		}
	}

	/**
	 * Checks the measurement input file for integrity errors.
	 * @param file - the measurements input file
	 * @param errors - the list of errors
	 * @throws Exception - if input file cannot be open or read from
	 */
	private void checkMeasurements(InputFile file, List<String> errors) throws Exception{
		if (Jerboa.getWorkFlow().getNeededInputFiles().get(file.getType())){
			MeasurementsFileChecker mfc = new MeasurementsFileChecker(inputSet.getMeasurementsFile());
			mfc.scan("measurement");
			errors = mfc.getErrorMessages();
			if (errors != null && errors.size() > 0){
				errors.add(0,"Line    Error     --  "
						+Arrays.toString(file.getHeader()).replace("[",  "").replace("]", ""));
				inputErrors.set(InputFileSet.MEASUREMENTS_FILE_INDEX,errors);
				InputFileUtilities.outputErrors(FilePaths.ERROR_LOG_MEASUREMENTS,
						"Measurements file "+file.getName(), inputErrors.get(InputFileSet.MEASUREMENTS_FILE_INDEX));
			}
			if (mfc.getEmptyValuesCount() > 0) {
				Logging.add("\t\t" + mfc.getEmptyValuesCount() + " measurements have an empty value", Logging.HINT);
			}
		}
	}

	/**
	 * Divides the input files into smaller subsets that are easier to manipulate.
	 * @param splitter - the splitter object doing the actual input file splitting
	 * @throws IOException - if an input file cannot be opened
	 */
	public void splitInputFiles(InputFileSplitter splitter) throws IOException{

		for (short i = 0; i< inputSet.getSelectedFiles().size(); i++){
			//assign each file to its checker and launch the checking
			if (inputSet.getSelectedFiles().get(i) != null &&
					Jerboa.getWorkFlow().getNeededInputFiles().get(inputSet.getSelectedFiles().get(i).getType())){
				InputFile file = inputSet.getSelectedFiles().get(i);
				if (file != null){
					splitter.split(file);
				}
			}
		}
	}

	/**
	 * Let the user know the API is working.
	 */
	public void busy(){
		Logging.addWithTimeStamp("Jerboa run started");
	}

	/**
	 * Let the user know the API has finished processing.
	 */
	public void done(){
		Logging.addWithTimeStamp("Done.");
	}

	//GETTERS AND SETTERS
	/**
	 * Sets the working folder of the current run.
	 * @param folder - the path to the folder chosen as working space
	 */
	public static void setWorkingFolder(String folder){
		if (folder != null)
			workingFolder = folder.endsWith("/") ? folder : folder+"/";
	}

	public List<List<String>> getInputErrorList() {
		return inputErrors;
	}

	public InputFileSet getInputSet() {
		return inputSet;
	}

	public List<String> getPatientErrors() {
		return patientErrors;
	}

}

