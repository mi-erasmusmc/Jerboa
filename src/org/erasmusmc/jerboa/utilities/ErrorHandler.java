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
 * $Rev:: 4804              $:  Revision of last commit                                   *
 * $Author:: root           $:  Author of last commit                                     *
 * $Date:: 					$:  Date and time (CET) of last commit						  *
 ******************************************************************************************/

package org.erasmusmc.jerboa.utilities;

import org.erasmusmc.jerboa.Jerboa;
import org.erasmusmc.jerboa.config.FilePaths;
import org.erasmusmc.jerboa.config.Parameters;
import org.erasmusmc.jerboa.gui.JerboaGUI;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Image;
import java.awt.Point;
import java.awt.Toolkit;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;

import javax.swing.BorderFactory;
import javax.swing.JFrame;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTabbedPane;
import javax.swing.JTextArea;
import javax.swing.ScrollPaneConstants;

/**
 * Handles the errors generated during the run of the application with a GUI.
 * It displays the error messages on the screen using a dialog window.
 * Multiple constructors are implemented for each type of error management.
 * Used both for output of errors towards a log file and for visualization through a GUI.
 *
 * @author MG
 *
 */
@SuppressWarnings("serial")
public class ErrorHandler extends JFrame {

	//GUI related
	private JTabbedPane tab;
	private JPanel panel;

	//error related
	private ErrorMessages lines;
	private String fileName;
	private ErrorMessages exceptions;
	private List<String> errorLines;
	private List<String> errorMessages;

	//CONSTRUCTORS
	/**
	 * Simple constructor generating a message dialog holding the error message passed as input.
	 * If the application is running in console mode, a plain text message is displayed.
	 * @param message - the error message to be displayed in the dialog
	 */
	public ErrorHandler(String message){
		if (!Jerboa.inConsoleMode){
			this.setLocationRelativeTo(JerboaGUI.frame);
			this.setMaximumSize(new Dimension(500, 800));
			JOptionPane.showMessageDialog(JerboaGUI.frame, message, "Error", JOptionPane.ERROR_MESSAGE);
		}else{
			System.out.println("ERROR: "+message);
		}
	}

	/**
	 * Constructor used to display the error messages via a GUI. Creates an ErrorHandler object using
	 * the already populated one passed as input.
	 * @param errorHandler - the ErrorHandler object to be displayed via a GUI
	 */
	public ErrorHandler(ErrorHandler errorHandler){
		this.fileName = errorHandler.getFileName();
		this.errorLines = errorHandler.getErrorLines();
		this.errorMessages = errorHandler.getErrorMessages();
		panel = createPanel(fileName, errorLines,errorMessages);
		add(panel);
		URL url = Jerboa.class.getResource(FilePaths.ICON_PATH);
		Image img = Toolkit.getDefaultToolkit().getImage(url);
    	setIconImage(img);
		pack();
		setLocationRelativeTo(JerboaGUI.frame);
		setVisible(true);
	}

	/**
	 * Constructor able to process a two dimensional list containing the error messages of all the input files.
	 * Receives the list of input files and the lists of error messages for the input file and puts them into
	 * a tabbed panel in order to visualize all the errors (per file) found while checking the input files for integrity.
	 * @param errorList - a list containing the lists of errors found for each input file
	 */
	public ErrorHandler(List<List<String>> errorList){
		//check if there were input files
		if (errorList != null && errorList.size() > 0){
			//check if the list of error messages per file is populated
			tab = new JTabbedPane();
			for (int i = 0; i < errorList.size(); i++){
				if (errorList.get(i) != null){
					//retrieve the error list for an input file
					List<String> fileErrors = errorList.get(i);
					if (fileErrors != null && fileErrors.size() > 0){
						//erase previous error content
						errorMessages = new ArrayList<String>();
						errorLines = new ArrayList<String>();

						//loop through the error list
						for (int j = 0; j < fileErrors.size(); j++){
							//separate the error messages from the actual input file content
							String[] row =  fileErrors.get(j).split("--");
							errorMessages.add(row[0]);
							errorLines.add(!row[1].equals("") ? row[1] : " ");
							if (errorLines.size() == Parameters.MAX_ERRORS_INTEGRITY){
								errorLines.add("..to be continued");
								errorMessages.add("..to be continued");
								break;
							}
						}
						//instantiate and populate a tab with all the error messages found for an input file
						tab.addTab(Jerboa.getRunThread().getInputSet().getSelectedFiles().get(i).getName(),createPanel(fileErrors.get(0), errorLines, errorMessages));
					}
				}
			}

			//set GUI properties
			setTitle("Errors in input files");
			setDefaultCloseOperation(DISPOSE_ON_CLOSE);
			//in case here are a lot of errors, limit the size - NOT HAVING ANY EFFECT
			setLayout(new BorderLayout());
			setPreferredSize(new Dimension(600,400));
			add(tab);
			URL url = Jerboa.class.getResource(FilePaths.ICON_PATH);
			Image img = Toolkit.getDefaultToolkit().getImage(url);
			setIconImage(img);
			pack();
			setResizable(true);
			setPosition();
			setVisible(true);
		}
	}

	/**
	 * Creates a GUI component in order to visualize a list of error messages generated during the checking of the input files.
	 * @param filename - the name of the input file that was checked for integrity
	 * @param errorLines - the list with the rows from the actual input file where an error was found
	 * @param errorMessages - the list with error messages that were generated for each row presenting an integrity error
	 * @return - a JPanel with the error messages from one input file
	 */
	public JPanel createPanel(String filename, List<String> errorLines, List<String> errorMessages){

		panel = new JPanel();
		panel.setLayout(new BorderLayout());

		//retrieve the error messages and the lines with errors from the input file
		exceptions = new ErrorMessages(errorMessages,"", Color.BLACK, new Dimension(200,200));
		lines = new ErrorMessages(errorLines, "",Color.BLACK, new Dimension(300,200));
		//add them to the GUI
		panel.add(exceptions,BorderLayout.WEST);
		panel.add(lines,BorderLayout.CENTER);
		//synchronize scrolling
		lines.areaScrollPane.setVerticalScrollBarPolicy(ScrollPaneConstants.VERTICAL_SCROLLBAR_NEVER);
		lines.areaScrollPane.getVerticalScrollBar().setModel(exceptions.areaScrollPane.getVerticalScrollBar().getModel());
		lines.areaScrollPane.getVerticalScrollBar().setValue(lines.areaScrollPane.getVerticalScrollBar().getMinimum());

		return panel;
	}

	/**
	 * Constructor able to process a list containing the error messages of the patient objects.
	 * Receives the list of error messages for the patient objects and puts them into
	 * a tabbed panel in order to visualize all the errors found while checking the patient history for coherence.
	 * @param errorList - a list containing the errors messages found in the patient objects
	 * @param title - the title of the error viewer
	 **/
	public ErrorHandler(List<String> errorList, String title){
		//check if there were input files
		if ((errorList != null && errorList.size() > 0)){
			//check if the list of error messages per file is populated
			tab = new JTabbedPane();
			tab.setTabLayoutPolicy(JTabbedPane.SCROLL_TAB_LAYOUT);
			//instantiate and populate a tab with all the error messages found for an input file

			panel = new JPanel();
			panel.setLayout(new BorderLayout());
			//retrieve the error messages and the lines with errors from the input file
			exceptions = new ErrorMessages(errorList,"Exceptions in patients", Color.BLACK, new Dimension(300,200));
			exceptions.areaScrollPane.setVerticalScrollBarPolicy(ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED);
			//add them to the GUI
			panel.add(exceptions, BorderLayout.CENTER);
			tab.addTab(title, panel);

			//set GUI properties
			setTitle("Error viewer");
			setDefaultCloseOperation(DISPOSE_ON_CLOSE);
			//in case here are a lot of errors, limit the size - NOT HAVING ANY EFFECT
			setLayout(new BorderLayout());
			setPreferredSize(new Dimension(450,400));
			add(tab);
			FileUtilities.putIcon(this);
			pack();
			setResizable(true);
			setPosition();
			setVisible(true);
		}
	}

	//GETTERS AND SETTERS

	/**
	 * Will set the display position of the error viewer based
	 * on the location of the main frame of the application.
	 */
	public void setPosition(){
		 Dimension screenSize = Toolkit.getDefaultToolkit().getScreenSize();
		 if (screenSize.getWidth() - JerboaGUI.frame.getLocation().x < 100)
			 if (JerboaGUI.frame.getLocation().x > 200)
				 this.setLocation(new Point(JerboaGUI.frame.getLocation().x-this.getWidth(),
							JerboaGUI.frame.getLocation().y));
			 else
				 this.setLocationRelativeTo(null);
		 else
			this.setLocation(new Point(JerboaGUI.frame.getLocation().x+JerboaGUI.frame.getWidth(),
					JerboaGUI.frame.getLocation().y));
	}

	public String getFileName() {
		return fileName;
	}

	public void setFileName(String fileName) {
		this.fileName = fileName;
	}

	public List<String> getErrorLines() {
		return errorLines;
	}

	public void setErrorLines(List<String> errorLines) {
		this.errorLines = errorLines;
	}

	public List<String> getErrorMessages() {
		return errorMessages;
	}

	public void setErrorMessages(List<String> errorMessages) {
		this.errorMessages = errorMessages;
	}

}

/**
 * Nested class setting up the error messages of an input
 * file into a text area to be displayed on the API GUI.
 *
 * @author MG
 *
 */
@SuppressWarnings("serial")
class ErrorMessages extends JPanel {

	JTextArea text = new JTextArea();
	JScrollPane areaScrollPane;

	/**
	 * Constructor receiving the list error messages, the title, color and size of
	 * the frame to be created and populated for the user to check the raised errors.
	 * @param errorMessages - the error messages to be displayed
	 * @param title - the title of the window
	 * @param color - the color of the text
	 * @param size - the size of the window to be displayed
	 */
	public ErrorMessages(List<String> errorMessages, String title, Color color, Dimension size) {
		//set the text area properties
		text.setEditable(false);
		setLayout(new BorderLayout());
		setSize(size);
		text.setForeground(color);
		//populate the text area
		for (int i = 1; i < (errorMessages.size() > Parameters.MAX_ERRORS_INTEGRITY ?
				Parameters.MAX_ERRORS_INTEGRITY : errorMessages.size()); i ++){
			text.append(errorMessages.get(i)+"\r\n");
		}
		text.setCaretPosition(text.getLineCount() > 1 ? text.getLineCount() : 0); //stops auto scrolling to bottom
		//creating scroll panels for the error messages
		areaScrollPane = new JScrollPane(text);
		areaScrollPane.setPreferredSize(size);
		areaScrollPane.setBorder(BorderFactory.createTitledBorder(errorMessages.get(0)));
		add(areaScrollPane, BorderLayout.CENTER);
	}

}

