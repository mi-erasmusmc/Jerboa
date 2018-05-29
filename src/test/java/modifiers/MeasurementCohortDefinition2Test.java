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

package test.java.modifiers;

import static org.junit.Assert.*;

import org.erasmusmc.jerboa.Jerboa;
import org.erasmusmc.jerboa.dataClasses.Measurement;
import org.erasmusmc.jerboa.dataClasses.Patient;
import org.erasmusmc.jerboa.modifiers.MeasurementCohortDefinition2;
import org.erasmusmc.jerboa.utilities.DateUtilities;
import org.junit.Before;
import org.junit.Test;

import test.java.TestAll;

public class MeasurementCohortDefinition2Test {

	/**
	 * @throws java.lang.Exception
	 */
	@Before
	public void setUp() throws Exception {
		Jerboa.unitTest = true;
	}

	@Test
	public void testLastNoMeasurements() {

		Patient p = TestAll.createPatient("1", "19640630", "M", "20000101", "20160101", "20000101", "20160101");

		TestAll.createEvent(p, "20021101", "CVD", "none");

		// Measurement sbp2 should be removed
		MeasurementCohortDefinition2 mcd2 = new MeasurementCohortDefinition2();
		mcd2.chainCohortDefinitions = false;
		mcd2.measurementsOfInterest.add("BMI");
		mcd2.useFirstMeasurement = false;
		mcd2.allHistory = false;
		mcd2.beforeEvents.add("CVD");
		mcd2.minimumDaysOfCohortTime = 1;
		mcd2.attritionFile = false;
		assertEquals(true, mcd2.init());
		p = mcd2.process(p);

		assertEquals(false, p.isInCohort());
	}

	@Test
	public void testLastNoMinimalCohortTime() {

		Patient p = TestAll.createPatient("1", "19640630", "M", "20000101", "20160101", "20000101", "20160101");

		TestAll.createMeasurement(p, "20010401", "BMI", "25");
		TestAll.createMeasurement(p, "20020601", "BMI", "27");
		TestAll.createMeasurement(p, "20151201", "BMI", "26");

		// Measurement sbp2 should be removed
		MeasurementCohortDefinition2 mcd2 = new MeasurementCohortDefinition2();
		mcd2.chainCohortDefinitions = false;
		mcd2.measurementsOfInterest.add("BMI");
		mcd2.useFirstMeasurement = false;
		mcd2.allHistory = false;
		mcd2.beforeEvents.add("CVD");
		mcd2.minimumDaysOfCohortTime = 40;
		mcd2.attritionFile = false;
		assertEquals(true, mcd2.init());
		p = mcd2.process(p);

		assertEquals(false, p.isInCohort());
	}

	@Test
	public void testLastNoEvent() {

		Patient p = TestAll.createPatient("1", "19640630", "M", "20000101", "20160101", "20000101", "20160101");

		TestAll.createMeasurement(p, "20010401", "BMI", "25");
		TestAll.createMeasurement(p, "20020601", "BMI", "27");
		TestAll.createMeasurement(p, "20030801", "BMI", "26");

		// Measurement sbp2 should be removed
		MeasurementCohortDefinition2 mcd2 = new MeasurementCohortDefinition2();
		mcd2.chainCohortDefinitions = false;
		mcd2.measurementsOfInterest.add("BMI");
		mcd2.useFirstMeasurement = false;
		mcd2.allHistory = false;
		mcd2.beforeEvents.add("CVD");
		mcd2.minimumDaysOfCohortTime = 1;
		mcd2.attritionFile = false;
		assertEquals(true, mcd2.init());
		p = mcd2.process(p);

		assertEquals(true, p.isInCohort());
		assertEquals("20030801", DateUtilities.daysToDate(p.getCohortStartDate()));
	}

	@Test
	public void testLastWithEvent() {

		Patient p = TestAll.createPatient("1", "19640630", "M", "20000101", "20160101", "20000101", "20160101");

		TestAll.createMeasurement(p, "20010401", "BMI", "25");
		Measurement bmi2 = TestAll.createMeasurement(p, "20020601", "BMI", "27");
		TestAll.createMeasurement(p, "20030801", "BMI", "26");

		TestAll.createEvent(p, "20021101", "CVD", "none");

		// Measurement sbp2 should be removed
		MeasurementCohortDefinition2 mcd2 = new MeasurementCohortDefinition2();
		mcd2.chainCohortDefinitions = false;
		mcd2.measurementsOfInterest.add("BMI");
		mcd2.useFirstMeasurement = false;
		mcd2.allHistory = false;
		mcd2.beforeEvents.add("CVD");
		mcd2.minimumDaysOfCohortTime = 1;
		mcd2.attritionFile = false;
		assertEquals(true, mcd2.init());
		p = mcd2.process(p);

		assertEquals(true, p.isInCohort());
		assertEquals("20020601", DateUtilities.daysToDate(p.getCohortStartDate()));
		assertEquals(bmi2, mcd2.getLastMeasurement());
	}

	@Test
	public void testLastWithEventAndMeasuementValue() {

		Patient p = TestAll.createPatient("1", "19640630", "M", "20000101", "20160101", "20000101", "20160101");

		Measurement bmi1 = TestAll.createMeasurement(p, "20010401", "BMI", "25");
		TestAll.createMeasurement(p, "20020601", "BMI", "27");
		TestAll.createMeasurement(p, "20030801", "BMI", "25");

		TestAll.createEvent(p, "20021101", "CVD", "none");

		// Measurement sbp2 should be removed
		MeasurementCohortDefinition2 mcd2 = new MeasurementCohortDefinition2();
		mcd2.chainCohortDefinitions = false;
		mcd2.measurementsOfInterest.add("BMI;25");
		mcd2.useFirstMeasurement = false;
		mcd2.allHistory = false;
		mcd2.beforeEvents.add("CVD");
		mcd2.minimumDaysOfCohortTime = 1;
		mcd2.attritionFile = false;
		assertEquals(true, mcd2.init());
		p = mcd2.process(p);

		assertEquals(true, p.isInCohort());
		assertEquals("20010401", DateUtilities.daysToDate(p.getCohortStartDate()));
		assertEquals(bmi1, mcd2.getLastMeasurement());
	}

	@Test
	public void testLastNoMeasurementBeforeEvent() {

		Patient p = TestAll.createPatient("1", "19640630", "M", "20000101", "20160101", "20000101", "20160101");

		TestAll.createMeasurement(p, "20010401", "BMI", "25");
		TestAll.createMeasurement(p, "20020601", "BMI", "27");
		TestAll.createMeasurement(p, "20030801", "BMI", "26");

		TestAll.createEvent(p, "20010101", "CVD", "none");

		// Measurement sbp2 should be removed
		MeasurementCohortDefinition2 mcd2 = new MeasurementCohortDefinition2();
		mcd2.chainCohortDefinitions = false;
		mcd2.measurementsOfInterest.add("BMI");
		mcd2.useFirstMeasurement = false;
		mcd2.allHistory = false;
		mcd2.beforeEvents.add("CVD");
		mcd2.minimumDaysOfCohortTime = 1;
		mcd2.attritionFile = false;
		assertEquals(true, mcd2.init());
		p = mcd2.process(p);

		assertEquals(false, p.isInCohort());
	}


	@Test
	public void testFirstNoMeasurements() {

		Patient p = TestAll.createPatient("1", "19640630", "M", "20000101", "20160101", "20000101", "20160101");

		TestAll.createEvent(p, "20021101", "CVD", "none");

		// Measurement sbp2 should be removed
		MeasurementCohortDefinition2 mcd2 = new MeasurementCohortDefinition2();
		mcd2.chainCohortDefinitions = false;
		mcd2.measurementsOfInterest.add("BMI");
		mcd2.useFirstMeasurement = true;
		mcd2.allHistory = false;
		mcd2.beforeEvents.add("CVD");
		mcd2.minimumDaysOfCohortTime = 1;
		mcd2.attritionFile = false;
		assertEquals(true, mcd2.init());
		p = mcd2.process(p);

		assertEquals(false, p.isInCohort());
	}

	@Test
	public void testFirstNoMinimalCohortTime() {

		Patient p = TestAll.createPatient("1", "19640630", "M", "20000101", "20160101", "20000101", "20160101");

		TestAll.createMeasurement(p, "20151201", "BMI", "26");
		TestAll.createMeasurement(p, "20151220", "BMI", "25");

		// Measurement sbp2 should be removed
		MeasurementCohortDefinition2 mcd2 = new MeasurementCohortDefinition2();
		mcd2.chainCohortDefinitions = false;
		mcd2.measurementsOfInterest.add("BMI");
		mcd2.useFirstMeasurement = true;
		mcd2.allHistory = false;
		mcd2.beforeEvents.add("CVD");
		mcd2.minimumDaysOfCohortTime = 40;
		mcd2.attritionFile = false;
		assertEquals(true, mcd2.init());
		p = mcd2.process(p);

		assertEquals(false, p.isInCohort());
	}

	@Test
	public void testFirstNoEvent() {

		Patient p = TestAll.createPatient("1", "19640630", "M", "20000101", "20160101", "20000101", "20160101");

		TestAll.createMeasurement(p, "20010401", "BMI", "25");
		TestAll.createMeasurement(p, "20020601", "BMI", "27");
		TestAll.createMeasurement(p, "20030801", "BMI", "26");

		// Measurement sbp2 should be removed
		MeasurementCohortDefinition2 mcd2 = new MeasurementCohortDefinition2();
		mcd2.chainCohortDefinitions = false;
		mcd2.measurementsOfInterest.add("BMI");
		mcd2.useFirstMeasurement = true;
		mcd2.allHistory = false;
		mcd2.beforeEvents.add("CVD");
		mcd2.minimumDaysOfCohortTime = 1;
		mcd2.attritionFile = false;
		assertEquals(true, mcd2.init());
		p = mcd2.process(p);

		assertEquals(true, p.isInCohort());
		assertEquals("20010401", DateUtilities.daysToDate(p.getCohortStartDate()));
	}

	@Test
	public void testFirstWithEvent() {

		Patient p = TestAll.createPatient("1", "19640630", "M", "20000101", "20160101", "20000101", "20160101");

		Measurement bmi1 = TestAll.createMeasurement(p, "20010401", "BMI", "25");
		TestAll.createMeasurement(p, "20020601", "BMI", "27");
		TestAll.createMeasurement(p, "20030801", "BMI", "26");

		TestAll.createEvent(p, "20021101", "CVD", "none");

		// Measurement sbp2 should be removed
		MeasurementCohortDefinition2 mcd2 = new MeasurementCohortDefinition2();
		mcd2.chainCohortDefinitions = false;
		mcd2.measurementsOfInterest.add("BMI");
		mcd2.useFirstMeasurement = true;
		mcd2.allHistory = false;
		mcd2.beforeEvents.add("CVD");
		mcd2.minimumDaysOfCohortTime = 1;
		mcd2.attritionFile = false;
		assertEquals(true, mcd2.init());
		p = mcd2.process(p);

		assertEquals(true, p.isInCohort());
		assertEquals("20010401", DateUtilities.daysToDate(p.getCohortStartDate()));
		assertEquals(bmi1, mcd2.getFirstMeasurement());
	}

	@Test
	public void testFirstWithEventAndMeasuementValue() {

		Patient p = TestAll.createPatient("1", "19640630", "M", "20000101", "20160101", "20000101", "20160101");

		TestAll.createMeasurement(p, "20010401", "BMI", "27");
		Measurement bmi2 = TestAll.createMeasurement(p, "20020601", "BMI", "25");
		TestAll.createMeasurement(p, "20030801", "BMI", "25");

		TestAll.createEvent(p, "20041101", "CVD", "none");

		// Measurement sbp2 should be removed
		MeasurementCohortDefinition2 mcd2 = new MeasurementCohortDefinition2();
		mcd2.chainCohortDefinitions = false;
		mcd2.measurementsOfInterest.add("BMI;25");
		mcd2.useFirstMeasurement = true;
		mcd2.allHistory = false;
		mcd2.beforeEvents.add("CVD");
		mcd2.minimumDaysOfCohortTime = 1;
		mcd2.attritionFile = false;
		assertEquals(true, mcd2.init());
		p = mcd2.process(p);

		assertEquals(true, p.isInCohort());
		assertEquals("20020601", DateUtilities.daysToDate(p.getCohortStartDate()));
		assertEquals(bmi2, mcd2.getFirstMeasurement());
	}

	@Test
	public void testFirstNoMeasurementBeforeEvent() {

		Patient p = TestAll.createPatient("1", "19640630", "M", "20000101", "20160101", "20000101", "20160101");

		TestAll.createMeasurement(p, "20010401", "BMI", "25");
		TestAll.createMeasurement(p, "20020601", "BMI", "27");
		TestAll.createMeasurement(p, "20030801", "BMI", "26");

		TestAll.createEvent(p, "20010101", "CVD", "none");

		// Measurement sbp2 should be removed
		MeasurementCohortDefinition2 mcd2 = new MeasurementCohortDefinition2();
		mcd2.chainCohortDefinitions = false;
		mcd2.measurementsOfInterest.add("BMI");
		mcd2.useFirstMeasurement = true;
		mcd2.allHistory = false;
		mcd2.beforeEvents.add("CVD");
		mcd2.minimumDaysOfCohortTime = 1;
		mcd2.attritionFile = false;
		assertEquals(true, mcd2.init());
		p = mcd2.process(p);

		assertEquals(false, p.isInCohort());
	}

}
