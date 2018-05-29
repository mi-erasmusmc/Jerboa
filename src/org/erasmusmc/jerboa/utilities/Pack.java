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
 * $Rev:: 3909              $:  Revision of last commit                                   *
 * $Author:: MG				$:  Author of last commit                                     *
 * $Date:: 					$:  Date and time (CET) of last commit						  *
 ******************************************************************************************/
package org.erasmusmc.jerboa.utilities;

import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;

import org.erasmusmc.jerboa.Jerboa;
import org.erasmusmc.jerboa.config.FilePaths;
import org.erasmusmc.jerboa.config.Parameters;
import org.erasmusmc.jerboa.encryption.Encrypt;
import org.erasmusmc.jerboa.engine.ScriptParser.Pair;
import org.erasmusmc.jerboa.engine.ScriptParser.Settings;
import org.erasmusmc.jerboa.gui.JerboaGUI;
import org.erasmusmc.jerboa.gui.graphs.Graphs;

/**
 * This utility class packs all the results of the current run of the application,
 * including the log file, archives them and optionally encrypts the resulting file.
 * It also provides the unpacking functionality.
 *
 * @author MM {@literal &} MG
 *
 */
public class Pack{

	 // the public encryption key to use
	 private String key;

	//true if the result should be encrypted
	private boolean encrypt;

	//the name of the output file containing all packed results
	private String outputFile;

	//the files to be packed
	private List<String> fileList;

	//flag if the log file of the current run should be included
	private boolean includeErrorLogs = true;

	//success flag
	private boolean packingOK = true;

	/**
	 * Constructor accepting the settings from the script parser.
	 * @param settings - the parsed settings found in the script file
	 */
	public Pack(Settings settings){
		setParameters(settings);
	}

	/**
	 * Sets the parameters for this class partly based on the parsed
	 * settings from the script file.
	 * @param settings - the script settings
	 */
	private void setParameters(Settings settings) {

		if (settings != null && settings.parameters != null &&
				settings.parameters.size() > 0){
			for (Pair<String, String> pair : settings.parameters){
				if (pair != null && pair.name != null && pair.value != null){
					if (pair.name.equals("key"))
						this.key = new String(pair.value);
					if (pair.name.equals("encrypt"))
						this.encrypt = Boolean.parseBoolean(pair.value);
				}
			}
		}

		if (this.encrypt && (this.key == null || this.key.equals("")))
			this.key = "EU-ADR";
	}

	/**
	 * Launches the data packing using the set encryption key.
	 * It will wait first for the other threads to finish.
	 * Note that if other threads are added in the application,
	 * they should be present here also.
	 */
	public void run(){
		Thread pack = new Thread(new Runnable(){
			public void run(){

				if (!Jerboa.inConsoleMode)
					JerboaGUI.busy();

				try {
					//wait for threads to finish
					Jerboa.getWorkFlow().join();
					Graphs.getExportThread().join();
				}catch (InterruptedException e) {
					Logging.outputStackTrace(e);
					packingOK = false;
				}

				//abnormal application stop
				if (Jerboa.errorOccurred){
					//still pack results created so far
					packAfterCrash();
				//successful run
				}else{

					if (Jerboa.getWorkFlow().hasRanModulesSuccessfully()){
						setOutputFileName();

						Logging.add("\n");
						Logging.addWithTimeStamp("Jerboa output "+(encrypt ? "encrypted" : "packed")+" to:");
						Logging.addNewLine();
						Logging.add(outputFile);
						Logging.addNewLine();

						//add module output
						fileList = new ArrayList<String>();
						fileList = FileUtilities.getFileList(FilePaths.WORKFLOW_PATH, fileList);
						//add API logs
						if (includeErrorLogs)
							FileUtilities.getFileList(FilePaths.LOG_PATH, fileList);
						//add used script file
						fileList.add(Jerboa.getScriptParser().getScriptFile());

						packFiles(fileList, outputFile, key);
						if (packingOK){
							Logging.addNewLine();
							Logging.addWithTimeStamp("Jerboa finished successfully");
						}else{
							Logging.add("The packing of results did not finish successfully.", Logging.ERROR);
						}

						if (!Jerboa.inConsoleMode)
							JerboaGUI.done();
					}
				}
			}
		});
		pack.start();
	}

	/**
	 * Will pack the existing results in case the application crashes (no successful run).
	 */
	private void packAfterCrash(){

		setOutputFileName();
		Logging.addNewLine();
		Logging.add("Packing existing results to:");
		Logging.add(outputFile);
		Logging.addNewLine();

		//add existing results, log file and script file
		fileList = new ArrayList<String>();
		fileList = FileUtilities.getFileList(FilePaths.WORKFLOW_PATH, fileList);
		FileUtilities.getFileList(FilePaths.LOG_PATH, fileList);
		fileList.add(Jerboa.getScriptParser().getScriptFile());
		packFiles(fileList, outputFile, key);

		if (!packingOK)
			Logging.add("The packing of existing results did not finish successfully.", Logging.ERROR);
	}

	/**
	 * Packs the files present in inputFiles to the outputFileName making use of publicKeyName.
	 * The zip file can be encrypted or not. Packing the files will make use of a public key file
	 * if encryption is desired.
	 * @param inputFiles - the list of file names to be added to the zip
	 * @param outputFileName - the name of the zip file to be output
	 * @param publicKeyName - the file containing the encryption key
	 */
	public void packFiles(List<String> inputFiles, String outputFileName, String publicKeyName) {

		OutputStream stream = null;

		if (this.encrypt && (publicKeyName != null && !publicKeyName.equals(""))) {
			Encrypt encryptor = new Encrypt();
			stream = encryptor.getEncryptedStream(outputFileName, publicKeyName);
		}else {
			try {
				stream = new FileOutputStream(outputFileName);
			} catch (FileNotFoundException e) {
				stream = null;
				packingOK = false;
			}
		}

		if (stream != null) {
			try {
				Zip zipper = new Zip(this.encrypt);
				packingOK = zipper.zip(inputFiles, stream);
				stream.close();
			} catch (IOException e) {
				Logging.outputStackTrace(e);
				packingOK = false;
			}
		}else{
			Logging.add("Unable to "+(this.encrypt ? "encrypt" : "pack") + " results", Logging.ERROR);
		}
	}

	/**
	 * Unpacks the files present in zipFile to the targetFolder.
	 * The zip file can be encrypted or not. Unpacking the files will make use of a private key file
	 * if decryption is needed.
	 * @param zipFile - the name of the zip file to be unpacked
	 * @param targetFolder - the folder in which the zip file should be unpacked
	 * @param privateKeyFile - the file containing the decryption key
	 * @return - a list of extracted files
	 */
	public static List<String> unpackFiles(String zipFile, String targetFolder, String privateKeyFile) {
		List<String> extractedFiles = null;
		InputStream stream = null;

		//check if decryption is needed
		if (!privateKeyFile.equals("")) {
			Encrypt encryptor = new Encrypt();
			stream = encryptor.getDecryptedStream(zipFile, privateKeyFile);

			//or prepare for regular unpacking
		}else {
			try {
				stream = new FileInputStream(zipFile);
			} catch (FileNotFoundException e) {
				Logging.add("The zip file name is invalid", Logging.ERROR);
				stream = null;
			}
		}

		//perform the actual unpacking
		if (stream != null) {
			try {
				Zip zipper = new Zip();
				extractedFiles = zipper.unzip(stream, targetFolder);
				stream.close();
			} catch (IOException e) {
				Logging.outputStackTrace(e);
			}
		}

		return extractedFiles;
	}

	/**
	 * Creates and sets the name of the output file.
	 */
	private void setOutputFileName() {
		this.outputFile = FilePaths.DAILY_DATA_PATH+Parameters.DATABASE_NAME+"_"+
				(Jerboa.errorOccurred ? "crash_" : "") +
				TimeUtilities.TIME_STAMP+"_Jerboa_"+Parameters.VERSION+
				(!Jerboa.getScriptParser().getScriptTag().equals("") ?
						"_"+Jerboa.getScriptParser().getScriptTag() : "") +
						(this.encrypt ? ".enc" : ".zip");
	}

}
