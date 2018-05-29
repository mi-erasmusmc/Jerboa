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
 * $Rev:: 4766              $:  Revision of last commit                                   *
 * $Author:: root           $:  Author of last commit                                     *
 * $Date:: 					$:  Date and time (CET) of last commit						  *
 ******************************************************************************************/

package org.erasmusmc.jerboa.engine;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Hashtable;
import java.util.List;

import org.apache.commons.lang3.text.StrBuilder;
import org.erasmusmc.jerboa.Jerboa;
import org.erasmusmc.jerboa.dataClasses.Episode;
import org.erasmusmc.jerboa.dataClasses.Event;
import org.erasmusmc.jerboa.dataClasses.Measurement;
import org.erasmusmc.jerboa.dataClasses.Patient;
import org.erasmusmc.jerboa.dataClasses.Prescription;
import org.erasmusmc.jerboa.utilities.DateUtilities;
import org.erasmusmc.jerboa.utilities.FileUtilities;
import org.erasmusmc.jerboa.utilities.Logging;
import org.erasmusmc.jerboa.utilities.InputFileUtilities;
import org.erasmusmc.jerboa.utilities.Progress;
import org.erasmusmc.jerboa.utilities.StringUtilities;
import org.erasmusmc.jerboa.utilities.stats.*;
import org.erasmusmc.jerboa.utilities.Timer;
import org.erasmusmc.jerboa.config.DataDefinition;
import org.erasmusmc.jerboa.config.FilePaths;
import org.erasmusmc.jerboa.config.PropertiesManager;
import org.erasmusmc.jerboa.config.Parameters;

/**
 * This class contains all the methods specific to loading the data from the different input files and populating patient objects
 * integrating all the history (e.g., events, prescriptions, measurements) of a patient. It contains two sets of methods
 * which can be applied either on sorted or unsorted input data. It will output the patient objects under a standardized format.
 * Note that the behavior is different if the input data is sorted or unsorted.
 *
 * @author MG
 *
 */
public class PatientObjectCreator {

	private InputFileSet inputSet;
	private boolean filesSorted;

	//used for a list of patients
	private Hashtable<String, Integer> hashTableID;
	private List<Patient> patients;
	private int patientsProcessed;

	//readers
	private BufferedReader patientReader;
	private BufferedReader eventReader;
	private BufferedReader prescriptionReader;
	private BufferedReader measurementReader;

	//EOF flags
	private  boolean eofEvents;
	private  boolean eofPrescriptions;
	private  boolean eofMeasurements;

	//output related
	private Hashtable<String, Integer> outputIndexes;
	private String outputFile;
	private boolean hasSubsetID;

	private int nbErrors;
	private List<String> errors;

	//progress related
	private Timer timer;
	private Progress progress;
	private boolean showExecutionTime;

	private Stats stats;

	//CONSTRUCTORS
	/**
	 * Constructor accepting an input file set and a splitter in case the files are not sorted.
	 * @param inputSet - the set of input files containing the patient details and history
	 * @param filesSorted - true if the input files are sorted; false otherwise
	 * @param splitter - the splitter object that will perform the actual splitting
	 */
	public PatientObjectCreator(InputFileSet inputSet, boolean filesSorted, InputFileSplitter splitter){

		this.timer = new Timer();
		this.progress = new Progress();
		this.filesSorted = filesSorted;
		this.inputSet = inputSet;
		this.stats = new Stats();
		this.showExecutionTime = false;
		this.outputIndexes = new Hashtable<String, Integer>();
		this.outputIndexes.put("1", 1);

		if (this.filesSorted)
			PatientObjectCreatorSorted();
		else
			PatientObjectCreatorUnsorted(splitter);

		FileUtilities.logPOFSize();
		this.progress.close();
	}

	/**
	 * Basic constructor used for debugging purposes.
	 */
	public PatientObjectCreator(){
		this.stats = new Stats();
		this.filesSorted = true;
		this.inputSet = null;
		this.outputIndexes = new Hashtable<String, Integer>();
		this.outputIndexes.put("1", 1);
		(new PropertiesManager()).ReadProperties();
	}

	/*------------------METHODS USED ON SORTED FILES--------------------*/

	/**
	 * Creates patient object from the sorted input files.
	 */
	public void PatientObjectCreatorSorted(){

		progress.init(Stats.nbPatients, "Compressing data");
		timer.start();

		resetCountersAndFlags();
		hasSubsetID = checkForSubsetId();
		patients = new ArrayList<Patient>();

		InputFileUtilities.createPOCFolderStructure(null);

		//open input files for reading
		if (inputSet != null && inputSet.getSelectedFiles().size() > 0){
			try{
				InputFile inputFile;
				for (byte i = 0; i < inputSet.getSelectedFiles().size(); i++ ){
					if ((inputFile = inputSet.getSelectedFiles().get(i)) != null){
						if (!inputFile.isEmpty()){
							byte type = inputFile.getType();
							switch (type){
							case DataDefinition.PATIENTS_FILE:
								patientReader = FileUtilities.openFile(inputFile.getName());
								patientReader.readLine(); //lose the header
								break;
							case DataDefinition.EVENTS_FILE:
								eventReader = FileUtilities.openFile(inputFile.getName());
								eventReader.readLine();//lose the header
								break;
							case DataDefinition.PRESCRIPTIONS_FILE:
								prescriptionReader = FileUtilities.openFile(inputFile.getName());
								prescriptionReader.readLine();//lose the header
								break;
							case DataDefinition.MEASUREMENTS_FILE:
								measurementReader = FileUtilities.openFile(inputFile.getName());
								measurementReader.readLine();
								break;
							}
						}
					}
				}

				loadPatients();

			}catch(IOException e){
				Logging.add("Unable to load patient objects", Logging.ERROR);
			}

			InputFileUtilities.outputLookUpTables(inputSet, true);
			//reload look-ups without quotes
			InputFileUtilities.loadAllCodeLists();

			displayStats();
		}

		if (showExecutionTime)
			timer.stopAndDisplay(Stats.nbPatients+" patients");
	}

	/**
	 * Loads patient objects from the input files.
	 * The files being sorted, allow sequential reading
	 * of each patient and population of its history.
	 * @throws IOException if not able to read from input files.
	 */
	public void loadPatients() throws IOException{

		if (patientReader != null){
			String strLine = "";
			Patient patient = null;
			Event event = null;
			Prescription prescription = null;
			Measurement measurement = null;

			int line = 0;

			//to check for duplicates
			Patient reference = null;

			//go through file
			while ((strLine = patientReader.readLine()) != null){
				String[] attributes = StringUtilities.splitLine(strLine,
						inputSet.getPatientsFile().getDelimiter());

				//initialize and populate a patient object with patient details
				patient =  new Patient(attributes, inputSet.getPatientsFile());
				stats.countActivePatients(patient);
				//check for duplicate IDs
				if ((reference != null) && patient.ID.compareTo(reference.ID) == 0 &&
						patient.subset.equals(reference.subset)){
					Logging.add("Duplicate patient ID on line "+line, Logging.ERROR);
					Jerboa.stop(true);
					break;
				}
				reference = patient;

				//load history
				event = loadPatientEvents(patient, event);
				prescription = loadPatientPrescriptions(patient, prescription);
				measurement = loadPatientMeasurements(patient, measurement);

				Stats.updatePatientStats(patient);
				checkPatient(patient);
				addPatientToOutput(patient);

				progress.update();

			}//end while

			//tidy up a bit
			closeReadersAndCheckForUnassigned();
			flushAndCloseOutputBuffers();

		}
	}

	/**
	 * Loads the events for the patient passed as parameter from the input events file.
	 * @param patient - the patient for which the events are to be loaded
	 * @param event - an event to be added to the patient object
	 * @throws IOException - if unable to read from input file
	 */
	private Event loadPatientEvents(Patient patient, Event event) throws IOException{

		if (inputSet.gotEvents && !eofEvents && eventReader != null){
			String strLine = "";
			boolean isAssigned = false;
			do {
				if (event != null){
					//same ID
					if (isAssigned || (event.patientID.equals(patient.ID) &&
							(hasSubsetID ? event.subset.equals(patient.subset) : true))){
						patient.getEvents().add(event);
						Stats.updateEventStats(event);
					//check if reached a superior patient ID and event was unassigned
					}else if (checkIfUnassigned(patient.ID, event.patientID) &&
							(hasSubsetID ? (event.subset.equals(patient.subset) ||
									compareIDS(event.subset, patient.subset) > 0) : true)){
						addUnassigned(event.toStringConvertedDate());
						Stats.nbUnassignedEvents ++;
					//or not yet reached the right patient id
					}else{
						break;
					}
				}

				//read an event
				strLine = eventReader.readLine();
				if (strLine != null && !strLine.equals("")){
					//populate event object
					String[] attributes = StringUtilities.splitLine(strLine,
							inputSet.getEventsFile().getDelimiter());
					event = new Event(attributes, inputSet.getEventsFile());
					//reached the end of the file
				}else{
					eofEvents = true;
					break;
				}
				//continue until prescription is assigned or the patient id in prescription
				//is inferior to the current patient ID
			} while ((isAssigned = event.patientID.equals(patient.ID) &&
					(hasSubsetID ? event.subset.equals(patient.subset) : true)) ||
					compareIDS(event.patientID, patient.ID) < 0);
		}

		return event;
	}

	/**
	 * Loads the prescriptions for the patient passed as parameter from the input prescriptions file.
	 * @param patient - the patient for which the prescriptions are to be loaded
	 * @param prescription - a prescription to be added to the patient object
	 * @throws IOException - if unable to read from input file
	 */
	private Prescription loadPatientPrescriptions(Patient patient, Prescription prescription) throws IOException{

		if (inputSet.gotPrescriptions && !eofPrescriptions && prescriptionReader != null){
			String strLine = "";
			boolean isAssigned = false;

			do {
				if (prescription != null){
					//same ID
					if (isAssigned || (prescription.patientID.equals(patient.ID) &&
							(hasSubsetID ? prescription.subset.equals(patient.subset) : true))){
						patient.getPrescriptions().add(prescription);
						// Add a copy of the prescription to the original prescriptions
						patient.getOriginalPrescriptions().add(new Prescription(prescription));
						Stats.updatePrescriptionStats(prescription);
						//check if reached a superior patient ID and prescription was unassigned
					}else if (checkIfUnassigned(patient.ID, prescription.patientID) &&
							(hasSubsetID ? (prescription.subset.equals(patient.subset) ||
									compareIDS(prescription.subset,patient.subset) > 0) : true)){
						addUnassigned(prescription.toStringConvertedDate());
						Stats.nbUnassignedPrescriptions ++;
						//or not yet reached the right patient id
					}else{
						break;
					}
				}
				//read a prescription
				strLine = prescriptionReader.readLine();
				if (strLine != null && !strLine.equals("")){
					//populate prescription object
					String[] attributes = StringUtilities.splitLine(strLine,
							inputSet.getPrescriptionsFile().getDelimiter());
					prescription  = new Prescription(attributes, inputSet.getPrescriptionsFile());
					//reached end of file
				}else{
					eofPrescriptions = true;
					break;
				}
				//continue until prescription is assigned or the patient id in prescription
				//is inferior to the current patient ID
			} while ((isAssigned = prescription.patientID.equals(patient.ID) &&
					(hasSubsetID ? prescription.subset.equals(patient.subset) : true)) ||
					compareIDS(prescription.patientID, patient.ID) < 0);
		}

		return prescription;
	}

	/**
	 * Loads the measurements for the patient passed as parameter from the input measurements file.
	 * @param patient - the patient for which the measurements are to be loaded
	 * @param measurement - a measurement to be added to the patient object
	 * @throws IOException - if unable to read from input file
	 */
	private Measurement loadPatientMeasurements(Patient patient, Measurement measurement) throws IOException{

		if (inputSet.gotMeasurements && !eofMeasurements && measurementReader != null){
			String strLine = "";
			boolean isAssigned = false;

			do {
				if (measurement != null){
					//same ID
					if (isAssigned || (measurement.patientID.equals(patient.ID) &&
							(hasSubsetID ? measurement.subset.equals(patient.subset) : true))){
						patient.getMeasurements().add(measurement);
						Stats.updateMeasurementStats(measurement);
						//check if reached a superior patient ID and prescription was unassigned
					}else if (checkIfUnassigned(patient.ID, measurement.patientID) &&
							(hasSubsetID ? (measurement.subset.equals(patient.subset) ||
									compareIDS(measurement.subset, patient.subset) > 0) : true)){
						addUnassigned(measurement.toStringConvertedDate());
						Stats.nbUnassignedMeasurements ++;
						//or not yet reached the right patient id
					}else{
						break;
					}
				}
				//read a measurement
				strLine = measurementReader.readLine();
				if (strLine != null && !strLine.equals("")){
					//populate the measurement object
					String[] attributes = StringUtilities.splitLine(strLine,
							inputSet.getMeasurementsFile().getDelimiter());
					measurement = new Measurement(attributes, inputSet.getMeasurementsFile());

					//reached end of file
				}else{
					eofMeasurements = true;
					break;
				}
				//continue until prescription is assigned or the patient id in prescription
				//is inferior to the current patient ID
			} while ((isAssigned = measurement.patientID.equals(patient.ID) &&
					(hasSubsetID ? measurement.subset.equals(patient.subset) : true)) ||
					compareIDS(measurement.patientID, patient.ID) < 0);
		}

		return measurement;
	}

	/*--------------END OF METHODS USED ON SORTED FILES-----------------*/


	/*------------------METHODS USED ON UNSORTED FILES------------------*/

	//CONSTRUCTOR USED FOR UNSORTED FILES
	/**
	 * Creates patient objects from unsorted input files.
	 * @param splitter - the splitter to be used to create smaller subsets from the input data
	 */
	public void PatientObjectCreatorUnsorted(InputFileSplitter splitter){

		//initialize/reset
		resetCountersAndFlags();
		hasSubsetID = checkForSubsetId();

		InputFileUtilities.createPOCFolderStructure(splitter);

		progress.init(inputSet.getTotalSize(), "Compressing data");
		timer.start();

		if (splitter.subsetNames != null && splitter.subsetNames.size() > 0){

			//loop through folders
			for (int fromFolder : splitter.subsetNames){
				patients = new ArrayList<Patient>();
				//start loading patients depending on the input data
				if (loadPatientSubset(fromFolder)){
					if (inputSet.gotEvents && !inputSet.getEventsFile().isEmpty())
						loadEventsSubset(fromFolder);
					if (inputSet.gotPrescriptions && !inputSet.getPrescriptionsFile().isEmpty())
						loadPrescriptionsSubset(fromFolder);
					if (inputSet.gotMeasurements && !inputSet.getMeasurementsFile().isEmpty())
						loadMeasurementsSubset(fromFolder);

					//check patient object coherence and output
					for (Patient p : patients){
						checkPatient(p);
						addPatientToOutput(p);
					}
				}

				InputFileUtilities.deleteSubset(fromFolder);
			}

			InputFileUtilities.outputLookUpTables(inputSet, true);
			//reload look-ups without quotes
			InputFileUtilities.loadAllCodeLists();
			InputFileUtilities.deleteSplitFolder();
			flushAndCloseOutputBuffers();

			displayStats();

		}else{
			Logging.add("The set of split folder names is empty or null", Logging.ERROR, true);
		}

		Logging.addNewLine();
		timer.stopAndDisplay(Stats.nbPatients+" patients");

	}

	/**
	 * Loads the patient details from a specified folder and
	 * creates a hash table of the ID's to ease up the retrieving of
	 * the patient history. Note that this method assumes the input files were split into subsets.
	 * Generates an error if the population file in the specified folder was not processed correctly.
	 * @param fromFolder - the folder from which patient data is to be loaded
	 * @return - true if the loading of patient details from the specified folder was successfully performed; false otherwise
	 */
	private boolean loadPatientSubset(int fromFolder){

		try{
			patientReader  = new BufferedReader(new InputStreamReader(
					new FileInputStream(FilePaths.SPLIT_PATH+fromFolder+"/"+FilePaths.FILE_PATIENTS)));

			String strLine = "";
			Patient patient;

			hashTableID = new Hashtable<String, Integer>();
			int line = 0;

			while ((strLine = patientReader.readLine()) != null){
				String[] attributes = StringUtilities.splitLine(strLine,
						inputSet.getPatientsFile().getDelimiter());

				progress.update(strLine.length());

				//populate the hash table with the patient ID
				String hashID = hasSubsetID ? attributes[inputSet.getPatientsFile().getSubsetIndex()]+"_"+
						attributes[inputSet.getPatientsFile().getPatientIDIndex()]:
							attributes[inputSet.getPatientsFile().getPatientIDIndex()];
				hashTableID.put(hashID , line++);
				//initialize and populate a patient object with patient details
				patient =  new Patient(attributes, inputSet.getPatientsFile());
				stats.countActivePatients(patient);

				Stats.updatePatientStats(patient);
				patients.add(hashTableID.get(hashID),patient);
			}

			//check for duplicate IDs
			if (patients != null && patients.size() > 1){
				Patient reference = patients.get(patients.size() -1);
				for (Patient p : patients){
					if (p.ID.compareTo(reference.ID) == 0 &&
							p.subset.equals(reference.subset)){
						Logging.add("Duplicate patient ID "+p.ID+". Please check the population file.",Logging.ERROR);
						Jerboa.stop(true);
						break;
					}
					reference = p;
				}
			}

			FileUtilities.closeBuffer(patientReader);

			return true;

		}catch(IOException e){
			Logging.add("Hashing patient IDs", Logging.ERROR, true);
			return false;
		}
	}

	/**
	 * Loads the events for each patient from a specified folder.
	 * Note that this method assumes the input files were split into subsets.
	 * Generates an error if the events file in the specified folder was not processed correctly.
	 * @param fromFolder - the folder from which patient data is to be loaded
	 */
	private void loadEventsSubset(int fromFolder){

		try{
			eventReader  = new BufferedReader(new InputStreamReader(
					new FileInputStream(FilePaths.SPLIT_PATH+fromFolder+"/"+FilePaths.FILE_EVENTS)));

			String strLine = "";
			Event event;

			while ((strLine = eventReader.readLine()) != null){
				String[] attributes = StringUtilities.splitLine(strLine,
						inputSet.getEventsFile().getDelimiter());

				progress.update(strLine.length());

				//check if event is assigned to a patient in the sub set
				Integer index = null;
				if (inputSet.getEventsFile() != null && inputSet.getEventsFile().getDataOrder() != null){
					index = hashTableID.get(hasSubsetID ? attributes[inputSet.getEventsFile().getSubsetIndex()]+"_"+
							attributes[inputSet.getEventsFile().getPatientIDIndex()]
									: attributes[inputSet.getEventsFile().getPatientIDIndex()]);
				}
				if (index != null){
					event = new Event(attributes, inputSet.getEventsFile());
					//add the event to the rightful owner
					patients.get(index).getEvents().add(event);
					Stats.updateEventStats(event);
				}else{
					addUnassigned(strLine);
					Stats.nbUnassignedEvents ++;
				}
			}

			FileUtilities.closeBuffer(eventReader);

		}catch(IOException e){
			Logging.add("Error retrieving events for patients from folder "+fromFolder, Logging.ERROR);
		}
	}

	/**
	 * Loads the prescriptions for each patient from a specified folder.
	 * Note that this method assumes the input files were split into subsets.
	 * Generates an error if the prescriptions file in the specified folder was not processed correctly.
	 * @param fromFolder - the folder from which patient data is to be loaded
	 */
	private void loadPrescriptionsSubset(int fromFolder){

		try{
			prescriptionReader  = new BufferedReader(new InputStreamReader(
					new FileInputStream(FilePaths.SPLIT_PATH+fromFolder+"/"+FilePaths.FILE_PRESCRIPTIONS)));

			String strLine = "";
			Prescription prescription;

			while ((strLine = prescriptionReader.readLine()) != null){
				String[] attributes = StringUtilities.splitLine(strLine,
						inputSet.getPrescriptionsFile().getDelimiter());

				progress.update(strLine.length());

				//check if the prescription is assigned to a patient from the sub set
				Integer index = null;
				if (inputSet.getPrescriptionsFile() != null && inputSet.getPrescriptionsFile().getDataOrder() != null){
					index = hashTableID.get(hasSubsetID ? attributes[inputSet.getPrescriptionsFile().getSubsetIndex()]+"_"+
							attributes[inputSet.getPrescriptionsFile().getPatientIDIndex()]
									: attributes[inputSet.getPrescriptionsFile().getPatientIDIndex()]);
				}
				if (index != null){
					prescription = new Prescription(attributes, inputSet.getPrescriptionsFile());
					//add the prescription to the rightful owner
					patients.get(index).getPrescriptions().add(prescription);
					Stats.updatePrescriptionStats(prescription);
				}else{
					addUnassigned(strLine);
					Stats.nbUnassignedPrescriptions ++;
				}
			}

			FileUtilities.closeBuffer(prescriptionReader);

		}catch(IOException e){
			Logging.add("Error retrieving prescriptions for patients from folder "+fromFolder, Logging.ERROR);
		}
	}

	/**
	 * Loads the measurements for each patient from a specified folder.
	 * Note that this method assumes the input files were split into subsets.
	 * Generates an error if the measurements file in the specified folder was not processed correctly.
	 * @param fromFolder - the folder from which patient data is to be loaded
	 */
	private void loadMeasurementsSubset(int fromFolder){

		try{
			measurementReader  = new BufferedReader(new InputStreamReader(
					new FileInputStream(FilePaths.SPLIT_PATH+fromFolder+"/"+FilePaths.FILE_MEASUREMENTS)));

			String strLine = "";
			Measurement measurement;

			while ((strLine = measurementReader.readLine()) != null){
				String[] attributes = StringUtilities.splitLine(strLine,
						inputSet.getMeasurementsFile().getDelimiter());

				progress.update(strLine.length());

				//check if the measurement is assigned to a patient from the sub set
				Integer index = null;
				if (inputSet.getMeasurementsFile() != null && inputSet.getMeasurementsFile().getDataOrder() != null){
					index = hashTableID.get(hasSubsetID ? attributes[inputSet.getMeasurementsFile().getSubsetIndex()]+"_"+
							attributes[inputSet.getMeasurementsFile().getPatientIDIndex()]
									: attributes[inputSet.getMeasurementsFile().getPatientIDIndex()]);
				}
				if (index != null){
					measurement = new Measurement(attributes, inputSet.getMeasurementsFile());
					//add the measurement to the rightful owner
					patients.get(index).getMeasurements().add(measurement);
					Stats.updateMeasurementStats(measurement);
				}else{
					addUnassigned(strLine);
					Stats.nbUnassignedMeasurements ++;
				}
			}

			FileUtilities.closeBuffer(measurementReader);

		}catch(IOException e){
			Logging.add("Error retrieving measurements for patients from folder "+fromFolder, Logging.ERROR);
		}
	}

	/*-------------END OF METHODS USED ON UNSORTED FILES----------------*/

	/*------------- CREATE PATIENT OBJECTS FOR DEBUG MODE--------------*/

	/**
	 * Creates patient objects from the input files passed as parameters and returns the newly created
	 * patient objects as a list. At least patientsFile has to be non null.
	 * @param patientsFile - the path to the population file
	 * @param eventsFile - the path to the events file
	 * @param prescriptionsFile - the path to the prescription file
	 * @param measurementsFile - the path to the measurement file
	 * @param sortFiles - true if the files are to be sorted; false otherwise
	 * @return - a list of patient objects
	 */
	public List<Patient> createPatients(String patientsFile, String eventsFile, String prescriptionsFile, String measurementsFile, boolean sortFiles){

		//sort available input files and add them to the list
		List<String> files = new ArrayList<String>();
		if (patientsFile != null && !patientsFile.equals("")){
			String sortedFile = sortFiles ? FileUtilities.sortFile(patientsFile) : patientsFile;
			files.add(sortedFile);

			if (eventsFile != null && !eventsFile.equals("")){
				sortedFile = sortFiles ? FileUtilities.sortFile(eventsFile) : eventsFile;
				files.add(sortedFile);
			}
			if (prescriptionsFile != null && !prescriptionsFile.equals("")){
				sortedFile = sortFiles ? FileUtilities.sortFile(prescriptionsFile) : prescriptionsFile;
				files.add(sortedFile);
			}
			if (measurementsFile != null && !measurementsFile.equals("")){
				sortedFile = sortFiles ? FileUtilities.sortFile(measurementsFile) : measurementsFile;
				files.add(sortedFile);
			}

			try{
				inputSet = new InputFileSet(files);
				filesSorted = true;

				//open files for reading and set date format to YYYYMMDD
				if (inputSet != null && inputSet.getSelectedFiles() != null){
					if (inputSet.getPatientsFile() != null){
						patientReader = FileUtilities.openFile(inputSet.getPatientsFile().getName());
						patientReader.readLine(); //lose the header
					}else{
						System.out.println("Invalid population file");
						return null;
					}
					if (inputSet.getEventsFile() != null){
						eventReader = FileUtilities.openFile(inputSet.getEventsFile().getName());
						eventReader.readLine(); //lose the header
					}
					if (inputSet.getPrescriptionsFile() != null){
						prescriptionReader = FileUtilities.openFile(inputSet.getPrescriptionsFile().getName());
						prescriptionReader.readLine(); //lose the header
					}
					if (inputSet.getMeasurementsFile() != null){
						measurementReader = FileUtilities.openFile(inputSet.getMeasurementsFile().getName());
						measurementReader.readLine(); //lose the header
					}
				}

				InputFileUtilities.getDateFormatInDebug(inputSet);

				patients = new ArrayList<Patient>();
				stats = new Stats();

				Patient patient = null;
				Event event = null;
				Prescription prescription = null;
				Measurement measurement = null;

				String strLine = "";
				int line = 0;

				//to check for duplicates
				Patient reference = null;

				while ((strLine = patientReader.readLine()) != null){
					String[] attributes = StringUtilities.splitLine(strLine,
							inputSet.getPatientsFile().getDelimiter());

					patient =  new Patient(attributes, inputSet.getPatientsFile());
					stats.countActivePatients(patient);

					//check for duplicate IDs
					if (line > 0){
						if (patient.ID.compareTo(reference.ID) == 0 &&
								patient.subset.equals(reference.subset)){
							System.out.println("Duplicate patient ID on line "+line);
							return null;
						}
					}
					reference = patient;

					//load patient history
					event = loadPatientEvents(patient, event);
					prescription = loadPatientPrescriptions(patient, prescription);
					measurement = loadPatientMeasurements(patient, measurement);
					patients.add(patient);

					Stats.updatePatientStats(patient);
					line ++;

					//protection for OOM exception //now set for 100K patients
					//to be lowered if needed
					if (patients.size() >= 100000)
						return patients;

				}//end while

				return patients;
			}catch(IOException io){
				System.out.println("An error occurred while reading input files.");
				return null;
			}

		}else{
			System.out.println("Invalid population file path");
			return null;
		}
	}

	/**
	 * Calls the createPatients method with the flag for sorted files set to true.
	 * It is used as a shortcut to function call.
	 * @param patientsFile - the path to the population file
	 * @param eventsFile - the path to the events file
	 * @param prescriptionsFile - the path to the prescription file
	 * @param measurementsFile - the path to the measurement file
	 * @throws IOException - if files could not be read
	 * @return - a list of patient objects loaded from files
	 */
	public List<Patient> createPatients(String patientsFile, String eventsFile, String prescriptionsFile, String measurementsFile) throws IOException{
		return createPatients(patientsFile, eventsFile, prescriptionsFile, measurementsFile, true);
	}


	//MAIN METHOD FOR TESTING AND DEBUGGING PURPOSES
	public static void main (String[] args){
		PatientObjectCreator poc = new PatientObjectCreator();
		String patientsFile = "D:/Work/TestData/test/Patients.txt";
		String eventsFile = "D:/Work/TestData/test/Events.txt";
		String prescriptionsFile = "D:/Work/TestData/test/Prescriptions.txt";
		String measurementsFile = "D:/Work/TestData/test/Measurements.txt";

		//if sorted needed
		FileUtilities.sortFile(patientsFile);
		patientsFile = "D:/Work/TestData/test/Patients_sorted.txt";

		FileUtilities.sortFile(eventsFile);
		eventsFile = "D:/Work/TestData/test/Events_sorted.txt";

		FileUtilities.sortFile(prescriptionsFile);
		prescriptionsFile = "D:/Work/TestData/test/Prescriptions_sorted.txt";

		FileUtilities.sortFile(measurementsFile);
		measurementsFile = "D:/Work/TestData/test/Measurements_sorted.txt";

		List<Patient> patients = poc.createPatients(patientsFile, eventsFile, prescriptionsFile, measurementsFile, false);
		if (patients != null && patients.size() > 0)
			System.out.println("\n");
		for (Patient p : patients)
			System.out.println(p.toStringDetails());
	}

	/*-------------CHECKING PATIENT DATA COHERENCE---------------*/

	/**
	 * Checks the coherence of the patient history based on the startDate and endDate of the patient.
	 * It sorts the episodes of  patient and checks if they occur between the startDate and the endDate
	 * of the patient.
	 * @param patient - the patient object to be checked for coherence
	 */
	public void checkPatient(Patient patient){

		if (patient != null){

			//retrieve patient data and sort it
			List<Event> events = patient.getEvents();
			Collections.sort(events);
			patient.setEvents(events);
			List<Prescription> prescriptions = patient.getPrescriptions();
			Collections.sort(prescriptions);
			patient.setPrescriptions(prescriptions);
			prescriptions = patient.getOriginalPrescriptions();
			Collections.sort(prescriptions);
			patient.setOriginalPrescriptions(prescriptions);
			List<Measurement> measurements = patient.getMeasurements();
			Collections.sort(measurements);
			patient.setMeasurements(measurements);

			//check episodes
			checkDateCoherence(events, patient, "event");
			checkDateCoherence(prescriptions, patient, "prescription");
			checkDateCoherence(measurements, patient, "measurement");
		}
	}

	/**
	 * Checks if the occurrence date of an event, prescription, etc., is between
	 * the start date and the end date of a patient.
	 * @param list - the list of events, prescriptions or measurements of the patient
	 * @param patient - the patient to be checked
	 * @param dataType - the kind of data we are dealing with (e.g., patient, event); used for the error message creation
	 */
	@SuppressWarnings("unchecked")
	private void checkDateCoherence(@SuppressWarnings("rawtypes") List list, Patient patient, String dataType){
		if (nbErrors < Parameters.MAX_ERRORS_INTEGRITY){
			if (list != null && list.size() > 0){
				for (Episode e : (List<Episode>)list){
					if ((e.date - patient.startDate < 0) || (e.date - patient.endDate > 0)){
						//create output file if necessary and add error
						if (Jerboa.getOutputManager().addFile(FilePaths.ERROR_LOG_PATIENT_OBJECTS)){
							Jerboa.getOutputManager().writeln(FilePaths.ERROR_LOG_PATIENT_OBJECTS, "[ID]         Message", false);
							errors = new ArrayList<String>();
							errors.add("[ID]         Message");
						}
						String errorMessage = "["+patient.ID+"]   Warning: "+dataType+" date outside patient time: "+
								Integer.valueOf(DateUtilities.daysToDate(e.date));
						Jerboa.getOutputManager().writeln(FilePaths.ERROR_LOG_PATIENT_OBJECTS, errorMessage, true);
						errors.add(errorMessage);

						nbErrors ++;
						if (nbErrors == Parameters.MAX_ERRORS_INTEGRITY){
							Jerboa.getOutputManager().writeln(FilePaths.ERROR_LOG_PATIENT_OBJECTS, ".....to be continued", true);
							Jerboa.getOutputManager().closeFile(FilePaths.ERROR_LOG_PATIENT_OBJECTS);
							errors.add("....to be continued");
							break;
						}
					}
				}
			}
		}
	}

	/*-------------END OF CHECKING PATIENT DATA COHERENCE ---------------*/

	/**
	 * Adds a patient with all its history to the output buffer.
	 * The patient object files (POF) are restricted to a maximum size defined
	 * in the Parameters class. The eventual subset of the patient is taken into
	 * consideration and will appear in the name of the output file.
	 * Once the maximum size is reached for one file, a new one is
	 * created with an increased index.
	 * @param patient - the patient object to be output
	 */
	private void addPatientToOutput(Patient patient){

		if (patient != null){

			String subset = hasSubsetID ? patient.subset : "1";

			//initialize output index for this subset if necessary
			if (outputIndexes.get(subset) == null)
				outputIndexes.put(subset, 1);

			outputFile = FilePaths.PATIENTS_PATH+subset+"_"+
					"patientObjects_"+outputIndexes.get(subset)+
					DataDefinition.PATIENT_OBJECT_FILE_EXTENSION;

			StrBuilder s = new StrBuilder();

			//see if the first patient in new file
			if (Jerboa.getOutputManager().addFile(outputFile))
				Jerboa.getOutputManager().writeln(outputFile, patient.toStringWithFlag(DataDefinition.PATIENT_DETAILS_FLAG), false);
			else
				s.appendln(patient.toStringWithFlag(DataDefinition.PATIENT_DETAILS_FLAG));

			//get history and append
			if (patient.getEvents()!= null && patient.getEvents().size() > 0)
				for (Event e: patient.getEvents())
					s.appendln(e.toStringWithFlag(DataDefinition.EVENT_FLAG));
			if (patient.getPrescriptions()!= null && patient.getPrescriptions().size() > 0)
				for (Prescription pr: patient.getPrescriptions())
					s.appendln(pr.toStringWithFlag(DataDefinition.PRESCRIPTION_FLAG));
			if (patient.getMeasurements()!= null && patient.getMeasurements().size() > 0)
				for (Measurement m: patient.getMeasurements())
					s.appendln(m.toStringWithFlag(DataDefinition.MEASUREMENT_FLAG));

			Jerboa.getOutputManager().write(outputFile, s.toString(), true);
			patientsProcessed++;

			//check if the POF size limit was not reached
			if ((patientsProcessed % 500 == 0) && (FileUtilities.getFileSizeInMB(outputFile) > Parameters.MAX_POF_SIZE)){
				Jerboa.getOutputManager().closeFile(outputFile);
				outputIndexes.put(subset, (outputIndexes.get(subset))+1);
			}
		}
	}

	/**
	 * Will add record to the output buffer of the unassigned records.
	 * @param record - the line for which no patient ID was found in the population file.
	 */
	private void addUnassigned(String record){

		if (Jerboa.getOutputManager().hasFile(FilePaths.UNASSIGNED_RECORDS)){
			Jerboa.getOutputManager().writeln(FilePaths.UNASSIGNED_RECORDS, record, true);
		}else if (Jerboa.getOutputManager().addFile(FilePaths.UNASSIGNED_RECORDS)){
			//check if the folder for error logs was not already created
			File file = new File(FilePaths.ERRORS_PATH);
			if (!file.exists())
				try {
					FileUtilities.forceMkdir(file);
				} catch (IOException e) {
					Logging.add("Cannot create errors folder", Logging.ERROR);
				}
			String header = "Records for which the patient ID is not present in the population file";
			Jerboa.getOutputManager().writeln(FilePaths.UNASSIGNED_RECORDS, header, false);
			Jerboa.getOutputManager().writeln(FilePaths.UNASSIGNED_RECORDS, record, false);
		}
	}

	/**
	 * Closes all the files that were used during the output of the
	 * patient object. The buffers are flushed before closed internally
	 * in the output manager.
	 */
	private void flushAndCloseOutputBuffers(){
		for (String s : outputIndexes.keySet())
			for (int i = 1; i <= outputIndexes.get(s); i++)
				Jerboa.getOutputManager().closeFile(FilePaths.PATIENTS_PATH+s+"_"+
					"patientObjects_"+i+DataDefinition.PATIENT_OBJECT_FILE_EXTENSION);

		Jerboa.getOutputManager().closeFile(FilePaths.UNASSIGNED_RECORDS);
		Jerboa.getOutputManager().closeFile(FilePaths.ERROR_LOG_PATIENT_OBJECTS);
	}

	/**
	 * Print some basic statistics in the console regarding the created patient objects.
	 */
	public void displayStats(){

		Logging.addNewLine();
		Logging.add("Statistics");
		Logging.add("---------------------------------");
		Logging.add("Total number of patients : "+Stats.nbPatients);
		if (inputSet.gotEvents){
			Logging.add("Total / Avg. number of events : "+Stats.nbEvents+" / "
					+StringUtilities.DECIMAL_FORMAT.format((float)Stats.nbEvents/(float)Stats.nbPatients));
		}
		if (inputSet.gotPrescriptions){
			Logging.add("Total / Avg. number of prescriptions : "+Stats.nbPrescriptions+" / "
					+StringUtilities.DECIMAL_FORMAT.format((float)Stats.nbPrescriptions/(float)Stats.nbPatients));
		}
		if (inputSet.gotMeasurements){
			Logging.add("Total / Avg. number of measurements : "+Stats.nbMeasurements+" / "
					+StringUtilities.DECIMAL_FORMAT.format((float)Stats.nbMeasurements/(float)Stats.nbPatients));
		}
		Logging.addNewLine();
		if (inputSet.gotEvents && Stats.nbUnassignedEvents > 0)
			Logging.add("Unassigned events: "+Stats.nbUnassignedEvents);
		if (inputSet.gotPrescriptions && Stats.nbUnassignedPrescriptions > 0)
			Logging.add("Unassigned prescriptions: "+Stats.nbUnassignedPrescriptions);
		if (inputSet.gotMeasurements && Stats.nbUnassignedMeasurements > 0)
			Logging.add("Unassigned measurements: "+Stats.nbUnassignedMeasurements);
	}

	/**
	 * Resets all the counters and flags of the patient creator.
	 */
	private void resetCountersAndFlags(){

		//counters
		Stats.nbEvents = 0;
		Stats.nbPrescriptions = 0;
		Stats.nbMeasurements = 0;
		Stats.nbPatients = 0;

		Stats.nbRecords = 0;

		Stats.nbUnassignedEvents = 0;
		Stats.nbUnassignedPrescriptions = 0;
		Stats.nbUnassignedMeasurements = 0;

		//EOF flags
		eofEvents = false;
		eofPrescriptions = false;
		eofMeasurements = false;

	}

	/**
	 * Closes the file readers and checks if end of file was
	 * reached in all files except the patients file in order
	 * to let the user know that there might be other unassigned records.
	 * @throws IOException - if cannot close the files
	 */
	private void closeReadersAndCheckForUnassigned() throws IOException{

		if (InputFileSet.MEASUREMENTS_FILE_INDEX != InputFileSet.NO_FILE &&
				FileUtilities.isEOF(measurementReader) && Jerboa.getOutputManager().hasFile(FilePaths.UNASSIGNED_RECORDS))
			addUnassigned("Possible more unassigned Measurements. " +
					"Reached end of Patients file, but not of Measurements file"+System.lineSeparator());

		if (InputFileSet.PRESCRIPTIONS_FILE_INDEX != InputFileSet.NO_FILE &&
				FileUtilities.isEOF(prescriptionReader) && Jerboa.getOutputManager().hasFile(FilePaths.UNASSIGNED_RECORDS))
			addUnassigned("Possible more unassigned Prescriptions. " +
					"Reached end of Patients file, but not of Prescriptions file"+System.lineSeparator());

		if (InputFileSet.EVENTS_FILE_INDEX != InputFileSet.NO_FILE &&
				FileUtilities.isEOF(eventReader) && Jerboa.getOutputManager().hasFile(FilePaths.UNASSIGNED_RECORDS))
			addUnassigned("Possible more unassigned Events. " +
					"Reached end of Patients file, but not of Events file"+System.lineSeparator());

		FileUtilities.closeBuffer(patientReader);
		FileUtilities.closeBuffer(eventReader);
		FileUtilities.closeBuffer(prescriptionReader);
		FileUtilities.closeBuffer(measurementReader);
	}

	//GETTERS AND SETTERS
	/**
	 * Allows the user to retrieve a patient object containing all the history.
	 * @param patientID - the ID of the patient to be retrieved
	 * @return - a patient object containing all data for the patient with the ID patientID;
	 * null if the ID is not in the patient list
	 */
	public Patient getPatient(String patientID){

		if ((hashTableID != null && hashTableID.size() > 0) &&
				patientID != null && !patientID.equals("")){
			return hashTableID.get(patientID) != null ?
					patients.get(hashTableID.get(patientID)) : null;
		}

		return null;
	}

	/**
	 * Checks if the subset ID column is present in all input files.
	 * @return - true if subset ID present in all input files; false otherwise
	 */
	public boolean checkForSubsetId(){
		return inputSet.getPatientsFile().hasSubset() &&
				(inputSet.gotEvents ? inputSet.getEventsFile().getSubsetIndex() != -1 : true) &&
				(inputSet.gotPrescriptions ? inputSet.getPrescriptionsFile().getSubsetIndex() != -1 : true) &&
				(inputSet.gotMeasurements ? inputSet.getMeasurementsFile().getSubsetIndex() != -1 : true);
	}

	/**
	 * Compares a patient ID to an episode ID in order to determine if a certain episode
	 * in a patients history is unassigned.
	 * @param patientID - the ID of the patient
	 * @param episodeID - the ID of the episode
	 * @return true if the episode cannot be assigned; false otherwise
	 */
	public static boolean checkIfUnassigned(String patientID, String episodeID) {
		return patientID.length() == episodeID.length() ? episodeID.compareTo(patientID) < 0
				: episodeID.length() - patientID.length() < 0;
	}

	/**
	 * Compares the episodeID with the patientID. They are compared
	 * length-wise and if equal then the compareTo method of the
	 * String object is invoked.
	 * @param episodeID - the patient ID present in episode
	 * @param patientID - the id of the patient in the patient object
	 * @return - same result structure as compareTo() method of the String object.
	 */
	public static int compareIDS(String episodeID, String patientID) {
		return patientID.length() == episodeID.length() ? episodeID.compareTo(patientID)
				: episodeID.length() - patientID.length();
	}

	public boolean isFilesSorted() {
		return filesSorted;
	}

	public void setFilesSorted(boolean filesSorted) {
		this.filesSorted = filesSorted;
	}

	public Stats getStats() {
		return stats;
	}

	public void setStats(Stats stats) {
		this.stats = stats;
	}

	public List<String> getErrors() {
		return errors;
	}

}
