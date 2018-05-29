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
 * $Rev:: 3909              $:  Revision of last commit                                   *
 * $Author:: MG				$:  Author of last commit                                     *
 * $Date:: 					$:  Date and time (CET) of last commit						  *
 ******************************************************************************************/
package org.erasmusmc.jerboa.utilities;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.apache.commons.lang3.text.StrBuilder;
import org.apache.commons.lang3.text.StrTokenizer;

/**
 * This class represents an utility class to parse the settings of worker parameters passed under a string representation.
 * It represents a collection of parsing methods and getters of the resulting tokenized strings.
 * It is considered that a line that is to be parsed is composed of tokens, which are delimited by a certain string of
 * characters or a single character and that each token is composed of one or multiple values, delimited by a/the same string
 * of characters or single character. Note that the functionality for both string dellimiters and character delimiters is supported.
 * @author MG
 *
 */
public class ParameterParser {

	public static final String DEFAULT_TOKEN_DELIMITER = ";";
	public static final String DEFAULT_VALUE_DELIMITER = ",";

	public static final char DEFAULT_CHAR_TOKEN_DELIMITER = ';';
	public static final char DEFAULT_CHAR_VALUE_DELIMITER = ',';

	//holds the different tokens
	private List<String> tokens;

	//holds the different values for each token
	private List<String[]> values;

	//the line to be parsed
	private String line;

	//CONSTRUCTORS
	/**
	 * Basic constructor initialized with the line to be parsed.
	 * @param line - the line to be processed
	 */
	public ParameterParser(String line){
		this.line = line;
		this.tokens = new ArrayList<String>();
		this.values = new ArrayList<String[]>();
	}

	/**
	 * Constructor used for the single character delimiters.
	 * Note that this constructor will already parse the line and populate the
	 * tokens and values parameters.
	 * @param line - the line to be parsed
	 * @param tokenDelimiter - the single character delimiting the tokens
	 * @param valueDelimiter - the single character delimiting the value
	 */
	public ParameterParser(String line, String tokenDelimiter, String valueDelimiter){
		this.line = line;
		this.tokens = new ArrayList<String>();
		this.values = new ArrayList<String[]>();
		parse(this.line,tokenDelimiter, valueDelimiter);
	}

	/**
	 * Constructor used for the delimiters under a string representation.
	 * Note that this constructor will already parse the line and populate the
	 * tokens and values parameters.
	 * @param line - the line to be parsed
	 * @param tokenDelimiter - the string delimiting the tokens
	 * @param valueDelimiter - the string delimiting the value
	 */
	public ParameterParser(String line, char tokenDelimiter, char valueDelimiter){
		this.line = line;
		this.tokens = new ArrayList<String>();
		this.values = new ArrayList<String[]>();
		parse(this.line,tokenDelimiter, valueDelimiter);
	}

	/**
	 * Splits line into tokens based on the tokenDelimiter and subsequently
	 * each token is split into its values based on the valueDelimiter.
	 * The result is a list of arrays of strings, an array representing all
	 * the different values of a token. If the delimiters are null or empty strings,
	 * the default delimiters will be used instead. Note that there is no guarantee
	 * that the result is correct if the delimiters are not specified.
	 * @param line - the line to be parsed
	 * @param tokenDelimiter - the delimiter between tokens as a string of characters
	 * @param valueDelimiter - the delimiter between the values of a token as a string of characters
	 * @return - a list of string arrays representing the line parsed into tokens and each token into its values
	 */
	public List<String[]> parse(String line, String tokenDelimiter, String valueDelimiter){

		this.tokens = parseToList(line, tokenDelimiter, true);
		if (tokens.size() > 0){
			this.values = new ArrayList<String[]>();
			for (String token : tokens){
				this.values.add(parseToArray(token, valueDelimiter, false));
			}
		}

		return this.values;
	}

	/**
	 * Splits line into tokens based on the tokenDelimiter and subsequently
	 * each token is split into its values based on the valueDelimiter.
	 * The result is a list of arrays of strings, an array representing all
	 * the different values of a token. If the delimiters are null or empty strings,
	 * the default delimiters will be used instead. Note that there is no guarantee
	 * that the result is correct if the delimiters are not specified.
	 * @param line - the line to be parsed
	 * @param tokenDelimiter - the delimiter between tokens as a single character
	 * @param valueDelimiter - the delimiter between the values of a token as a single character
	 * @return - a list of string arrays representing the line parsed into tokens and each token into its values
	 */
	public List<String[]> parse(String line, char tokenDelimiter, char valueDelimiter){

		this.tokens = parseToList(line, tokenDelimiter, true);
		if (tokens.size() > 0){
			this.values = new ArrayList<String[]>();
			for (String token : tokens){
				this.values.add(parseToArray(token, valueDelimiter, false));
			}
		}

		return this.values;
	}

	/**
	 * Splits line into strings based on delimiter.
	 * The resulting strings are put in a list which is returned.
	 * Note that it does NOT make use of the class parameters for storage.
	 * @param line - the line to be parsed
	 * @param delimiter - the string representing the delimiter
	 * @return - a list of strings representing the parsed values from line
	 */
	public static List<String> parseToList(String line, String delimiter){
		List<String> list = new ArrayList<String>();
		if (line != null && (delimiter != null && !delimiter.equals(""))){
			StrTokenizer strLine = new StrTokenizer(line, delimiter);
			strLine.setIgnoreEmptyTokens(false);
			Collections.addAll(list, strLine.getTokenArray());
		}

		return list;
	}

	/**
	 * Splits line into an array of strings based on delimiter.
	 * The resulting strings are put in an array which is returned.
	 * Note that it does NOT make use of the class parameters for storage.
	 * @param line - the line to be parsed
	 * @param delimiter - the string representing the delimiter
	 * @param isToken - true if line represents a token; false otherwise
	 * @return - an array of strings representing the parsed values from line
	 */
	public static String[] parseToArray(String line, String delimiter){
		String[] array = new String[]{};
		if (line != null && (delimiter != null && !delimiter.equals(""))){
			StrTokenizer strLine = new StrTokenizer(line, delimiter);
			strLine.setIgnoreEmptyTokens(false);
			array = strLine.getTokenArray();
		}

		return array;
	}

	/**
	 * Splits line into strings based on delimiter.
	 * The resulting strings are put in a list which is returned.
	 * Note that it does NOT make use of the class parameters for storage.
	 * @param line - the line to be parsed
	 * @param delimiter - the single character representing the delimiter
	 * @return - a list of strings representing the parsed values from line
	 */
	public static List<String> parseToList(String line, char delimiter){
		List<String> list = new ArrayList<String>();
		if (line != null && delimiter != '\u0000'){
			StrTokenizer strLine = new StrTokenizer(line, delimiter);
			strLine.setIgnoreEmptyTokens(false);
			Collections.addAll(list, strLine.getTokenArray());
		}

		return list;
	}

	/**
	 * Splits line into an array of strings based on delimiter.
	 * The resulting strings are put in an array which is returned.
	 * Note that it does NOT make use of the class parameters for storage.
	 * @param line - the line to be parsed
	 * @param delimiter - the single character representing the delimiter
	 * @return - an array of strings representing the parsed values from line
	 */
	public static String[] parseToArray(String line, char delimiter){
		String[] array = new String[]{};
		if (line != null && delimiter != '\u0000'){
			StrTokenizer strLine = new StrTokenizer(line, delimiter);
			strLine.setIgnoreEmptyTokens(false);
			array = strLine.getTokenArray();
		}

		return array;
	}

	//SPECIFIC METHODS
	/**
	 * Splits line into strings based on delimiter.
	 * The resulting strings are put in a list which is returned.
	 * Note that it does NOT make use of the class parameters for storage.
	 * @param line - the line to be parsed
	 * @param delimiter - the string representing the delimiter
	 * @param isToken - true if line represents a token; false otherwise
	 * @return - a list of strings representing the parsed values from line
	 */
	private List<String> parseToList(String line, String delimiter, boolean isToken){
		List<String> list = new ArrayList<String>();
		if (line != null){
			StrTokenizer strLine = new StrTokenizer(line,
					(delimiter == null || delimiter.equals("") ?
							(isToken ? DEFAULT_TOKEN_DELIMITER : DEFAULT_VALUE_DELIMITER) : delimiter));
			strLine.setIgnoreEmptyTokens(false);
			Collections.addAll(list, strLine.getTokenArray());
		}

		return list;
	}

	/**
	 * Splits line into an array of strings based on delimiter.
	 * The resulting strings are put in an array which is returned.
	 * Note that it does NOT make use of the class parameters for storage.
	 * @param line - the line to be parsed
	 * @param delimiter - the string representing the delimiter
	 * @param isToken - true if line represents a token; false otherwise
	 * @return - an array of strings representing the parsed values from line
	 */
	private String[] parseToArray(String line, String delimiter, boolean isToken){
		String[] array = new String[]{};
		if (line != null){
			StrTokenizer strLine = new StrTokenizer(line,
					(delimiter == null || delimiter.equals("") ?
							(isToken ? DEFAULT_TOKEN_DELIMITER : DEFAULT_VALUE_DELIMITER) : delimiter));
			strLine.setIgnoreEmptyTokens(false);
			array = strLine.getTokenArray();
		}

		return array;
	}

	/**
	 * Splits line into strings based on delimiter.
	 * The resulting strings are put in a list which is returned.
	 * Note that it does NOT make use of the class parameters for storage.
	 * @param line - the line to be parsed
	 * @param delimiter - the single character representing the delimiter
	 * @param isToken - true if line represents a token; false otherwise
	 * @return - a list of strings representing the parsed values from line
	 */
	private List<String> parseToList(String line, char delimiter, boolean isToken){
		List<String> list = new ArrayList<String>();
		if (line != null){
			StrTokenizer strLine = new StrTokenizer(line,
					(delimiter == '\u0000' ? (isToken ?
							DEFAULT_CHAR_TOKEN_DELIMITER : DEFAULT_CHAR_VALUE_DELIMITER) : delimiter));
			strLine.setIgnoreEmptyTokens(false);
			Collections.addAll(list, strLine.getTokenArray());
		}

		return list;
	}

	/**
	 * Splits line into an array of strings based on delimiter.
	 * The resulting strings are put in an array which is returned.
	 * Note that it does NOT make use of the class parameters for storage.
	 * @param line - the line to be parsed
	 * @param delimiter - the single character representing the delimiter
	 * @param isToken - true if line represents a token; false otherwise
	 * @return - an array of strings representing the parsed values from line
	 */
	private String[] parseToArray(String line, char delimiter, boolean isToken){
		String[] array = new String[]{};
		if (line != null){
			StrTokenizer strLine = new StrTokenizer(line,
					(delimiter == '\u0000' ? (isToken ?
							DEFAULT_CHAR_TOKEN_DELIMITER : DEFAULT_CHAR_VALUE_DELIMITER) : delimiter));
			strLine.setIgnoreEmptyTokens(false);
			array = strLine.getTokenArray();
		}

		return array;
	}

	//GETTERS
	public List<String> getTokens() {
		return tokens;
	}

	public List<String[]> getValues() {
		return values;
	}

	/**
	 * Returns the token as a string that is found at index
	 * in the list of tokens.
	 * @param index - the index of the token in the list
	 * @return - a string representation of the token at index
	 */
	public String getToken(int index) {
		return (tokens != null &&
				(tokens.size() > 0 && tokens.size() > index)) ?
						tokens.get(index) : null;
	}

	/**
	 * Returns all the values of a certain token found at index in the list.
	 * @param index - the index of the token of interest
	 * @return - and array of strings representing the values of the token found at index in the list.
	 */
	public String[] getValues(int index) {
		return (values != null &&
				(values.size() > 0 && values.size() > index)) ?
						values.get(index) : null;
	}

	/**
	 * Returns a human readable format of the original line that is to be parsed
	 * and the resulting tokens with all of their different values.
	 * @return - the content of the values parameter under a string representation
	 */
	public String toString(){
		StrBuilder sb = new StrBuilder();
		System.out.println("Original line: "+line);
		if (values != null && values.size() > 0)
			for (int i = 0; i < values.size(); i++){
				sb.append("Token "+(i+1)+" :");
				for (String s : values.get(i))
					sb.append(s + " ");
				sb.appendNewLine();
			}
		return sb.toString();
	}

	//MAIN METHOD FOR TESTING AND DEBUGGING
	public static void main(String[] args){

		String line = "AD,AB|AC;BLA,TRA,FRA;BOO";
		ParameterParser pp = new ParameterParser(line);
	//	pp.parse(line, null, null);
	//	System.out.println(pp.toString());

		System.out.println(ParameterParser.parseToArray(line, ",").toString());

		pp.parse(line, ";", ",");
		System.out.println(pp.toString());

	}
}
