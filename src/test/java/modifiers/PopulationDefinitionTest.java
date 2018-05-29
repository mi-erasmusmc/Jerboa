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
import org.erasmusmc.jerboa.modifiers.PopulationDefinition;
import org.erasmusmc.jerboa.utilities.DateUtilities;
import org.junit.Before;
import org.junit.Test;

import test.java.TestAll;

public class PopulationDefinitionTest {
	private Patient p1,p2,p3;
	private PopulationDefinition pd;

	@Before
	public void setUp() throws Exception {
		Jerboa.unitTest = true;

		//Test Child Inclusion, PopulatiationStart, PopulationEnd, RunIn
		p1 = TestAll.createPatient("1", "19640630", "M", "20000101", "20140101");
		p2 = TestAll.createPatient("1", "20010630", "M", "20010701", "20160101");
		p3 = TestAll.createPatient("1", "19900630", "M", "20010701", "20160101");

		pd = new PopulationDefinition();
		pd.runInPeriod.add("OTHER;365");
		pd.childInclusionPeriod = 365;
		pd.childToBirthDate = true;
		pd.studyStart = "20010101";
		pd.studyEnd = "20150101";
		pd.minAge = 0;
		pd.maxAge = 999;
		pd.init();

		p1 = pd.process(p1);
		p2 = pd.process(p2);
		p3 = pd.process(p3);

	}

	@Test
	public void testPopulationStartEnd() {
		assertEquals("Incorrect populationStart: " +p1.toStringDetails(),
			     "20010101", DateUtilities.daysToDate(p1.getPopulationStartDate()));
		assertEquals("Incorrect populationEnd: " +p1.toStringDetails(),
			     "20140101", DateUtilities.daysToDate(p1.getPopulationEndDate()));

		assertEquals("Incorrect populationStart: " +p2.toStringDetails(),
			     "20010630", DateUtilities.daysToDate(p2.getPopulationStartDate()));
		assertEquals("Incorrect populationEnd: " +p2.toStringDetails(),
			     "20150101", DateUtilities.daysToDate(p2.getPopulationEndDate()));

		assertEquals("Incorrect populationStart: " +p3.toStringDetails(),
			     "20020701", DateUtilities.daysToDate(p3.getPopulationStartDate()));
		assertEquals("Incorrect populationEnd: " +p3.toStringDetails(),
			     "20150101", DateUtilities.daysToDate(p3.getPopulationEndDate()));
	}

	@Test
	public void testMinAge() {
		pd.runInPeriod.add("OTHER;365");
		pd.childInclusionPeriod = 365;
		pd.childToBirthDate = true;
		pd.studyStart = "20010101";
		pd.studyEnd = "20150101";
		pd.minAge = 40;
		pd.maxAge = 999;
		pd.init();

		p1 = pd.process(p1);
		p2 = pd.process(p2);

		assertEquals("Incorrect populationStart: " +p1.toStringDetails(),
			     "20040630", DateUtilities.daysToDate(p1.getPopulationStartDate()));
		assertEquals("Incorrect populationEnd: " +p1.toStringDetails(),
			     "20140101", DateUtilities.daysToDate(p1.getPopulationEndDate()));

		assertFalse("Should not be in population:", p2.inPopulation);
	}

}
