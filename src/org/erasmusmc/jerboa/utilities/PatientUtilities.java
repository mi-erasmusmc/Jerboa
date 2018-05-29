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
 * $Rev:: 4797              $:  Revision of last commit                                   *
 * $Author:: root           $:  Author of last commit                                     *
 * $Date:: 					$:  Date and time (CET) of last commit						  *
 ******************************************************************************************/

package org.erasmusmc.jerboa.utilities;

import org.apache.commons.io.FilenameUtils;
import org.apache.commons.lang3.text.StrMatcher;
import org.erasmusmc.jerboa.Jerboa;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;

import javax.swing.JFrame;
import javax.swing.ProgressMonitor;

import org.erasmusmc.jerboa.config.DataDefinition;
import org.erasmusmc.jerboa.dataClasses.Event;
import org.erasmusmc.jerboa.dataClasses.Measurement;
import org.erasmusmc.jerboa.dataClasses.Patient;
import org.erasmusmc.jerboa.dataClasses.Prescription;
import org.erasmusmc.jerboa.gui.JerboaGUI;
import org.erasmusmc.jerboa.modules.viewers.PatientViewer;

/**
 * This class contains a collection of methods used for patient object manipulation.
 * It implies that the patient objects were already created from the input dataset.
 *
 * @author MG
 *
 */
public class PatientUtilities {

	//will contain the patient objects
	public List<Patient> patients;
	public List<String> patientFiles;

	//user feedback
	public boolean withProgress;
	public ProgressMonitor progress;

	//CONSTRUCTORS
	/**
	 * Basic constructor.
	 */
	public PatientUtilities(){
		super();
		patients = new ArrayList<Patient>();
		patientFiles = new ArrayList<String>();
		withProgress = false;
	}

	/**
	 * Constructor accepting a path to the location of the patient object files.
	 * @param fileLocation - the location of the patient object files
	 */
	public PatientUtilities(String fileLocation){
		super();
		patients = new ArrayList<Patient>();
		patientFiles = new ArrayList<String>();
		InputFileUtilities.loadAllCodeLists();
		getPatientFileNames(fileLocation);
		withProgress = false;
	}

	/**
	 * Loads patient objects from a file passed as parameter. It makes use of patient object files that
	 * were generated during the input data compression step in the last run.
	 * @param fileName - the path to the file containing patient objects.
	 * @param withEvents - true if the patient should be loaded with its events; false otherwise
	 * @param withPrescriptions - true if the patient should be loaded with its prescriptions; false otherwise
	 * @param withMeasurements - true if the patient should be loaded with its measurements; false otherwise
	 * @return - a list of the patients found in fileName.
	 */
	public List<Patient> loadPatientsFromFile(String fileName, boolean withEvents, boolean withPrescriptions, boolean withMeasurements){

		patients = new ArrayList<Patient>();
		if (fileName != null && !fileName.equals("")){

			try{

				//initialize reader
				BufferedReader br = FileUtilities.openFile(fileName);

				//read first line and initialize patient object
				String strLine = br.readLine();
				StrMatcher delimiter = StrMatcher.charMatcher(DataDefinition.COMMA_DELIMITER);
				Patient patient = null;

				do{
					//retrieve attributes from input line
					String[] attributes = StringUtilities.splitLine(strLine, delimiter);
					int flag = Integer.valueOf(attributes[attributes.length-1]);

					//see what data there is
					switch (flag){
					case DataDefinition.PATIENT_DETAILS_FLAG :
						if (patient != null)
							patients.add(patient);
						patient = new Patient(attributes);
						break;
					case DataDefinition.EVENT_FLAG :
						if (withEvents){
							Event event = new Event(attributes);
							patient.getEvents().add(event);
						}
						break;
					case DataDefinition.PRESCRIPTION_FLAG :
						if (withPrescriptions){
							Prescription prescription = new Prescription(attributes);
							patient.getPrescriptions().add(prescription);
							patient.getOriginalPrescriptions().add(new Prescription(prescription));
						}
						break;
					case DataDefinition.MEASUREMENT_FLAG :
						if (withMeasurements){
							Measurement measurement = new Measurement(attributes);
							patient.getMeasurements().add(measurement);
						}
						break;
					}

					//update progress and display each 10%
				}while ((strLine = br.readLine()) != null);
				patients.add(patient);

				br.close();
				br = null;

			}catch(IOException e ){
				Logging.add("Unable to read from patient object file "+fileName, Logging.ERROR);
				Jerboa.stop(true);
			}
			return patients;
		}else{
			Logging.add("The patient object file path is invalid", Logging.ERROR);
			Jerboa.stop(true);
			return null;
		}
	}

	/**
	 * Searches and loads (if found) patient data from the file where the patientId is found.
	 * The method searches through the patient object files in the fromFolder folder
	 * and returns the complete patient object list found in that file.
	 * This implies that the input data was at least checked through the API and patient objects created.
	 * @param patientId - the ID of the patient to search for
	 * @param fromFolder - the path of the folder in which the patient object should be located
	 * @param withHistory - true if the patients should be loaded with their history; false otherwise
	 * @return - the list of patients in the file containing the object with patientID; null if patientID not found in fromFolder
	 */
	public List<Patient> loadPatientsFromFolder(String patientId, String fromFolder, boolean withHistory){

		if ((patientId != null && !patientId.equals("")) &&
				(fromFolder != null && !fromFolder.equals(""))){

			File f = new File( fromFolder.replace("\\","/") );
			File[] files = f.listFiles();
			StrMatcher delimiter = StrMatcher.charMatcher(DataDefinition.COMMA_DELIMITER);

			if (files != null && files.length > 0){

				//progress related
				if (withProgress){
					JFrame frame = JerboaGUI.frame != null ? JerboaGUI.frame :
						PatientViewer.frame != null ? PatientViewer.frame : null;
					progress = new ProgressMonitor(frame,
							"Searching patient..", "",0 , files.length);
				}

				for (int i = 0; i < files.length; i++){

					//check if not a folder
					if( !files[i].isDirectory()){
						String path = files[i].getAbsolutePath().replace("\\","/");
						try{
							//open the file
							BufferedReader br  = new BufferedReader(new InputStreamReader(
									new FileInputStream(path)));

							String strLine = "";
							while ((strLine = br.readLine()) != null){
								//check if on line with patient details
								String[] columns = StringUtilities.splitLine(strLine, delimiter);
								if (Integer.valueOf(columns[columns.length-1]) == DataDefinition.PATIENT_DETAILS_FLAG){
									//check if we found the patient
									if (patientId.equals(columns[1])){
										//close file and update progress
										br.close();
										//close progress
										if (withProgress){
											progress.setProgress(files.length);
											//and return the patient list
											progress.close();
										}
										return loadPatientsFromFile(path, withHistory, withHistory, withHistory);
									}
								}
							}
							//close the file - we are done with it
							br.close();

						}catch(IOException e){
							Logging.add("Unable to open the "+path+" file.", Logging.ERROR);
							return null;
						}
					}

					//update progress and
					if (withProgress)
						progress.setProgress(i);
				}
				if (withProgress)
					progress.close();
			}else{
				Logging.add("No files found in folder "+fromFolder, Logging.ERROR);
				return null;
			}
		}

		return null;
	}

	/**
	 * Searches and loads (if found) the data of the patient with the ID patientId from the folder fromFolder.
	 * The method searches through the patient object files in the fromFolder folder
	 * and returns the complete data of the patient with ID patientId if found in fromFolder.
	 * This implies that the input data was at least checked through the API and patient objects created.
	 * @param patientId - the ID of the patient to search for
	 * @param fromFolder - the path of the folder in which the patient object should be located.
	 * @return - the patient object if the patient with ID patientId was found; null otherwise.
	 */
	public Patient loadPatient(String patientId, String fromFolder){

		if ((patientId != null && !patientId.equals("")) &&
				(fromFolder != null && !fromFolder.equals(""))){
			InputFileUtilities.loadAllCodeLists();
			File f = new File( fromFolder.replace("\\","/") );
			File[] files = f.listFiles();
			StrMatcher delimiter = StrMatcher.charMatcher(DataDefinition.COMMA_DELIMITER);
			if (files != null && files.length > 0){

				//progress related
				if (withProgress){
					JFrame frame = JerboaGUI.frame != null ? JerboaGUI.frame :
						PatientViewer.frame != null ? PatientViewer.frame : null;
						progress = new ProgressMonitor(frame,
								"Searching patient..", "",0 , files.length);
				}

				for (int i = 0; i < files.length; i++){

					//check if not a folder
					if( !files[i].isDirectory()){
						String path = files[i].getAbsolutePath().replace("\\","/");
						try{
							//open the file
							BufferedReader br  = new BufferedReader(new InputStreamReader(
									new FileInputStream(path)));

							String strLine = "";
							while ((strLine = br.readLine()) != null){
								//check if on line with patient details
								String[] attributes = StringUtilities.splitLine(strLine, delimiter);
								if (Integer.valueOf(attributes[attributes.length-1]) == DataDefinition.PATIENT_DETAILS_FLAG){
									//check if we found the patient
									if (patientId.equals(attributes[1])){
										if (withProgress)
											progress.setProgress(files.length);
										//populate patient details
										Patient patient = new Patient(attributes);
										//read new line from file
										strLine = br.readLine();
										if (strLine != null){

											do {
												attributes = StringUtilities.splitLine(strLine, delimiter);
												if (patientId.equals(attributes[1])){
													int flag = Integer.valueOf(attributes[attributes.length-1]);
													switch (flag){

													case DataDefinition.EVENT_FLAG :
														Event event = new Event(attributes);
														patient.getEvents().add(event);
														break;
													case DataDefinition.PRESCRIPTION_FLAG :
														Prescription prescription = new Prescription(attributes);
														patient.getPrescriptions().add(prescription);
														break;
													case DataDefinition.MEASUREMENT_FLAG :
														Measurement measurement = new Measurement(attributes);
														patient.getMeasurements().add(measurement);
														break;
													}
													strLine = br.readLine();
												}else{
													strLine = null;
												}
											}while (strLine != null);

											//close file
											br.close();
										}
										if (withProgress)
											progress.close();
										return patient;
									}
								}else{
									continue;
								}
								//close the file - we are done with it
							}
							br.close();
						}catch(IOException e){
							Logging.add("Unable to open the "+path+" file.", Logging.ERROR);
							return null;
						}
					}

					//update progress
					if (withProgress)
						progress.setProgress(i);
				}
				if (withProgress)
					progress.close();
			}else{
				Logging.add("No files found in folder "+fromFolder, Logging.ERROR);
				return null;
			}
		}

		return null;
	}

	/**
	 * Retrieves the files containing patient objects from a specified path.
	 * It populates a list with the names of the files containing patient
	 * objects present in the folder located at path.
	 * @param path - the folder in which to search patient object files
	 */
	public void getPatientFileNames(String path){

		if (path != null && !path.equals("")){
			if (new File(path).isDirectory()){
				File f = new File( path.replace("\\","/") );
				File[] currentFiles = f.listFiles();
				if (currentFiles != null && currentFiles.length > 0)
					for (int i = 0; i < currentFiles.length; i++)
						//check if not a folder
						if( !currentFiles[i].isDirectory()){
							if (FilenameUtils.getExtension(currentFiles[i].getName()).
									equals(DataDefinition.PATIENT_OBJECT_FILE_EXTENSION.replace(".","")))
								patientFiles.add(currentFiles[i].getAbsolutePath().replace("\\","/"));
						}else{
							getPatientFileNames(currentFiles[i].getAbsolutePath());
						}
				if (patientFiles == null || patientFiles.size() == 0){
					Logging.add("The folder does not contain any patient object files.", Logging.ERROR);
				}
			}
		}else{
			Logging.add("Invalid patient object file path.", Logging.ERROR);
		}
	}

}
