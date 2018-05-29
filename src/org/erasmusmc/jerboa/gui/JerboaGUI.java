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
 * $Rev:: 4812              $:  Revision of last commit                                   *
 * $Author:: root           $:  Author of last commit                                     *
 * $Date:: 					$:  Date and time (CET) of last commit						  *
 ******************************************************************************************/
package org.erasmusmc.jerboa.gui;

import org.apache.commons.io.FilenameUtils;
import org.erasmusmc.jerboa.Jerboa;
import org.erasmusmc.jerboa.config.DataDefinition;
import org.erasmusmc.jerboa.config.FilePaths;
import org.erasmusmc.jerboa.config.PropertiesManager;
import org.erasmusmc.jerboa.config.Parameters;
import org.erasmusmc.jerboa.engine.InputFileSet;
import org.erasmusmc.jerboa.engine.Processing;
import org.erasmusmc.jerboa.engine.ScriptParser;
import org.erasmusmc.jerboa.engine.WorkFlow;
import org.erasmusmc.jerboa.gui.AboutBox;
import org.erasmusmc.jerboa.modules.viewers.DataViewer;
import org.erasmusmc.jerboa.modules.viewers.PatientViewer;
import org.erasmusmc.jerboa.utilities.ErrorHandler;
import org.erasmusmc.jerboa.utilities.FileUtilities;
import org.erasmusmc.jerboa.utilities.InputFileUtilities;
import org.erasmusmc.jerboa.utilities.Logging;
import org.erasmusmc.jerboa.utilities.MemoryBenchmark;
import org.erasmusmc.jerboa.utilities.MemoryUtilities;
import org.erasmusmc.jerboa.utilities.PatientUtilities;
import org.erasmusmc.jerboa.utilities.StringUtilities;
import org.erasmusmc.jerboa.utilities.TimeUtilities;

import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.Image;
import java.awt.Toolkit;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.net.URL;
import java.util.HashMap;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JDialog;
import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JMenu;
import javax.swing.JMenuBar;
import javax.swing.JMenuItem;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JProgressBar;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.JTabbedPane;
import javax.swing.JTextField;
import javax.swing.border.EtchedBorder;
import javax.swing.filechooser.FileNameExtensionFilter;

/**
 * This class configures and launches the GUI version of the application.
 * It also contains methods that deal with the state of the application
 * at a given point during the execution of the work flow.
 * @author MG
 *
 */
public class JerboaGUI{

	/*--------GUI COMPONENTS--------*/

	public static JFrame frame = null;
	private static JPanel mainPanel;
	public static JPanel filesPanel;
	public static JTabbedPane tabbedPane;
	public static JSplitPane split;
	public static JScrollPane consoleScrollPane;
	public static JPanel lowerPanel;
	public static JPanel statusPanel;
	public static JPanel progressPanel;

	public static JLabel statusLabel;
	public static JProgressBar statusBar;
	public static JCheckBox sortedFiles;

	private static JButton startButton;
	private static JButton stopButton;
	private static JButton resultsButton;

	private static JMenuBar menuBar;
	private static JMenuItem checkFiles;
	private static JMenuItem decryptFile;
	private static JMenuItem openWorkspaceItem;
	private static JMenuItem viewDataItem;
	public static JMenuItem showErrorsItem;
	private static JMenuItem inputErrors;
	private static JMenuItem patientErrors;
	private static JLabel memoryLabel;

	//GUI components dimension
	public static final int FRAME_WIDTH = 750;
	public static final int FRAME_HEIGHT = 500;
	public static final int PANEL_WIDTH = 400;

	//keep a list of all GUI components names
	@SuppressWarnings("rawtypes")
	private HashMap componentMap;

	/*------END OF GUI COMPONENTS------*/

	/*------------UTILS----------------*/

	//memory related
	public long memoryValue;
	protected MemoryThread memoryThread;

	//flags
	public static boolean noInputFiles;
	public static boolean checkForDuplicateFiles;
	public static boolean inputFileSelected;
	public static boolean memoryUsage;

	//manually selected input file
	private String manualFileSelection;


	/**
	 * Basic constructor launching the application in GUI mode.
	 */
	public JerboaGUI(){
		go();
	}

	/**
	 * Start the application.
	 */
	public void go(){
		displayLicense();
		selectWorkspace();
		sayHello();
		parseScript();
		displayScriptDetails();
		checkVersion();
		if (Jerboa.isVersionCompatible){
			initializeWorkflow();
			initializeGUI();
			checkRun();
		}else{
			go();
		}
	}

	/**
	 * Displays the license screen if the API runs for the first time.
	 * It is based on the existence of the properties file in the current directory.
	 * If there is a properties file, it implies the user has run before the application,
	 * therefore no license has to be displayed.
	 */
	private void displayLicense(){
		if (PropertiesManager.isFirstRun())
			new LicenseBox();
	}

	/**
	 * Lets the user select a workspace and initializes
	 * the input file set from the selected folder.
	 */
	private void selectWorkspace(){
		Jerboa.setInputFileSet(new WorkspaceDialog().getInputSet());
	}

	/**
	 * Creates a welcome message.
	 */
	private void sayHello(){
		//first let the user know if in debug mode
		if (PropertiesManager.isInDebugMode())
			Logging.add("Running in debug mode"+"\n");
		Logging.add("Welcome to Jerboa "+Parameters.VERSION+"\n");
		Logging.add("Working folder:");
		Logging.add(WorkspaceDialog.getWorkingFolder()+"\n");
	}

	/**
	 * Parses the selected script file if present.
	 */
	private void parseScript(){
		if (Jerboa.getInputFileSet() != null){
			if (Jerboa.getInputFileSet().getScriptFile() != null){
				Jerboa.setScriptParser(new ScriptParser(Jerboa.getInputFileSet().getScriptFile()));
			}else{
				Logging.add("No script file found in the working folder", Logging.ERROR);
				new ErrorHandler("No script file found in the working folder");
				selectWorkspace();
			}
		}else{
			Logging.add("No input files present in the working folder", Logging.ERROR);
			new ErrorHandler("No input files present in the working folder");
		}
	}

	/**
	 * Displays the meta data from the script file if present.
	 */
	private void displayScriptDetails(){
		if (Jerboa.getScriptParser() != null)
			Jerboa.getScriptParser().displayMetaData();
	}

	/**
	 * Checks if the version required by the script file is compatible
	 * with the version of the application.
	 */
	private void checkVersion(){
		if (Jerboa.getScriptParser() != null){
			Jerboa.isVersionCompatible = Jerboa.getScriptParser().checkRequiredVersion();
		}
	}

	/**
	 * Initializes the work flow based on the selected script file.
	 * If the application is running in debug mode, the work flow is
	 * initialized even without having a script file present in the working folder.
	 */
	private void initializeWorkflow(){
		if (Jerboa.isVersionCompatible && Jerboa.getInputFileSet() != null && Jerboa.getInputFileSet().getScriptFile() != null){

			//instantiate needed modules and figure out needed files
			Jerboa.setWorkFlow(new WorkFlow(Jerboa.getScriptParser()));

			//remove input files that are not needed - depending if in debug mode or not
			if (!PropertiesManager.isInDebugMode())
				Jerboa.getInputFileSet().removeUneededFiles(Jerboa.getWorkFlow().getNeededInputFiles());
			else
				Jerboa.getWorkFlow().setNeededFiles(Jerboa.getInputFileSet().getProvidedFiles());

			//see if we need to recycle the patient objects
			if (Jerboa.getScriptParser().reusePatients())
				FileUtilities.prepareToReusePatients();

		}else{

			//initialize the work flow if in debug mode, even if no script
			if (PropertiesManager.isInDebugMode()){
				Jerboa.setWorkFlow(new WorkFlow());
				Jerboa.getWorkFlow().setNeededFiles(Jerboa.getInputFileSet().getProvidedFiles());
			}
		}
	}

	/**
	 * Checks if the application is ready to run
	 * based on the version required by the script, the parameter
	 * settings from the script and the run mode from the properties file.
	 */
	private void checkRun(){
		if ((!PropertiesManager.isInDebugMode() &&
				(Jerboa.getInputFileSet() != null && Jerboa.getInputFileSet().getScriptFile() != null)) &&
				!Jerboa.isVersionCompatible)
			wrongVersion();
		else if (Jerboa.getWorkFlow() == null ||
				(Jerboa.getWorkFlow() != null &&
				!Jerboa.getWorkFlow().hasSettingsOk()))
			hibernate("I'm faulty.. ");
		else{
			if (Jerboa.getInputFileSet().getScriptFile() != null &&
				Jerboa.getScriptParser().isScriptValid())
				Logging.add("Press start to begin processing the script: "+
					FilenameUtils.getName(Jerboa.getInputFileSet().getScriptFile())+"\n");
			else
				hibernate("I'm faulty.. ");
		}

		if (noInputFiles)
			noInput();
	}

	/**
	 * Initializes all the elements of the GUI.
	 */
	private void initializeGUI(){

		//initialize the GUI frame
		frame = createFrame();

		//init main panel
		mainPanel = new JPanel();
		mainPanel.setLayout(new BorderLayout());
		mainPanel.setName("MainPanel");

		//init the tabbed pane and add the files panel
		tabbedPane = new JTabbedPane(JTabbedPane.TOP);
		tabbedPane.setName("Tabbed Pane");
		tabbedPane.setTabLayoutPolicy(JTabbedPane.SCROLL_TAB_LAYOUT);

		//add files panel
		filesPanel = new JPanel();
		filesPanel.setName("Input files");
		filesPanel.setLayout(new BoxLayout(filesPanel, BoxLayout.Y_AXIS));
		filesPanel = createUpperPanel();

		//add console:
		lowerPanel = new JPanel();
		lowerPanel.setName("ConsolePanel");
		lowerPanel.setLayout(new BorderLayout());
		lowerPanel.setBorder(BorderFactory.createEmptyBorder(5,10,5,10));

		//initialize API log
		consoleScrollPane = new JScrollPane(Logging.getTextArea());
		consoleScrollPane.setName("ConsoleScrollPane");
		consoleScrollPane.setPreferredSize(new Dimension(PANEL_WIDTH,280));
		consoleScrollPane.setMinimumSize(new Dimension(PANEL_WIDTH,280));
		consoleScrollPane.setMaximumSize(new Dimension(PANEL_WIDTH,(FRAME_HEIGHT - filesPanel.getHeight())));
		consoleScrollPane.setBorder(BorderFactory.createTitledBorder("Console"));
		consoleScrollPane.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED);
		consoleScrollPane.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
		consoleScrollPane.setAutoscrolls(true);

		//add console panel to the tabbed pane
		lowerPanel.add(consoleScrollPane, BorderLayout.CENTER);

		split = new JSplitPane(JSplitPane.VERTICAL_SPLIT);
		split.setName("Workflow");
		split.setResizeWeight(0.0);
		split.setEnabled(false);

		//new panel to hold both files and console panel
		split.add(filesPanel);
		split.add(lowerPanel);

		tabbedPane.add(split);
		mainPanel.add(tabbedPane, BorderLayout.CENTER);

		//add the progress label and run button
		statusPanel = new JPanel();
		statusPanel.setName("ButtonsPanel");
		statusPanel.setLayout(new BoxLayout(statusPanel, BoxLayout.X_AXIS));
		statusPanel.setBorder(BorderFactory.createEtchedBorder(EtchedBorder.LOWERED));

		statusLabel = new JLabel("I'm idle ");
		statusLabel.setName("ProgressLabel");
		statusPanel.add(statusLabel);

		statusBar = new JProgressBar();
		statusBar.setName("ProgressBar");
		statusBar.setPreferredSize(new Dimension(40,15));
		statusBar.setMaximumSize(new Dimension(40,15));
		statusPanel.add(statusBar);

		memoryValue = MemoryUtilities.memoryValue();
		memoryLabel = new JLabel(MemoryUtilities.memory());
		statusPanel.add(memoryLabel);
		statusPanel.add(Box.createHorizontalGlue());
		sortedFiles = new JCheckBox("Files Sorted");
		sortedFiles.setEnabled(true);
		sortedFiles.setSelected(true);
		sortedFiles.addActionListener(new ActionListener(){
			public void actionPerformed(ActionEvent e) {
				Processing.filesSorted = sortedFiles.isSelected();
			}
		});
		startButton = new JButton("Start");
		startButton.setName("StartButton");
		startButton.addActionListener(new ActionListener(){
			public void actionPerformed(ActionEvent e) {

				//log table with input file details
				Jerboa.getInputFileSet().outputSelectedFiles();

				//allow user to select the database
				new DatabaseDialog();
				if (Parameters.DATABASE_NAME != null && Parameters.DATABASE_NAME != "DB"){

					//update time stamp for file names
					TimeUtilities.refreshTimeStamp();

					//check if already ran once
					if (WorkspaceDialog.hasAlreadyRan()){

						//update the output paths and create folder structure for the new run
						FilePaths.updatePaths(WorkspaceDialog.getWorkingFolder(), PropertiesManager.getRunFolder());
						InputFileUtilities.createFolderStructure(Jerboa.getPropertiesManager());

						//and recreate the work flow thread - because it cannot be restarted
						Jerboa.setWorkFlow(new WorkFlow(Jerboa.getScriptParser()));
					}
					if (!Jerboa.getWorkFlow().hasSettingsOk())
						hibernate("I'm faulty.. ");
				else if (Jerboa.getInputFileSet().getSelectedFiles() != null &&
						Jerboa.getInputFileSet().getSelectedFiles().size() > 0 ||
							Jerboa.getScriptParser().isExtractionNeeded()){
						JerboaGUI.removeTabs();
						WorkspaceDialog.setAlreadyRan(true);
						busy();
						(new MemoryThread()).start();
						Processing.checkOnly = false;
						Processing.filesSorted = sortedFiles.isSelected();
					Jerboa.setRunThread(new Processing(Jerboa.getInputFileSet()));
						Jerboa.getRunThread().start();
				}
			}
		}});
		stopButton = new JButton("Stop");
		stopButton.setName("StopButton");
		stopButton.setEnabled(false);
		stopButton.addActionListener(new ActionListener(){
			public void actionPerformed(ActionEvent e) {
				Logging.addNewLine();
				Logging.add("Application stopped by user.", Logging.HINT);
				Jerboa.stop();
			}});

		resultsButton = new JButton("Results");
		resultsButton.setName("ResultsButton");
		resultsButton.setEnabled(false);
		resultsButton.addActionListener(new ActionListener(){
			public void actionPerformed(ActionEvent e) {
				FileUtilities.openFolder(FilePaths.DAILY_DATA_PATH);
			}});

		statusPanel.add(resultsButton);
		statusPanel.add(stopButton);
		statusPanel.add(startButton);

		progressPanel = new JPanel();
		progressPanel.setLayout(new BoxLayout(progressPanel, BoxLayout.Y_AXIS));
		progressPanel.add(statusPanel);

		//add a dummy progress panel in the beginning
		JPanel dummyPanel = new JPanel();
		dummyPanel.setPreferredSize(new Dimension(FRAME_WIDTH, 20));
		dummyPanel.setMinimumSize(new Dimension(FRAME_WIDTH, 20));
		dummyPanel.setMaximumSize(new Dimension(Toolkit.getDefaultToolkit().getScreenSize().width, 20));

		progressPanel.add(dummyPanel);

		//add components to the frame
		frame.setJMenuBar(createMenuBar());
		//split in two by the console
		frame.add(mainPanel, BorderLayout.CENTER);
		frame.add(progressPanel, BorderLayout.SOUTH);

		//create HasMap with GUI components
		createComponentMap();

		// prepare and display GUI
		frame.setPreferredSize(new Dimension(FRAME_WIDTH,FRAME_HEIGHT));
		frame.setMinimumSize(new Dimension(FRAME_WIDTH,FRAME_HEIGHT));
		frame.getRootPane().setDefaultButton(startButton);
		frame.pack();
		frame.setLocationRelativeTo(null);
		//	frame.setLocationByPlatform(true);
		frame.setVisible(true);
		frame.revalidate();
	}

	/**
	 * Creates and populates the menu bar of the API.
	 * @return - the menu bar of the application
	 */
	private JMenuBar createMenuBar(){

		//menu bar
		menuBar  = new JMenuBar();

		//menu entries
		JMenu fileMenu;
		JMenu toolsMenu;
		JMenu viewMenu;
		JMenu helpMenu;

		//menu items
		JMenuItem exitItem;
		JMenuItem manualItem;
		JMenuItem aboutItem;
		JMenuItem memoryItem;
		JMenuItem viewPatientItem;
		JMenuItem viewScriptItem;
		JMenuItem viewLogItem;

		//add the File menu and its items
		fileMenu = new JMenu("File");
		openWorkspaceItem = new JMenuItem("Open Workspace");
		openWorkspaceItem.setEnabled(true);
		openWorkspaceItem.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				go();
			}});
		fileMenu.add(openWorkspaceItem);

		exitItem = new JMenuItem("Exit");
		exitItem.addActionListener(new ActionListener(){
			public void actionPerformed(ActionEvent e) {
				Jerboa.stop();
				memoryThread = null;
				System.exit(0);
			}});
		fileMenu.add(exitItem);

		menuBar.add(fileMenu);

		//add the tools menu and its items
		toolsMenu = new JMenu("Tools");

		checkFiles = new JMenuItem("Check Files");
		checkFiles.addActionListener(new ActionListener(){
			public void actionPerformed(ActionEvent e) {
				Processing.checkOnly = true;
				Jerboa.setRunThread(new Processing(Jerboa.getInputFileSet()));
				Jerboa.getRunThread().start();
			}});

		toolsMenu.add(checkFiles);

		decryptFile = new JMenuItem("Decrypt File");
		decryptFile.addActionListener(new ActionListener(){
			public void actionPerformed(ActionEvent e) {
				new Decryption(FilePaths.DATA_PATH);
			}});

		toolsMenu.add(decryptFile);

		memoryItem = new JMenuItem("Memory usage");
		memoryItem.addActionListener(new ActionListener(){
			public void actionPerformed(ActionEvent e) {
				MemoryBenchmark.Automated.sample(3, true, true);
				memoryUsage = !memoryUsage;
			}});

		toolsMenu.add(memoryItem);

		menuBar.add(toolsMenu);

		//add the view menu and its items
		viewMenu = new JMenu("View");

		showErrorsItem = new JMenu("Errors");
		showErrorsItem.setEnabled(false);
		inputErrors = new JMenuItem("Input files");
		inputErrors.setEnabled(false);
		patientErrors = new JMenuItem("Patients");
		patientErrors.setEnabled(false);
		patientErrors.setEnabled(false);
		inputErrors.addActionListener(new ActionListener(){
			public void actionPerformed(ActionEvent e) {
				busy();
				new ErrorHandler(Jerboa.getRunThread().getInputErrorList());
				done();
			}});
		patientErrors.addActionListener(new ActionListener(){
			public void actionPerformed(ActionEvent e) {
				busy();
				new ErrorHandler(Jerboa.getRunThread().getPatientErrors(), "Patients");
				done();
			}});
		showErrorsItem.add(inputErrors);
		showErrorsItem.add(patientErrors);
		viewMenu.add(showErrorsItem);

		viewDataItem = new JMenuItem("Data Profile");
		viewDataItem.addActionListener(new ActionListener(){
			public void actionPerformed(ActionEvent e) {
				new DataViewer();
			}});
		viewMenu.add(viewDataItem);

		viewPatientItem = new JMenuItem("Patients");
		viewPatientItem.setEnabled(true);
		viewPatientItem.addActionListener(new ActionListener(){
			public void actionPerformed(ActionEvent e) {
				String fileName = FileUtilities.openFileWithDialog(frame,FilePaths.DATA_PATH, false);
				if (fileName != null && !fileName.equals("")){
					frame.setCursor(Cursor.getPredefinedCursor(Cursor.WAIT_CURSOR));
					Logging.add("Loading patient viewer..", Logging.HINT);
					PatientUtilities pu = new PatientUtilities(fileName);
					new PatientViewer(pu.loadPatientsFromFile(fileName, true, true, true), fileName, "Patient Viewer");
					frame.setCursor(Cursor.getPredefinedCursor(Cursor.DEFAULT_CURSOR));
				}
			}});

		viewMenu.add(viewPatientItem);

		viewScriptItem = new JMenuItem("Script");
		viewScriptItem.setEnabled(true);
		viewScriptItem.addActionListener(new ActionListener(){
			public void actionPerformed(ActionEvent e){
				FileUtilities.openFileInDefaultTextEditor(Jerboa.getInputFileSet().getScriptFile());
			}
		});

		viewMenu.add(viewScriptItem);

		viewLogItem = new JMenuItem("Log");
		viewLogItem.setEnabled(true);
		viewLogItem.addActionListener(new ActionListener(){
			public void actionPerformed(ActionEvent e){
				FileUtilities.openFileInDefaultTextEditor(Logging.outputFile);
			}
		});

		viewMenu.add(viewLogItem);

		menuBar.add(viewMenu);

		//add the help menu and its items
		helpMenu = new JMenu("Help");
		manualItem = new JMenuItem("Manual");
		manualItem.addActionListener(new ActionListener(){
			public void actionPerformed(ActionEvent e) {
				//TODO load the manual
				System.err.println("That would be Jerboa Manual.");
			}});
		aboutItem = new JMenuItem("About");
		aboutItem.addActionListener(new ActionListener(){
			public void actionPerformed(ActionEvent e) {
				AboutBox aboutBox = new AboutBox();
				aboutBox.setModal(true);
				aboutBox.setVisible(true);
			}});

		helpMenu.add(aboutItem);
		helpMenu.setEnabled(true);
		menuBar.add(helpMenu);

		//add logo - it will run faster :]
		menuBar.add(Box.createHorizontalGlue());
		URL url = Jerboa.class.getResource(FilePaths.LOGO_PATH);
		Image img = Toolkit.getDefaultToolkit().getImage(url);
		JLabel logo = new JLabel(new ImageIcon(img, null));
		logo.setHorizontalAlignment(JLabel.RIGHT);
		menuBar.add(logo,BorderLayout.EAST);

		return menuBar;
	}

	/**
	 * Will enable the view errors menu and its corresponding
	 * sub menu based on the existing errors (input file related
	 * or patient object related).
	 */
	public static void setErrorMenu(){
		if (!Processing.noInputErrors){
			showErrorsItem.setEnabled(true);
			inputErrors.setEnabled(true);
		}
		if (Jerboa.getRunThread().getPatientErrors() != null &&
				Jerboa.getRunThread().getPatientErrors().size() > 0){
			showErrorsItem.setEnabled(true);
			patientErrors.setEnabled(true);
		}
	}

	/**
	 * Creates the main frame of the application.
	 * @return - the newly created application frame equipped with an event listener
	 * for exiting the application
	 */
	private JFrame createFrame() {

		frame = new JFrame("Jerboa "+Parameters.VERSION);
		frame.setDefaultCloseOperation(JFrame.DO_NOTHING_ON_CLOSE);
		frame.addWindowListener(new WindowAdapter() {
			public void windowClosing(WindowEvent e) {
				int option = JOptionPane.showConfirmDialog(frame, "Are you sure you want to exit?",
						"Confirm",JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE);
				if (option == JOptionPane.YES_OPTION){
					frame.dispose();
					stop();
					System.exit(0);
				}
			}
		});

		frame.setLayout(new BorderLayout());
		FileUtilities.putIcon(frame);
		return frame;
	}

	/**
	 * Creates and populates the upper panel of the GUI.
	 * More of a shortcut to reinitialize the file selectors.
	 * @return - the panel with all the components initialized and displayed
	 */
	private JPanel createUpperPanel(){

		JPanel panel = new JPanel();
		panel.setName("Input files");
		panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));

		int nbPanels = 1;

		if (Jerboa.getInputFileSet().getSelectedFiles() != null &&
				Jerboa.getInputFileSet().getSelectedFiles().size() > 0 ||
				Jerboa.getScriptParser().isExtractionNeeded()){

			//set the flag for valid input files
			noInputFiles = false;

			//add the file panels by type
			if (Jerboa.getScriptParser() != null){

				boolean extractData = Jerboa.getScriptParser().isExtractionNeeded();

				//patients - should always be there
				if (Jerboa.getWorkFlow().neededInputFiles.get(DataDefinition.PATIENTS_FILE)){
					createPatientsFilePanel(extractData, panel);
				}

				//events
				if (Jerboa.getWorkFlow().neededInputFiles.get(DataDefinition.EVENTS_FILE)){
					createEventsFilePanel(extractData, panel);
					nbPanels ++;
				}

				//prescriptions
				if (Jerboa.getWorkFlow().neededInputFiles.get(DataDefinition.PRESCRIPTIONS_FILE)){
					createPrescriptionsFilePanel(extractData, panel);
					nbPanels ++;
				}

				//measurements
				if (Jerboa.getWorkFlow().neededInputFiles.get(DataDefinition.MEASUREMENTS_FILE)){
					createMeasurementsFilePanel(extractData, panel);
					nbPanels++;
				}
			}
			//no input files
		}else if (Jerboa.getScriptParser() != null && !Jerboa.getScriptParser().isExtractionNeeded()){
			//set the flag for valid input files
			noInputFiles = true;
			//raise error and set the upper panel to no files
			new ErrorHandler("No valid input files found in the folder.\n\rPlease choose a different workspace.");
			Logging.add("No valid input files found in the folder", Logging.ERROR);
			JLabel noFileLabel = new JLabel("No valid input file in the folder");
			noFileLabel.setAlignmentX(JLabel.CENTER_ALIGNMENT);
			panel.add(noFileLabel);
		}
		//set dimensions
		panel.validate();

		//set the frame size
		setFrameSize(nbPanels);

		return panel;
	}

	/**
	 * Adds an empty panel to the input files tab in order
	 * to preserve the aesthetics of the interface.
	 * @return - a dummy file panel
	 */
	private static JPanel addEmptyFilePanel(){
		JPanel filePanel = new JPanel();
		final JTextField text = new JTextField();
		text.setPreferredSize(new Dimension(PANEL_WIDTH, 60));
		text.setEditable(false);
		//filePanel.setBorder(BorderFactory.createTitledBorder(BorderFactory.createEtchedBorder(), null));
		filePanel.setLayout(new BoxLayout(filePanel, BoxLayout.X_AXIS));
		filePanel.setPreferredSize(new Dimension(PANEL_WIDTH, 60));
		filePanel.setMinimumSize(new Dimension(PANEL_WIDTH, 60));
		filePanel.add(text);
		return filePanel;
	}

	/**
	 * Creates the GUI panel for the patients input file.
	 * @param extractData - flag to let the user know if data extraction is needed
	 * @param panel - the parent JPanel on which to add the newly created panel
	 */
	private void createPatientsFilePanel(boolean extractData, JPanel panel){
		String fileName = null;
		String[] fileNames = null;
		if (extractData){
			fileName = "To be extracted..";
		}else if (Jerboa.getInputFileSet().gotPatients){
			if (Jerboa.getInputFileSet().getPatientFiles().size() > 1){
				fileNames = new String[Jerboa.getInputFileSet().getPatientFiles().size() + 1];
				for (int i = 0; i < Jerboa.getInputFileSet().getPatientFiles().size(); i++)
					fileNames[i+1] = StringUtilities.maskWorkingPath(Jerboa.getInputFileSet().getPatientFiles().get(i).getName());
				fileNames[0] = "Please select a file..";
			}else if (Jerboa.getInputFileSet().getPatientsFile() != null)
				fileName = StringUtilities.maskWorkingPath(Jerboa.getInputFileSet().getPatientsFile().getName());
		}else
			fileName = "No patients file found.";
		panel.add(fileNames != null ? createFileDropdown("Patients", fileNames, DataDefinition.PATIENTS_FILE, 0) :
			createFilePanel("Patients", fileName, DataDefinition.PATIENTS_FILE));
	}

	/**
	 * Creates the GUI panel for the events input file.
	 * @param extractData - flag to let the user know if data extraction is needed
	 * @param panel - the parent JPanel on which to add the newly created panel
	 */
	private void createEventsFilePanel(boolean extractData, JPanel panel){
		String fileName = null;
		String[] fileNames = null;
		if (extractData){
			fileName = "To be extracted..";
		}else if (Jerboa.getInputFileSet().gotEvents){
			if (Jerboa.getInputFileSet().getEventFiles().size() > 1){
				fileNames = new String[Jerboa.getInputFileSet().getEventFiles().size() + 1];
				for (int i = 0; i < Jerboa.getInputFileSet().getEventFiles().size(); i++)
					fileNames[i+1] = StringUtilities.maskWorkingPath(Jerboa.getInputFileSet().getEventFiles().get(i).getName());
				fileNames[0] = "Please select a file..";
			}else if (Jerboa.getInputFileSet().getEventsFile() != null)
				fileName = StringUtilities.maskWorkingPath(Jerboa.getInputFileSet().getEventsFile().getName());
		}else
			fileName = "No events file found.";
		panel.add(fileNames != null ? createFileDropdown("Events", fileNames, DataDefinition.EVENTS_FILE, 0) :
			createFilePanel("Events", fileName, DataDefinition.EVENTS_FILE));
	}

	/**
	 * Creates the GUI panel for the prescriptions input file.
	 * @param extractData - flag to let the user know if data extraction is needed
	 * @param panel - the parent JPanel on which to add the newly created panel
	 */
	private void createPrescriptionsFilePanel(boolean extractData, JPanel panel){
		String fileName = null;
		String[] fileNames = null;
		if (extractData){
			fileName = "To be extracted..";
		}else if (Jerboa.getInputFileSet().gotPrescriptions){
			if (Jerboa.getInputFileSet().getPrescriptionFiles().size() > 1){
				fileNames = new String[Jerboa.getInputFileSet().getPrescriptionFiles().size() + 1];
				for (int i = 0; i < Jerboa.getInputFileSet().getPrescriptionFiles().size(); i++)
					fileNames[i+1] = StringUtilities.maskWorkingPath(Jerboa.getInputFileSet().getPrescriptionFiles().get(i).getName());
				fileNames[0] = "Please select a file..";
			}else if (Jerboa.getInputFileSet().getPrescriptionsFile() != null)
				fileName = StringUtilities.maskWorkingPath(Jerboa.getInputFileSet().getPrescriptionsFile().getName());
		}else
			fileName = "No prescriptions file found.";
		panel.add(fileNames != null ? createFileDropdown("Prescriptions", fileNames, DataDefinition.PRESCRIPTIONS_FILE, 0) :
			createFilePanel("Prescriptions", fileName, DataDefinition.PRESCRIPTIONS_FILE));
	}

	/**
	 * Creates the GUI panel for the measurements input file.
	 * @param extractData - flag to let the user know if data extraction is needed
	 * @param panel - the parent JPanel on which to add the newly created panel
	 */
	private void createMeasurementsFilePanel(boolean extractData, JPanel panel){
		String fileName = null;
		String[] fileNames = null;
		if (extractData){
			fileName = "To be extracted..";
		}else if (Jerboa.getInputFileSet().gotMeasurements){
			if (Jerboa.getInputFileSet().getMeasurementFiles().size() > 1){
				fileNames = new String[Jerboa.getInputFileSet().getMeasurementFiles().size() + 1];
				for (int i = 0; i < Jerboa.getInputFileSet().getMeasurementFiles().size(); i++)
					fileNames[i+1] = StringUtilities.maskWorkingPath(Jerboa.getInputFileSet().getMeasurementFiles().get(i).getName());
				fileNames[0] = "Please select a file..";
			}else if (Jerboa.getInputFileSet().getMeasurementsFile() != null)
				fileName = StringUtilities.maskWorkingPath(Jerboa.getInputFileSet().getMeasurementsFile().getName());
		}else
			fileName = "No measurements file found.";
		panel.add(fileNames != null ? createFileDropdown("Measurements", fileNames, DataDefinition.MEASUREMENTS_FILE, 0) :
			createFilePanel("Measurements", fileName, DataDefinition.MEASUREMENTS_FILE));
	}

	/**
	 * Will set the preferred size of the GUI frame
	 * depending on the number of file panels that are to be added to the GUI.
	 * @param nbFilePanels - the number of file panels
	 */
	public static void setFrameSize(int nbFilePanels){
		//dynamically set the size depending on the number of file panels
		if (filesPanel.getComponentCount() < nbFilePanels)
			for (int i = filesPanel.getComponentCount(); i <= nbFilePanels; i ++)
				filesPanel.add(addEmptyFilePanel());
		int height = (nbFilePanels == 1 ? 120 : nbFilePanels*100);
		int consoleHeight = consoleScrollPane == null ? 300 : consoleScrollPane.getHeight();
		filesPanel.setMinimumSize(new Dimension(PANEL_WIDTH, height));
		filesPanel.setPreferredSize(new Dimension(PANEL_WIDTH, height));
		filesPanel.setMaximumSize(new Dimension(PANEL_WIDTH, height));
		frame.setPreferredSize(new Dimension(FRAME_WIDTH,(height+consoleHeight)));
		frame.setMinimumSize(new Dimension(FRAME_WIDTH,(height+consoleHeight)));
		frame.setLocationRelativeTo(null);
		frame.revalidate();
	}

	/**
	 * Creates a panel with the available options for the selection of an input file.
	 * @param panelName - the name of the panel to be created
	 * @param fileName - the absolute path of the input file to be displayed in the text field
	 * @param order - the order of the panel in the list of panels
	 * @return - the populated and initialized panel
	 */
	private JPanel createFilePanel(final String panelName, String fileName, final int order){

		//create a panel to hold all components for an input file
		JPanel filePanel = new JPanel();
		filePanel.setName(panelName+"Panel");
		filePanel.setBorder(BorderFactory.createTitledBorder(BorderFactory.createEtchedBorder(), panelName));
		filePanel.setLayout(new BoxLayout(filePanel, BoxLayout.X_AXIS));

		//create a text field holding the input file path
		final JTextField text = new JTextField();
		text.setName(panelName+"Text");
		text.setText(fileName);
		text.setEditable(false);

		//create a button to allow input file selection
		final JButton select = new JButton("Select");
		final String paneName = panelName;
		select.setName(panelName+"Button");
		select.setEnabled(!Jerboa.getScriptParser().isExtractionNeeded());
		select.addActionListener(new ActionListener(){
			public void actionPerformed(ActionEvent e) {
				if (chooseFile()){
					//add file path to the list of input files
					if (Jerboa.getInputFileSet().updateInputFile(manualFileSelection.trim(), (byte)order) == null){
						text.setText("Please select a valid file..");
						JOptionPane.showMessageDialog(JerboaGUI.frame, "Please select a valid " + paneName.toLowerCase() + " file"
							, "Error", JOptionPane.ERROR_MESSAGE);
					}else{
						String[] fileNames = new String[]{"Please select a valid file..", text.getText(),  manualFileSelection.trim()};
						createFileDropdown(panelName, fileNames, order, fileNames.length-1);
						Jerboa.getInputFileSet().updateInputFile(manualFileSelection.trim(), (byte)order);
					}
					inputFileSelected = true;
				}
			}
		});

		filePanel.add(text);
		filePanel.add(select);
		filePanel.setPreferredSize(new Dimension(PANEL_WIDTH, 60));
		filePanel.setMinimumSize(new Dimension(PANEL_WIDTH, 60));
		filePanel.setMaximumSize(new Dimension(Toolkit.getDefaultToolkit().getScreenSize().width, 60));

		return filePanel;
	}

	/**
	 * Creates a panel with the available options for the selection of an input file.
	 * @param panelName - the name of the panel to be created
	 * @param fileNames - an array with the path of the input files of a certain type displayed as dropdown list
	 * @param order - the order of the panel in the list of panels
	 * @param selectedIndex - the index to be selected in the dropdown list
	 * @return - the populated and initialized panel
	 */
	private JPanel createFileDropdown(String panelName, String[] fileNames, final int order, int selectedIndex){

		//create a panel to hold all components for an input file
		JPanel filePanel = new JPanel();
		filePanel.setName(panelName+"Panel");
		//if previously created as text field remove all and re-populate
		if (getComponentByName(filePanel.getName()) != null){
			filePanel = (JPanel)(getComponentByName(filePanel.getName()));
			filePanel.removeAll();
		}
		filePanel.setBorder(BorderFactory.createTitledBorder(BorderFactory.createEtchedBorder(), panelName));
		filePanel.setLayout(new BoxLayout(filePanel, BoxLayout.X_AXIS));

		//create a drop down holding the input file path
		final JComboBox<String> list = new JComboBox<String>(fileNames);
		list.setName(panelName+"List");
		list.setSelectedIndex(selectedIndex);
		//remove selected file by default
		Jerboa.getInputFileSet().updateInputFile(InputFileSet.NO_FILE, (byte)order);
		list.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent arg0) {
				if (list.getSelectedIndex() != 0){ //first one is not a file
					inputFileSelected = true;
					Jerboa.getInputFileSet().updateInputFile(list.getSelectedIndex()-1, (byte)order);
				}
			}
		});

		//create a button to allow input file selection
		final JButton select = new JButton("Select");
		final String paneName = panelName;
		select.setName(panelName+"Button");
		select.setEnabled(!Jerboa.getScriptParser().isExtractionNeeded());
		select.addActionListener(new ActionListener(){
			public void actionPerformed(ActionEvent e) {
				if (chooseFile()){
					//add file path to the list of input files
					if (Jerboa.getInputFileSet().updateInputFile(manualFileSelection.trim(), (byte)order) != null){
						String maskedText = StringUtilities.maskWorkingPath(manualFileSelection.trim());
						if (!alreadyInList(list, maskedText)){
							list.addItem(maskedText);
							list.setSelectedItem(maskedText);
						}
						inputFileSelected = true;
					}else{
						list.setSelectedIndex(0);
						Jerboa.getInputFileSet().updateInputFile(InputFileSet.NO_FILE, (byte)order);
						JOptionPane.showMessageDialog(JerboaGUI.frame, "Please select a valid " + paneName.toLowerCase() + " file"
								, "Error", JOptionPane.ERROR_MESSAGE);
					}
				}
			}
		});

		filePanel.add(list);
		filePanel.add(select);
		filePanel.setPreferredSize(new Dimension(PANEL_WIDTH, 60));
		filePanel.setMinimumSize(new Dimension(PANEL_WIDTH, 60));
		filePanel.setMaximumSize(new Dimension(Toolkit.getDefaultToolkit().getScreenSize().width, 60));

		return filePanel;
	}

	/**
	 * Creates a mapping of the GUI components from files panel by their assigned name.
	 * Used in order to access components of the GUI inside any nested class.
	 */
	@SuppressWarnings("unchecked")
	private void createComponentMap() {
		componentMap = new HashMap<String,Component>();
		Component[] components = filesPanel.getComponents();
		for (int i=0; i < components.length; i++) {
			componentMap.put(components[i].getName(), components[i]);
		}
	}

	/**
	 * Lets the user choose the folder from which to load multiple files.
	 * @return - true if successfully opened; false otherwise
	 */
	private boolean chooseFile(){
		try{
			//initialize components of JDialog
			JFileChooser fileChooser = new JFileChooser(WorkspaceDialog.getWorkingFolder() != null ?
					WorkspaceDialog.getWorkingFolder() : ".");
			FileNameExtensionFilter filter = new FileNameExtensionFilter(DataDefinition.FILE_FORMATS, DataDefinition.FILE_EXTENSIONS);
			fileChooser.setFileFilter(filter);
			//set the right selection mode
			fileChooser.setFileSelectionMode(JFileChooser.FILES_ONLY);
			int returnVal = fileChooser.showDialog(JerboaGUI.frame, "Select file");
			if(returnVal == JFileChooser.APPROVE_OPTION){
				manualFileSelection = fileChooser.getSelectedFile().getAbsolutePath().replaceAll("\\\\", "/");
				return true;
			}else{
				return false;
			}

		}catch (Exception e){
			Logging.outputStackTrace(e);
			return false;
		}
	}

	/*------------------END OF GUI INITIALIZERS----------------*/


	/*------------------- APPLICATION STATE RELATED--------------------*/

	/**
	 * Checks if the current API version is compatible with the version
	 * required by the script file.
	 */
	private void wrongVersion(){
		disableAllFilePanels();
		stop();
		hibernate(null);
	}

	/**
	 * Updates the components on a filePanel. This method is used when the user opens
	 * a data set and additionally hand picks specific input files.
	 * @param fileName - the name of the input file - used to update the JTextField
	 * @param panelName - the name of the file panel that is to be updated
	 */
	@SuppressWarnings("unused")
	private void updateFilePanel(String fileName, String panelName){
		((JTextField)((JPanel)getComponentByName(panelName)).getComponent(0)).
		setText(StringUtilities.maskWorkingPath(fileName));//text field
		((JTextField)((JPanel)getComponentByName(panelName)).getComponent(0)).setEnabled(!Jerboa.getScriptParser().isExtractionNeeded());
		((JTextField)((JPanel)getComponentByName(panelName)).getComponent(0)).revalidate();
		((JButton)((JPanel)getComponentByName(panelName)).getComponent(1)).setEnabled(!Jerboa.getScriptParser().isExtractionNeeded()); //button
		((JButton)((JPanel)getComponentByName(panelName)).getComponent(1)).revalidate();
		((JPanel)getComponentByName(panelName)).revalidate();
	}

	/**
	 * Tries to stop the thread handling the input file checking
	 * and resets the database name.
	 */
	@SuppressWarnings("deprecation")
	public static void stop(){
		if (frame != null){
			statusBar.setIndeterminate(false);
			startButton.setEnabled(true);
			stopButton.setEnabled(false);
			resultsButton.setEnabled(true);
			statusLabel.setText("I'm idle ");
			hideProgress();
			frame.setCursor(Cursor.getPredefinedCursor(Cursor.DEFAULT_CURSOR));
			try{
				if (Jerboa.getWorkFlow() != null && Jerboa.getWorkFlow().isAlive()){
					Jerboa.getWorkFlow().interrupt();
					Jerboa.getWorkFlow().stop();
				}
				if (Jerboa.getRunThread() != null && Jerboa.getRunThread().isAlive()){
					Jerboa.getRunThread().interrupt();
					Jerboa.getRunThread().stop();
				}
			}catch(Throwable e){
				Logging.add("Threads did not finish correctly.", Logging.HINT, true);
				Logging.outputStackTrace(e);
			}
		}
	}

	/**
	 * Updates necessary GUI components to show that the application is busy processing.
	 */
	public static void busy(){
		if (frame != null){
			startButton.setEnabled(false);
			stopButton.setEnabled(true);
			resultsButton.setEnabled(false);
			checkFiles.setEnabled(false);
			statusLabel.setText("I'm busy.. ");
			//frame.setCursor(Cursor.getPredefinedCursor(Cursor.WAIT_CURSOR));
			statusBar.setIndeterminate(true);
		}
	}

	/**
	 * Updates necessary GUI components to show that the application has finished processing.
	 */
	public static void done(){
		if (frame != null){
			startButton.setEnabled(true);
			stopButton.setEnabled(false);
			resultsButton.setEnabled(true);
			checkFiles.setEnabled(true);
			statusLabel.setText("I'm idle ");
			statusBar.setIndeterminate(false);
			hideProgress();
			frame.setCursor(Cursor.getPredefinedCursor(Cursor.DEFAULT_CURSOR));
		}
	}

	/**
	 * Disables all features available on the GUI to prevent the user from
	 * continuing. It is used if the version required by the script is superior
	 * to the current version.
	 * @param message - the custom message to be displayed on the lower left label of the application
	 */
	public static void hibernate(String message){
		if (frame != null){
			startButton.setEnabled(false);
			stopButton.setEnabled(false);
			resultsButton.setEnabled(false);
			statusLabel.setText(message == null || message.equals("") ?
					"I'm old.. " : message);
			statusBar.setIndeterminate(false);
			menuBar.setVisible(Jerboa.isVersionCompatible);
			frame.setCursor(Cursor.getPredefinedCursor(Cursor.DEFAULT_CURSOR));
		}
	}

	/**
	 * Disables the run button but makes sure the menu bar is visible
	 * in order to allow the user to select a workspace in case there
	 * are no valid input files in the folder.
	 */
	private void noInput(){
		if (frame != null){
			statusLabel.setText("I'm bored.. ");
			startButton.setEnabled(false);
			menuBar.setVisible(true);
		}
	}

	/**
	 * Disables all the components present in a file panel.
	 * @param panelName - the name of the panel to be disabled
	 */
	private void disableFilePanel(String panelName){
		String panel = panelName+"Panel";
		Component component = getComponentByName(panel);
		if (component != null){
			component.setEnabled(false);
			((JTextField)((JPanel)component).getComponent(0)).setEnabled(false);
			((JButton)((JPanel)component).getComponent(1)).setEnabled(false);
		}
	}

	/**
	 * Disable all the file panels with all the components present in a file panel.
	 * It is used when an incompatible version (in accordance with the script file) of the application is run.
	 */
	private void disableAllFilePanels(){
		String[] panels = new String[]{"Patients", "Events", "Prescriptions", "Measurements"};
		for (String panel : panels)
			disableFilePanel(panel);
	}

	/**
	 * Will add panel to the progress panel underneath the status panel
	 * @param panel - the progress panel to be added.
	 */
	public static void showProgress(JPanel panel){
		progressPanel.removeAll();
		progressPanel.add(statusPanel, BorderLayout.CENTER);
		progressPanel.add(panel, BorderLayout.SOUTH);
		progressPanel.revalidate();
	}

	/**
	 * Will display (if any) the post processing message that is part
	 * in the meta data part of the script.
	 */
	public static void showEndMessage(){
		if (Jerboa.getScriptParser().hasPostMessage()){
			StringBuilder message = new StringBuilder();
			message.append("<html>");
			message.append(Jerboa.getScriptParser().getPostMessage().trim());
			message.append("</html>");
			JOptionPane optionPane = new JOptionPane(new JLabel(message.toString(),JLabel.CENTER), JOptionPane.INFORMATION_MESSAGE);
			optionPane.setPreferredSize(new Dimension(400, (message.length() > 200 ? 120 : 80)));
			JDialog dialog = optionPane.createDialog("Message");
			dialog.setModal(true);
			FileUtilities.putIcon(dialog);
			dialog.setVisible(true);
		}
	}

	/**
	 * Will clear the progress panel and add a dummy panel to it.
	 */
	public static void hideProgress(){
		progressPanel.removeAll();
		progressPanel.add(statusPanel, BorderLayout.CENTER);
		JPanel dummyPanel = new JPanel();
		dummyPanel.setPreferredSize(new Dimension(FRAME_WIDTH, 20));
		dummyPanel.setMinimumSize(new Dimension(FRAME_WIDTH, 20));
		dummyPanel.setMaximumSize(new Dimension(Toolkit.getDefaultToolkit().getScreenSize().width, 20));
		progressPanel.add(dummyPanel, BorderLayout.SOUTH);
		progressPanel.revalidate();
	}

	/**
	 * Adds panel as a tab to the tabbedPane.
	 * @param panel - the panel to be added
	 */
	public static void addTab(JPanel panel){
		JerboaGUI.tabbedPane.add(panel);
		//JerboaGUI.tabbedPane.setSelectedIndex(JerboaGUI.tabbedPane.getTabCount()-1);
	}

	/**
	 * Adds panel as a tab in the tabbedPane.
	 * @param panel - the panel to be added
	 * @param name - the name of the panel
	 */
	public static void addTab(JPanel panel, String name){
		panel.setName(name);
		JerboaGUI.tabbedPane.add(panel);
		//JerboaGUI.tabbedPane.setSelectedIndex(JerboaGUI.tabbedPane.getTabCount()-1);
	}

	/**
	 * When start is pressed again, all tabs,
	 *  excluding the first one should be removed.
	 *  If only two tabs are present, the workflow and the memory usage,
	 *  then none is removed.
	 */
	public static void removeTabs(){
		if (JerboaGUI.tabbedPane.getTabCount() > 1){
			while (JerboaGUI.tabbedPane.getTabCount() > 1){
				if (JerboaGUI.tabbedPane.getTabCount() == 2 &&
						JerboaGUI.tabbedPane.getTitleAt(JerboaGUI.tabbedPane.getTabCount()-1).toLowerCase().contains("memory usage"))
					break;
			JerboaGUI.tabbedPane.removeTabAt(JerboaGUI.tabbedPane.getTabCount()-1);
			}
		}
	}

	/**
	 * Checks if path is present in the list of a drop down box.
	 * @param list - the dropdown list
	 * @param path - the path to be checked
	 * @return - true if path is found in list; false otherwise
	 */
	private boolean alreadyInList(JComboBox<String> list, String path){
		if (list != null)
			for (int i = 0; i < list.getItemCount(); i ++)
				if (list.getItemAt(i).equals(path))
					return true;
		return false;
	}

	/*-------------END OF APPLICATION STATE RELATED-----------*/


	/*------------------ MEMORY THREAD ---------------*/

	/**
	 * This thread updates the available memory value on the GUI.
	 * @author MG
	 */
	public class MemoryThread extends Thread{
		public void run(){
			while (!Thread.currentThread().isInterrupted()){
				try {
					if (!MemoryUtilities.noMemoryUsage(memoryValue)){
						memoryLabel.setText(MemoryUtilities.memory());
					}
					Thread.sleep(1000);
				} catch (Exception e) {
					e.printStackTrace();
				}
			}
		}
	}

	/*---------------END OF MEMORY THREAD-----------------*/

	/**
	 * Retrieve a component of the GUI by its name.
	 * @param name - name of the component to be retrieved
	 * @return - the component of interest
	 */
	public Component getComponentByName(String name) {
		if (componentMap != null &&
				componentMap.containsKey(name)) {
			return (Component) componentMap.get(name);
		}
		else
			return null;
	}

}
