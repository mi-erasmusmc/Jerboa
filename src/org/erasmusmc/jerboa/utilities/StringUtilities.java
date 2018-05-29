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
 * $Author::				$:  Author of last commit                                     *
 * $Date:: 					$:  Date and time (CET) of last commit						  *
 ******************************************************************************************/
package org.erasmusmc.jerboa.utilities;

import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.Iterator;

import org.apache.commons.io.FilenameUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.text.StrMatcher;
import org.apache.commons.lang3.text.StrTokenizer;
import org.erasmusmc.jerboa.config.FilePaths;
import org.erasmusmc.jerboa.utilities.stats.HistogramStats;

/**
 * This class contains a collection of utility methods for string manipulation and display.
 * It extends the StringUtils class from the Apache common library.
 *
 * @author MG
 *
 */
public class StringUtilities extends StringUtils{

	//formatting
	public static final DecimalFormat DECIMAL_FORMAT = setDecimalFormat(2);
	public static final DecimalFormat DECIMAL_FORMAT_1 = setDecimalFormat(1);
	public static final DecimalFormat DECIMAL_FORMAT_FORCE_PRECISION = setDecimalFormat(2, true);
	public static final DecimalFormat DECIMAL_FORMAT_1_FORCE_PRECISION = setDecimalFormat(1, true);

	//max length of a path to be displayed on the GUI before repplacing the middle part with "..."
	public static final int MAX_STRING_LENGTH = 50;

	/**
	 * The short form of the Parameters.DECIMAL_FORMAT_FORCE_PRECISION.format.
	 * This format adds to value two decimals.
	 * @param value - the value to be formatted
	 * @return - the formatted double value
	 */
	public static String format(double value){
		return StringUtilities.DECIMAL_FORMAT_FORCE_PRECISION.format(value);
	}

	/**
	 * The short form of the Parameters.DECIMAL_FORMAT.format.
	 * It will not force decimals on value if not needed.
	 * @param value - the value to be formatted
	 * @return - the formatted double value
	 */
	public static String formatAsIs(double value){
		return StringUtilities.DECIMAL_FORMAT_FORCE_PRECISION.format(value);
	}

	/**
	 * The short form of the Parameters.DECIMAL_FORMAT_1.format.
	 * Formats value with one decimal if needed.
	 * @param value - the value to be formatted
	 * @return - the formatted double value
	 */
	public static String formatOneDec(double value){
		return StringUtilities.DECIMAL_FORMAT_1.format(value);
	}

	/**
	 * Sets the display format for parsing double values.
	 * @param fractionDigits - the precision to be used
	 * @return - the decimal format
	 */
	private static DecimalFormat setDecimalFormat(int fractionDigits){
		return setDecimalFormat(fractionDigits, false);
	}

	/**
	 * Sets the display format for parsing double values.
	 * Even if the number does not have any decimals, it
	 * is forced to display them (e.g. 34 will be 34.0 or 34.00).
	 * @param fractionDigits - the precision to be used
	 * @param forcePrecision - true if the number represented by fractionDigits
	 * should be added regardless if there are decimals or not; false otherwise
	 * @return - the decimal format
	 */
	public static DecimalFormat setDecimalFormat(int fractionDigits, boolean forcePrecision){
		DecimalFormat df = new DecimalFormat();
		DecimalFormatSymbols symbols = new DecimalFormatSymbols();
		symbols.setDecimalSeparator('.');
		//	symbols.setGroupingSeparator(' '); //thousands separator
		df.setGroupingUsed(false);
		df.setMaximumFractionDigits(fractionDigits);
		if (forcePrecision)
			df.setMinimumFractionDigits(fractionDigits);
		df.setDecimalFormatSymbols(symbols);

		return df;
	}

	/**
	 * Checks if path contains the working folder and replaces it with {@literal <working folder>/"}.
	 * Only for display reasons.
	 * @param path - the path to be checked for the working folder substring
	 * @return - the replaced path of the original if the working folder was not found
	 */
	public static String replaceWorkingPath(String path){
		if (path != null && /*path.length() > 60 &&*/
				path.contains(FilePaths.WORKING_PATH)) {
			path = path.replaceFirst(FilePaths.WORKING_PATH, "<working folder>");
		}

		return path;
	}

	/**
	 * Masks part of the string path in case it is longer than the MAX_STRING_LENGTH.
	 * It will display the beginning of path and the end, while the middle of it
	 * is replaced by "...". Only for display reasons.
	 * @param path - the path to be partially masked
	 * @return - a partially masked string if path is longer than the set threshold
	 */
	public static String maskWorkingPath(String path){
		if (path != null && path.length() > MAX_STRING_LENGTH)
			path = path.substring(0, 30).concat(".....").
					concat(path.substring(path.lastIndexOf("/"), path.length()));
		return path;
	}

	/**
	 * Splits a line from the input file into columns based on a
	 * field delimiter that is input file specific and a string delimiter
	 * which is set to be the double quotes character. It can process also
	 * fields which contain the separator between the string delimiters.
	 * @param line - a line from the input file
	 * @param separator - the field separator of the input file
	 * @return - an array of strings that represents the different fields obtained from the input line
	 */
	//NOT USED
	public static String[] splitLine(final String line, char separator) {
		ArrayList<String> columns = new ArrayList<String>();
		boolean notBetweenDelimiters = true;
		int start = 0;
		for(int i = 0; i < line.length(); i ++){
			if(line.charAt(i) == separator && notBetweenDelimiters){
				columns.add(line.substring(start,i));
				start = i+1;
			}else if(line.charAt(i) =='"'){
				notBetweenDelimiters = !notBetweenDelimiters;
			}
		}
		columns.add(line.substring(start));
		String [] array = new String[columns.size()];

		return columns.toArray(array);
	}

	/**
	 * Splits a line from the input file into columns based on a
	 * field delimiter that is input file specific and a string delimiter
	 * which is set to be either the double quotes character or single quote.
	 * It can process also fields which contain the separator between the string delimiters.
	 * Note that empty tokens are taken into consideration (missing values)
	 * This method makes use of a StrTokenizer object.
	 * @param line - a line from the input file
	 * @param separator - the field separator of the input file
	 * @return - an array of strings that represents the different fields obtained from the input line
	 */
	public static String[] splitLine(final String line, StrMatcher separator) {
		StrTokenizer tokenizer = new StrTokenizer(line, separator, StrMatcher.quoteMatcher());
		tokenizer.setEmptyTokenAsNull(false);
		tokenizer.setIgnoreEmptyTokens(false);
		String[] tokens = new String[tokenizer.size()];
		for (int i = 0; i < tokens.length; i++)
			tokens[i] = tokenizer.nextToken();

		return tokens;
	}

	/**
	 * Will trim the elements of array one by one.
	 * @param array - a vector of strings
	 * @return - the array with trimmed elements
	 */
	public static String[] trim(String[] array){
		if (array != null && array.length > 0)
			for (int i = 0; i < array.length; i ++)
				array[i] = array[i].trim();

		return array;
	}

	/**
	 * Will lower case all the elements of array one by one.
	 * @param array - a vector of strings
	 * @return - the array with lower cased elements
	 */
	public static String[] toLowerCase(String[] array){
		if (array != null && array.length > 0)
			for (int i = 0; i < array.length; i ++)
				array[i] = array[i].toLowerCase();

		return array;
	}

	/**
	 * Will add white spaces to the right of this string until
	 * it reaches size. If the length of the string is bigger
	 * than size, then the original string is returned (no truncation).
	 * @param s - the string to be padded
	 * @param size - the length of the string after padding
	 * @return - the padded to right string if its length inferior to size
	 */
	public static String padToRight(String s, int size){
		if (s != null){
			if (s.length() >= size)
				return s;
			else
				return StringUtils.rightPad(s, size, " ");
		}

		return "";
	}

	/**
	 * Will put an upper case letter in the beginning of
	 * each element of the string array obtained after
	 * splitting s with sep. Note that the initial string is lower cased
	 * and that all the occurrences of sep will be removed in
	 * the resulting string.
	 * @param s - the string of interest
	 * @param sep - the separator of the string elements
	 * @return - a capitalized string
	 */
	public static String capitalizeWords(String s, String sep){
		if (s == null || s.equals(""))
			return s;
		String[] split = s.toLowerCase().split(sep);
		for (int i = 0; i < split.length; i++)
			split[i] = StringUtils.capitalize(split[i]);

		return StringUtils.join(split, "");
	}

	/**
	 * Will put an upper case letter in the beginning of
	 * each element of the string array obtained after
	 * splitting s with sep. Note that the initial string is lower cased.
	 * @param s - the string of interest
	 * @param sep - the separator of the string elements
	 * @param keepSeparator - if true, the initial separator is kept in the resulting string;
	 * otherwise it will be removed (same behavior as capitalizeWords(s, sep))
	 * @return - a capitalized string
	 */
	public static String capitalizeWords(String s, String sep, boolean keepSeparator){
		if (s == null || s.equals(""))
			return s;
		String[] split = s.toLowerCase().split(sep);
		for (int i = 0; i < split.length; i++)
			split[i] = StringUtils.capitalize(split[i]);

		return StringUtils.join(split, keepSeparator ? sep : "");
	}

	/**
	 * Will put an upper case in the beginning of each string present in s.
	 * @param s - an array of strings
	 * @return - the same array with capitalized elements
	 */
	public static String[] capitalize(String[] s){
		if (s != null && s.length > 0)
			for (int i = 0; i < s.length; i++)
				s[i] = StringUtils.capitalize(s[i]);

		return s;
	}

	/**
	 * Will put suffix before the extension of the file name s.
	 * For instance if suffix is "_test" and fileName is MyFile.csv, the
	 * resulting string will be MyFile_test.csv.
	 * @param fileName - the file name to be processed
	 * @param suffix - the string to be added at the end of file name
	 * @return - a file name having concatenated suffix before its extension
	 */
	public static String addSuffixToFileName(String fileName, String suffix){
		String ext = FilenameUtils.getExtension(fileName).equals("") ? null : "." + FilenameUtils.getExtension(fileName);
		return ext == null ? fileName+suffix : fileName.replace(ext, suffix+ext);
	}

	/**
	 * Will put suffix before the extension of the file name s.
	 * For instance if suffix is "_test" and fileName is MyFile.csv, the
	 * resulting string will be MyFile_test.csv.
	 * @param fileName - the file name to be processed
	 * @param suffix - the string to be added at the end of file name
	 * @param replaceExtension - true if the extension will be replaced.
	 * Note that this implies that either suffix contains the new extension or
	 * it will be added a posteori; false if the extension should be left in place.
	 * @return - a file name having concatenated suffix and its extension optionally replaced.
	 */
	public static String addSuffixToFileName(String fileName, String suffix, boolean replaceExtension){
		String ext = FilenameUtils.getExtension(fileName).equals("") ? null : "." + FilenameUtils.getExtension(fileName);
		return replaceExtension ? (ext == null ? fileName+suffix : fileName.replace("." + FilenameUtils.getExtension(fileName), suffix)) :
			addSuffixToFileName(fileName, suffix);
	}

	/**
	 * Returns a String representation of the statistics.
	 * @param stats - statistics of a bag
	 * @param outputCount - if true the count is added in the total column, otherwise the sum
	 * @param delimiter - the string delimiter to be used between the values
	 * @return - String representation or empty string if there is no data
	 * @see HistogramStats
	 */
	public String statsToString(HistogramStats stats, boolean outputCount, String delimiter){
		if (stats != null){
			if (stats.getCount()>0) {
				return 	format(stats.getMin()) +delimiter+
						format(stats.getMax()) +delimiter+
						(outputCount ? format(stats.getCount()) : format(stats.getSum()))+delimiter+
						format(stats.getMean())+delimiter+
						format(stats.getPercentile(25))+delimiter+
						format(stats.getPercentile(50))+delimiter+
						format(stats.getPercentile(75))+delimiter+
						format(stats.getStdDev());
			}
		}

		return delimiter+delimiter+delimiter+delimiter+delimiter+delimiter+delimiter; //check consistency with number of elements in stats
	}

	/**
	 * Checks if value object is numeric.
	 * This method checks if value is an instance of Integer
	 * or Double or Long. If not, it tries to parse value to one of those three types.
	 * @param str - the string to be checked if numeric
	 * @return - true if value is numeric; false otherwise
	 */
	public static boolean isNumeric(String str){
		try {
		    Double.parseDouble(str);
		 }catch(NumberFormatException nfe){
		    return false;
		 }
		  return true;
	}

	/**
	 * Checks if s is a logical operator or not.
	 * @param s - the string to be checked
	 * @return - true if s is one of the following:
	 * {@literal "||", "&&", or "!"; false otherwise}
	 */
	public static boolean isLogicalOperator(String s){
		return 	s.equals("||") || s.equals("&&") ||
				s.equals("!");
	}

	/**
	 * Checks if s is an equality operator or not.
	 * @param s - the string to be checked
	 * @return - true if s is one of the following:
	 * {@literal "!=", "=="; false otherwise}
	 */
	public static boolean isEqualityOperator(String s){
		return s.equals("!=") || s.equals("==");
	}

	/**
	 * Checks if s is a comparison operator or not.
	 * @param s - the string to be checked
	 * @return - true if s is one of the following:
	 * {@literal ">", "<", ">=" or "<="; false otherwise}
	 */
	public static boolean isComparisonOperator(String s){
		return 	s.equals(">") || s.equals("<") ||
				s.equals(">=") || s.equals("<=");
	}

	/**
	 * Checks if s is an arithmetic operator or not.
	 * @param s - the string to be checked
	 * @return - true if s is one of the following:
	 * {@literal "+", "-", "*" or "/"; false otherwise}
	 */
	public static boolean isArithmeticOperator(String s){
		return 	s.equals("+") || s.equals("-") ||
				s.equals("*") || s.equals("/");
	}

	/**
	 * Checks if s is a parenthesis or bracket.
	 * @param s - the string to be checked
	 * @return - true if s is a parenthesis; false otherwise
	 */
	public static boolean isParenthesis(String s){
		return s.equals("(") || s.equals(")") ||
			   s.equals("[") || s.equals("]");
	}

	/**
	 * Checks if the string s has quotes in the beginning and in the end.
	 * @param s - the string to be checked
	 * @return - true if s is delimited with quotes; false otherwise
	 */
	public static boolean isQuotedString(String s){
		return s.startsWith("\"") || s.startsWith("\'");
	}

	/**
	 * Will join the elements in collection s with delimiter between each two elements.
	 * @param s - the collection of elements to be joined
	 * @param delimiter - the delimiter to be used between elements
	 * @return - a string representation of the joined elements of s
	 */
	public static String join(Collection<?> s, String delimiter) {
		StringBuffer buffer = new StringBuffer();
		Iterator<?> iter = s.iterator();
		if (iter.hasNext()) {
			buffer.append(iter.next().toString());
		}
		while (iter.hasNext()) {
			buffer.append(delimiter);
			buffer.append(iter.next().toString());
		}
		return buffer.toString();
	}

	/**
	 * This class represents a custom string comparator
	 * that allows the user to sort first by length of a
	 * string and then based on characters.
	 *
	 * @author MG
	 *
	 */
	public static class StringComparatorWithLength implements Comparator<String>{
	    @Override
	    public int compare(String o1, String o2) {
	      if (o1.length() > o2.length()) {
	         return 1;
	      } else if (o1.length() < o2.length()) {
	         return -1;
	      }
	      return o1.compareTo(o2);
	    }
	}

}
