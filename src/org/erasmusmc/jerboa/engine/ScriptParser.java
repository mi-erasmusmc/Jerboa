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
 * $Rev:: 4841              $:  Revision of last commit                                   *
 * $Author:: root           $:  Author of last commit                                     *
 * $Date:: 					$:  Date and time (CET) of last commit						  *
 ******************************************************************************************/

package org.erasmusmc.jerboa.engine;

import java.io.BufferedReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;

import org.apache.commons.io.FilenameUtils;
import org.apache.commons.lang3.StringUtils;
import org.erasmusmc.jerboa.Jerboa;
import org.erasmusmc.jerboa.config.Parameters;
import org.erasmusmc.jerboa.utilities.ErrorHandler;
import org.erasmusmc.jerboa.utilities.FileUtilities;
import org.erasmusmc.jerboa.utilities.Logging;


/**
 * Parses a script file (.jsf) into an internal representation. Different sections
 * in the script are delimited by specific tags and they can contain general information,
 * parameter settings for modules and modifiers, data extraction or data encryption
 * necessary throughout the API work flow.
 *
 * @author MG {@literal &} MS
 */
public class ScriptParser {

	//script section types
	private static int NONE = 0;
	private static int MODULE = 1;
	private static int EXTRACT = 2;
	private static int PACK = 3;
	private static int METADATA = 4;

	//script file name
	private String scriptFile;

	//script content
	private List<String> scriptLines;

	//holds the parsed settings
	private ParsedSettings parsedSettings;

	private boolean extractionNeeded;

	private boolean scriptValid;

	//CONSTRUCTOR
	/**
	 * Constructor receiving the name of the script file as parameter.
	 * @param scriptFile - the script file
	 */
	public ScriptParser(String scriptFile){

		if (scriptFile != null && !scriptFile.equals("")){
			this.scriptFile = scriptFile;
			//read settings line by line
			scriptLines = new ArrayList<String>();
			try{
				BufferedReader br = FileUtilities.openFile(this.scriptFile);
				String strLine = "";
				while ((strLine = br.readLine()) != null)
					scriptLines.add(strLine);
			}catch(IOException e){
				Logging.add("Cannot read the script file "+this.scriptFile, Logging.ERROR);
				Jerboa.stop(true);
			}
			scriptValid = true;
			parse(scriptLines);
		}
	}

	/**
	 * Parses the script. Creates pairs of parameter name - parameter value
	 * and assigns them to the appropriate modules.
	 * @param lines - the settings to be distributed
	 * @return - an internal representation (name-value pairs) of the module settings
	 */
	public ParsedSettings parse(List<String> lines){

		Settings moduleSettings = null;
		Settings extractSettings = null;
		Settings packSettings = null;
		List<Settings> modifierSettings = null;

		parsedSettings = new ParsedSettings();
		boolean isModifier = false;

		int lineNr = 0;
		int modifierNb = 0;
		int mode = NONE;

		for (String line : lines){
			lineNr++;
			try{
				String trimLine = line.trim();
				String lcTrimLine = trimLine.toLowerCase();
				if (!trimLine.equals("") && !trimLine.startsWith("//") && !trimLine.startsWith("#")){
					//outside parameter settings
					if (mode == NONE){
						if (lcTrimLine.equals("metadata")){
							mode = METADATA;
							//initialize and get title
						}else if (lcTrimLine.startsWith("module")){
							mode = MODULE;
							modifierNb = 0;
							isModifier = false;
							//get title and class to be instantiated
							moduleSettings = new Settings();
							modifierSettings = new ArrayList<Settings>();
							moduleSettings.name = trimLine.substring(7,trimLine.indexOf('(')).trim();						 //the title of the module
							moduleSettings.type = trimLine.substring(trimLine.indexOf('(')+1, trimLine.indexOf(')')).trim(); //the module class to be called
							//check for duplicate module names
							if (!parsedSettings.moduleAndModifierNames.add(moduleSettings.name.toLowerCase())){
								scriptValid = false;
								if (!Jerboa.inConsoleMode)
									new ErrorHandler("Duplicate module name " + moduleSettings.name);
								Logging.add("Duplicate module name " + moduleSettings.name, Logging.ERROR);
								break;
							}
						}else if (lcTrimLine.startsWith("extract")){
							mode = EXTRACT;
							extractSettings = new Settings();
						}else if (lcTrimLine.startsWith("pack")){
							mode = PACK;
							packSettings = new Settings();
						}

					//parameter/modifier settings
					}else if (mode == MODULE){
						String moduleName = moduleSettings.name;
						//reached the end
						if (lcTrimLine.equals("/module")){
							//save the parsed settings
							parsedSettings.moduleSettings.add(moduleSettings);
							parsedSettings.modifierSettings.add(modifierSettings);
							//clear lists and mode
							moduleSettings = null;
							modifierSettings = null;
							modifierNb = 0;
							isModifier = false;
							mode = NONE;

							//check if in parameter setting zone
						}else if (lcTrimLine.startsWith("filter") || lcTrimLine.startsWith("modifier")){
							if (!isModifier)
								isModifier = true;
							//get title and class to be instantiated
							modifierSettings.add(modifierNb, new Settings());
							modifierSettings.get(modifierNb).name = trimLine.substring(StringUtils.startsWithIgnoreCase(trimLine, "modifier") ? 8 : 7,trimLine.indexOf('(')).trim();											  //the title of the modifier
							modifierSettings.get(modifierNb).type = trimLine.substring(trimLine.indexOf('(')+1, trimLine.indexOf(')')).trim();					  //the modifier class to be called
							//check for duplicate modifier names per module
							if (!parsedSettings.moduleAndModifierNames.add(moduleName.toLowerCase() + "_" + modifierSettings.get(modifierNb).name.toLowerCase())){
								scriptValid = false;
								if (!Jerboa.inConsoleMode)
									new ErrorHandler("Duplicate modifier name " + modifierSettings.get(modifierNb).name + " for module "+ moduleName);
								Logging.add("Duplicate modifier name " + modifierSettings.get(modifierNb).name + " for module "+ moduleName, Logging.ERROR);
								break;
							}
						}else if (lcTrimLine.startsWith("/filter") || lcTrimLine.startsWith("/modifier")){
							isModifier = false;
							modifierNb++;
						}else{
							if (isLineCoherent(trimLine) && !isOldSchoolScript(lcTrimLine)){
								//parameter name and value
								String[] parts = trimLine.split("=", 2); // variable = value
								if (!isModifier){
									//module parameter
									if (parts.length == 1){
										moduleSettings.parameters.add(new Pair<String,String>(parts[0].trim().toLowerCase(), ""));
									}else{
										moduleSettings.parameters.add(new Pair<String,String>(parts[0].trim().toLowerCase(),
												(!parts[0].toLowerCase().trim().equals("outputfilename") ?
														parts[1].trim().toUpperCase() : parts[1].trim())));
									}
									//modifier parameter
								}else{
									if (parts.length == 1){
										modifierSettings.get(modifierNb).parameters.add(new Pair<String,String>(parts[0].trim().toLowerCase(), ""));
									}else{
										modifierSettings.get(modifierNb).parameters.add(new Pair<String,String>(parts[0].trim().toLowerCase(),parts[1].trim()));
									}
								}
							}else{
								Jerboa.stop(true);
								break;
							}
						}
					//data extraction settings
					}else if (mode == EXTRACT){
						//reached the end
						if (lcTrimLine.equals("/extract")){
							//save the parsed settings
							parsedSettings.dataExtractionSettings = extractSettings;
							//clear list and mode
							extractSettings = null;
							mode = NONE;
						}else{
							if (isLineCoherent(trimLine) && !isOldSchoolScript(lcTrimLine)){
								extractionNeeded = true;
								//parameter name and value
								String[] parts = trimLine.split("=", 2); // variable = value
								extractSettings.parameters.add(new Pair<String,String>(parts[0].trim().toLowerCase(), parts[1].trim()));
							}else{
								Jerboa.stop(true);
								break;
							}
						}
					//data packing settings
					}else if (mode == PACK){
						//reached the end
						if (lcTrimLine.equals("/pack")){
							//save the parsed settings
							parsedSettings.packSettings = packSettings;
							//clear list and mode
							packSettings = null;
							mode = NONE;
						}else{
							if (isLineCoherent(trimLine) && !isOldSchoolScript(lcTrimLine)){
								String[] parts = trimLine.split("=", 2); // variable = value
								packSettings.parameters.add(new Pair<String,String>(parts[0].trim().toLowerCase(), parts[1].trim()));
							}else{
								Jerboa.stop(true);
								break;
							}
						}
					} else if (mode == METADATA){
						if (lcTrimLine.equals("/metadata"))
							mode = NONE;
						else {
							if (isLineCoherent(trimLine) && !isOldSchoolScript(lcTrimLine)){
								String[] parts = trimLine.split("=", 2); // variable = value
								if (parts[0].trim().equals("databaseNames")){
									String[] databases = parts[1].trim().split(",");
									for (int i = 0; i < databases.length; i ++)
										databases[i] = databases[i].trim();
									parsedSettings.metaData.databaseNames = Arrays.asList(databases);
								}
								//retrieve meta information from the line
								retrieveMetaData(parts);
							}else{
								Jerboa.stop(true);
								break;
							}
						}
					}
				}
			} catch (Exception e){
				scriptValid = false;
				Logging.add("Error parsing Jerboa script line " + lineNr + ": " + e.getMessage(), Logging.ERROR);
			}
		}

		return parsedSettings;
	}

	/**
	 * Checks if line contains an equal character marking the split point for parameter name=value.
	 * @param line - the string of characters to be checked
	 * @return - true if the line contains the character '='; false otherwise.
	 */
	private boolean isLineCoherent(String line){
		if (!line.contains("=")){
			Logging.add("Error in script file on line: " + line, Logging.ERROR);
			if (!Jerboa.inConsoleMode)
				new ErrorHandler("Error in script file on line: " + line);
			this.scriptValid = false;
			return false;
		}
		return true;
	}

	/**
	 * Checks if line contains one of the tags present in the
	 * scripts used in the old version of the application.
	 * @param line - the string of characters to be checked
	 * @return - true if the line contains "end" or "parameters"; false otherwise.
	 */
	private boolean isOldSchoolScript(String line){
		if (line.equals("end") || line.equals("parameters")){
				Logging.add("The script file seems to be from a previous version of Jerboa.", Logging.ERROR);
				if (!Jerboa.inConsoleMode)
					new ErrorHandler("The script file is not compatible with this version of Jerboa");
				this.scriptValid = false;
			return true;
		}
		return false;
	}

	/**
	 * Processes a split line from the MetaData part of the script
	 * and sets the respective property.
	 * @param split - a script line split by the character "="
	 */
	public void retrieveMetaData(String[] split){
		if (split[0].trim().equals("scriptVersion"))
			parsedSettings.metaData.scriptVersion = split[1].trim();
		if (split[0].trim().equals("requiredVersion"))
			parsedSettings.metaData.requiredVersion = split[1].trim();
		if (split[0].trim().equals("checksumRequired"))
			parsedSettings.metaData.checksumRequired = split[1].trim();
		if (split[0].trim().equals("splitSize"))
			parsedSettings.metaData.splitSize = split[1].trim();
		if (split[0].trim().equals("description"))
			parsedSettings.metaData.description = split[1].trim();
		if (split[0].trim().equals("creationDate"))
			parsedSettings.metaData.creationDate = split[1].trim();
		if (split[0].trim().equals("postMessage"))
			parsedSettings.metaData.postMessage = split[1].trim();
		if (split[0].trim().equals("scriptTag"))
			parsedSettings.metaData.scriptTag = split[1].trim();
		if (split[0].trim().equals("reusePatients"))
			parsedSettings.metaData.reusePatients = split[1].trim();
	}

	/**
	 * Adds to log the information contained in the MetaData section of the script file.
	 */
	public void displayMetaData(){
		String scriptVersion = parsedSettings != null ? parsedSettings.metaData.scriptVersion : null;
		String scriptCreationDate = parsedSettings != null ? parsedSettings.metaData.creationDate : null;
		String scriptDescription = parsedSettings != null ? parsedSettings.metaData.description : null;
		if (scriptFile != null && scriptValid){
			Logging.add("Loaded script file "+FilenameUtils.getName(scriptFile)+
					(scriptVersion != null && !scriptVersion.equals("") ? " version "+scriptVersion : "")+
					(scriptCreationDate != null && !scriptCreationDate.equals("") ? " created on "+scriptCreationDate+"." : ""));
			if (scriptDescription != null && !scriptDescription.equals(""))
				Logging.add(scriptDescription);
			Logging.addNewLine();
		}
	}

	/**
	 * Checks if the software version stored in the Parameters class is
	 * the same or superior to the one requested by the script file.
	 * The script file should contain a tag with the word "requiredVersion" and the
	 * right hand side of the tag is considered the version number.
	 * If the software version is not the same or superior to the required,
	 * the application should not continue as compatibility is not assured.
	 * This method checks group-wise (delimited by ".") if the part of version
	 * is the same length and if the one in the application is equal or superior to the one in the script.
	 * @return true if the version required by the script file is inferior or equal to
	 * the version of the software; false otherwise
	 * @see Parameters
	 */
	public boolean checkRequiredVersion(){
		boolean versionOK = true;
		if (getRequiredVersion() != null && !getRequiredVersion().equals("")){
			//split into groups based on the "." delimiter
			String[] currentVersion = Parameters.VERSION_NUMBER.split("\\.");
			String[] scriptVersion = getRequiredVersion().split("\\.");
			int end = Math.min(currentVersion.length, scriptVersion.length);
			if (end > 0){
				for (int i = 0; i < end; i++){
					//not same length in group
					if (scriptVersion[i].length() != currentVersion[i].length()){
						int diff = currentVersion[i].length() - scriptVersion[i].length();
						//pad with zeros where necessary
						if (diff < 0)
							currentVersion[i] = StringUtils.repeat("0", Math.abs(diff)) + currentVersion[i];
						else
							scriptVersion[i] = StringUtils.repeat("0", Math.abs(diff)) + scriptVersion[i];
					}
					//first group in the application version superior to the version in script
					if (i == 0 && currentVersion[i].compareTo(scriptVersion[i]) > 0){
						return true;
					}else{
						if (currentVersion[i].compareTo(scriptVersion[i]) > 0){
							versionOK &= true;
							break;
						}
						versionOK &= currentVersion[i].compareTo(scriptVersion[i]) == 0;
					}
				}

				if (!versionOK){
					if (!Jerboa.inConsoleMode){
						new ErrorHandler("The script requires Jerboa version " + getRequiredVersion() + " or higher in order to run." +
							" \nPlease select another script file or obtain a newer Jerboa version.");
					}
					Logging.add("The script requires Jerboa version " + getRequiredVersion() + " or higher in order to run", Logging.ERROR);
					Jerboa.stop(true);
				}
			}else{
				Logging.add("No required version present in script file or application.", Logging.ERROR);
				versionOK = false;
			}
		}else{
			Logging.add("No required version present in the script file.", Logging.ERROR);
			versionOK = false;
			if (!Jerboa.inConsoleMode){
				new ErrorHandler("There is no required minimum version in the script file.");
			}
		}

		return versionOK;
	}

	//INTERMEDIATE NESTED CLASSES USED IN PARSING
	public class ParsedSettings {
		public List<Settings> moduleSettings = new ArrayList<Settings>();
		public List<List<Settings>> modifierSettings = new ArrayList<List<Settings>>();
		public HashSet<String> moduleAndModifierNames = new HashSet<String>();
		public Settings dataExtractionSettings = new Settings();
		public Settings packSettings = new Settings();
		public MetaData metaData = new MetaData();
	}

	public class MetaData {
		public List<String> databaseNames = new ArrayList<String>();
		public String scriptVersion;
		public String requiredVersion;
		public String checksumRequired;
		public String splitSize;
		public String description;
		public String creationDate;
		public String postMessage;
		public String scriptTag;
		public String reusePatients;
	}

	public class Settings {
		public List<Pair<String,String>> parameters = new ArrayList<Pair<String,String>>();
		public String name;
		public String type;
	}

	public class Pair<A, B> {
		public String name;
		public String value;
		public Pair(String name, String value){
			this.name = name;
			this.value = value;
		}

		public String toString(){
			return "[[" + name + "],[" + value + "]]";
		}

		public int hashCode(){
			return name.hashCode() + value.hashCode();
		}

		@SuppressWarnings("rawtypes")
		public boolean equals(Object other){
			if (other instanceof Pair)
				if (((Pair)other).name.equals(name))
					if (((Pair)other).value.equals(value))
						return true;
			return false;
		}
	}

	//GETTERS AND SETTERS
	public List<String> getSettings() {
		return scriptLines;
	}

	public ParsedSettings getParsedSettings() {
		return parsedSettings;
	}

	public void setParsedSettings(ParsedSettings parsedSettings) {
		this.parsedSettings = parsedSettings;
	}

	public String getScriptFile() {
		return scriptFile;
	}

	public void setScriptFile(String scriptFile) {
		this.scriptFile = scriptFile;
	}

	public List<String> getDatabasenames() {
		return parsedSettings != null ?
				(parsedSettings.metaData != null ?
						parsedSettings.metaData.databaseNames : null) : null;
	}

	public String getScriptVersion() {
		return parsedSettings.metaData.scriptVersion;
	}

	public String getScriptTag() {
		return (parsedSettings.metaData.scriptTag == null ?
				"" : parsedSettings.metaData.scriptTag) ;
	}

	public String getRequiredVersion() {
		return parsedSettings.metaData.requiredVersion;
	}

	/**
	 * Checks if the script requires a checksum to be calculated for each
	 * of the input files. This parameter is part of the script metadata.
	 * @return - true if the checksumRequired flag is set in the script; false otherwise
	 */
	public boolean isChecksumRequired() {
		return parsedSettings.metaData.checksumRequired == null ||
				parsedSettings.metaData.checksumRequired.equals("") ||
				parsedSettings.metaData.checksumRequired.toLowerCase().equals("false") ||
				parsedSettings.metaData.checksumRequired.toLowerCase().equals("f") ? false :
					(parsedSettings.metaData.checksumRequired.toLowerCase().equals("true") ||
					parsedSettings.metaData.checksumRequired.toLowerCase().equals("t")) ? true : false;
	}

	/**
	 * This method will check if the reusePatients flag was set in the script.
	 * This allows the patient objects created in the previous run to be used,
	 * thus the compression step will be skipped.
	 * @return - true if the reusePatients flag in the script is set; false otherwise
	 */
	public boolean reusePatients() {
		return parsedSettings.metaData.reusePatients == null ||
				parsedSettings.metaData.reusePatients.equals("") ||
				parsedSettings.metaData.reusePatients.toLowerCase().equals("false") ||
				parsedSettings.metaData.reusePatients.toLowerCase().equals("f") ? false :
					(parsedSettings.metaData.reusePatients.toLowerCase().equals("true") ||
					parsedSettings.metaData.reusePatients.toLowerCase().equals("t")) ? true : false;
	}

	/**
	 * Returns the maximum size of a subset. This is used when the
	 * input files are not sorted. Note that the functionality
	 * is still present, but this parameter is obsolete.
	 * @return - the size of the splitting subset; -1 if not value present
	 */
	public int getSplitChunkSize() {
		int chunkSize = -1;
		if (parsedSettings.metaData.splitSize != null &&
				!parsedSettings.metaData.splitSize.equals("")){
			try{
				chunkSize = Integer.valueOf(parsedSettings.metaData.splitSize);
			}catch(Exception e){
				Logging.add("Invalid value for the split chunk size in the script", Logging.ERROR);
			}
		}
		return chunkSize;
	}

	public boolean isExtractionNeeded() {
		return extractionNeeded;
	}

	public boolean isScriptValid() {
		return scriptValid;
	}

	public boolean hasPostMessage(){
		return (parsedSettings.metaData.postMessage != null &&
				!parsedSettings.metaData.postMessage.equals(""));
	}

	public String getPostMessage(){
		return parsedSettings.metaData.postMessage;
	}

}
