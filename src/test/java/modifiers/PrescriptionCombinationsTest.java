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

import java.util.List;

import org.erasmusmc.jerboa.Jerboa;
import org.erasmusmc.jerboa.dataClasses.Patient;
import org.erasmusmc.jerboa.dataClasses.Prescription;
import org.erasmusmc.jerboa.modifiers.PrescriptionCombinations;
import org.erasmusmc.jerboa.modifiers.PrescriptionCombinations.Interval;
import org.erasmusmc.jerboa.modifiers.PrescriptionCombinations.TimeLine;
import org.junit.Before;
import org.junit.Test;

import test.java.TestAll;

public class PrescriptionCombinationsTest {
	private Patient p1;
	private PrescriptionCombinations pc;

	@Before
	public void setUp() throws Exception {
		Jerboa.unitTest = true;

		//Test Child Inclusion, PopulatiationStart, PopulationEnd, RunIn
		p1 = TestAll.createPatient("1", "19700101", "M", "20000101", "20170101");
		TestAll.createPrescription(p1, "A01AA01", "20010101", 30, "", "").setDose("1");
		TestAll.createPrescription(p1, "B01BB01", "20010106", 30, "", "").setDose("2");
		TestAll.createPrescription(p1, "C01CC01", "20010111", 30, "", "").setDose("3");
		TestAll.createPrescription(p1, "A01AA01", "20010116", 30, "", "").setDose("4");
		TestAll.createPrescription(p1, "D01DD01", "20050101", 30, "", "").setDose("5");
		TestAll.createPrescription(p1, "A01AC01", "20060101", 30, "", "").setDose("6");
		TestAll.createPrescription(p1, "B01BC01", "20070101", 30, "", "").setDose("7");
		TestAll.createPrescription(p1, "D01DD02", "20080101", 30, "", "").setDose("8");
		TestAll.createPrescription(p1, "A01BC01", "20060101", 30, "", "").setDose("9");

		pc = new PrescriptionCombinations();
		pc.drugGroups.add("A;A01AA01");
		pc.drugGroups.add("A+B;A01BB01");
		pc.drugGroups.add("B;B01BB01");
		pc.drugGroups.add("A+C;A01AC01");
		pc.drugGroups.add("B+C;B01BC01");
		pc.drugGroups.add("C;C01CC01");
		pc.drugGroups.add("A+B+C;A01BC01");
		pc.drugGroups.add("D*;D");
		pc.combinations.add("A;A;");
		pc.combinations.add("A;A+B;");
		pc.combinations.add("A;A+C;");
		pc.combinations.add("B;B;");
		pc.combinations.add("B;A+B;");
		pc.combinations.add("B;B+C;");
		pc.combinations.add("C;C;");
		pc.combinations.add("C;A+C;");
		pc.combinations.add("C;B+C;");
		pc.combinations.add("D;D*;");
		pc.combinations.add("E;E;");
		pc.combinations.add("E;E+F;");
		pc.combinations.add("F;F;");
		pc.combinations.add("F;E+F;");
		pc.combinations.add("A;A+B+C;");
		pc.combinations.add("B;A+B+C;");
		pc.combinations.add("C;A+B+C;");
		pc.combinations.add("A+B+C;A+B+C;");
		pc.combinations.add("AB;A,B;");
		pc.combinations.add("AC;A,C;");
		pc.combinations.add("BC;B,C;");
		pc.combinations.add("ABC;A,B,C;");
		pc.removeDifferentCompStarts = false;
		pc.mergeCombinations = true;
		pc.init();

		p1 = pc.process(p1);
	}

	@Test
	public void testTimeLine() {
		String[] intervalDescriptions = {
			"20010101-20010106 (5 days): _A",
			"20010106-20010111 (5 days): _A, _B",
			"20010111-20010116 (5 days): _A, _B, _C",
			"20010116-20010131 (15 days): _A, _B, _C",
			"20010131-20010205 (5 days): _A, _B, _C",
			"20010205-20010210 (5 days): _A, _C",
			"20010210-20010215 (5 days): _A",
			"20050101-20050131 (30 days): _D*",
			"20060101-20060131 (30 days): _A+B+C, _A+C",
			"20070101-20070131 (30 days): _B+C",
			"20080101-20080131 (30 days): _D*"
		};
		TimeLine timeLine = pc.getExposureTimeLine();
		List<Interval> intervals = timeLine.getTimeLine();
		assertEquals(intervalDescriptions.length, intervals.size());
		for (int intervalNr = 0; intervalNr < intervals.size(); intervalNr++) {
			if (intervalNr < intervalDescriptions.length) {
				assertEquals(intervalDescriptions[intervalNr], intervals.get(intervalNr).toString());
			}
		}
	}

	@Test
	public void testPrescriptions() {
		String[] prescriptionDescriptions = {
				"1,1,A01AA01,20010101-20010131,30,1,,",
				"1,1,_A,20010101-20010215,45,MIXED,,",
				"1,1,B01BB01,20010106-20010205,30,2,,",
				"1,1,_AB,20010106-20010205,30,MIXED,,",
				"1,1,_B,20010106-20010205,30,MIXED,,",
				"1,1,C01CC01,20010111-20010210,30,3,,",
				"1,1,_ABC,20010111-20010205,25,MIXED,,",
				"1,1,_AC,20010111-20010210,30,MIXED,,",
				"1,1,_BC,20010111-20010205,25,MIXED,,",
				"1,1,_C,20010111-20010210,30,MIXED,,",
				"1,1,A01AA01,20010116-20010215,30,4,,",
				"1,1,D01DD01,20050101-20050131,30,5,,",
				"1,1,_D,20050101-20050131,30,MIXED,,",
				"1,1,A01AC01,20060101-20060131,30,6,,",
				"1,1,A01BC01,20060101-20060131,30,9,,",
				"1,1,_A,20060101-20060131,30,MIXED,,",
				"1,1,_C,20060101-20060131,30,MIXED,,",
				"1,1,_B,20060101-20060131,30,MIXED,,",
				"1,1,_A+B+C,20060101-20060131,30,MIXED,,",
				"1,1,B01BC01,20070101-20070131,30,7,,",
				"1,1,_B,20070101-20070131,30,MIXED,,",
				"1,1,_C,20070101-20070131,30,MIXED,,",
				"1,1,D01DD02,20080101-20080131,30,8,,",
				"1,1,_D,20080101-20080131,30,MIXED,,",
		};
		List<Prescription> prescriptions = p1.getPrescriptions();
		assertEquals(prescriptionDescriptions.length, prescriptions.size());
		for (int intervalNr = 0; intervalNr < prescriptions.size(); intervalNr++) {
			if (intervalNr < prescriptionDescriptions.length) {
				assertEquals(prescriptionDescriptions[intervalNr], prescriptions.get(intervalNr).toString());
			}
		}
	}

}
