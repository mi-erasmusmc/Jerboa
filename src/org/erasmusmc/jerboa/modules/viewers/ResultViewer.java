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
 * $Rev:: 3728              $:  Revision of last commit                                   *
 * $Author:: root           $:  Author of last commit                                     *
 * $Date:: 					$:  Date and time (CET) of last commit						  *
 ******************************************************************************************/
package org.erasmusmc.jerboa.modules.viewers;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.net.URL;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;

import javax.swing.JFrame;
import javax.swing.JPanel;

import org.apache.commons.io.FilenameUtils;
import org.erasmusmc.jerboa.Jerboa;
import org.erasmusmc.jerboa.config.FilePaths;
import org.erasmusmc.jerboa.config.Parameters;
import org.erasmusmc.jerboa.gui.JerboaGUI;
import org.erasmusmc.jerboa.gui.graphs.Plot;

import org.erasmusmc.jerboa.utilities.*;
import org.jfree.chart.JFreeChart;

import com.lowagie.text.Document;
import com.lowagie.text.Rectangle;
import com.lowagie.text.pdf.DefaultFontMapper;
import com.lowagie.text.pdf.PdfContentByte;
import com.lowagie.text.pdf.PdfTemplate;
import com.lowagie.text.pdf.PdfWriter;

import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.Image;
import java.awt.Insets;
import java.awt.Toolkit;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.geom.Rectangle2D;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.DefaultListModel;
import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;

/**
 * This class is the super class for a graphical representation of results or data.
 * It contains methods to initialize the interface, the JFreechart with data and to
 * export the plots to file.
 * @author MG
 */
public class ResultViewer extends JPanel{

	private static final long serialVersionUID = 1L;

	//frame related
	public String title;

	//graphs related
	public HashMap<String, List<Plot>> plots;
	public List<Plot> subplots;
	private int subplotIndex;
	//export
	private LinkedHashMap<String, JFreeChart> export;
	private boolean noExport;

	//GUI related
	private JList<String>  list;
	private DefaultListModel<String> model;
	private JScrollPane listPane;
	public static JFrame frame;
	private JPanel mainPanel;
	private JPanel optionsPanel;
	private JPanel chartPanel;
	private JLabel nextLabel;
	private JLabel previousLabel;

	//user feed-back
	public Timer timer;
	public Progress progress;

	//CONSTRUCTORS
	/**
	 * Basic constructor
	 */
	public ResultViewer(){
		super();
		this.progress = new Progress();
		this.timer = new Timer();
		this.plots = new HashMap<String, List<Plot>>();
		this.export = new LinkedHashMap<String, JFreeChart>();
	}

	/**
	 * Basic constructor generating the frame with a title.
	 * @param title - the title of the results viewer
	 */
	public ResultViewer(String title){
		super();
		if (!Jerboa.inConsoleMode){
			this.plots = new HashMap<String, List<Plot>>();
			this.export = new LinkedHashMap<String, JFreeChart>();
			this.title = title;
			this.progress = new Progress();
			this.timer = new Timer();
			initGUI();
		}else{
			Logging.add("No graphs created. Running in console mode", Logging.HINT);
		}
	}

	/**
	 * Constructor receiving the title of the viewer and a flag if the graphs should be exported or not.
	 * @param title - the title of the result viewer
	 * @param noExport - true if the graphs should not be exported; false otherwise
	 */
	public ResultViewer(String title, boolean noExport) {
		super();
		this.noExport = noExport;
		if (!Jerboa.inConsoleMode){
			this.title = title;
			this.export = new LinkedHashMap<String, JFreeChart>();
			this.plots = new HashMap<String, List<Plot>>();
			initGUI();
		}else{
			Logging.add("No graphs created. Running in console mode", Logging.HINT);
		}
	}

	/**
	 * Initializes the necessary GUI components.
	 */
	public void initGUI(){

		this.setLayout(new BorderLayout());

		//initialize panels
		mainPanel = new JPanel();
		mainPanel.setLayout(new BorderLayout());

		optionsPanel = new JPanel();
		optionsPanel.setLayout(new BoxLayout(optionsPanel, BoxLayout.X_AXIS));

		//initialize the lists that will contain the patients
		model = new DefaultListModel<String>();
		list = new JList<String>(model);
		listPane = new JScrollPane(list);
		listPane.setPreferredSize(new Dimension(150, 300));
		listPane.setMinimumSize(new Dimension(150, 300));
		listPane.setMaximumSize(new Dimension(300, 300));

		//panel holding the chart
		chartPanel = new JPanel();
		chartPanel.setLayout(new BorderLayout());

		//add a header with the column names
		listPane.setBorder(BorderFactory.createTitledBorder("Plot title"));

		JSplitPane split = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT);
		split.setResizeWeight(0.0);
		split.setEnabled(true);

		//new panel to hold both files and console panel
		split.add(listPane);
		split.add(chartPanel);

		mainPanel.add(split, BorderLayout.CENTER);

		//add previous button for multiple graphs
		final JButton previousButton = new JButton();
		try{
			URL url = Jerboa.class.getResource(FilePaths.PREVIOUS_ICON_PATH);
			Image img = Toolkit.getDefaultToolkit().getImage(url);
			previousButton.setIcon(new ImageIcon(img, null));
			previousButton.setMargin(new Insets(0, 0, 0, 0));
			previousButton.setBorder(null);
			previousButton.setEnabled(false);
		}catch(Exception e){
			Logging.add("Unable to retrieve previous button icon", Logging.ERROR, true);
			previousButton.setText("PREV");
		}

		//add next button for multiple plots
		final JButton nextButton = new JButton();
		try{
			URL url = Jerboa.class.getResource(FilePaths.NEXT_ICON_PATH);
			Image img = Toolkit.getDefaultToolkit().getImage(url);
			nextButton.setIcon(new ImageIcon(img, null));
			nextButton.setMargin(new Insets(0, 0, 0, 0));
			nextButton.setBorder(null);
			nextButton.setEnabled(false);
		}catch(Exception e){
			Logging.add("Unable to retrieve next button icon", Logging.ERROR, true);
			nextButton.setText("PREV");
		}

		//add buttons and lables to panel
		nextLabel = new JLabel("Next");
		previousLabel = new JLabel("Previous");
		JLabel zoomLabel1 = new JLabel("Select an area to zoom, drag to the left to zoom out");
		zoomLabel1.setAlignmentX(JComponent.CENTER_ALIGNMENT);
		JLabel zoomLabel2 = new JLabel("right-click for more options");
		zoomLabel2.setAlignmentX(JComponent.CENTER_ALIGNMENT);
		optionsPanel.setBorder(BorderFactory.createEtchedBorder());
		optionsPanel.add(previousButton, BorderLayout.EAST);
		optionsPanel.add(previousLabel,BorderLayout.EAST);
		optionsPanel.add(Box.createHorizontalGlue());

		//add labels to the panel
		Box box = Box.createVerticalBox();
		box.add(zoomLabel1);
		box.add(zoomLabel2);
		optionsPanel.add(box,BorderLayout.CENTER);
		optionsPanel.add(Box.createHorizontalGlue());
		optionsPanel.add(nextLabel,BorderLayout.EAST);
		optionsPanel.add(nextButton, BorderLayout.EAST);
		chartPanel.add(optionsPanel, BorderLayout.SOUTH);

		//EVENT CONTROLLERS

		//add down/up arrow listener to refresh the chart
		list.addListSelectionListener(new ListSelectionListener() {
			@Override
			public void valueChanged(ListSelectionEvent e) {
				int index = list.getSelectedIndex();
				list.setSelectedIndex(index < model.size()
						? model.size() : index+1);

				updateChartPanel(list.getSelectedValue());
				nextButton.setEnabled(subplots.size() > 0);
				previousButton.setEnabled(false);

				//show optionsPanel if necessary
				nextButton.setVisible(subplotIndex > 0 || subplotIndex < subplots.size()-1);
				nextLabel.setVisible(subplotIndex > 0 || subplotIndex < subplots.size()-1);
				previousButton.setVisible(subplotIndex > 0 || subplotIndex < subplots.size()-1);
				previousLabel.setVisible(subplotIndex > 0 || subplotIndex < subplots.size()-1);
			}
		});

		//add the previous button functionality
		previousButton.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent arg0) {
				nextButton.setEnabled(true);
				subplotIndex --;
				if (subplotIndex >= 0){
					chartPanel.removeAll();
					chartPanel.add(subplots.get(subplotIndex).getPanel(), BorderLayout.CENTER);
					chartPanel.revalidate();
					chartPanel.add(optionsPanel, BorderLayout.SOUTH);
					mainPanel.repaint();
				}
				previousButton.setEnabled(subplotIndex > 0);
			}
		});

		//add the next button functionality
		nextButton.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent arg0) {
				previousButton.setEnabled(true);
				subplotIndex ++;
				if (subplotIndex < subplots.size()){
					chartPanel.removeAll();
					chartPanel.add(subplots.get(subplotIndex).getPanel(), BorderLayout.CENTER);
					chartPanel.revalidate();
					chartPanel.add(optionsPanel, BorderLayout.SOUTH);
					mainPanel.repaint();
				}
				nextButton.setEnabled(subplotIndex < subplots.size()-1);
			}
		});
		//END OF EVENT CONTROLLERS

		populateViewerList();

		//add components to the GUI and display frame
		this.add(mainPanel, BorderLayout.CENTER);
		this.setName(title);
		this.setVisible(true);

		//put it on the GUI as new tab
		if (!Jerboa.inConsoleMode)
			JerboaGUI.addTab(this);
	}

	/**
	 * Refresh of the chart displayed on the chart panel
	 * upon an action from the user.
	 * @param which - the index of the chart to be displayed
	 */
	private void updateChartPanel(String which){
		//remove all components from panel
		chartPanel.removeAll();

		if (plots.get(which).size() > 1){
			subplots = plots.get(which);
			subplotIndex = 0;
			chartPanel.add(subplots.get(0).getPanel(), BorderLayout.CENTER);
		}else{
			subplots = new ArrayList<Plot>();
			chartPanel.add(plots.get(which).get(0).getPanel(), BorderLayout.CENTER);
		}

		chartPanel.revalidate();
		chartPanel.add(optionsPanel, BorderLayout.SOUTH);
		mainPanel.repaint();
	}

	/**
	 * Adds an entry in the viewer's left hand side list
	 * for each plot present in the plots list.
	 */
	private void populateViewerList(){
		if (plots != null && !plots.isEmpty()){
			model.clear();
			for (String s : plots.keySet())
				model.addElement(s);
			list.setSelectedIndex(0);
		}
	}

	/**
	 * Adds one entry in the viewer's left hand side list.
	 * @param indexLabel - the text describing the entry in the list.
	 */
	private void addPlotToViewerList(String indexLabel){
			if (!model.contains(indexLabel))
				model.addElement(indexLabel);
			else
				list.setSelectedIndex(0);
		//FIXME if you can. If there is only one plot in the list
		//it is not selected by default. Leave the "else" there as
		//it helps with the activation of the next button by default
		//for plot list if it is the first element in the list.
	}

	/**
	 * Adds a plot to the list of plots having the indexLabel in the list
	 * on the left hand side.
	 * @param indexLabel - the label of the plot
	 * @param plot - the plot to be added
	 */
	public void addPlot(String indexLabel, Plot plot){
		if ((indexLabel != null && !indexLabel.equals("")) && plot != null){
			if (plots.get(indexLabel) == null){
				plots.put(indexLabel, new ArrayList<Plot>());
			}
			plots.get(indexLabel).add(plot);
			addPlotToViewerList(indexLabel);

			if (!plot.doNotExport)
				export.put(plot.getTitle(), plot.getChart());
		}else{
			Logging.add("Unable to add plot "+plot.getTitle()+" in the viewer "+
					this.title+" due to missing/incorrect index label", Logging.ERROR);
		}
	}

	/**
	 * Adds a list of plots to a new or existing index in the list.
	 * @param indexLabel - the label of the plots in the left hand side list
	 * @param plots - the list of plots to be added
	 */
	public void addPlotList(String indexLabel, List<Plot> plots){
		if (plots != null && plots.size() > 0){
			for (Plot plot : plots)
				addPlot(indexLabel, plot);
		}else{
			Logging.add("The list of plots to be added to "+this.title+
					" is null or empty. Index label: "+indexLabel, Logging.ERROR);
		}
	}

	/**
	 * Saves charts as PDF file. Requires iText library.
	 */
	public void saveChartsToPDF(){
		if (!noExport){
			if (this.export != null && this.export.size() >0) {

				progress = new Progress();
				progress.init(this.export.size(), "Exporting graphs from "+this.title);

				try{
					String fileName = FilePaths.WORKFLOW_PATH+this.title.toLowerCase()+"/"+Parameters.DATABASE_NAME+"_"+
							title.replaceAll(" ", "")+"_"+TimeUtilities.TIME_STAMP+"_"+Parameters.VERSION+".pdf";
					int height = 550;
					int width = 650;
					BufferedOutputStream out = null;
					Document document = null;
					try {
						//check if the file/folder exists or create them
						File f = new File(FilenameUtils.getFullPath(fileName));
						boolean mkDir = true;
						if (!f.exists())
							mkDir = f.mkdirs();
						if (mkDir){
							out = new BufferedOutputStream(new FileOutputStream(fileName));
							//convert chart to PDF with iText:
							Rectangle pagesize = new Rectangle(width, height);
							document = new Document(pagesize, 50, 50, 50, 50);
							PdfWriter writer = PdfWriter.getInstance(document, out);
							document.addAuthor("Jerboa");
							document.open();

							for (JFreeChart chart : this.export.values()){
								if (chart != null){
									PdfContentByte cb = writer.getDirectContent();
									PdfTemplate tp = cb.createTemplate(width, height);
									//testing
									DefaultFontMapper dfm = new DefaultFontMapper();
									dfm.awtToPdf(new Font("Dialog", Font.PLAIN, 8));
									Graphics2D g2 = tp.createGraphics(width, height, dfm);

									Rectangle2D r2D = new Rectangle2D.Double(0, 0, width, height);
									chart.draw(g2, r2D, null);
									g2.dispose();
									cb.addTemplate(tp, 0, 0);
									document.newPage();
								}
								progress.update();
							}
						}else{
							Logging.add("Unable to create folder structure to export graphs for "+this.title, Logging.ERROR);
							Jerboa.stop();
						}
					}catch(Exception e){
						Logging.add("Unable to export graphs to PDF for the "+this.title, true);
						Logging.outputStackTrace(e);
					} finally {
						document.close();
						if (out != null) {
							out.close();
						}

						progress.close();
					}
				}catch(Exception e){
					Logging.add("Unable to create PDF file for plot export", Logging.ERROR);
				}
			}else{
				Logging.add("No data in the plot to export");
			}
		}
	}

}

