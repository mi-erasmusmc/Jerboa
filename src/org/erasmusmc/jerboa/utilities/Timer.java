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
 * $Rev:: 4275              $:  Revision of last commit                                   *
 * $Author:: Peter Rijnbeek $:  Author of last commit                                     *
 * $Date:: 					$:  Date and time (CET) of last commit						  *
 ******************************************************************************************/

package org.erasmusmc.jerboa.utilities;

/**
 * This class provides the necessary methods in order to follow the execution time
 * of computationally intensive and/or time consuming processes and display it.
 *
 * @author MG
 *
 */
public class Timer {

	//attributes
	private long start;
	private long end;
	private long total = 0;

	//run mode flag
	private boolean inConsoleMode;

	//CONSTRUCTORS
	/**
	 * Basic constructor.
	 */
	public Timer(){
		super();
	}

	/**
	 * Constructor receiving a flag if the application is running in console mode.
	 * @param inConsoleMode - true if the software is launched in CLI; false otherwise
	 */
	public Timer(boolean inConsoleMode){
		super();
		this.inConsoleMode = inConsoleMode;
	}

	/**
	 * Starts the timer.
	 */
	public void start(){
		start = System.currentTimeMillis();
	}

	/**
	 * Stops the timer.
	 */
	public void stop(){
		end = System.currentTimeMillis();
		total += (end - start);
	}

	/**
	 * Stops the timer and displays result.
	 */
	public void stopAndDisplay(){
		stop();
		display();
	}

	/**
	 * Stops the timer and displays result.
	 * @param comment - user defined comment to be displayed next to the execution time
	 */
	public void stopAndDisplay(String comment){
		stop();
		displayTotal(comment);
	}

	/**
	 * Stops the timer and displays result preceded by the current time stamp.
	 * @param comment - user defined comment to be displayed next to the execution time
	 */
	public void stopAndDisplayWithTimeStamp(String comment){
		stop();
		displayTotalWithTimeStamp(comment);
	}

	/**
	 * Display the execution time.
	 */
	public void display(){
		if (start != -1 && end != -1)
			if (!inConsoleMode)
				Logging.add("Execution time : "+TimeUtilities.readableTime(end-start), Logging.TIMER);
			else
				System.out.println("Execution time : "+TimeUtilities.readableTime(end-start));
	}

	/**
	 * Display the execution time with a custom comment.
	 * @param comment - the comment that is to be displayed
	 */
	public void display(String comment){
		if (start != -1 && end != -1)
			if (!inConsoleMode)
				Logging.add(("Execution time : "+TimeUtilities.readableTime(end-start)+
					" for "+comment), Logging.TIMER);
			else
				System.out.println(("Execution time : "+TimeUtilities.readableTime(end-start)+
						" for "+comment));
	}

	/**
	 * Resets the cumulative timer.
	 */
	public void resetTotal(){
		total = 0;
	}

	/**
	 * Display the total execution time since the reset of the total.
	 * @param comment - the comment that is to be displayed
	 */
	public void displayTotal(String comment){
		if (total != -1)
			if (!inConsoleMode){
				Logging.add("\t\t"+comment+" "+TimeUtilities.readableTime(end-start),Logging.TIMER);
			}else{
				System.out.println("\t\t"+comment+" "+TimeUtilities.readableTime(end-start));
			}
	}

	/**
	 * Display the total execution time since the reset of the total,
	 * preceded by the current time as a stamp.
	 * @param comment - the comment that is to be displayed
	 */
	public void displayTotalWithTimeStamp(String comment){
		if (total != -1)
			if (!inConsoleMode){
				Logging.addWithTimeStamp(comment+" "+TimeUtilities.readableTime(end-start));
			}else{
				System.out.println(comment+" "+TimeUtilities.readableTime(end-start));
			}
	}

	public String toString(){
		return TimeUtilities.readableTime(end-start);
	}

	public long getTotal(){
		return end-start;
	}

	public String getTotalReadable(){
		return TimeUtilities.readableTime(end-start);
	}

	public boolean isInConsoleMode() {
		return inConsoleMode;
	}

	public void setInConsoleMode(boolean inConsoleMode) {
		this.inConsoleMode = inConsoleMode;
	}

}
