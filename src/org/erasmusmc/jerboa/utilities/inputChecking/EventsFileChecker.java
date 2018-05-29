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
 * $Rev:: 4605              $:  Revision of last commit                                   *
 * $Author:: root           $:  Author of last commit                                     *
 * $Date:: 					$:  Date and time (CET) of last commit						  *
 ******************************************************************************************/

package org.erasmusmc.jerboa.utilities.inputChecking;

import java.util.Arrays;
import java.util.List;
import org.erasmusmc.jerboa.dataClasses.Event;
import org.erasmusmc.jerboa.engine.InputFile;

import org.erasmusmc.jerboa.utilities.DateUtilities;
import org.erasmusmc.jerboa.utilities.stats.Stats;


/**
 * This class contains all methods used to check an event input file.
 * The input data is checked for integrity and coherence, record by record.
 * If errors found, an error log is output. In case the error number is
 * higher than the set limit ( @see Parameters ) ; the rest of the file is skipped.
 *
 * @author MG
 *
 */
public class EventsFileChecker extends InputFileChecker{

	/**
	 * Constructor receiving an input file to be checked.
	 * @param file - the input file
	 */
	public EventsFileChecker(InputFile file){
		super(file);
	}

	@Override
	public boolean attributesOK(String[] columns, long line) {

		byte dateFormat = file.getDateFormat();

		//retrieve mandatory data
		String patientID = columns[dataOrder[Event.COLUMN_PATIENT_ID]].trim();
		int[] dateComponents = DateUtilities.splitDate(columns[dataOrder[Event.COLUMN_DATE]].trim(), dateFormat);
		Integer eventDate = DateUtilities.dateToDays(dateComponents);
		String type = columns[dataOrder[Event.COLUMN_TYPE]].trim();

		//check ID
		if (patientID == null || patientID.equals("")){
			errorMessages.add("["+line+"]   No patient ID -- "+Arrays.toString(columns));
			return false;
		}

		//check event date
		if (DateUtilities.isDateCoherent(dateComponents)){
			if (eventDate == null){
				errorMessages.add("["+line+"]   Illegal date -- "+Arrays.toString(columns));
				return false;
			}
		}else{
			errorMessages.add("["+line+"]   Invalid date -- "+Arrays.toString(columns));
			return false;
		}

		//check type
		if ( type == null || type.equals("") ){
			errorMessages.add("["+line+"]   Invalid type -- "+Arrays.toString(columns));
			return false;
		}
		else {
			//check numeric values
			if (!numericValuesOK(type, columns, line)) {
				return false;
			}
		}

		//TODO - decide if extended data should be checked for integrity

		return true;
	}

	@Override
	public void resetCounters(){
		Stats.nbEvents = 0;
	}

	@Override
	public void updateCounters(long value){
		Stats.nbEvents = value;
		Stats.nbRecords += value;
	}

	//GETTERS
	public List<String> getErrorMessages() {
		return errorMessages;
	}
	public InputFile getInputFile(){
		return file;
	}

}
