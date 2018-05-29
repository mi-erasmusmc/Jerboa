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
 * Author: Mees Mosseveld (MM) - department of Medical Informatics						  *
 * 																						  *
 * $Rev:: 3961              $:  Revision of last commit                                   *
 * $Author:: root           $:  Author of last commit                                     *
 * $Date:: 2013-05#			$:  Date and time (CET) of last commit						  *
 ******************************************************************************************/
package org.erasmusmc.jerboa.modifiers;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.commons.lang3.text.StrBuilder;
import org.erasmusmc.jerboa.Jerboa;
import org.erasmusmc.jerboa.config.DataDefinition;
import org.erasmusmc.jerboa.dataClasses.Patient;
import org.erasmusmc.jerboa.dataClasses.Prescription;
import org.erasmusmc.jerboa.engine.Modifier;
import org.erasmusmc.jerboa.utilities.DateUtilities;
import org.erasmusmc.jerboa.utilities.Logging;

/**
 * This modifier defines combinations of drugs prescribed.
 * @author bmosseveld
 *
 */
public class PrescriptionCombinations extends Modifier {

	/**
	 * The drug group definitions it will look for.
	 * The drug groups should be distinct.
	 * Format:
	 *
	 *   Group Name;ATC, ... ,ATC
	 *
	 * where
	 *
	 *   Group Name       = The name of the group that is also used in the combination definitions.
	 *   ATC              = An ATC code of a drug that is a member of the group.
	 */
	public List<String> drugGroups = new ArrayList<String>();

	/**
	 * The combination definitions it will look for.
	 * Format:
	 *
	 *   Combination Name;Drug Group, ... ,Drug Group
	 *
	 * where
	 *
	 *   Combination Name = The name of the combination.
	 *                      IMPORTANT: In subsequent modifiers and the surrounding module the name of the combination
	 *                                 has to be preceded by an underscore.
	 *   Drug Group       = A drug group, as specified in the drugGroups parameter, that are part of the combination.
	 */
	public List<String> combinations = new ArrayList<String>();

	/**
	 * When true all duplicates of combinations (same ATC, start date, and duration) are removed, otherwise only
	 * duplicates where either in both combinations all components of the combination start at the start of the
	 * combination or in both combinations they do not all start at the start of the combination.
	 */
	public boolean removeDifferentCompStarts;

	/**
	 * If true, prescriptions of combinations of the same drugs that connect or overlap are merged.
	 * Merging is never applied to combinations of the original prescriptions (no exposure definition applied).
	 */
	public boolean mergeCombinations;



	private TimeLine timeLine = null;
	private TimeLine exposureTimeLine = null;
	private TimeLine originalTimeLine = null;
	private DrugGroups drugGroupDefinitions = null;
	private Combinations combinationDefinitions = null;
	private String error = "";


	@Override
	public boolean init() {

		boolean initOK = (drugGroupDefinitions = new DrugGroups(drugGroups)).isOk();
		initOK = initOK && (combinationDefinitions = new Combinations(combinations)).isOk();

		if (initOK && intermediateFiles){
			initOK = initOK && Jerboa.getOutputManager().addFile(this.intermediateFileName,100);
			if (initOK) {
				String header = "Source";
				header += "," + "SubsetID";
				header += "," + "PatientID";
				header += "," + "Date";
				header += "," + "Duration";
				header += "," + "Combination";
				header += "," + "Dose";
				header += "," + "CombinationStartDate";
				Jerboa.getOutputManager().writeln(this.intermediateFileName, header, true);
			}
			else {
				error = "Could not create intermediate file " + this.intermediateFileName;
			}
		}

		if (!initOK) {
			Logging.add("Modifier " + this.title + ": " + error, Logging.ERROR);
		}

		return initOK;
	}


	@Override
	public Patient process(Patient patient) {
		if ((patient != null) && patient.isInPopulation() && patient.isInCohort()) {
			addCombinations(patient.ID, patient.prescriptions,"Prescriptions", mergeCombinations);
			if (Jerboa.unitTest) {
				exposureTimeLine = timeLine;
			}
			addCombinations(patient.ID, patient.originalPrescriptions,"Original Prescriptions", false);
			if (Jerboa.unitTest) {
				originalTimeLine = timeLine;
			}
		}
		return patient;
	}


	@Override
	public void outputResults() {
		Jerboa.getOutputManager().closeFile(this.intermediateFileName);
	}


	@Override
	public void setNeededFiles() {
		setRequiredFile(DataDefinition.PATIENTS_FILE);
		setRequiredFile(DataDefinition.PRESCRIPTIONS_FILE);
	}


	@Override
	public void setNeededExtendedColumns() {
	}


	@Override
	public void setNeededNumericColumns() { }

	/**
	 * Adds all the prescriptions to the timeline as drug groups
	 *
	 * @param patientID
	 * @param prescriptions
	 * @param intermediateLabel
	 * @param mergePrescriptions
	 */
	private void addCombinations(String patientID, List<Prescription> prescriptions, String intermediateLabel, boolean mergePrescriptions) {

		// Add prescriptions to timeline as drug group
		timeLine = new TimeLine();

		for (Prescription prescription : prescriptions) {
			String prescriptionATC = prescription.getATC();
			Set<String> drugGroups = drugGroupDefinitions.getDrugGroups(prescriptionATC);
			for (String drugGroup : drugGroups) {
				Prescription drugGroupPrescription = new Prescription(prescription);
				drugGroupPrescription.setATC(drugGroup);
				timeLine.addPrescription(drugGroupPrescription);
			}
		}

		if (debugPatientIDs.contains(patientID)){
			Logging.add(patientID+":"+intermediateLabel);
			Logging.add(timeLine.toString());
			Logging.addNewLine();
		}

		// scan each interval in the time for combinations of interest
		List<Prescription> addedPrescriptions = new ArrayList<Prescription>();

		Set<String> combinationATC = new HashSet<String>();
		Map<String, List<Prescription> > combinationPrescriptions = new HashMap<String, List<Prescription> >();
		for (Interval interval : timeLine.getTimeLine()) {
			Map<String, List<Prescription> > intervalATCMap = interval.getATCMap();
			for (Combination combination : combinationDefinitions.getCombinations()) {

				/* combinationsList contains for the combination of interest a list of prescriptions
				 * that are in the combination.
				 */

				List< List<Prescription> > combinationsList = new ArrayList< List<Prescription> >();
				List<Integer> combinationStartDates = new ArrayList<Integer>();
				/* create all possible subsets of prescriptions that could form the combination
				 * if foundAll = false the combination could not be formed from a subset
				 */
				boolean foundAll = true;
				for (String drugGroup : combination.getDrugGroupSet()) {
					if (!intervalATCMap.containsKey(drugGroup)) {
						foundAll = false;
						break;
					}
					else {
						if (combinationsList.size() == 0) {
							for (Prescription prescription : intervalATCMap.get(drugGroup)) {
								List<Prescription> prescriptionList = new ArrayList<Prescription>();
								prescriptionList.add(prescription);
								combinationsList.add(prescriptionList);
								combinationStartDates.add(prescription.getDate());
							}
						}
						else {
							List< List<Prescription> > newCombinationsList = new ArrayList< List<Prescription> >();
							List<Integer> newCombinationStartDates = new ArrayList<Integer>();
							for (Prescription newPrescription : intervalATCMap.get(drugGroup)) {
								int startDate = -1;
								for (int combinationNr = 0; combinationNr < combinationsList.size(); combinationNr++) {
									List<Prescription> combinations = combinationsList.get(combinationNr);
									List<Prescription> newCombination = new ArrayList<Prescription>();
									for (Prescription prescription : combinations) {
										newCombination.add(prescription);
									}
									startDate = combinationStartDates.get(combinationNr);
									newCombination.add(newPrescription);
									if (newPrescription.getDate() != startDate) {
										startDate = -1;
									}
									newCombinationsList.add(newCombination);
									newCombinationStartDates.add(startDate);
								}
							}
							combinationsList = newCombinationsList;
							combinationStartDates = newCombinationStartDates;
							newCombinationsList = null;
						}
					}
				}
				if (foundAll) {
					for (Integer startDate : combinationStartDates) {
						// create a new prescription based on the found subsets that could form the combination
						Prescription combinationPrescription = new Prescription();
						combinationPrescription.setPatientID(patientID);
						combinationPrescription.setATC(combination.getName());
						combinationPrescription.setDate(interval.startDate);
						combinationPrescription.setDuration(interval.endDate - interval.startDate);
						combinationPrescription.setCombinationStartDate(startDate);
						combinationPrescription.setDose("MIXED");
						combinationPrescription.setPrescriberId(interval.getPrescriberId());
						combinationPrescription.setPrescriberType(interval.getPrescriberType());

						// Set boolean to check if all components start on this startDate and collect the doses
						for (Prescription prescription : combinationsList.get(combinationStartDates.indexOf(startDate))) {
							if (prescription.getStartDate() != startDate || prescription.getAllComponentsStart()==false) {
								combinationPrescription.setAllComponentsStart(false);
								break;
							}
						}

						// Workaround to avoid duplicate entries
						boolean exists = false;
						for (Prescription prescription : addedPrescriptions){
							if (prescription.getATC() == combinationPrescription.getATC() &&
									prescription.getDate() == combinationPrescription.getDate() &&
									prescription.getDuration() == combinationPrescription.getDuration() &&
									(removeDifferentCompStarts || (prescription.getAllComponentsStart() == combinationPrescription.getAllComponentsStart()))) {
								exists = true;
								break;
							}
						}
						if (!exists) {
							addedPrescriptions.add(combinationPrescription);
							combinationATC.add(combinationPrescription.getATC());
						}

						// save all the found combinations to allow merge
						List<Prescription> combinations = combinationPrescriptions.get(combination.getName());
						if (combinations == null) {
							combinations = new ArrayList<Prescription>();
							combinationPrescriptions.put(combination.getName(), combinations);
						}
						combinations.add(combinationPrescription);
					}
				}
			}
		}

		// Added to avoid duplicates
		prescriptions.addAll(addedPrescriptions);

		if (mergePrescriptions) {
			Collections.sort(prescriptions);

			if (intermediateFiles) {
				for (Prescription prescription : prescriptions) {
					if (combinationATC.contains(prescription.getATC())) {
						Jerboa.getOutputManager().write(
								this.intermediateFileName,
								"BEFORE MERGE " + intermediateLabel
								+ "," + prescription.getSubset()
								+ "," + prescription.getPatientID()
								+ "," + DateUtilities.daysToDate(prescription.getDate())
								+ "," + prescription.getDuration()
								+ "," + prescription.getATC()
								+ "," + (prescription.hasDose() ? prescription.getDose() : "")
								+ "," + (prescription.getCombinationStartDate() == -1 ? "" : DateUtilities.daysToDate(prescription.getCombinationStartDate()))
								+ System.lineSeparator(),
								true);
					}
				}
			}

			// Merge consecutive prescriptions of the same combination
			for (String combinationName : combinationPrescriptions.keySet()) {
				List<Prescription> combinations = combinationPrescriptions.get(combinationName);
				Collections.sort(combinations);
				Prescription lastCombination = null;
				for (Prescription combination : combinations) {
					if (lastCombination != null) {

						//only consider if the enddate > the lastCombination
						// <------- drug ------->
						//		<----- drug----->
						// would otherwise stop the combination
						if (combination.getEndDate()>lastCombination.getEndDate()) {
							if (lastCombination.getEndDate() == combination.getDate()) {
								lastCombination.setDuration(lastCombination.getDuration() + combination.getDuration());
								prescriptions.remove(combination);
							}
							else {
								//	if (lastCombination.getDuration() < combinationDefinitions.getCombination(combinationName).getMinimumDuration()) {
								//		prescriptions.remove(lastCombination);
								//	}
								lastCombination = combination;
							}
						}
					}
					else {
						lastCombination = combination;
					}
				}
				//if ((lastCombination != null) && (lastCombination.getDuration() < combinationDefinitions.getCombination(combinationName).getMinimumDuration())) {
				//	prescriptions.remove(lastCombination);
				//}
			}
		}

		Collections.sort(prescriptions);

		if (intermediateFiles) {
			for (Prescription prescription : prescriptions) {
				if (combinationATC.contains(prescription.getATC())) {
					Jerboa.getOutputManager().write(
							this.intermediateFileName,
							intermediateLabel
							+ "," + prescription.getSubset()
							+ "," + prescription.getPatientID()
							+ "," + DateUtilities.daysToDate(prescription.getDate())
							+ "," + prescription.getDuration()
							+ "," + prescription.getATC()
							+ "," + (prescription.hasDose() ? prescription.getDose() : "")
							+ "," + (prescription.getCombinationStartDate() == -1 ? "" : DateUtilities.daysToDate(prescription.getCombinationStartDate()))
							+ System.lineSeparator(),
							true);
				}
			}
		}
	}


	public class TimeLine {
		private List<Interval> timeLine = new ArrayList<Interval>();


		public void addPrescription(Prescription prescription) {
			int exposureStart = prescription.getDate();
			int exposureEnd = prescription.getEndDate();

			int intervalNr = 0;
			while (intervalNr < timeLine.size()) {
				Interval interval = timeLine.get(intervalNr);
				if (((exposureEnd - exposureStart) > 0) && (exposureStart < interval.endDate)) {
					Interval newInterval = null;
					Interval newInterval2 = null;

					if (exposureStart > interval.startDate) {
						newInterval = interval.split(exposureStart);
						timeLine.add(intervalNr + 1, newInterval);
						newInterval2 = null;
						if (exposureEnd < newInterval.endDate) {
							newInterval2 = newInterval.split(exposureEnd);
							timeLine.add(intervalNr + 2, newInterval2);
							exposureStart = exposureEnd;
							intervalNr++;
						}
						else {
							exposureStart = newInterval.endDate;
							intervalNr++;
						}
						newInterval.addPrescription(prescription);
					}
					else {
						if (exposureEnd < interval.endDate) {
							newInterval = interval.split(exposureEnd);
							timeLine.add(intervalNr + 1, newInterval);
							exposureStart = exposureEnd;
						}
						else {
							exposureStart = interval.endDate;
						}
						interval.addPrescription(prescription);
					}
					//if (logDebugInfo > 3) {
					//	Logging.add("  -> " + interval.toString());
					//	if (newInterval != null) Logging.add("     " + newInterval.toString());
					//	if (newInterval2 != null) Logging.add("     " + newInterval2.toString());
					//}
				}
				intervalNr++;
			}

			if (exposureStart != exposureEnd) {
				//TODO Wijziging ongedaan gemaakt
				//Interval newInterval;
				//if (prescription.getStartDate() != exposureStart) {
				//	Prescription restPrescription = new Prescription(prescription);
				//	restPrescription.setAllComponentsStart(false);
				//	newInterval = new Interval(restPrescription);
				//}
				//else {
				//	newInterval = new Interval(prescription);
				//}
				//TODO
				Interval newInterval = new Interval(prescription);
				//TODO Wijziging ongedaan gemaakt
				newInterval.startDate = exposureStart;
				newInterval.endDate = exposureEnd;
				timeLine.add(newInterval);
			}
		}


		public List<Interval> getTimeLine() {
			return timeLine;
		}


		public String toString() {
			StrBuilder result = new StrBuilder();
			result.appendln("TimeLine");
			for (Interval interval : timeLine)
				result.appendln("  " + interval);
			return result.toString();
		}
	}


	public class Interval {
		private int startDate = -1;
		private int endDate = -1;
		private Set<String> atcSet = new HashSet<String>();
		private Map<String, List<Prescription> > atcMap = new HashMap<String, List<Prescription> >();
		private String prescriberId = "";
		private String prescriberType = "";


		public Interval(int start, int end) {
			startDate = start;
			endDate = end;
		}


		public Interval(Prescription prescription) {
			startDate = prescription.getDate();
			endDate = prescription.getEndDate();
			atcSet.add(prescription.getATC());
			List<Prescription> prescriptionList = atcMap.get(prescription.getATC());
			if (prescriptionList == null) {
				prescriptionList = new ArrayList<Prescription>();
				atcMap.put(prescription.getATC(), prescriptionList);
			}
			prescriptionList.add(prescription);
			if (prescriberId.equals("") && prescription.hasPrescriberId() && prescription.hasPrescriberType()) {
				prescriberId = prescription.getPrescriberId();
				prescriberType = prescription.getPrescriberType();
			}
		}


		public int getDuration() {
			return (endDate - startDate);
		}


		public Set<String> getATCSet() {
			return atcSet;
		}


		public Map<String, List<Prescription> > getATCMap() {
			return atcMap;
		}


		public void setPrescriberId(String id) {
			prescriberId = id;
		}


		public String getPrescriberId() {
			return prescriberId;
		}


		public void setPrescriberType(String type) {
			prescriberType = type;
		}


		public String getPrescriberType() {
			return prescriberType;
		}


		public Interval split(int date) {
			Interval newInterval = new Interval(date, endDate);
			endDate = date;
			for (String atc : atcSet) {
				newInterval.atcSet.add(atc);
				newInterval.setPrescriberId(prescriberId);
				newInterval.setPrescriberType(prescriberType);
			}
			for (String atc : atcMap.keySet()) {
				List<Prescription> prescriptionList = new ArrayList<Prescription>();
				for (Prescription prescription : atcMap.get(atc)) {
					//TODO Wijziging ongedaan gemaakt
					//Prescription newPrescription = new Prescription(prescription);
					//newPrescription.setAllComponentsStart(false);
					//prescriptionList.add(newPrescription);
					//TODO
					prescriptionList.add(prescription);
					//TODO Wijziging ongedaan gemaakt
				}
				newInterval.atcMap.put(atc, prescriptionList);
			}
			return newInterval;
		}


		public void addPrescription(Prescription prescription) {
			atcSet.add(prescription.getATC());
			List<Prescription> prescriptionList = atcMap.get(prescription.getATC());
			if (prescriptionList == null) {
				prescriptionList = new ArrayList<Prescription>();
				atcMap.put(prescription.getATC(), prescriptionList);
			}
			prescriptionList.add(prescription);
			if (prescriberId.equals("") && prescription.hasPrescriberId() && prescription.hasPrescriberType()) {
				prescriberId = prescription.getPrescriberId();
				prescriberType = prescription.getPrescriberType();
			}
		}


		public String toString() {
			String result = "";
			result += DateUtilities.daysToDate(startDate);
			result += "-";
			result += DateUtilities.daysToDate(endDate);
			result += " (";
			result += Integer.toString(getDuration());
			result += " days)";
			result += ": ";
			String resultATCs = "";
			for (String atc : atcSet) {
				if (!resultATCs.equals("")) {
					resultATCs += ", ";
				}
				resultATCs += atc;
			}
			result += resultATCs;
			return result;
		}
	}


	private class DrugGroups {
		private Map<String, Set<String> > drugGroupMap = new HashMap<String, Set<String> >();
		private boolean initOK = true;


		public DrugGroups(List<String> drugGroupDefinitions) {
			if (drugGroupDefinitions.size() > 0) {
				for (String drugGroupDefinition : drugGroupDefinitions) {
					String[] drugGroupDefinitionSplit = drugGroupDefinition.split(";");
					if ((drugGroupDefinitionSplit.length == 2) && (!drugGroupDefinitionSplit[0].equals(""))) {
						if (!drugGroupMap.containsKey("_"+drugGroupDefinitionSplit[0])) {
							Set<String> drugGroupATCs = new HashSet<String>();
							drugGroupMap.put("_"+drugGroupDefinitionSplit[0], drugGroupATCs);
							for (String atc : drugGroupDefinitionSplit[1].split(",")) {
								if (!atc.equals("")) {
									drugGroupATCs.add(atc);
								}
							}
						}
						else {
							error = "Duplicate drug group name: " + drugGroupDefinitionSplit[0];
							initOK = false;
							break;
						}
					}
					else {
						error = "Incorrect drug group definition: " + drugGroupDefinition;
						initOK = false;
						break;
					}
				}
			}
			else {
				error = "No drug groups defined";
				initOK = false;
			}
		}


		public boolean isOk() {
			return initOK;
		}


		public Set<String> getDrugGroups(String atc) {
			Set<String> matchingDrugGroups = new HashSet<String>();
			for (String drugGroup : drugGroupMap.keySet()) {
				for (String drugGroupAtc : drugGroupMap.get(drugGroup)) {
					if (atc.startsWith(drugGroupAtc)) {
						matchingDrugGroups.add(drugGroup);
						//TODO Wijziging ongedaan gemaakt
						//break;
						//TODO Wijziging ongedaan gemaakt
					}
				}
				//TODO Wijziging ongedaan gemaakt
				//if (matchingDrugGroups.size() > 0) {
				//	break;
				//}
				//TODO Wijziging ongedaan gemaakt
			}
			return matchingDrugGroups;
		}


		public String toString() {
			StrBuilder result = new StrBuilder();
			result.appendln("DrugGroups:");
			for (String drugGroupName : drugGroupMap.keySet()) {
				String drugGroupString = "";
				for (String atc : drugGroupMap.get(drugGroupName)) {
					drugGroupString += (drugGroupString.equals("") ? drugGroupName + " = " : ", ") + atc;
				}
				result.appendln("  " + drugGroupString);
			}
			return result.toString();
		}
	}


	private class Combinations {
		private List<Combination> combinationList = new ArrayList<Combination>();
		private Map<String, Combination> combinationIndex = new HashMap<String, Combination>();
		private boolean initOK = true;


		public Combinations(List<String> combinationDefinitions) {
			if (combinationDefinitions.size() > 0) {
				for (String combinationDefinition : combinationDefinitions) {
					String[] combinationDefinitionSplit = combinationDefinition.split(";");
					if ((combinationDefinitionSplit.length >= 2) && (!combinationDefinitionSplit[0].equals(""))) {
						//if (combinationNames.add(combinationDefinitionSplit[0])) {
						int minimumDuration = 0;
						if ((combinationDefinitionSplit.length >= 3) && (!combinationDefinitionSplit[2].equals(""))) {
							try {
								minimumDuration = Integer.parseInt(combinationDefinitionSplit[2]);
							}
							catch (NumberFormatException e) {
								minimumDuration = 0;
								Logging.add("Illegal minimum duration in combination definition: " + combinationDefinition);
							}
						}
						Combination combination = new Combination(combinationDefinitionSplit[0], combinationDefinitionSplit[1], minimumDuration);
						if (combination.isOk()) {
							combinationList.add(combination);
							combinationIndex.put(combination.getName(), combination);
						}
						else {
							initOK = false;
							break;
						}
						//}
						//else {
						//	error = "Duplicate combination name: " + combinationDefinitionSplit[0];
						//	initOK = false;
						//	break;
						//}
					}
					else {
						error = "Incorrect combination definition: " + combinationDefinition;
						initOK = false;
						break;
					}
				}
			}
			else {
				error = "No combinations defined";
				initOK = false;
			}
		}


		public boolean isOk() {
			Collections.sort(combinationList);
			return initOK;
		}


		public List<Combination> getCombinations() {
			return combinationList;
		}


		@SuppressWarnings("unused")
		public Combination getCombination(String combinationName) {
			return combinationIndex.get(combinationName);
		}


		public String toString() {
			StrBuilder result = new StrBuilder();
			result.appendln("Combinations:");
			for (Combination combination : combinationList) {
				result.appendln("  " + combination);
			}
			return result.toString();
		}
	}


	private class Combination implements Comparable<Combination> {
		private String name = "";
		private Set<String> drugGroupSet = new HashSet<String>();
		private int minimumDuration = 0;
		private boolean initOK = false;


		public Combination(String name, String drugGroups, int minimumDuration) {
			this.name = "_"+name;
			for (String drugGroup : drugGroups.split(",")) {
				if (!drugGroup.equals("")) {
					initOK = true;
					drugGroupSet.add("_"+drugGroup);
				}
			}
			this.minimumDuration = minimumDuration;
		}


		@SuppressWarnings("unused")
		public int getMinimumDuration() {
			return minimumDuration;
		}


		public boolean isOk() {
			return initOK;
		}


		public String getName() {
			return name;
		}


		public Set<String> getDrugGroupSet() {
			return drugGroupSet;
		}


		public int compareTo(Combination c) {
			return (c.drugGroupSet.size() - this.drugGroupSet.size());
		}


		public String toString() {
			String result = "";
			for (String drugGroup : drugGroupSet) {
				result += (result.equals("") ? name + " = " : ", ") + drugGroup;
			}
			if (minimumDuration > 0) {
				result += " at least " + minimumDuration + " days";
			}
			return result;
		}
	}


	// GETTERS FOR UNIT TEST

	public TimeLine getExposureTimeLine() {
		return exposureTimeLine;
	}

	public TimeLine getOriginalTimeLine() {
		return originalTimeLine;
	}


	//MAIN FOR DEBUGGING
	public static void main(String[] args) {
		PrescriptionCombinations pc = new PrescriptionCombinations();
		pc.drugGroups.add("A;A1,A2,A3");
		pc.drugGroups.add("B;B1,B2");
		pc.drugGroups.add("C;C1,C2,C3,C4");
		pc.combinations = new ArrayList<String>();
		pc.combinations.add("A+B;A,B;20");
		//pc.combinations.add("A+B+C;A,B,C");
		pc.intermediateFiles = false;
		pc.intermediateStats = false;
		pc.init();

		List<Prescription> pList = new ArrayList<Prescription>();
		Prescription p;
		p = new Prescription(); p.setPatientID("1"); p.setATC("A1"); p.setDate(DateUtilities.dateToDays(new int[] { 2012,  1,  1 })); p.setDuration(55); p.setDose("1"); pList.add(p);
		p = new Prescription(); p.setPatientID("1"); p.setATC("B1"); p.setDate(DateUtilities.dateToDays(new int[] { 2012,  2,  1 })); p.setDuration(43); p.setDose("1"); pList.add(p);
		p = new Prescription(); p.setPatientID("1"); p.setATC("C1"); p.setDate(DateUtilities.dateToDays(new int[] { 2012,  2, 15 })); p.setDuration(90); p.setDose("1"); pList.add(p);

		pc.addCombinations("1", pList, "plist", true);

		System.out.println(pList.toString());
	}

}
