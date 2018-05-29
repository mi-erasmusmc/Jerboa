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
package org.erasmusmc.jerboa.modifiers;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import org.apache.commons.lang3.StringUtils;
import org.erasmusmc.jerboa.Jerboa;
import org.erasmusmc.jerboa.config.DataDefinition;
import org.erasmusmc.jerboa.dataClasses.Patient;
import org.erasmusmc.jerboa.dataClasses.Prescription;
import org.erasmusmc.jerboa.engine.Modifier;
import org.erasmusmc.jerboa.utilities.ExtendedMultiKey;
import org.erasmusmc.jerboa.utilities.InputFileUtilities;
import org.erasmusmc.jerboa.utilities.Logging;
import org.erasmusmc.jerboa.utilities.MultiKeyBag;


/**
 * Processes the prescriptions of a patient to create exposure episodes.
 * Possible transformations include merging repeated prescriptions, closing gaps between
 * prescriptions, and adding wash-out time after prescriptions.
 * It will not take into account the cohort definition (To be Discussed)
 *
 * @author MG {@literal &} PR
 *
 */

/** *********************************************************************************************
 *
 * It is still not clear how to deal with a combination of stockpiling and removing gaps.
 *
 * @author bmosseveld
 *
 ************************************************************************************************/
public class ExposureDefinition extends Modifier {

	/**
	 * If a new prescription occurs within this fraction of the original prescription duration after the
	 * prescription ended, the new and old prescription are assumed to be repeats, and are merged.<br>
	 * Example:<br>
	 * {@code <ATC>;<max gap fraction(double)>}
	 */
	public List<String> maxGapFraction = new ArrayList<String>();

	/**
	 * If a new prescription occurs within this number of days after the
	 * prescription ended, the new and old prescription are assumed to be repeats, and are merged.<br>
	 * Example:<br>
	 * {@code <ATC>;<max gap days(int)>}
	 */
	public List<String> maxGapDays = new ArrayList<String>();

	/**
	 * Prolong each duration with this number of days.<br>
	 * Example:<br>
	 * {@code <ATC>;<add to duration days(int)>}
	 */
	public List<String> addToDurationDays = new ArrayList<String>();

	/**
	 * Prolong each duration with this fraction of the duration.<br>
	 * Example:<br>
	 * {@code <ATC>;<add to duration fraction(double)>}
	 */
	public List<String> addToDurationFraction = new ArrayList<String>();

	/**
	 * When true, patients are assumed to stockpile, and two overlapping prescriptions of the same drug
	 * are assumed to be a single episode with a length equal to the sum of the separate prescriptions.<br>
	 * Example:<br>
	 * {@code <ATC>;<assume stockpiling(True or False)>}
	 */
	public List<String> assumeStockpiling = new ArrayList<String>();

	/**
	 * When merging prescriptions, ignore differences in dose.<br>
	 * Example:<br>
	 * {@code <ATC>;<ignore dose(True or False)>}
	 */
	public List<String> ignoreDose = new ArrayList<String>();

	/**
	 * When merging prescriptions, ignore differences in indication.<br>
	 * Example:<br>
	 * {@code <ATC>;<ignore indication(True or False)>}
	 */
	public List<String> ignoreIndication = new ArrayList<String>();

	/**
	 * Indications that have priority over other indications from high priority to lower priority in
	 * case of merging prescriptions. For all other prescriptions except the ones mentioned in the
	 * indicationPriorityLow list, the one from the running prescriptions is taken.<br>
	 * Example:<br>
	 * {@code <ATC>;<indication>, ... ,<indication>}
	 */
	public List<String> indicationPriorityHigh = new ArrayList<String>();

	/**
	 * Indications that have the lowest priority from high to low priority in case of merging
	 * prescriptions. For all other prescriptions except the ones mentioned in the
	 * indicationPriorityHigh list, the one from the running prescriptions is taken.<br>
	 * Example:<br>
	 * {@code <ATC>;<indication>, ... ,<indication>}
	 */
	public List<String> indicationPriorityLow = new ArrayList<String>();

	//counters
	private long countNew;
	private long sumDurationNew;
	private long countOriginal;
	private long sumDurationOriginal;
	private MultiKeyBag countZeroDuration;

	private ATCParameterMaps atcMaps;

	@Override
	public void setNeededFiles() {
		setRequiredFile(DataDefinition.PRESCRIPTIONS_FILE);
	}

	@Override
	public void setNeededExtendedColumns() { /*NOTHING TO ADD */ }

	@Override
	public void setNeededNumericColumns() {
		// TODO Auto-generated method stub

	}

	@Override
	public boolean init() {
		boolean initOK = true;

		//reset counters
		countOriginal = 0;
		sumDurationOriginal = 0;
		countNew = 0;
		sumDurationNew = 0;
		countZeroDuration = new MultiKeyBag();

		atcMaps = new ATCParameterMaps();
		// Parse maxGapFraction
		atcMaps.addMap("maxGapFraction", "Double", maxGapFraction);
		// Parse maxGapDays
		atcMaps.addMap("maxGapDays", "Integer", maxGapDays);
		// Parse addToDurationDays
		atcMaps.addMap("addToDurationDays", "Integer", addToDurationDays);
		// Parse addToDurationFraction
		atcMaps.addMap("addToDurationFraction", "Double", addToDurationFraction);
		// Parse addToDurationDays
		atcMaps.addMap("addToDurationDays", "Integer", addToDurationDays);
		// Parse assumeStockpiling
		atcMaps.addMap("assumeStockpiling", "Boolean", assumeStockpiling);
		// Parse ignoreDose
		atcMaps.addMap("ignoreDose", "Boolean", ignoreDose);
		// Parse ignoreIndication
		atcMaps.addMap("ignoreIndication", "Boolean", ignoreIndication);
		// Parse indicationPriorityHigh
		atcMaps.addMap("indicationPriorityHigh", "StringList", indicationPriorityHigh);
		// Parse indicationPriorityLow
		atcMaps.addMap("indicationPriorityLow", "StringList", indicationPriorityLow);
		initOK = (initOK && atcMaps.isOK());

		if (intermediateFiles){
				//TODO: it is not adding dose yet (only if it is present)
			initOK &= Jerboa.getOutputManager().addFile(this.intermediateFileName);
			Jerboa.getOutputManager().writeln(this.intermediateFileName,
					InputFileUtilities.getPrescriptionsFileHeaderForExport(), false);
		}

		return initOK;
	}

	/**
	 * Modifies the duration of the prescriptions according to the module parameters.
	 * It creates continuous episodes if the prescriptions are not further away from each other
	 * than the maxGapDays.
	 * @param patient - the patient object to be modified
	 * @return patient - the same patient object with a modified list of prescriptions
	 */
	@Override
	public Patient process(Patient patient) {

		if (patient != null && patient.isInPopulation() && patient.isInCohort() &&
				(patient.getPrescriptions() != null && patient.getPrescriptions().size() >0)){

			// Sort the prescriptions according to date and duration (long to short).
			List<Prescription> SortedPrescriptions = patient.getPrescriptions();
			Collections.sort(SortedPrescriptions, Prescription.DateUpDurationDownComparator);

			//will hold the modified prescriptions
			List<Prescription> modifiedPrescriptions = new ArrayList<Prescription>();
			//running prescriptions are the prescriptions that are still active in an observation window
			List<CustomPrescription> runningPrescriptions = new ArrayList<CustomPrescription>();

			for (Prescription prescription : SortedPrescriptions){
				//update counters of original prescriptions
				countOriginal++;
				sumDurationOriginal += prescription.getDuration();

				if (prescription.getDuration() > 0) {

					//change the duration based on addToDuration and addToDurationFraction
					CustomPrescription customPrescription = new CustomPrescription(prescription);
					customPrescription.setDuration((int)(customPrescription.getDuration() +
							Math.round(atcMaps.getDoubleMap("addToDurationFraction").getValue(prescription) * prescription.getDuration()) + atcMaps.getIntegerMap("addToDurationDays").getValue(prescription)));

					//calculate the date at which we scan if the next prescription should be merged or not
					//PR: ask MS what happens if you add duration and use the gap definitions at the same time?? scandate is based on original end date!?
					customPrescription.scanDate = customPrescription.getOriginalEnd() + (int) Math.max(Math.round(atcMaps.getDoubleMap("maxGapFraction").getValue(prescription) * prescription.getDuration()), atcMaps.getIntegerMap("maxGapDays").getValue(prescription));
					//customPrescription.scanDate = customPrescription.getEndDate() + (int) Math.max(Math.round(maxGapFraction * prescription.duration), maxGapDays);

					mergeAndOutputPrescriptions(customPrescription, runningPrescriptions, modifiedPrescriptions);
					runningPrescriptions.add(customPrescription);
				} else {
					countZeroDuration.add(new ExtendedMultiKey(prescription.getATC(),prescription.getIndication()));
				}
			}

			mergeAndOutputPrescriptions(null, runningPrescriptions, modifiedPrescriptions);

			patient.setPrescriptions(modifiedPrescriptions);
			countNew = countNew + modifiedPrescriptions.size();
			for (Prescription prescription : modifiedPrescriptions){
				sumDurationNew = sumDurationNew + prescription.getDuration();
			}

			//add to output
			addToOutputBuffer(modifiedPrescriptions);
		}
		//sort prescriptions by date
		patient.sortPrescriptions();
		return patient;
	}

	@Override
	public void addToOutputBuffer(List<?> data){
		if (intermediateFiles) {
			if (!Jerboa.getOutputManager().hasFile(this.intermediateFileName))
				Jerboa.getOutputManager().addFile(this.intermediateFileName);
			for (int i=0;i<data.size();i++)
				Jerboa.getOutputManager().writeln(this.intermediateFileName,
						((Prescription)data.get(i)).toStringForExport(), true);
		}
	}

	/**
	 * Returns a unique key of prescription.
	 * This key is used to check if a prescription is the first of its kind in the history of the patient.
	 * If the dose of a prescription is present, the key is a combination of ATC and dose.
	 * @param prescription - the prescription to be processed
	 * @return - the key as either ATC or ATC_dosage
	 *
	private String getKey(Prescription prescription) {
		String key = prescription.getATC();
		if (!atcMaps.getBooleanMap("ignoreDose").getValue(prescription)) key += "_" + prescription.getDose();
		if (!atcMaps.getBooleanMap("ignoreIndication").getValue(prescription)) key += "_" + prescription.getIndication();
		return key;
	}
	*/

	/**
	 * Returns true if the two prescriptions match.
	 * When required dose and indication are also taken into account.
	 */
	private boolean match(Prescription prescription1, Prescription prescription2) {
		boolean match = false;
		for (String atc : atcMaps.getIndex("maxGapDays,maxGapFraction")) {
			atc = StringUtils.upperCase(atc);
			match = false;
			//TODO: does not work if # is defined -> not merged
			if ((prescription1.startsWith(atc) && prescription2.startsWith(atc)) || (atc.equals("#") && prescription1.getATC().equals(prescription2.getATC()))) {
				match = true;
				//if one of the two has not ignoreDose check
				if ((atcMaps.getBooleanMap("ignoreDose").contains(prescription1) && (!atcMaps.getBooleanMap("ignoreDose").getValue(prescription1))) ||
					(atcMaps.getBooleanMap("ignoreDose").contains(prescription2) && (!atcMaps.getBooleanMap("ignoreDose").getValue(prescription2)))) {
					if (!prescription1.hasDose() || !prescription2.hasDose() ||
						!prescription1.getDose().equals(prescription2.getDose())) {
						    match = false;
					}
				}

				//if one of the two has not ignoreIndication check
				if ((atcMaps.getBooleanMap("ignoreIndication").contains(prescription1) && (!atcMaps.getBooleanMap("ignoreIndication").getValue(prescription1))) ||
					(atcMaps.getBooleanMap("ignoreIndication").contains(prescription2) && (!atcMaps.getBooleanMap("ignoreIndication").getValue(prescription2)))) {
					if (!prescription1.hasIndication() || ! prescription2.hasIndication() ||
						!prescription1.getIndication().equals(prescription2.getIndication())) {
						    match = false;
					}
				}
				if (match)
					break;
			}
		}
		return match;
	}


	/**
	 * Output the counts of the original prescriptions and their duration,
	 * as well as the count of the modified prescriptions and their duration.
	 * It can be used per patient or overall.
	 */
	@Override
	public void outputResults(){
		flushRemainingData();
		Logging.add("Original size: " + countOriginal + " prescriptions, after merging: " +
				countNew + " prescriptions");
		Logging.add("Original average duration: " + (float)sumDurationOriginal/(float)countOriginal +
				" days, after merging: " + (float)sumDurationNew/(float)countNew + " days");
	}

	/**
	 * Checks if a new prescription should be merged with any of the running prescriptions.
	 * If the answer is no it will be added to the outputPrescriptions list, otherwise it is merged
	 * @param newPrescription - the prescription under consideration
	 * @param runningPrescriptions - all prescriptions that are still active at the time of observation
	 * @param outputPrescriptions - final list of modified prescriptions
	 */
	private void mergeAndOutputPrescriptions(CustomPrescription newPrescription, List<CustomPrescription> runningPrescriptions, List<Prescription> outputPrescriptions) {
		Iterator<CustomPrescription> prescriptionIterator = runningPrescriptions.iterator();
		while (prescriptionIterator.hasNext()){
			CustomPrescription prescription = prescriptionIterator.next();
			if (newPrescription == null || prescription.scanDate < newPrescription.date){
				outputPrescriptions.add(prescription);
				prescriptionIterator.remove();
			}
			else {
				//if (getKey(prescription).equals(getKey(newPrescription))) {
				if (match(prescription, newPrescription)) {
					if (prescription.date <= newPrescription.scanDate && prescription.scanDate >= newPrescription.date) { //some overlap
						int tempEnd = newPrescription.getEndDate();
						int tempOriginalEnd = newPrescription.getOriginalEnd();
						int tempScan = newPrescription.scanDate;
						if (atcMaps.getBooleanMap("assumeStockpiling").getValue(prescription) && (prescription.getOriginalEnd() > newPrescription.date)) {
							newPrescription.date = Math.min(prescription.date, newPrescription.date);
							newPrescription.setDuration(prescription.getDuration() + newPrescription.getDuration());
							newPrescription.originalDuration = prescription.originalDuration + newPrescription.originalDuration;
							//TODO Should the next line not be:
							//newPrescription.scanDate = (int) (newPrescription.getOriginalEnd() + Math.max(Math.round(maxGapFractionMap.getValue(prescription) * newPrescription.originalDuration), maxGapDaysMap.getValue(prescription)));
							newPrescription.scanDate = (int) (newPrescription.getOriginalEnd() + Math.max(Math.round(atcMaps.getDoubleMap("maxGapFraction").getValue(prescription) * prescription.getDuration()), atcMaps.getIntegerMap("maxGapDays").getValue(prescription)));
						}
						else if ((atcMaps.getDoubleMap("maxGapFraction").contains(prescription)) || (atcMaps.getIntegerMap("maxGapDays").contains(prescription))) {
							newPrescription.date = Math.min(prescription.date, newPrescription.date);
							newPrescription.setDuration(Math.max(tempEnd, prescription.getEndDate()) - newPrescription.date);
							newPrescription.originalDuration = Math.max(tempOriginalEnd, prescription.getOriginalEnd()) - newPrescription.date;
							newPrescription.scanDate = Math.max(tempScan, prescription.scanDate);
							newPrescription.setDose(prescription.getDose());
							// Set the indication
							newPrescription.setIndication(mergeIndications(prescription, newPrescription));
							if (prescription.hasPrescriberId())
								newPrescription.setPrescriberId(prescription.getPrescriberId());
							if (prescription.hasPrescriberType())
								newPrescription.setPrescriberType(prescription.getPrescriberType());
						}
						else {
							outputPrescriptions.add(prescription);
						}
						newPrescription.repeats+= prescription.repeats;
						prescriptionIterator.remove();
					}
				}
			}
		}
	}


	/**
	 * Select the right indication for the merge of the prescriptions.
	 * @param prescription
	 * @param newPrescription
	 */
	private String mergeIndications(CustomPrescription prescription, CustomPrescription newPrescription) {
		String indication = prescription.hasIndication() ? prescription.getIndication() : "";
		String newIndication = newPrescription.hasIndication() ? newPrescription.getIndication() : "";
		String resultIndication = "";
		if (indication.equals("")) {
			resultIndication = newIndication;
		}
		else if (newIndication.equals(""))
			resultIndication = indication;
		else if (indication.equals(newIndication)) {
			resultIndication = indication;
		}
		else {
			List<String> indicationPriorityHighPrescription = atcMaps.getStringListMap("indicationPriorityHigh").getValue(prescription);
			List<String> indicationPriorityHighNewPrescription = atcMaps.getStringListMap("indicationPriorityHigh").getValue(newPrescription);
			if (indicationPriorityHighPrescription.contains(indication)) {
				if (indicationPriorityHighNewPrescription.contains(newIndication)) {
					if (indicationPriorityHighPrescription.indexOf(indication) < indicationPriorityHighNewPrescription.indexOf(newIndication))
						resultIndication = indication;
					else
						resultIndication = newIndication;
				}
				else
					resultIndication = indication;
			}
			else if (indicationPriorityHighNewPrescription.contains(newIndication))
				resultIndication = newIndication;
			else {
				List<String> indicationPriorityLowPrescription = atcMaps.getStringListMap("indicationPriorityLow").getValue(prescription);
				List<String> indicationPriorityLowNewPrescription = atcMaps.getStringListMap("indicationPriorityLow").getValue(newPrescription);
				if (indicationPriorityLowPrescription.contains(indication)) {
					if (indicationPriorityLowNewPrescription.contains(newIndication)) {
						if (indicationPriorityLowPrescription.indexOf(indication) < indicationPriorityLowNewPrescription.indexOf(newIndication))
							resultIndication = indication;
						else
							resultIndication = newIndication;
					}
				}
				else
					resultIndication = indication;
			}
		}
		return resultIndication;
	}


	/**
	 * Inner class that extends Prescription with temporary variables
	 * that contain the scandate and originalDuration.
	 */
	private static class CustomPrescription extends Prescription {
		int scanDate; //Date at which we need to determine if we need to merge
		int originalDuration;
		int repeats = 1;
		public int getOriginalEnd(){
			return date + originalDuration;
		}
		public CustomPrescription(Prescription prescription){
			super(prescription);
			originalDuration = prescription.getDuration();
		}
	}


	public void testMaps() {
		System.out.println("IntegerMap");
		List<String> integerMapDefinition = new ArrayList<String>();
		integerMapDefinition.add("A01AB;5");
		integerMapDefinition.add("A01;6");
		integerMapDefinition.add(";8");
		integerMapDefinition.add("A01AC01;4");
		integerMapDefinition.add("A01AB03;3");
		//integerMapDefinition.add("A01AB03;99999999");
		integerMapDefinition.add("A01AB01;1");
		integerMapDefinition.add("A01AB02;2");
		integerMapDefinition.add("A;7");

		Prescription p = new Prescription();

		atcMaps = new ATCParameterMaps();

		atcMaps.addMap("Test", "Integer", integerMapDefinition);
		System.out.println(atcMaps.getIntegerMap("Test"));
		p.setATC("A01AB06"); System.out.println(p.getATC() + " -> " + atcMaps.getIntegerMap("Test").getValue(p));
		p.setATC("A01AB03"); System.out.println(p.getATC() + " -> " + atcMaps.getIntegerMap("Test").getValue(p));
		p.setATC("A01AC01"); System.out.println(p.getATC() + " -> " + atcMaps.getIntegerMap("Test").getValue(p));
		p.setATC("C01AC01"); System.out.println(p.getATC() + " -> " + atcMaps.getIntegerMap("Test").getValue(p));

		System.out.println("DoubleMap");
		List<String> doubleMapDefinition = new ArrayList<String>();
		doubleMapDefinition.add("A01AB;5.5");
		doubleMapDefinition.add("A01;6.6");
		doubleMapDefinition.add(";8.8");
		doubleMapDefinition.add("A01AC01;4.4");
		doubleMapDefinition.add("A01AB03;3.3");
		//doubleMapDefinition.add("A01AB03;9999.9999");
		doubleMapDefinition.add("A01AB01;1.1");
		doubleMapDefinition.add("A01AB02;2.2");
		doubleMapDefinition.add("A;7.7");

		atcMaps.addMap("Test", "Double", doubleMapDefinition);
		System.out.println(atcMaps.getDoubleMap("Test"));
		p.setATC("A01AB06"); System.out.println(p.getATC() + " -> " + atcMaps.getDoubleMap("Test").getValue(p));
		p.setATC("A01AB03"); System.out.println(p.getATC() + " -> " + atcMaps.getDoubleMap("Test").getValue(p));
		p.setATC("A01AC01"); System.out.println(p.getATC() + " -> " + atcMaps.getDoubleMap("Test").getValue(p));
		p.setATC("C01AC01"); System.out.println(p.getATC() + " -> " + atcMaps.getDoubleMap("Test").getValue(p));

		System.out.println("BooleanMap");
		List<String> booleanMapDefinition = new ArrayList<String>();
		booleanMapDefinition.add("A01AB;false");
		booleanMapDefinition.add("A01;true");
		booleanMapDefinition.add(";true");
		booleanMapDefinition.add("A01AC01;true");
		booleanMapDefinition.add("A01AB03;false");
		//booleanMapDefinition.add("A01AB03;9999.9999");
		booleanMapDefinition.add("A01AB01;false");
		booleanMapDefinition.add("A01AB02;true");
		booleanMapDefinition.add("A;false");

		atcMaps.addMap("Test", "Boolean", booleanMapDefinition);
		System.out.println(atcMaps.getBooleanMap("Test"));
		p.setATC("A01AB06"); System.out.println(p.getATC() + " -> " + atcMaps.getBooleanMap("Test").getValue(p));
		p.setATC("A01AB03"); System.out.println(p.getATC() + " -> " + atcMaps.getBooleanMap("Test").getValue(p));
		p.setATC("A01AC01"); System.out.println(p.getATC() + " -> " + atcMaps.getBooleanMap("Test").getValue(p));
		p.setATC("C01AC01"); System.out.println(p.getATC() + " -> " + atcMaps.getBooleanMap("Test").getValue(p));

		System.out.println("Overall Index");
		System.out.println(atcMaps.getIndex());
	}


	//MAIN FOR DEBUGGING
	public static void main(String[] args) {
		// Test maps
		ExposureDefinition ed = new ExposureDefinition();
		ed.testMaps();
		//
		/*
		PatientObjectCreator poc = new PatientObjectCreator();

		FilePaths.WORKFLOW_PATH = "D:/Temp/Jerboa/Test Exposure Definition Nov 2014/Jerboa/";
		FilePaths.LOG_PATH = FilePaths.WORKFLOW_PATH+"/Log/";
		String patientsFile = FilePaths.WORKFLOW_PATH+"Patients.txt";
		String prescriptionsFile = FilePaths.WORKFLOW_PATH+"Prescriptions.txt";
		String eventsFile = FilePaths.WORKFLOW_PATH+"Events.txt";
		String measurementsFile = FilePaths.WORKFLOW_PATH+"Measurements.txt";

		Logging.prepareOutputLog();

		ExposureDefinition exposureDefinition = new ExposureDefinition();
		exposureDefinition.maxGapDays = new ArrayList<String>();
		exposureDefinition.maxGapDays.add(";30");
		exposureDefinition.intermediateFiles = true;
		exposureDefinition.assumeStockpiling = new ArrayList<String>();
		exposureDefinition.assumeStockpiling.add(";No");

		exposureDefinition.addToDurationDays.add("M01AB01,B01AB01;50");
		exposureDefinition.addToDurationDays.add("C01AB01;200");

		exposureDefinition.init();
		exposureDefinition.setIntermediateFileName(FilePaths.WORKFLOW_PATH + "Intermediate/exposureDefinition.txt");

		PopulationDefinition populationDefinition = new PopulationDefinition();

		populationDefinition.runInPeriod = 365;
		populationDefinition.childInclusionPeriod = 0;
		populationDefinition.studyStart = "20020101";
		populationDefinition.studyEnd = "20040101";

		populationDefinition.init();
		populationDefinition.setIntermediateFileName(FilePaths.WORKFLOW_PATH + "Intermediate/populationDefinition.txt");

		CohortDefinition cohortDefinition = new CohortDefinition();
		cohortDefinition.drugsOfInterest.add("M01AB01");
		cohortDefinition.drugsOfInterest.add("N01AB01");
		cohortDefinition.eventsInclusion.add("CONV");
		cohortDefinition.init();
		cohortDefinition.setIntermediateFileName(FilePaths.WORKFLOW_PATH + "Intermediate/cohortDefinition.txt");

		try{
			List<Patient> patients = poc.createPatients(patientsFile, eventsFile, prescriptionsFile, measurementsFile);
			List<Patient> patients_after = new ArrayList<Patient>();
			if (patients != null && patients.size() > 0){

				Timer timer = new Timer();
				timer.start();
				int count = 0;

				for (Patient patient : patients){
					count ++;
					patient = populationDefinition.process(patient);
					patient = cohortDefinition.process(patient);

					Patient patient_after = new Patient(patient);
					patient_after = populationDefinition.process(patient_after);
					patient_after = cohortDefinition.process(patient_after);
					patient_after = exposureDefinition.process(patient_after);
					patients_after.add(patient_after);
				}

				exposureDefinition.flushOutputBuffer();
				exposureDefinition.outputResults();
				timer.stop();
				System.out.println("Exposure defition of "+ count +" run in:"+timer);
				new PatientViewer(patients,null, "Original");
				new PatientViewer(patients_after,null,"After Exposure Definition");
			}

		}catch(IOException e){
			System.out.println("Error while opening input files");
			//		} catch (CloneNotSupportedException e) {
			//			// TODO Auto-generated catch block
			//			System.out.println("Clone not supported");
		}
		*/
	}


	private class ATCParameterMaps {
		private List<String> overallIndex = new ArrayList<String>();
		private Map<String, List<String>> indexMap = new HashMap<String, List<String>>();
		private Map<String, ATCIntegerMap> atcIntegerMaps = new HashMap<String, ATCIntegerMap>();
		private Map<String, ATCDoubleMap> atcDoubleMaps = new HashMap<String, ATCDoubleMap>();
		private Map<String, ATCBooleanMap> atcBooleanMaps = new HashMap<String, ATCBooleanMap>();
		private Map<String, ATCStringListMap> atcStringListMaps = new HashMap<String, ATCStringListMap>();
		boolean ok = true;


		public ATCParameterMaps() {
			indexMap.put("OVERALL_INDEX", overallIndex);
		}


		public void addMap(String mapName, String mapType, List<String> mappingDefinition) {
			 if (mapType.equals("Integer")) {
				 ATCIntegerMap atcIntegerMap = new ATCIntegerMap(mapName, mappingDefinition);
				 if (atcIntegerMap.isOK()) {
					 atcIntegerMaps.put(mapName, atcIntegerMap);
				 }
				 else {
					 ok = false;
				 }
			 }
			 if (mapType.equals("Double")) {
				 ATCDoubleMap atcDoubleMap = new ATCDoubleMap(mapName, mappingDefinition);
				 if (atcDoubleMap.isOK()) {
					 atcDoubleMaps.put(mapName, atcDoubleMap);
				 }
				 else {
					 ok = false;
				 }
			 }
			 if (mapType.equals("Boolean")) {
				 ATCBooleanMap atcBooleanMap = new ATCBooleanMap(mapName, mappingDefinition);
				 if (atcBooleanMap.isOK()) {
					 atcBooleanMaps.put(mapName, atcBooleanMap);
				 }
				 else {
					 ok = false;
				 }
			 }
			 if (mapType.equals("StringList")) {
				 ATCStringListMap atcStringListMap = new ATCStringListMap(mapName, mappingDefinition);
				 if (atcStringListMap.isOK()) {
					 atcStringListMaps.put(mapName, atcStringListMap);
				 }
				 else {
					 ok = false;
				 }
			 }
		}


		public ATCIntegerMap getIntegerMap(String mapName) {
			return atcIntegerMaps.get(mapName);
		}


		public ATCDoubleMap getDoubleMap(String mapName) {
			return atcDoubleMaps.get(mapName);
		}


		public ATCBooleanMap getBooleanMap(String mapName) {
			return atcBooleanMaps.get(mapName);
		}


		public ATCStringListMap getStringListMap(String mapName) {
			return atcStringListMaps.get(mapName);
		}


		public List<String> getIndex() {
			return overallIndex;
		}


		public List<String> getIndex(String mapList) {
			List<String> index = indexMap.get(mapList);
			if (index == null) {
				index = new ArrayList<String>();
				indexMap.put(mapList, index);
				String[] mapNames = mapList.split(",");
				for (String mapName : mapNames) {
					if (atcIntegerMaps.containsKey(mapName)) {
						for (String atc : atcIntegerMaps.get(mapName).index) {
							if (!index.contains(atc)) {
								index.add(atc);
							}
						}
					}
					else if (atcDoubleMaps.containsKey(mapName)) {
						for (String atc : atcDoubleMaps.get(mapName).index) {
							if (!index.contains(atc)) {
								index.add(atc);
							}
						}
					}
					else if (atcBooleanMaps.containsKey(mapName)) {
						for (String atc : atcBooleanMaps.get(mapName).index) {
							if (!index.contains(atc)) {
								index.add(atc);
							}
						}
					}
					else if (atcStringListMaps.containsKey(mapName)) {
						for (String atc : atcStringListMaps.get(mapName).index) {
							if (!index.contains(atc)) {
								index.add(atc);
							}
						}
					}
				}
			}
			return index;
		}


		public boolean isOK() {
			return ok;
		}


		private class ATCParameterMap {
			protected List<String> index = new ArrayList<String>();
			protected boolean ok = true;

			public boolean addToIndex(String atc) {
				boolean ok = true;
				if (atc.equals("#") && (!index.contains(atc))) {
					index.add(atc);
				}
				else {
					boolean added = false;
					int indexNr = 0;
					while ((!added) && (indexNr < index.size())) {
						String indexAtc = index.get(indexNr);
						if (indexAtc.equals("#")) {
							index.add(indexNr, atc);
							added = true;
						}
						else if (atc.length() > indexAtc.length()) {
							index.add(indexNr, atc);
							added = true;
						}
						else if (atc.length() == indexAtc.length()) {
							int compare = atc.compareTo(indexAtc);
							if (compare < 0) {
								index.add(indexNr, atc);
								added = true;
							}
							else if (compare == 0) {
								ok = false;
								added = true;
							}
						}
						indexNr++;
					}
					if (!added) {
						index.add(atc);
					}
				}

				if (atc.equals("#") && (!overallIndex.contains(atc))) {
					overallIndex.add(atc);
				}
				else {
					boolean added = false;
					int indexNr = 0;
					while ((!added) && (indexNr < overallIndex.size())) {
						String indexAtc = overallIndex.get(indexNr);
						if (atc.equals("#") && (!overallIndex.get(overallIndex.size() - 1).equals(atc))) {
							overallIndex.add(indexNr, atc);
							added = true;
						}
						else if (atc.length() > indexAtc.length()) {
							overallIndex.add(indexNr, atc);
							added = true;
						}
						else if (atc.length() == indexAtc.length()) {
							int compare = atc.compareTo(indexAtc);
							if (compare < 0) {
								overallIndex.add(indexNr, atc);
								added = true;
							}
							else if (compare == 0) {
								added = true;
							}
						}
						indexNr++;
					}
					if (!added) {
						overallIndex.add(atc);
					}
				}

				return ok;
			}

			public boolean isOK() {
				return ok;
			}

			public boolean contains(Prescription prescription) {
				boolean found = false;
				for (String atc : index) {
					if (atc.equals("#")) {
						found = true;
						break;
					}
					else if (prescription.startsWith(atc)) {
						found = true;
						break;
					}
				}
				return found;
			}

		}


		private class ATCIntegerMap extends ATCParameterMap {
			private Map<String, Integer> map = new HashMap<String, Integer>();

			public ATCIntegerMap(String mapName, List<String> mappingDefinition) {
				for (String mapping : mappingDefinition) {
					String[] mappingSplit = mapping.split(";");
					if ((mappingSplit.length > 1) && (!mappingSplit[1].equals(""))) {
						try {
							int value = Integer.parseInt(mappingSplit[1]);
							String atc = mappingSplit[0];
							if (atc.equals("")) {
								atc = "#";
							}
							if (addToIndex(atc)) {
								map.put(atc, value);
							}
							else {
								Logging.add("Duplicate atc entry " + mapName + ": " + mapping);
								ok = false;
							}
						}
						catch (NumberFormatException e) {
							Logging.add("Illegal " + mapName + ": " + mapping);
							ok = false;
						}
					}
					else if (mappingSplit.length != 0) {
						Logging.add("Illegal " + mapName + ": " + mapping);
						ok = false;
					}
				}
			}

			public int getValue(Prescription prescription) {
				int value = Integer.MIN_VALUE;
				for (String atc : index) {
					if (atc.equals("#")) {
						value = map.get(atc);
						break;
					}
					else if (prescription.startsWith(atc)) {
						value = map.get(atc);
						break;
					}
				}
				return (value == Integer.MIN_VALUE ? 0 : value);
			}

			public String toString() {
				String description = "";
				for (String atc : index) {
					description += atc + " -> " + map.get(atc) + "\r\n";
				}
				return description;
			}
		}


		private class ATCDoubleMap extends ATCParameterMap {
			private Map<String, Double> map = new HashMap<String, Double>();

			public ATCDoubleMap(String mapName, List<String> mappingDefinition) {
				for (String mapping : mappingDefinition) {
					String[] mappingSplit = mapping.split(";");
					if ((mappingSplit.length > 1) && (!mappingSplit[1].equals(""))) {
						try {
							double value = Double.parseDouble(mappingSplit[1]);
							String atc = mappingSplit[0];
							if (atc.equals("")) {
								atc = "#";
							}
							if (addToIndex(atc)) {
								map.put(atc, value);
							}
							else {
								Logging.add("Duplicate atc entry " + mapName + ": " + mapping);
								ok = false;
							}
						}
						catch (NumberFormatException e) {
							Logging.add("Illegal " + mapName + ": " + mapping);
							ok = false;
						}
					}
					else if (mappingSplit.length != 0) {
						Logging.add("Illegal " + mapName + ": " + mapping);
						ok = false;
					}
				}
			}

			public double getValue(Prescription prescription) {
				double value = Double.MIN_VALUE;
				for (String atc : index) {
					if (atc.equals("#")) {
						value = map.get(atc);
						break;
					}
					else if (prescription.startsWith(atc)) {
						value = map.get(atc);
						break;
					}
				}
				return (value == Double.MIN_VALUE ? 0 : value);
			}

			public String toString() {
				String description = "";
				for (String atc : index) {
					description += atc + " -> " + map.get(atc) + "\r\n";
				}
				return description;
			}
		}


		private class ATCBooleanMap extends ATCParameterMap {
			private Map<String, Boolean> map = new HashMap<String, Boolean>();

			public ATCBooleanMap(String mapName, List<String> mappingDefinition) {
				for (String mapping : mappingDefinition) {
					String[] mappingSplit = mapping.split(";");
					if ((mappingSplit.length > 1) && (!mappingSplit[1].equals(""))) {
						try {
							boolean value = mappingSplit[1].toUpperCase().equals("TRUE") ? true : false;
							String atc = mappingSplit[0];
							if (atc.equals("")) {
								atc = "#";
							}
							if (addToIndex(atc)) {
								map.put(atc, value);
							}
							else {
								Logging.add("Duplicate atc entry " + mapName + ": " + mapping);
								ok = false;
							}
						}
						catch (NumberFormatException e) {
							Logging.add("Illegal " + mapName + ": " + mapping);
							ok = false;
						}
					}
					else if (mappingSplit.length != 0) {
						Logging.add("Illegal " + mapName + ": " + mapping);
						ok = false;
					}
				}
			}

			public boolean getValue(Prescription prescription) {
				boolean value = false;
				for (String atc : index) {
					if (atc.equals("#")) {
						value = map.get(atc);
						break;
					}
					else if (prescription.startsWith(atc)) {
						value = map.get(atc);
						break;
					}
				}
				return value;
			}

			public String toString() {
				String description = "";
				for (String atc : index) {
					description += atc + " -> " + map.get(atc) + "\r\n";
				}
				return description;
			}
		}


		private class ATCStringListMap extends ATCParameterMap {
			private Map<String, List<String>> map = new HashMap<String, List<String>>();

			public ATCStringListMap(String mapName, List<String> mappingDefinition) {
				for (String mapping : mappingDefinition) {
					String[] mappingSplit = mapping.split(";");
					if ((mappingSplit.length > 1) && (!mappingSplit[1].equals(""))) {
						try {
							String atc = mappingSplit[0];
							if (atc.equals("")) {
								atc = "#";
							}
							String value = mappingSplit[1].toUpperCase();
							String[] valueSplit = value.split(",");
							List<String> list = new ArrayList<String>();
							for (String listElement : valueSplit) {
								if (!listElement.equals("")) {
									list.add(listElement);
								}
							}
							if (addToIndex(atc)) {
								map.put(atc, list);
							}
							else {
								Logging.add("Duplicate atc entry " + mapName + ": " + mapping);
								ok = false;
							}
						}
						catch (NumberFormatException e) {
							Logging.add("Illegal " + mapName + ": " + mapping);
							ok = false;
						}
					}
					else if (mappingSplit.length != 0) {
						Logging.add("Illegal " + mapName + ": " + mapping);
						ok = false;
					}
				}
			}

			public List<String> getValue(Prescription prescription) {
				List<String> value = new ArrayList<String>();
				for (String atc : index) {
					if (atc.equals("#")) {
						value = map.get(atc);
						break;
					}
					else if (prescription.startsWith(atc)) {
						value = map.get(atc);
						break;
					}
				}
				return value;
			}

			public String toString() {
				String description = "";
				for (String atc : index) {
					description += atc + " -> " + map.get(atc) + "\r\n";
				}
				return description;
			}
		}
	}
}
