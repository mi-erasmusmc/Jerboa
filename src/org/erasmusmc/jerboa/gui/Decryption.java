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
 * $Rev:: 4631              $:  Revision of last commit                                   *
 * $Author:: root           $:  Author of last commit                                     *
 * $Date:: 					$:  Date and time (CET) of last commit						  *
 ******************************************************************************************/
package org.erasmusmc.jerboa.gui;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.HashMap;
import java.util.List;

import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JTextField;
import javax.swing.filechooser.FileNameExtensionFilter;

import org.erasmusmc.jerboa.utilities.ErrorHandler;
import org.erasmusmc.jerboa.utilities.FileUtilities;
import org.erasmusmc.jerboa.utilities.Logging;
import org.erasmusmc.jerboa.utilities.Pack;

/**
 * This class contains the necessary methods to decrypt an encrypted result file created during a run of the application.
 * It contains the methods to create a GUI that allows the user to select the file to decrypt as well the
 * private decryption key.
 *
 * @author MG
 *
 */
public class Decryption{

	//file paths
	private String encryptedFilePath;
	private String privateKeyPath;
	private String targetFolderPath;
	private String defaultPath;

	//GUI related
	public static JFrame frame;
	private static JPanel filePanel;
	final static JButton decryptButton = new JButton("Decrypt");
	final static JButton cancelButton = new JButton("Cancel");
	@SuppressWarnings("rawtypes")
	private HashMap componentMap;

	/**
	 * Basic constructor setting the path as current path and initializing the GUI components
	 */
	public Decryption(){
		this.defaultPath = ".";
		initGUI();
	}

	/**
	 * Constructor receiving the path to the encrypted file.
	 * @param path - the absolute path of the file to be decrypted
	 */
	public Decryption(String path){
		this.defaultPath = path;
		initGUI();
	}

	/**
	 * Separate thread to actually decrypt the file and
	 *  to properly display progress bar.
	 *
	 * @author MG
	 *
	 */
	public static class Decrypt extends Thread{

		//file paths
		private String encryptedFilePath;
		private String targetFolder;
		private String privateKeyPath;

		/**
		 * Constructor receiving the path to the encrypted file, the folder in which to
		 * decrypt and the path to the private key to use in the decryption.
		 * @param encryptedFilePath - the location of the encrypted results file
		 * @param targetFolder - the folder in which to decrypt the results
		 * @param privateKeyPath - the private encryption key to use
		 */
		public Decrypt(String encryptedFilePath, String targetFolder, String privateKeyPath){
			this.encryptedFilePath = encryptedFilePath;
			this.targetFolder = targetFolder;
			this.privateKeyPath = privateKeyPath;
		}

		@Override
		@SuppressWarnings("deprecation")
		public void run() {
			decryptButton.setEnabled(false);
			List<String> files = Pack.unpackFiles(encryptedFilePath,targetFolder, privateKeyPath);
			if (files != null && files.size() > 0){
				JOptionPane.showMessageDialog(frame, "Decryption finished succesfully!"
					, "Decrypt", JOptionPane.INFORMATION_MESSAGE);
			}else{
				decryptButton.setEnabled(true);
				Logging.add("Unable to decrypt the file "+encryptedFilePath+".\nPlease check the private key or target folder.", true);
				new ErrorHandler("Unable to decrypt the file "+encryptedFilePath+".\nPlease make sure " +
						"you are using the right private key and/or the target folder is accessible.");
				Decrypt.currentThread().stop();
			}
		}
	}

	/**
	 * Creates a panel with the necessary components to select a file to be used in the decryption process.
	 * @param panelName - the name of the panel to be created
	 * @param fileExtension - the extension of the desired file to be opened; used in the file extension filter
	 * @param is folder - true if the user wants to open a folder and not a file
	 * @return - the populated and initialized panel
	 */
	private  JPanel createPanel(final String panelName, final String fileExtension, final boolean isFolder){

		//create a panel to hold all components for a file
		JPanel panel = new JPanel();
		panel.setName(panelName);
		panel.setBorder(BorderFactory.createTitledBorder(BorderFactory.createEtchedBorder(), panelName));
		panel.setLayout(new BoxLayout(panel, BoxLayout.X_AXIS));

		//create a text field holding the input file path
		final JTextField filePath = new JTextField();
		filePath.setEditable(false);
		filePath.setBackground(Color.WHITE);

		//create a button to allow input file selection
		final JButton selectFile = new JButton("Select");
		selectFile.addActionListener(new ActionListener(){
			public void actionPerformed(ActionEvent e) {
				String path = FileUtilities.openFileWithDialog(frame,
						defaultPath, isFolder, new FileNameExtensionFilter(panelName, fileExtension));
				if (path != null && !path.equals("")){
					filePath.setText(path);
				}
			}
		});

		//add components to panel
		panel.add(filePath);
		panel.add(selectFile);

		return panel;
	}

	/**
	 * Initializes all the GUI components and makes the frame visible.
	 */
	private void initGUI(){

		//a panel for the files
		filePanel = new JPanel();
		filePanel.setLayout(new BoxLayout(filePanel, BoxLayout.Y_AXIS));
		filePanel.setBorder(BorderFactory.createEtchedBorder());

		//add panels for each file path
		final String encryptedPanelName = "Encrypted file";
		final String privateKeyPanelName = "Private key file";
		final String targetFolderPanelName = "Target folder";

		filePanel.add(createPanel(encryptedPanelName, "enc", false));
		filePanel.add(createPanel(privateKeyPanelName, "key", false));
		filePanel.add(createPanel(targetFolderPanelName, "*.*", true));

		//initialize frame
		frame = new JFrame("Decrypting file");
		createComponentMap();
		FileUtilities.putIcon(frame);

		//a panel for the buttons
		JPanel buttonPanel = new JPanel();
		buttonPanel.setLayout(new FlowLayout(FlowLayout.CENTER));

		//add actions
		decryptButton.addActionListener(new ActionListener(){
			public void actionPerformed(ActionEvent e){

				//retrieve the selected paths
				encryptedFilePath = ((JTextField)((JPanel)getComponentByName(encryptedPanelName)).getComponent(0)).getText();
				privateKeyPath = ((JTextField)((JPanel)getComponentByName(privateKeyPanelName)).getComponent(0)).getText();
				targetFolderPath = ((JTextField)((JPanel)getComponentByName(targetFolderPanelName)).getComponent(0)).getText();

				//see if all are in order
				if (encryptedFilePath == null || encryptedFilePath.equals("")){
					new ErrorHandler("Please select an encrypted file");
					decryptButton.setEnabled(true);
				}else if (privateKeyPath == null || privateKeyPath.equals("")){
					new ErrorHandler("Please select a private key file");
					decryptButton.setEnabled(true);
				}else if (targetFolderPath == null || targetFolderPath.equals("")){
					new ErrorHandler("Please select a target folder");
					decryptButton.setEnabled(true);
				}else{
					Decrypt decrypt = new Decrypt(encryptedFilePath, targetFolderPath, privateKeyPath);
					decrypt.start();
				}
			}
		});

		cancelButton.addActionListener(new ActionListener(){
			public void actionPerformed(ActionEvent e){
				if (Decrypt.currentThread().isAlive())
					Decrypt.currentThread().interrupt();
				frame.dispose();
			}
		});

		//add buttons to panel
		buttonPanel.add(cancelButton);
		buttonPanel.add(decryptButton);

		//add main panel for all sub panels
		JPanel mainPanel = new JPanel();
		mainPanel.setLayout(new BorderLayout());

		mainPanel.add(filePanel, BorderLayout.CENTER);
		mainPanel.add(buttonPanel, BorderLayout.SOUTH);

		//add components on frame
		frame.setPreferredSize(new Dimension(450,230));
		frame.setLayout(new BorderLayout());
		frame.setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
		frame.add(mainPanel);
		frame.pack();
		frame.setLocationRelativeTo(null);
		frame.setResizable(false);
		frame.setVisible(true);
	}

	/**
	 * Creates a mapping of the GUI components from files panel by their assigned name.
	 * Used in order to access components of the GUI inside any nested class.
	 */
	@SuppressWarnings("unchecked")
	private void createComponentMap() {
		componentMap = new HashMap<String,Component>();
		Component[] components = filePanel.getComponents();
		for (int i=0; i < components.length; i++) {
			componentMap.put(components[i].getName(), components[i]);
		}
	}

	/**
	 * Retrieve a component of the GUI by its name.
	 * @param name - name of the component to be retrieved
	 * @return - the demanded component
	 */
	public Component getComponentByName(String name) {
		if (componentMap.containsKey(name))
			return (Component) componentMap.get(name);
		else
			return null;
	}

	//MAIN METHOD - FOR DEBUGGING
	public static void main(String[] args){
		try {
		    Thread.sleep(1500);
		} catch(InterruptedException ex) {
		    Thread.currentThread().interrupt();
		}
		new Decryption();
	}

}