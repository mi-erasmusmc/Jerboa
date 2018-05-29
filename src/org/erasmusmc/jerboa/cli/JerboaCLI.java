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
 * $Rev:: 4578              $:  Revision of last commit                                   *
 * $Author:: root           $:  Author of last commit                                     *
 * $Date:: 2013-10-02 16:27#$:  Date and time (CET) of last commit						  *
 ******************************************************************************************/

package org.erasmusmc.jerboa.cli;

import org.apache.commons.io.FilenameUtils;
import org.erasmusmc.jerboa.Jerboa;
import org.erasmusmc.jerboa.config.FilePaths;
import org.erasmusmc.jerboa.config.Parameters;
import org.erasmusmc.jerboa.config.PropertiesManager;
import org.erasmusmc.jerboa.engine.InputFileSet;
import org.erasmusmc.jerboa.engine.Processing;
import org.erasmusmc.jerboa.engine.ScriptParser;
import org.erasmusmc.jerboa.engine.WorkFlow;
import org.erasmusmc.jerboa.utilities.InputFileUtilities;
import org.erasmusmc.jerboa.utilities.Logging;
import org.erasmusmc.jerboa.utilities.TimeUtilities;

/**
 * This class configures and launches the command
 * line interface version of the application.
 *
 * @author MG
 *
 */
public class JerboaCLI {

	private InputFileSet inputSet;

	//paths
	private String workingFolder;
	private String scriptFile;

	/**
	 * Basic constructor.
	 * @param args - command line arguments
	 */
	public JerboaCLI(String[] args){

		//initialize
		parseArguments(args);
		initializeStructure();
		initializeWorkflow();
		TimeUtilities.refreshTimeStamp();
		Jerboa.setWorkFlow(new WorkFlow(Jerboa.getScriptParser()));

		//launch
		if (readyToRun()){
			Jerboa.setRunThread(new Processing(inputSet));
			Jerboa.getRunThread().start();
		}

	}

	/**
	 * Parses the arguments of the program.
	 * @param args - the CLI arguments
	 */
	private void parseArguments(String[] args){

		workingFolder = null;
		scriptFile = null;
		String database = null;

		for (String arg : args){
			arg = arg.trim();

			//check if user needs help
			if (isHelpNeeded(arg)){
				usage();
				System.exit(1);
			}

			//get working folder (if any)
			if ((arg.contains("/") || arg.contains("\\"))
					&& workingFolder == null){
				workingFolder = arg.replaceAll("\\\\", "/");
				continue;
			}

			//get script file
			if (arg.contains(".jsf") && scriptFile == null){
				scriptFile = arg;
				continue;
			}

			//get database name
			database = arg.toUpperCase();
		}

		//see if database name specified or use default value
		if (database == null || database.equals(""))
			database = Parameters.DATABASE_NAME;
		else
			Parameters.DATABASE_NAME = database;


		if (scriptFile == null){
			System.out.println("No script file specified." +
					" The application will not continue.");
			System.exit(1);
		}

		//check if workspace not specified, set the current one
		if (workingFolder == null){
			workingFolder = ".";
			System.out.println("No workspace specified." +
					" Current folder will be used.");
		}

		//check if the script file is in the working folder
		if (!isScriptInWorkingFolder()){
			System.out.println("Please specify a script file present in the working folder.");
			System.exit(1);
		}else{
			scriptFile = FilenameUtils.getName(scriptFile);
		}

	}

	/**
	 * Will set the parameters used across the application and
	 * create the folder structure for this run.
	 */
	@SuppressWarnings("resource")
	private void initializeStructure(){

		//set the working paths and create folders
		Processing.setWorkingFolder(workingFolder);
		FilePaths.updatePaths(workingFolder, PropertiesManager.getRunFolder());
		InputFileUtilities.createFolderStructure(Jerboa.getPropertiesManager());

		new Logging();

		//get the files in the working folder and initialize input dataset
		workingFolder = Processing.workingFolder;
		inputSet = new InputFileSet(workingFolder);
		inputSet.setScriptFile(workingFolder+scriptFile);
		Jerboa.setInputFileSet(inputSet);

		//check if valid dataset or exit with error code
		if (inputSet != null){
			if (inputSet.noInputFiles){
				Logging.add("No input files present in the working folder." +
						" The application will not continue", Logging.ERROR);
				System.exit(1);
				//parse the script file
			}else{
				if (inputSet.getScriptFile() != null)
					Jerboa.setScriptParser(new ScriptParser(inputSet.getScriptFile()));
			}
		}

		//check if the script is compatible with the software version
		Jerboa.isVersionCompatible = Jerboa.getScriptParser().checkRequiredVersion();
	}

	/**
	 * Initializes the work flow based on the selected script file
	 * and the required input files specified in the script file.
	 */
	private void initializeWorkflow(){

		if (Jerboa.isVersionCompatible){

			//display script details on console
			Jerboa.getScriptParser().displayMetaData();

			//instantiate needed modules and figure out needed files
			Jerboa.setWorkFlow(new WorkFlow(Jerboa.getScriptParser()));

			//remove input files that are not needed
			inputSet.removeUneededFiles(Jerboa.getWorkFlow().getNeededInputFiles());
		}else{
			Logging.add("Incompatible version of the script. " +
					"The application will not continue", Logging.ERROR);
			System.exit(1);
		}
	}

	/**
	 * Checks if the application is ready to run
	 * based on the version required by the script
	 * and the parameter settings from the script.
	 */
	private boolean readyToRun(){
		return Jerboa.isVersionCompatible &&
				Jerboa.getScriptParser().isScriptValid() &&
				Jerboa.getWorkFlow().hasSettingsOk();
	}

	/**
	 * Checks if the script file contains an absolute path and if that
	 * path is pointing towards the current working folder.
	 * @return - true if the script file is present in the working folder; false otherwise
	 */
	private boolean isScriptInWorkingFolder(){
		if (scriptFile.contains("/") || scriptFile.contains("\\")){
			scriptFile = scriptFile.replaceAll("\\\\", "/");
			return (scriptFile.contains(workingFolder) &&
					scriptFile.lastIndexOf("/") == ((workingFolder.endsWith("/") || workingFolder.endsWith("\\")) ?
							workingFolder.length()-1 : workingFolder.length()));
		}

		return true;
	}

	/**
	 * Check if the user needs help.
	 * @param arg - a program argument
	 * @return - true if the argument is a cry for help; false otherwise
	 */
	private boolean isHelpNeeded(String arg){
		return ((arg.equals("-help") || arg.contains("-help")) ||
				(arg.equals("-h") || arg.equals("/h")) ||
				(arg.equals("?h") || arg.contains("-help")));
	}

	/**
	 * Print on the screen information on how to run the software via the command line.
	 */
	private void usage(){
		System.out.println("usage: java - jar <JarFileName.jar> <working_folder>[optional] <script_file.jsf> <database_name>[optional]\r\n"+
			"If no working folder is specified, the current folder is used.\r\n The script file has to be present in the working folder.\r\n" +
			"If not database name is specified the \'DB\' string of characters will be used as default.\r\n Good luck!");
	}

}
