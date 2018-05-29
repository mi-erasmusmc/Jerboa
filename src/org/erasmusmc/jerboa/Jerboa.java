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
 * $Rev:: 4684              $:  Revision of last commit                                   *
 * $Author:: root           $:  Author of last commit                                     *
 * $Date:: 2013-10-28 17:08#$:  Date and time (CET) of last commit						  *
 ******************************************************************************************/

package org.erasmusmc.jerboa;

import org.erasmusmc.jerboa.engine.InputFileSet;
import org.erasmusmc.jerboa.engine.Processing;
import org.erasmusmc.jerboa.engine.ScriptParser;
import org.erasmusmc.jerboa.engine.WorkFlow;
import org.erasmusmc.jerboa.gui.JerboaGUI;
import org.erasmusmc.jerboa.utilities.Logging;
import org.erasmusmc.jerboa.utilities.OutputManager;
import org.erasmusmc.jerboa.utilities.ResultSet;
import org.erasmusmc.jerboa.cli.JerboaCLI;
import org.erasmusmc.jerboa.config.PropertiesManager;


/**
 * Main class of the API. Launches either in CLI or GUI.
 *
 * @author MG
 *
 */
public class Jerboa{

	//the application threads
	private static Processing processing;
	private static WorkFlow workFlow;

	//input related
	private static ScriptParser scriptParser;
	private static InputFileSet inputFileSet;

	//output related
	private static OutputManager outputManager;
	private static ResultSet resultSet;

	//the application properties (e.g., workspaces, parameter settings, etc.)
	private static PropertiesManager propertiesManager;

	//flags
	public static boolean inConsoleMode;
	public static boolean isVersionCompatible;
	public static boolean isInDebugMode;
	public static boolean hasDebugFolder;
	public static boolean intendedStop;
	public static boolean errorOccurred;
	public static boolean unitTest = false;


	/*-------------------MAIN-------------------*/

	/**
	 * The main method for the API. If arguments are present, the CLI
	 * version is instantiated; GUI version otherwise.
	 * @param args - the parameter settings for CLI.
	 */
	public static void main(String[] args) {

		inConsoleMode = args != null && args.length > 0;
		propertiesManager = new PropertiesManager();
		outputManager = new OutputManager();
		resultSet = new ResultSet();

		isInDebugMode = PropertiesManager.isInDebugMode();
		if (inConsoleMode){
			new JerboaCLI(args);
		}else{
			if (PropertiesManager.isFirstRun()){
				try {
					Thread.sleep(1500);
				} catch(InterruptedException ex) {
					Thread.currentThread().interrupt();
				}
			}
			new JerboaGUI();
		}
	}

	/*----------------END OF MAIN----------------*/

	/**
	 * Tries to stop the processing thread.
	 * Deprecated functionality might end up
	 * in a lower level exception.
	 * No decent alternative available.
	 */
	@SuppressWarnings("deprecation")
	public static void stop(){
		intendedStop = true;
		if (!Jerboa.inConsoleMode)
			JerboaGUI.stop();
		else
			System.out.println("The application reached an intended stop.");

		try{
			if (workFlow != null){
				workFlow.interrupt();
				workFlow.stop();
			}
			if (processing != null){
				processing.interrupt();
				processing.stop();
			}
		}catch(Throwable e){
			Logging.add("Threads did not finish ordinarily.", Logging.HINT, true);
			Logging.outputStackTrace(e);
		}
	}

	public static void restart(){
		Jerboa.stop();
	}

	/**
	 * Tries to stop the processing thread and exit with an
	 * error status, if the application runs in console mode.
	 * No decent alternative available instead of deprecated thread method.
	 * @param withError: true if the application should exit with an error status.
	 */
	public static void stop(boolean withError){
		Jerboa.stop();
		//exit with error status for CLI
		if (Jerboa.inConsoleMode){
			System.out.println("The application reached an intended stop.");
			System.exit(1);
		}
	}

	//GETTERS AND SETTERS
	public static Processing getRunThread() {
		return processing;
	}

	public static void setRunThread(Processing processing) {
		Jerboa.processing = processing;
	}

	public static WorkFlow getWorkFlow() {
		return workFlow;
	}

	public static void setWorkFlow(WorkFlow workFlow) {
		Jerboa.workFlow = workFlow;
	}

	public static ScriptParser getScriptParser() {
		return scriptParser;
	}

	public static void setScriptParser(ScriptParser scriptParser) {
		Jerboa.scriptParser = scriptParser;
	}

	public static PropertiesManager getPropertiesManager() {
		return propertiesManager;
	}

	public static void setPropertiesManager(PropertiesManager propertiesManager) {
		Jerboa.propertiesManager = propertiesManager;
	}

	public static OutputManager getOutputManager() {
		return outputManager;
	}

	public static ResultSet getResultSet() {
		return resultSet;
	}

	public static void setOutputManager(OutputManager outputManager) {
		Jerboa.outputManager = outputManager;
	}

	public static InputFileSet getInputFileSet() {
		return inputFileSet;
	}

	public static void setInputFileSet(InputFileSet inputFileSet) {
		Jerboa.inputFileSet = inputFileSet;
	}

	public static void isIntendedStop() {
		Jerboa.intendedStop = true;
	}

	public static void notIntendedStop() {
		Jerboa.intendedStop = false;
	}

	public static boolean hasIntendedStop() {
		return Jerboa.intendedStop;
	}

	public static void errorOccurred(){
		Jerboa.errorOccurred = true;
	}
}
