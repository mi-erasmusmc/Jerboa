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

import org.erasmusmc.jerboa.dataClasses.Prescription;
import org.erasmusmc.jerboa.engine.InputFile;

import org.erasmusmc.jerboa.utilities.DateUtilities;
import org.erasmusmc.jerboa.utilities.stats.Stats;

/**
 * This class contains all methods used to check a prescription input file.
 * The input data is checked for integrity and coherence, record by record.
 * If errors found, an error log is output. In case the error number is
 * higher than the set limit ( @see Parameters ); the rest of the files is skipped.
 *
 * @author MG
 *
 */
public class PrescriptionsFileChecker extends InputFileChecker{

	/**
	 * Constructor receiving an input file.
	 * @param file - the file to be checked for integrity
	 */
	public PrescriptionsFileChecker(InputFile file){
		super(file);
	}

	@Override
	public boolean attributesOK(String[] columns, long line) {

		//get data format
		byte dateFormat = file.getDateFormat();

		//retrieve data
		String patientID = columns[dataOrder[Prescription.COLUMN_PATIENT_ID]].trim();
		String atcCode = columns[dataOrder[Prescription.COLUMN_TYPE]].trim();
		int[] dateComponents = DateUtilities.splitDate(columns[dataOrder[Prescription.COLUMN_DATE]].trim(), dateFormat);
		Integer prescriptionDate = DateUtilities.dateToDays(dateComponents);
		String duration = columns[dataOrder[Prescription.COLUMN_DURATION]].trim();

		//check patient ID
		if (patientID == null || patientID.equals("")){
			errorMessages.add("["+line+"]   No patient ID -- "+Arrays.toString(columns));
			return false;
		}

		//check ATC code
		if (!AttributeChecker.isValidATC(atcCode)){
			errorMessages.add("["+line+"]   Invalid ATC code -- "+Arrays.toString(columns));
			return false;
		}

		//check prescription date
		if (DateUtilities.isDateCoherent(dateComponents)){
			if (prescriptionDate == null){
				errorMessages.add("["+line+"]   Illegal date -- "+Arrays.toString(columns));
				return false;
			}
		}else{
			errorMessages.add("["+line+"]   Invalid date -- "+Arrays.toString(columns));
			return false;
		}

		//check duration
		if (!AttributeChecker.isValidDuration(duration)){
			errorMessages.add("["+line+"] Illegal duration -- "+Arrays.toString(columns));
			return false;
		}

		//check numeric values
		if ((atcCode != null) && (atcCode != "")) {
			if (!numericValuesOK(atcCode, columns, line)) {
				return false;
			}
		}

		//TODO - decide if extended data should be checked for integrity

		return true;
	}


	@Override
	public void resetCounters(){
		Stats.nbPrescriptions = 0;
	}

	@Override
	public void updateCounters(long value){
		Stats.nbPrescriptions = value;
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
