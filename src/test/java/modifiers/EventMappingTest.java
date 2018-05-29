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
import org.erasmusmc.jerboa.modifiers.EventMapping;
import org.junit.Before;
import org.junit.Test;

import test.java.TestAll;

/**
 * @author PR
 *
 */
public class EventMappingTest {

	private Patient p;
	private EventMapping ep;

	/**
	 * @throws java.lang.Exception
	 */
	@Before
	public void setUp() throws Exception {
		Jerboa.unitTest = true;
	}

	@Test
	public void testMultipleMappings() {

		p = TestAll.createPatient("1", "19640630", "M", "20000101", "20160101", "20000101", "20160101", "20000101", "20160101");

		TestAll.createEvent(p, "20010101", "1", "none");
		TestAll.createEvent(p, "20010201", "2", "none");
		TestAll.createEvent(p, "20010301", "3", "none");
		TestAll.createEvent(p, "20010401", "1", "none");
		TestAll.createEvent(p, "20010501", "2", "none");
		TestAll.createEvent(p, "20010601", "3", "none");

		ep = new EventMapping();
		ep.eventMapping.add("1,2;Group 1-2;Events 1-2");
		ep.eventMapping.add("1,2,3;Group 1-3;Events 1-3");
		ep.attritionFile = false;
		ep.init();
		p = ep.process(p);

		assertEquals((int) 16, p.events.size());
	}

	@Test
	public void testHistoryExclusion() {

		p = TestAll.createPatient("1", "19640630", "M", "20000101", "20160101", "20000101", "20160101", "20000101", "20160101");

		TestAll.createEvent(p, "20010201", "NAFLD", "none");
		TestAll.createEvent(p, "20010301", "ALCOHOLABUSE", "none");
		TestAll.createEvent(p, "20010401", "NAFLD", "none");

		ep = new EventMapping();
		ep.eventMapping.add("NAFLD;NAFLD_NO_ALC;NAFLD No alcohol abuse");
		ep.historyExclusion.add("NAFLD;ALCOHOLABUSE;NAFLD No alcohol abuse");
		ep.attritionFile = false;
		ep.init();
		p = ep.process(p);

		assertEquals((int) 4, p.events.size());
	}

}
