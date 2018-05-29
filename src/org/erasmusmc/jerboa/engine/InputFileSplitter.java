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
 * $Rev:: 4585              $:  Revision of last commit                                   *
 * $Author:: root           $:  Author of last commit                                     *
 * $Date:: 					$:  Date and time (CET) of last commit						  *
 ******************************************************************************************/

package org.erasmusmc.jerboa.engine;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import org.apache.commons.io.FilenameUtils;
import org.apache.commons.lang3.text.StrBuilder;
import org.apache.commons.lang3.text.StrTokenizer;
import org.erasmusmc.jerboa.Jerboa;
import org.erasmusmc.jerboa.utilities.FileUtilities;
import org.erasmusmc.jerboa.utilities.InputFileUtilities;
import org.erasmusmc.jerboa.utilities.Logging;
import org.erasmusmc.jerboa.utilities.Progress;

import org.erasmusmc.jerboa.config.DataDefinition;
import org.erasmusmc.jerboa.config.FilePaths;
import org.erasmusmc.jerboa.config.Parameters;

/**
 * This class contains all the necessary methods to perform the splitting of the input data in smaller subsets
 * in order to ease manipulation and improve performance. It is used only if the input files are not sorted.
 * The number of subsets is determined by the total size of the input files used in the processing.
 * It creates a folder structure which will hold records from the initial data grouped together based on a specified criteria.
 * The data that is fed to the subsets is subsequently compressed based on look-up tables created during input checking.
 *
 * @author MG
 *
 */
public class InputFileSplitter {

	//the names of the folders used to create the splitting structure
	public List<Integer> subsetNames; 				  	// the names of the subsets in which the input data is to be split
	public HashMap<String, Integer> patientSubsetIDs; 	// will contain all existing subsets present in the patient input files

	private int nbSubsets;	 						 	//used to determine in how many subsets the input files are to be split (based on their size)
	private double chunkSize;							// the maximum size of a subset (double to avoid automatic cast to int in division)

	//the input file set
	private InputFileSet inputSet;						// the set of input files
	private InputFile inputFile;						// the input file that is currently processed

	//output buffer
	private BufferedWriter out; 						// the writer used to output data to file

	//buckets for the data
	private List<StrBuilder> subsetData;      			// the list of buffers that will contain data from the input file

	private int sizeOfsubsetData;						// keeps track of the total size that the subsetData list has

	//progress
	private Progress progress;

	/**
	 * Constructor taking as input the set of input files used for processing.
	 * Based on the total size of the input files, the number of subsets is determined and
	 * the necessary folder structure is created.
	 * @param inputSet - the set of input files used in the processing
	 */
	public InputFileSplitter(InputFileSet inputSet){

		//determine number of subsets in which input data is to be split
		this.inputSet = inputSet;

		this.chunkSize = Jerboa.getScriptParser().getSplitChunkSize() == -1 ?
				Parameters.MAX_SPLIT_SIZE : Jerboa.getScriptParser().getSplitChunkSize();

		this.nbSubsets = setSplitNumber(inputSet.getSelectedFiles());

		//create folder to hold the splitting structure
		File file = new File(FilePaths.SPLIT_PATH);
		file.mkdir();
		//initialize structure creation
		try{
			createFolders();
		}catch (IOException e){
			Logging.add("Error while creating the sorting folder structure", Logging.ERROR);
		}catch (NullPointerException ne){
			Logging.add("Unable to retrieve folder names for input sorting", Logging.ERROR);
		}

		this.sizeOfsubsetData = 0;

		progress = new Progress();
	}

	/**
	 * Sets the number of subsets in which the input data is to be split.
	 * It makes use of the total sum of the input file sizes and the maximum
	 * size allowed at one point in time in memory during splitting.
	 * @param files - the set of input files that the API is using
	 * @return - the number of subsets in which the data will be split
	 */
	public int setSplitNumber(List<InputFile> files){
		long totalSize = InputFileUtilities.getInputFileSetSize(files) / FileUtilities.ONE_MB;
		int nbSubsets = (int)Math.ceil(totalSize / chunkSize);
		nbSubsets = nbSubsets == 0 ? 1 : nbSubsets;
		return nbSubsets;
	}

	/**
	 * Feeds an input line from a file to the right subset.
	 * @param subset - the subset in which the data from line is assigned to
	 * @param line - the actual data from a line in the patients file
	 */
	public void addToSubset(int subset, String line){
		if (line != null){
			//add line
			if (subsetData.get(subset) == null)
				subsetData.set(subset, new StrBuilder());
			subsetData.get(subset).appendln(line);
			sizeOfsubsetData += line.length(); //a char is encoded on 2 bytes

			//check if maximum allowed memory for this list is reached
			if ((sizeOfsubsetData / FileUtilities.ONE_MB) >= chunkSize){
				flushAll();
				sizeOfsubsetData = 0;
			}
		}
	}

	/**
	 * Computes a hash value from the patient ID in order to divide the input files into subsets.
	 * This is used when the input files are not sorted. The number of subsets is predetermined based on
	 * the total size of the input files.
	 * @param ID - the patient identifier that is to be hashed
	 * @return - the hash value for this patient ID.
	 */
	public int subset(String ID){
		int hash = 0;
		for (int i = 0; i < ID.length(); i ++){
			int bits = hash & 0xf8000000; 	    // extract high-order 5 bits from hash (0xf8000000 is the hexadecimal representation)
			hash = hash << 5;                   // shift hash left by 5 bits
			hash = hash ^ (bits >> 27);         // move the most significant 5 bits to the low-order
			hash = hash ^ ID.charAt(i);         // XOR hash and char i
		}

		return Math.abs(hash % nbSubsets);
	}

	/**
	 * Runs through inputFile and assigns each record line to a designated subset
	 * based on a hash value obtained from the patient ID.
	 * @param inputFile - the input file to be split into subsets
	 * @throws IOException - if the file cannot be read
	 */
	public void split(InputFile inputFile) throws IOException{
		if (inputFile != null && !inputFile.isEmpty()){
			this.inputFile = inputFile;
			Logging.add("\t\tSorting "+FilenameUtils.getName(inputFile.getName())+" file");
			//initialize progress
			String fileName = FilePaths.SUBSET_FILES[inputFile.getType()];
			progress = new Progress();
			progress.init(inputFile.getSize(), "Sorting the "+FilenameUtils.getName(fileName)+" file");
			int eol = inputFile.getEol().length();
			//initialize hash for input file subset
			if (inputFile.getType() == DataDefinition.PATIENTS_FILE)
				patientSubsetIDs = new HashMap<String, Integer>();
			//initialize buffers for data
			subsetData = new ArrayList<StrBuilder>();
			for (int i = 0; i< subsetNames.size(); i++)
				subsetData.add(i, new StrBuilder());
			//open input file
			try{
				BufferedReader br = FileUtilities.openFile(inputFile.getName());
				StrTokenizer strLine = new StrTokenizer(br.readLine(), inputFile.getDelimiter()); //header
				strLine.setIgnoreEmptyTokens(false);
				progress.update(strLine.getContent().length()+eol);
				String[] columns = null;

				//go through line by line
				strLine.reset(br.readLine());
				while (strLine.getContent() != null){
					progress.update(strLine.getContent().length()+eol);
					columns = strLine.getTokenArray();
					//add line to right buffer
					addToSubset(subset(columns[inputFile.getPatientIDIndex()]), strLine.getContent());
					//keep track of the input subset (if present in the input file)
					if (inputFile.getType() == DataDefinition.PATIENTS_FILE && inputFile.getSubsetIndex() != -1)
						addElementToCounterHash(columns[inputFile.getSubsetIndex()], patientSubsetIDs);
					progress.show();

					strLine.reset(br.readLine());
				}

				progress.close();
				//free memory
				br.close();
				br = null;
				flushAll();

			}catch(IOException e){
				Logging.add("Error while openning the input file "+inputFile.getName()+".", Logging.ERROR);
				throw new IOException("Error while openning the input file "+inputFile.getName()+".");
			}
		}
	}

	/**
	 * Adds an element to a hash map that is used for counting.
	 * The method checks if the element is present in the hash map and increases its value by one.
	 * If not present in the table; the element is added with a key equal to one.
	 * @param element - the element to be added in the hash map
	 * @param hash - the hash map in which the element should be added
	 */
	public static void addElementToCounterHash(String element, HashMap<String, Integer> hash){
		hash.put(element, hash.get(element) == null ? 1 : hash.get(element)+1);
	}

	/**
	 * Appends data from a subset to the designated folder and clears the buffer.
	 * It can deal with different input file types.
	 * @param subset - the designated subset
	 */
	private void outputSubset(int subset){
		try{
			String outputFile = FilePaths.SPLIT_PATH+(subset)+"/"+FilePaths.SUBSET_FILES[inputFile.getType()];
			out = new BufferedWriter(new FileWriter(outputFile,true));
			out.append(subsetData.get(subsetNames.get(subset)).toString());
			out.flush();
			out.close();
		}catch (Exception e){
			Logging.add("Unable to append subset data to file "+subset+"/"+FilePaths.SUBSET_FILES[inputFile.getType()], Logging.ERROR);
		}
		subsetData.set(subsetNames.get(subset), new StrBuilder());
	}

	/**
	 * Makes sure that all data remaining in the buffers is output to their designated folder.
	 * It checks through all the lists and empties their content in the right subset folder.
	 */
	public void flushAll(){

		if ((subsetNames != null && subsetNames.size() > 0) &&
				(subsetData != null && subsetData.size() > 0)){
			//retrieve folder names
			for (int s: subsetNames){
				if (subsetData.get(subsetNames.get(s)) != null){
					StrBuilder data = subsetData.get(subsetNames.get(s));
					if (data.length() > 0){
						outputSubset(s);
						//clear buffer
						subsetData.set(subsetNames.get(s), new StrBuilder());
					}
				}
			}

			System.gc(); //explicti call to the garbage collector (just in case)
		}
	}

	/**
	 * Creates the folder and file structure for splitting the input files.
	 * It creates a folder for each subset that is to be created from the input data
	 * and populates it with empty files according to the input file types.
	 * It creates also a folder for the eventual data that does not fit any subset.
	 * @throws IOException - in case a folder or a file cannot be created
	 */
	private void createFolders() throws IOException{

		//retrieve split path
		String path = FilePaths.SPLIT_PATH;

		//initialize folder names
		subsetNames = new ArrayList<Integer>();
		for (int i = 0; i < nbSubsets; i++)
			subsetNames.add(i,i);

		//create folder for each entry in the set
		for (int s : subsetNames){
			File file = new File(path+s);
			FileUtilities.forceMkdir(file);

			//create empty patient file and add its reference to the bucket
			if (inputSet.gotPatients && !inputSet.getPatientsFile().isEmpty()){
				file = new File(path+s+"/"+FilePaths.FILE_PATIENTS);
				file.createNewFile();
			}
			//create empty event file and add its reference to the bucket
			if (inputSet.gotEvents && !inputSet.getEventsFile().isEmpty()){
				file = new File(path+s+"/"+FilePaths.FILE_EVENTS);
				file.createNewFile();
			}
			//create empty prescription file and add its reference to the bucket
			if (inputSet.gotPrescriptions && !inputSet.getPrescriptionsFile().isEmpty()){
				file = new File(path+s+"/"+FilePaths.FILE_PRESCRIPTIONS);
				file.createNewFile();
			}
			//create empty measurement file and add its reference to the bucket
			if (inputSet.gotMeasurements && !inputSet.getMeasurementsFile().isEmpty()){
				file = new File(path+s+"/"+FilePaths.FILE_MEASUREMENTS);
				file.createNewFile();
			}
		}
	}

	/**
	 * Deletes the folder and file structure created during the input file splitting.
	 * Makes sure that for each run there is a new and shiny folder structure created.
	 * @return - true if no IOException was raised during the structure deletion; false otherwise
	 */
	public boolean deleteTempData(){
		if (subsetNames != null && subsetNames.size() > 1){
			try{
				//retrieve folder names
				String path = FilePaths.SPLIT_PATH;
				//loop through folders
				for (int s: subsetNames){
					//and recursively delete their content
					FileUtilities.delete (new File(path+s));
				}
				//remove the lost data folder
				FileUtilities.delete (new File(path+"lost"));
				return true;
			}catch (IOException e){
				Logging.add("Unable to delete temporary files", Logging.ERROR);
				return false;
			}
		}else{
			return true;
		}
	}

	/**
	 * Returns the value of the maximum size ratio between the
	 * patients file and any of the other input files.
	 * @return - the value of the maximum file size ratio
	 */
	//NOT USED
	public int getMaxFileSizeRatio(){
		int maxRatio = 1;
		if (inputSet != null && inputSet.getSelectedFiles() != null){
			long patientFileSize = inputSet.getPatientsFile().getSize();
			if (patientFileSize != 0){
				for (InputFile file : inputSet.getSelectedFiles())
					if (inputSet.getProvidedFiles().get(file.getType()))
						maxRatio = maxRatio < file.getSize()/patientFileSize ?
								(int)(file.getSize()/patientFileSize) : maxRatio;
			}
		}

		return maxRatio;
	}

	//GETTERS AND SETTERS
	public List<Integer> getSubsetFolderNames() {
		return subsetNames;
	}

	public void setSubsetFolderNames(List<Integer> folderNames) {
		this.subsetNames = folderNames;
	}

	public InputFile getInputFile() {
		return inputFile;
	}

	public void setInputFile(InputFile inputFile) {
		this.inputFile = inputFile;
	}

}
