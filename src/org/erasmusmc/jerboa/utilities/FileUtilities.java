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

import java.awt.Desktop;
import java.awt.Image;
import java.awt.Toolkit;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.RandomAccessFile;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.attribute.BasicFileAttributes;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;

import javax.swing.JDialog;
import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.filechooser.FileNameExtensionFilter;

import org.apache.commons.codec.digest.DigestUtils;
import org.apache.commons.collections.bidimap.DualHashBidiMap;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.FilenameUtils;
import org.apache.commons.lang3.text.StrBuilder;
import org.erasmusmc.jerboa.Jerboa;
import org.erasmusmc.jerboa.config.FilePaths;
import org.erasmusmc.jerboa.config.PropertiesManager;

/**
 * This class contains a collection of methods that are used in file manipulations
 * such as opening files for reading/writing, loading files based on dialogs,
 * retrieving file details, etc.
 *
 * @author MG
 *
 */
public class FileUtilities extends FileUtils{

	/**
	 * Allows the user to choose a file or folder to open via a JDialog.
	 * @param frame - the frame in which to be displayed
	 * @param path - the path from which the file is to be opened
	 * @param folder - true if the path represents a folder; false if path represents a file
	 * @return - the absolute path of the chosen file.
	 */
	public static String openFileWithDialog(JFrame frame, String path, boolean folder){

		try{
			//initialize components of JDialog
			JFileChooser fileChooser = new JFileChooser(path);
			FileNameExtensionFilter filter = new FileNameExtensionFilter("Patient Object Files", "pof");
			fileChooser.setFileFilter(filter);
			fileChooser.setFileSelectionMode(folder ? JFileChooser.DIRECTORIES_ONLY : JFileChooser.FILES_AND_DIRECTORIES);
			//set the right selection mode
			int returnVal = fileChooser.showDialog(frame, folder ? "Select folder" : "Select file");
			if(returnVal == JFileChooser.APPROVE_OPTION){
				return(fileChooser.getSelectedFile().getAbsolutePath().replaceAll("\\\\", "/"));
			}else{
				return null;
			}
		}catch (Exception e){
			Logging.outputStackTrace(e);
			return null;
		}
	}

	/**
	 * Allows the user to open a file via a JDialog with a specified extension filter.
	 * @param frame - the frame in which the dialog should be displayed
	 * @param path - the path from which the file is to be opened
	 * @param isFolder - true if the path should represent a folder; false otherwise
	 * @param filter - the file extension filter to be applied
	 * @return - the absolute path of the chosen file.
	 */
	public static String openFileWithDialog(JFrame frame, String path, boolean isFolder, FileNameExtensionFilter filter){

		try{
			//initialize components of JDialog
			JFileChooser fileChooser = new JFileChooser(path);
			if (filter != null)
				fileChooser.setFileFilter(filter);
			fileChooser.setFileSelectionMode(isFolder ? JFileChooser.DIRECTORIES_ONLY : JFileChooser.FILES_AND_DIRECTORIES);
			//set the right selection mode
			int returnVal = fileChooser.showDialog(frame, isFolder ? "Select folder" : "Select file or folder");
			if(returnVal == JFileChooser.APPROVE_OPTION){
				return(fileChooser.getSelectedFile().getAbsolutePath().replaceAll("\\\\", "/"));
			}else{
				return null;
			}
		}catch (Exception e){
			Logging.outputStackTrace(e);
			return null;
		}
	}

	/**
	 * Opens a file in order to be read.
	 * @param fileName - the name of the file that is to be opened
	 * @return - a BufferedReader object assigned if the file was successfully opened
	 * @throws IOException - if unable to open the file
	 */
	public static BufferedReader openFile(String fileName) throws IOException{
		return fileName != null && !fileName.equals("") ?
				new BufferedReader(new InputStreamReader(new FileInputStream(fileName))) : null;
	}

	/**
	 * Opens a file in the default text editor of the operating system.
	 * If the file does not have a .txt extension, a copy of it is created
	 * with a .txt extension appended to it and this is opened.
	 * Once the application is closed, the copy is deleted.
	 * @param file - the file to be opened
	 */
	public static void openFileInDefaultTextEditor(String file){
		File f = new File(file);
		boolean renamed = false;

		//check extension if text if not try to rename the file and append .txt
		if (!FilenameUtils.getExtension(file).equals("txt")){
			File newFile = new File(file+".txt");
			try {
				FileUtils.copyFile(f,newFile);
				f = newFile;
				renamed = true;
			} catch (IOException e) {
				Logging.add("Could not copy script file for viewing.", Logging.ERROR);
				Logging.outputStackTrace(e);
			}
		}

		//open in default text editor
		if (f.exists()){
			try{
				f.setReadOnly();
				if (System.getProperty("os.name").toLowerCase().contains("windows")) {
					String cmd = "rundll32 url.dll,FileProtocolHandler " + f.getCanonicalPath();
					Runtime.getRuntime().exec(cmd);
				}
				else {
					Desktop.getDesktop().edit(f);
				}
			}catch(IOException ee){
				Logging.outputStackTrace(ee);
			}
		}

		if (renamed)
			f.deleteOnExit();
	}

	/**
	 * Will open an explorer view with the location at path.
	 * @param path - the location to be opened
	 */
	public static void openFolder(String path){

		if (path != null && !path.equals("")){
			try{
				File file = new File(path);
				if (file.exists())
					Desktop.getDesktop().open(file);
				else
					Desktop.getDesktop().open(new File(FilePaths.DAILY_DATA_PATH));
			}catch (IOException e){
				Logging.add("Cannot open folder "+path, Logging.ERROR);
			}
		}
	}

	/**
	 * Retrieves the size of the file fileName.
	 * It makes use of the length() function from the RandomAccessFile object.
	 * The size of the file is returned as the number of bytes.
	 * @param fileName - the name of the file of interest
	 * @return - the size of the file in bytes; -1 if the file could not be accessed
	 */
	public static long getFileSize(String fileName){
		long size = 0;
		if (fileName != null && !fileName.equals("")){
			try{
				RandomAccessFile raf = new RandomAccessFile(fileName, "r");
				size = raf.length();
				raf.close();
			}catch(IOException e){
				Logging.add("Unable to open file "+fileName, Logging.ERROR);
			}
		}

		return size;
	}

	/**
	 * Retrieves the size in megabytes of the file fileName.
	 * @param fileName - the name of the file for which the size is to be retrieved
	 * @return - the size of the file named fileName in megabytes
	 */
	public static long getFileSizeInMB(String fileName){
		return FileUtilities.getFileSize(fileName) / (1024 * 1024);
	}

	/**
	 * Retrieves the size in kilobytes of the file fileName.
	 * @param fileName - the name of the file for which the size is to be retrieved
	 * @return - the size of fileName in kilobytes
	 */
	public static long getFileSizeInKB(String fileName){
		return FileUtilities.getFileSize(fileName) / 1024;
	}

	/**
	 * Recursively deletes a folder and its sub-folders/files.
	 * @param file - the path to the file or folder to delete
	 * @return - true if the deletion was successful;false otherwise
	 * @throws IOException - if unable to delete a file or folder (e.g., if in use)
	 */
	public static boolean delete(File file) throws IOException {
		if (file.isDirectory())
			for (File f : file.listFiles())
				delete(f);

		if (!file.delete())
			return false;
		else
			return true;
	}

	/**
	 * Retrieves the date of the last modification of this file.
	 * @param file - the path to the file of interest
	 * @return - the date of last modification; null if the file cannot be opened
	 */
	public static String getModificationDate(String file){
		SimpleDateFormat sdf = new SimpleDateFormat("dd/MM/yyyy HH:mm:ss");
		File f = new File(file);
		return (f != null ? sdf.format(f.lastModified()) : null);
	}

	/**
	 * Retrieves the modification attributes of this file.
	 * A string is created with the creation date, last modification
	 * date and last access date of this file and returned.
	 * @param file - the path to the file of interest
	 * @return - the creation, last modification and last access date to this file separated by "|"
	 */
	public static String getFileModificationAttributes(String file){
		Path path = Paths.get(file, new String());
		BasicFileAttributes attr;
		String attributes = null;
		SimpleDateFormat sdf = new SimpleDateFormat("dd/MM/yyyy HH:mm:ss");
		try {
			attr = Files.readAttributes(path, BasicFileAttributes.class);
			attributes = sdf.format(attr.creationTime().toMillis())+" | "+
			sdf.format(attr.lastModifiedTime().toMillis())+" | "+
				sdf.format(attr.lastAccessTime().toMillis());
		} catch (IOException e) {
			Logging.add("Unable to get file attributes "+file);
			Logging.outputStackTrace(e);
		}

		return attributes;
	}

	/**
	 * Returns the latest folder in directory.
	 * @param directory - the directory of interest
	 * @return - the absolute path of the latest created folder
	 */
	public static String getLastModified(String directory) {
		File folder = new File(directory);
	    File[] files = folder.listFiles();
	    if (files.length == 0)
	    	return folder.getAbsolutePath();
	    for (int i = files.length -1; i >= 0; i--)
	    	if (!files[i].isDirectory())
	    		files[i] = null;
	    Arrays.sort(files, new Comparator<File>() {
	        public int compare(File firstFolder, File secondFolder) {
	            return new Long(secondFolder.lastModified()).compareTo(firstFolder.lastModified()); //latest 1st
	        }});
	    return files[0].getAbsolutePath();
	}

	/**
	 * Return the MD5 checksum of file.
	 * @param file - the file to calculate the checksum
	 * @return - the value of the MD5 checksum of file
	 */
    public static String checksumMD5(String file){
        String checksum = null;
        try{
            checksum = DigestUtils.md5Hex(new FileInputStream(file));
        }catch (IOException e) {
            Logging.outputStackTrace(e);
        }

        return checksum;
    }

    /**
	 * Return the CRC32 checksum of file.
	 * @param file - the file to calculate the checksum
	 * @return - the value of the CRC32 checksum of file
	 */
    public static String checksumCRC32(String file){
        String checksum = null;
        try{
            checksum = String.valueOf(FileUtils.checksumCRC32(new File(file)));
        }catch (IOException e) {
            Logging.outputStackTrace(e);
        }

        return checksum;
    }

	/**
	 * Closes the read buffer reader.
	 * @param buffer - the buffer to be closed
	 * @throws IOException - if unable to access file
	 */
	public static void closeBuffer(BufferedReader buffer) throws IOException{
		if (buffer != null){
			buffer.close();
			buffer = null;
		}
	}

	/**
	 * Checks if the end of file is reached with the reader.
	 * @param reader - the reader of the file
	 * @return - true if EOF is reached; false otherwise
	 * @throws IOException - if file cannot be accessed
	 */
	public static boolean isEOF(BufferedReader reader) throws IOException{
		if (reader != null){
			 return reader.readLine() == null;
		}
		return true;
	}

	/**
	 * Retrieves the files present in path.
	 * Recursively runs through sub folders.
	 * @param path - the path to the folder from which the files are to be loaded
	 * @param fileList - the list containing the files found at path
	 * @return - a list with the paths of the files present in the folder
	 */
	public static List<String> getFileList(String path, List<String> fileList){

		if (fileList == null)
			fileList = new ArrayList<String>();

		if (path != null && !path.equals("")){

			//retrieve list of file from root folder
			File file = new File( path.replace("\\","/") );
			File[] files = file.listFiles();

			//recursively run through sub folders or add file to list
			for (File f : files)
				if (f.isDirectory())
					getFileList(f.getAbsolutePath(), fileList);
				else
					fileList.add(f.getAbsolutePath().replace("\\","/"));
		}else{
			Logging.add("Invalid path for retrieving files.", Logging.ERROR);
		}

		//check if there are files
		if (fileList.size() == 0)
			Logging.add("The folder does not contain any files.", Logging.HINT);

		return fileList;

	}

	/**
	 * Writes to log the size in MB of a patient object file (.pof).
	 * It is purely as an indication for the memory consumption.
	 * This files are to be found in the data/patients folder of the current run.
	 */
	public static void logPOFSize(){
		File f = new File(FilePaths.PATIENTS_PATH);
		if (f.isDirectory()){
			long size = FileUtilities.getFileSizeInMB((f.listFiles()[0]).getAbsolutePath());
			Logging.add("The size of a patient object file is: "+
					(size == 0 ? "less than 1" : size)+" MB", Logging.HINT, true);
		}
	}

	/**
	 * Checks in folder if a debug folder was created.
	 * The name of the debug folder is set in the properties manager class.
	 * @param folder - the folder of interest
	 * @return - true if folder contains a debug folder; false otherwise
	 *
	 */
	public static boolean hasDebugFolder(String folder){
		File f = new File(folder);
		File[] files = f.listFiles();
		for (int i = 0; i < files.length; i ++){
			if (files[i].isDirectory() &&
					FilenameUtils.getName(files[i].getName()).equals(PropertiesManager.getDebugFolder())){
				Jerboa.hasDebugFolder = true;
				return true;
			}
		}

		return false;

	}

	/**
	 * This method will prepare the folder structure to reuse the patient objects
	 * created during the previous run. It will remove the newly created folder structure
	 * and the previous results, logs and intermediate files from the debug folder (i.e., 01-01-01).
	 * @return - true if the deletion of the previous folders was performed successfully; false otherwise
	 */
	@SuppressWarnings("resource")
	public static boolean prepareToReusePatients(){

		//remove the current folder structure
		try{
			delete(new File(FilePaths.DAILY_DATA_PATH));
		}catch(IOException e){
			Logging.add("Reusing patients. Unable to delete current folder structure.", Logging.ERROR);
			return false;
		}

		//update paths
		FilePaths.updatePaths(PropertiesManager.getLastWorkSpace(), PropertiesManager.getDebugFolder()+"/");

		//delete previous results, logs and intermediate files
		if (hasDebugFolder(FilePaths.DATA_PATH)){
			try {
				delete(new File(FilePaths.WORKFLOW_PATH));
				delete(new File(FilePaths.INTERMEDIATE_PATH));
				delete(new File(FilePaths.LOG_PATH));

				new Logging();
			} catch (IOException e) {
				Logging.add("Unable to delete previous (intermediate) results from debug folder.", Logging.ERROR);
				return false;
			}
		}else{
			Logging.add("Flagged to reuse patients but no previous debug folder found. Will run normally.", Logging.HINT);
			InputFileUtilities.createFolderStructureDebug();
		}
			return true;
	}

	/**
	 * Sorts the input file by grabbing the header first and removing
	 * it from the input file so only data is to be sorted. The header is output
	 * in the resulting sorted file and the sorted data appended.
	 * If in a Windows environment, the sed and head commands are not
	 * supported without extra libraries, thus the input file has to be opened,
	 * header copied, the rest of the file copied instead of the original file
	 * and afterwards sorted.
	 * @param fileName - the name of the file to be sorted
	 * @return - the path of the sorted file; null if sorting failed
	 */
	public static String sortFile(String fileName){


		//create name of sorted file
		try{
			File input = null;
			input = new File(fileName);
			String path = input.getAbsolutePath().replaceAll("\\\\", "/");
			String sortedFile = input.getName().substring(0, input.getName().length() - 4);
			String tempFile = sortedFile+"_temp.txt";
			sortedFile = sortedFile+"_sorted.txt";
			sortedFile = path.replaceFirst(input.getName(), sortedFile);
			tempFile = path.replaceFirst(input.getName(), tempFile);

			System.out.println("Sorting file "+input.getAbsolutePath());
			long start = System.currentTimeMillis();

			boolean isWindows = System.getProperty("os.name").toLowerCase().contains("win");

			if (isWindows){
				//get header
				BufferedReader br = openFile(input.getAbsolutePath());
				String head = br.readLine();

				//prepare for output
				StringBuilder out = new StringBuilder();

				//read from file
				String line = br.readLine();
				while (line != null){
					out.append(line+"\n");
					line = br.readLine();
				}
				br.close();

				//write to file
				BufferedWriter bw = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(tempFile)));
				bw.write(out.toString());
				bw.flush();
				bw.close();

				//write header to new file
				bw = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(sortedFile)));
				bw.write(head+"\n");
				bw.flush();
				bw.close();

				//not windows
			}else{
				//copy header to output file
				String[] header = {
					(isWindows ? "cmd" : "sh"), (isWindows ? "/c" : "-c"),
					"head -1 "+"\""+path+"\""+" > "+"\""+sortedFile+"\""};
				Process headerProcess = Runtime.getRuntime().exec(header);
				headerProcess.waitFor();
			}

			//sort file by starting at second row
		    String[] sort = {
		           (isWindows ? "cmd" : "sh"), (isWindows ? "/c" : "-c"),
		           (!isWindows ? "sed 1d "+"\""+path+"\""+ " | " : "") +
		           "sort " + (isWindows ? "\""+tempFile+"\" " : "") +">> "+"\""+sortedFile+"\""};
		    Process sortProcess = Runtime.getRuntime().exec(sort);

		    // capture error messages (if any)
		    BufferedReader reader = new BufferedReader(new InputStreamReader(
		        sortProcess.getErrorStream()));

		    //output errors
		    String outputS = reader.readLine();
		    while (outputS != null) {
		        System.err.println(outputS);
		        outputS = reader.readLine();
		    }
		    sortProcess.waitFor();
		    System.out.println(input.getName()+ " file sorted in: "+(System.currentTimeMillis() - start)/1000+" sec.");

		    //delete temporary file
		    delete(new File(tempFile));

		    return sortedFile;

		}catch(IOException io){
			System.out.println("Unable to read the file "+fileName+" Check the file name/path.");
			System.exit(0);
			return null;
		}catch(InterruptedException ie){
			System.out.println("The sorting process was interrupted unexpectedly. Is the file still there?");
			System.exit(0);
			return null;
		}
	}

	/**
	 * Will write the contents of data to the file fileName. It makes use of the
	 * writeStringToFile method in the Apache FileUtils class. It is a wrapper to
	 * avoid the try catch construction.
	 * @param fileName - the name of the output file
	 * @param data - the data to be output
	 * @param append - if the file would be open in append mode or not
	 * @return - true if data was output successfully; false otherwise
	 */
	public static boolean writeStringToFile(String fileName, String data, boolean append){
		if (fileName != null && !fileName.equals("")){
			File file = new File(fileName);
			if (!file.exists())
				new File(FilenameUtils.getFullPath(fileName)).mkdirs();
			try{
				FileUtils.writeStringToFile(file, data, append);
				return true;
			}catch(IOException e){
				Logging.add("Unable to write to file "+fileName, Logging.ERROR);
				return false;
			}
		}
		return false;
	}

	/**
	 * Will write the contents of data to the file fileName and append a new line character to it.
	 * It makes use of the writeStringToFile method in the Apache FileUtils class.
	 * @param fileName - the name of the output file
	 * @param data - the data to be output
	 * @param append - if the file would be open in append mode or not
	 * @return - true if data was output successfully; false otherwise
	 */
	public static boolean writeLineToFile(String fileName, String data, boolean append){
		return writeStringToFile(fileName, data+System.lineSeparator(), append);
	}

	/**
	 * Writes to fileName the content of data. It can be used to output data represented as a list.
	 * The output of string delimiters is optional.
	 * @param fileName - the name of the file towards which the output is directed
	 * @param data - the data to be output
	 * @param removeQuotes - true if the string delimiters should be removed; false otherwise
	 */
	public static void outputData(String fileName, List<String> data, boolean removeQuotes){

		if ((fileName != null && !fileName.equals("")) && (data != null && data.size() > 0)){
			try{
				File file = new File(fileName);
				if (!file.exists())
					new File(FilenameUtils.getFullPath(fileName)).mkdirs();
				BufferedWriter out = new BufferedWriter(new FileWriter(fileName));
				for (int i = 0; i < data.size(); i ++){
					String line = removeQuotes ? data.get(i).replaceAll("\"", "") : data.get(i);
					out.append(i+"\t"+line+System.lineSeparator());
				}
				out.flush();
				out.close();
			}catch (Throwable e){
				Logging.add("Unable to output to "+fileName, Logging.ERROR);
				Logging.outputStackTrace(e);
				Jerboa.stop(true);
			}
		}else{
			Logging.add("Cannot output. The file name is invalid or there is no data.", Logging.ERROR);
			Jerboa.stop(true);
		}
	}

	/**
	 * Add all values in a DualHashBidiMap to an {@literal ArrayList<String>}.
	 * @param data - the data to be added to the list
	 * @return - a list with data added
	 */
	public static List<String> getList(DualHashBidiMap data){
		List<String> list = new ArrayList<String>();
		for (Object item : data.keySet()){
			list.add(data.get(item).toString());
		}

		return list;
	}

	/**
	 * Writes to fileName the content of data. It can be used to output data represented as a list.
	 * The output of string delimiters is optional.
	 * @param fileName - the name of the file towards which the output is directed
	 * @param data - the data to be output
	 * @param removeQuotes - true if the string delimiters should be removed; false otherwise
	 */
	public static void outputData(String fileName, DualHashBidiMap data, boolean removeQuotes){

		if ((fileName != null && !fileName.equals("")) && (data != null && data.size() > 0)){
			try{
				File file = new File(fileName);
				if (!file.exists())
					new File(FilenameUtils.getFullPath(fileName)).mkdirs();
				BufferedWriter out = new BufferedWriter(new FileWriter(fileName));
				for (Object item : data.keySet()){
					//String line = removeQuotes ? data.get(item).replaceAll("\"", "") : item;
					out.append(item+"\t"+data.get(item)+System.lineSeparator());
				}
				out.flush();
				out.close();
			}catch (Exception e){
				Logging.add("Unable to output to "+fileName, Logging.ERROR);
				Jerboa.stop(true);
			}
		}else{
			Logging.add("Cannot output. The file name is invalid or there is no data.", Logging.ERROR);
			Jerboa.stop(true);
		}
	}

	/**
	 * Writes to fileName the content of data. It can be used to output data represented as a list.
	 * The output of string delimiters is optional.
	 * @param fileName - the name of the file towards which the output is directed
	 * @param data - the data to be output
	 * @param removeQuotes - true if the string delimiters should be removed; false otherwise
	 */
	public static void outputDataHashMap(String fileName, DualHashBidiMap data, boolean removeQuotes){

		if ((fileName != null && !fileName.equals("")) && (data != null && data.size() > 0)){
			try{
				File file = new File(fileName);
				if (!file.exists())
					new File(FilenameUtils.getFullPath(fileName)).mkdirs();
				BufferedWriter out = new BufferedWriter(new FileWriter(fileName));
				for (Object item : data.keySet()){
					//String line = removeQuotes ? data.get(item).replaceAll("\"", "") : item;
					out.append(item+"\t"+data.get(item)+System.lineSeparator());
				}
				out.flush();
				out.close();
			}catch (Exception e){
				Logging.add("Unable to output to "+fileName, Logging.ERROR);
				Jerboa.stop(true);
			}
		}else{
			Logging.add("Cannot output. The file name is invalid or there is no data.", Logging.ERROR);
			Jerboa.stop(true);
		}
	}

	/**
	 * Writes to fileName the content of data. It can be used to output data represented as a string builder.
	 * @param fileName - the name of the file towards which the output is directed
	 * @param data - the data to be output
	 */
	public static void outputData(String fileName, StrBuilder data){

		if ((fileName != null && !fileName.equals("")) && (data != null && !data.equals(""))){
			try{
				File file = new File(fileName);
				if (!file.exists())
					new File(FilenameUtils.getFullPath(fileName)).mkdirs();
				BufferedWriter out = new BufferedWriter(new FileWriter(fileName));
				out.append(data.toString());
				out.flush();
				out.close();
			}catch (Throwable e){
				Logging.add("Unable to output to "+fileName, Logging.ERROR);
				Logging.outputStackTrace(e);
				Jerboa.stop(true);
			}
		}else{
			Logging.add("Cannot output. The file name is invalid or there is no data.", Logging.ERROR);
			Jerboa.stop(true);
		}
	}

	/**
	 * Writes to fileName the content of data. It can be used to output data represented as a string builder.
	 * Appending to the output file is optional.
	 * @param fileName - the name of the file towards which the output is directed
	 * @param data - the data to be output
	 * @param append - if true, the data will be appended to the file
	 */
	public static void outputData(String fileName, StrBuilder data, boolean append){

		if ((fileName != null && !fileName.equals("")) && (data != null && !data.equals(""))){
			try{
				File file = new File(fileName);
				if (!file.exists())
					new File(FilenameUtils.getFullPath(fileName)).mkdirs();
				BufferedWriter out = new BufferedWriter(new FileWriter(fileName, append));
				out.append(data.toString());
				out.flush();
				out.close();
			}catch (Throwable e){
				Logging.add("Unable to output to "+fileName, Logging.ERROR);
				Logging.outputStackTrace(e);
				Jerboa.stop(true);
			}
		}else{
			Logging.add("Cannot output. The file name is invalid or there is no data.", Logging.ERROR);
			Jerboa.stop(true);
		}
	}

	/**
	 * Sets an icon on a JFrame or a JDialog.
	 * @param container - the GUI component on which the icon is to be put
	 */
	public static void putIcon(Object container){
		URL url = Jerboa.class.getResource(FilePaths.ICON_PATH);
		Image img = Toolkit.getDefaultToolkit().getImage(url);
		if (container.getClass() == JFrame.class ||
				JFrame.class.isAssignableFrom(container.getClass()))
			((JFrame)container).setIconImage(img);
		else if (container.getClass() == JDialog.class  ||
				JDialog.class.isAssignableFrom(container.getClass()))
			((JDialog)container).setIconImage(img);
		else
			((JFrame)container).setIconImage(img);
	}
}
