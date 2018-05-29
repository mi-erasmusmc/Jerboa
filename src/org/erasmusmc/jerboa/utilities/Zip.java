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
 * Author: Mees Mosseveld (MM) - department of Medical Informatics						  *
 * 																						  *
 * $Rev::            	    $:  Revision of last commit                                	  *
 * $Author::				$:  Author of last commit                                     *
 * $Date:: 					$:  Date and time (CET) of last commit						  *
 ******************************************************************************************/
package org.erasmusmc.jerboa.utilities;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

import org.apache.commons.io.FilenameUtils;
import org.erasmusmc.jerboa.Jerboa;
import org.erasmusmc.jerboa.config.FilePaths;

/**
 * Class for zipping and unzipping files in a stream.
 *
 * @author MM {@literal &} MG
 *
 */
public class Zip {

	private Progress progress;
	private Timer timer;

	private boolean encrypted;

	//COSNTRUCTORS
	/**
	 * Basic constructor.
	 */
	public Zip(){
		this.progress = new Progress();
		this.timer = new Timer();
	}

	/**
	 * Constructor receiving a flag if the stream should be encrypted or not
	 * @param encrypted - true if the stream should be encrypted; false otherwise
	 */
	public Zip(boolean encrypted){
		this.progress = new Progress();
		this.timer = new Timer();
		this.encrypted = encrypted;
	}

	/**
	 * Archives (.zip) the given files with a specified base folder to a stream.
	 * @param inputFiles - the list of input files.
	 * @param stream - the output stream where the archive is written.
	 * @return - true if the zipping of the files was performed successfully; false otherwise
	 */
	public boolean zip(List<String> inputFiles, OutputStream stream) {

		if (inputFiles != null && inputFiles.size() > 0){

			progress.init(inputFiles.size(), "Packing files");
			timer.start();

			//will hold all unique zip entries (crashes if duplicate entry)
			Set<String> zipEntries = new TreeSet<String>();

			try {
				Logging.add("Zipping files", Logging.HINT, true);
				ZipOutputStream zipOut = new ZipOutputStream(stream);
				for (String inputFile : inputFiles){
					//keep folder structure inside the zip
					String newFile = inputFile;
					if (newFile.contains(FilePaths.WORKFLOW_PATH))
						newFile = newFile.replace(FilePaths.WORKFLOW_PATH, "");
					else
						newFile = FilenameUtils.getName(newFile);
					String[] pathFolders = newFile.split("/");
					//adding paths with "/" at the end are considered empty folders and making sure there are no duplicates
					for (int i = 0; i < pathFolders.length; i ++)
						zipEntries.add(i < pathFolders.length - 1 ? pathFolders[i] + "/" : newFile);
				}

				//add them to the zip stream one by one
				for (String entry : zipEntries){
					zipOut.putNextEntry(new ZipEntry(entry));
					if (!entry.endsWith("/")){
						for (String inputFile : inputFiles){
							if (inputFile.endsWith(entry)){
								copyStream(new FileInputStream(inputFile), zipOut);
								if (!Jerboa.errorOccurred){
									Logging.add("Compressing " + inputFile, true);
									if (this.encrypted)
										Logging.add("Encrypted " + inputFile);
								}
								progress.update();
							}
						}
					}
				}
				zipOut.close();
			} catch (Exception e){
				Logging.outputStackTrace(e);
				return false;
			}

			timer.stopAndDisplay("Result packing done in:");
			progress.close();
			progress = null;
			return true;

		}else{
			Logging.add("No files to pack", Logging.ERROR);
			return false;
		}
	}

	/**
	 * Unzip files from an archived (zipped) stream to a target folder.
	 * @param stream - the archived stream.
	 * @param targetFolder - the target folder.
	 * @return - a list of the extracted files.
	 */
	public List<String> unzip(InputStream stream, String targetFolder) {
		List<String> extractedFiles = new ArrayList<String>();

		try {

			Logging.add("Unzipping files", Logging.HINT, true);
			ZipInputStream zipInputStream = new ZipInputStream(stream);
			ZipEntry zipEntry = null;
			while ((zipEntry = zipInputStream.getNextEntry()) != null) {
				Logging.add("Extracting " + zipEntry.getName(), Logging.HINT, true);

				if (zipEntry.isDirectory())
					new File(targetFolder+"/"+zipEntry.getName()).mkdirs();
				else {
					new File(targetFolder).mkdirs();
					File file = new File(targetFolder+"/"+zipEntry.getName());
					FileOutputStream fout = new FileOutputStream(file);
					extractedFiles.add(targetFolder+"/"+zipEntry.getName());
					copyStream(zipInputStream, fout);
					zipInputStream.closeEntry();
					fout.close();
					file.setLastModified(zipEntry.getTime());
				}
			}
			zipInputStream.close();
		} catch (Exception e){
			Logging.outputStackTrace(e);
		}

		return extractedFiles;
	}

	/**
	 * Copy the source stream to the destination stream.
	 * @param source - the source stream.
	 * @param dest - the destination stream.
	 */
	public void copyStream(InputStream source, OutputStream dest){
		int bufferSize = 1024;
		int bytes;
		byte[] buffer;
		buffer = new byte[bufferSize];
		try {
			while ((bytes = source.read(buffer)) != -1) {
				if (bytes == 0) {
					bytes = source.read();
					if (bytes < 0)
						break;
					dest.write(bytes);
					dest.flush();
					continue;
				}
				dest.write(buffer, 0, bytes);
				dest.flush();
			}
		} catch (IOException e) {
			Logging.outputStackTrace(e);
		}

	}

}
