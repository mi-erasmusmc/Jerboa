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
import org.erasmusmc.jerboa.modifiers.MeasurementCategories;
import org.junit.Before;
import org.junit.Test;

import test.java.TestAll;

public class MeasurementCategoriesTest {
	private Patient p;
	private MeasurementCategories mc;

	@Before
	public void setUp() throws Exception {
		Jerboa.unitTest = true;

		p = TestAll.createPatient("1", "19640630", "M", "20000101", "20160101", "20000101", "20160101", "20000101", "20160101");

		TestAll.createMeasurement(p, "19700401", "WEIGHT", "10");
		TestAll.createMeasurement(p, "20010401", "WEIGHT", "80");
		TestAll.createMeasurement(p, "20011001", "HEIGHT", "1.2");
		TestAll.createMeasurement(p, "20011201", "HEIGHT", "1.9");

		mc = new MeasurementCategories();
		mc.categories.add("WEIGHTCAT;10-50;WEIGHT;10;50");
		mc.categories.add("WEIGHTCAT;50-100;WEIGHT;50;100");

		// should result in no additions because of age criterion
		mc.categories.add("HEIGHTCAT;1.0-1.5;HEIGHT;1.5;2.0;50;999");

		mc.categories.add("HEIGHTCAT;1.5-2.0;HEIGHT;1.5;2.0");

		mc.init();
		p = mc.process(p);

	}

	@Test
	public void testNumberMeasurementsAdded() {
			assertEquals("Incorrect number of measurements added: " +p.toStringDetails(), (int) 7, p.measurements.size());

	}

}
