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
 * $Rev:: 4797              $:  Revision of last commit                                   *
 * $Author:: root           $:  Author of last commit                                     *
 * $Date:: 					$:  Date and time (CET) of last commit						  *
 ******************************************************************************************/
package org.erasmusmc.jerboa.wrapper;

import java.io.IOException;
import java.lang.management.ManagementFactory;

import org.erasmusmc.jerboa.config.Parameters;

import com.sun.management.OperatingSystemMXBean;

/**
 * This class represents a wrapper for the main JAR of Jerboa.
 * It was put in place in order to be able to pass arguments to the VM
 * without launching the JAR file via the command line.
 * This arguments are related to the invocation of the JVM based on the
 * OS architecture and heap size manipulation.
 *
 * @author MG
 *
 */
public class Wrapper {

	public static void main(String[] args) throws IOException, InterruptedException {

		//check OS architecture and if it is a first run
		boolean bits64 = System.getProperty("os.arch").contains("64");
		//boolean firstRun = !(new File(".", "jerboa.properties")).exists();
		OperatingSystemMXBean mxbean = (OperatingSystemMXBean) ManagementFactory.getOperatingSystemMXBean();
		int physicalMemory = (int) (mxbean.getTotalPhysicalMemorySize()/(1024 * 1024 * 1024));
		int maxMemory = Math.max(4, (int) (physicalMemory * 0.75));

		String[] cmdArray = {
				"java",
				bits64 ? "-d64" : "",
						"-Dfile.encoding=UTF-8",  //force the file encoding here as in the main method is too late
						"-jar",
						"-Xms512m",
						bits64 ? "-Xmx" + maxMemory + "G" : "-Xmx1280m",
						//firstRun ? "-splash:org/erasmusmc/jerboa/gui/resources/splash_screen.png" : "",
						//"-XX:+AggressiveHeap", //forces the allocation of the maximum amount of memory available - removed due to conflicts in latest versions of Java (> 1.7 (51 +))
						"Jerboa_"+Parameters.VERSION+".dat"
		};

		//DEBUG
//		printDebugInfo(cmdArray);

		// create the process
		try{
			Process p = Runtime.getRuntime().exec(cmdArray);
			synchronized (p) {
				p.waitFor();
			}
		} catch (IOException ioe) {
			ioe.printStackTrace();
		}
	}

	/**
	 * Prints to the console the java version and the command formed for launching the jar.
	 * @param command - the command formed in the wrapper
	 */
	public static void printDebugInfo(String[] command){
		System.out.println("Java version: ");
		System.out.println("-----------------");
		System.out.println(System.getProperty("java.version"));
		System.out.println(command.toString());
	}

}
