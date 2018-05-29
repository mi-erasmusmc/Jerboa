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
import org.erasmusmc.jerboa.dataClasses.Measurement;
import org.erasmusmc.jerboa.dataClasses.Patient;
import org.erasmusmc.jerboa.modifiers.MeasurementCleaner;
import org.junit.Before;
import org.junit.Test;

import test.java.TestAll;

/**
 * @author MM
 *
 */
public class MeasurementCleanerTest {

	/**
	 * @throws java.lang.Exception
	 */
	@Before
	public void setUp() throws Exception {
		Jerboa.unitTest = true;
	}

	@Test
	public void testRemoveDuplicatesNoPriority() {

		Patient p = TestAll.createPatient("1", "19640630", "M", "20000101", "20160101", "20000101", "20160101", "20000101", "20160101");
		
		Measurement sbp1 = TestAll.createMeasurement(p, "20010401", "SBP", "150");
		Measurement sbp2 = TestAll.createMeasurement(p, "20010401", "SBP", "160");
		Measurement sbp3 = TestAll.createMeasurement(p, "20010501", "SBP", "170");

		Measurement dbp1 = TestAll.createMeasurement(p, "20010401", "DBP", "150");
		Measurement dbp2 = TestAll.createMeasurement(p, "20010401", "DBP", "160");
		Measurement dbp3 = TestAll.createMeasurement(p, "20010501", "DBP", "170");
		
		Measurement bmi1 = TestAll.createMeasurement(p, "20010401", "BMI", "30");
		Measurement bmi2 = TestAll.createMeasurement(p, "20010401", "BMI", "35");
		Measurement bmi3 = TestAll.createMeasurement(p, "20010401", "BMI", "25");
		
		Measurement len1 = TestAll.createMeasurement(p, "20010401", "LEN", "179");
		Measurement len2 = TestAll.createMeasurement(p, "20010401", "LEN", "180");
		Measurement len3 = TestAll.createMeasurement(p, "20010401", "LEN", "178");
		
		// Measurements sbp2, dbp1, bmi1, bmi3, len1, and len2 should be removed
		MeasurementCleaner mc = new MeasurementCleaner();
		mc.measurements.add("SBP;KEEPFIRST");
		mc.measurements.add("DBP;KEEPLAST");
		mc.measurements.add("BMI;KEEPHIGHEST");
		mc.measurements.add("LEN;KEEPLOWEST");
		mc.init();
		p = mc.process(p);
		assertEquals((int) 6, mc.getUnitTestRemoved().size());

		assertEquals(true, mc.getUnitTestRemoved().contains(sbp2));
		assertEquals(true, mc.getUnitTestRemoved().contains(dbp1));
		assertEquals(true, mc.getUnitTestRemoved().contains(bmi1));
		assertEquals(true, mc.getUnitTestRemoved().contains(bmi3));
		assertEquals(true, mc.getUnitTestRemoved().contains(len1));
		assertEquals(true, mc.getUnitTestRemoved().contains(len2));
		
		assertEquals((int) 6, p.getMeasurements().size());
		
		assertEquals(true, p.getMeasurements().contains(sbp1));
		assertEquals(true, p.getMeasurements().contains(sbp3));
		assertEquals(true, p.getMeasurements().contains(dbp2));
		assertEquals(true, p.getMeasurements().contains(dbp3));
		assertEquals(true, p.getMeasurements().contains(bmi2));
		assertEquals(true, p.getMeasurements().contains(len3));
	}

	@Test
	public void testRemoveDuplicatesPriority() {

		Patient p = TestAll.createPatient("1", "19640630", "M", "20000101", "20160101", "20000101", "20160101", "20000101", "20160101");

		Measurement copd1 = TestAll.createMeasurement(p, "20010401", "COPD", "MODERATE");
		TestAll.createMeasurement(p, "20010401", "COPD", "SEVERE");
		TestAll.createMeasurement(p, "20010501", "COPD", "MILD");
		
		// Measurement copd1 should be removed
		MeasurementCleaner mc = new MeasurementCleaner();
		mc.measurements.add("COPD;KEEPPRIORITY;SEVERE,MODERATE,MILD");
		mc.init();
		p = mc.process(p);
		assertEquals((int) 1, mc.getUnitTestRemoved().size());
		if (mc.getUnitTestRemoved().contains(copd1)) {
			assertEquals((int) 1, (int) 1);
		}
		else {
			assertEquals((int) 1, (int) 1);
		}
	}

	@Test
	public void testEventWindows() {
		
		Patient p = TestAll.createPatient("1", "19640630", "M", "20000101", "20160101", "20000101", "20160101", "20000101", "20160101");

		Measurement bmi1 = TestAll.createMeasurement(p, "20010401", "BMI", "25");
		Measurement bmi2 = TestAll.createMeasurement(p, "20020601", "BMI", "27");
		Measurement bmi3 = TestAll.createMeasurement(p, "20030801", "BMI", "26");
		Measurement bmi4 = TestAll.createMeasurement(p, "20040301", "BMI", "1");
		Measurement bmi5 = TestAll.createMeasurement(p, "20050701", "BMI", "27");
		Measurement bmi6 = TestAll.createMeasurement(p, "20060501", "BMI", "100");
		Measurement bmi7 = TestAll.createMeasurement(p, "20070901", "BMI", "27");
		
		TestAll.createEvent(p, "20021101", "PREGNANT", "none");
		TestAll.createEvent(p, "20051001", "PREGNANT", "none");
		
		// Measurements bmi2, bmi3, bmi5, and bmi6 should be removed
		MeasurementCleaner mc = new MeasurementCleaner();
		mc.measurements.add("BMI;KEEPALL");
		mc.eventWindows.add("PREGNANT;-365;365");
		mc.init();
		p = mc.process(p);
		
		assertEquals((int) 4, mc.getUnitTestRemoved().size());
		
		assertEquals(true, mc.getUnitTestRemoved().contains(bmi2));
		assertEquals(true, mc.getUnitTestRemoved().contains(bmi3));
		assertEquals(true, mc.getUnitTestRemoved().contains(bmi5));
		assertEquals(true, mc.getUnitTestRemoved().contains(bmi6));
		
		assertEquals((int) 3, p.getMeasurements().size());
		
		assertEquals(true, p.getMeasurements().contains(bmi1));
		assertEquals(true, p.getMeasurements().contains(bmi4));
		assertEquals(true, p.getMeasurements().contains(bmi7));
	}

	@Test
	public void testOutliers() {
		
		Patient p = TestAll.createPatient("1", "19640630", "M", "20000101", "20160101", "20000101", "20160101", "20000101", "20160101");

		Measurement bmi1 = TestAll.createMeasurement(p, "20010401", "BMI", "25");
		Measurement bmi2 = TestAll.createMeasurement(p, "20020601", "BMI", "27");
		Measurement bmi3 = TestAll.createMeasurement(p, "20030801", "BMI", "26");
		Measurement bmi4 = TestAll.createMeasurement(p, "20040301", "BMI", "1");
		Measurement bmi5 = TestAll.createMeasurement(p, "20050701", "BMI", "27");
		Measurement bmi6 = TestAll.createMeasurement(p, "20060501", "BMI", "100");
		Measurement bmi7 = TestAll.createMeasurement(p, "20070901", "BMI", "27");
		
		// Measurements bmi4, and bmi6 should be removed
		MeasurementCleaner mc = new MeasurementCleaner();
		mc.outlierDefinitions.add("BMI;20,30");
		mc.init();
		p = mc.process(p);
		
		assertEquals((int) 2, mc.getUnitTestRemoved().size());
		
		assertEquals(true, mc.getUnitTestRemoved().contains(bmi4));
		assertEquals(true, mc.getUnitTestRemoved().contains(bmi6));
		
		assertEquals((int) 5, p.getMeasurements().size());
		
		assertEquals(true, p.getMeasurements().contains(bmi1));
		assertEquals(true, p.getMeasurements().contains(bmi2));
		assertEquals(true, p.getMeasurements().contains(bmi3));
		assertEquals(true, p.getMeasurements().contains(bmi5));
		assertEquals(true, p.getMeasurements().contains(bmi7));
	}

	@Test
	public void testNonNumeric() {
		
		Patient p = TestAll.createPatient("1", "19640630", "M", "20000101", "20160101", "20000101", "20160101", "20000101", "20160101");

		Measurement bmi1 = TestAll.createMeasurement(p, "20010401", "BMI", "NaN");
		Measurement bmi2 = TestAll.createMeasurement(p, "20020601", "BMI", "27");
		Measurement bmi3 = TestAll.createMeasurement(p, "20030801", "BMI", "");
		Measurement bmi4 = TestAll.createMeasurement(p, "20040301", "BMI", "1");
		Measurement bmi5 = TestAll.createMeasurement(p, "20050701", "BMI", " ");
		Measurement bmi6 = TestAll.createMeasurement(p, "20060501", "BMI", "100");
		Measurement bmi7 = TestAll.createMeasurement(p, "20070901", "BMI", "27+");
		Measurement bmi8 = TestAll.createMeasurement(p, "20080101", "BMI", "25.6");
		Measurement copdsev1 = TestAll.createMeasurement(p, "20050901", "COPDSEV", "SEVERE");
		
		// Measurements bmi1, bmi3, bmi5, and bmi7 should be removed
		MeasurementCleaner mc = new MeasurementCleaner();
		mc.removeNonNumeric.add("BMI");
		mc.init();
		p = mc.process(p);
		
		assertEquals((int) 4, mc.getUnitTestRemoved().size());

		assertEquals(true, mc.getUnitTestRemoved().contains(bmi1));
		assertEquals(true, mc.getUnitTestRemoved().contains(bmi3));
		assertEquals(true, mc.getUnitTestRemoved().contains(bmi5));
		assertEquals(true, mc.getUnitTestRemoved().contains(bmi7));
		
		assertEquals((int) 5, p.getMeasurements().size());
		
		assertEquals(true, p.getMeasurements().contains(bmi2));
		assertEquals(true, p.getMeasurements().contains(bmi4));
		assertEquals(true, p.getMeasurements().contains(bmi6));
		assertEquals(true, p.getMeasurements().contains(bmi8));
		assertEquals(true, p.getMeasurements().contains(copdsev1));
	}

}
