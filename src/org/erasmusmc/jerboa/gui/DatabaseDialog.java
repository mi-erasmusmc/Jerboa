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
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTextField;

import org.erasmusmc.jerboa.Jerboa;
import org.erasmusmc.jerboa.config.Parameters;
import org.erasmusmc.jerboa.config.PropertiesManager;
import org.erasmusmc.jerboa.utilities.FileUtilities;
import org.erasmusmc.jerboa.utilities.Logging;

/**
 * Allows the user to select or add the name of the database to be used in this run.
 *
 * @author MG
 *
 */
public class DatabaseDialog{

	private JDialog databaseDialog;
	private String databaseName;

	/**
	 * Basic constructor calling the dialog initialization and displaying it on the GUI.
	 */
	public DatabaseDialog(){
		if (!JerboaGUI.noInputFiles)
			selectDatabase();
	}

	/**
	 * Displays the actual dialog allowing the user to select
	 * the database from which the input files were created.
	 */
	@SuppressWarnings("rawtypes")
	private void selectDatabase(){

		//see if the dialog is not already displayed
		if (databaseDialog != null && databaseDialog.isVisible()){
			databaseDialog.dispose();
			databaseDialog = null;
		}
		//retrieve list of used workspaces
		List<String> usedDatabases = new ArrayList<String>();
		String lastDatabase = null;
		if (PropertiesManager.listProperties != null){
			Collections.addAll(usedDatabases, PropertiesManager.getUsedDataBasesAsArray());
			lastDatabase = PropertiesManager.getLastDataBase();
		}
		//empty list? add current directory
		if (lastDatabase == null || lastDatabase.equals("")){
			lastDatabase = usedDatabases.get(0);
		}
		//frame components
		databaseDialog = new JDialog(JerboaGUI.frame, "Select database", true);
		JPanel browsePanel = new JPanel();
		JButton add = new JButton("Add");
		//database names
		List<String> scriptDatabases = Jerboa.getScriptParser() != null ?
				Jerboa.getScriptParser().getDatabasenames() : null;
		@SuppressWarnings("unchecked")
		final JComboBox<String> usedNames =	new JComboBox(scriptDatabases != null &&
			scriptDatabases.size() > 0 ? scriptDatabases.toArray() : usedDatabases.toArray());
		usedNames.setSelectedItem(scriptDatabases != null && scriptDatabases.size() > 0 ? (scriptDatabases.contains(lastDatabase) ? lastDatabase : 0) : lastDatabase);

		//disable add button if databases are specified in the script
		add.setEnabled(scriptDatabases == null || scriptDatabases.isEmpty());

		JPanel optionsPanel = new JPanel();
		final JButton okButton = new JButton("OK");
		final JButton cancelButton = new JButton("Cancel");

		//upper panel
		browsePanel.setLayout(new BoxLayout(browsePanel, BoxLayout.X_AXIS));
		browsePanel.setBorder(BorderFactory.createEtchedBorder());

		//action listeners
		usedNames.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent arg0) {
				databaseName = (String)(usedNames.getSelectedItem());
			}});

		add.addActionListener(new ActionListener(){
			public void actionPerformed(ActionEvent e) {
				//update the list of used workspaces if not already present
				addDatabase();
				databaseDialog.dispose();
			}});
		browsePanel.add(usedNames);
		browsePanel.add(Box.createHorizontalGlue());
		if (scriptDatabases == null || scriptDatabases.isEmpty())
			browsePanel.add(add);
		okButton.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent arg0) {
				databaseName = (String)usedNames.getSelectedItem();
				Logging.add("Database name: "+databaseName, Logging.HINT);
				Logging.addNewLine();
				databaseDialog.dispose();
				Jerboa.getPropertiesManager().updateProperty("lastDatabase", databaseName);
				Parameters.DATABASE_NAME = databaseName;
			}
		});
		cancelButton.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent arg0) {
				databaseDialog.dispose();
				Parameters.DATABASE_NAME = null;
			}
		});
		databaseDialog.addWindowListener(new WindowAdapter() {
			public void windowClosing(WindowEvent e) {
				databaseDialog.dispose();
				Parameters.DATABASE_NAME = null;
			}
		});

		usedNames.setPreferredSize(new Dimension(100,30));
		usedNames.revalidate();

		//set frame attributes
		databaseDialog.setLayout(new BorderLayout());
		databaseDialog.setResizable(false);
		FileUtilities.putIcon(databaseDialog);
		databaseDialog.setPreferredSize(new Dimension(300, 100));
		databaseDialog.setMaximumSize(new Dimension(300,100));

		//put components on dialog
		optionsPanel.add(okButton);
		optionsPanel.add(cancelButton);
		JPanel bigPanel = new JPanel();
		bigPanel.add(browsePanel);
		bigPanel.add(optionsPanel);
		bigPanel.setLayout(new BoxLayout(bigPanel, BoxLayout.Y_AXIS));

		databaseDialog.add(bigPanel);
		databaseDialog.pack();
		databaseDialog.getRootPane().setDefaultButton(okButton);
		databaseDialog.setLocationRelativeTo(JerboaGUI.frame);
		databaseDialog.setAlwaysOnTop(true);
		databaseDialog.setVisible(true);
	}

	/**
	 * Allows the user to insert a new database name for the current run.
	 * The newly inserted database will be saved in the properties file and
	 * present at the next run in the selection list.
	 */
	public void addDatabase(){

		//initialize dialog and set properties
		final JDialog addDatabase = new JDialog(JerboaGUI.frame, "Add Database Name", true);
		addDatabase.setLayout(new BorderLayout());
		addDatabase.setPreferredSize(new Dimension(300,80));
		addDatabase.setMaximumSize(new Dimension(300,80));
		addDatabase.setFocusable(true);
		addDatabase.setResizable(false);

		//panels for components
		JPanel mainPanel = new JPanel();
		final JPanel fieldPanel = new JPanel();

		//components
		JLabel label = new JLabel("Type in the database name");
		final JTextField textField = new JTextField();
		JButton okButton = new JButton("OK");

		//action
		okButton.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent arg0) {
				databaseName = textField.getText().toUpperCase().trim();
				if (databaseName != null && !databaseName.equals("")){
					Parameters.DATABASE_NAME = databaseName;
					String lastDatabase = databaseName;
					String[] workspaces = PropertiesManager.getUsedDataBasesAsArray();
					boolean isThere = false;
					for (String s : workspaces)
						if (s.equals(lastDatabase)){
							isThere = true;
							break;
						}
					if (!isThere){
						Jerboa.getPropertiesManager().updateProperty("usedDatabases",
								PropertiesManager.getUsedDataBases()+","+databaseName);
					}
					Jerboa.getPropertiesManager().updateProperty("lastDatabase",databaseName);
					addDatabase.dispose();
					selectDatabase();
				}
			}
		});

		//add components to panels and dialog
		fieldPanel.add(textField);
		fieldPanel.add(okButton);
		fieldPanel.setLayout(new BoxLayout(fieldPanel, BoxLayout.X_AXIS));
		mainPanel.setLayout(new BorderLayout());
		mainPanel.add(label, BorderLayout.NORTH);
		mainPanel.add(fieldPanel, BorderLayout.CENTER);
		addDatabase.add(mainPanel);
		FileUtilities.putIcon(addDatabase);
		addDatabase.setAlwaysOnTop(true);
		addDatabase.setLocationRelativeTo(JerboaGUI.frame);
		addDatabase.pack();
		addDatabase.setVisible(true);
	}

}