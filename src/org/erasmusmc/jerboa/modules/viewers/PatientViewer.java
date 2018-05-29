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
 * $Rev:: 4753              $:  Revision of last commit                                   *
 * $Author:: root           $:  Author of last commit                                     *
 * $Date:: 					$:  Date and time (CET) of last commit						  *
 ******************************************************************************************/
package org.erasmusmc.jerboa.modules.viewers;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.Image;
import java.awt.Insets;
import java.awt.Paint;
import java.awt.Toolkit;
import java.io.File;
import java.net.URL;
import java.text.DateFormat;
import java.text.NumberFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.List;

import javax.swing.ImageIcon;
import javax.swing.JFrame;
import javax.swing.JPanel;

import org.erasmusmc.jerboa.Jerboa;
import org.erasmusmc.jerboa.config.FilePaths;
import org.erasmusmc.jerboa.dataClasses.*;
import org.erasmusmc.jerboa.gui.JerboaGUI;

import org.jfree.chart.ChartFactory;
import org.jfree.chart.ChartPanel;
import org.jfree.chart.JFreeChart;
import org.jfree.chart.axis.CategoryAxis;
import org.jfree.chart.axis.ValueAxis;
import org.jfree.chart.entity.CategoryItemEntity;
import org.jfree.chart.entity.EntityCollection;
import org.jfree.chart.event.ChartChangeEvent;
import org.jfree.chart.labels.CategoryItemLabelGenerator;
import org.jfree.chart.labels.CategoryToolTipGenerator;
import org.jfree.chart.labels.IntervalCategoryToolTipGenerator;
import org.jfree.chart.plot.CategoryPlot;
import org.jfree.chart.plot.IntervalMarker;
import org.jfree.chart.plot.Marker;
import org.jfree.chart.plot.PlotOrientation;
import org.jfree.chart.renderer.category.CategoryItemRendererState;
import org.jfree.chart.renderer.category.GanttRenderer;
import org.jfree.chart.renderer.category.StandardBarPainter;
import org.jfree.data.category.CategoryDataset;
import org.jfree.data.gantt.GanttCategoryDataset;
import org.jfree.data.gantt.SlidingGanttCategoryDataset;
import org.jfree.data.gantt.Task;
import org.jfree.data.gantt.TaskSeries;
import org.jfree.data.gantt.TaskSeriesCollection;
import org.jfree.data.time.SimpleTimePeriod;
import org.jfree.ui.Layer;
import org.jfree.ui.LengthAdjustmentType;
import org.jfree.ui.RectangleAnchor;
import org.jfree.ui.RectangleEdge;
import org.jfree.ui.TextAnchor;
import org.jfree.data.time.TimePeriod;


import org.erasmusmc.jerboa.utilities.*;

import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.KeyEvent;
import java.awt.event.KeyListener;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.geom.Rectangle2D;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.DefaultListCellRenderer;
import javax.swing.DefaultListModel;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JOptionPane;
import javax.swing.JScrollBar;
import javax.swing.JScrollPane;
import javax.swing.JTextField;
import javax.swing.SwingConstants;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;
import javax.swing.filechooser.FileNameExtensionFilter;

/**
 * This class contains all the methods used in viewing the history of a patient.
 * It makes use of a chart to plot the history of a patient across its recorded period.
 * The patient events, prescriptions and measurements (if any) are also displayed in the graph
 * respecting a certain color code denoted in the legend of the plot. Different time spans
 * are set depending on the cohort, exposure and population definition.
 * A patient can be viewed individually or searched for in a user selected file/folder.
 *
 * @author MG
 */
public class PatientViewer{

	//patient related
	private HashMap<String,Integer> patientIDs = new HashMap<String, Integer>();
	private List<Patient> patients;
	private static String fileName;

	//GUI related
	private JList<String>  list;
	private String title;
	private DefaultListModel<String> model;
	private JScrollPane listPane;
	public static JFrame frame;
	private JPanel mainPanel;
	private JPanel optionsPanel;
	private JPanel chartPanel;
	private JTextField searchField;

	/**
	 * Constructor generating a frame which will contain the list of patients from a chosen file.
	 * @param patients - the list of patients loaded for which the history is to be viewed.
	 * @param fileName - the name of the file containing patient objects
	 * @param title - the title of the frame
	 */
	public PatientViewer(final List<Patient> patients, String fileName, String title){

		PatientViewer.fileName = fileName;
		this.patients = patients;
		this.title = title;
		frame = new JFrame();
		frame.setLayout(new BorderLayout());
		if (InputFileUtilities.isLookUpsEmpty() &&
				fileName != null && !fileName.equals(""))
			InputFileUtilities.loadAllCodeLists(formLookUpPath(fileName));
		initGUI(null);
	}

	/**
	 * Initializes the necessary GUI components focusing on the patient with patientId.
	 * @param patientId - the patient to be first displayed; allowed null
	 */
	private void initGUI(String patientId){

		if (patientId != null && !patientId.equals("")){
			list = null;
			mainPanel.removeAll();
			optionsPanel.removeAll();
			frame.setVisible(true);
		}

		frame = new JFrame();

		mainPanel = new JPanel();
		mainPanel.setLayout(new BorderLayout());

		optionsPanel = new JPanel();
		optionsPanel.setLayout(new BoxLayout(optionsPanel, BoxLayout.X_AXIS));

		frame.setLayout(new BorderLayout());

		//initialize the lists that will contain the patients
		model = new DefaultListModel<String>();
		list = new JList<String>(model);
		patientIDs = new HashMap<String, Integer>(patients.size());
		listPane = new JScrollPane(list);

		//set a renderer in order to align strings
		DefaultListCellRenderer renderer =
				(DefaultListCellRenderer)list.getCellRenderer();
		renderer.setHorizontalAlignment(JLabel.RIGHT);

		//search field
		final JCheckBox groupByATC = new JCheckBox("Group by type");
		groupByATC.setSelected(true);
		searchField = new JTextField(" Search patient");
		final JButton searchButton = new JButton();
		try{
			URL url = Jerboa.class.getResource(FilePaths.SEARCH_ICON_PATH);
			Image img = Toolkit.getDefaultToolkit().getImage(url);
			searchButton.setIcon(new ImageIcon(img, null));
			searchButton.setMargin(new Insets(0, 0, 0, 0));
			searchButton.setBorder(null);
		}catch(Exception e){
			Logging.prepareOutputLog();
			Logging.add("Unable to retrieve search icon", Logging.ERROR, true);
			searchButton.setText("GO");
		}
		searchField.setFont(new Font("Arial",Font.ITALIC,14));
		searchField.setPreferredSize(new Dimension(40, 25));

		//upper panel with all the options
		optionsPanel.setBorder(BorderFactory.createEtchedBorder());
		optionsPanel.add(searchButton);
		optionsPanel.add(searchField, BorderLayout.WEST);
		optionsPanel.add(Box.createHorizontalGlue());
		optionsPanel.add(new JPanel());
		optionsPanel.add(groupByATC);

		//panel holding the chart
		chartPanel = new JPanel();
		chartPanel.setLayout(new BorderLayout());

		//add a header with the column names
		listPane.setBorder(BorderFactory.createTitledBorder("  ID   BirthDate  Gender  StartDate  EndDate"));

		mainPanel.add(listPane, BorderLayout.WEST);
		mainPanel.add(chartPanel, BorderLayout.CENTER);

		//EVENT CONTROLLERS

		//erase the search field if clicked
		searchField.addMouseListener(new MouseAdapter()  {
			public void mouseClicked(MouseEvent evt) {
				searchField.selectAll();
				searchField.setFont(new Font("Arial",Font.PLAIN,14));
			}
		});

		//add the search field <Enter> key functionality
		searchField.addKeyListener(new KeyListener() {
			@Override
			public void keyPressed(KeyEvent ke) {
				if(ke.getKeyCode() == KeyEvent.VK_ENTER){
					ke.consume();
					String ID = searchField.getText().trim();
					if (ID != "" && !ID.contains("Search")){
						searchPatient(ID);
					}
				}
			}
			@Override
			public void keyReleased(KeyEvent arg0) {}
			@Override
			public void keyTyped(KeyEvent arg0) {}
		});

		//add the search button functionality
		searchButton.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent arg0) {
				String ID = searchField.getText().trim();
				if (ID != "" && !ID.contains("Search")){
					searchPatient(ID);
				}
			}
		});

		//add the search field <Enter> key functionality
		searchButton.addKeyListener(new KeyListener() {
			@Override
			public void keyPressed(KeyEvent ke) {
				if(ke.getKeyCode() == KeyEvent.VK_ENTER){
					ke.consume();
					String ID = searchField.getText().trim();
					if (ID != "" && !ID.contains("Search")){
						searchPatient(ID);
					}
				}
			}
			@Override
			public void keyReleased(KeyEvent arg0) {}
			@Override
			public void keyTyped(KeyEvent arg0) {}
		});

		//add the double click event to view a patient
		list.addMouseListener(new MouseAdapter()  {
			@SuppressWarnings("unchecked")
			public void mouseClicked(MouseEvent evt) {
				list = (JList<String>)evt.getSource();
				if (evt.getClickCount() == 2) {
					int index = list.locationToIndex(evt.getPoint());
					if (patients != null)
						new PatientChart(patients.get(index),groupByATC.isSelected());
				}
			}
		});
		//add down/up arrow listener to refresh the chart
		list.addListSelectionListener(new ListSelectionListener() {
			@Override
			public void valueChanged(ListSelectionEvent e) {
				int index = list.getSelectedIndex();
				if (patients != null){
					list.setSelectedIndex(index < patients.size()
							? patients.size() : index+1);
				//avoid refreshing if mouse hovering/dragging...
				//				if (e.getValueIsAdjusting())  //stops the key up/key down events
					updateChartPanel(patients.get(list.getSelectedIndex()),groupByATC.isSelected());
				}
			}
		});
		//add enter event to open chart in separate frame
		list.addKeyListener(new KeyListener() {
			@Override
			public void keyPressed(KeyEvent ke) {
				if(ke.getKeyCode() == KeyEvent.VK_ENTER){
					ke.consume();
					int index = list.getSelectedIndex();
					new PatientChart(patients.get(index), groupByATC.isSelected());
				}
			}
			@Override
			public void keyReleased(KeyEvent arg0) {}
			@Override
			public void keyTyped(KeyEvent arg0) {}
		});
		//add grouping by ATC event
		groupByATC.addActionListener(new ActionListener(){
			public void actionPerformed(ActionEvent e) {
				updateChartPanel(patients.get(list.getSelectedIndex()),groupByATC.isSelected());
			}});

		//END OF EVENT CONTROLLERS

		//populate list depending on the size of the population
		if (patients != null && patients.size() > 0){

			//check if the number of patients in the list is not bigger than the list limit
			int limit = 40000;
			if (patients.size() > limit){
				//map IDs first
				for (int i = 0; i < patients.size(); i++)
					patientIDs.put(patients.get(i).ID, i);
				//see at what index we start
				int index = (patientId == null || patientId.equals("") ? 0 : patientIDs.get(patientId));
				int start = index - limit/2 < 0 ? 0 : (int)(index - limit/2);
				//populate list
				for (int j = start; j < start + limit; j++)
					model.addElement(patients.get(j).toStringAligned());
			}else{
				for (int i = 0; i < patients.size(); i++){
					patientIDs.put(patients.get(i).ID, i);
					model.addElement(patients.get(i).toStringAligned());
				}
			}
		}
		list.revalidate();

		//see if we have to display results of search
		if (patientId != null && !patientId.equals("")){
			Integer index;
			if ((index = patientIDs.get(patientId)) != null){
				list.setSelectedIndex(index);
				list.ensureIndexIsVisible(index);
			}
			//or just select the first element in the list
		}else{
			list.setSelectedIndex(0);
		}

		//add components to the GUI and display frame
		frame.add(optionsPanel, BorderLayout.NORTH);
		frame.add(mainPanel, BorderLayout.CENTER);
		URL url = Jerboa.class.getResource(FilePaths.ICON_PATH);
		Image img = Toolkit.getDefaultToolkit().getImage(url);
		frame.setIconImage(img);
		frame.pack();
		frame.setLocationRelativeTo(JerboaGUI.frame);
		frame.setTitle(this.title != null ? title : "Patient Viewer");
		frame.revalidate();
		frame.setVisible(true);
	}

	/**
	 * Searches for the patient with its identifier ID and sets the list entry on that patient if found.
	 * The method checks if the patient is located in the current list and displays its data.
	 * If not found, a dialog is shown letting the user choose his subsequent actions.
	 * @param ID - the ID of the patient to be searched for; null not allowed
	 */
	public void searchPatient(String ID){

		if (patientIDs.get(ID) != null){
			list.setSelectedIndex(patientIDs.get(ID));
			list.ensureIndexIsVisible(list.getSelectedIndex());
		}else{
			String text = "<html><div style=\"text-align: center;\">No patient with ID "+ID+" " +
					"exists in the current list. Search in folder?</html>";
			try {
				patientNotFound(ID, PatientViewer.fileName, text);
			} catch (InterruptedException e) {
				e.printStackTrace();
			}
		}
	}

	/**
	 * Allows the user to choose a file or a folder in which patients are located.
	 * @param patientId - the id of the patient the user wishes to search for
	 * @param fileName - the name of the file in which the current patient list is formed from
	 * @param text - the text to be displayed in the confirmation dialog
	 * @throws InterruptedException - if thread is stopped brutally
	 */
	private void patientNotFound(String patientId, String fileName, String text) throws InterruptedException{

		int choice = JOptionPane.showConfirmDialog(frame, text, "Info", JOptionPane.YES_NO_OPTION);
		if (choice == JOptionPane.YES_OPTION){
			//retrieve the folder path
			if (fileName == null || fileName.equals("")){
				fileName = FileUtilities.openFileWithDialog(frame, ".", true,
						new FileNameExtensionFilter("Patient Objects File", "pof"));
			}else if (!new File(fileName).isDirectory()){
				String[] path = fileName.split("/");
				fileName = fileName.substring(0, fileName.length()-path[path.length-1].length());
			}

			//update the list
			SearchPatient sp = new SearchPatient(patientId, fileName);
			sp.start();
		}
	}

	/**
	 * Refresh of the chart displayed on the chart panel.
	 * @param patient - the patient which is to be viewed next
	 * @param groupByCode - true if the history of the patient should be grouped by code
	 */
	private void updateChartPanel(Patient patient, boolean groupByCode){
		//remove all components from panel
		chartPanel.removeAll();
		//create a new chart if the patient has prescriptions
		chartPanel.add(new PatientChart.DataPanel(patient, groupByCode), BorderLayout.CENTER);
		chartPanel.revalidate();
	//	mainPanel.repaint();
	}

	/**
	 * Forms the path to the look-up tables folder from an existing path
	 * to patient objects. This is achieved based on the folder chosen by the user.
	 * @param fileName - the path to the patients object
	 * @return - a newly formed path to the look-up tables
	 */
	public String formLookUpPath(String fileName){
		String lookUpPath = "";
		if (fileName != null && !fileName.equals("")){
			String[] folders = null;
			if (fileName.contains("/"))
				folders = fileName.split("/");
			else
				folders = fileName.split("\\");
			if (folders != null && folders.length > 0){
				for (int i = 0; i < (new File(fileName).isDirectory() ?
						folders.length - 1 : folders.length -2); i ++)
					lookUpPath = lookUpPath.concat(folders[i]).concat("/");
				lookUpPath = lookUpPath.concat("lookups").concat("/");
			}
		}

		return lookUpPath;

	}

	/**
	 * This inner class launches the patient searcher.
	 * It is used to correctly display the progress bar.
	 *
	 * @author MG
	 *
	 */
	class SearchPatient extends Thread{

		//attributes
		private String patientId;
		private String fileName;
		private List<Patient> patientList;

		/**
		 * Constructor receiving a patient identifier and a file in which to search for it.
		 * @param patientId - the ID of the patient of interest
		 * @param fileName - the file which should contain the patient of interest
		 */
		public SearchPatient(String patientId, String fileName){
			this.patientId = patientId;
			this.fileName = fileName;
		}

		public void run(){
			//update the patient list
			PatientUtilities po = new PatientUtilities();
			patients = po.loadPatientsFromFolder(patientId.trim(),fileName, true);
			if (patients != null && patients.size() > 0){
				//rebuild the interface
				try{
					InputFileUtilities.loadAllCodeLists(formLookUpPath(fileName));
					if (InputFileUtilities.isLookUpsEmpty())
						JOptionPane.showMessageDialog(frame, "Unable to load look-up tables for code mapping", "Error", JOptionPane.OK_OPTION);
					else
						initGUI(patientId);
				}catch(Exception e){
					Logging.outputStackTrace(e);
				}
			}else{
				JOptionPane.showMessageDialog(frame, "Patient with ID "+patientId+" not found", "Error", JOptionPane.OK_OPTION);
			}
		}

		//GETTER
		public List<Patient> getPatientList() {
			return patientList;
		}
	}

	//GETTER/SETTER PARENT CLASS
	public String getTitle() {
		return title;
	}

	public void setTitle(String title) {
		this.title = title;
	}

}

/**
 * This inner class contains all the methods used to create a chart containing
 * the history of a patient that is passed as an argument.
 *
 * @author MG
 *
 */
class PatientChart{

	//tracking the time range that should be displayed
	private static Date[] patientRange = null;
	private static Date[] cohortRange = null;
	private static Date[] populationRange = null;
	private static Date[] extremeDates = null;

	/**
	 * Constructor receiving the patient object to display and a flag if
	 * the information should be displayed grouped by code.
	 * @param patient - the patient to be displayed
	 * @param groupByType - true if the history of the patient should be displayed grouped by code; false otherwise
	 */
	public PatientChart(Patient patient, boolean groupByType) {
		super();
		if (patient != null){

			try{
				//create the chart and add it to the frame
				JPanel chartPanel = new DataPanel(patient, groupByType);
				chartPanel.setPreferredSize(new Dimension(550, 300));
				JFrame frame = new JFrame("Episodes for patient ID "+patient.ID);
				frame.setContentPane(chartPanel);
				URL url = Jerboa.class.getResource(FilePaths.ICON_PATH);
				Image img = Toolkit.getDefaultToolkit().getImage(url);
				frame.setIconImage(img);
				frame.setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
				frame.setLocation(PatientViewer.frame.getLocation());
				frame.pack();
				frame.setVisible(true);
			}catch(Exception e){
				System.out.println("An error occurred whithin the chart.");
				Logging.outputStackTrace(e);
			}
		}
	}

	/**
	 * Nested static class creating and populating the JPanel holding the patient data.
	 * It is declared static in order to properly use the scrolling feature.
	 *
	 * @author MG
	 *
	 */
	@SuppressWarnings("serial")
	static class DataPanel extends JPanel implements ChangeListener {

		//attributes
		JScrollBar scroller;
		static SlidingGanttCategoryDataset dataset;
		static final int MAX_ENTRIES_TO_DISPLAY = 10;

		//defines the offset in days in order to represent an event or measurement
		static final int DATE_OFFSET = 15;

		//chart range
		static long minDate;
		static long maxDate;

		//stats of the datasets
		static int entriesGrouped = 0 ;
		static int entriesUngrouped = 0;
		static boolean isScrolled;

		//task indexing
		private static int taskIndex;
		private static int refRow;

		/**
		 * Constructor receiving the patient object to display and a flag if
		 * the information should be displayed grouped by code.
		 * @param patient - the patient to be displayed
		 * @param groupByType - true if the history of the patient should be displayed grouped by code; false otherwise
		 */
		public DataPanel(Patient patient, boolean groupByType) {
			super(new BorderLayout());

			//reset task index and reference
			taskIndex = 0;
			refRow = -1;

			isScrolled = false;

			//compute marker ranges
			extremeDates = DataPanel.computeDateRange(patient);
			patientRange = DataPanel.computeRange(patient.startDate, patient.endDate);
			if (patient.inPopulation)
				populationRange = DataPanel.computeRange(patient.populationStartDate, patient.populationEndDate);
			if (patient.inCohort)
				cohortRange = DataPanel.computeRange(patient.cohortStartDate, patient.cohortEndDate);

			// get data for diagrams
			JFreeChart chart = createChart(patient, groupByType);

			//initialize the panel holding the chart
			MyChartPanel panel = new MyChartPanel(chart);
			panel.setPreferredSize(new Dimension(500, 270));

			panel.setMouseWheelEnabled(true);

			//add the scroll bar if normal view (not grouped)
			JPanel scrollPanel = new JPanel(new BorderLayout());
			add(panel);
			if (!groupByType && entriesUngrouped > MAX_ENTRIES_TO_DISPLAY){
				this.scroller = new JScrollBar(SwingConstants.VERTICAL, 0, MAX_ENTRIES_TO_DISPLAY, 0, entriesUngrouped);
				this.scroller.getModel().addChangeListener(this);
				scrollPanel.add(this.scroller);
				add(scrollPanel, BorderLayout.EAST);
			}
		}

		/**
		 * Creates a chart in order to visualize patient history.
		 * @param dataset  the plotting coordinates representing the patient history.
		 * @param grouped - true if wnated to group the episodes from the patient history by code
		 * @return a patient viewer chart.
		 */
		private static JFreeChart createChart(Patient patient, boolean grouped) {

			JFreeChart chart = ChartFactory.createGanttChart(
					null, 				 // chart title
					"Episodes",    		 // domain axis label
					"Date",              // range axis label
					grouped ? createDatasetGroupedByCode(patient) :
						(dataset = new MySlidingGanttCategoryDataset(createDatasetUngrouped(patient),
								0, MAX_ENTRIES_TO_DISPLAY)),// data
								true,                // include legend
								true,                // tooltips
								false                // urls
					);
			CategoryPlot plot = (CategoryPlot) chart.getPlot();
			plot.getDomainAxis().setMaximumCategoryLabelWidthRatio(10.0f);

			MyGanttRenderer renderer = new MyGanttRenderer();
			MyIntervalCategoryGanttToolTipGenerator tooltipGenerator =
					new MyIntervalCategoryGanttToolTipGenerator("{3} - {4}", new SimpleDateFormat("MMM d, yyyy"));
			renderer.setBaseToolTipGenerator(tooltipGenerator);
			renderer.setDrawBarOutline(true);

			renderer.setSeriesPaint(0, Color.red);
			renderer.setSeriesPaint(1, Color.blue);
			renderer.setSeriesPaint(2, Color.orange);

			renderer.setBarPainter(new StandardBarPainter());
			renderer.setShadowVisible(false);

			plot.setRenderer(renderer);

			//set the range markers
			setRangeMarker(plot, patientRange, new Color(255, 225, 255), "Patient time", RectangleAnchor.BOTTOM_LEFT);
			if (patient.inPopulation)
			  setRangeMarker(plot, populationRange, new Color(135, 206, 250), "Population definition", RectangleAnchor.TOP_RIGHT);
			if (patient.inCohort)
			setRangeMarker(plot, cohortRange, new Color(140, 255, 140), "Cohort", RectangleAnchor.TOP_LEFT);

			//do not show crosshair
			plot.setRangeCrosshairVisible(!grouped);
			plot.setDomainCrosshairVisible(!grouped);
			plot.setRangePannable(true);

			return chart;

		}

		/**
		 * Creates the dataset that is used to display the patient history in chronological order
		 * without grouping the episodes by their code.
		 * @param patient - the patient for which the data is to be displayed
		 * @return - the dataset populated with the patient history.
		 */
		private static TaskSeriesCollection createDatasetUngrouped(Patient patient) {

			if (patient != null){
				TaskSeriesCollection collection = new TaskSeriesCollection();
				MyTask task;
				int loop = 0;
				int taskIndex = 0;

				//retrieve patient time
				int[] dateStart = DateUtilities.splitDate(DateUtilities.daysToDate(patient.startDate), DateUtilities.DATE_ON_YYYYMMDD);
				int[] dateEnd = DateUtilities.splitDate(DateUtilities.daysToDate(patient.endDate), DateUtilities.DATE_ON_YYYYMMDD);

				//go through prescriptions if there are any
				MyTaskSeries prescriptions = new MyTaskSeries("Prescriptions");
				if (patient.hasPrescriptions()){

					//sort prescriptions by date
					Collections.sort(patient.getPrescriptions());

					//go through the prescriptions
					for (Prescription pr: patient.getPrescriptions()){
						loop ++;
						dateStart = DateUtilities.splitDate(DateUtilities.daysToDate(pr.date), DateUtilities.DATE_ON_YYYYMMDD);
						dateEnd = DateUtilities.splitDate(DateUtilities.daysToDate(pr.date + pr.getDuration()), DateUtilities.DATE_ON_YYYYMMDD);

						//add them to the series
						task = new MyTask(InputFileUtilities.getPrescriptionAtcs().get(pr.type)+" #"+loop,
								null, date(dateStart[2], dateStart[1], dateStart[0]), date(dateEnd[2], dateEnd[1], dateEnd[0]));
						task.setIndex(taskIndex ++);
						prescriptions.add(task);
					}
				}
				collection.add(prescriptions);

				//go through events if there are any
				MyTaskSeries events = new MyTaskSeries("Events");
				taskIndex = 0;
				if (patient.hasEvents()){
					Collections.sort(patient.getEvents());
					loop = 0;
					//go through the events
					for (Event ev: patient.getEvents()){
						loop ++;
						dateStart = DateUtilities.splitDate(DateUtilities.daysToDate(ev.date), DateUtilities.DATE_ON_YYYYMMDD);
						//add them to the series
						task = new MyTask(InputFileUtilities.getEventTypes().get(ev.type)+" #"+loop,
								("Code:"+(ev.getCode() != null && !ev.getCode().equals("NO CODE") ? ev.getCode() : "")),
								date(dateStart[2], dateStart[1], dateStart[0]), date(dateStart[2] + DATE_OFFSET, dateStart[1], dateStart[0]));
						task.setIndex(taskIndex ++);
						events.add(task);
					}
				}
				collection.add(events);

				//go through measurements if there are any
				MyTaskSeries measurements = new MyTaskSeries("Measurements");
				taskIndex = 0;
				if (patient.hasMeasurements()){
					Collections.sort(patient.getMeasurements());
					loop = 0;
					//go through the measurements
					for (Measurement me: patient.getMeasurements()){
						loop ++;
						dateStart = DateUtilities.splitDate(DateUtilities.daysToDate(me.date), DateUtilities.DATE_ON_YYYYMMDD);
						//add them to the series
						task = new MyTask(InputFileUtilities.getMeasurementTypes().get(me.type)+" #"+loop,
								("Value:"+(me.getValue() != null && !me.getValue().equals("") ? me.getValue() : "")),
								date(dateStart[2], dateStart[1], dateStart[0]), date(dateStart[2] + DATE_OFFSET, dateStart[1], dateStart[0]));
						task.setIndex(taskIndex ++);
						measurements.add(task);
					}
				}
				collection.add(measurements);

				//retrieve column count of the whole dataset
				entriesUngrouped = collection.getColumnCount();

				return collection;

			}//patient is null

			return null;
		}

		/**
		 * Creates the dataset that is used to display the patient history
		 * grouped by types of events/prescriptions/measurements.
		 * @param patient - the patient for which the data is to be displayed
		 * @return - the dataset as plotting coordinates populated with the patient history.
		 */
		private static TaskSeriesCollection createDatasetGroupedByCode(Patient patient) {

			if (patient != null){
				TaskSeriesCollection collection = new TaskSeriesCollection();
				//create a task the length of patient time
				List<MyTask> subTasks = new ArrayList<MyTask>();
				int refType = -1;
				int taskIndex = 0;

				//keep track of the interval of prescription (for the tooltips)
				int minDate = Integer.MAX_VALUE;
				int maxDate = Integer.MIN_VALUE;

				int[] episodeStart = DateUtilities.splitDate(DateUtilities.daysToDate(patient.startDate), DateUtilities.DATE_ON_YYYYMMDD);
				int[] episodeEnd = DateUtilities.splitDate(DateUtilities.daysToDate(patient.endDate), DateUtilities.DATE_ON_YYYYMMDD);;

				//PRESCRIPTIONS
				MyTaskSeries series = new MyTaskSeries("Prescriptions");
				if (patient.hasPrescriptions()){

					subTasks = new ArrayList<MyTask>();

					//sort by ATC (string version)
					Collections.sort(patient.getPrescriptions(),
							new Episode.CompareType());

					//initialize reference
					refType = patient.getPrescriptions().get(0).type;
					//add the prescriptions to the tasks
					for (Prescription pr: patient.getPrescriptions()){
						if (pr.type != refType){
							//add the task with subtasks to series
							series.add(addSubtasksToSeries(InputFileUtilities.prescriptionATCS.get(refType).toString(),
									taskIndex ++, null,	subTasks, minDate, maxDate));
							//clear list
							subTasks = new ArrayList<MyTask>();
							//update reference
							refType = pr.type;
							//clear main task range
							minDate = pr.date;
							maxDate = pr.date+pr.getDuration();
						}else{
							//update the min and max date for the tooltips
							minDate = minDate > pr.date ? pr.date : minDate;
							maxDate = maxDate < pr.date+pr.getDuration() ? pr.date+pr.getDuration() : maxDate;
						}

						//get prescription start date and end date
						episodeStart = DateUtilities.splitDate(DateUtilities.daysToDate(pr.date), DateUtilities.DATE_ON_YYYYMMDD);
						episodeEnd = DateUtilities.splitDate(DateUtilities.daysToDate(pr.date + pr.getDuration()), DateUtilities.DATE_ON_YYYYMMDD);
						//add it as subtask
						subTasks.add(new MyTask(InputFileUtilities.getPrescriptionAtcs().get(pr.type).toString(), null,
								new SimpleTimePeriod(date(episodeStart[2], episodeStart[1], episodeStart[0]),
										date(episodeEnd[2], episodeEnd[1], episodeEnd[0]))));
					}
					//add the last ones
					if (subTasks != null && subTasks.size() > 0){
						series.add(addSubtasksToSeries(InputFileUtilities.prescriptionATCS.get(refType).toString(),
								taskIndex, null, subTasks, minDate, maxDate));
					}
				}
				collection.add(series);

				//EVENTS
				series = new MyTaskSeries("Events");
				taskIndex = 0;
				if (patient.hasEvents()){
					//sort by type (string version)
					Collections.sort(patient.getEvents(),
							new Episode.CompareType());

					//create a task the length of patient time
					subTasks = new ArrayList<MyTask>();

					//initialize reference
					refType = patient.getEvents().get(0).type;
					String code = patient.getEvents().get(0).getCode();
					//add the events to the tasks
					for (Event ev: patient.getEvents()){
						if (ev.type != refType){
							//add the task with subtasks to series
							series.add(addSubtasksToSeries(InputFileUtilities.eventTypes.get(refType).toString(), taskIndex ++,
									("Code:"+(code != null && !code.equals("NO CODE") ? code : "")),
									subTasks, minDate, maxDate));
							//clear list
							subTasks = new ArrayList<MyTask>();
							//update reference
							refType = ev.type;
							//clear main task range
							minDate = ev.date;
							maxDate = ev.date + DATE_OFFSET;
						}else{
							//update the min and max date for the tool tips
							minDate = minDate > ev.date ? ev.date : minDate;
							maxDate = maxDate < ev.date + DATE_OFFSET ? ev.date + DATE_OFFSET : maxDate;
						}

						code = ev.getCode();
						//get event start date and end date
						episodeStart = DateUtilities.splitDate(DateUtilities.daysToDate(ev.date), DateUtilities.DATE_ON_YYYYMMDD);
						episodeEnd = DateUtilities.splitDate(DateUtilities.daysToDate(ev.date + DATE_OFFSET), DateUtilities.DATE_ON_YYYYMMDD);

						//add it as subtask
						subTasks.add(new MyTask(InputFileUtilities.getEventTypes().get(ev.type).toString(),
								("Code:"+(code != null && !code.equals("NO CODE") ? code : "")),
								new SimpleTimePeriod(date(episodeStart[2], episodeStart[1], episodeStart[0]),
										date(episodeEnd[2], episodeEnd[1], episodeEnd[0]))));
					}
					//add the last ones
					if (subTasks != null && subTasks.size() > 0){
						series.add(addSubtasksToSeries(InputFileUtilities.eventTypes.get(refType).toString(), taskIndex,
								("Code:"+(code != null && !code.equals("NO CODE") ? code : "")),
								subTasks, minDate, maxDate));
					}
				}
				collection.add(series);

				//MEASUREMENTS
				series = new MyTaskSeries("Measurements");
				taskIndex = 0;
				if (patient.hasMeasurements()){
					//sort by type (string version)
					Collections.sort(patient.getMeasurements(),
							new Episode.CompareType());

					//create a task the length of patient time
					subTasks = new ArrayList<MyTask>();

					//initialize reference
					refType = patient.getMeasurements().get(0).type;
					String value = patient.getMeasurements().get(0).getValue();
					//add the measurements to the tasks
					for (Measurement me: patient.getMeasurements()){
						if (me.type != refType){
							//add the task with subtasks to series
							series.add(addSubtasksToSeries( InputFileUtilities.measurementTypes.get(refType).toString(), taskIndex ++,
									("Value:"+(value != null && !value.equals("") ? value : "")),
									subTasks, minDate, maxDate));
							//clear list
							subTasks = new ArrayList<MyTask>();
							//update reference
							refType = me.type;
							//clear main task range
							minDate = me.date;
							maxDate = me.date + DATE_OFFSET;
						}else{
							//update the min and max date for the tooltips
							minDate = minDate > me.date ? me.date : minDate;
							maxDate = maxDate < me.date + DATE_OFFSET ? me.date + DATE_OFFSET : maxDate;
						}

						value = me.getValue();
						//get measurement start date and end date
						episodeStart = DateUtilities.splitDate(DateUtilities.daysToDate(me.date), DateUtilities.DATE_ON_YYYYMMDD);
						episodeEnd = DateUtilities.splitDate(DateUtilities.daysToDate(me.date + DATE_OFFSET), DateUtilities.DATE_ON_YYYYMMDD);
						//add it as subtask
						subTasks.add(new MyTask(InputFileUtilities.getMeasurementTypes().get(me.type).toString(),
								("Value:"+(value != null && !value.equals("") ? value : "")),
								new SimpleTimePeriod(date(episodeStart[2], episodeStart[1], episodeStart[0]),
										date(episodeEnd[2], episodeEnd[1], episodeEnd[0]))));
					}
					//add the last ones
					if (subTasks != null && subTasks.size() > 0){
						series.add(addSubtasksToSeries(InputFileUtilities.measurementTypes.get(refType).toString(), taskIndex,
								("Value:"+(value != null && !value.equals("") ? value : "")),
								subTasks, minDate, maxDate));
					}
				}
				collection.add(series);

				//retrieve dataset column count
				entriesGrouped = collection.getColumnCount();

				return collection;
			}//patient is null

			return null;

		}

		/**
		 * Will add to a task series a main task that also contains subtasks.
		 * It is used for the representation of the patient history episodes grouped
		 * by the same code.
		 * @param typeOfInterest - the type of the episode concerned
		 * @param taskIndex - the index of the task in the series
		 * @param value - the value defining the task
		 * @param subTasks - the list of sub tasks to be added to the main task
		 * @param minDate - the start date of the main task (for the tool tip)
		 * @param maxDate - the end date of the main task (for the tool tip)
		 * @return - a Task object ready to be added to the series
		 */
		private static Task addSubtasksToSeries(String typeOfInterest, int taskIndex, String value, List<MyTask> subTasks, int minDate, int maxDate){
			int[] start = DateUtilities.splitDate(DateUtilities.daysToDate(minDate), DateUtilities.DATE_ON_YYYYMMDD);
			int[] end = DateUtilities.splitDate(DateUtilities.daysToDate(maxDate), DateUtilities.DATE_ON_YYYYMMDD);
			MyTask task = new MyTask(typeOfInterest, value, new SimpleTimePeriod(date(start[2], start[1], start[0]), date(end[2], end[1], end[0])));
			task.setIndex(taskIndex);
			for (MyTask subTask : subTasks)
				task.addSubtask(subTask);

			return task;
		}

		/**
		 * Utility method for creating Date objects.
		 * @param day - the date.
		 * @param month - the month.
		 * @param year - the year.
		 * @return - a date object.
		 */
		private static Date date(int day, int month, int year) {
			Calendar calendar = Calendar.getInstance();
			calendar.set(year, month-1, day); //month - 1 due to the java calendar
			Date result = calendar.getTime();

			return result;
		}

		/**
		 * Retrieves the time interval the patient was registered in the system.
		 * @param patient - the patient in question
		 */
		private static Date[] computeRange(int dateStart, int dateEnd){

			int[] start = DateUtilities.splitDate(DateUtilities.daysToDate(dateStart), DateUtilities.DATE_ON_YYYYMMDD);
			int[] end = DateUtilities.splitDate(DateUtilities.daysToDate(dateEnd), DateUtilities.DATE_ON_YYYYMMDD);

			//retrieve start and end date for the patient
			if (start!=null && end!=null){
				Date[] range = new Date[2];
				range[0] = date(start[2], start[1], start[0]);
				range[1] = date(end[2], end[1], end[0]);

				return range;
			}
			else {
				return null;
			}
		}

		/**
		 * Computes the date extremes for a patient based on all the history of the patient.
		 * It is used in order to properly initialize the chart with a time span
		 * large enough to allow the visualization of all the episodes in the patient history.
		 * @param patient - the patient in question
		 * @return - a pair of date objects representing the borders of the time interval
		 */
		private static Date[] computeDateRange(Patient patient){
			//keep track of the date extremes for the chart range
			int min = patient.startDate;
			int max = patient.endDate;

			if (patient.hasPrescriptions()){

				Collections.sort(patient.getPrescriptions());

				Prescription firstPrescription = patient.getPrescriptions().get(0);
				Prescription lastPrescription = patient.getPrescriptions().get(patient.getPrescriptions().size() - 1);

				//update date extremes
				min = min > firstPrescription.date ? firstPrescription.date : min;
				max = max < lastPrescription.date ? lastPrescription.date : max;
			}

			//sort events by date if any
			if (patient.hasEvents()){

				//sort by date
				Collections.sort(patient.getEvents());

				Event firstEvent = patient.getEvents().get(0);
				Event lastEvent = patient.getEvents().get(patient.getEvents().size() - 1);

				//update date extremes
				min = min > firstEvent.date ? firstEvent.date : min;
				max = max < lastEvent.date ? lastEvent.date : max;
			}
			//sort measurements if any
			if (patient.hasMeasurements()){

				//sort by date
				Collections.sort(patient.getMeasurements());

				Measurement firstMeasurement = patient.getMeasurements().get(0);
				Measurement lastMeasurement = patient.getMeasurements().get(patient.getMeasurements().size() - 1);

				//update date extremes
				min = min > firstMeasurement.date ? firstMeasurement.date : min;
				max = max < lastMeasurement.date ? lastMeasurement.date : max;
			}

			//set the extremes of the date for the chart
			Date[] dates = new Date[2];
			int[] date = DateUtilities.splitDate(DateUtilities.daysToDate(min), DateUtilities.DATE_ON_YYYYMMDD);
			dates[0] = date(date[2], date[1], date[0]);
			date = DateUtilities.splitDate(DateUtilities.daysToDate(max), DateUtilities.DATE_ON_YYYYMMDD);
			dates[1] = date(date[2], date[1], date[0]);

			return dates;
		}

		/**
		 * Adds a range marker to plot depending on the pair of dates passed as parameter.
		 * @param plot - the plot onto which the marker is to be added
		 * @param dates - an array of dates containing the range extremes
		 * @param color - the color of the range marker
		 * @param label - the label of the range
		 * @param labelAnchor - the position of the label versus the range
		 */
		private static void setRangeMarker(CategoryPlot plot, Date[] dates, Color color, String label, RectangleAnchor labelAnchor){
			if (dates != null){
				plot.getRangeAxis().setRange(extremeDates[0].getTime(), extremeDates[1].getTime());
				Marker marker = new IntervalMarker(dates[0].getTime(), dates[1].getTime());
				marker.setLabelOffsetType(LengthAdjustmentType.EXPAND);
				marker.setPaint(color);
				marker.setLabel(label);
				marker.setLabelFont(new Font("SansSerif", Font.PLAIN, 11));
				marker.setLabelAnchor(labelAnchor);
				marker.setLabelTextAnchor(TextAnchor.TOP_CENTER);
				plot.addRangeMarker(marker, Layer.BACKGROUND);
			}
		}

		/**
		 * Allows scrolling into the chart panel and updates
		 * the indexes of the current position.
		 * @param arg0 - the change event determining the scroll
		 */
		@Override
		public void stateChanged(ChangeEvent arg0) {
			isScrolled = true;
			DataPanel.dataset.setFirstCategoryIndex(this.scroller.getValue());
			DataPanel.taskIndex = this.scroller.getValue();
		}

		//-------------------------------OVERRIDEN FOR TOOL TIPS AND TASKS---------------------------//

		/**
		 * Customized Task class with extra two attributes
		 * allowing to pass meta data to the tool tip and providing
		 * a task with an index.
		 *
		 * @author MG
		 *
		 */
		static class MyTask extends Task{

			/** The task value. */
			private String value = null;
			private int index;

			//CONSTRUCTORS
			public MyTask(String description, TimePeriod duration) {
				super(description,duration);
			}

			public MyTask(String description, String value, TimePeriod duration) {
				super(description,duration);
				this.value = value;
				this.index = 0;
			}

			public MyTask(String description, String value, Date start, Date end) {
				super(description,start,end);
				this.value = value;
			}

			//GETTER
			public String getValue() {
				return this.value;
			}

			public void setValue(String value) {
				this.value = value;
			}

			public int getIndex() {
				return index;
			}

			public void setIndex(int index) {
				this.index = index;
			}
		}

		/**
		 * Customized ChartPanel class in order to reset the task index
		 * as the chart changes (zoom, etc).
		 *
		 * @author MG
		 *
		 */
		class MyChartPanel extends ChartPanel{

			public MyChartPanel(JFreeChart chart){
				super(chart);
			}

			@Override
			public void chartChanged(ChartChangeEvent arg0) {
				if (!isScrolled){
					taskIndex = 0;
					refRow = -1;
				}
				super.chartChanged(arg0);
			}
		}

		/**
		 * Customized TaskSeries class which allows the user to
		 * get a task by an index.
		 *
		 * @author MG
		 *
		 */
		static class MyTaskSeries extends TaskSeries {

			public MyTaskSeries(String name){
				super(name);
			}

			public MyTask getTask(int index){
				if (this != null && this.getItemCount() > 0){
					for (int i = 0; i < this.getItemCount(); i ++){
						MyTask task = (MyTask)this.get(i);
						if (task.getIndex() == index)
							return task;
					}
				}

				return null;
			}
		}

		/**
		 * Customized tool tip generator allowing user
		 * specified information in the label of the tool tip.
		 * @author MG
		 *
		 */
		static class MyIntervalCategoryGanttToolTipGenerator extends IntervalCategoryToolTipGenerator {

			private static int subTaskIndex = 0;
			private static int refSubTask = 0;

			//CONSTRUCTORS
			public MyIntervalCategoryGanttToolTipGenerator(){
				super();
				subTaskIndex = 0;
			}

			public MyIntervalCategoryGanttToolTipGenerator(String labelFormat, NumberFormat format){
				super(labelFormat, format);
				subTaskIndex = 0;
			}

			public MyIntervalCategoryGanttToolTipGenerator(String labelFormat, DateFormat format){
				super(labelFormat, format);
				subTaskIndex = 0;
			}

			/**
			 * Creates the array of items that can be passed to the
			 * MessageFormat class for creating customized tool tip labels.
			 * @param dataset  the dataset; null not allowed
			 * @param row - the row index (zero-based).
			 * @param column - the column index (zero-based).
			 * @return the items; never null
			 */
			protected Object[] createItemArray(CategoryDataset dataset, int row, int column){

				//true if we are on the same row in the chart
				boolean onSameRow = false;

				//update row reference and reset task index if necessary
				if (refRow != row){
					refRow = row;
					taskIndex = 0;
					//still on the same row
				}else
					onSameRow = true;

				//reset scroll flag
				isScrolled = false;

				//initialize array and put basic task info in the first elements
				Object[] result = new Object[9];
				result[0] = dataset.getRowKey(row).toString();
				result[1] = dataset.getColumnKey(column).toString();
				Number number = dataset.getValue(row, column);
				if (getNumberFormat() != null){
					result[2] = getNumberFormat().format(number);
				}else if (getDateFormat() != null){
					result[2] = getDateFormat().format(number);
				}

				//put no data by default in the tool tip info elements
				result[3] = "NO ";
				result[4] = "DATA";

				//initialize data depending on the class
				TaskSeriesCollection data = null;
				MyTask task = null;

				//not grouped by code
				if (dataset instanceof MySlidingGanttCategoryDataset){
					try{
						data = ((MySlidingGanttCategoryDataset) dataset).getDataset();
						task = ((MyTaskSeries)data.getSeries(row)).getTask(taskIndex ++);
					}catch(ClassCastException e){
						e.printStackTrace();
					}
				}

				//grouped by code - tasks with multiple subtasks
				if (dataset instanceof TaskSeriesCollection){

					//check if needed to update the taskIndex
					if (refSubTask >= subTaskIndex && onSameRow){
						taskIndex ++;
					}

					try{
						//get the task in question
						data = (TaskSeriesCollection) dataset;
						task = ((MyTaskSeries)data.getSeries(row)).getTask(taskIndex);
						//check for subtasks
						if (task.getSubtaskCount() > subTaskIndex){
							//and get the one in question
							task = (MyTask)task.getSubtask(subTaskIndex);
						}

						//update subtask reference
						refSubTask = subTaskIndex;

					}catch(ClassCastException e){
						e.printStackTrace();
					}
				}

				//get the data
				if (data != null && data.getSeriesCount() > row){
					if (task != null){

						if (task.getValue() == null){

							//get the time interval
							Date start = task.getDuration().getStart();
							Date end = task.getDuration().getEnd();

							//set the tool tip based on data type
							if (getNumberFormat() != null){
								result[3] = getNumberFormat().format(0.0);
								result[4] = getNumberFormat().format(0.0);
							}else if (getDateFormat() != null){
								result[3] = getDateFormat().format(start);
								result[4] = getDateFormat().format(end);
							}
						}else{
							//check if there is info for the tool tip
							String value = task.getValue();
							if (value != null && !value.equals("")){
								String[] parts = value.split(":",2);
								result[3] = parts[0];
								result[4] = (parts[1].equals("") ? "NONE" : parts[1]);
							}
						}
					}
				}

				return result;
			}

			//SETTER
			public void setSubTaskIndex(int subTaskIndex){
				DataPanel.MyIntervalCategoryGanttToolTipGenerator.subTaskIndex = subTaskIndex;
			}
		}

		/**
		 * Customized class for a SlidingGanttCategoryDataset
		 * which allows you to retrieve the dataset as a task series collection.
		 *
		 * @author MG
		 *
		 */
		static class MySlidingGanttCategoryDataset extends SlidingGanttCategoryDataset{

			private TaskSeriesCollection dataset;

			//CONSTRUCTOR
			public MySlidingGanttCategoryDataset(TaskSeriesCollection dataset, int firstColumn, int maxColumns){
				super((GanttCategoryDataset)dataset, firstColumn, maxColumns);
				this.dataset = dataset;
			}

			//GETTER
			public TaskSeriesCollection getDataset(){
				return this.dataset;
			}
		}

		/**
		 * Customized Gantt renderer class which allows
		 * tool tips for sub tasks of a task in a series.
		 *
		 * @author MG
		 *
		 */
		static class MyGanttRenderer extends GanttRenderer{

			/**
			 * Basic constructor.
			 */
			public MyGanttRenderer(){
				super();
			}

			/**
			 * Draws the tasks/subtasks for one item.
			 * @param g2  the graphics device.
			 * @param state  the renderer state.
			 * @param dataArea  the data plot area.
			 * @param plot  the plot.
			 * @param domainAxis  the domain axis.
			 * @param rangeAxis  the range axis.
			 * @param dataset  the data.
			 * @param row  the row index (zero-based).
			 * @param column  the column index (zero-based).
			 */
			protected void drawTasks(Graphics2D g2, CategoryItemRendererState state,
					Rectangle2D dataArea, CategoryPlot plot,
					CategoryAxis domainAxis, ValueAxis rangeAxis,
					GanttCategoryDataset dataset, int row,
					int column){
				try{
					int count = dataset.getSubIntervalCount(row, column);
					if (count == 0){
						drawTask(g2, state, dataArea, plot, domainAxis, rangeAxis,
								dataset, row, column);
					}

					for (int subinterval = 0; subinterval < count; subinterval++){

						RectangleEdge rangeAxisLocation = plot.getRangeAxisEdge();

						// value 0
						Number value0 = dataset.getStartValue(row, column, subinterval);
						if (value0 == null){
							return;
						}
						double translatedValue0 = rangeAxis.valueToJava2D(value0.doubleValue(), dataArea,
								rangeAxisLocation);

						// value 1
						Number value1 = dataset.getEndValue(row, column, subinterval);
						if (value1 == null){
							return;
						}
						double translatedValue1 = rangeAxis.valueToJava2D(value1.doubleValue(), dataArea,
								rangeAxisLocation);

						if (translatedValue1 < translatedValue0){
							double temp = translatedValue1;
							translatedValue1 = translatedValue0;
							translatedValue0 = temp;
						}

						double rectStart = calculateBarW0(plot, plot.getOrientation(), dataArea,
								domainAxis, state, row, column);
						double rectLength = Math.abs(translatedValue1 - translatedValue0);
						double rectBreadth = state.getBarWidth();

						// DRAW THE BARS...
						Rectangle2D bar = null;

						if (plot.getOrientation() == PlotOrientation.HORIZONTAL){
							bar = new Rectangle2D.Double(translatedValue0, rectStart, rectLength, rectBreadth);
						}else if (plot.getOrientation() == PlotOrientation.VERTICAL){
							bar = new Rectangle2D.Double(rectStart, translatedValue0, rectBreadth, rectLength);
						}

						Rectangle2D completeBar = null;
						Rectangle2D incompleteBar = null;
						Number percent = dataset.getPercentComplete(row, column, subinterval);
						double start = getStartPercent();
						double end = getEndPercent();
						if (percent != null){
							double p = percent.doubleValue();
							if (plot.getOrientation() == PlotOrientation.HORIZONTAL){
								completeBar = new Rectangle2D.Double(translatedValue0, rectStart +
										start * rectBreadth,
										rectLength * p,
										rectBreadth * (end - start));
								incompleteBar =	new Rectangle2D.Double(translatedValue0 + rectLength *
										p,
										rectStart + start * rectBreadth,
										rectLength * (1 - p),
										rectBreadth * (end - start));
							}else if (plot.getOrientation() == PlotOrientation.VERTICAL){
								completeBar = new Rectangle2D.Double(rectStart + start * rectBreadth,
										translatedValue0 +
										rectLength * (1 - p),
										rectBreadth * (end - start),
										rectLength * p);
								incompleteBar =	new Rectangle2D.Double(rectStart + start * rectBreadth,
										translatedValue0,
										rectBreadth * (end - start),
										rectLength * (1 - p));
							}
						}

						Paint seriesPaint = getItemPaint(row, column);
						g2.setPaint(seriesPaint);
						g2.fill(bar);
						if (completeBar != null){
							g2.setPaint(getCompletePaint());
							g2.fill(completeBar);
						}
						if (incompleteBar != null){
							g2.setPaint(getIncompletePaint());
							g2.fill(incompleteBar);
						}
						if (isDrawBarOutline() && state.getBarWidth() > BAR_OUTLINE_WIDTH_THRESHOLD){
							g2.setStroke(getItemStroke(row, column));
							g2.setPaint(getItemOutlinePaint(row, column));
							g2.draw(bar);
						}

						CategoryItemLabelGenerator generator = getItemLabelGenerator(row, column);
						if (generator != null && isItemLabelVisible(row, column)){
							((MyIntervalCategoryGanttToolTipGenerator) generator).setSubTaskIndex(subinterval);
							drawItemLabel(g2, dataset, row, column, plot, generator, bar,
									false);
						}

						// collect entity and tool tip information...
						if (state.getInfo() != null){
							EntityCollection entities = state.getEntityCollection();
							if (entities != null){
								String tip = null;
								CategoryToolTipGenerator tooltip = getToolTipGenerator(row, column);

								if (tooltip != null){
									((MyIntervalCategoryGanttToolTipGenerator) tooltip).setSubTaskIndex(subinterval);
									tip = tooltip.generateToolTip( dataset,	row, column);
								}
								String url = null;
								if (getItemURLGenerator(row, column) != null){
									url = getItemURLGenerator(row, column).generateURL(dataset,	row, column);
								}
								@SuppressWarnings("deprecation")
								CategoryItemEntity entity = new CategoryItemEntity(bar, tip, url, dataset, row, dataset.getColumnKey(column), column);
								entities.add(entity);
							}
						}
					}
				}
				catch (Exception e)	{
					Logging.add("Patient viewer crashed and burned", Logging.ERROR);
					Logging.outputStackTrace(e);
				}
			}
		}
	}

}

