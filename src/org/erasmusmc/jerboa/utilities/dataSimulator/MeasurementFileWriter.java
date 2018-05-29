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

import org.erasmusmc.jerboa.dataClasses.Measurement;
import org.erasmusmc.jerboa.utilities.DateUtilities;

/**
 * This class contains the necessary methods to export Measurement
 * objects into CSV file format.
 *
 * @author MG
 *
 */
public class MeasurementFileWriter extends FileWriter{

  /**
   * Constructor receiving the name of the output file
   * @param filename - the output file
   */
  public MeasurementFileWriter(String filename){
	  super(filename);
  }

  /**
   * Exports the header for the measurements file.
   */
  public void writeHeader() {
	  this.header.add("SubsetID");
	  this.header.add("PatientID");
	  this.header.add("Measurementtype");
	  this.header.add("Date");
	  this.header.add("Value");
	  this.file.write(header);
  }

  @Override
  public void write(Object o){
	  	Measurement m = new Measurement((Measurement)o);
		if (m != null){
			String s = m.subset+","+m.patientID+","+m.getType()+","+
				DateUtilities.daysToDate(m.date)+","+m.getValue();
			file.write(s);
		}
	}

}