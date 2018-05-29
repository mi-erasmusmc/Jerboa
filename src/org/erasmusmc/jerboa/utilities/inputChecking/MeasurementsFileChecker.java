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

import org.erasmusmc.jerboa.dataClasses.Measurement;
import org.erasmusmc.jerboa.engine.InputFile;
import org.erasmusmc.jerboa.utilities.DateUtilities;
import org.erasmusmc.jerboa.utilities.stats.Stats;

/**
 * This class contains all methods used to check a measurement input file.
 * The input data is checked for integrity and coherence, record by record.
 * If errors found, an error log is output. In case the error number is
 * higher than the set limit ( @see Parameters ); the rest of the files is skipped.
 * @author MG
 *
 */
public class MeasurementsFileChecker extends InputFileChecker {

	// Counter for empty values
	int emptyValuesCount = 0;

	/**
	 * Constructor receiving an input file to be checked.
	 * @param file - the input file
	 */
	public MeasurementsFileChecker(InputFile file) {
		super(file);
	}

	@Override
	public boolean attributesOK(String[] columns, long line) {

		byte dateFormat = file.getDateFormat();

		String patientID = columns[dataOrder[Measurement.COLUMN_PATIENT_ID]].trim();
		String type = columns[dataOrder[Measurement.COLUMN_TYPE]].trim();
		int[] dateComponents = DateUtilities.splitDate(columns[dataOrder[Measurement.COLUMN_DATE]].trim(), dateFormat);
		Integer measurementDate = DateUtilities.dateToDays(dateComponents);
		String value = columns[dataOrder[Measurement.COLUMN_VALUE]].trim();

		if (patientID == null || patientID.equals("")) {
			errorMessages.add("["+line+"]   No patient ID -- "+Arrays.toString(columns));
			return false;
		}

		//check type
		if (type == null || type.equals("")) {
			errorMessages.add("["+line+"]   Illegal code -- "+Arrays.toString(columns));
			return false;
		}
		else {
			//check numeric values
			if (!numericValuesOK(type, columns, line)) {
				return false;
			}
		}

		//check measurement date
		if (DateUtilities.isDateCoherent(dateComponents)) {
			if (measurementDate == null) {
				errorMessages.add("["+line+"]   Illegal date -- "+Arrays.toString(columns));
				return false;
			}
		}else{
			errorMessages.add("["+line+"]   Invalid date -- "+Arrays.toString(columns));
			return false;
		}

		//check value
		if (value == null || value.equals("")) {
			errorMessages.add("["+line+"]   Illegal value -- "+Arrays.toString(columns));
			emptyValuesCount++;
			return false;
		}

		return true;
	}

	@Override
	public void resetCounters() {
		Stats.nbMeasurements = 0;
		emptyValuesCount = 0;
	}

	@Override
	public void updateCounters(long value) {
		Stats.nbMeasurements = value;
		Stats.nbRecords += value;
	}

	//GETTERS
	public List<String> getErrorMessages() {
		return errorMessages;
	}

	public InputFile getInputFile() {
		return file;
	}

	public int getEmptyValuesCount() {
		return emptyValuesCount;
	}

}
