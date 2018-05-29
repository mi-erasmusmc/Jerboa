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
import org.erasmusmc.jerboa.modifiers.BMICalculation2;
import org.junit.Before;
import org.junit.Test;

import test.java.TestAll;

public class BMICalculation2Test {
	private Patient p;
	private BMICalculation2 bc;

	@Before
	public void setUp() throws Exception {
		Jerboa.unitTest = true;

		p = TestAll.createPatient("1", "19640630", "M", "20000101", "20160101", "19900101", "20160101", "19900101", "20160101");

		//Weight during childhood
		TestAll.createMeasurement(p, "19700401", "WEIGHT", "10");

		//Weight during adulthood
		TestAll.createMeasurement(p, "20010401", "WEIGHT", "80");

		//height during adulthood
		TestAll.createMeasurement(p, "20011001", "HEIGHT", "1.2");

		//height during adulthood
		TestAll.createMeasurement(p, "20011201", "HEIGHT", "1.9");

		bc = new BMICalculation2();

		bc.adultAge = 18;
		bc.BMILabel = "BMI";
		bc.heightLabel = "HEIGHT";
		bc.weightLabel = "WEIGHT";
		bc.useMeasurementsOutsidePatientTime = true;

		bc.init();
		p = bc.process(p);
		//System.out.println(p.toStringDetails());
	}

	@Test
	public void testNumberOfMeasurements() {
		assertEquals("Incorrect number of measurements", (int) 6, p.measurements.size());
	}

	@Test
	public void testAddedMeasurements() {
		boolean Found = false;
		for (Measurement m : p.measurements){
			if (m.getType().equals(bc.BMILabel) && m.getValue().equals("22.16")){
				Found = true;
				break;
			}
		}
		assertTrue("BMI not Found",Found);
	}

}
