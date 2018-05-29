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
 * Author: Peter Rijnbeek (PR) - department of Medical Informatics						  *
 * 																						  *
 * $Rev::            	    $:  Revision of last commit                                	  *
 * $Author::				$:  Author of last commit                                     *
 * $Date:: 					$:  Date and time (CET) of last commit						  *
 ******************************************************************************************/
package org.erasmusmc.jerboa.utilities;

import java.util.ArrayList;
import java.util.concurrent.TimeUnit;

/**
 * This class will contain all methods used for timers and system time manipulation.
 *
 * @author MM {@literal &} MG
 *
 */
public class TimeUtilities {

	@SuppressWarnings("unused")
	private static final long HOUR = TimeUnit.HOURS.toMillis(1);
	public static String TIME_STAMP = getTimeStamp();

	/**
	 * Will calculate the current time from a number of milliseconds
	 * and will return time as a string that is readable in days, hours, minutes and seconds.
	 * @param msec - the number of milliseconds as returned by the system time.
	 * @return - a human readable string representing the current time
	 */
	public static String readableTime(long msec){
		int seconds = (int)(msec / 1000) % 60 ;
		int minutes = (int)((msec / (1000*60)) % 60);
		int hours = (int)((msec / (1000*60*60)) % 24);
		int days = (int)((msec / (1000*60*60*24)) % 365);

		ArrayList<String> timeArray = new ArrayList<String>();

		if(days > 0)
		    timeArray.add(String.valueOf(days) + "d");

		if(hours>0)
		    timeArray.add(String.valueOf(hours) + "h");

		if(minutes>0)
		    timeArray.add(String.valueOf(minutes) + "min");

		if(seconds>0)
		    timeArray.add(String.valueOf(seconds) + "sec");

		String time = "";
		for (int i = 0; i < timeArray.size(); i++)
		{
		    time = time + timeArray.get(i);
		    if (i != timeArray.size() - 1)
		        time = time + ", ";
		}

		if (time == "")
		  time = "0 sec";
		return time;
	}

	/**
	 * Converts a number of seconds into hours, minutes and seconds.
	 * @param nbSeconds - the number of seconds to be converted
	 * @return - the string representation of seconds into hours, minutes and seconds.
	 */
	public static String convertSecondsToHHMMSS(long nbSeconds){
		int hours = (int)nbSeconds/3600;
		nbSeconds = nbSeconds % 3600;
		int minutes = (int)nbSeconds / 60;
	    nbSeconds  = (nbSeconds % 60);
	    return (hours < 10 ? "0"+hours : hours)+":"+
	   	   (minutes < 10 ? "0"+minutes : minutes)+":"+
	   	   (nbSeconds < 10 ? "0"+nbSeconds : nbSeconds);
	}

	/**
	 * Retrieves the current time as a time stamp.
	 * @return - a time stamp
	 */
	public static String getTimeStamp(){
		return DateUtilities.getCurrentDateAndTimeWithNoColumn();
	}

	/**
	 * Updates the time stamp with the current time.
	 */
	public static void refreshTimeStamp(){
		TimeUtilities.TIME_STAMP = DateUtilities.getCurrentDateAndTimeWithNoColumn();
	}
}
