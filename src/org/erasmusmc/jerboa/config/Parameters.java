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
 * This class contains all the parameter definitions that act directly on the behavior of the application.
 * It contains definitions of the application version, the valid date interval, buffer sizes, etc.
 * The modification of these values should be highly motivated and carefully accomplished in order to maintain 
 * the overall functionality of the API. Comments should reflect the motivation behind any modification that
 * occurs upon the constants/parameters defined in this class. 
 * 
 * @author MG
 *
 */
public class Parameters {
	
	/*****************
	  VERSION NUMBER
	 ****************/
	public static final String VERSION_NUMBER = "1.0.0.1";
	
	/****************
	  DATABASE NAMES
	 ***************/
	public static final String DATABASES =  "ARS, AUH, CSDRM, DPUK, EDAR, GOTHENBURG, HSD, IPCI, MEMENTO, PEDIANET, PHARMO, SIDIAP, SMG, THIN, UNIMAN, UPF, UTARTU";

	//the interval of legal dates
	public static final String MIN_LEGAL_DATE = "18500101"; // 1st of January 1850
	public static final String MAX_LEGAL_DATE = "21000101"; // 1st of January 2100
	public static final int FIRST_LEAP_YEAR = 1852;

	//error related
	/**
	 * The maximum number of errors found during the primary
	 * input file checking until the application stops.
	 */
	public static final int MAX_ERRORS_INTEGRITY = 1000;
	
	//SPLITTING RELATED
 	/**
 	 * The maximum allowed amount of memory to be allocated to the list
 	 * holding data from the subsets created during input file splitting.
 	 * If this value is reached then the all data in the list are flushed to files.
 	 */
 	public static final int MAX_SPLIT_SIZE = 75; //in MB
 	
 	/**
 	 * The relative maximum size of a patient object file (POF). Until this size is reached
 	 * data is appended to the same file. If the value is passed, a new POF file is created.
 	 */
	public static final int MAX_POF_SIZE = 30; //in MB
	

	//formatting
	public static final String VERSION = "v"+VERSION_NUMBER;
	public static String DATABASE_NAME = "DB";
	
}
