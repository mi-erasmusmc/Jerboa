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
 * $Rev:: 4277              $:  Revision of last commit                                   *
 * $Author:: Peter Rijnbeek $:  Author of last commit                                     *
 * $Date:: 					$:  Date and time (CET) of last commit						  *
 ******************************************************************************************/
package org.erasmusmc.jerboa.utilities;

/**
 * This class contains a colelction of methods that deal with memory
 * checking/consumption/following during the run of the application.
 *
 * @author MG
 *
 */
public class MemoryUtilities {

	private static Runtime runtime = Runtime.getRuntime();
	private static int unit = 1024 * 1024; //MB

	/**
	 * Get info about how much memory is available.
	 * @return - a string with how much memory is used,
	 * allocated and the maximum allowed memory
	 */
	public static String memory(){
		int mb = 1024*1024;
			//get free memory in heap
			long free = runtime.freeMemory();
			free = free != 0 ? free/mb : free;
			//get current heap size
			long total = runtime.totalMemory();
			total = total != 0 ? total/mb : total;
			//get available current heap size
			long used = total - free;
			//get max heap size
			long max = runtime.maxMemory();
			max = max != 0 ? max/mb : max;

			return "  Used / Allocated Memory : "+used+" / "+total+" MB "+" |  Max. Memory : "+max+" MB";
	}

	/**
	 * Get the available memory value.
	 * @return - the available memory in megabytes
	 */
	public static long memoryValue(){
		int mb = 1024*1024;
		Runtime runtime = Runtime.getRuntime();
			return runtime.freeMemory() / mb;
	}

	/**
	 * Check if memory usage changed.
	 * @param currentValue - the current available memory
	 * @return true if currentValue is the same as the available memory; false otherwise
	 */
	public static boolean noMemoryUsage(long currentValue){
		int mb = 1024*1024;
		Runtime runtime = Runtime.getRuntime();
			return currentValue == runtime.freeMemory() / mb;
	}

	//GETTERS
	public static long getFreeMemory(){
		return (runtime.freeMemory() / unit);
	}

	public static long getUsedMemory(){
		return (runtime.totalMemory() - runtime.freeMemory()) / unit;
	}

	public static long getAllocatedMemory(){
		return runtime.totalMemory() / unit;
	}

	public static long getMaxMemory(){
		return runtime.maxMemory() / unit;
	}

}
