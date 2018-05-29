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

/******************************************************************************************
 * Jerboa software version 3.0 - Copyright Erasmus University Medical Center, Rotterdam   *
 *																				          *
 * Author: Marius Gheorghe (MG) - department of Medical Informatics						  *
 * 																						  *
 * $Rev:: 3848              $:  Revision of last commit                                   *
 * $Author:: root           $:  Author of last commit                                     *
 * $Date:: 					$:  Date and time (CET) of last commit						  *
 ******************************************************************************************/

package test.java.core;

import static org.junit.Assert.*;

import java.awt.Dimension;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JPanel;
import javax.swing.JTextField;

import org.erasmusmc.jerboa.utilities.DateUtilities;
import org.junit.Test;
import org.junit.runner.JUnitCore;
import org.junit.runner.Result;
import org.junit.runner.notification.Failure;

/**
 * This class represents a unit testing for the date conversion methods.
 *
 * @author MG
 *
 */
public class DateTest {

	/**
	 * This method tests the conversion of all possible dates
	 * between the FIRST_YEAR and LAST_YEAR as declared in DataDefinition.
	 * It first checks if a generated date is coherent and converts it into days
	 * and days into a date.
	 */
	@Test
	public void testAllDates(){

		int date = 18500101;
		String inDate = "";
		int inDays = 0;

		int counter  = 0;
		int counterConvTo = 0, counterConvFrom = 0;

		boolean conversionTo;
		boolean conversionFrom;

		//loop through all possible dates
		while (date < 21000101){

			try{
				//check if coherent
				if (DateUtilities.isDateCoherent(DateUtilities.splitDate(String.valueOf(date), DateUtilities.DATE_ON_YYYYMMDD))){
					//keep track of nb of coherent dates
					counter ++;
					//convert
					inDays = DateUtilities.dateToDaysUnitTest(String.valueOf(date));
					inDate = DateUtilities.daysToDateUnitTestJavaCalendar(inDays);

					//check which conversion was successful
					conversionTo = (inDays == counter);
					conversionFrom = (Integer.parseInt(inDate) == date);
					//count them
					if (conversionTo)
						counterConvTo ++;
					if (conversionFrom)
						counterConvFrom ++;
				}

			}catch(Exception e){
				System.out.println(date);
				e.printStackTrace();
				break;
			}
			//update date
			date ++;
		}
		//print results
/*		System.out.println("Date conversion test unit");
		System.out.println("=========================");
		System.out.println("Total valid dates : "+counter);
		System.out.println("Nb. good conversions to: "+counterConvTo);
		System.out.println("Nb. good conversions from: "+counterConvFrom);
		System.out.println();*/
		assertEquals(counter,counterConvTo);
		assertEquals(counter,counterConvFrom);
	}

	@Test
	public void testDateToDaysConversion(){
		assertEquals((int)49002, DateUtilities.dateToDaysUnitTest("19840229").intValue());
	}

	@Test
	public void testDaysToDateConversion(){
		assertEquals("19840229", DateUtilities.daysToDateUnitTestJavaCalendar(49002));
	}


//CAN BE USED OUTSIDE ECLIPSE AND/OR JUNIT

	/**
	 * Main method. Independent of JUnit
	 * @param args - none
	 */
	public static void main(String[] args){

		//independent of JUnit
		dateConverter();

		//using JUnit
		Result result = JUnitCore.runClasses(DateTest.class);
		for (Failure failure : result.getFailures())
			System.out.println(failure.toString());

	}

	/**
	 * Converts a date input as a string of characters by the user in either the number of days
	 * that passed from the first legal date or from a number of days into a YYYYMMDD format.
	 */
	public static void dateConverter(){

		JFrame convertorFrame = new JFrame("Date Converter");
		convertorFrame.setPreferredSize(new Dimension(350,60));
		convertorFrame.setFocusable(true);
		final JPanel convertorPanel = new JPanel();
		convertorPanel.setLayout(new BoxLayout(convertorPanel, BoxLayout.X_AXIS));
		final JTextField convertorField = new JTextField();
		JButton inDaysButton = new JButton("In days");
		JButton fromDaysButton = new JButton("From days");
		JButton testAll = new JButton("Test all");
		inDaysButton.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent arg0) {
				if (!DateUtilities.isValidDate(convertorField.getText())){
					System.out.println("baaad date");
				}else{
					String valueToConvert = convertorField.getText();
					String value = String.valueOf(DateUtilities.dateToDays(valueToConvert,
							DateUtilities.dateFormat(convertorField.getText())));
					convertorField.setText(value);
					System.out.println(valueToConvert+" in days: "+value);
				}
			}
		});
		fromDaysButton.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent arg0) {
				String value = DateUtilities.daysToDateUnitTestJavaCalendar(Integer.parseInt(convertorField.getText()));
				convertorField.setText(value);
				System.out.println("from days: "+value);
			}
		});
		testAll.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent arg0) {
				(new DateTest()).testAllDates();
			}
		});
		convertorPanel.add(convertorField);
		convertorPanel.add(inDaysButton);
		convertorPanel.add(fromDaysButton);
		convertorPanel.add(testAll);
		convertorFrame.add(convertorPanel);
		convertorFrame.pack();
		convertorFrame.setLocationRelativeTo(null);
		convertorFrame.setVisible(true);
	}

}
