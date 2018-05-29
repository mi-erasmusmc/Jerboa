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
 * $Rev:: 4429               $:  Revision of last commit                                  *
 * $Author:: root            $:  Author of last commit                                    *
 * $Date:: 2013-09-10 14:16:#$:  Date and time (CET) of last commit						  *
 ******************************************************************************************/

package org.erasmusmc.jerboa.config;

/**
 * This class contains all the paths towards resources, file input and output that are used across the application core.
 * The modification of these values should be highly motivated and carefully accomplished in order to maintain
 * the overall functionality of the API. Comments should reflect the motivation behind any modification that
 * occurs upon the constants/parameters defined in this class.
 *
 * @author MG
 *
 */
public class FilePaths {

	//GUI resources
	public static final String ICON_PATH = "/org/erasmusmc/jerboa/gui/resources/icon48.gif";
	public static final String LOGO_PATH = "/org/erasmusmc/jerboa/gui/resources/logo.gif";
	public static final String LICENSE_LOGO_PATH = "/org/erasmusmc/jerboa/gui/resources/license_logo.png";
	public static final String SEARCH_ICON_PATH = "/org/erasmusmc/jerboa/gui/resources/search32.png";
	public static final String NEXT_ICON_PATH = "/org/erasmusmc/jerboa/gui/resources/next_button.png";
	public static final String PREVIOUS_ICON_PATH = "/org/erasmusmc/jerboa/gui/resources/prev_button.png";
	public static final String RESOURCE_PATH = "/org/erasmusmc/jerboa/gui/resources/";
	public static final String LICENSE_PATH = "/org/erasmusmc/jerboa/gui/resources/GNU General Public License Version 2.txt";

	//current run
	public static String WORKING_PATH = PropertiesManager.getLastWorkSpace();
	public static String DATA_PATH = WORKING_PATH+"/"+"jerboa"+"/";

	public static String RUN_FOLDER = PropertiesManager.getRunFolder();
	public static String DAILY_DATA_PATH = DATA_PATH+RUN_FOLDER;
	public static String LOG_PATH = DAILY_DATA_PATH+"logs/";
	public static String SPLIT_PATH = DAILY_DATA_PATH+"data/split/";
	public static String PATIENTS_PATH = DAILY_DATA_PATH+"data/patients/";
	public static String LOOKUPS_PATH = DAILY_DATA_PATH+"data/lookups/";
	public static String WORKFLOW_PATH = DAILY_DATA_PATH+"results/";
	public static String ERRORS_PATH = DAILY_DATA_PATH+"data/errors/";
	public static String INTERMEDIATE_PATH = DAILY_DATA_PATH+"data/intermediate/";


	//OUTPUT

	//input splitting for sorting
	public static final String FILE_PATIENTS = "patients.csv";
	public static final String FILE_EVENTS = "events.csv";
	public static final String FILE_PRESCRIPTIONS = "prescriptions.csv";
	public static final String FILE_MEASUREMENTS = "measurements.csv";
	public static final String FILE_LOST = "homeless.csv";
	public static final String[] SUBSET_FILES = {FILE_PATIENTS, FILE_EVENTS, FILE_PRESCRIPTIONS, FILE_MEASUREMENTS};

	//error logs
	public static final String ERROR_LOG_PATIENTS = "patient_errors.txt";
	public static final String ERROR_LOG_EVENTS = "event_errors.txt";
	public static final String ERROR_LOG_PRESCRIPTIONS = "prescription_errors.txt";
	public static final String ERROR_LOG_MEASUREMENTS = "measurement_errors.txt";
	public static final String ERROR_LOG_PATIENT_OBJECTS = ERRORS_PATH+"patient_object_errors.txt";
	public static String UNASSIGNED_RECORDS = ERRORS_PATH+"unassigned_records.txt";

	//look-up tables
	public static final String FILE_EVENT_TYPES = "event_types.txt";
	public static final String FILE_PRESCRIPTION_TYPES = "prescription_types.txt";
	public static final String FILE_MEASUREMENT_TYPES = "measurement_types.txt";
	public static final String FILE_MEASUREMENT_VALUES = "measurement_values.txt";

	//look-up paths
	public static String EVENT_TYPES_PATH = LOOKUPS_PATH+FILE_EVENT_TYPES;
	public static String PRESCRIPTION_TYPES_PATH = LOOKUPS_PATH+FILE_PRESCRIPTION_TYPES;
	public static String MEASUREMENT_TYPES_PATH = LOOKUPS_PATH+FILE_MEASUREMENT_TYPES;
	public static String MEASUREMENT_VALUES_PATH = LOOKUPS_PATH+FILE_MEASUREMENT_VALUES;

	//encryption keys
	public static final String ENCRYPTION_EU_ADR = "/org/erasmusmc/jerboa/encryption/JerboaPublicEU-ADR.key";
	public static final String ENCRYPTION_PUBLIC = "/org/erasmusmc/jerboa/encryption/Public.key";

	//modules and modifiers related
	public static final String MODULES_PACKAGE = "org.erasmusmc.jerboa.modules.";
	public static final String MODIFIERS_PACKAGE = "org.erasmusmc.jerboa.modifiers.";

	//properties file
	public static final String PROPERTIES_FILE = "./jerboa.properties";


	/**
	 * Update the output paths of the current run.
	 * @param workingFolder - the new working folder
	 * @param runFolder - the index of the new run
	 */
	public static void updatePaths(String workingFolder, String runFolder){

		WORKING_PATH = workingFolder;
		DATA_PATH = workingFolder+"/"+"jerboa"+"/";
		RUN_FOLDER = runFolder;
		DAILY_DATA_PATH = DATA_PATH+RUN_FOLDER;
		LOG_PATH = DAILY_DATA_PATH+"logs/";
		LOOKUPS_PATH = DAILY_DATA_PATH+"data/lookups/";
		SPLIT_PATH = DAILY_DATA_PATH+"data/split/";
		PATIENTS_PATH = DAILY_DATA_PATH+"data/patients/";
		WORKFLOW_PATH = DAILY_DATA_PATH+"results/";
		ERRORS_PATH = DAILY_DATA_PATH+"data/errors/";
		INTERMEDIATE_PATH = DAILY_DATA_PATH+"data/intermediate/";
		UNASSIGNED_RECORDS = ERRORS_PATH+"unassigned_records.txt";

		//look-up tables
		EVENT_TYPES_PATH = LOOKUPS_PATH+FILE_EVENT_TYPES;
		PRESCRIPTION_TYPES_PATH = LOOKUPS_PATH+FILE_PRESCRIPTION_TYPES;
		MEASUREMENT_TYPES_PATH = LOOKUPS_PATH+FILE_MEASUREMENT_TYPES;
		MEASUREMENT_VALUES_PATH = LOOKUPS_PATH+FILE_MEASUREMENT_VALUES;
	}

}
