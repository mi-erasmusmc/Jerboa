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
 * $Rev:: 3810              $:  Revision of last commit                                   *
 * $Author:: root           $:  Author of last commit                                     *
 * $Date:: 					$:  Date and time (CET) of last commit						  *
 ******************************************************************************************/

package org.erasmusmc.jerboa.utilities.stats;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.Iterator;
import java.util.TreeSet;

import org.erasmusmc.jerboa.config.Parameters;
import org.erasmusmc.jerboa.dataClasses.Event;
import org.erasmusmc.jerboa.dataClasses.Measurement;
import org.erasmusmc.jerboa.dataClasses.Patient;
import org.erasmusmc.jerboa.dataClasses.Prescription;
import org.erasmusmc.jerboa.utilities.DateUtilities;
import org.erasmusmc.jerboa.utilities.ExtendedMultiKey;
import org.erasmusmc.jerboa.utilities.Logging;
import org.erasmusmc.jerboa.utilities.MultiKeyBag;

/**
 * This class contains all the data counters and statistics related to the raw input data.
 * Manipulate with care and document modifications.
 *
 * @author MG
 *
 */
public class Stats {

	//BASIC STATS OF THE INPUT DATA
	public static long nbRecords = 0;

	public static long nbPatients = 0;
	public static long nbEvents = 0;
	public static long nbPrescriptions = 0;
	public static long nbMeasurements = 0;

	public static int nbUnassignedEvents = 0;
	public static int nbUnassignedPrescriptions = 0;
	public static int nbUnassignedMeasurements = 0;

	public static long nbFemales = 0;
	public static long nbMales = 0;
	public static long nbUnknown = 0;

	public static long nbEventTypes = 0;
	public static long nbATCcodes = 0;
	public static long nbMeasurementTypes = 0;

	public static double totalPrescriptionDuration = 0;

	//date related - first and last
	static int firstDateInDays = DateUtilities.dateToDays(Parameters.MIN_LEGAL_DATE, DateUtilities.DATE_ON_YYYYMMDD);
	static int lastDateInDays = DateUtilities.dateToDays(Parameters.MAX_LEGAL_DATE, DateUtilities.DATE_ON_YYYYMMDD);

	public static int firstStartDate = lastDateInDays;
	public static int lastStartDate = firstDateInDays;

	public static int firstEndDate = lastDateInDays;
	public static int lastEndDate = firstDateInDays;

	public static int firstBirthDate = firstDateInDays;
	public static int lastBirthDate = firstDateInDays;

	public static int firstEvent = firstDateInDays;
	public static int lastEvent = firstDateInDays;

	public static int firstPrescription = firstDateInDays;
	public static int lastPrescription = firstDateInDays;

	public static int firstMeasurement = firstDateInDays;
	public static int lastMeasurement = firstDateInDays;

	//description of active patient population
	public static MultiKeyBag activePatients;

	//CONSTRUCTOR
	public Stats(){
		activePatients = new MultiKeyBag(); // gender, year
	}

	/**
	 * Used to create the histogram of the active patients over calendar years.
	 * @param patient - a patient to be added to the list based on its observation time.
	 */
	public void countActivePatients(Patient patient){
		int yearStart = patient.getStartYear();
		int yearEnd = patient.getEndYear();
		for (int i = yearStart; i<= yearEnd; i++){
			if (patient.getPatientTimeInYear(i)>0)
			  activePatients.add(new ExtendedMultiKey(patient.gender, i));
		}
	}

	/*-------RESET COUNTERS AT OBJECT LEVEL (e.g., event, prescription)----------*/

	/**
	 * Updates the statistics regarding patient details using the attributes of patient.
	 * @param patient - the patient used for the update.
	 */
	public static void updatePatientStats(Patient patient){

		nbPatients ++;
		nbRecords ++;

		//birth date
		firstBirthDate = patient.birthDate < firstBirthDate ? patient.birthDate : firstBirthDate;
		lastBirthDate = patient.birthDate < lastBirthDate ? patient.birthDate : lastBirthDate;

		//start date
		firstStartDate = patient.startDate < firstStartDate ? patient.startDate : firstStartDate;
		lastStartDate = patient.startDate > lastStartDate ? patient.startDate : lastStartDate;

		//end date
		firstEndDate = patient.endDate < firstEndDate ? patient.endDate : firstEndDate;
		lastEndDate = patient.endDate > lastEndDate ? patient.endDate : lastEndDate;

		//gender
		if (patient != null && patient.gender == 'm')
			nbMales ++;
		else if (patient != null && patient.gender == 'f')
			nbFemales ++;
		else
			nbUnknown ++;

	}

	/**
	 * Updates the statistics regarding the events details using the attributes of patient.
	 * @param event - the event used for the update
	 */
	public static void updateEventStats(Event event){

		nbEvents ++;
		nbRecords ++;

		firstEvent = event.date < firstEvent ? event.date : firstEvent;
		lastEvent = event.date > lastEvent ? event.date : lastEvent;

	}

	/**
	 * Updates the statistics regarding the prescriptions details using the attributes of patient.
	 * @param prescription - the prescription used for the update
	 */
	public static void updatePrescriptionStats(Prescription prescription){

		nbPrescriptions ++;
		nbRecords ++;

		firstPrescription = prescription.date < firstPrescription ? prescription.date : firstPrescription;
		lastPrescription = prescription.date < lastPrescription ? prescription.date : lastPrescription;

		totalPrescriptionDuration += (double)(prescription.getDuration());

	}

	/**
	 * Updates the statistics regarding the measurements details using the attributes of patient.
	 * @param measurement - the measurement used for the update
	 */
	public static void updateMeasurementStats(Measurement measurement){

		nbMeasurements ++;
		nbRecords ++;

		firstMeasurement = measurement.date < firstMeasurement ? measurement.date : firstMeasurement;
		lastMeasurement = measurement.date > lastMeasurement ? measurement.date : lastMeasurement;

	}

	// GETTERS AND SETTERS
	public static MultiKeyBag getActivePatients() {
		return activePatients;
	}

	public static void setActivePatients(MultiKeyBag activePatients) {
		Stats.activePatients = activePatients;
	}

	// SAVE STATS TO FILE AND RESTORE STATS FROM FILE
	public static void saveStats(String path) {
		String statsFileName = path + "Stats.txt";
		try {
			PrintWriter statsFile = new PrintWriter(statsFileName);

			statsFile.println("nbRecords=" + nbRecords);

			statsFile.println("nbPatients=" + nbPatients);
			statsFile.println("nbEvents=" + nbEvents);
			statsFile.println("nbPrescriptions=" + nbPrescriptions);
			statsFile.println("nbMeasurements=" + nbMeasurements);

			statsFile.println("nbUnassignedEvents=" + nbUnassignedEvents);
			statsFile.println("nbUnassignedPrescriptions=" + nbUnassignedPrescriptions);
			statsFile.println("nbUnassignedMeasurements=" + nbUnassignedMeasurements);

			statsFile.println("nbFemales=" + nbFemales);
			statsFile.println("nbMales=" + nbMales);
			statsFile.println("nbUnknown=" + nbUnknown);

			statsFile.println("nbEventTypes=" + nbEventTypes);
			statsFile.println("nbATCcodes=" + nbATCcodes);
			statsFile.println("nbMeasurementTypes=" + nbMeasurementTypes);

			statsFile.println("totalPrescriptionDuration=" + totalPrescriptionDuration);

			//date related - first and last
			statsFile.println("firstDateInDays=" + firstDateInDays);
			statsFile.println("lastDateInDays=" + lastDateInDays);

			statsFile.println("firstStartDate=" + firstStartDate);
			statsFile.println("lastStartDate=" + lastStartDate);

			statsFile.println("firstEndDate=" + firstEndDate);
			statsFile.println("lastEndDate=" + lastEndDate);

			statsFile.println("firstBirthDate=" + firstBirthDate);
			statsFile.println("lastBirthDate=" + lastBirthDate);

			statsFile.println("firstEvent=" + firstEvent);
			statsFile.println("lastEvent=" + lastEvent);

			statsFile.println("firstPrescription=" + firstPrescription);
			statsFile.println("lastPrescription=" + lastPrescription);

			statsFile.println("firstMeasurement=" + firstMeasurement);
			statsFile.println("lastMeasurement=" + lastMeasurement);

			//description of active patient population
			TreeSet<Object> genders = activePatients.getKeyValuesAsObject(0);
			Iterator<Object> genderIterator = genders.iterator();
			while (genderIterator.hasNext()) {
				byte gender = (Byte) genderIterator.next();
				TreeSet<Object> years = activePatients.getKeyValuesAsObject(1);
				Iterator<Object> yearIterator = years.iterator();
				while (yearIterator.hasNext()) {
					int year = (Integer) yearIterator.next();
					statsFile.println("activePatients=" + gender + "," + year + "," + activePatients.getCount(new ExtendedMultiKey(gender, year)));
				}
			}

			statsFile.close();
		}
		catch (FileNotFoundException e) {
			Logging.add("Couldn't save input statistics in file " + path + "/" + "Stats.txt");
		}

	}

	public static void restoreStats(String path) {
		Logging.add("Restoring input file stats.");
		String statsFileName = path + "Stats.txt";
		File statsFile = new File(statsFileName);

	    if (statsFile.canRead()) {
	    	try {
				BufferedReader statsBuffer = new BufferedReader(new FileReader(statsFileName));
				try {
					String line = "";
					do {
						line = statsBuffer.readLine();
						if (line != null) {
							String[] lineSplit = line.split("=");
							if (lineSplit.length == 2) {

								if (lineSplit[0].equals("nbRecords")) nbRecords = Long.parseLong(lineSplit[1]);

								if (lineSplit[0].equals("nbPatients")) nbPatients = Long.parseLong(lineSplit[1]);
								if (lineSplit[0].equals("nbEvents")) nbEvents = Long.parseLong(lineSplit[1]);
								if (lineSplit[0].equals("nbPrescriptions")) nbPrescriptions = Long.parseLong(lineSplit[1]);
								if (lineSplit[0].equals("nbMeasurements")) nbMeasurements = Long.parseLong(lineSplit[1]);

								if (lineSplit[0].equals("nbUnassignedEvents")) nbUnassignedEvents = Integer.parseInt(lineSplit[1]);
								if (lineSplit[0].equals("nbUnassignedPrescriptions")) nbUnassignedPrescriptions = Integer.parseInt(lineSplit[1]);
								if (lineSplit[0].equals("nbUnassignedMeasurements")) nbUnassignedMeasurements = Integer.parseInt(lineSplit[1]);

								if (lineSplit[0].equals("nbFemales")) nbFemales = Long.parseLong(lineSplit[1]);
								if (lineSplit[0].equals("nbMales")) nbMales = Long.parseLong(lineSplit[1]);
								if (lineSplit[0].equals("nbUnknown")) nbUnknown = Long.parseLong(lineSplit[1]);

								if (lineSplit[0].equals("nbEventTypes")) nbEventTypes = Long.parseLong(lineSplit[1]);
								if (lineSplit[0].equals("nbATCcodes")) nbATCcodes = Long.parseLong(lineSplit[1]);
								if (lineSplit[0].equals("nbMeasurementTypes")) nbMeasurementTypes = Long.parseLong(lineSplit[1]);

								if (lineSplit[0].equals("totalPrescriptionDuration")) totalPrescriptionDuration = Double.parseDouble(lineSplit[1]);

								//date related - first and last
								if (lineSplit[0].equals("firstDateInDays")) firstDateInDays = Integer.parseInt(lineSplit[1]);
								if (lineSplit[0].equals("lastDateInDays")) lastDateInDays = Integer.parseInt(lineSplit[1]);

								if (lineSplit[0].equals("firstStartDate")) firstStartDate = Integer.parseInt(lineSplit[1]);
								if (lineSplit[0].equals("lastStartDate")) lastStartDate = Integer.parseInt(lineSplit[1]);

								if (lineSplit[0].equals("firstEndDate")) firstEndDate = Integer.parseInt(lineSplit[1]);
								if (lineSplit[0].equals("lastEndDate")) lastEndDate = Integer.parseInt(lineSplit[1]);

								if (lineSplit[0].equals("firstBirthDate")) firstBirthDate = Integer.parseInt(lineSplit[1]);
								if (lineSplit[0].equals("lastBirthDate")) lastBirthDate = Integer.parseInt(lineSplit[1]);

								if (lineSplit[0].equals("firstEvent")) firstEvent = Integer.parseInt(lineSplit[1]);
								if (lineSplit[0].equals("lastEvent")) lastEvent = Integer.parseInt(lineSplit[1]);

								if (lineSplit[0].equals("firstPrescription")) firstPrescription = Integer.parseInt(lineSplit[1]);
								if (lineSplit[0].equals("lastPrescription")) lastPrescription = Integer.parseInt(lineSplit[1]);

								if (lineSplit[0].equals("firstMeasurement")) firstMeasurement = Integer.parseInt(lineSplit[1]);
								if (lineSplit[0].equals("lastMeasurement")) lastMeasurement = Integer.parseInt(lineSplit[1]);

								if (lineSplit[0].equals("activePatients")) {
									String[] multiKeySplit = lineSplit[1].split(",");
									byte gender = Byte.parseByte(multiKeySplit[0]);
									int year = Integer.parseInt(multiKeySplit[1]);
									long count = Long.parseLong(multiKeySplit[2]);
									for (long n = 0; n < count; n++) {
										  activePatients.add(new ExtendedMultiKey(gender, year));
									}
								}

							}
						}
					} while (line != null);
				} finally {
					if (statsBuffer != null)
						statsBuffer.close();
				}
			} catch (FileNotFoundException e) {
				Logging.add("Couldn't restore input statistics from file " + statsFileName);
			} catch (IOException e) {
				Logging.add("Couldn't restore input statistics from file " + statsFileName);
			}

		}

	}

}
