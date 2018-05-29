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

import static org.junit.Assert.assertEquals;

import org.erasmusmc.jerboa.Jerboa;
import org.erasmusmc.jerboa.dataClasses.Patient;
import org.erasmusmc.jerboa.modifiers.PrescriptionCohortDefinition;
import org.erasmusmc.jerboa.utilities.DateUtilities;
import org.junit.Before;
import org.junit.Test;

import test.java.TestAll;

public class PrescriptionCohortDefinitionTest {

	@Before
	public void setUp() throws Exception {
		Jerboa.unitTest = true;

	}

	@Test
	public void testEventEndpoints() {

		Patient p1 = TestAll.createPatient("1", "19860701", "M", "20030301", "20040701", "20030301", "20040701");
		TestAll.createEvent(p1, "20021115", "ASTHMA", "");

		Patient p2 = TestAll.createPatient("2", "19860701", "M", "20030301", "20040701", "20030301", "20040701");
		TestAll.createEvent(p2, "20021115", "COPD", "");

		Patient p3 = TestAll.createPatient("3", "19860701", "M", "20030301", "20040701", "20030301", "20040701");
		TestAll.createEvent(p3, "20021115", "AMI", "");
		TestAll.createEvent(p3, "20040404", "AMI", "");

		// Test not in cohort
		PrescriptionCohortDefinition cd = new PrescriptionCohortDefinition();
		cd.chainCohortDefinitions = false;
		cd.naivePeriod = 365;
		cd.naiveForDrugsOfInterestOnly = true;
		//cd.drugsOfInterest
		//cd.drugsExclusion
		//cd.eventsInclusion
		cd.eventsTimewindow = ";";
		cd.eventsEndpoint.add("ASTHMA;Incident");
		cd.eventsEndpoint.add("COPD;");
		cd.eventsEndpoint.add("AMI");
		//cd.drugsEndpoint
		cd.drugsEndpointStartOnly = true;
		cd.drugsEndpointOverlapAllowed = 0;
		//cd.drugsConcomittantExclusion
		cd.drugsExclusionOverlapAllowed = 0;
		cd.minFreeOverlap = 0;
		cd.extendCohortTimeDrugsOfInterest = 9999999;
		//cd.extensionStopAtcs
		cd.extendedCohortSearch = false;
		cd.sortPrescriptionsByDuration = false;

		cd.init();
		cd.process(p1);
		cd.process(p2);
		cd.process(p3);

		assertEquals("Patient p1 is in cohort", false, p1.isInCohort());

		assertEquals("CohortStart patient p2", "20030301", DateUtilities.daysToDate(p2.getCohortStartDate()));
		assertEquals("CohortEnd patient p2", "20040701", DateUtilities.daysToDate(p2.getCohortEndDate()));

		assertEquals("CohortStart patient p3", "20030301", DateUtilities.daysToDate(p3.getCohortStartDate()));
		assertEquals("CohortEnd patient p3", "20040404", DateUtilities.daysToDate(p3.getCohortEndDate()));
	}

	@Test
	public void testDrugOfInterestAndExtendedSearch() {

		Patient p = TestAll.createPatient("1", "19501201", "M", "20110301", "20160101", "20110301", "20160101");
		TestAll.createPrescription(p, "R03AC12", "20121103", 90, "", "");
		TestAll.createPrescription(p, "R03AC13", "20140603", 60, "", "");
		TestAll.createPrescription(p, "R03AC12", "20140603", 90, "", "");
		TestAll.createEvent(p, "20131004", "COPD", "R95.00");

		// Test not in cohort
		PrescriptionCohortDefinition cd = new PrescriptionCohortDefinition();
		cd.chainCohortDefinitions = false;
		cd.naivePeriod = 365;
		cd.naiveForDrugsOfInterestOnly = true;
		cd.drugsOfInterest.add("R03AC11");
		cd.drugsOfInterest.add("R03AC12");
		cd.drugsOfInterest.add("R03AC13");
		cd.drugsOfInterest.add("R03AC14");
		cd.drugsOfInterest.add("R03AC18");
		cd.drugsOfInterest.add("R03AC19");
		//cd.drugsExclusion
		cd.eventsInclusion.add("COPD");
		cd.eventsTimewindow = ";183";
		//cd.eventsEndpoint
		cd.drugsEndpoint.add("R03BB04");
		cd.drugsEndpoint.add("R03AK08");
		cd.drugsEndpointStartOnly = true;
		cd.drugsEndpointOverlapAllowed = 30;
		//cd.drugsConcomittantExclusion
		cd.drugsExclusionOverlapAllowed = 0;
		cd.minFreeOverlap = 0;
		cd.extendCohortTimeDrugsOfInterest = 0;
		//cd.extensionStopAtcs
		cd.extendedCohortSearch = true;
		cd.sortPrescriptionsByDuration = true;

		cd.init();
		cd.process(p);

		assertEquals("Patient is in cohort", true, p.isInCohort());
		assertEquals("CohortStart", "20140603", DateUtilities.daysToDate(p.getCohortStartDate()));
		assertEquals("CohortEnd", "20140901", DateUtilities.daysToDate(p.getCohortEndDate()));
	}

	@Test
	public void testNotInCohort() {

		Patient p = TestAll.createPatient("1", "19371001", "F", "20061101", "20150101", "20121101", "20150101");
		TestAll.createPrescription(p, "R03AC13", "19931206", 120, "", "");
		TestAll.createPrescription(p, "R03AK08", "20110916", 199, "", "");
		TestAll.createPrescription(p, "R03BB04", "20110726", 251, "", "");
		TestAll.createPrescription(p, "R03BB04", "20120822", 90, "", "");
		TestAll.createPrescription(p, "R03BB04", "20130205", 11, "", "");
		TestAll.createPrescription(p, "R03AC13", "20130205", 81, "", "");
		TestAll.createPrescription(p, "R03AC13", "20130527", 539, "", "");
		TestAll.createPrescription(p, "R03AC13", "20141120", 81, "", "");
		TestAll.createEvent(p, "20140526", "COPD", "R95.00");

		// Test not in cohort
		PrescriptionCohortDefinition cd = new PrescriptionCohortDefinition();
		cd.chainCohortDefinitions = false;
		cd.naivePeriod = 365;
		cd.naiveForDrugsOfInterestOnly = true;
		cd.drugsOfInterest.add("R03AC11");
		cd.drugsOfInterest.add("R03AC12");
		cd.drugsOfInterest.add("R03AC13");
		cd.drugsOfInterest.add("R03AC14");
		cd.drugsOfInterest.add("R03AC18");
		cd.drugsOfInterest.add("R03AC19");
		//cd.drugsExclusion
		cd.eventsInclusion.add("COPD");
		cd.eventsTimewindow = ";183";
		//cd.eventsEndpoint
		cd.drugsEndpoint.add("R03BB04");
		cd.drugsEndpoint.add("R03AK08");
		cd.drugsEndpointStartOnly = true;
		cd.drugsEndpointOverlapAllowed = 30;
		//cd.drugsConcomittantExclusion
		cd.drugsExclusionOverlapAllowed = 0;
		cd.minFreeOverlap = 0;
		cd.extendCohortTimeDrugsOfInterest = 0;
		//cd.extensionStopAtcs
		cd.extendedCohortSearch = true;
		cd.sortPrescriptionsByDuration = true;

		cd.init();
		cd.process(p);

		assertEquals("Patient is in cohort", false, p.isInCohort());
	}

}
