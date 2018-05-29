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

import org.erasmusmc.jerboa.dataClasses.Patient;
import org.erasmusmc.jerboa.engine.InputFile;

import org.erasmusmc.jerboa.utilities.DateUtilities;
import org.erasmusmc.jerboa.utilities.stats.Stats;
import org.erasmusmc.jerboa.config.DataDefinition;

/**
 * This class contains all methods used to check a patients input file.
 * The input data is checked for integrity and coherence, record by record.
 * If errors found, an error log is output. In case the error number is
 * higher than the set limit ( @see Parameters ); the rest of the files is skipped.
 *
 * @author MG
 *
 */
public class PatientsFileChecker extends InputFileChecker{


	/**
	 * Constructor receiving an input file.
	 * @param file - the file to be checked
	 */
	public PatientsFileChecker(InputFile file){
		super(file);
	}

	@Override
	public boolean attributesOK(String[] columns, long line) {

		//get data format
		byte dateFormat = file.getDateFormat();

		//retrieve patient data
		String patientID = columns[dataOrder[Patient.COLUMN_PATIENT_ID]].trim();
		int[] dateComponentsBirthDate = DateUtilities.splitDate(columns[dataOrder[Patient.COLUMN_BIRTHDATE]].trim(), dateFormat);
		Integer birthDate = DateUtilities.dateToDays(dateComponentsBirthDate);
		byte gender = AttributeChecker.checkGender(columns[dataOrder[Patient.COLUMN_GENDER]].trim());
		int[] dateComponentsStartDate = DateUtilities.splitDate(columns[dataOrder[Patient.COLUMN_START_DATE]].trim(), dateFormat);
		Integer startDate = DateUtilities.dateToDays(dateComponentsStartDate);
		int[] dateComponentsEndDate = DateUtilities.splitDate(columns[dataOrder[Patient.COLUMN_END_DATE]].trim(), dateFormat);
		Integer endDate = DateUtilities.dateToDays(dateComponentsEndDate);

		//check ID
		if (patientID == null || patientID.equals("")){
			errorMessages.add("["+line+"]   No patient ID -- "+Arrays.toString(columns));
			return false;
		}

		//check gender
		if (gender == DataDefinition.INVALID_GENDER){
			errorMessages.add("["+line+"]   Invalid gender -- "+Arrays.toString(columns));
			return false;
		}

		//check birth date
		if (DateUtilities.isDateCoherent(dateComponentsBirthDate)){
			if (birthDate == null){
				errorMessages.add("["+line+"]   Illegal birthdate -- "+Arrays.toString(columns));
				return false;
			}
		}else{
			errorMessages.add("["+line+"]   Invalid birthdate -- "+Arrays.toString(columns));
			return false;
		}

		//check start date - if valid and legal add to stats
		if (DateUtilities.isDateCoherent(dateComponentsStartDate)){
			if ((startDate = DateUtilities.dateToDays(dateComponentsStartDate)) == null){
				errorMessages.add("["+line+"]   Illegal start date -- "+Arrays.toString(columns));
				return false;
			}
		}else{
			errorMessages.add("["+line+"]   Invalid start date -- "+Arrays.toString(columns));
			return false;
		}

		//check end date - if valid and legal add to stats
		if (DateUtilities.isDateCoherent(dateComponentsEndDate)){
			if (endDate == null){
				errorMessages.add("["+line+"]   Illegal end date -- "+Arrays.toString(columns));
				return false;
			}

		}else{
			errorMessages.add("["+line+"]   Invalid end date -- "+Arrays.toString(columns));
			return false;
		}

		//check dates coherence
		if (endDate != null && startDate != null && endDate < startDate){
			errorMessages.add("["+line+"]   Start date > End date -- "+Arrays.toString(columns));
			return false;
		}

		if (birthDate != null && startDate != null && endDate != null){
			if (birthDate > startDate || birthDate > endDate){
				errorMessages.add("["+line+"]   Birth date > Start/End date -- "+Arrays.toString(columns));
				return false;
			}
		}

		//TODO decide if we check the integrity of the extended data

		return true;
	}

	@Override
	public void resetCounters(){
		Stats.nbPatients = 0;
		Stats.nbRecords = 0;
	}

	@Override
	public void updateCounters(long value){
		Stats.nbPatients = value;
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
