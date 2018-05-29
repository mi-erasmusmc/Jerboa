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
 * $Rev:: 4437              $:  Revision of last commit                                   *
 * $Author:: root           $:  Author of last commit                                     *
 * $Date:: 					$:  Date and time (CET) of last commit						  *
 ******************************************************************************************/

package org.erasmusmc.jerboa.engine;

import java.util.ArrayList;
import java.util.BitSet;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.commons.io.FilenameUtils;
import org.erasmusmc.jerboa.Jerboa;
import org.erasmusmc.jerboa.config.FilePaths;
import org.erasmusmc.jerboa.config.Parameters;
import org.erasmusmc.jerboa.dataClasses.Patient;
import org.erasmusmc.jerboa.engine.ScriptParser.Settings;
import org.erasmusmc.jerboa.utilities.Logging;
import org.erasmusmc.jerboa.utilities.PatientUtilities;
import org.erasmusmc.jerboa.utilities.Progress;
import org.erasmusmc.jerboa.utilities.TimeUtilities;
import org.erasmusmc.jerboa.utilities.Timer;
import org.erasmusmc.jerboa.utilities.stats.Stats;

/**
 * Generic class for the API modules. This class should be extended by any module developed for the software.
 *
 * @author MG
 *
 */
public abstract class Module extends Worker{

	//module and modifier settings
	private Settings moduleParameters;

	private List<Patient> patients;

	//flag if post processing is needed
	private boolean postProcessingNeeded = false;

	//flag if patients should be shuffled
	private boolean shufflePatients = false;

	//module modifiers
	protected boolean noModifiers;
	private List<Modifier> modifiers;

	/**
	 * Determines if a resultSet should be created
	 */
	public boolean resultSetActive = false;

	/**
	 * Defines the delimiter used in the ResultSet (Default = ",")
	 */
	public String delimiterResultSet = ",";

	/**
	 * Defines the missing value used in the ResultSet (Default = "")
	 */
	public String missingValueResultSet = "";

	/**
	 * Should the result set only be created for patients in the cohort?
	 */
	public boolean resultSetOnlyInCohort = true;


	//CONSTRUCTOR
	public Module(){
		super();
		modifiers = new ArrayList<Modifier>();
		patients = new ArrayList<Patient>();
	}

	/**
	 * Let the module do its task.
	 */
	public void runModule(){

		//start the module
		if (isActive()){
			if ((hasSettingsOK() && (noModifiers || initModifiers())) &&
					init() && checkParameters()){
				if (Jerboa.isInDebugMode)
					showParameterSettings();
				processPatients();
			}else{
				Logging.add("Unable to initialize module "+
					this.title, Logging.ERROR);
				Jerboa.stop(true);
			}
		}else{
			finishedSuccessfully = true;
			Logging.addNewLine();
			Logging.add("Note: the module "+this.getTitle()+" is not active", Logging.HINT);
			Logging.addNewLine();
		}
	}

	/**
	 * Reads through all patients object files created during the current run of the software
	 * and gathers descriptive statistics of the input data in order to generate a summary table.
	 */
	private void processPatients() {
		inPostProcessing = false;

		//feed-back
		timer = new Timer();
		progress = new Progress();

		PatientUtilities pu = new PatientUtilities(FilePaths.PATIENTS_PATH);
		pu.withProgress = false;

		//start counter
		timer.start();
		progress.init(Stats.nbPatients, title);

		Logging.addWithTimeStamp(title+" started");

		Jerboa.getResultSet().setDelimiter(delimiterResultSet);
		Jerboa.getResultSet().setMissingValue(missingValueResultSet);
		Jerboa.getResultSet().setOnlyInCohort(resultSetOnlyInCohort);

		//process all patients
		int anonymizedPatientID = 0;
		Map<String, Integer> anonymizedPatientIDs = new HashMap<String, Integer>();
		for (String file : pu.patientFiles){
			patients = pu.loadPatientsFromFile(file, withEvents(),
					withPrescriptions(), withMeasurements());
			while (patients.size() > 0){
				//apply modifiers if necessary and process patient
				Patient p = patients.get(0);
				anonymizedPatientIDs.put(p.getPatientID(), anonymizedPatientID);
				p.setAnonymizedPatientId(anonymizedPatientID);
				process(noModifiers ? p : applyModifiers(p, inPostProcessing));
				progress.update();
				patients.remove(0);
				anonymizedPatientID++;
			}
		}

		//make sure the progress bar is closed
		progress.close();

		if (postProcessingNeeded) {
			inPostProcessing = true;

			progress.init(Stats.nbPatients, title+" post processing");

			Logging.addWithTimeStamp(title+" post processing started");

			//shuffle the patient files when needed
			if (shufflePatients) {
				Collections.shuffle(pu.patientFiles);
			}

			//post process all patients
			for (String file : pu.patientFiles){
				patients = pu.loadPatientsFromFile(file, withEvents(),
						withPrescriptions(), withMeasurements());

				//shuffle the patients when needed
				if (shufflePatients) {
					Collections.shuffle(patients);
				}

				while (patients.size() > 0){
					Patient p = patients.get(0);
					p.setAnonymizedPatientId(anonymizedPatientIDs.get(p.getPatientID()));
					postProcess(noModifiers ? p : applyModifiers(p, inPostProcessing));
					progress.update();
					patients.remove(0);
				}
			}

			//make sure the progress bar is closed
			progress.close();
		}

		//output
		outputModifierResults();
		outputWorkerResults();
		flushRemainingData();

		//make graphs
		if (isCreateGraphs() && !Jerboa.inConsoleMode)
			displayGraphs();

		//display execution timers
		timer.stop();
		timer.displayTotal(title+" done in: ");

		finish();
		clearMemory();

	}

	/**
	 * Method to be overridden when post processing is needed.
	 * @param patient - the patient to process
	 * @return - the patient after post processing
	 */
	public Patient postProcess(Patient patient) {
		return patient;
	}

	/**
	 * Calls the initialization method for each one of the modifiers
	 * required by this module, if any and if active. It also checks
	 * if the public parameter settings are still legal after the initialization.
	 */
	private boolean initModifiers(){
		boolean initOK = true;
		if (modifiers != null && modifiers.size() > 0) {
			for (Modifier f : modifiers) {
				if (f.isActive()){
					if (!f.init()) {
						initOK = false;
						Logging.addNewLine();
						Logging.add("Note: the modifier "+f.getTitle()+" from the module "+
								this.getTitle()+" could not be initialized." +
										" Check the modifier parameters.", Logging.ERROR);
						break;
					}
					initOK &= f.checkParameters();
				}else{
					f.finishedSuccessfully = true;
					Logging.addNewLine();
					Logging.add("Note: the modifier "+f.getTitle()+" from the module "+
							this.getTitle()+" is not active", Logging.HINT);
					Logging.addNewLine();
				}
			}
		}

		return initOK;
	}

	/**
	 * Will apply all the necessary modifying steps on patient.
	 * The order of the modifiers is the same as the order in the script file.
	 * @param patient - the patient on which the modifiers are to be applied
	 * @param inPostProcessing - flag specifying if we are in the post processing.
	 * @return - the patient after being processed by the modifiers.
	 */
	private Patient applyModifiers(Patient patient, boolean inPostProcessing){
		if (modifiers != null && modifiers.size() > 0)
			for (Modifier f : modifiers) {
				f.inPostProcessing = inPostProcessing;
				if (f.isActive())
					patient = f.process(patient);
			}
		return patient;
	}

	/**
	 * Writes to file the rest of information that is present in the output buffer.
	 * This includes all intermediate files created during modifying, if the flags are set accordingly.
	 */
	@Override
	public void flushRemainingData(){
		for (Modifier f : modifiers)
			if (f.isActive())
				f.flushRemainingData();
	}

	/**
	 * Writes to file the results (if any) of the modifiers. The output can
	 * be intermediate calculations, statistics or whatever the content of
	 * the outputResults() method dictates as output. Also sets the flag that
	 * the modifier has finished successfully, once it reached to output its results.
	 */
	public void outputModifierResults(){
		if (modifiers != null && modifiers.size() > 0)
			for (Modifier f : modifiers)
				if (f.isActive()){
					f.outputResults();
					f.setFinishedSuccessfully(true);
				}
	}

	/**
	 * Will print to the console the parameter settings of this module
	 * and for each of its attached modifiers (if any). Mainly used
	 * for debug purposes.
	 */
	private void showParameterSettings(){
		displayParameterSettings();
		if (modifiers != null && modifiers.size() > 0)
			for (Modifier f : modifiers)
				f.displayParameterSettings();
	}

	/**
	 * Will write to file the result set of this module (if any).
	 * Result sets are created if the flags are set accordingly in the script.
	 * @return - true if the result set was successfully output; false otherwise
	 */
	protected boolean outputResultSet(){
		String baseName = FilePaths.INTERMEDIATE_PATH+FilenameUtils.getBaseName(outputFileName);
		if  (Jerboa.getResultSet().outputToFile(baseName+"_resultSet.csv"))
			return true;
		else
			return false;
	}

	/**
	 * Tidy up a bit.
	 */
	@Override
	public void clearMemory(){
		super.clearMemory();
		patients = null;
		modifiers = null;
		moduleParameters = null;

		progress = null;
		timer = null;
	}

	//GETTERS AND SETTERS
	/**
	 * Creates the name of the intermediate result and final result
	 * output file for this module and its modifiers.
	 */
	@Override
	public void setOutputFileNames() {

		if (!Jerboa.unitTest) {
			//for this module
			String moduleName = (outputFileName != null && !outputFileName.equals("")) ? this.outputFileName : this.title;
			String extension = ".csv";
			if (moduleName.contains(".")){
				extension = "." + FilenameUtils.getExtension(moduleName);
				moduleName = FilenameUtils.getName(moduleName);
			}

			this.outputFileName = FilePaths.WORKFLOW_PATH + this.title + "/" + Parameters.DATABASE_NAME + "_" + moduleName +
					"_" + TimeUtilities.TIME_STAMP + "_" + Parameters.VERSION+extension;
			this.intermediateFileName = FilePaths.INTERMEDIATE_PATH + this.title + "/" + Parameters.DATABASE_NAME + "_" + moduleName +
					"_" + TimeUtilities.TIME_STAMP + "_" + Parameters.VERSION + extension;
			this.resultSetFileName = intermediateFileName;
		}
		else {
			this.outputFileName = "Output";
			this.intermediateFileName = "Intermediate";
			this.resultSetFileName = intermediateFileName;
		}

		//for its modifiers
		if (modifiers != null && modifiers.size() > 0)
			for (Modifier f : modifiers)
				f.setOutputFileNames();
	}

	/**
	 * Sets the resultSet to be active or not.
	 */
	public void setResultSet(){
		Jerboa.getResultSet().setActive(resultSetActive);
	}

	/**
	 * Sets flag if post processing is needed.
	 * @param needed - true if the patient requires post processing; false otherwise
	 */
	public void setPostProcessingNeeded(boolean needed) {
		this.postProcessingNeeded = needed;
	}

	/**
	 * Sets flag if patients should be shuffled.
	 * @param shuffle - true if the order of the patients should be randomized; false otherwise
	 */
	public void setShufflePatients(boolean shuffle) {
		this.shufflePatients = shuffle;
	}

	/**
	 * Creates a "list" of all the needed files for the modifiers attached to this module
	 * and combines it with the ones needed by this module.
	 * @return - a bit set representation of all needed files for this module
	 */
	public BitSet getNeededFilesIncludingModifiers(){
		this.neededFiles = getNeededFiles();
		if (modifiers != null && !modifiers.isEmpty())
			for (Modifier f : modifiers)
				if (f.isActive())
					neededFiles.or(f.getNeededFiles());
		return neededFiles;
	}

	/**
	 * Creates a map of all the extended columns needed by the various modifiers and
	 * combines it with the ones needed by this module for each input file type.
	 * @return - a map of the needed extended columns per input file type
	 */
	public HashMap<Integer, Set<String>> getNeededExtendedColumnsIncludingModifiers(){
		neededExtendedColumns = getNeededExtendedColumns();
		if (modifiers != null && !modifiers.isEmpty())
			for (Modifier f : modifiers)
				if (f.isActive())
					for (int fileType : f.getNeededExtendedColumns().keySet()){
						if (neededExtendedColumns.get(fileType) == null)
							neededExtendedColumns.put(fileType, new HashSet<String>());
						neededExtendedColumns.get(fileType).addAll(f.getNeededExtendedColumns(fileType));
					}
		return neededExtendedColumns;
	}

	/**
	 * Creates a Map of all numeric colums needed by the various modifiers and
	 * combines it with the ones needed by this module for each input file type.
	 * @return - a map of the needed extended columns per input file type
	 */
	public HashMap<Integer, HashMap<String, Set<String>>> getNeededNumericColumnsIncludingModifiers() {
		neededNumericColumns = getNeededNumericColumns();
		if (modifiers != null && !modifiers.isEmpty())
			for (Modifier f : modifiers)
				if (f.isActive())
					for (int fileType : f.getNeededNumericColumns().keySet()){
						if (neededNumericColumns.get(fileType) == null)
							neededNumericColumns.put(fileType, new HashMap<String, Set<String>>());
						HashMap<String, Set<String>> modifierNumericColums = f.getNeededNumericColumns(fileType);
						if (modifierNumericColums != null) {
							for (String type : modifierNumericColums.keySet()) {
								Set<String> typeNumericColumns = neededNumericColumns.get(fileType).get(type);
								if (typeNumericColumns == null) {
									typeNumericColumns = new HashSet<String>();
									neededNumericColumns.get(fileType).put(type, typeNumericColumns);
								}
								typeNumericColumns.addAll(modifierNumericColums.get(type));
							}
						}
					}
		return neededNumericColumns;
	}

	/**
	 * Instantiates and sets the parameters for the required modifiers.
	 * @param modifierParameters - the parameter settings for the modifiers
	 */
	public void setModifierParameters(List<Settings> modifierParameters) {
		if (modifierParameters != null && !modifierParameters.isEmpty()){
			Modifier modifier = null;
			//create instances
			int nbModifier = 0;
			for (Settings modifierSettings : modifierParameters){
				try {

					//instantiate and set title as moduleTitle_modifierTitle
					modifier = (Modifier) Class.forName(FilePaths.MODIFIERS_PACKAGE + modifierSettings.type).newInstance();
					modifier.setTitle(this.title+"_"+modifierSettings.name);
					modifier.setParentModule(this.title);

				} catch (InstantiationException | IllegalAccessException | ClassNotFoundException | NoClassDefFoundError e) {
					Logging.add("Could not instantiate modifier " + modifierSettings.type+" for module "+this.title+". No such class.", Logging.ERROR);
					modifier = null;
					settingsOK = false;
				}

				//set the intermediate output and graph flags as it is set for the module
				if (modifier != null){
					modifier.intermediateFiles = this.intermediateFiles;
					modifier.intermediateStats = this.intermediateStats;
					modifier.createGraphs = this.createGraphs;

					//set modifier parameters
					modifier.setModifierParameters(modifierSettings);

					//check if there are unspecified parameters
					if (modifier.unspecifiedParameters != null && modifier.unspecifiedParameters.size() > 0){
						if (unspecifiedParameters == null)
							unspecifiedParameters = new ArrayList<String>();
						unspecifiedParameters.addAll(modifier.unspecifiedParameters);
					}

					//add it to the list respecting the order in the script
					modifiers.add(nbModifier++,modifier);

					settingsOK = settingsOK && modifier.hasSettingsOK();
				}
			}
		}
	}

	public Settings getModuleParameters() {
		return this.moduleParameters;
	}

	/**
	 * Will set all the parameters for this module.
	 * @param moduleParameters - the parameters of the module
	 */
	public void setModuleParameters(Settings moduleParameters) {
		this.moduleParameters = moduleParameters;
		if (Jerboa.isInDebugMode)
			printParameterMapping(moduleParameters.parameters);
		setParameters(moduleParameters.parameters);
	}

	/**
	 * Sets a flag for the validity of the parameter settings for
	 * all the modifiers required by this module.
	 * @return - true if all the parameter settings of the modifiers are valid; false otherwise
	 */
	public boolean hasSettingsOK(){
		if (this.modifiers != null && !this.modifiers.isEmpty())
			for (Modifier f : modifiers)
				if (f.isActive() && !f.hasSettingsOK())
					return false;

		return settingsOK && true;
	}

	/**
	 * Sets the successful finish flag for this module and
	 * checks if all the modifiers assigned to this module
	 * have finished successfully. If a modifier did not finish
	 * successfully, the user is warned.
	 */
	private void finish(){
		finishedSuccessfully = true;
		for (Modifier f : modifiers){
			finishedSuccessfully &= f.hasFinishedSuccessfully();
			if (!f.hasFinishedSuccessfully())
				Logging.add("The modifier "+f.getTitle()+" did not finish successfully.", Logging.ERROR);
		}
	}

	/**
	 * Returns the modifier corresponding to modifierClass name if present in the
	 * list of modifiers for this module. If more than one modifier of the same class
	 * exists in the list of modifiers for this module, the first occurrence is returned.
	 * @param modifierClass - the class of the modifier of interest
	 * @return - the modifier object corresponding to modifierClass
	 */
	public Modifier getModifier(Class<?> modifierClass){
		if (modifiers != null && modifiers.size() > 0)
			for (Modifier f : modifiers)
					if (f.getClass().equals(modifierClass))
						return f;
		return null;
	}

	/**
	 * Returns the modifier having its name the same as title if present in the
	 * list of modifiers for this module. No duplicate names are allowed.
	 * @param title - the name of the modifier of interest
	 * @return - the modifier object if found in the list
	 */
	public Modifier getModifier(String title){
		if (modifiers != null && modifiers.size() > 0)
			for (Modifier f : modifiers)
					if (f.getTitle().equals(title))
						return f;
		return null;
	}

	public boolean hasNoModifiers() {
		return noModifiers;
	}

	public void setNoModifiers(boolean noModifiers) {
		this.noModifiers = noModifiers;
	}

	public List<Modifier> getModifiers() {
		return modifiers;
	}

	public void setModifiers(List<Modifier> modifiers) {
		this.modifiers = modifiers;
	}

	private boolean withEvents(){
		return neededFiles.get(1);
	}

	private boolean withPrescriptions(){
		return neededFiles.get(2);
	}

	private boolean withMeasurements(){
		return neededFiles.get(3);
	}

}
