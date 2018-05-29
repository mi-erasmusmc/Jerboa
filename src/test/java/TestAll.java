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
 * $Rev:: 3848              $:  Revision of last commit                                   *
 * $Author:: root           $:  Author of last commit                                     *
 * $Date::					$:  Date and time (CET) of last commit						  *
 ******************************************************************************************/

package test.java;

import static org.junit.Assert.*;

import java.util.Collections;
import java.util.List;

import org.erasmusmc.jerboa.Jerboa;
import org.erasmusmc.jerboa.config.DataDefinition;
import org.erasmusmc.jerboa.dataClasses.Event;
import org.erasmusmc.jerboa.dataClasses.Measurement;
import org.erasmusmc.jerboa.dataClasses.Patient;
import org.erasmusmc.jerboa.dataClasses.Prescription;
import org.erasmusmc.jerboa.utilities.DateUtilities;
import org.junit.runner.RunWith;
import org.junit.runners.Suite;
import org.junit.runners.Suite.SuiteClasses;

import test.java.core.DateTest;
import test.java.modifiers.BMICalculation2Test;
import test.java.modifiers.PrescriptionCohortDefinitionTest;
import test.java.modifiers.EventMappingTest;
import test.java.modifiers.FixedCohortDefinitionTest;
import test.java.modifiers.MeasurementCategoriesTest;
import test.java.modifiers.MeasurementCleanerTest;
import test.java.modifiers.MeasurementCohortDefinition2Test;
import test.java.modifiers.MeasurementCountCohortDefinitionTest;
import test.java.modifiers.PopulationDefinitionTest;
import test.java.modifiers.PrescriptionCombinationsTest;
import test.java.modules.CodeCountingTest;

/**
 * Will run all the tests that are enumerated inside the SuiteClasses.
 * This is to be used when all tests are to be performed.
 *
 * @author MG
 *
 */
@RunWith(Suite.class)
@SuiteClasses({ //Filters
				BMICalculation2Test.class,
				DateTest.class,
				EventMappingTest.class,
				FixedCohortDefinitionTest.class,
				MeasurementCategoriesTest.class,
				MeasurementCleanerTest.class,
				MeasurementCohortDefinition2Test.class,
				MeasurementCountCohortDefinitionTest.class,
				PopulationDefinitionTest.class,
				PrescriptionCohortDefinitionTest.class,
				PrescriptionCombinationsTest.class,
				//Modules
				CodeCountingTest.class
				})


public class TestAll {

	public static Patient createPatient(String id, String birthDate, String gender, String startDate, String endDate) {
		return createPatient(id, birthDate, gender, startDate, endDate, startDate, endDate);
	}

	public static Patient createPatient(String id, String birthDate, String gender, String startDate, String endDate, String populationStartDate, String populationEndDate) {
		return createPatient(id, birthDate, gender, startDate, endDate, populationStartDate, populationEndDate, populationStartDate, populationEndDate);
	}

	public static Patient createPatient(String id, String birthDate, String gender, String startDate, String endDate, String populationStartDate, String populationEndDate, String cohortStartDate, String cohortEndDate) {
		Patient p = new Patient();
		p.ID = id;
		p.birthDate = DateUtilities.dateToDaysUnitTest(birthDate);
		p.gender = (gender.substring(0, 1).equals("M") ? DataDefinition.MALE_GENDER : (gender.substring(0, 1).equals("F") ? DataDefinition.FEMALE_GENDER : DataDefinition.UNKNOWN_GENDER));
		p.startDate = DateUtilities.dateToDaysUnitTest(startDate);
		p.endDate = DateUtilities.dateToDaysUnitTest(endDate);
		if ((!populationStartDate.equals("")) && (!populationEndDate.equals(""))) {
			p.populationStartDate = DateUtilities.dateToDaysUnitTest(populationStartDate);
			p.populationEndDate = DateUtilities.dateToDaysUnitTest(populationEndDate);
			p.inPopulation = true;

			if ((!cohortStartDate.equals("")) && (!cohortEndDate.equals(""))) {
				p.cohortStartDate = DateUtilities.dateToDaysUnitTest(cohortStartDate);
				p.cohortEndDate = DateUtilities.dateToDaysUnitTest(cohortEndDate);
				p.inCohort = true;
			}
			else {
				p.inCohort = false;
			}
		}
		else {
			p.inPopulation = false;
			p.inCohort = false;
		}
		return p;
	}

	public static Event createEvent(Patient patient, String date, String type, String code) {
		Event e = new Event();
		e.setPatientID(patient.getPatientID());
		e.setDate(DateUtilities.dateToDaysUnitTest(date));
		e.setType(type);
		e.setCode(code);
		List<Event> events = patient.getEvents();
		events.add(e);
		Collections.sort(events);
		patient.setEvents(events);
		return e;
	}

	public static Prescription createPrescription(Patient patient, String atc, String date, int duration, String prescriberId, String prescriberType) {
		Prescription p = new Prescription();
		p.setPatientID(patient.getPatientID());
		p.setATC(atc);
		p.setDate(DateUtilities.dateToDaysUnitTest(date));
		p.setDuration(duration);
		p.setPrescriberId(prescriberId);
		p.setPrescriberType(prescriberType);
		List<Prescription> prescriptions = patient.getPrescriptions();
		prescriptions.add(p);
		Collections.sort(prescriptions);
		patient.setPrescriptions(prescriptions);
		patient.getOriginalPrescriptions().add(new Prescription(p));
		Collections.sort(patient.getOriginalPrescriptions());
		return p;
	}

	public static Measurement createMeasurement(Patient patient, String date, String measurementType, String value) {
		Measurement m = new Measurement();
		m.setPatientID(patient.getPatientID());
		m.setDate(DateUtilities.dateToDaysUnitTest(date));
		m.setType(measurementType);
		m.setValue(value);
		List<Measurement> measurements = patient.getMeasurements();
		measurements.add(m);
		Collections.sort(measurements);
		patient.setMeasurements(measurements);
		return m;
	}

	public static void assertFile(String fileName, List<String> verificationContents) {
		List<String> fileContents = Jerboa.getOutputManager().getFile(fileName);
		assertTrue("File " + fileName + " has incorrect size.", fileContents.size() == verificationContents.size());
		if (fileContents.size() == verificationContents.size()) {
			for (int lineNr = 0; lineNr < fileContents.size(); lineNr++) {
				assertTrue("File " + fileName + " line " + Integer.toString(lineNr) + " not equal.", fileContents.get(lineNr).equals(verificationContents.get(lineNr)));
			}
		}
	}
}
