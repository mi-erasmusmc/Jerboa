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

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.BitSet;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

import org.apache.commons.collections.list.SetUniqueList;
import org.apache.commons.lang3.text.StrBuilder;
import org.erasmusmc.jerboa.Jerboa;
import org.erasmusmc.jerboa.config.DataDefinition;
import org.erasmusmc.jerboa.config.FilePaths;
import org.erasmusmc.jerboa.config.Parameters;
import org.erasmusmc.jerboa.dataClasses.Patient;
import org.erasmusmc.jerboa.engine.ScriptParser.Pair;
import org.erasmusmc.jerboa.utilities.DateUtilities;
import org.erasmusmc.jerboa.utilities.ErrorHandler;
import org.erasmusmc.jerboa.utilities.FileUtilities;
import org.erasmusmc.jerboa.utilities.Logging;
import org.erasmusmc.jerboa.utilities.OutputManager;
import org.erasmusmc.jerboa.utilities.Progress;
import org.erasmusmc.jerboa.utilities.Timer;

/**
 * Generic class for the modules and modifiers. All modules and modifiers should extend this class.
 * Contains all methods to be overridden in the modules and modifiers.
 *
 * @author MG
 *
 */
public abstract class Worker{

	/**
	 * Name of the output file (relative to the working folder).
	 * default = The name of the module, without spaces, with .csv extension
	 */
	public String outputFileName;

	/**
	 * Name of the resultSet file (relative to the working folder).
	 * default = The name of the module, without spaces, with .csv extension
	 */
	public String resultSetFileName = "resultSet";

	/**
	 * Determines whether the module should output graphs.
	 * default = false;
	 */
	public boolean createGraphs;

	/**
	 * Determines whether the module should create an intermediate result file
	 * during the processing of the patients. Note that this can considerably
	 * slow down a module or a modifier.
	 * default = false;
	 */
	public boolean intermediateFiles;

	/**
	 * Determines whether the module should create an intermediate statistics
	 * during the processing of the patients.
	 * Note that this might considerably increase the memory load of the module and reduces speed.
	 * default = false;
	 */
	public boolean intermediateStats;

	/**
	 * Determines if this worker will be active or not during the workflow.
	 * By default it is set to be active. This setting can be overridden in the script.
	 */
	public boolean active = true;

	/**
	 * Just a remainder from the old days.
	 */
	//TODO remove it when all scripts are modified
	public boolean outputInFLOC = false;

	/**
	 * For these patients special output can be added for debugging.
	 * For example show in the patient viewer.
	 */
	public List<String> debugPatientIDs = new ArrayList<String>();

	/**
	 * For these patients special processing is needed for debugging.
	 */
	public List<String> processPatientIDs = new ArrayList<String>();

	//output specific
	protected String intermediateFileName;	//the name of the intermediate file
	protected String title;		    		// title of the worker

	//parameter mapping
	protected Map<String, Field> specificParameters;
	protected Map<String, Field> genericParameters;
	protected List<String> unspecifiedParameters;
	protected boolean settingsOK;

	//input dependencies files/extended columns
	protected BitSet neededFiles = new BitSet(DataDefinition.INPUT_FILE_TYPES.length);
	protected HashMap<Integer, Set<String>> neededExtendedColumns =
			new HashMap<Integer, Set<String>>(DataDefinition.INPUT_FILE_TYPES.length);
	protected HashMap<Integer, HashMap<String, Set<String>>> neededNumericColumns =
			new HashMap<Integer, HashMap<String, Set<String>>>(DataDefinition.INPUT_FILE_TYPES.length);

	//user feed-back
	protected Timer timer;
	protected Progress progress;
	protected boolean finishedSuccessfully;
	protected boolean inPostProcessing;

	/**
	 * Basic constructor
	 */
	public Worker(){
		mapParameters();
		if (Jerboa.unitTest) {
			Parameters.DATABASE_NAME = "TEST";
			Jerboa.setOutputManager(new OutputManager());
			setOutputFileNames();
		}
	}

	/**
	 * Maps the parameters specific to each worker
	 * and the ones that are generic to the super classes.
	 */
	private void mapParameters() {

		Class<?> c = this.getClass();

		//module specific (keep only public parameters)
		specificParameters = new HashMap<String, Field>();
		Field[] fields = c.getDeclaredFields();
		for (Field field : fields)
			if (Modifier.isPublic(field.getModifiers()))
				specificParameters.put(field.getName().toLowerCase(), field);

		//generic parameters (from super classes)
		c = c.getSuperclass();
		fields = c.getFields();
		genericParameters = new HashMap<String, Field>();
		for (Field generic : fields)
			if (Modifier.isPublic(generic.getModifiers()))
				genericParameters.put(generic.getName().toLowerCase(), generic);

	}

	/**
	 * Performs a generic check on the legality of the values of the public parameters
	 * (i.e., the ones declared in the script) for this worker. This method is to be called
	 * after the initialization of the worker, in order to make sure that no faulty parameters settings
	 * occurred during this process. In general, parameters that are numbers should contain
	 * a positive value, lists and strings should not be null, etc.
	 * @param exceptions - a list of parameter names (as Strings) that are to be excluded from checking
	 * @return - true if all the public parameter settings are legal; false otherwise
	 */
	@SuppressWarnings("rawtypes")
	protected boolean checkParameterValues(List<String> exceptions){
		boolean isOK = true;
		boolean exceptionsExist = exceptions != null && exceptions.size() > 0;
		Field[] fields = this.getClass().getFields();
		for (Field field : fields){
			try {
				//check if parameter null or in the list of exceptions
				if (field == null)
					logFaultyParameter(field);
				if (exceptionsExist && exceptions.contains(field.getName()))
					continue;
				if (field.get(this) == null)
					isOK &= logFaultyParameter(field);
				/*
				//check the most common types of public parameters
				else if ((field.getType().equals(int.class) ||
						field.getType().equals(Integer.class)) &&
						(Integer)field.get(this) < 0)
					isOK &= logFaultyParameter(field);
				else if ((field.getType().equals(double.class) ||
						field.getType().equals(Double.class)) &&
						(Double)field.get(this) < 0)
					isOK &= logFaultyParameter(field);
				else if ((field.getType().equals(long.class) ||
						field.getType().equals(Long.class)) &&
						(Long)field.get(this) < 0)
					isOK &= logFaultyParameter(field);
				else if ((field.getType().equals(float.class) ||
						field.getType().equals(Float.class)) &&
						(Float)field.get(this) < 0)
					isOK &= logFaultyParameter(field);
				*/
				//should never be the case on the primitive to be null - default is false
				else if ((field.getType().equals(boolean.class) ||
						field.getType().equals(Boolean.class)) &&
						(Boolean)field.get(this) == null)
					isOK &= logFaultyParameter(field);
				else if (field.getType().equals(List.class) &&
						(List)field.get(this) == null)
					isOK &= logFaultyParameter(field);
				else if (field.getType().equals(String.class) &&
						(String)field.get(this) == null)
					isOK &= logFaultyParameter(field);
			} catch (IllegalArgumentException | IllegalAccessException e) {
				Logging.outputStackTrace(e, true);
				return false;
			}
		}

		return isOK;
	}

	/**
	 * Writes to the log and on the console of the application if a parameter
	 * value is illegal. In case the parameter is not null, then details
	 * regarding this parameter are logged and displayed.
	 * @param field - the public parameter to be checked
	 * @throws IllegalArgumentException - if the parameter value cannot be accessed
	 * @throws IllegalAccessException - if the parameter does not exist
	 * @return - always false; just out of convenience to save a statement in the method where it is used
	 */
	private boolean logFaultyParameter(Field field) throws IllegalArgumentException, IllegalAccessException {
		Logging.add("Illegal parameter value in "+this.getTitle()+"."+
			(field == null ? " A public parameter is set to NULL" :
			" The parameter " + field.getName()+ " of type " +
					field.getType() + " is set to " +
					field.get(this)), Logging.ERROR);
		return false;
	}

	/**
	 * Set the parameter to the given value. There are five different parameter
	 * classes supported. The function checks if the parameter name is correct
	 * and if the parameter value is not a default.
	 * @param parameter - a pair of name and value which represents the parameter setting.
	 * @param parameterList - the list in which the parameter should be found;
	 * @return - true if the parameter setting was performed successfully; false otherwise
	 */
	@SuppressWarnings("unchecked")
	public boolean setParameter(Pair<String, String> parameter, Map<String, Field> parameterList){

		//retrieve parameter
		Field field = parameterList.get(parameter.name);

		if (field != null){
			try{
				//check if integer
				if (field.getType().equals(int.class) || field.getType().equals(Integer.class)){
					if (parameter.value.equals(""))
						return false;
					field.setInt(this, Integer.parseInt(parameter.value));
					return true;
				}
				//check if long
				if (field.getType().equals(long.class) || field.getType().equals(Long.class)){
					if (parameter.value.equals(""))
						return false;
					field.setLong(this, Long.parseLong(parameter.value));
					return true;
				}
				//check if boolean
				if (field.getType().equals(boolean.class) || field.getType().equals(Boolean.class)){
					if (parameter.value.equals("") ||
							!(parameter.value.trim().toLowerCase().equals("false") ||
							parameter.value.trim().toLowerCase().equals("true")))
						return false;
					field.setBoolean(this, Boolean.parseBoolean(parameter.value.trim().toLowerCase()));
					return true;
				}
				//check if double
				if (field.getType().equals(double.class) || field.getType().equals(Double.class)){
					if (parameter.value.equals(""))
						return false;
					field.setDouble(this, Double.parseDouble(parameter.value));
					return true;
				}
				//check if string (allowed empty)
				if (field.getType().equals(String.class)){
					field.set(this, parameter.value);
					return true;
				}
				//check if list (allowed empty)
				if (field.getType().equals(List.class)){
					List<String> list = (List<String>)field.get(this);
					if (!parameter.value.equals(""))
						list.add(parameter.value);
					return true;
				}
			} catch (IllegalArgumentException | IllegalAccessException e) {
				Logging.add("Parsing parameter "+parameter.name+" with value "+parameter.value, Logging.ERROR);
				return false;
			}
			//field is null
		}else{
			return false;
		}

		return true;
	}

	/**
	 * Sets all the parameters of the module to their corresponding
	 * value parsed from the script. It also sets a flag reflecting
	 * the validity of the parameter settings.
	 * @param parameters - the list of parameters to be set
	 */
	public void setParameters(List<Pair<String, String>> parameters){

		if (parameters != null && !parameters.isEmpty()){
			List<Pair<String, String>> specific = new ArrayList<Pair<String, String>>();
			List<Pair<String, String>> generic = new ArrayList<Pair<String, String>>();
			@SuppressWarnings("unchecked")
			List<String> specificParameterNames = SetUniqueList.decorate(new ArrayList<String>());

			unspecifiedParameters = new ArrayList<String>();

			//split the parameters into two lists (generic / specific)
			for (Pair<String, String> parameter : parameters){
				if (specificParameters.containsKey(parameter.name)){
					specific.add(parameter);
					specificParameterNames.add(parameter.name);
				}else if (genericParameters.containsKey(parameter.name))
					generic.add(parameter);
				else {
					settingsOK = false;
					String errorMessage = "Incorrect parameter. The " + parameter.name +
							" parameter is\n not a property of " + this.getTitle();
					if (!Jerboa.inConsoleMode)
						new ErrorHandler(errorMessage);
					errorMessage = "Parameter " + parameter.name +
							" is not a property of " + this.getTitle();
					Logging.add(errorMessage, Logging.ERROR);
					throw new IllegalArgumentException(errorMessage);
				}
			}

			//check if there are specific parameters and if all are present in the list
			if (specificParameterNames.size() < specificParameters.size()){
				//keep track of unspecified parameters in the script
				Set<String> parameterNames = specificParameters.keySet();
				for (String parameter : parameterNames)
					if (!specificParameterNames.contains(parameter))
						unspecifiedParameters.add(this.title+" : "+parameter);
				settingsOK = false;
				return;
			}else{
				settingsOK = true;
			}

			//set specific parameters
			if (settingsOK){
				for (Pair<String, String> parameter : specific){
					settingsOK = setParameter(parameter, specificParameters);
					//keep track of successful parameter settings
					if (!settingsOK){
						if (!Jerboa.inConsoleMode)
							new ErrorHandler("Incorrect value for parameter "+parameter.name+ " in module/modifier "+this.getTitle());
						Logging.add("Incorrect value for parameter "+parameter.name+ " in module/modifier "+this.getTitle(), Logging.ERROR);
						break;
					}
				}
			}

			//set generic parameters
			for (Pair<String, String> parameter : generic)
				setParameter(parameter, genericParameters);

			//empty parameters list
		}else{
			settingsOK = true;
		}
	}

	/**
	 * This function represents a wrapper of the private function
	 * checkParameterValues(). It is to be overridden in each worker
	 * that custom parameter checking is to be performed.
	 * By overriding it, one can define a list of names for the
	 * parameters that represent exceptions and are to be skipped during
	 * the checking. The list is allowed to be null (i.e., no overriding).
	 * Note that it checks only the public parameters (same ones as declared
	 * in the settings file).
	 * @return - true if all parameter settings (besides exceptions) turned out to be legal; false otherwise
	 */
	public boolean checkParameters(){
		//will contain a list of the parameter names to be skipped
		List<String> exceptions = null;
		return checkParameterValues(exceptions);
	}

	/**
	 * Generic method that should initialize the private parameters
	 * (used internally by the worker but not explicitly defined in
	 * the script file) and displays the settings (if any)
	 * of the specific parameters (present in the script file).
	 * Should also initialize all the files that are used in the
	 * output of the module (intermediate) results.
	 * @return - true if the initialization of this worker was successful; false otherwise
	 */
	public abstract boolean init();

	/**
	 * Applies the necessary operations on patient.
	 * @param patient - the patient to be processed
	 * @return - the patient with its added/modified attributes
	 */
	public abstract Patient process(Patient patient);

	/**
	 * Will graphically display the results of the module and
	 * output them in PDF format.
	 * To be overridden by each worker that requires graphs.
	 */
	public void displayGraphs(){/* TO BE OVERRIDEN */}

	/**
	 * Will output the results of the worker.
	 */
	public abstract void outputResults();

	/**
	 * Manually empty, close, delete objects used through the
	 * worker to avoid memory leaks. The GC should take care
	 * of it, but this is still good practice.
	 * To be overridden where used.
	 */
	public void clearMemory(){
		specificParameters = null;
		genericParameters = null;
		neededFiles = null;
		neededExtendedColumns = null;
	}

	/**
	 * Represents a wrapper of the outputResults() method to allow setting
	 * the flag for a successful run of this worker. Once the worker has reached
	 * and passed the outputResults() method, it is considered to have finished
	 * successfully.
	 */
	public void outputWorkerResults(){
		outputResults();
		finishedSuccessfully = true;
	}

	/**
	 * Calculates some intermediate statistics specific to this worker.
	 */
	public void calcStats(){/* TO BE OVERRIDEN */}

	//OUTPUT RELATED
	/**
	 * Adds data to the output buffer.
	 * @param fileName - the name of the output file
	 * @param data - the data to be added to the output
	 */
	public void addToOutputBuffer(String fileName, String data){
		if (!Jerboa.getOutputManager().hasFile(fileName))
			Jerboa.getOutputManager().addFile(fileName);
		Jerboa.getOutputManager().writeln(fileName, data, true);
	}

	/**
	 * Adds the elements in data to the output buffer.
     * @param fileName - the name of the output file
	 * @param data - a list of data to be output
	 */
	public void addToOutputBuffer(String fileName, List<?> data){
		if (!Jerboa.getOutputManager().hasFile(fileName))
			Jerboa.getOutputManager().addFile(fileName);
		for (int i=0;i<data.size();i++)
			Jerboa.getOutputManager().writeln(fileName, data.get(i).toString(), true);
	}

	/**
	 * Converts all the information in patient into a string and adds
	 * it to the output buffer.
	 * @param fileName - the name of the output file
	 * @param patient - a patient object to be output
	 */
	public void addToOutputBuffer(String fileName, Patient patient) {
		if (patient != null){
			String data  = patient.toStringConvertedDate(false) + "," +
				(patient.getPopulationStartDate() == -1 ? "" : DateUtilities.daysToDate(patient.getPopulationStartDate())) + "," +
				(patient.getPopulationEndDate() == -1 ? "" : DateUtilities.daysToDate(patient.getPopulationEndDate())) + "," +
				(patient.getCohortStartDate() == -1 ? "" : DateUtilities.daysToDate(patient.getCohortStartDate())) + "," +
				(patient.getCohortEndDate() == -1 ? "" : DateUtilities.daysToDate(patient.getCohortEndDate()));
			addToOutputBuffer(fileName, data);
		}
	}

	/**
	 * Converts all the information in patient into a string and adds
	 * it to the output buffer.
	 * @param fileName - the name of the output file
	 * @param patient - a patient object to be output
	 */
	public void addToOutputBuffer(String fileName, Patient patient, String extraColumns){
		if (patient != null) {
			String data  = patient.toStringConvertedDate(false) + "," +
				(patient.isInPopulation() ? DateUtilities.daysToDate(patient.getPopulationStartDate()) : "") + "," +
				(patient.isInPopulation() ? DateUtilities.daysToDate(patient.getPopulationEndDate()) : "") + "," +
				(patient.isInCohort() ? DateUtilities.daysToDate(patient.getCohortStartDate()) : "") + "," +
				(patient.isInCohort() ? DateUtilities.daysToDate(patient.getCohortEndDate()) : "") + "," +
				extraColumns;
			addToOutputBuffer(fileName, data);
		}
	}

	/**
	 * Converts all the information in patient into a string and adds
	 * it to the output buffer. By default it writes to the intermediate
	 * results file.
	 * @param patient - a patient object to be output
	 */
	public void addToOutputBuffer(Patient patient){
		addToOutputBuffer(this.intermediateFileName, patient);
	}

	/**
	 * Adds the elements in data to the output buffer
	 * of the intermediateFileName.
	 * @param data - a list of data to be output
	 */
	public void addToOutputBuffer(List<?> data){
		if (!Jerboa.getOutputManager().hasFile(this.intermediateFileName))
			Jerboa.getOutputManager().addFile(this.intermediateFileName);
		for (int i=0;i<data.size();i++)
			Jerboa.getOutputManager().writeln(this.intermediateFileName, data.get(i).toString(), true);
	}

	/**
	 * Adds data to the output buffer of the intermediateFileName.
	 * @param data - the data to be added to the output
	 */
	public void addToOutputBuffer(String data){
		if (!Jerboa.getOutputManager().hasFile(this.intermediateFileName))
			Jerboa.getOutputManager().addFile(this.intermediateFileName);
		Jerboa.getOutputManager().writeln(this.intermediateFileName, data, true);
	}

	/**
	 * This method will flush whatever data remains in the buffer
	 * for the intermediateFile. This method is to be overridden
	 * by each module/modifier.
	 */
	public void flushRemainingData(){
		if (Jerboa.getOutputManager().hasFile(this.intermediateFileName))
			Jerboa.getOutputManager().closeFile(this.intermediateFileName);
	}

	/**
	 * Will display on the console all the parameter settings for this worker.
	 * It is to be used mainly in debug mode.
	 */
	public void displayParameterSettings(){
		if (specificParameters != null && specificParameters.size() > 0){
			Logging.add("Settings for "+this.title+": ");
			for (Map.Entry<String, Field> entry : specificParameters.entrySet()){
				if (entry != null){
					Field field = entry.getValue();
					try{
						//check if integer or double
						if (field.getType().equals(int.class) ||
								field.getType().equals(double.class)){
							Logging.add(field.getName() + ": "+field.get(this));
						//check if boolean
						}else if (field.getType().equals(boolean.class)){
							Logging.add(field.getName() + ": "+ (field.getBoolean(this) ? "true" : "false"));
						//check if string (allowed empty)
						}else if (field.getType().equals(String.class)){
							Logging.add(field.getName() + ": "+ ((String)field.get(this) != null &&
									!((String)field.get(this)).equals("") ? field.get(this) : "NOT DEFINED"));
						//check if list (allowed empty)
						}else if (field.getType().equals(List.class)){
							@SuppressWarnings("unchecked")
							List<String> list = (List<String>)field.get(this);
							Logging.add(field.getName().toUpperCase() + ": ");
							if (list != null && list.size() > 0){
								for (String s : list)
									Logging.add("\t"+s);
							}else{
								Logging.add("\t"+"NOT DEFINED");
							}
						}
					} catch (Exception e) {
						Logging.add("Unable to diplay parameter settings for worker "+this.title, Logging.ERROR);
					}
				}
			}

			Logging.addNewLine();
		}
	}

	/**
	 * Method used for debugging purposes. It prints to file all the parameter settings
	 * present in the script file for this worker (specific and generic).
	 * It also keeps track and prints the ones that are not declared in the module itself.
	 * @param parameters - the list parameters passed from the script file to be mapped by this worker.
	 */
	public void printParameterMapping(List<Pair<String, String>> parameters){

		if (parameters != null && !parameters.isEmpty()){

			//sort the parameters of the module
			TreeMap<String, Field> specificClass = new TreeMap<String, Field>();
			specificClass.putAll(specificParameters);

			TreeMap<String, Field> genericClass = new TreeMap<String, Field>();
			genericClass.putAll(genericParameters);

			//declare lists for the parameters coming from the script file
			List<String> specificScript = new ArrayList<String>();
			List<String> genericScript = new ArrayList<String>();
			List<String> notDeclaredScript = new ArrayList<String>();

			//split the script parameters into separate lists
			for (Pair<String, String> parameter : parameters){
				if (specificParameters.containsKey(parameter.name))
					specificScript.add(parameter.name+"\t"+parameter.value);
				else if (genericParameters.containsKey(parameter.name))
					genericScript.add(parameter.name+"\t"+parameter.value);
				else
					notDeclaredScript.add(parameter.name+"\t"+parameter.value);
			}

			//sort parameters
			Collections.sort(specificScript);
			Collections.sort(genericScript);
			Collections.sort(notDeclaredScript);

			//prepare output
			StrBuilder out = new StrBuilder();
			out.appendNewLine();
			out.appendln("Parameters for module/modifier: " + this.getTitle());
			out.appendln("=====================================================");
			out.appendNewLine();

			//go through generic parameters
			if (genericClass.size() > 0 || genericScript.size() > 0){
				out.appendln("Generic parameters:");
				out.appendln("In class\t"+"In script\t"+"Value\t");
				out.appendln("-----------------------------------------------------");

				Iterator<String> itClass = genericClass.keySet().iterator();
				Iterator<String> itScript = genericScript.iterator();

				while (itClass.hasNext() || itScript.hasNext()){
					out.append((itClass.hasNext() ? itClass.next() : "") + "\t");
					out.appendln((itScript.hasNext() ? itScript.next() : ""));
				}
			}

			//go through specific parameters
			if (specificClass.size() > 0 || specificScript.size() > 0){
				out.appendNewLine();
				out.appendln("Specific parameters:");
				out.appendln("In class\t"+"In script\t"+"Value\t");
				out.appendln("-----------------------------------------------------");

				Iterator<String> itClass = specificClass.keySet().iterator();
				Iterator<String> itScript = specificScript.iterator();

				while (itClass.hasNext() || itScript.hasNext()){
					out.append((itClass.hasNext() ? itClass.next() : "") + "\t");
					out.appendln((itScript.hasNext() ? itScript.next() : ""));
				}
			}
			//go through undeclared (only in script)
			if (notDeclaredScript.size() > 0){
				out.appendNewLine();
				out.appendln("Not declared in class but present in script:");
				out.appendln("Name\t"+"Value\t");
				out.appendln("-----------------------------------------------------");

				Iterator<String> itScript = notDeclaredScript.iterator();

				while (itScript.hasNext()){
					out.appendln((itScript.hasNext() ? itScript.next() : ""));
				}
			}

			//write to file
			String outputFile = FilePaths.LOG_PATH + "ParameterSettings.txt";
			FileUtilities.outputData(outputFile, out,true);

		}
	}

	//GETTERS AND SETTERS
	/**
	 * Checks what input files are required by this worker.
	 * Should be overridden by each module/modifier
	 */
	public abstract void setNeededFiles();

	/**
	 * Will set the name of the outputFileName and of the intermediateFileName
	 * when the modules are run in test/debug mode (i.e., with no database name and no API version).
	 * If path is null then the path is set to the current directory.
	 * @param path - the desired path for the output files
	 */
	public void setOutputFileNamesInDebug(String path){
		if (path != null && !path.equals("") )
			path = path.endsWith("/") ? path : path+"/";
		else
			path = "./";

		this.outputFileName = path+this.getClass().getSimpleName()+"_output.csv";
		this.intermediateFileName = path+this.getClass().getSimpleName()+"_intermediate.csv";

	}

	/**
	 * Will delete both the result file and intermediate results file of the module
	 * if they were created in path. It is to be used in debug mode mainly, but can force
	 * the deletion of the worker output if needed, just make sure path reflects the current
	 * working folder of the API.
	 * @param path - the desired path of the output files
	 */
	public void deleteOutputFilesInDebug(String path){
		if (path != null && !path.equals("") )
			path = path.endsWith("/") ? path : path+"/";
		else
			path = "./";
		try{
			File f = new File(this.outputFileName);
			if (f.exists())
				FileUtilities.delete(f);
			f = new File(this.intermediateFileName);
			if (f.exists())
				FileUtilities.delete(f);
		}catch (IOException e){
			System.out.println("Could not delete the output files");
		}
	}

	/**
	 * Creates the path and name for the intermediate and result output
	 * of the worker. To be implemented by each worker.
	 */
	public abstract void setOutputFileNames();

	/**
	 * Returns a "truth table" of the needed files for this module.
	 * @return - a bit set containing a binary representation of the needed files
	 * for this module to run.
	 */
	public final BitSet getNeededFiles(){
		neededFiles.set(DataDefinition.PATIENTS_FILE);
		setNeededFiles();
		return neededFiles;
	}

	/**
	 * Will set the bit for the required input file in the BitSet containing the neededFiles.
	 * The file to be required is one of the input file types defined in DataDefinition
	 * (e.g., DataDefinition.PATIENTS_FILE) and its index in the BitSet is corresponding to its type declaration.
	 * @param file - the input file to be set as required by this worker
	 */
	public void setRequiredFile(int file){
		if (file > 0)
			this.neededFiles.set(file);
	}

	/**
	 * Will set the bits for the required input files in the BitSet containing the neededFiles.
	 * The files to be required is one of the input file types defined in DataDefinition
	 * (e.g., DataDefinition.PATIENTS_FILE) and its index in the BitSet is corresponding to its type declaration.
	 * @param files - an array containing the input file types to be set as required by this worker
	 */
	public void setRequiredFiles(int[] files){
		if (files.length  > 0)
			for (int i = 0; i < files.length; i++)
				this.neededFiles.set(files[i]);
	}

	/**
	 * Returns a "truth table" of the needed extended data columns for this module.
	 * @param fileType - the type of the input file the extended data columns are needed for
	 * @return - a set of strings representing the names of the needed extended columns
	 * for this fileType.
	 */
	public Set<String> getNeededExtendedColumns(int fileType){
		if (fileType != DataDefinition.NO_FILE)
			return neededExtendedColumns.get(fileType);
		return null;
	}

	/**
	 * Returns a "truth table" of the needed extended data columns for this module.
	 * @return - a map of sets containing the name of the extended data columns needed
	 * per input file needed for this worker to run.
	 */
	public HashMap<Integer, Set<String>> getNeededExtendedColumns() {
		setNeededExtendedColumns();
		return neededExtendedColumns;
	}

	/**
	 * Each module should provide a list of the extended data columns,
	 * in every input file, needed in order to run the worker.
	 */
	public abstract void setNeededExtendedColumns();

	/**
	 * Will set a needed extended data column for the inputFileType.
	 * The inputFileType is one of the input file types defined in DataDefinition
	 * (e.g., DataDefinition.PATIENTS_FILE). The index of the set in the list
	 * is equal to its type value in DataDefinition and the name of the extended column
	 * under string representation.
	 * @param inputFileType - the type of the input file
	 * @param column - the name of the extended data column
	 */
	public void setRequiredExtendedColumn(byte inputFileType, String column) {
		if (inputFileType != DataDefinition.NO_FILE) {
			Set<String> extendedColumns = neededExtendedColumns.get((int)inputFileType);
			if (extendedColumns == null)
				extendedColumns = new HashSet<String>();
			extendedColumns.add(column.toLowerCase());
			neededExtendedColumns.put((int)inputFileType, extendedColumns);
		}
	}

	/**
	 * Will set an array of needed extended data columns for the inputFileType.
	 * The inputFileType is one of the input file types defined in DataDefinition
	 * (e.g., DataDefinition.PATIENTS_FILE). The index of the set in the list
	 * is equal to its type value in DataDefinition and the name of the extended column
	 * is added under character string representation.
	 * @param inputFileType - the type of the input file
	 * @param columns - an array of extended data columns names
	 */
	public void setRequiredExtendedColumns(byte inputFileType, String[] columns) {
		if (inputFileType != DataDefinition.NO_FILE) {
			Set<String> extendedColumns = neededExtendedColumns.get((int)inputFileType);
			if (extendedColumns == null)
				extendedColumns = new HashSet<String>();
			for (int i = 0; i < columns.length; i++)
				extendedColumns.add(columns[i].toLowerCase());
			neededExtendedColumns.put((int)inputFileType, extendedColumns);
		}
	}

	/**
	 * Returns for the specified file type a hashmap containing the columns that should be
	 * numeric for a specified type value (ATC, EventType, or MeasurementType).
	 * @param inputFileType - the type of the input file
	 * @return - hashmap with key type and value column
	 */
	public HashMap<String, Set<String>> getNeededNumericColumns(int inputFileType) {
		return neededNumericColumns.get(inputFileType);
	}

	/**
	 * Returns a "truth table" of the needed extended data columns for this module.
	 * @return - a hashmap with key type and value column per input file that need
	 * to be numeric for this worker to run.
	 */
	public HashMap<Integer, HashMap<String, Set<String>>> getNeededNumericColumns() {
		setNeededNumericColumns();
		return neededNumericColumns;
	}

	/**
	 * Each module and modifier should provide a list of pairs of type (ATC, EventType,
	 * or MeasurementType) and a column that should be numeric in the specified input file.
	 */
	public abstract void setNeededNumericColumns();

	/**
	 * Will set a map of a map of lists of columns per type (ATC, EventType, or MeasurementType)
	 * per inputFileType for which the specified columns need to be numeric.
	 * @param inputFileType - the type of the input file
	 * @param type - ATC, EventType, or MeasurementType
	 * @param column - columns name
	 */
	public void setRequiredNumericColumn(byte inputFileType, String type, String column) {
		type = type.toUpperCase();
		column = column.toLowerCase();
		if (inputFileType != DataDefinition.NO_FILE) {
			HashMap<String, Set<String>> typeNumericColumns = neededNumericColumns.get((int)inputFileType);
			if (typeNumericColumns == null) {
				typeNumericColumns = new HashMap<String, Set<String>>();
				neededNumericColumns.put((int) inputFileType, typeNumericColumns);
			}
			Set<String> numericColumns = typeNumericColumns.get(type);
			if (numericColumns == null) {
				numericColumns = new HashSet<String>();
				typeNumericColumns.put(type, numericColumns);
			}
			numericColumns.add(column);
		}
	}

	//GETTERS AND SETTERS FOR ATTRIBUTES
	public String getTitle() {
		return title;
	}

	public void setTitle(String title) {
		this.title = title;
	}

	public String getIntermediateFileName() {
		return intermediateFileName;
	}

	public void setIntermediateFileName(String filename) {
		intermediateFileName = filename;
	}

	public boolean isCreateGraphs() {
		return createGraphs;
	}

	public void setCreateGraphs(boolean createGraphs) {
		this.createGraphs = createGraphs;
	}

	public void setNeededFiles(BitSet neededFiles) {
		this.neededFiles = neededFiles;
	}

	public boolean hasSettingsOK() {
		return settingsOK;
	}

	public boolean isActive() {
		return active;
	}

	public void setActive(boolean active) {
		this.active = active;
	}

	public boolean hasFinishedSuccessfully() {
		return finishedSuccessfully;
	}

	public void setFinishedSuccessfully(boolean finishedOK) {
		this.finishedSuccessfully = finishedOK;
	}

}
