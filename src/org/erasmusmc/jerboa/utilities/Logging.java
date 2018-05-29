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
 * $Rev:: 4811              $:  Revision of last commit                                   *
 * $Author:: root           $:  Author of last commit                                     *
 * $Date:: 					$:  Date and time (CET) of last commit						  *
 ******************************************************************************************/

package org.erasmusmc.jerboa.utilities;

import org.apache.commons.lang3.text.StrBuilder;
import org.erasmusmc.jerboa.Jerboa;
import org.erasmusmc.jerboa.config.FilePaths;
import org.erasmusmc.jerboa.config.PropertiesManager;

import com.sun.management.OperatingSystemMXBean;

import org.erasmusmc.jerboa.config.Parameters;

import java.awt.Cursor;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintStream;
import java.lang.management.ManagementFactory;

import javax.swing.JTextArea;
import javax.swing.text.BadLocationException;
import javax.swing.text.DefaultCaret;

/**
 * This class ensures the output of the software status, input data details, progress during processing
 * and all user feed-back throughout the work flow.
 * The logs are specific to each run of the API and the progress is to be displayed either
 * in the CLI or in the GUI, depending on the type of the current run.
 * @author MG
 *
 */
public class Logging extends OutputStream{

	private static JTextArea textArea;

	//output related
	public static String outputFile;
	private static boolean outputFailed;

	private static StrBuilder buffer = new StrBuilder();

	//log message types
	public static final String ERROR = "ERROR: ";
	public static final String HINT = "HINT: ";
	public static final String TIMER = "TIMER: ";

	/**
	 * Basic constructor initializing the output and also the GUI
	 * components of the application console if running in GUI mode.
	 */
	public Logging(){
		super();

		if (!Jerboa.inConsoleMode){

			//redirect output towards console
			System.setOut(new PrintStream(this));
			System.setErr(new PrintStream(this));

			textArea = new JTextArea();
			DefaultCaret caret = (DefaultCaret)textArea.getCaret();
			caret.setUpdatePolicy(DefaultCaret.ALWAYS_UPDATE);
			textArea.setName("ConsoleArea");
			textArea.setEditable(false);
			textArea.setLineWrap(true);
			textArea.setWrapStyleWord(true);
			textArea.setCursor(Cursor.getPredefinedCursor(Cursor.DEFAULT_CURSOR));
		}

		prepareOutputLog();
		flushBuffer();

	}

	/**
	 * Appends text to the current log file of the API and/or prints it on the screen.
	 * @param text - the text to be output to the log
	 */
	public static void add(String text){
		outputLog(text+System.lineSeparator());
		println(text);
	}

	/**
	 * Appends text to the current log file of the software and/or prints it
	 * on the screen with a time stamp in front.
	 * @param text - the text to be output to the log
	 */
	public static void addWithTimeStamp(String text){
		String temp = DateUtilities.getCurrentDateAndTime() + "\t"+text;
		outputLog(temp+System.lineSeparator());
		println(temp);
	}

	/**
	 * Adds a new line character in the log.
	 */
	public static void addNewLine(){
		outputLog(System.lineSeparator());
		println("");
	}

	/**
	 * Appends text to the current log file of the software and/or prints it
	 * on the screen without newline but with a leading tab.
	 * @param text - the text to be output to the log
	 */
	public static void append(String text){
		outputLog(text);
		append("\t"+text);
	}

	/**
	 * Appends text to the current log file of the software and/or prints it on the screen.
	 * @param text - the text to be output to the log
	 * @param messageType - one of the above defined types of messages. Present only in the log file.
	 */
	public static void add(String text, String messageType){
		outputLog((messageType != null ? messageType : "")+text+System.lineSeparator());
		println(text);
	}

	/**
	 * Appends text to the current log file of the software and/or prints it on the screen.
	 * @param text - the text to be output to the log
	 * @param onlyToLog - true if the text is intended only for the log file;
	 *  false outputs the text to both log and application console
	 */
	public static void add(String text, boolean onlyToLog){
		outputLog(text+System.lineSeparator());
		if (!onlyToLog)
			println(text);
	}

	/**
	 * Appends text to the current log file of the application and/or prints it on the screen.
	 * @param text - the text to be output to the log
	 * @param messageType - one of the above defined types of messages. Used only in the API log
	 * @param onlyToLog - true if the text is intended only for the application log;
	 *  false outputs the text to both log and console
	 */
	public static void add(String text, String messageType, boolean onlyToLog){
		outputLog(messageType+text+System.lineSeparator());
		if (!onlyToLog)
			println(text);
	}

	/**
	 * Creates the file in which the log of the application will be output and its header exported.
	 */
	public static void prepareOutputLog(){
		new File(FilePaths.LOG_PATH).mkdirs();
		if (!outputFailed){
			try{
				outputFile = FilePaths.LOG_PATH+"Jerboa_"+Parameters.VERSION+"_log_"+
						DateUtilities.getCurrentDateAndTime().replaceAll(":", "-").replaceAll(" ", "_")+".txt";
				BufferedWriter out = new BufferedWriter(new FileWriter(outputFile));
				out.append("JERBOA "+Parameters.VERSION+" API log for "+DateUtilities.getCurrentDateAndTime() );
				out.append(System.lineSeparator()+"--------------------------------------------------"+System.lineSeparator());
				out.append(addSystemConfiguration());
				out.flush();
				out.close();
			}catch (Exception e){
				outputFailed = true;
				prepareOutputLog();
			}
		}else{
			println("Unable to create API log file.");
		}
	}

	/**
	 * Writes to the API log the reason of exception and the stack trace
	 * in order to trace back the origin of the error/exception.
	 * @param e - the raised exception
	 */
	public static void outputStackTrace(Throwable e){
		Jerboa.notIntendedStop();
		if (!e.toString().contains("ThreadDeath")){
			Jerboa.errorOccurred();
			add("------------------------------------------", true);
				boolean first = true;
				for (StackTraceElement elem : e.getStackTrace()){
					if (first){
						add("The application stopped unexpectedly. Check the menu View -> Log for details", ERROR);
						add(e.getLocalizedMessage() + " at "+ elem, Logging.ERROR, true);
						first = false;
					}
					add(elem.toString(),true);
				}
				add("",true);
				add("Details using the toString() method:", true);
				add(e.toString(), true);
				add("",true);
			add(MemoryUtilities.memory(), HINT, true);
			add("------------------------------------------",true);
			add("",true);
		}
	}

	/**
	 * Writes to the application log the reason of exception and the stack trace
	 * in order to trace back the origin of the error/exception.
	 * @param e - the raised exception
	 * @param onlyToLog - true if the stack trace should be output only to the log file
	 * and not also on the console of the application; false otherwise
	 */
	public static void outputStackTrace(Throwable e, boolean onlyToLog){
		Jerboa.errorOccurred();
		add("------------------------------------------", true);
		add("The application stopped unexpectedly. Check the menu View -> Log for details", ERROR);
		add("An error occurred: " + e.getLocalizedMessage(), Logging.ERROR, true);
		for (StackTraceElement elem : e.getStackTrace())
			add(elem.toString(),true);
		add("",true);
		add("Details using the toString() method:", true);
		add(e.toString(), true);
		add("",true);
		add(MemoryUtilities.memory(), HINT, true);
		add("------------------------------------------",true);
		add("",true);
	}

	/**
	 * Outputs the application log for the current run.
	 * @param text - the information to be logged
	 */
	public static void outputLog(String text){
		try{
			//write log content to file
			BufferedWriter out = new BufferedWriter(new FileWriter(outputFile, true));
			out.append(text);
			out.flush();
			out.close();
			//or catch an eventual raised Input Output exception
		}catch (IOException e){
			//check if already tried to output
			if (new File(outputFile).canWrite() && !Jerboa.hasIntendedStop()){
				if (outputFailed){
					//and raise error message
					buffer.appendln(text);
					println("Unable to update API log. Info buffered for later output.");
				}else{
					//or try to create the folder structure for the current run
					outputFailed = true;
					InputFileUtilities.createFolderStructure(new PropertiesManager());
					//and output the log
					outputLog(text);
				}
			}
		}catch (Exception e){
			buffer.appendln(text);
			println("Unable to update API log. Info buffered for later output.");
		}
	}

	/**
	 * Creates a string of characters containing the current system properties to be output in the log.
	 * @return - the system properties as character string
	 */
	private static String addSystemConfiguration(){

		Runtime runTime = Runtime.getRuntime();
		OperatingSystemMXBean mxbean = (OperatingSystemMXBean) ManagementFactory.getOperatingSystemMXBean();
		StrBuilder systemProperties = new StrBuilder();
		systemProperties.appendln("System configuration");
		systemProperties.appendln("-----------------------");
		systemProperties.appendln("Processor type: " + System.getProperty("sun.cpu.isalist"));
		systemProperties.appendln("Available processors: " + runTime.availableProcessors());
		systemProperties.appendln("Maximum available physical memory: " + ((double)(mxbean.getTotalPhysicalMemorySize()/(1024 * 1024)))+" MB");
		systemProperties.appendln("Maximum available memory Java: " + ((double)(runTime.maxMemory()/(1024 * 1024)))+" MB");
	  //systemProperties.appendln("Used memory: " + ((double)(runTime.totalMemory()-runTime.freeMemory())/ (1024 * 1024))+" MB");
		systemProperties.appendln("Java version: " + System.getProperty("java.version"));
		systemProperties.appendln("Java vendor: " + System.getProperty("java.vendor"));
		systemProperties.appendln("OS architecture: " + System.getProperty("os.arch"));
		systemProperties.appendln("OS name: " + System.getProperty("os.name"));
		systemProperties.appendln("OS version: " + System.getProperty("os.version"));
		systemProperties.appendln("OS patch level: " + System.getProperty("sun.os.patch.level"));
		systemProperties.appendln("---------------------------------------------------");
		systemProperties.appendln(" ");

		return systemProperties.toString();
	}

	/**
	 * Will output the contents of the output buffer that were not output due
	 * to lack of initialization or unable to access log file.
	 */
	public static void flushBuffer(){
		outputLog(buffer.toString());
	}

	//GETTERS AND SETTERS
	public void setTextArea(JTextArea textArea){
		Logging.textArea = textArea;
	}

	/**
	 * Retrieves the text from the application console.
	 * @return - the logged info in the application;
	 * null if failed to access the text area containing the text
	 */
	public String getText(){
		try {
			return textArea.getDocument().getText(0, textArea.getDocument().getLength());
		} catch (BadLocationException e) {
			e.printStackTrace();
		}

		return null;
	}


	//DEPENDENCIES
	/**
	 * Prints a line in CLI or the API console
	 * @param string - the line to be printed
	 */
	public static void println(String string){
		if (textArea != null){
			textArea.append(string+System.lineSeparator());
			textArea.repaint();
		} else{
			System.out.println(string);
		}
	}

	//NOT USED
	@Override
	public void write(int b) throws IOException {}

	public static JTextArea getTextArea() {
		return textArea;
	}

}
