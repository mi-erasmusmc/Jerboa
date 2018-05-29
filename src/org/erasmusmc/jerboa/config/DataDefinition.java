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
 * $Rev:: 4783               $:  Revision of last commit                                  *
 * $Author:: root            $:  Author of last commit                                    *
 * $Date:: 2013-11-12 15:05:#$:  Date and time (CET) of last commit						  *
 ******************************************************************************************/

package org.erasmusmc.jerboa.config;

/**
 * This class contains all the input file types, field separators, headers, name of columns, etc..
 * related to the different input data files that are checked for coherence and used in data compression.
 * The modification of these values should be highly motivated and carefully accomplished in order to maintain
 * the overall functionality of the API. Comments should reflect the motivation behind any modification that
 * occurs upon the constants/parameters defined in this class. As new mandatory columns are added to the input files,
 * they should be present into this class as well.
 *
 * @author MG
 *
 */
public class DataDefinition {

	//file delimiters
	public static final char COMMA_DELIMITER = ',';
	public static final char SEMICOLON_DELIMITER = ';';
	public static final char TAB_DELIMITER = '\t';
	public static final String STRING_DELIMITER = "<example_string>";
	public static final char UNKNOWN_DELIMITER = '@';

	//file extensions
	public static final String FILE_FORMATS = "Text, CSV, SSV and TSV Files";
	public static final String[] FILE_EXTENSIONS =  new String[]{"txt","csv", "ssv", "tsv"};
	public static final String SCRIPT_FILE_EXTENSION = ".jsf";
	public static final String PATIENT_OBJECT_FILE_EXTENSION = ".pof";

	//input file types
	public static final byte NO_FILE = -1;
	public static final byte PATIENTS_FILE = 0;
	public static final byte EVENTS_FILE = 1;
	public static final byte PRESCRIPTIONS_FILE = 2;
	public static final byte MEASUREMENTS_FILE = 3;
	public static final byte SCRIPT_FILE = 9;
	public static final byte[] INPUT_FILE_TYPES =
		{PATIENTS_FILE,EVENTS_FILE, PRESCRIPTIONS_FILE, MEASUREMENTS_FILE};

	//episode types
	public static final String EPISODE_EVENT = "event";
	public static final String EPISODE_PRESCRIPTION = "prescription";
	public static final String EPISODE_MEASUREMENT = "measurement";
	//patients
	public static final String PATIENT = "patient";

	/**
	 * PATIENTS FILE - note that while searching for the column order in the input file, the header is lower cased
	 */
	public static String PATIENTS_ID = "patientid";
	public static String PATIENT_BIRTHDATE = "birthdate";
	public static String PATIENT_GENDER = "gender";
	public static String PATIENT_START_DATE = "startdate";
	public static String PATIENT_END_DATE = "enddate";
	public static final int NB_COLUMNS_PATIENT = 5;
	public static String[] PATIENT_COLUMNS = new String[]
			{PATIENTS_ID, PATIENT_BIRTHDATE, PATIENT_GENDER,PATIENT_START_DATE, PATIENT_END_DATE};

	/**
	 * EVENTS FILE - note that while searching for the column order in the input file, the header is lower cased
	 */
	public static final String EVENT_PATIENT_ID = "patientid";
	public static final String EVENT_TYPE = "eventtype";
	public static final String EVENT_DATE = "date";
	public static final int NB_COLUMNS_EVENT = 3;
	public static final String[] EVENT_COLUMNS = new String[]
			{EVENT_PATIENT_ID, EVENT_TYPE, EVENT_DATE};

	/**
	 *PRESCRIPTIONS FILE - note that while searching for the column order in the input file, the header is lower cased
	 */
	public static final String PRESCRIPTION_PATIENT_ID = "patientid";
	public static final String PRESCRIPTION_ATC_CODE = "atc";
	public static final String PRESCRIPTION_DATE = "date";
	public static final String PRESCRIPTION_DURATION = "duration";
	public static final int NB_COLUMNS_PRESCRIPTION = 4;
	public static final String[] PRESCRIPTION_COLUMNS = new String[]
			{PRESCRIPTION_PATIENT_ID, PRESCRIPTION_ATC_CODE, PRESCRIPTION_DATE, PRESCRIPTION_DURATION};

	/**
	 * MEASUREMENT FILE - note that while searching for the column order in the input file, the header is lower cased
	 */
	public static final String MEASUREMENT_PATIENT_ID = "patientid";
	public static final String MEASUREMENT_TYPE = "measurementtype";
	public static final String MEASUREMENT_DATE = "date";
	public static final String MEASUREMENT_VALUE = "value";
	public static final int NB_COLUMNS_MEASUREMENT = 4;
	public static final String[] MEASUREMENT_COLUMNS = new String[]
			{MEASUREMENT_PATIENT_ID, MEASUREMENT_TYPE, MEASUREMENT_DATE, MEASUREMENT_VALUE};

	public static final String NO_DATA = "NO DATA";

	/**
	 * EXTENDED DATA RELATED - KNOWN AND USED SO FAR
	 */
	public final static String PATIENT_PRACTICE_ID = "practiceId";
	public final static String EVENT_CODE = "code";
	public static final String PRESCRIPTION_DOSE = "dose";
	public static final String PRESCRIPTION_INDICATION = "indication";
	public static final String PRESCRIPTION_FORMULATION = "formulation";
	public static final String PRESCRIPTION_STRENGTH = "strength";
	public static final String PRESCRIPTION_VOLUME = "volume";
	public static final String PRESCRIPTION_PRESCRIBER_ID = "prescriberid";
	public static final String PRESCRIPTION_PRESCRIBER_TYPE = "prescribertype";
	public final static String MEASUREMENT_UNIT = "unit";

	/**
	 * PATIENT OBJECT RELATED
	 */
	//subset id
	public static final String COLUMN_SUBSET_ID = "subsetid";
	public static final String DEFAULT_SUBSET_ID = "1";

	//episode type flags
	public static final short PATIENT_DETAILS_FLAG = 0;
	public static final short EVENT_FLAG = 1;
	public static final short PRESCRIPTION_FLAG = 2;
	public static final short MEASUREMENT_FLAG = 3;

	//patient gender
	public static final byte FEMALE_GENDER = 0;
	public static final byte MALE_GENDER = 1;
	public static final byte UNKNOWN_GENDER = 2;
	public static final byte INVALID_GENDER = -1;

}
