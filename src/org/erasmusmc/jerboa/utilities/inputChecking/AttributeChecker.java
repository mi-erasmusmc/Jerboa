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
 * $Rev:: 4443              $:  Revision of last commit                                   *
 * $Author:: root           $:  Author of last commit                                     *
 * $Date:: 					$:  Date and time (CET) of last commit						  *
 ******************************************************************************************/

package org.erasmusmc.jerboa.utilities.inputChecking;

import java.util.regex.Pattern;

import org.erasmusmc.jerboa.config.DataDefinition;

/**
 * This class contains a collection of methods used to verify the integrity
 * of different types of attributes contained in the input files.
 *
 * @author MG
 *
 */
public class AttributeChecker {

	//attribute
	private static final String ATCregex = "^([a-zA-Z])(\\d\\d)?([a-zA-Z][a-zA-Z])?(\\d\\d)?$";
	private static final Pattern ATCpattern = Pattern.compile(ATCregex);

	/**
	 * Basic constructor.
	 */
	public AttributeChecker(){
		super();
	}

	/**
	 * Checks if attribute represents a valid gender.
	 * The allowed values are "male", "female", "unknown", "m", "f", "u","".
	 * @param attribute - to be checked if valid gender
	 * @return one of the assigned constant values stored in DataDefinition class
	 * @see DataDefinition
	 */
	public static byte checkGender(String attribute){
	 if (attribute != null){
		 return (attribute.equals("") || attribute.toLowerCase().equals("u") || attribute.toLowerCase().equals("unknown")) ? DataDefinition.UNKNOWN_GENDER :
			 (attribute.toLowerCase().equals("f") || attribute.toLowerCase().equals("female") ? DataDefinition.FEMALE_GENDER :
			 (attribute.toLowerCase().equals("m") || attribute.toLowerCase().equals("male") ? DataDefinition.MALE_GENDER : DataDefinition.INVALID_GENDER));
	 }
	 return DataDefinition.INVALID_GENDER;
	}

	/**NOT USED - SHOULD REPLACE THE METHOD isValidATC(String attribute)
	 * Checks if the attribute is a valid ATC code.
	 * The checking is done based on regular expressions.
	 * All detail levels of ATC codes are supported.
	 * @param attribute - the attribute to be checked if valid ATC code
	 * @return - true if attribute is a valid ATC code; false otherwise
	 */
	public static boolean isATC(String attribute){
		return (attribute != null && !attribute.equals("")) ?
			(ATCpattern.matcher(attribute)).matches() : null;
	}

	/**
	 * Basic check if the attribute field is not empty.
	 * Note that it should be replaced by the method isATC(attribute) of this classs.
	 * @param attribute - the attribute to be checked if valid ATC code
	 * @return - true if attribute is a valid ATC code; false otherwise
	 */
	public static boolean isValidATC(String attribute){
		return (attribute != null && !attribute.equals(""));
	}

	/**
	 * Checks if attribute represents a valid duration.
	 * A valid duration is considered if it is positive (in some cases allowed null).
	 * @param attribute - the duration to be checked if valid
	 * @return - true if attribute is a valid duration; false otherwise
	 */
	public static boolean isValidDuration(String attribute){
		return (attribute != null && isPositiveNumber(attribute));
	}

	/**
	 * Checks if the attribute represents a positive number.
	 * @param attribute - the attribute to be checked
	 * @return - true if attribute is a positive number; false otherwise
	 */
	private static boolean isPositiveNumber(String attribute){
		Double number;
		try{
			number = Double.parseDouble(attribute);
		}catch (NumberFormatException nfe){
			return false;
		}return number >= 0;
	}

	/**
	 * Checks if an input string is numeric. Deals also with negative and float numbers
	 * by means of regex matching.
	 * @param attribute - the string to be checked
	 * @return - true if attribute is numeric; false otherwise
	 */
	public static boolean isNumeric(String attribute) {
		return attribute.matches("^-?\\d+(\\.\\d+)?$");
	}

}
