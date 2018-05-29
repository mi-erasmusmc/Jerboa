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
 * Author: Peter Rijnbeek (PR) - department of Medical Informatics						  *
 * 																						  *
 * $Rev::            	    $:  Revision of last commit                                	  *
 * $Author::				$:  Author of last commit                                     *
 * $Date:: 					$:  Date and time (CET) of last commit						  *
 ******************************************************************************************/
package org.erasmusmc.jerboa.utilities;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.TreeSet;

import org.erasmusmc.jerboa.utilities.stats.HistogramStats;

/**
 * Writes the bag and its statistics to a file.
 * Only combinations of columns that match a filter (ExtendedMultiKey) are written to file. The filter can contain wildcards.
 * The statistics can be either a simple count for category bags or full statistics on a histogram type bag.
 *
 * @author PR
 *
 */

public class OutputBagStats {

	//output related
	private String outputFilename;
	private OutputManager outputManager;

	//database column
	private String databaseName;
	private Boolean addDatabaseName = false;

	//CONSTRUCTORS
	/**
	 * Constructor receiving the name of the output file.
	 * @param Filename - the name of the output file
	 */
	public OutputBagStats(String Filename){
		this.outputFilename = Filename;
		outputManager = new OutputManager();
		outputManager.addFile(Filename);
	}

	/**
	 * Constructor receiving the name of the output file
	 * and the name of the database used in the current run.
	 * @param Filename - the name of the output file
	 * @param databaseName - the name of the database
	 */
	public OutputBagStats(String Filename, String databaseName){
		this.outputFilename = Filename;
		this.databaseName = databaseName;
		addDatabaseName = true;
		outputManager = new OutputManager();
		outputManager.addFile(Filename);
	}
	/**
	 * Add the header to the output file.
	 * @param header - the header to add
	 */
	public void addHeaderCount(String header){
		outputManager.write(outputFilename, header+",Count"+System.lineSeparator(), true);
	}

	/**
	 * Add the header including the statistical columns.
	 * @param header - the first set of columns
	 */
	public void addHeaderStats(String header){
		String out = "";
		out = out + header + ",Min,Max,Count,Mean,P25,P50,P75,SD"+System.lineSeparator();
		outputManager.write(outputFilename, out, true);
	}

	/**
	 * Outputs the counts in the MultiKeyBag based on a set of keys.
	 * For all wildcard keys all possible values are looped through
	 * @param bag - MultiKeyBag
	 * @param filterKeys - ExtendedMultiKey to loop through
	 */
	public void outputCount(MultiKeyBag bag,ExtendedMultiKey filterKeys){
		//TODO: Add Check Gender is first in MultiKey
		if (bag != null){

			List<TreeSet<Object>> values = new ArrayList<TreeSet<Object>>();

			for (int i=0; i<filterKeys.size();i++)
				if (filterKeys.getKey(i) instanceof Wildcard)
					values.add(bag.getKeyValuesAsObject(i));
				else {
					TreeSet<Object> fixedValue = new TreeSet<Object>();
					fixedValue.add(filterKeys.getKey(i));
					values.add(fixedValue);
				}

			switch (filterKeys.size()) {

			case 2:
				for (Object value0: values.get(0)){
					for (Object value1: values.get(1)){
						addToOutput(new ArrayList<String>(Arrays.asList(value0.toString(),value1.toString())),
								String.valueOf(bag.getCount(new ExtendedMultiKey(value0,value1))));

					}
				}
				break;

			case 3:
				for (Object value0: values.get(0)){
					for (Object value1: values.get(1)){
						for (Object value2: values.get(2)){

							addToOutput(new ArrayList<String>(Arrays.asList(value0.toString(),value1.toString(),value2.toString())),
									String.valueOf(bag.getCount(new ExtendedMultiKey(value0,value1,value2))));
						}

					}
				}
				break;

			case 4:
				for (Object value0: values.get(0)){
					for (Object value1: values.get(1)){
						for (Object value2: values.get(2)){
							for (Object value3: values.get(3)){

								addToOutput(new ArrayList<String>(Arrays.asList(value0.toString(),value1.toString(),value2.toString(),value3.toString())),
										String.valueOf(bag.getCount(new ExtendedMultiKey(value0,value1,value2,value3))));
							}
						}

					}
				}
				break;

			case 5:
				for (Object value0: values.get(0)){
					for (Object value1: values.get(1)){
						for (Object value2: values.get(2)){
							for (Object value3: values.get(3)){
								for (Object value4: values.get(4)){
									addToOutput(new ArrayList<String>(Arrays.asList(value0.toString(),value1.toString(),value2.toString(),value3.toString(),value4.toString())),
											String.valueOf(bag.getCount(new ExtendedMultiKey(value0,value1,value2,value3,value4))));

								}
							}
						}
					}
				}
				break;
			}
		}
	}

	/**
	 * Outputs the full statistics of a MultiKeyBag based on a set of keys.
	 * For all wildcard keys all possible values are looped through
	 * The bag should contain a numeric value as last key.
	 * The keys should be one less the size of the bag.
	 * @param bag - MultiKeyBag with a nummeric last key
	 * @param filterKeys - ExtendedMultiKey to loop through. Size one less the size of the bag!
	 */
	public void outputStats(MultiKeyBag bag,ExtendedMultiKey filterKeys){
		//TODO: Check of numeric values in last key.
		if (bag != null){

			List<TreeSet<Object>> values = new ArrayList<TreeSet<Object>>();

			for (int i=0; i<filterKeys.size();i++)
				if (filterKeys.getKey(i) instanceof Wildcard)
					values.add(bag.getKeyValuesAsObject(i));
				else {
					TreeSet<Object> fixedValue = new TreeSet<Object>();
					fixedValue.add(filterKeys.getKey(i));
					values.add(fixedValue);
				}

			switch (filterKeys.size()) {

			case 2:
				for (Object value0: values.get(0)){
					for (Object value1: values.get(1)){
						addToOutput(new ArrayList<String>(Arrays.asList(value0.toString(),value1.toString())),
								statsToString(bag.getHistogramStats(new ExtendedMultiKey(value0,value1)),true));

					}
				}
				break;

			case 3:
				for (Object value0: values.get(0)){
					for (Object value1: values.get(1)){
						for (Object value2: values.get(2)){

							addToOutput(new ArrayList<String>(Arrays.asList(value0.toString(),value1.toString(),value2.toString())),
									statsToString(bag.getHistogramStats(new ExtendedMultiKey(value0,value1,value2)),true));
						}

					}
				}
				break;

			case 4:
				for (Object value0: values.get(0)){
					for (Object value1: values.get(1)){
						for (Object value2: values.get(2)){
							for (Object value3: values.get(3)){

								addToOutput(new ArrayList<String>(Arrays.asList(value0.toString(),value1.toString(),value2.toString(),value3.toString())),
										statsToString(bag.getHistogramStats(new ExtendedMultiKey(value0,value1,value2,value3)),true));
							}
						}

					}
				}
				break;

			case 5:
				for (Object value0: values.get(0)){
					for (Object value1: values.get(1)){
						for (Object value2: values.get(2)){
							for (Object value3: values.get(3)){
								for (Object value4: values.get(4)){
									addToOutput(new ArrayList<String>(Arrays.asList(value0.toString(),value1.toString(),value2.toString(),value3.toString(),value4.toString())),
											statsToString(bag.getHistogramStats(new ExtendedMultiKey(value0,value1,value2,value3,value4)),true));

								}
							}
						}
					}
				}
				break;
			}
		}
	}

	/**
	 * Adds a line to the output including statistics
	 * @param columns - all columns to add
	 * @param Stats - a comma-separated string with statistics to add
	 */
	private void addToOutput(List<String> columns, String Stats){
		String out = "";
		if (addDatabaseName)
			out = databaseName+",";

		for (int i=0;i<columns.size();i++){
			out = out + columns.get(i) + ",";
		}
		out = out + Stats;
		out = out + System.lineSeparator();
		outputManager.write(outputFilename, out, true);
	}

	/**
	 * Returns a String representation of the statistics
	 * @param stats - statistics of a bag
	 * @param outputCount - if true the count is added in the total column, otherwise the sum
	 * @return - String representation or empty string if there is no data
	 * @see HistogramStats
	 */
	private String statsToString(HistogramStats stats, boolean outputCount){
		if (stats != null){
			if (stats.getCount()>0) {
				return 	stats.getMin() +","+
						stats.getMax() +","+
						(outputCount ? stats.getCount() : stats.getSum())+","+
						StringUtilities.format(stats.getMean())+","+
						StringUtilities.format(stats.getPercentile(25))+","+StringUtilities.format(stats.getPercentile(50))+","+
						StringUtilities.format(stats.getPercentile(75))+","+StringUtilities.format(stats.getStdDev());
			}
		}

		return ",,,,,"; //check consistency with number of elements in stats
	}

	/**
	 * Closes the output buffer assigned to the output file name.
	 */
	public void close(){
		outputManager.closeFile(outputFilename);
	}

}
