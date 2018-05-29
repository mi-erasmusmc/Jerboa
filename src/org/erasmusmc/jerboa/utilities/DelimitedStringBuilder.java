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
 * $Rev::            	    $:  Revision of last commit                                	  *
 * $Author::PR				$:  Author of last commit                                     *
 * $Date:: 					$:  Date and time (CET) of last commit						  *
 ******************************************************************************************/
package org.erasmusmc.jerboa.utilities;

/**
 * Creates a delimited StringBuilder. The default is comma separated.
 *
 * @author - PR
 */
public class DelimitedStringBuilder{

	StringBuilder str;
	String delimiter = ",";

	/**
	 * Basic constructor initializing the string builder.
	 */
	public DelimitedStringBuilder() {
		str = new StringBuilder();
	}

	/**
	 * Constructor receiving a delimiter to be used in the creation of the string.
	 * @param delimiter - the character(s) serving as value delimiter in the string
	 */
	public DelimitedStringBuilder(String delimiter) {
		str = new StringBuilder();
		setDelimiter(delimiter);
	}

	/**
	 * Append a string and the delimiter to the string builder.
	 * @param string - the string to add
	 */
	public void append(String string){
		if (str.length()>0)
			str.append(delimiter);
		str.append(string);
	}

	/**
	 * Append a string without adding the delimiter to the string builder.
	 * @param string - the string to add
	 */
	public void appendWithoutDelimiter(String string){
		str.append(string);
	}

	/**
	 * Initializes the string builder with string
	 * @param string - the string
	 */
	public void set(String string){
		str = new StringBuilder(string);
	}
	/**
	 * Converts the StringBuilder to a String of characters.
	 * @return - the string representation of this string builder
	 */
	public String toString(){
		return str.toString();
	}

	/**
	 * Gets the delimiter (by default comma-separated).
	 * @return - the new delimiter
	 */
	public String GetDelimiter(){
		return delimiter;
	}

	/**
	 * Sets the delimiter (by default comma-separated).
	 * @param delimiter - the new delimiter
	 */
	public void setDelimiter(String delimiter){
		this.delimiter = delimiter;
	}

	/**
	 * Adds the end of line character to the string builder.
	 */
	public void addEol(){
		str.append(System.lineSeparator());
	}

}
