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
 * $Rev:: 3728              $:  Revision of last commit                                   *
 * $Author:: root           $:  Author of last commit                                     *
 * $Date:: 					$:  Date and time (CET) of last commit						  *
 ******************************************************************************************/

package org.erasmusmc.jerboa.modules.viewers;


import java.util.ArrayList;
import java.util.List;

import org.erasmusmc.jerboa.config.FilePaths;
import org.erasmusmc.jerboa.dataClasses.*;
import org.erasmusmc.jerboa.gui.JerboaGUI;
import org.erasmusmc.jerboa.modules.DataProfiler;
import org.erasmusmc.jerboa.utilities.*;

/**
 * This class contains all the methods used in viewing the profile of the data
 * from a previous run of the application. It accepts a path to a folder which should contain
 * patient object files. It processes all patients and gathers the statistics defined
 * in the DataProfiler class. It will not export the graphs neither tabulated files
 * with the counter values.
 *
 * @see org.erasmusmc.jerboa.modules.DataProfiler
 *
 * @author MG
 */

public class DataViewer{

	/**
	 * Constructor generating a frame which will contain the list of patients from a chosen file.
	 */
	public DataViewer(){

		String folderName = FileUtilities.openFileWithDialog(JerboaGUI.frame, FilePaths.DATA_PATH, true);
		if (folderName != null && !folderName.equals("")){
			new Profile(folderName).start();
		}else{
			new ErrorHandler("Please select a folder containing patient object files");
		}
	}

	/**
	 * Will pass through each patient object file found in fromFolder and process
	 * patients one by one gathering the statistics defined in @see DataProfiler.
	 * Once it is done, the gathered data is displayed under a graph representation.
	 */
	class Profile extends Thread{

		private String folder;

		/**
		 * Basic constructor receiving a path to the folder containing the POF.
		 * @param folder - the folder with patient objects
		 */
		public Profile(String folder){
			this.folder = folder;
		}

		public void run(){

			List<Patient> patients = new ArrayList<Patient>();
			PatientUtilities pu = new PatientUtilities(this.folder);

			if (pu.patientFiles != null && pu.patientFiles.size() > 0){

				//start counter
				Timer timer = new Timer();
				timer.start();
				Progress progress = new Progress();
				progress.init(pu.patientFiles.size(), "Processing patient objects");

				//initialize the profiler
				DataProfiler dp = new DataProfiler();
				dp.setOutputFileNamesInDebug(this.folder);
				dp.init();

				JerboaGUI.busy();

				Logging.add("Processing patient objects");
				//process patients one by one
				for (String file : pu.patientFiles){
					patients = pu.loadPatientsFromFile(file, false,false,false);
					for (Patient p : patients)
						dp.process(p);

					//update progress
					progress.update(1);
					progress.show();
				}

				timer.stopAndDisplay("Done in: ");
				progress.close();

				//display graphs
				dp.deleteOutputFilesInDebug(this.folder);
				dp.displayGraphs();

				JerboaGUI.done();
			}
		}
	}

}