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

package test.java.modules;

import static org.junit.Assert.*;

import org.erasmusmc.jerboa.Jerboa;
import org.erasmusmc.jerboa.dataClasses.Patient;
import org.erasmusmc.jerboa.modules.CodeCounting;
import org.erasmusmc.jerboa.utilities.ExtendedMultiKey;
import org.erasmusmc.jerboa.utilities.MultiKeyBag;
import org.erasmusmc.jerboa.utilities.Wildcard;
import org.junit.Before;
import org.junit.Test;

import test.java.TestAll;

public class CodeCountingTest {

	private CodeCounting cc;

	@Before
	public void setUp() throws Exception {
		Jerboa.unitTest = true;


		// Patient with events
		Patient p1 = TestAll.createPatient("1", "19640630", "M", "20000101", "20160101", "19900101", "20160101", "19900101", "20160101");

		TestAll.createEvent(p1, "20010401", "NAFLD", "code1");
		TestAll.createEvent(p1, "20010201", "NAFLD", "code2");
		TestAll.createEvent(p1, "20010301", "ALCOHOLABUSE", "code3");
		TestAll.createEvent(p1, "20010301", "ALCOHOLABUSE", "");

		// Patient without events
		Patient p2 = TestAll.createPatient("2", "19640630", "M", "20000101", "20160101", "20000101", "20160101", "20000101", "20160101");

		cc = new CodeCounting();
		cc.init();
		p1 = cc.process(p1);
		p2 = cc.process(p2);

		//System.out.println(p.toStringDetails());

		cc.calcStats();
	}

	@Test
	public void testCounters() {
		assertEquals((int) 4, cc.getEventsInPatientTime());
		assertEquals((int) 0, cc.getEventsOutsidePatientTime());
		assertEquals((int) 1, cc.getEventsWithNoCode());
		assertEquals((int) 2, cc.getNumberOfEventTypes());
		// missing code is counted
		assertEquals((int) 4, cc.getNumberOfEventCodes());
		assertEquals((int) 1, cc.getPatientsWithoutEvents());
	}

	@Test
	public void testBagContent() {
		MultiKeyBag fc = cc.getFirstCountBag();
		MultiKeyBag tc = cc.getTotalCountBag();
		assertEquals((int) 2, fc.getSize());
		assertEquals((int) 1, fc.getCount(new ExtendedMultiKey("NAFLD","code2")));
		assertEquals((int) 2, tc.getCount(new ExtendedMultiKey(Wildcard.BYTE(),"NAFLD",Wildcard.STRING())));
	}

}
