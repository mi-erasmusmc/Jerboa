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
import org.erasmusmc.jerboa.modifiers.FixedCohortDefinition;
import org.erasmusmc.jerboa.utilities.DateUtilities;
import org.junit.Before;
import org.junit.Test;

import test.java.TestAll;

public class FixedCohortDefinitionTest {
	private FixedCohortDefinition fcd = new FixedCohortDefinition();
	private Patient p;

	@Before
	public void setUp() throws Exception {
		Jerboa.unitTest = true;
	}

	@Test
	public void testOpenCohort() {
		fcd = new FixedCohortDefinition();
		fcd.chainCohortDefinitions = false;
		fcd.cohortStartDate = "";
		fcd.cohortEndDate = "";
		fcd.init();

		p = TestAll.createPatient("1","19860701","M","20090101","20150101");
		fcd.process(p);
		assertEquals("Patient in cohort", true, p.isInCohort());
		assertEquals("Incorrect cohortStart", "20090101", DateUtilities.daysToDate(p.getCohortStartDate()));
		assertEquals("Incorrect cohortStart", "20150101", DateUtilities.daysToDate(p.getCohortEndDate()));
	}

	@Test
	public void testRightOpenCohort() {
		fcd = new FixedCohortDefinition();
		fcd.chainCohortDefinitions = true;
		fcd.cohortStartDate = "20110101";
		fcd.cohortEndDate = "";
		fcd.init();

		p = TestAll.createPatient("1","19860701","M","20090101","20150101");
		fcd.process(p);
		assertEquals("Patient in cohort", true, p.isInCohort());
		assertEquals("Incorrect cohortStart", "20110101", DateUtilities.daysToDate(p.getCohortStartDate()));
		assertEquals("Incorrect cohortStart", "20150101", DateUtilities.daysToDate(p.getCohortEndDate()));

		p = TestAll.createPatient("1","19860701","M","20090101","20100101");
		fcd.process(p);
		assertEquals("Patient in cohort", false, p.isInCohort());

		p = TestAll.createPatient("1","19860701","M","20120101","20150101");
		fcd.process(p);
		assertEquals("Patient in cohort", true, p.isInCohort());
		assertEquals("Incorrect cohortStart", "20120101", DateUtilities.daysToDate(p.getCohortStartDate()));
		assertEquals("Incorrect cohortStart", "20150101", DateUtilities.daysToDate(p.getCohortEndDate()));
	}

	@Test
	public void testLeftOpenCohort() {
		fcd = new FixedCohortDefinition();
		fcd.chainCohortDefinitions = false;
		fcd.cohortStartDate = "";
		fcd.cohortEndDate = "20130101";
		fcd.init();

		p = TestAll.createPatient("1","19860701","M","20090101","20150101");
		fcd.process(p);
		assertEquals("Patient in cohort", true, p.isInCohort());
		assertEquals("Incorrect cohortStart", "20090101", DateUtilities.daysToDate(p.getCohortStartDate()));
		assertEquals("Incorrect cohortStart", "20130101", DateUtilities.daysToDate(p.getCohortEndDate()));

		p = TestAll.createPatient("1","19860701","M","20140101","20150101");
		fcd.process(p);
		assertEquals("Patient in cohort", false, p.isInCohort());

		p = TestAll.createPatient("1","19860701","M","20090101","20120101");
		fcd.process(p);
		assertEquals("Patient in cohort", true, p.isInCohort());
		assertEquals("Incorrect cohortStart", "20090101", DateUtilities.daysToDate(p.getCohortStartDate()));
		assertEquals("Incorrect cohortStart", "20120101", DateUtilities.daysToDate(p.getCohortEndDate()));
	}

	@Test
	public void testClosedCohort() {
		fcd = new FixedCohortDefinition();
		fcd.chainCohortDefinitions = true;
		fcd.cohortStartDate = "20110101";
		fcd.cohortEndDate = "20130101";
		fcd.init();

		p = TestAll.createPatient("1","19860701","M","20090101","20100101");
		fcd.process(p);
		assertEquals("Patient in cohort", false, p.isInCohort());

		p = TestAll.createPatient("1","19860701","M","20090101","20120101");
		fcd.process(p);
		assertEquals("Patient in cohort", true, p.isInCohort());
		assertEquals("Incorrect cohortStart", "20110101", DateUtilities.daysToDate(p.getCohortStartDate()));
		assertEquals("Incorrect cohortStart", "20120101", DateUtilities.daysToDate(p.getCohortEndDate()));

		p = TestAll.createPatient("1","19860701","M","20090101","20150101");
		fcd.process(p);
		assertEquals("Patient in cohort", true, p.isInCohort());
		assertEquals("Incorrect cohortStart", "20110101", DateUtilities.daysToDate(p.getCohortStartDate()));
		assertEquals("Incorrect cohortStart", "20130101", DateUtilities.daysToDate(p.getCohortEndDate()));

		p = TestAll.createPatient("1","19860701","M","20120101","20150101");
		fcd.process(p);
		assertEquals("Patient in cohort", true, p.isInCohort());
		assertEquals("Incorrect cohortStart", "20120101", DateUtilities.daysToDate(p.getCohortStartDate()));
		assertEquals("Incorrect cohortStart", "20130101", DateUtilities.daysToDate(p.getCohortEndDate()));

		p = TestAll.createPatient("1","19860701","M","20140101","20150101");
		fcd.process(p);
		assertEquals("Patient in cohort", false, p.isInCohort());
	}

}
