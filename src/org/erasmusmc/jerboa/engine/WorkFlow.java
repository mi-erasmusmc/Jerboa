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
 * $Rev:: 4608              $:  Revision of last commit                                   *
 * $Author:: root           $:  Author of last commit                                     *
 * $Date:: 					$:  Date and time (CET) of last commit						  *
 ******************************************************************************************/

package org.erasmusmc.jerboa.engine;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.BitSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

import org.apache.commons.io.FilenameUtils;
import org.erasmusmc.jerboa.Jerboa;
import org.erasmusmc.jerboa.config.DataDefinition;
import org.erasmusmc.jerboa.config.FilePaths;
import org.erasmusmc.jerboa.config.PropertiesManager;
import org.erasmusmc.jerboa.engine.ScriptParser.ParsedSettings;
import org.erasmusmc.jerboa.engine.ScriptParser.Settings;
import org.erasmusmc.jerboa.gui.JerboaGUI;
import org.erasmusmc.jerboa.utilities.ErrorHandler;
import org.erasmusmc.jerboa.utilities.FileUtilities;
import org.erasmusmc.jerboa.utilities.InputFileUtilities;
import org.erasmusmc.jerboa.utilities.Logging;
import org.erasmusmc.jerboa.utilities.MemoryBenchmark;
import org.erasmusmc.jerboa.utilities.Pack;
import org.erasmusmc.jerboa.utilities.RunSQL;

/**
 * This extension of a thread class starts the work flow by parsing the script file
 * and instantiating the necessary workers with their respective parameter settings.
 * The main method of this thread will launch the worker(s) defined in the script file.
 * In case of multiple workers needed for a run, they are run in the same order as declared in the script.
 *
 * @author MG
 *
 */
public class WorkFlow extends Thread{

	//needed data
	public BitSet neededInputFiles = new BitSet();
	public HashMap<Integer, Set<String>> neededExtendedColumns = new HashMap<Integer, Set<String>>();
	public HashMap<Integer, HashMap<String, Set<String>>> neededNumericColumns = new HashMap<Integer, HashMap<String, Set<String>>>();

	private List<String> unspecifiedParameters;

	//the list of modules in the current work flow
	private List<Module> modules;
	//data extraction (if needed)
	private RunSQL dataExtraction;

	//result packing (if needed)
	private Pack packResults;

	//flag for the validity of the worker settings
	public boolean scriptSettingsOK = true;

	//true if there is no successfully configured module
	private boolean noValidModule;

	//set to false if one of the modules failed to initialize or run
	private boolean ranModulesSuccessfully = true;

	//CONSTRUCTORS
	/**
	 * Basic constructor.
	 */
	public WorkFlow(){
		super();
	}

	/**
	 * Constructor receiving the script settings parsed.
	 * @param sp - the parameter settings from the script file
	 */
	public WorkFlow(ScriptParser sp){
		super();
		if (sp != null && sp.isScriptValid() && sp.getParsedSettings() != null)
			configureModules(sp);
		else
			Jerboa.stop(true);
	}

	/**
	 * Distributes the parsed settings from the script to the modules used in the work flow
	 * and retrieves the needed input files based on the modules needed in the run.
	 * @param sp - the parsed settings
	 */
	public void configureModules(ScriptParser sp){

		modules = new ArrayList<Module>();
		ParsedSettings parsedSettings = sp.getParsedSettings();

		unspecifiedParameters = new ArrayList<String>();

		Module module = null;
		Iterator<List<Settings>> modifierIterator = parsedSettings.modifierSettings.iterator();

		//instantiate modules
		for (Settings moduleSettings : parsedSettings.moduleSettings){
			try {
				try {
					module = (Module)Class.forName(FilePaths.MODULES_PACKAGE + moduleSettings.type).newInstance();
				} catch (InstantiationException | IllegalAccessException | ClassNotFoundException | NoClassDefFoundError e) {
					scriptSettingsOK = false;
					ranModulesSuccessfully = false;
					if (!Jerboa.inConsoleMode)
						new ErrorHandler("Could not instantiate module " + moduleSettings.type+". No such class.");
					Logging.add("Could not instantiate module " + moduleSettings.type+". No such class.", Logging.ERROR);
				}

				if (module != null){
					module.setTitle(moduleSettings.name);

					//set parameters
					module.setModuleParameters(moduleSettings);
					List<Settings> modifierSettings = modifierIterator.hasNext() ? modifierIterator.next() : null;

					module.setNoModifiers(modifierSettings == null || modifierSettings.isEmpty());
					if (!module.hasNoModifiers())
						module.setModifierParameters(modifierSettings);

					if (module.unspecifiedParameters != null && module.unspecifiedParameters.size() > 0)
						unspecifiedParameters.addAll(module.unspecifiedParameters);

					//check if all settings were correct
					scriptSettingsOK = scriptSettingsOK && module.hasSettingsOK();

					//get the necessary files and extended columns
					setNeededFiles(module.getNeededFilesIncludingModifiers());
					setNeededExtendedColumns(module.getNeededExtendedColumnsIncludingModifiers());

					//get the necessary numeric columns
					setNeededNumericColumns(module.getNeededNumericColumnsIncludingModifiers());

					//add it to the list
					modules.add(module);
				}

			} catch (RuntimeException e){
				ranModulesSuccessfully = false;
				scriptSettingsOK = false;
				Logging.outputStackTrace(e, true);
				if (Jerboa.inConsoleMode)
					System.exit(1);
			}
		}

		//check if modules were found and assigned
		noValidModule = (modules == null || modules.size() ==0);
		if (noValidModule){
			if (!Jerboa.inConsoleMode)
				new ErrorHandler("No valid module found to be run"+
						(Jerboa.getScriptParser().isExtractionNeeded() ? ". Data will be extracted" : ""));
			Logging.add("No valid module found to be run"+
						(Jerboa.getScriptParser().isExtractionNeeded() ? ". Data will be extracted" : ""), Logging.ERROR);
			Jerboa.stop(true);
		}

		//check if there are unspecified parameters and put them in the log file
		printUnspecifiedParameters();

		//check if data extraction required and initialize
		initDataExtraction(parsedSettings);

		//check if result packing required and initialize
		initResultPacking(parsedSettings);
	}

	/**
	 * Method launching the thread.
	 * It launches modules sequentially in the order they are present in the script file.
	 */
	public void run(){

		try{
			FileUtilities.forceMkdir(new File(FilePaths.WORKFLOW_PATH));
		}catch (IOException e){
			Logging.add("Cannot create the results folder", Logging.ERROR);
		}

		if (!noValidModule){
			String moduleTitle = "";
			try{
				for (Module module : modules){
					if (!Jerboa.inConsoleMode)
						JerboaGUI.busy();
					moduleTitle = module.getTitle();
					module.setOutputFileNames();
					module.setResultSet();
					if (module.isActive())
						MemoryBenchmark.Automated.putMarker(moduleTitle);
					module.runModule();
					if (!module.hasFinishedSuccessfully()){
						ranModulesSuccessfully = false;
						Logging.add("The module "+moduleTitle+" or one of its modifiers did not finish successfully.", Logging.ERROR);
						if (!Jerboa.inConsoleMode)
							JerboaGUI.done();
						Jerboa.stop(true);
					}
					if (!module.outputResultSet())
						Logging.add("Unable to write resultset "+moduleTitle, Logging.ERROR);
					Jerboa.getResultSet().clear();
				}
			}catch(Throwable e){
				ranModulesSuccessfully = false;
				Logging.addNewLine();
				Logging.add("Workflow stopped on module "+moduleTitle+". Check View -> Log", Logging.ERROR);
				Logging.outputStackTrace(e);
				if (!Jerboa.inConsoleMode)
					JerboaGUI.done();
				Jerboa.stop(true);
			}finally{
				if (!Jerboa.inConsoleMode)
					JerboaGUI.showEndMessage();
			}
		}

		if (this.ranModulesSuccessfully){
			Logging.addNewLine();
		    Processing.overallTimer.stopAndDisplayWithTimeStamp("Workflow finished successfully in: ");
		    Logging.addNewLine();
		}

		if (!Jerboa.isInDebugMode && !Jerboa.getScriptParser().reusePatients())
			InputFileUtilities.deletePOF();

		if (!Jerboa.inConsoleMode)
			JerboaGUI.done();
		Jerboa.stop();
	}

	/**
	 * Initializes the data extraction object if corresponding settings
	 * were found in the script file.
	 * @param settings - the parsed settings from the script file
	 */
	private void initDataExtraction(ParsedSettings settings){
		if(settings.dataExtractionSettings != null &&
			settings.dataExtractionSettings.parameters != null &&
			!settings.dataExtractionSettings.parameters.isEmpty())
				dataExtraction = new RunSQL(settings.dataExtractionSettings);
	}

	/**
	 * Initializes the result packing if corresponding settings
	 * were found in the script file.
	 * @param settings - the parsed settings from the script file
	 */
	private void initResultPacking(ParsedSettings settings){
		if (settings.packSettings != null &&
				settings.packSettings != null &&
				!settings.packSettings.parameters.isEmpty())
			packResults = new Pack(settings.packSettings);
	}

	//GETTERS AND SETTERS
	/**
	 * Sets the flags for what input files are needed to run all workers
	 * declared in the script file.
	 * @param workerFiles - a binary representation of the input files
	 * needed to run a certain module
	 */
	public void setNeededFiles(BitSet workerFiles){
		neededInputFiles.or(workerFiles);
	}

	public BitSet getNeededInputFiles() {
		return neededInputFiles;
	}

	public HashMap<Integer, Set<String>> getNeededExtendedColumns() {
		return neededExtendedColumns;
	}

	/**
	 * Sets the flags for what extended data columns are needed for each input file based
	 * on the settings present in the script file.
	 * @param workerExtendedColumns - a list of BitSets containing a binary representation of the
	 * needed extended columns for each of the needed input files.
	 */
	public void setNeededExtendedColumns(HashMap<Integer, Set<String>> workerExtendedColumns){
		if (workerExtendedColumns != null){
			for (int key : workerExtendedColumns.keySet()){
				Set<String> fileExtendedColumns = neededExtendedColumns.get(key);
				if (fileExtendedColumns == null)
					fileExtendedColumns = new HashSet<String>(InputFileUtilities.getNumberOfExtenedColumns((byte)key));
				fileExtendedColumns.addAll(workerExtendedColumns.get(key));
				neededExtendedColumns.put(key, fileExtendedColumns);
			}
		}
	}

	public HashMap<Integer, HashMap<String, Set<String>>> getNeededNumericColumns() {
		return neededNumericColumns;
	}

	/**
	 * Sets the flags for what extended data columns are needed for each input file based
	 * on the settings present in the script file.
	 * @param workerNumericColumns - a map of numeric columns per type for each of the needed input files.
	 */
	public void setNeededNumericColumns(HashMap<Integer, HashMap<String, Set<String>>> workerNumericColumns){
		if (workerNumericColumns != null){
			for (int key : workerNumericColumns.keySet()){
				HashMap<String, Set<String>> fileNumericColumns = neededNumericColumns.get(key);
				if (fileNumericColumns == null) {
					fileNumericColumns = new HashMap<String, Set<String>>();
					neededNumericColumns.put(key, fileNumericColumns);
				}
				for (String type : workerNumericColumns.get(key).keySet()) {
					Set<String> typeNumericColumns = fileNumericColumns.get(type);
					if (typeNumericColumns == null) {
						typeNumericColumns = new HashSet<String>();
						fileNumericColumns.put(type, typeNumericColumns);
					}
					typeNumericColumns.addAll(workerNumericColumns.get(key).get(type));
				}
			}
		}
	}

	public boolean hasSettingsOk(){
		return scriptSettingsOK;
	}

	public boolean hasRanModulesSuccessfully() {
		return ranModulesSuccessfully;
	}

	public RunSQL getDataExtraction() {
		return dataExtraction;
	}

	public Pack getResultPacking() {
		return packResults;
	}

	public void setDataExtraction(RunSQL dataExtraction) {
		this.dataExtraction = dataExtraction;
	}

	public boolean noValidModule() {
		return noValidModule;
	}

	/**
	 * Prints to the console the needed input files for the run.
	 * @param providedFiles - the input files that are provided in
	 * the working space, under a bit set representation
	 */
	public void printNeededInputFiles(BitSet providedFiles){
		Logging.addNewLine();
		Logging.add("Needed input files:", Logging.HINT);
		Logging.add("-------------------------");
		Logging.add("Population file"+(providedFiles.get(DataDefinition.PATIENTS_FILE) ?
				(Jerboa.inConsoleMode ? " - provided" : " \u2713") : ""));
		if (neededInputFiles.get(DataDefinition.EVENTS_FILE))
			Logging.add("Events file"+(providedFiles.get(DataDefinition.EVENTS_FILE) ?
					(Jerboa.inConsoleMode ? " - provided" : " \u2713") : ""));
		if (neededInputFiles.get(DataDefinition.PRESCRIPTIONS_FILE))
			Logging.add("Prescriptions file"+(providedFiles.get(DataDefinition.PRESCRIPTIONS_FILE) ?
					(Jerboa.inConsoleMode ? " - provided" : " \u2713") : ""));
		if (neededInputFiles.get(DataDefinition.MEASUREMENTS_FILE))
			Logging.add("Measurement file"+(providedFiles.get(DataDefinition.MEASUREMENTS_FILE) ?
					(Jerboa.inConsoleMode ? " - provided" : " \u2713") : ""));
		Logging.addNewLine();

	}

	/**
	 * Will write to log file all mandatory parameters that were not specified in the script file.
	 */
	private void printUnspecifiedParameters(){
		if (unspecifiedParameters != null && unspecifiedParameters.size() > 0){
			Logging.add("Unspecified parameters", Logging.ERROR, true);
			Logging.add("----------------------", true);
			for (String parameter : unspecifiedParameters)
				Logging.add(parameter, true);
			Logging.add("", true);
			Logging.add("Not all module/modifier parameters were specified.\n" +
					"Check the application log file via View Menu -> Log\n");
			if (!Jerboa.inConsoleMode)
				new ErrorHandler("Not all the module/modifier parameters were specified. Check\n the log" +
					" file for more details. The application will not continue");
		}
	}

	/**
	 * Prints to the console the version of the script file (if any).
	 */
	public void printScriptVersion(){
		//print the script version
		if (Jerboa.getScriptParser() != null && !PropertiesManager.isInDebugMode()){
			String scriptVersion = Jerboa.getScriptParser().getScriptVersion();
			Logging.addWithTimeStamp(FilenameUtils.getName(Jerboa.getScriptParser().getScriptFile())+
					(scriptVersion != null && !scriptVersion.equals("") ? " version "+scriptVersion : "")+" started");
		}
	}

}
