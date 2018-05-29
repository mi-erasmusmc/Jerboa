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
 * $Rev:: 4806              $:  Revision of last commit                                   *
 * $Author:: root           $:  Author of last commit                                     *
 * $Date:: 2013-11-18 17:15#$:  Date and time (CET) of last commit						  *
 ******************************************************************************************/

package org.erasmusmc.jerboa.config;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.net.URL;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Properties;

import org.erasmusmc.jerboa.Jerboa;
import org.erasmusmc.jerboa.utilities.ErrorHandler;
import org.erasmusmc.jerboa.utilities.FileUtilities;
import org.erasmusmc.jerboa.utilities.DateUtilities;

/**
 * This class contains all the methods necessary to manipulate the properties file of the application.
 *
 * @author MG
 *
 */
public class PropertiesManager {

	//the application properties
	public static HashMap<String, String> listProperties;

	//flags
	private static boolean inDebugMode;		//running in debug mode
	private static boolean firstRun;		//if true; GPL license will be displayed

	//special folder if the application runs in debug mode
	private static String debugFolder = "01-01-01";

	/**
	 * Basic constructor.
	 */
	public PropertiesManager() {
		listProperties = new HashMap<String, String>();
		ReadProperties();
		exportPropertiesForMaven();
	}

	/**
	 * Writes the enumerated properties to a file called
	 * jerboa.properties in the current directory.
	 * If any new property is to be added, this is the place.
	 **/
	public void WriteProperties(){

		try {
			Properties properties = new Properties();
			properties.setProperty("version", Parameters.VERSION);
			properties.setProperty("runIndex", "1");
			properties.setProperty("lastRun", DateUtilities.getCurrentDate());
			properties.setProperty("lastWorkspace", new File("./").getCanonicalPath().replaceAll("\\\\", "/"));
			properties.setProperty("lastDatabase", "");
			properties.setProperty("usedWorkspaces", new File("./").getCanonicalPath().replaceAll("\\\\", "/"));
			properties.setProperty("usedDatabases", Parameters.DATABASES);

			File file = new File(FilePaths.PROPERTIES_FILE);
			FileOutputStream fileOut = new FileOutputStream(file);
			properties.store(fileOut, "Jerboa Settings");
			fileOut.close();

			ReadProperties();

		}catch (IOException e) {
			stop("Unable to write properties file.");
		}

	}

	/**
	 * Reads the application properties from the file
	 * jerboa.properties present in the current directory.
	 */
	public void ReadProperties(){

		try {
			File file = new File(FilePaths.PROPERTIES_FILE);
			FileInputStream fileInput = new FileInputStream(file);
			Properties properties = new Properties();
			properties.load(fileInput);
			fileInput.close();

			@SuppressWarnings("rawtypes")
			Enumeration enuKeys = properties.keys();
			while (enuKeys.hasMoreElements()) {
				String key = (String) enuKeys.nextElement();
				listProperties.put(key, properties.getProperty(key));
			}
			if (listProperties != null && listProperties.size() > 0){
				String version = listProperties.get("version");
				//check if different version
				if (version!= null){
					if (!version.equals(Parameters.VERSION))
						updateProperty("version", Parameters.VERSION);

					//check if running in debug mode
					String runMode = listProperties.get("runMode");
					if (runMode != null && runMode.contains("debug")){
						//create "special" folder
						if (!listProperties.get("lastRun").equals(debugFolder))
							updateProperty("lastRunNotDebug", listProperties.get("lastRun"));
						updateProperty("lastRun", debugFolder);
						//and set flag
						inDebugMode = true;
					}
					if (!inDebugMode){
						if (listProperties.get("lastRunNotDebug") != null){
							if(!listProperties.get("lastRunNotDebug").equals(DateUtilities.getCurrentDate())){
								updateProperty("runIndex", "1");
								updateProperty("lastRun", DateUtilities.getCurrentDate());
							}else {
								updateProperty("lastRun", listProperties.get("lastRunNotDebug"));
							}
							removeProperty("lastRunNotDebug");
						}else if (!listProperties.get("lastRun").equals(DateUtilities.getCurrentDate())){
							updateProperty("runIndex", "1");
							updateProperty("lastRun", DateUtilities.getCurrentDate());
						}
					}

					//or write a new properties file
				}else{
					WriteProperties();
				}
			}else{
				WriteProperties();
			}
		} catch (FileNotFoundException e) {
			firstRun = true;
			WriteProperties();
		} catch (IOException e) {
			firstRun = true;
			stop("Unable to read properties file." +
						"\nCheck if the file exists or is not in use.");
		}
	}

	/**
	 * Updates an existing property in the jerboa.properties file.
	 * @param key - the name of the property to be updated
	 * @param value - the new value of the property
	 */
	public void updateProperty(String key, String value){

		try {

			listProperties.put(key, value);

			File file = new File(FilePaths.PROPERTIES_FILE);
			FileInputStream fileInput = new FileInputStream(file);
			Properties properties = new Properties();
			properties.load(fileInput);
			fileInput.close();

			properties.put(key, value);

			OutputStream fileOut = new FileOutputStream(file);
			properties.store(fileOut, "Jerboa settings");
			fileOut.close();
		}catch (IOException e) {
			stop("Unable to update properties file." +
						"\nCheck if the file exists or is not in use.");
		}
	}

	/**
	 * Removes an existing property in the jerboa.properties file.
	 * @param key - the name of the property to be removed
	 */
	public void removeProperty(String key){

		try {

			listProperties.remove(key);

			File file = new File(FilePaths.PROPERTIES_FILE);
			FileInputStream fileInput = new FileInputStream(file);
			Properties properties = new Properties();
			properties.load(fileInput);
			fileInput.close();

			properties.remove(key);

			OutputStream fileOut = new FileOutputStream(file);
			properties.store(fileOut, "Jerboa settings");
			fileOut.close();
		}catch (IOException e) {
			stop("Unable to remove entry from properties file." +
						"\nCheck if the file exists or is not in use.");
		}
	}

	/**
	 * Writes a properties file for the Maven build.
	 * This file should contain at least the software version
	 * so that it is automatically updated in the pom.xml file.
	 */
	private void exportPropertiesForMaven(){
		URL location = PropertiesManager.class.getProtectionDomain().getCodeSource().getLocation();
		//check if running from a jar and do not export
		if (!location.toString().endsWith(".jar") && !location.toString().endsWith(".dat")){
			String exportPath = location.getFile().replace("bin/","");
			exportPath = exportPath + "scripts/maven.properties";
			exportPath = exportPath.replaceAll(" ", "%20");
			String versionInFile = "";
			//read the version that is in the property file
			if (new File(exportPath).exists()){
				try{
					File file = new File(exportPath);
					FileInputStream fileInput = new FileInputStream(file);
					Properties properties = new Properties();
					properties.load(fileInput);
					fileInput.close();

					versionInFile = properties.getProperty("versionNumber");

				}catch(IOException e){
					System.out.println("Unable to read maven properties file.");
				}
			}
			//write the version to file only if the file does not exist or the property has been changed
			if (!new File(exportPath).exists() || (!versionInFile.equals(Parameters.VERSION)))
					FileUtilities.writeStringToFile(exportPath, "versionNumber="+Parameters.VERSION, false);
		}
	}

	/**
	 * Displays an error message and stops the application with an error code.
	 * @param message - the error message to be displayed
	 */
	private void stop(String message){
		if (Jerboa.inConsoleMode)
			System.out.println("ERROR: "+message);
		else
			new ErrorHandler(message);
		Jerboa.stop(true);
	}

	//GETTERS AND SETTERS
	public static boolean isInDebugMode() {
		return inDebugMode;
	}

	public static void setInDebugMode(boolean inDebugMode) {
		PropertiesManager.inDebugMode = inDebugMode;
	}

	public static String getRunFolder(){
		return PropertiesManager.listProperties.get("lastRun")+
				(!PropertiesManager.inDebugMode ? "-"+PropertiesManager.listProperties.get("runIndex") : "")
				+"/";
	}

	public static String getLastWorkSpace(){
		return PropertiesManager.listProperties.get("lastWorkspace").trim();
	}

	public static String getUsedWorkspaces(){
		return PropertiesManager.listProperties.get("usedWorkspaces").
				replaceAll(", ", ",").replaceAll(" ,", ",");
	}

	public static String[] getUsedWorkspacesAsArray(){
		String [] workspaces = PropertiesManager.listProperties.get("usedWorkspaces").split(",");
		for (int i = 0; i < workspaces.length; i++)
			workspaces[i] = workspaces[i].trim();
		return workspaces;
	}

	public static String[] getUsedDataBasesAsArray(){
		String [] databases = PropertiesManager.listProperties.get("usedDatabases").split(",");
		for (int i = 0; i < databases.length; i++)
			databases[i] = databases[i].trim();
		return databases;
	}

	public static String getUsedDataBases(){
		return PropertiesManager.listProperties.get("usedDatabases").
				replaceAll(", ", ",").replaceAll(" ,", ",");
	}

	public static String getLastDataBase(){
		return PropertiesManager.listProperties.get("lastDatabase").trim();
	}

	public static boolean isFirstRun() {
		return firstRun;
	}

	public static void setFirstRun(boolean firstRun) {
		PropertiesManager.firstRun = firstRun;
	}

	public static String getDebugFolder() {
		return debugFolder;
	}

}
