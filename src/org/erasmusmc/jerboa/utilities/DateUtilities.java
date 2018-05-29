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
 * $Rev:: 4850              $:  Revision of last commit                                   *
 * $Author:: MG     $:  Author of last commit                                     *
 * $Date:: 					$:  Date and time (CET) of last commit						  *
 ******************************************************************************************/

package org.erasmusmc.jerboa.utilities;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.erasmusmc.jerboa.config.Parameters;

/**
 * Contains all the methods used to process, convert and validate a date attribute.
 * Manipulate with care. In case of modifications, run the test unit for validation.
 *
 * @author MG
 *
 */
public class DateUtilities{

	//legal dates interval
	public static final int FIRST_YEAR = Integer.parseInt(Parameters.MIN_LEGAL_DATE.substring(0,4));
	public static final int LAST_YEAR = Integer.parseInt(Parameters.MAX_LEGAL_DATE.substring(0,4));
	public static final HashMap<Integer, Integer> YEARS_TO_DAYS = yearsToDays();
	public static final HashMap<Integer, Integer> MONTHS_TO_DAYS = monthsToDays();

	//will hold all the possible dates and their corresponding number of days from first legal date
	public static HashMap<String, Integer> DATES_IN_DAYS = new HashMap<String, Integer>();
	public static HashMap<Integer, String> DAYS_IN_DATES = new HashMap<Integer, String>();

	//date validator
	private static Pattern pattern;
	private static Matcher matcher;

	public static String format;
	public static short[] dateComponentsOrder;

	public static final byte DATE_INVALID = -1;
	public static final byte DATE_ON_YYYYMMDD = 0;
	public static final byte DATE_ON_YYYYMMDD_WITH_SEPARATOR = 1;
	public static final byte DATE_ON_DDMMYYYY = 2;
	public static final byte DATE_ON_DDMMYYYY_WITH_SEPARATOR = 3;
	//public static final byte DATE_ON_MMDDYYYY = 4;
	//public static final byte DATE_ON_MMDDYYYY_WITH_SEPARATOR = 5;

	//supported date formats
	public static final Pattern[] DATE_PATTERNS = {
		Pattern.compile("^((?:1[0-9][0-9]|19[0-9]|20[0-9])\\d)(0?[1-9]|1[012])(0?[1-9]|[12][0-9]|3[01])$"), //yyyymmdd
		Pattern.compile("^((?:1[0-9][0-9]|19[0-9]|20[0-9])\\d)[-/.](0?[1-9]|1[012])[-/.](0?[1-9]|[12][0-9]|3[01])$"), //yyyy-mm-dd or yyyy/mm/dd or yyyy.mm.dd
		Pattern.compile("^(0?[1-9]|[12][0-9]|3[01])(0?[1-9]|1[012])((?:1[0-9][0-9]|19[0-9]|20[0-9])\\d)$"), //ddmmyyyy
		Pattern.compile("^(0?[1-9]|[12][0-9]|3[01])[-/.](0?[1-9]|1[012])[-/.]((?:1[0-9][0-9]|19[0-9]|20[0-9])\\d)$")}; //dd-mm-yyyy or dd/mm/yyyy or dd.mm.yyyy
	//  Pattern.compile("^(0?[1-9]|1[012])(0?[1-9]|[12][0-9]|3[01])((?:18[5-9]|19[0-9]|20[0-9])\\d)$"), //mmddyyyy
	//  Pattern.compile("^(0?[1-9]|1[012])[-/.](0?[1-9]|[12][0-9]|3[01])[-/.]((?:18[5-9]|19[0-9]|20[0-9])\\d)$")}; //mm-dd-yyyy or mm/dd/yyyy or mm.dd.yyyy


	//number of days per month
	@SuppressWarnings("serial")
	private static final Map<Boolean, Integer[]> monthDays = new HashMap<Boolean, Integer[]>() {{
		put(false, new Integer[] { 31, 28, 31, 30, 31, 30, 31, 31, 30, 31, 30, 31 } );
		put(true , new Integer[] { 31, 29, 31, 30, 31, 30, 31, 31, 30, 31, 30, 31 } );
	}};

	//details
	public static final double daysPerMonth = 30.4375;
	public static final double daysPerYear = 365.25;
	public static final double monthsPerYear = 12.00;

	/**
	 * Validate date format with regular expression.
	 * @param dateString - the String containing the date to be validated
	 * @return - true if a valid date format and a coherent date; false otherwise
	 */
	public static boolean isValidDate(String dateString){

		boolean foundMatch = false;
		dateComponentsOrder = new short[] {1, //year
										   2, //month
										   3};//day

		if (dateString != null && !dateString.equals("")){

			for (short i = 0; i < DATE_PATTERNS.length; i++){

				//check if the ddmmyyyy pattern is reached
				if (i > 0 && i % 2 == 0)
					//change date components order
					dateComponentsOrder = new short[] {3,2,1};
				//check if the mmddyyyy pattern is reached
				if (i > 0 && i % 4 == 0)
					//change date components order
					dateComponentsOrder = new short[] {3,1,2};
				//try to match the pattern
				pattern = (DATE_PATTERNS[i]);
				matcher = pattern.matcher(dateString);
				//found a match; get out of the loop
				if(foundMatch = matcher.matches())
					break;
			}
			//check if there was any pattern matching
			if(foundMatch){
				matcher.reset();
				//retrieve the date components
				if(matcher.find()){
					try{
						Integer.parseInt(matcher.group(dateComponentsOrder[0]));	 //year
						Integer.parseInt(matcher.group(dateComponentsOrder[1]));	 //month
						Integer.parseInt(matcher.group(dateComponentsOrder[2]));	 //day
						//System.out.println(date.toString());
					}catch(NumberFormatException e){
						return false;
					}
					//check for coherence
					return true;

				}else{ //no valid date components
					return false;
				}
			}else{ //no pattern matched
				return false;
			}
		}else{ //no valid date string
			return false;
		}
	}

	/**
	 * Checks if the dateString represents a date in a valid format.
	 * The checking is done via predefined regex patterns.
	 * @param dateString - the date that is to be validated
	 * @return - the date format as one of the ones defined in this class
	 */
	public static byte dateFormat(String dateString){
		dateComponentsOrder = new short[] {1, //year
										   2, //month
										   3};//day

		if (dateString != null && !dateString.equals("")){

			for (byte i = 0; i < DATE_PATTERNS.length; i++){

				//check if the ddmmyyyy pattern is reached
				if (i > 0 && i % 2 == 0)
					//change date components order
					dateComponentsOrder = new short[] {3,2,1};
				//check if the mmddyyyy pattern is reached
				if (i > 0 && i % 4 == 0)
					//change date components order
					dateComponentsOrder = new short[] {3,1,2};
				//try to match the pattern
				pattern = DATE_PATTERNS[i];
				matcher = pattern.matcher(dateString);
				//found a match; get out of the loop
				if(matcher.matches())
					return i;
			}
		}

		return DATE_INVALID;
	}

	/**
	 * Check the coherence of a date based on the number of days in a month and being a leap year.
	 * @param dateComponents - the date components (i.e., year, month, day) to be checked for coherence
	 * @return true - if the date is coherent; false otherwise
	 */
	public static boolean isDateCoherent(int[] dateComponents){

		if (dateComponents != null && dateComponents.length == 3){

			//check if the year is legal
			if (dateComponents[0] < FIRST_YEAR || dateComponents[0] > LAST_YEAR){
				return false;
			}
			//check if the month is legal
			if (dateComponents[1] > 12 || dateComponents[1] < 1){
				return false;
			}
			//check if the day is legal
			if (dateComponents[2] < 1 || dateComponents[2] > 31){
				return false;
			}
			//check day value coherence  (only 1,3,5,7,8,10,12 have 31 days)
			if ((dateComponents[2] == 31) && (dateComponents[1] == 4 || dateComponents[1] == 6
					|| dateComponents[1] == 9 || dateComponents[1] == 11)){
				return false;
				//special check for my birth date :]
			}else if (dateComponents[1] == 2) {
				//leap year - divisibility by 4 or 400 but not 100 (e.g., 1900, 2100 are not leap years)
				if(isLeapYear(dateComponents[0])){
					if(dateComponents[2] == 30 || dateComponents[2] == 31){
						return false;
					}else{
						return true;
					}
				}else{
					if(dateComponents[2] == 29 || dateComponents[2] == 30 || dateComponents[2] == 31){
						return false;
					}else{
						return true;
					}
				}
			}else{
				return true;
			}
		}else
			return false;
	}

	/**
	 * Checks if the year passed in argument is a leap year.
	 * A leap year is divisible by 4 or divisible by 400 but not 100
	 * (e.g., 1700 is not a leap year but 1600 is).
	 * @param year - the year to be checked
	 * @return - true if year is a leap year; false otherwise
	 */
	public static boolean isLeapYear(Integer year) {
		return (year % 4 == 0 && year % 100 != 0) || (year % 400 == 0);
	}

	/**
	 * Returns the number of days in the given month of the given year.
	 * @param year - the year in question
	 * @param month - the month in question
	 * @return - the number of days in that month
	 */
	public static int daysInMonth(int year, int month) {
		return monthDays.get(isLeapYear(year))[--month];
	}

	/**
	 * Returns the number of days from first legal date to year.
	 * @param year - the year in question
	 * @return the number of days passed from the FIRST_YEAR to year
	 */
	public static Integer getYearInDays(Integer year) {
		if (year < FIRST_YEAR || year > LAST_YEAR)
			throw new IllegalArgumentException("Illegal year in the date conversion: " + year);
		return YEARS_TO_DAYS.get(year);
	}

	/**
	 * Returns the number of days from 1st of January until month.
	 * @param month - month of interest
	 * @return the number of days passed from the 1st of January to month
	 */
	public static Integer getMonthInDays(Integer month){
		if (month < 0 || month > DateUtilities.monthsPerYear)
			throw new IllegalArgumentException("Illegal month in the date conversion: " + month);
		return MONTHS_TO_DAYS.get(month);
	}

	/**
	 * Returns the year from the date represented by days.
	 * @param days - the date in days
	 * @return the year of the date.
	 */
	public static Integer getYearFromDays(int days) {
		return Integer.parseInt(DateUtilities.daysToDate(days).substring(0, 4));
	}

	/**
	 * Returns the value in years from a number of days.
	 * Is to be used in computing for instance the age or years passed.
	 * @param days - the number of days to be transformed in years
	 * @return - the amount of years
	 */
	public static int getYearsFromDays(int days){
		return (int)(days/daysPerYear);
	}

	/**
	 * Returns the month from the date represented by days.
	 * @param days - the date in days
	 * @return the month of the date.
	 */
	public static Integer getMonthFromDays(int days) {
		return Integer.parseInt(DateUtilities.daysToDate(days).substring(4, 6));
	}

	/**
	 * Returns the day from the date represented by days.
	 * @param days - the date in days
	 * @return the day number of the date.
	 */
	public static Integer getDayFromDays(int days) {
		return Integer.parseInt(DateUtilities.daysToDate(days).substring(6, 8));
	}

	/**
	 * Converts a date into the number of days accumulated since the first legal date.
	 * It uses the constants defined in the Parameters class.
	 * @param dateComponents - the date components (e.g., year, month, day) to be converted into days
	 * @return - the converted date into the number of days
	 */
	public static Integer dateToDays(int[] dateComponents){
		Integer inDays = null;
		if (dateComponents != null && (dateComponents[0] >= FIRST_YEAR)){
			//check if the date was not already calculated
			String date = dateComponents[0]+""+
			(dateComponents[1] < 10 ? "0"+dateComponents[1] : dateComponents[1])+""+
			(dateComponents[2] < 10 ? "0"+dateComponents[2] : dateComponents[2]);
			if ((inDays = DATES_IN_DAYS.get(date)) == null){
				//check if the data is actually valid
				if (!isDateCoherent(dateComponents)){
					Logging.add("\nIllegal date found - year: "+dateComponents[0]+
							" month: "+dateComponents[1]+" day: "+dateComponents[2], Logging.ERROR, true);
					return null;
				}

				//retrieve number of days for months + the days of current month
				int month = dateComponents[1];
				int day = dateComponents[2];
				//compute total number of days
				try{
					inDays = getYearInDays(dateComponents[0]) + getMonthInDays(month) + day;
				}catch(IllegalArgumentException e){
					Logging.add("\nFailed converting date - year: "+dateComponents[0]+
							" month: "+dateComponents[1]+" day: "+dateComponents[2], Logging.ERROR, true);
					return null;
				}
				//check if it is a leap year and the month is superior to February - to add the leap day
				if (isLeapYear(dateComponents[0]) && month > 2)
					inDays += 1;

				//add it to the map
				DATES_IN_DAYS.put(date, inDays);
				DAYS_IN_DATES.put(inDays, date);
			}
		}

		return inDays;
	}

	/**
	 * Converts a date into the number of days accumulated since the first legal date.
	 * It uses the constants defined in the Parameters class.
	 * @param date - a string containing the date to be converted into days
	 * @param format - the format of the date
	 * @return - the converted date into the number of days
	 */
	public static Integer dateToDays(String date, byte format){

		Integer inDays = null;
		//check if the string is valid for a date
		if (format != DATE_INVALID && date != null && !date.equals("")
				&& (date.length() == 8 || date.length() == 10)) {

			//check if not already calculated
			String dateInYYYYMMDD = convertDateStringToYYYYMMDD(date, format);
			if ((inDays = DATES_IN_DAYS.get(dateInYYYYMMDD)) == null){

				int year = 0;
				int month = 0;
				int day = 0;

				//retrieve each date component depending on the date format
				switch (format){
				case DATE_ON_YYYYMMDD:
					//retrieve year value
					year = Integer.parseInt(date.substring(0, 4));
					//retrieve number of days for months + the days of current month
					month = Integer.parseInt(date.substring(4, 6));
					day = Integer.parseInt(date.substring(6,8));
					break;
				case DATE_ON_YYYYMMDD_WITH_SEPARATOR:
					year = Integer.parseInt(date.substring(0, 4));
					month = Integer.parseInt(date.substring(5, 7));
					day = Integer.parseInt(date.substring(8,10));
					break;
				case DATE_ON_DDMMYYYY:
					year = Integer.parseInt(date.substring(4, 8));
					month = Integer.parseInt(date.substring(2, 4));
					day = Integer.parseInt(date.substring(0,2));
					break;
				case DATE_ON_DDMMYYYY_WITH_SEPARATOR:
					year = Integer.parseInt(date.substring(6, 10));
					month = Integer.parseInt(date.substring(3, 5));
					day = Integer.parseInt(date.substring(0,2));
					break;
				}
				//check if not an illegal year
				if (year < FIRST_YEAR)
					return null;
				//compute total number of days
				try{
					inDays = getYearInDays(year) + getMonthInDays(month) + day;
				}catch(IllegalArgumentException e){
					Logging.add("\nFailed converting date: "+date, Logging.ERROR, true);
					return null;
				}

				//check if the date is actually valid
				if (!isDateCoherent(new int[]{year,month,day})){
					Logging.add("\nIllegal date found - year: "+year+
							" month: "+month+" day: "+day, Logging.ERROR, true);
					return null;
				}

				//check if it is a leap year and the month is superior to February  - to add the leap day
				if (isLeapYear(year) && month > 2)
					inDays += 1;

				//add it to the map of all possible dates
				DATES_IN_DAYS.put(dateInYYYYMMDD, inDays);
				DAYS_IN_DATES.put(inDays, dateInYYYYMMDD);
			}
		}

		return inDays;
	}

	/**
	 * Converts a number of days into a date of format yyyymmdd.
	 * @param days - the number of days to be converted into a date
	 * @return - the converted date; null if days is -1
	 */
	public static String daysToDate(int days){

		if (days != -1){

			//check if present in the map of all possible dates
			String date = DAYS_IN_DATES.get(days);
			if (date != null)
				return date;

			//create an instance of the calendar
			Calendar calendar = Calendar.getInstance();
			//check if we passed at least a year
			if (days <= 365){
				calendar.set(Calendar.YEAR, FIRST_YEAR);
				calendar.set(Calendar.DAY_OF_YEAR, (int)days);
				return (new SimpleDateFormat("yyyyMMdd").format(calendar.getTime())).toString();
			}else{
				//loop through the hash table with days accumulated with years
				for (int i = FIRST_YEAR; i <= LAST_YEAR; i++){
					//found a perfect match? we are at the new year's eve of last year
					if (getYearInDays(i) == days){
						return (i-1)+"1231";
					}else{
						//check in between which two years we are
						if (getYearInDays(i) < days && days < getYearInDays(i+1)){
							//and compute the right time of the year
							calendar.set(Calendar.YEAR, i);
							calendar.set(Calendar.DAY_OF_YEAR, (int)(days-getYearInDays(i)));
							return (new SimpleDateFormat("yyyyMMdd").format(calendar.getTime())).toString();
						}
					}
				}
			}
		}//days = -1

		return null;
	}

	/**
	 * Converts a number of days into date components of format yyyymmdd.
	 * The first components [0] is the year, the second [1] is the month and third component [2] is the day.
	 * @param days - the number of days to be converted into a date
	 * @return - an array with the date components; null if days is -1
	 */
	public static int[] daysToDateComponents(int days){
		if (days != -1)
			return splitDate(daysToDate(days), DATE_ON_YYYYMMDD);
		return null;
	}

	/**
	 * Splits a date string into date components (e.g., year, month, day).
	 * It makes use of the date format in order to properly split the date string.
	 * @param date - the date string to be split
	 * @param format - the format of the date (e.g., yyyymmdd or ddmmyyyy)
	 * @return - an array of integers containing the date components
	 */
	public static int[] splitDate(String date, byte format){
		int[] dateComponents = null;
		//check if the string is valid for a date
		if (format != DATE_INVALID && date != null && !date.equals("") &&
				date.length() == (format % 2 == 0 ? 8 : 10)) {
			dateComponents = new int[3];
			//retrieve each date component depending on the date format
			try{
				switch (format){
				case DATE_ON_YYYYMMDD:
					//retrieve year
					dateComponents[0] = Integer.parseInt(date.substring(0, 4));
					//retrieve month
					dateComponents[1] = Integer.parseInt(date.substring(4, 6));
					//retrieve days
					dateComponents[2] = Integer.parseInt(date.substring(6,8));
					break;
				case DATE_ON_YYYYMMDD_WITH_SEPARATOR:
					dateComponents[0] = Integer.parseInt(date.substring(0, 4));
					dateComponents[1] = Integer.parseInt(date.substring(5, 7));
					dateComponents[2] = Integer.parseInt(date.substring(8,10));
					break;
				case DATE_ON_DDMMYYYY:
					dateComponents[0] = Integer.parseInt(date.substring(4, 8));
					dateComponents[1] = Integer.parseInt(date.substring(2, 4));
					dateComponents[2] = Integer.parseInt(date.substring(0,2));
					break;
				case DATE_ON_DDMMYYYY_WITH_SEPARATOR:
					dateComponents[0] = Integer.parseInt(date.substring(6, 10));
					dateComponents[1] = Integer.parseInt(date.substring(3, 5));
					dateComponents[2] = Integer.parseInt(date.substring(0,2));
					break;
				}
			}catch (NumberFormatException e){
				return null;
			}
		}

		return dateComponents;
	}

	/**
	 * Converts years into days. It takes as input the first legal year and the last legal year and populates a list with
	 * the number of days accumulated for every year in between the first and last legal year. Leap years are taken into consideration.
	 * @return - a list with the number of accumulated days for each year between the first and last legal year
	 */
	private static HashMap<Integer, Integer> yearsToDays(){

		//initialize list
		HashMap<Integer, Integer> yearsToDays = new HashMap<Integer, Integer>();
		//the first element of the list (year 1850 and 0 day accumulated)
		yearsToDays.put(FIRST_YEAR, 0);
		//populate the list by adding the right number of days to the previous value
		for (int year = FIRST_YEAR+1; year <= LAST_YEAR+1; year++){
			//check also if previous year was a leap year (e.g., with 29th of February)
			yearsToDays.put(year, (yearsToDays.get(year-1))+(isLeapYear(year-1) ? 366 : 365));
		}

		return yearsToDays;
	}

	/**
	 * Converts the months into days. The leap year (i.e., with 29th of February) is taken into consideration in the yearsToDays method.
	 * Each month represents the key of a hash table and is denoted by its numeric order.
	 * The value represents how many days of the year have passed until the 1st of the month.
	 * @return - a hash table containing the number of days accumulated during a year for each month
	 */
	private static HashMap<Integer, Integer> monthsToDays(){

		HashMap<Integer, Integer> monthsToDays = new HashMap<Integer, Integer>();
		monthsToDays.put(1, 0); 	//January (0)
		monthsToDays.put(2, 31); 	//February (31)
		monthsToDays.put(3, 59); 	// March (31 + 28)
		monthsToDays.put(4, 90); 	// April (59 + 31)
		monthsToDays.put(5, 120); 	// May (90 + 30)
		monthsToDays.put(6, 151); 	// June (120 + 31)
		monthsToDays.put(7, 181); 	// July (151 + 30)
		monthsToDays.put(8, 212); 	// August (181 + 31)
		monthsToDays.put(9, 243); 	// September (212 + 31)
		monthsToDays.put(10, 273); 	// October (243 + 30)
		monthsToDays.put(11, 304); 	// November (273 + 31)
		monthsToDays.put(12, 334); 	// December (304 + 30)

		return monthsToDays;
	}

	/**
	 * Will convert date based on format to a string that represents a date
	 * under the yyyymmdd format. This is mainly used to keep track of all possible
	 * date to days combinations in the same format.
	 * @param date - the date of interest
	 * @param format - the format of the date
	 * @return - a string representation of date under yyyymmdd format
	 */
	public static String convertDateStringToYYYYMMDD(String date, byte format){
		switch (format){
		case DATE_ON_DDMMYYYY:
			return date.substring(4, date.length()).concat(date.substring(2,4)).concat(date.substring(0, 2));
		case DATE_ON_DDMMYYYY_WITH_SEPARATOR:
			return date.substring(date.length()-4).concat(date.substring(3,5)).concat(date.substring(0, 2));
		case DATE_ON_YYYYMMDD_WITH_SEPARATOR:
			return date.substring(0, 4).concat(date.substring(5,7)).concat(date.substring(8, 10));
		case DATE_ON_YYYYMMDD:
			return date;
		case DATE_INVALID:
			return null;
		}

		return null;
	}

	/**
	 * Will check and correct a user generated date in case it is
	 * a leap day (29th of February) and the year is not a leap year.
	 * @param days - the generated date as number of days since first legal date
	 * @return - the corrected number of days
	 */
	public final static int correctForLeapDay(int days){
		int[] dateComponents = daysToDateComponents(days);
		if (!isLeapYear(dateComponents[0]) && dateComponents[1] == 2 && dateComponents[2] == 29){
			dateComponents[1] = 3;
			dateComponents[2] = 1;
		}

		return dateToDays(dateComponents);
	}

	/**
	 * Will check and correct a user generated date in case it is
	 * a leap day (29th of February) and the year is not a leap year.
	 * @param dateComponents - the components of the date: 1st is year; 2nd is month, 3rd is day
	 * @return - the corrected date as number of days
	 */
	public final static int correctForLeapDay(int[] dateComponents){
		if (!isLeapYear(dateComponents[0]) && dateComponents[1] == 2 && dateComponents[2] == 29){
			dateComponents[1] = 3;
			dateComponents[2] = 1;
		}

		return dateToDays(dateComponents);
	}

	/**
	 * Returns the current date using the java Calendar class.
	 * @return - the current date
	 */
	public final static String getCurrentDate(){
		Calendar cal = Calendar.getInstance();
		SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd");
		return sdf.format(cal.getTime());
	}

	/**
	 * Returns the current date and current time using the java Calendar class.
	 * @return - the current date and time
	 */
	public final static String getCurrentDateAndTime(){
		Calendar cal = Calendar.getInstance();
		SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
		return sdf.format(cal.getTime());
	}

	/**
	 * Returns the current date and current time using the java Calendar class.
	 * It removes the : character from the return string.
	 * @return - the current date and time
	 */
	public final static String getCurrentDateAndTimeWithNoColumn(){
		Calendar cal = Calendar.getInstance();
		SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
		return sdf.format(cal.getTime()).replaceAll(":", "-").replaceAll(" ", "_");
	}

	/**
	 * Returns the current time using the java Calendar class.
	 * @return - the current time
	 */
	public final static String getCurrentTime(){
		Calendar cal = Calendar.getInstance();
		SimpleDateFormat sdf = new SimpleDateFormat("HH:mm:ss");
		return sdf.format(cal.getTime());
	}

	/*---------------------UNIT TESTING METHODS--------------------------*/
	/**
	 * Converts the date string into the number of days that passed
	 * from the first legal date defined (e.g., 18500101).
	 * It assumes the format is YYYYMMDD.
	 * @param date - the string representation of the date to be converted
	 * @return - the number of days from first legal date until date
	 */
	public static Integer dateToDaysUnitTest(String date){
		Integer inDays = null;
		if ((date != null && !date.equals("")) && date.length() == 8){
			//parse date components
			int year = Integer.parseInt(date.substring(0, 4));
			int month = Integer.parseInt(date.substring(4, 6));
			int day = Integer.parseInt(date.substring(6,8));
			if (year < FIRST_YEAR)
				return null;
			//compute total number of days
			try{
				inDays = getYearInDays(year) + getMonthInDays(month) + day;
			}catch(IllegalArgumentException e){
				Logging.add("\nFailed converting date: "+date, Logging.ERROR, true);
				return null;
			}
			//check if it is a leap year and the month is superior to February  - to add the leap day
			if (isLeapYear(year) && month > 2)
				inDays += 1;
		}

		return inDays;
	}

	/**
	 * Converts a number of days into a date of format yyyymmdd making use
	 * of the java calendar object. mainly used for testing purposes of
	 * back and forward date conversions.
	 * @param days - the number of days to be converted into a date
	 * @return - the converted date; null if days is -1
	 */
	public static String daysToDateUnitTestJavaCalendar(int days){
		if (days > 0){
			//create an instance of the calendar
			Calendar calendar = Calendar.getInstance();
			//check if we passed at least a year
			if (days <= 365){
				calendar.set(Calendar.YEAR, FIRST_YEAR);
				calendar.set(Calendar.DAY_OF_YEAR, (int)days);
				return (new SimpleDateFormat("yyyyMMdd").format(calendar.getTime())).toString();
			}else{
				//loop through the hash table with days accumulated with years
				for (int i = FIRST_YEAR; i <= LAST_YEAR; i++){
					//found a perfect match? we are at the new year's eve of last year
					if (getYearInDays(i) == days){
						return (i-1)+"1231";
						//check in between which two years we are
					}else if (getYearInDays(i) < days && days < getYearInDays(i+1)){
							//and compute the right time of the year
							calendar.set(Calendar.YEAR, i);
							calendar.set(Calendar.DAY_OF_YEAR, (int)(days-getYearInDays(i)));
							return (new SimpleDateFormat("yyyyMMdd").format(calendar.getTime())).toString();
					}
				}
			}
		}

		return null;
	}
	/*---------------------END OF UNIT TESTING METHODS--------------------------*/

}
