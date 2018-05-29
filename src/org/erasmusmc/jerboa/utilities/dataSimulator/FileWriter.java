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
 * Author: Marius Gheorghe (MG) - department of Medical Informatics	 					  *
 * 																						  *
 * $Rev:: 3961              $:  Revision of last commit                                   *
 * $Author:: root           $:  Author of last commit                                     *
 * $Date:: 2013-05#$:  Date and time (CET) of last commit								  *
 ******************************************************************************************/
package org.erasmusmc.jerboa.utilities.dataSimulator;

import java.util.ArrayList;
import java.util.List;

import org.erasmusmc.jerboa.utilities.dataSimulator.WriteCSVFile;

/**
 * Generic class to be extended by the different type of input file writers into CSV file format.
 *
 * @author MG
 *
 */
public abstract class FileWriter {

  protected WriteCSVFile file;
  protected List<String> header;

  /**
   * Constructor receiving the name of the output file
   * @param filename - the output file
   */
  public FileWriter(String filename){
    file = new WriteCSVFile(filename);
    header = new ArrayList<String>();
    writeHeader();
  }

  /**
   * Exports the header for the events file.
   */
  public abstract void writeHeader();


  /**
   * Writes object o to file in CSV format.
   * @param o - the object to be output
   */
  public void write(Object o){
	  if (o != null)
		   file.write(o.toString());
  }

  /**
   * Writes the list of objects to file in CSV format.
   * @param list - a list of objects to be output
   */
  public void write(List<?> list){
    if (list != null && list.size() > 0)
    	for (Object o : list)
    		write(o);
  }

  /**
   * Will flush and close the output buffer.
   */
  public void close(){
	file.flush();
    file.close();
  }
}