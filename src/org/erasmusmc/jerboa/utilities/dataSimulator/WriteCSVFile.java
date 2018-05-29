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
 * Author: Marius Gheorghe (MG) - department of Medical Informatics	  					  *
 * 																						  *
 * $Rev:: 3961              $:  Revision of last commit                                   *
 * $Author:: root           $:  Author of last commit                                     *
 * $Date:: 2013-05#$:  Date and time (CET) of last commit								  *
 ******************************************************************************************/
package org.erasmusmc.jerboa.utilities.dataSimulator;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.util.Iterator;
import java.util.List;

import org.erasmusmc.jerboa.utilities.FileUtilities;

/**
 * This class contains all methods necessary to output data into CSV format.
 *
 * @author MG
 *
 */
public class WriteCSVFile {

	private BufferedWriter bufferedWrite;

	/**
	 * Constructor accepting the name of the output file.
	 * @param filename - the output file
	 */
	public WriteCSVFile(String filename){
		FileOutputStream PSFFile;
		try {
			PSFFile = FileUtilities.openOutputStream(new File(filename));
			bufferedWrite = new BufferedWriter( new OutputStreamWriter(PSFFile), 10000);
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	/**
	 * Writes to file a list of strings.
	 * @param string - the list of strings to be output
	 */
	public void write(List<String> string){
		try {
			bufferedWrite.write(columns2line(string));
			bufferedWrite.newLine();
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	/**
	 * Writes string to file.
	 * @param string - the string to be output
	 */
	public void write(String string){
		try {
			bufferedWrite.write(string);
			bufferedWrite.newLine();
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	/**
	 * Converts the elements of the list columns into fields separated by comma.
	 * It supports quotes as string delimiters.
	 * @param columns - the list of elements to be converted into a line
	 * @return - a line of comma separated values
	 */
	public static String columns2line(List<String> columns) {
		StringBuilder sb = new StringBuilder();
		Iterator<String> iterator = columns.iterator();
		while (iterator.hasNext()){
			String column = iterator.next();
			if (column != null){
				column = column.replace("\"", "\\\"");
				if (column.contains(","))
					column = "\"" + column + "\"";
				sb.append(column);
			}
				if (iterator.hasNext())
					sb.append(",");
		}
		return sb.toString();
	}

	/**
	 * Flush the output buffer.
	 */
	public void flush(){
		try {
			bufferedWrite.flush();
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	/**
	 * Close the output buffer.
	 */
	public void close() {
		try {
			bufferedWrite.close();
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

}