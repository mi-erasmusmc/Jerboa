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

import java.util.Map;

import org.erasmusmc.jerboa.Jerboa;
import org.erasmusmc.jerboa.dataClasses.Measurement;
import org.erasmusmc.jerboa.dataClasses.Patient;
import org.erasmusmc.jerboa.modifiers.MeasurementFirstAndLast;
import org.junit.Before;
import org.junit.Test;

import test.java.TestAll;

public class MeasurementFirstAndLastTest {
	private Patient p;
	private Measurement w2, w3, w5, w6;
	private Measurement h2;
	private Measurement b2, b3,b6;
	private MeasurementFirstAndLast mfl;

	@Before
	public void setUp() throws Exception {
		Jerboa.unitTest = true;
	}

	@Test
	public void testPopulation() {

		p = TestAll.createPatient("1", "19640630", "M", "20000101", "20160101", "20000101", "20160101", "20050101", "20100101");

		TestAll.createMeasurement(p, "19900101", "WEIGHT", "1");
		w2 = TestAll.createMeasurement(p, "20020101", "WEIGHT", "2");
		w3 = TestAll.createMeasurement(p, "20060101", "WEIGHT", "3");
		TestAll.createMeasurement(p, "20070101", "WEIGHT", "4");
		w5 = TestAll.createMeasurement(p, "20080101", "WEIGHT", "5");
		w6 = TestAll.createMeasurement(p, "20120101", "WEIGHT", "6");
		TestAll.createMeasurement(p, "20170101", "WEIGHT", "7");

		TestAll.createMeasurement(p, "19900101", "HEIGHT", "1");
		h2 = TestAll.createMeasurement(p, "20020101", "HEIGHT", "2");
		TestAll.createMeasurement(p, "20170101", "HEIGHT", "7");

		TestAll.createMeasurement(p, "19900101", "BMI", "1");
		b2 = TestAll.createMeasurement(p, "20020101", "BMI", "2");
		b3 = TestAll.createMeasurement(p, "20060101", "BMI", "3");
		b6 = TestAll.createMeasurement(p, "20120101", "BMI", "6");
		TestAll.createMeasurement(p, "20170101", "BMI", "7");

		mfl = new MeasurementFirstAndLast();
		mfl.window = "PopulationStart;0;PopulationEnd;0";
		mfl.measurements.add("WEIGHT;WEIGHT_FIRST;WEIGHT_LAST");
		mfl.measurements.add("HEIGHT;HEIGHT_FIRST;HEIGHT_LAST");
		mfl.measurements.add("BMI;BMI_FIRST;BMI_LAST");

		mfl.init();
		p = mfl.process(p);

		Map<String, Measurement> first = mfl.getFirst();
		Map<String, Measurement> last = mfl.getLast();

		if (first.get("WEIGHT") != null) {
			assertEquals(first.get("WEIGHT").getType(), "WEIGHT_FIRST");
			assertEquals(first.get("WEIGHT").getDate(), w2.getDate());
			assertEquals(first.get("WEIGHT").getValue(), w2.getValue());
		}
		else {
			assertEquals(true, false);
		}

		if (last.get("WEIGHT") != null) {
			assertEquals(last.get("WEIGHT").getType(), "WEIGHT_LAST");
			assertEquals(last.get("WEIGHT").getDate(), w6.getDate());
			assertEquals(last.get("WEIGHT").getValue(), w6.getValue());
		}
		else {
			assertEquals(true, false);
		}

		if (first.get("HEIGHT") != null) {
			assertEquals(first.get("HEIGHT").getType(), "HEIGHT_FIRST");
			assertEquals(first.get("HEIGHT").getDate(), h2.getDate());
			assertEquals(first.get("HEIGHT").getValue(), h2.getValue());
		}
		else {
			assertEquals(true, false);
		}

		assertEquals(last.get("HEIGHT"), null);

		if (first.get("BMI") != null) {
			assertEquals(first.get("BMI").getType(), "BMI_FIRST");
			assertEquals(first.get("BMI").getDate(), b2.getDate());
			assertEquals(first.get("BMI").getValue(), b2.getValue());
		}
		else {
			assertEquals(true, false);
		}

		if (last.get("BMI") != null) {
			assertEquals(last.get("BMI").getType(), "BMI_LAST");
			assertEquals(last.get("BMI").getDate(), b6.getDate());
			assertEquals(last.get("BMI").getValue(), b6.getValue());
		}
		else {
			assertEquals(true, false);
		}

	}

	@Test
	public void testCohort() {

		p = TestAll.createPatient("1", "19640630", "M", "20000101", "20160101", "20000101", "20160101", "20050101", "20100101");

		TestAll.createMeasurement(p, "19900101", "WEIGHT", "1");
		w2 = TestAll.createMeasurement(p, "20020101", "WEIGHT", "2");
		w3 = TestAll.createMeasurement(p, "20060101", "WEIGHT", "3");
		TestAll.createMeasurement(p, "20070101", "WEIGHT", "4");
		w5 = TestAll.createMeasurement(p, "20080101", "WEIGHT", "5");
		w6 = TestAll.createMeasurement(p, "20120101", "WEIGHT", "6");
		TestAll.createMeasurement(p, "20170101", "WEIGHT", "7");

		TestAll.createMeasurement(p, "19900101", "HEIGHT", "1");
		h2 = TestAll.createMeasurement(p, "20020101", "HEIGHT", "2");
		TestAll.createMeasurement(p, "20170101", "HEIGHT", "7");

		TestAll.createMeasurement(p, "19900101", "BMI", "1");
		b2 = TestAll.createMeasurement(p, "20020101", "BMI", "2");
		b3 = TestAll.createMeasurement(p, "20060101", "BMI", "3");
		b6 = TestAll.createMeasurement(p, "20120101", "BMI", "6");
		TestAll.createMeasurement(p, "20170101", "BMI", "7");

		mfl = new MeasurementFirstAndLast();
		mfl.window = "CohortStart;0;CohortEnd;0";
		mfl.measurements.add("WEIGHT;WEIGHT_FIRST;WEIGHT_LAST");
		mfl.measurements.add("HEIGHT;HEIGHT_FIRST;HEIGHT_LAST");
		mfl.measurements.add("BMI;BMI_FIRST;BMI_LAST");

		mfl.init();
		p = mfl.process(p);

		Map<String, Measurement> first = mfl.getFirst();
		Map<String, Measurement> last = mfl.getLast();

		if (first.get("WEIGHT") != null) {
			assertEquals(first.get("WEIGHT").getType(), "WEIGHT_FIRST");
			assertEquals(first.get("WEIGHT").getDate(), w3.getDate());
			assertEquals(first.get("WEIGHT").getValue(), w3.getValue());
		}
		else {
			assertEquals(true, false);
		}

		if (last.get("WEIGHT") != null) {
			assertEquals(last.get("WEIGHT").getType(), "WEIGHT_LAST");
			assertEquals(last.get("WEIGHT").getDate(), w5.getDate());
			assertEquals(last.get("WEIGHT").getValue(), w5.getValue());
		}
		else {
			assertEquals(true, false);
		}

		assertEquals(first.get("HEIGHT"), null);

		assertEquals(last.get("HEIGHT"), null);

		if (first.get("BMI") != null) {
			assertEquals(first.get("BMI").getType(), "BMI_FIRST");
			assertEquals(first.get("BMI").getDate(), b3.getDate());
			assertEquals(first.get("BMI").getValue(), b3.getValue());
		}
		else {
			assertEquals(true, false);
		}

		assertEquals(last.get("BMI"), null);

	}
}
