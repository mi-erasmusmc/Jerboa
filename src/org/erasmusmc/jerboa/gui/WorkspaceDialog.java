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
package org.erasmusmc.jerboa.gui;

import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JDialog;
import javax.swing.JFileChooser;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.filechooser.FileNameExtensionFilter;

import org.erasmusmc.jerboa.utilities.BoundsPopupMenuListener;
import org.erasmusmc.jerboa.Jerboa;
import org.erasmusmc.jerboa.config.DataDefinition;
import org.erasmusmc.jerboa.config.FilePaths;
import org.erasmusmc.jerboa.config.Parameters;
import org.erasmusmc.jerboa.config.PropertiesManager;
import org.erasmusmc.jerboa.engine.InputFileSet;
import org.erasmusmc.jerboa.engine.Processing;
import org.erasmusmc.jerboa.utilities.ErrorHandler;
import org.erasmusmc.jerboa.utilities.FileUtilities;
import org.erasmusmc.jerboa.utilities.InputFileUtilities;
import org.erasmusmc.jerboa.utilities.Logging;

/**
 * This class contains all the methods used to display a dialog allowing
 * the user to choose a working folder for the current run of the application
 * as well as the script file to be used, in case there are more than one present
 * in the selected working folder.
 *
 * @author MG
 *
 */
public class WorkspaceDialog extends JDialog{

	private static final long serialVersionUID = 1L;

	//the input files
	private InputFileSet inputSet;

	private static String workingFolder;

	//know if the application requires initialization or called from the menu
	private static boolean alreadyRan;

	/**
	 * Basic constructor that will initialize the dialog and display it.
	 */
	public WorkspaceDialog(){
		super(null, ModalityType.APPLICATION_MODAL);
		setTitle("Jerboa "+Parameters.VERSION);
		setAlwaysOnTop(false);

		inputSet = Jerboa.getInputFileSet();

		//see if already instantiated
		if (JerboaGUI.frame != null){
			//hide GUI
			JerboaGUI.frame.setVisible(false);
			JerboaGUI.frame = null;
			alreadyRan = false;
		}

		//launch the GUI for workspace selection
		openWorkSpace();
	}

	/**
	 * Allows the user to chose a workspace when launching the application.
	 * Once a workspace is chosen, the files in the folder are processed to determine their type
	 * and if a script file is found, it is parsed in order to verify version requirements
	 * and needed input files for the run.
	 */
	@SuppressWarnings({ "rawtypes", "unchecked" })
	private void openWorkSpace(){

		//retrieve the current folder
		String currentPath = "";
		//retrieve list of used workspaces
		List<String> usedWorkspaces = new ArrayList<String>();
		String lastWorkspace = null;

		try{
			currentPath = new File(".").getCanonicalPath().replaceAll("\\\\","/");
		}catch(Exception e){
			new ErrorHandler("Unable to retrieve current directory");
		}
		if (PropertiesManager.listProperties != null){
			Collections.addAll(usedWorkspaces, PropertiesManager.getUsedWorkspacesAsArray());
			lastWorkspace = PropertiesManager.getLastWorkSpace();
		}

		//check which of the workspaces still exists on disk
		for (int i = usedWorkspaces.size() -1; i >= 0; i--)
			if (!new File(usedWorkspaces.get(i)).exists())
				usedWorkspaces.remove(i);

		//empty list? add current directory
		if (usedWorkspaces == null || usedWorkspaces.size() == 0){
			usedWorkspaces.add(currentPath);
		}

		//the dialog
	//	final JDialog dialog = new JDialog(JerboaGUI.frame, "Jerboa "+Parameters.VERSION, true);
		FileUtilities.putIcon(this);

		//components
		JPanel bigPanel = new JPanel();
		JPanel instructionsPanel = new JPanel();
		final JPanel selectionPanel = new JPanel();
		JPanel browsePanel = new JPanel();
		final JPanel scriptPanel = new JPanel();
		JPanel optionsPanel = new JPanel();
		JButton browse = new JButton("Browse");
		final JButton okButton = new JButton("OK");
		final JButton cancelButton = new JButton("Cancel");
		Collections.sort(usedWorkspaces);
		final JComboBox<String> usedPaths = new JComboBox(usedWorkspaces.toArray());
		usedPaths.setSelectedItem(lastWorkspace);
		BoundsPopupMenuListener listener =
			       new BoundsPopupMenuListener(true, false);
		usedPaths.addPopupMenuListener(listener);
		usedPaths.setPrototypeDisplayValue("Item");
	    usedPaths.setToolTipText(lastWorkspace);

		//set layouts
		bigPanel.setLayout(new BoxLayout(bigPanel, BoxLayout.Y_AXIS));
		instructionsPanel.setPreferredSize(new Dimension(350,90));
		selectionPanel.setLayout(new BoxLayout(selectionPanel, BoxLayout.Y_AXIS));
		browsePanel.setLayout(new BoxLayout(browsePanel, BoxLayout.X_AXIS));
		browsePanel.setBorder(BorderFactory.createEtchedBorder());
		scriptPanel.setLayout(new BoxLayout(scriptPanel, BoxLayout.X_AXIS));
		scriptPanel.setBorder(BorderFactory.createEtchedBorder());

		//initialize input set with last opened workspace
		inputSet = new InputFileSet((String)usedPaths.getSelectedItem());
		//set the input file presence flag
		JerboaGUI.noInputFiles = inputSet.noInputFiles;
		//set the script panel if multiple scripts in the last workspace
		if ((inputSet != null) && (inputSet.getScriptFiles() != null) && (inputSet.getScriptFiles().size() > 0)) {
			scriptPanel.removeAll();
			scriptPanel.add(new JLabel("Select script: "));
			//multiple script files - do not select any by default
			if (inputSet.getScriptFiles().size() > 1){
				inputSet.getScriptFilesNames().add(0, "Please select file...");
				inputSet.getScriptFiles().add(0, null);
				inputSet.setScriptFile(inputSet.getScriptFiles().get(0));
			}
			final JComboBox scriptFiles =
					new JComboBox(inputSet.getScriptFilesNames().toArray());
			scriptFiles.addPopupMenuListener(listener);
			scriptFiles.setSelectedIndex(0);
			scriptFiles.setPrototypeDisplayValue("Item");
			scriptFiles.setToolTipText(scriptFiles.getSelectedItem().toString());
			scriptPanel.add(scriptFiles);
			scriptPanel.setVisible(true);
			scriptFiles.addActionListener(new ActionListener(){
				public void actionPerformed(ActionEvent arg0) {
					inputSet.setScriptFile(inputSet.getScriptFiles().
						get(scriptFiles.getSelectedIndex()));
					}
				});
		}
		selectionPanel.add(browsePanel);
		selectionPanel.add(scriptPanel);

		usedPaths.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent arg0) {
				workingFolder = (String)(usedPaths.getSelectedItem());
				inputSet = new InputFileSet(workingFolder);
				JerboaGUI.noInputFiles = inputSet.noInputFiles;
				//check for multiple script files
				scriptPanel.setVisible(false);
				scriptPanel.removeAll();
				scriptPanel.add(new JLabel("Select script: "));

				//multiple script files - do not select any by default
				if (inputSet.getScriptFiles().size() > 1){
					inputSet.getScriptFilesNames().add(0, "Please select file...");
					inputSet.getScriptFiles().add(0, null);
					inputSet.setScriptFile(inputSet.getScriptFiles().get(0));
				}
				final JComboBox scriptFiles =
						new JComboBox(inputSet.getScriptFilesNames().toArray());
				BoundsPopupMenuListener listener =
					       new BoundsPopupMenuListener(true, false);
				scriptFiles.addPopupMenuListener(listener);
				scriptFiles.setSelectedIndex(0);
				scriptFiles.setPrototypeDisplayValue("Item");
				scriptFiles.setToolTipText(scriptFiles.getSelectedItem().toString());
				scriptPanel.add(scriptFiles);
				if (inputSet.getScriptFiles() != null) {
					scriptPanel.setVisible(true);
					inputSet.setScriptFile(inputSet.getScriptFiles().
						get(scriptFiles.getSelectedIndex()));
				}
				if (scriptPanel.isVisible()){
					selectionPanel.repaint();
					revalidate();
				}
				scriptFiles.addActionListener(new ActionListener() {
					public void actionPerformed(ActionEvent arg0) {
						inputSet.setScriptFile(inputSet.getScriptFiles().
								get(scriptFiles.getSelectedIndex()));
					}
				});
			}});

		browse.addActionListener(new ActionListener(){
			public void actionPerformed(ActionEvent e) {
				//update the list of used workspaces if not already present
				if (chooseFolder(true)){
					String lastWorkspace = workingFolder;
					String[] workspaces = PropertiesManager.getUsedWorkspacesAsArray();
					boolean isThere = false;
					for (String s : workspaces)
						if (s.equals(lastWorkspace)){
							isThere = true;
							break;
						}
					if (!isThere){
						Jerboa.getPropertiesManager().updateProperty("usedWorkspaces",
								PropertiesManager.getUsedWorkspaces()+","+workingFolder.trim());
					}

					//update properties in the file
					Jerboa.getPropertiesManager().updateProperty("lastWorkspace",workingFolder.trim());

					//set chosen folder as selected
					usedPaths.addItem(workingFolder);
					usedPaths.setSelectedItem(workingFolder);
					okButton.setEnabled(true);
				}
			}});
		browsePanel.add(usedPaths);
		browsePanel.add(Box.createHorizontalGlue());
		browsePanel.add(browse);
		okButton.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent arg0) {

				if (inputSet.getScriptFile() != null){
					dispose();

					//create folder structure
					workingFolder = (String)(usedPaths.getSelectedItem());
					FilePaths.updatePaths(workingFolder, PropertiesManager.getRunFolder());
					Processing.setWorkingFolder(workingFolder);
					InputFileUtilities.createFolderStructure(Jerboa.getPropertiesManager());

					//update last workspace in properties file
					Jerboa.getPropertiesManager().updateProperty("lastWorkspace",workingFolder.trim());

					//redirect output
					new Logging();
				}else{
					JOptionPane.showMessageDialog(JerboaGUI.frame, "Please select a workspace that contains a script file"
							, "Error", JOptionPane.ERROR_MESSAGE);
				}
			}
		});
		cancelButton.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent arg0) {
				dispose();
				System.exit(0);
			}
		});
		addWindowListener(new WindowAdapter() {
			public void windowClosing(WindowEvent e) {
				System.exit(0);
			}
		});

		usedPaths.setPreferredSize(new Dimension(250,30));

		usedPaths.revalidate();
		//			dialog.setResizable(false);
		setPreferredSize(new Dimension(350, 170));
		setMaximumSize(new Dimension(350,170));
		setResizable(false);

		//instructions panel
		JLabel instruction1 = new JLabel("Select the working folder");
		JLabel instruction2 = new JLabel("containing the input files and script (.jsf)");
		instructionsPanel.add(instruction1,BorderLayout.CENTER);
		instructionsPanel.add(instruction2,BorderLayout.CENTER);
		//put components on interface
		optionsPanel.add(okButton);
		optionsPanel.add(cancelButton);
		bigPanel.add(instructionsPanel);
		bigPanel.add(selectionPanel);
		bigPanel.add(optionsPanel);
		add(bigPanel);
		pack();
		getRootPane().setDefaultButton(okButton);
		setLocationRelativeTo(JerboaGUI.frame);
		setVisible(true);
	}

	/**
	 * Lets the user choose the folder designated as the working space, from which to load the input dataset.
	 * @param folder - true if the user wants to open a folder; false if the user wants to open a file
	 * @return - true if successfully opened; false otherwise
	 */
	private boolean chooseFolder(boolean folder){
		try{
			//initialize components of JDialog
			JFileChooser fileChooser = new JFileChooser(workingFolder != null ? workingFolder : ".");
			FileNameExtensionFilter filter = new FileNameExtensionFilter(DataDefinition.FILE_FORMATS, DataDefinition.FILE_EXTENSIONS);
			fileChooser.setFileFilter(filter);
			//set the right selection mode
			fileChooser.setFileSelectionMode(folder ? JFileChooser.DIRECTORIES_ONLY : JFileChooser.FILES_AND_DIRECTORIES);
			int returnVal = fileChooser.showDialog(JerboaGUI.frame, folder ? "Select folder" : "Select file");
			if(returnVal == JFileChooser.APPROVE_OPTION){
				workingFolder = (fileChooser.getSelectedFile().getAbsolutePath().replaceAll("\\\\", "/"));
				//successful selection
				return true;
			}else{
				return false;
			}
		}catch (Exception e){
			Logging.outputStackTrace(e);
			return false;
		}
	}

	//GETTERS AND SETTERS
	public InputFileSet getInputSet() {
		return inputSet;
	}
	public void setInputSet(InputFileSet inputSet) {
		this.inputSet = inputSet;
	}
	public static String getWorkingFolder() {
		return workingFolder;
	}
	public static void setWorkingFolder(String workingFolder) {
		WorkspaceDialog.workingFolder = workingFolder;
	}
	public static boolean hasAlreadyRan() {
		return alreadyRan;
	}
	public static void setAlreadyRan(boolean alreadyRan) {
		WorkspaceDialog.alreadyRan = alreadyRan;
	}

}
