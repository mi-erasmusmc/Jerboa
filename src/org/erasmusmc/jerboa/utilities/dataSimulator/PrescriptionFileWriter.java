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

import org.erasmusmc.jerboa.dataClasses.Prescription;
import org.erasmusmc.jerboa.utilities.DateUtilities;

/**
 * This class contains the necessary methods to export Prescription
 * objects into CSV file format.
 *
 * @author MG
 *
 */
public class PrescriptionFileWriter extends FileWriter{

  /**
   * Constructor receiving the name of the output file
   * @param filename - the output file
   */
  public PrescriptionFileWriter(String filename){
	  super(filename);
  }

  /**
   * Exports the header for the prescriptions file.
   */
  public void writeHeader() {
	  this.header.add("SubsetID");
	  this.header.add("PatientID");
	  this.header.add("ATC");
	  this.header.add("Date");
	  this.header.add("Duration");
	  this.header.add("Dose");
	  this.header.add("Indication");
	  this.file.write(header);
  }

  @Override
  public void write(Object o){
	  Prescription p = new Prescription((Prescription)o);
		if (p != null){
			String s = p.subset+","+p.patientID+","+p.getATC()+","+
					DateUtilities.daysToDate(p.date)+","+
					p.getDuration()+","+
					p.getDose()+","+
					p.getIndication();
			file.write(s);
		}
	}

}