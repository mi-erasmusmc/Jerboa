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
import org.erasmusmc.jerboa.dataClasses.Patient;
import org.erasmusmc.jerboa.modifiers.MeasurementCountCohortDefinition;
import org.erasmusmc.jerboa.utilities.DateUtilities;
import org.junit.Before;
import org.junit.Test;

import test.java.TestAll;

public class MeasurementCountCohortDefinitionTest {
	private MeasurementCountCohortDefinition nmcd = new MeasurementCountCohortDefinition();

	@Before
	public void setUp() throws Exception {
		Jerboa.unitTest = true;
	}

	@Test
	public void testOpenWindow() {
		nmcd = new MeasurementCountCohortDefinition();
		nmcd.chainCohortDefinitions = false;
		nmcd.measurementsOfInterest.add("BMI;0;0;CohortStart;;CohortStart;");
		nmcd.attritionFile = false;
		nmcd.init();

		Patient p1 = TestAll.createPatient("1","19860701","M","20090101","20150101");

		Patient p2 = TestAll.createPatient("2","19860701","M","20090101","20150101");
		TestAll.createMeasurement(p2, "20010305", "BMI", "25");

		nmcd.process(p1);
		assertEquals("Patient in cohort", true, p1.isInCohort());
		assertEquals("Cohort start date", "20090101", DateUtilities.daysToDate(p1.getCohortStartDate()));
		assertEquals("Cohort end date", "20150101", DateUtilities.daysToDate(p1.getCohortEndDate()));

		nmcd.process(p2);
		assertEquals("Patient in cohort", false, p2.isInCohort());
	}

	@Test
	public void testRightOpenWindow() {
		nmcd = new MeasurementCountCohortDefinition();
		nmcd.chainCohortDefinitions = true;
		nmcd.measurementsOfInterest.add("BMI;0;0;CohortStart;30;CohortEnd;");
		nmcd.attritionFile = false;
		nmcd.init();

		Patient p1 = TestAll.createPatient("1","19860701","M","20000101","20150101","20000101","20150101","20060101","20120101");
		TestAll.createMeasurement(p1, "20060129", "BMI", "25");

		nmcd.process(p1);
		assertEquals("Patient in cohort", true, p1.isInCohort());
		assertEquals("Cohort start date", "20060101", DateUtilities.daysToDate(p1.getCohortStartDate()));
		assertEquals("Cohort end date", "20120101", DateUtilities.daysToDate(p1.getCohortEndDate()));

		Patient p2 = TestAll.createPatient("1","19860701","M","20000101","20150101","20000101","20150101","20060101","20120101");
		TestAll.createMeasurement(p2, "20080101", "BMI", "25");

		nmcd.process(p2);
		assertEquals("Patient in cohort", false, p2.isInCohort());
	}

	@Test
	public void testLeftOpenWindow() {
		nmcd = new MeasurementCountCohortDefinition();
		nmcd.chainCohortDefinitions = true;
		nmcd.measurementsOfInterest.add("BMI;0;0;CohortStart;;CohortEnd;30");
		nmcd.attritionFile = false;
		nmcd.init();

		Patient p1 = TestAll.createPatient("1","19860701","M","20000101","20150101","20000101","20150101","20060101","20120101");
		TestAll.createMeasurement(p1, "20120201", "BMI", "25");

		nmcd.process(p1);
		assertEquals("Patient in cohort", true, p1.isInCohort());
		assertEquals("Cohort start date", "20060101", DateUtilities.daysToDate(p1.getCohortStartDate()));
		assertEquals("Cohort end date", "20120101", DateUtilities.daysToDate(p1.getCohortEndDate()));

		Patient p2 = TestAll.createPatient("1","19860701","M","20000101","20150101","20000101","20150101","20060101","20120101");
		TestAll.createMeasurement(p2, "20050101", "BMI", "25");

		nmcd.process(p2);
		assertEquals("Patient in cohort", false, p2.isInCohort());
	}

	@Test
	public void testClosedWindow() {
		nmcd = new MeasurementCountCohortDefinition();
		nmcd.chainCohortDefinitions = true;
		nmcd.measurementsOfInterest.add("BMI;0;0;CohortStart;-365;CohortEnd;-365");
		nmcd.measurementsOfInterest.add("CHOL;1;4;CohortStart;0;CohortEnd;0");
		nmcd.attritionFile = false;
		nmcd.init();

		Patient p1 = TestAll.createPatient("1","19860701","M","20000101","20150101","20000101","20150101","20060101","20120101");
		TestAll.createMeasurement(p1, "20041231", "BMI", "25");
		TestAll.createMeasurement(p1, "20010101", "CHOL", "25");
		TestAll.createMeasurement(p1, "20020101", "CHOL", "25");
		TestAll.createMeasurement(p1, "20030101", "CHOL", "25");
		TestAll.createMeasurement(p1, "20040101", "CHOL", "25");
		TestAll.createMeasurement(p1, "20050101", "CHOL", "25");
		TestAll.createMeasurement(p1, "20070101", "CHOL", "25");
		TestAll.createMeasurement(p1, "20080101", "CHOL", "25");
		TestAll.createMeasurement(p1, "20090101", "CHOL", "25");

		nmcd.process(p1);
		assertEquals("Patient in cohort", true, p1.isInCohort());
		assertEquals("Cohort start date", "20060101", DateUtilities.daysToDate(p1.getCohortStartDate()));
		assertEquals("Cohort end date", "20120101", DateUtilities.daysToDate(p1.getCohortEndDate()));

		Patient p2 = TestAll.createPatient("1","19860701","M","20000101","20150101","20000101","20150101","20060101","20120101");
		TestAll.createMeasurement(p2, "20050501", "BMI", "25");
		TestAll.createMeasurement(p2, "20010101", "CHOL", "25");
		TestAll.createMeasurement(p2, "20020101", "CHOL", "25");
		TestAll.createMeasurement(p2, "20030101", "CHOL", "25");
		TestAll.createMeasurement(p2, "20040101", "CHOL", "25");
		TestAll.createMeasurement(p2, "20050101", "CHOL", "25");
		TestAll.createMeasurement(p2, "20070101", "CHOL", "25");
		TestAll.createMeasurement(p2, "20080101", "CHOL", "25");
		TestAll.createMeasurement(p2, "20090101", "CHOL", "25");

		nmcd.process(p2);
		assertEquals("Patient in cohort", false, p2.isInCohort());

		Patient p3 = TestAll.createPatient("1","19860701","M","20000101","20150101","20000101","20150101","20060101","20120101");
		TestAll.createMeasurement(p3, "20010101", "CHOL", "25");
		TestAll.createMeasurement(p3, "20020101", "CHOL", "25");
		TestAll.createMeasurement(p3, "20030101", "CHOL", "25");
		TestAll.createMeasurement(p3, "20040101", "CHOL", "25");
		TestAll.createMeasurement(p3, "20050101", "CHOL", "25");
		TestAll.createMeasurement(p3, "20070101", "CHOL", "25");
		TestAll.createMeasurement(p3, "20080101", "CHOL", "25");
		TestAll.createMeasurement(p3, "20090101", "CHOL", "25");

		nmcd.process(p3);
		assertEquals("Patient in cohort", true, p3.isInCohort());
		assertEquals("Cohort start date", "20060101", DateUtilities.daysToDate(p3.getCohortStartDate()));
		assertEquals("Cohort end date", "20120101", DateUtilities.daysToDate(p3.getCohortEndDate()));

		Patient p4 = TestAll.createPatient("1","19860701","M","20000101","20150101","20000101","20150101","20060101","20120101");
		TestAll.createMeasurement(p4, "20010101", "CHOL", "25");
		TestAll.createMeasurement(p4, "20020101", "CHOL", "25");
		TestAll.createMeasurement(p4, "20030101", "CHOL", "25");
		TestAll.createMeasurement(p4, "20040101", "CHOL", "25");
		TestAll.createMeasurement(p4, "20050101", "CHOL", "25");
		TestAll.createMeasurement(p4, "20070101", "CHOL", "25");
		TestAll.createMeasurement(p4, "20080101", "CHOL", "25");
		TestAll.createMeasurement(p4, "20090101", "CHOL", "25");
		TestAll.createMeasurement(p4, "20100101", "CHOL", "25");
		TestAll.createMeasurement(p4, "20110101", "CHOL", "25");

		nmcd.process(p4);
		assertEquals("Patient in cohort", false, p4.isInCohort());
	}

	@Test
	public void testClosedDatesWindow() {
		nmcd = new MeasurementCountCohortDefinition();
		nmcd.chainCohortDefinitions = true;
		nmcd.measurementsOfInterest.add("BMI;0;0;20080101;0;20100101;0");
		nmcd.attritionFile = false;
		nmcd.init();

		Patient p1 = TestAll.createPatient("1","19860701","M","20000101","20150101","20000101","20150101","20060101","20120101");
		TestAll.createMeasurement(p1, "20041231", "BMI", "25");

		nmcd.process(p1);
		assertEquals("Patient in cohort", true, p1.isInCohort());
		assertEquals("Cohort start date", "20060101", DateUtilities.daysToDate(p1.getCohortStartDate()));
		assertEquals("Cohort end date", "20120101", DateUtilities.daysToDate(p1.getCohortEndDate()));

		Patient p2 = TestAll.createPatient("1","19860701","M","20000101","20150101","20000101","20150101","20060101","20120101");
		TestAll.createMeasurement(p2, "20090501", "BMI", "25");

		nmcd.process(p2);
		assertEquals("Patient in cohort", false, p2.isInCohort());
	}

	@Test
	public void testExtraNoBMI() {
		// No BMI
		nmcd = new MeasurementCountCohortDefinition();
		nmcd.chainCohortDefinitions = true;
		nmcd.measurementsOfInterest.add("BMI;0;0;PopulationStart;;PopulationStart");
		nmcd.attritionFile = false;
		nmcd.init();

		Patient p1 = TestAll.createPatient("1","19860701","M","20000101","20150101","20020101","20150101","20060101","20120101");

		nmcd.process(p1);
		assertEquals("Patient in cohort", true, p1.isInCohort());
		assertEquals("Cohort start date", "20060101", DateUtilities.daysToDate(p1.getCohortStartDate()));
		assertEquals("Cohort end date", "20120101", DateUtilities.daysToDate(p1.getCohortEndDate()));

		Patient p2 = TestAll.createPatient("1","19860701","M","20000101","20150101","20020101","20150101","20060101","20120101");
		TestAll.createMeasurement(p2, "20020731", "BMI", "25");

		nmcd.process(p2);
		assertEquals("Patient in cohort", false, p2.isInCohort());

		Patient p3 = TestAll.createPatient("1","19860701","M","20000101","20150101","20020101","20150101","20060101","20120101");
		TestAll.createMeasurement(p3, "20040221", "BMI", "26");

		nmcd.process(p3);
		assertEquals("Patient in cohort", false, p3.isInCohort());

		Patient p4 = TestAll.createPatient("1","19860701","M","20000101","20150101","20020101","20150101","20060101","20120101");
		TestAll.createMeasurement(p4, "20070221", "BMI", "27");

		nmcd.process(p4);
		assertEquals("Patient in cohort", false, p4.isInCohort());
	}

	@Test
	public void testExtraBMIBeforeThreeYears() {
		// BMI in last 3 years before cohort
		nmcd = new MeasurementCountCohortDefinition();
		nmcd.chainCohortDefinitions = true;
		nmcd.measurementsOfInterest.add("BMI;1;;CohortStart;;CohortStart;-1095");
		nmcd.attritionFile = false;
		nmcd.init();
		Patient p1 = TestAll.createPatient("1","19860701","M","20000101","20150101","20020101","20150101","20060101","20120101");

		nmcd.process(p1);
		assertEquals("Patient in cohort", false, p1.isInCohort());

		Patient p2 = TestAll.createPatient("1","19860701","M","20000101","20150101","20020101","20150101","20060101","20120101");
		TestAll.createMeasurement(p2, "20020731", "BMI", "25");

		nmcd.process(p2);
		assertEquals("Patient in cohort", true, p2.isInCohort());
		assertEquals("Cohort start date", "20060101", DateUtilities.daysToDate(p2.getCohortStartDate()));
		assertEquals("Cohort end date", "20120101", DateUtilities.daysToDate(p2.getCohortEndDate()));

		Patient p3 = TestAll.createPatient("1","19860701","M","20000101","20150101","20020101","20150101","20060101","20120101");
		TestAll.createMeasurement(p3, "20040221", "BMI", "26");

		nmcd.process(p3);
		assertEquals("Patient in cohort", false, p3.isInCohort());

		Patient p4 = TestAll.createPatient("1","19860701","M","20000101","20150101","20020101","20150101","20060101","20120101");
		TestAll.createMeasurement(p4, "20070221", "BMI", "27");

		nmcd.process(p4);
		assertEquals("Patient in cohort", false, p4.isInCohort());
	}

	@Test
	public void testExtraBMILastThreeYears() {
		// BMI in last 3 years before cohort
		nmcd = new MeasurementCountCohortDefinition();
		nmcd.chainCohortDefinitions = true;
		nmcd.measurementsOfInterest.add("BMI;1;;CohortStart;-1095;CohortStart;0");
		nmcd.attritionFile = false;
		nmcd.init();
		Patient p1 = TestAll.createPatient("1","19860701","M","20000101","20150101","20020101","20150101","20060101","20120101");

		nmcd.process(p1);
		assertEquals("Patient in cohort", false, p1.isInCohort());

		Patient p2 = TestAll.createPatient("1","19860701","M","20000101","20150101","20020101","20150101","20060101","20120101");
		TestAll.createMeasurement(p2, "20020731", "BMI", "25");

		nmcd.process(p2);
		assertEquals("Patient in cohort", false, p2.isInCohort());

		Patient p3 = TestAll.createPatient("1","19860701","M","20000101","20150101","20020101","20150101","20060101","20120101");
		TestAll.createMeasurement(p3, "20040221", "BMI", "26");

		nmcd.process(p3);
		assertEquals("Patient in cohort", true, p3.isInCohort());
		assertEquals("Cohort start date", "20060101", DateUtilities.daysToDate(p3.getCohortStartDate()));
		assertEquals("Cohort end date", "20120101", DateUtilities.daysToDate(p3.getCohortEndDate()));

		Patient p4 = TestAll.createPatient("1","19860701","M","20000101","20150101","20020101","20150101","20060101","20120101");
		TestAll.createMeasurement(p4, "20070221", "BMI", "27");

		nmcd.process(p4);
		assertEquals("Patient in cohort", false, p4.isInCohort());
	}

	@Test
	public void testExtraBMIAfter() {
		// BMI in last 3 years before cohort
		nmcd = new MeasurementCountCohortDefinition();
		nmcd.chainCohortDefinitions = true;
		nmcd.measurementsOfInterest.add("BMI;1;;CohortStart;0;CohortStart;");
		nmcd.attritionFile = false;
		nmcd.init();
		Patient p1 = TestAll.createPatient("1","19860701","M","20000101","20150101","20020101","20150101","20060101","20120101");

		nmcd.process(p1);
		assertEquals("Patient in cohort", false, p1.isInCohort());

		Patient p2 = TestAll.createPatient("1","19860701","M","20000101","20150101","20020101","20150101","20060101","20120101");
		TestAll.createMeasurement(p2, "20020731", "BMI", "25");

		nmcd.process(p2);
		assertEquals("Patient in cohort", false, p2.isInCohort());

		Patient p3 = TestAll.createPatient("1","19860701","M","20000101","20150101","20020101","20150101","20060101","20120101");
		TestAll.createMeasurement(p3, "20040221", "BMI", "26");

		nmcd.process(p3);
		assertEquals("Patient in cohort", false, p3.isInCohort());

		Patient p4 = TestAll.createPatient("1","19860701","M","20000101","20150101","20020101","20150101","20060101","20120101");
		TestAll.createMeasurement(p4, "20070221", "BMI", "27");

		nmcd.process(p4);
		assertEquals("Patient in cohort", true, p4.isInCohort());
		assertEquals("Cohort start date", "20060101", DateUtilities.daysToDate(p4.getCohortStartDate()));
		assertEquals("Cohort end date", "20120101", DateUtilities.daysToDate(p4.getCohortEndDate()));
	}

}
