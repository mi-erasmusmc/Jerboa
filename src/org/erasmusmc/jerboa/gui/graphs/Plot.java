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
 * $Rev:: 4459              $:  Revision of last commit                                   *
 * $Author:: root           $:  Author of last commit                                     *
 * $Date:: 					$:  Date and time (CET) of last commit						  *
 ******************************************************************************************/
package org.erasmusmc.jerboa.gui.graphs;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.Comparator;
import java.util.Map;
import java.util.TreeMap;

import javax.swing.JCheckBox;
import javax.swing.JFrame;
import javax.swing.JPanel;
import javax.swing.border.EtchedBorder;

import org.erasmusmc.jerboa.utilities.Logging;
import org.jfree.chart.ChartPanel;
import org.jfree.chart.JFreeChart;
import org.jfree.data.general.Dataset;

/**
 * This generic class represents a template for each type of graphical representation
 * that is to be defined in this package. It is to be extended by any new type of plots.
 *
 * @author MG
 *
 */
@SuppressWarnings("serial")
public abstract class Plot extends JFrame{

	//GUI
	protected String title;
	public ChartPanel panel;
	public JFreeChart chart;
	public JCheckBox sortByName;

	//data
	public Dataset dataset = null;
	public Object data;

	//label text
	public String XLabel;
	public String YLabel;
	public String seriesName;

	//flags
	public boolean showLegend = true;
	public boolean doNotExport = false;
	public boolean sortOption = false;

	public static final Color[] seriesColor = new Color[]{Color.RED, Color.BLUE,
		Color.GREEN, Color.YELLOW, Color.CYAN, Color.BLACK, Color.MAGENTA, Color.ORANGE};

	//in case the sort option is active, remember state if refresh
	public boolean sortingActive;

	//CONSTRUCTOR
	public Plot(String title){
		this.title = title;
	}

	//METHODS TO BE OVERIDDEN
	/**
	 * Creates a chart panel object to be fed to the GUI.
	 * The graphical component is initialized and ready to use.
	 */
	public void createPanel() {

		createDataset();

		this.chart = createChart(this.dataset);
		this.setNoDataMessage();
		this.panel = new ChartPanel(this.chart);
//   	this.panel.setMouseWheelEnabled(true);
		this.panel.setZoomInFactor(new Double(0.5));
		this.panel.setZoomOutFactor(new Double(0.5));
		this.panel.setZoomAroundAnchor(true);
		this.panel.setLayout(new BorderLayout());

		if (sortOption){
			JPanel sortPanel = new JPanel();
			sortByName = new JCheckBox("Sort by name");
			sortByName.setSelected(sortingActive);
			sortByName.addActionListener(new ActionListener() {
				public void actionPerformed(ActionEvent e) {
					if (sortingActive = sortByName.isSelected())
						sortByName();
					else
						sortInactive();
				}});
			sortPanel.setLayout(new BorderLayout());
			sortPanel.setBorder(new EtchedBorder());
			sortPanel.add(sortByName, BorderLayout.EAST);
			this.panel.add(sortPanel, BorderLayout.SOUTH);
		}

		this.add(this.panel, BorderLayout.CENTER);
		this.setPreferredSize(new Dimension(600, 250));
		this.setMinimumSize(new Dimension(600, 250));
		this.setDefaultCloseOperation(DISPOSE_ON_CLOSE);
	}

	/**
	 * Creates a dataset with the data available.
	 * If there is data to plot, the dataset is initialized
	 * and a series is added to it with the data contained
	 * in this.data; if there is no data, the dataset is just initialized.
	 * @return the dataset (empty or populated with a series)
	 */
	public Dataset createDataset() {
		this.dataset = initDataset();
		if (this.data != null)
			this.dataset = addSeriesToDataset(this.data, this.seriesName);

		return this.dataset;
	}

	/**
	 * Writes to the log if the creation of the dataset fails.
	 * @param title - the title of the graph
	 * @param e - the exception to be logged
	 */
	public void cannotCreate(String title, Exception e){
		Logging.add("Error while creating dataset for graph: "+title, Logging.ERROR, true);
		Logging.outputStackTrace(e);
	}

	/**
	 * The message to display if there is no data to plot.
	 */
	public void setNoDataMessage(){
		org.jfree.chart.plot.Plot plot = this.chart.getPlot();
		plot.setNoDataMessage("No data to plot for: \n "+this.title);
		plot.setNoDataMessageFont(new Font("Arial", Font.BOLD, 13));
        plot.setNoDataMessagePaint(Color.DARK_GRAY);
	}

	/**
	 * Sort the data in alphabetical order.
	 */
	public void sortByName(){}

	/**
	 * Put the data as it was for each plot type.
	 */
	public void sortInactive(){}

	/**
	 * Add a vertical line as marker in the plot.
	 * Note that a check should be done if the object type
	 * allow markers.
	 * @param value - the "place" on the X axis for the marker
	 * @param label - the description of the marker
	 */
	public void addMarker(Object value, String label){}

	//METHODS TO BE IMPLEMENTED
	/**
	 * Initializes a specific dataset type depending on
	 * the class inheriting from this super class.
	 * @return - the initialized dataset
	 */
	public abstract Dataset initDataset();

	/**
	 * Creates the JFreeChart object populated with data from dataset.
	 * @param dataset - the data that is to be plotted
	 * @return - a JFreeChart object representing the graph
	 */
	public abstract JFreeChart createChart(Object dataset);

	/**
	 * Will add to this dataset the data passed as parameter.
	 * The type of data and of the dataset is to be defined by the user
	 * and casted according to the plot type.
	 * @param data - the data to be added to the dataset
	 * @param seriesName - the name of the series
	 * @return - the dataset with the new data added
	 */
	public abstract Dataset addSeriesToDataset(Object data, String seriesName);

	/**
	 * Sorts a map (including a tree map) descending by its values.
	 * @param map - the map to be sorted
	 * @param <K> - the key
	 * @param <V> - the value associated with the key
	 * @return - a map sorted by it's values
	 */
	public <K, V extends Comparable<V>> Map<K, V> sortMapByValues(final Map<K, V> map) {
		Comparator<K> valueComparator =  new Comparator<K>() {
		    public int compare(K k1, K k2) {
		        int compare = map.get(k2).compareTo(map.get(k1));
		        if (compare == 0) return 1;
		        else return compare;
		    }
		};
		Map<K, V> sortedByValues = new TreeMap<K, V>(valueComparator);
		sortedByValues.putAll(map);
		return sortedByValues;
	}

	//GETTERS AND SETTERS
	public String getTitle() {
		return title;
	}
	public void setTitle(String title) {
		this.title = title;
	}
	public String getPlotTitle(){
		return this.title;
	}
	public ChartPanel getPanel() {
		return panel;
	}
	public void setPanel(ChartPanel panel) {
		this.panel = panel;
	}
	public Dataset getDataset() {
		return dataset;
	}
	public void setDataset(Dataset dataset) {
		this.dataset = dataset;
	}
	public JFreeChart getChart() {
		return chart;
	}
	public void setChart(JFreeChart chart) {
		this.chart = chart;
	}

	//BUILDER
	public abstract static class Builder{
		protected String title;
		private String XLabel;
		private String YLabel;
		private String seriesName;
		private boolean showLegend;
		private boolean doNotExport;
		private boolean displaySortPanel;
		private Object data;

		public Builder(String title) {
			this.title = title;
		}

		public Builder XLabel(String value) { XLabel = value; return this;}

		public Builder YLabel(String value) { YLabel = value; return this;}

		public Builder seriesName(String value) { seriesName = value; return this;}

		public Builder showLegend(boolean value) { showLegend = value; return this;}

		public Builder doNotExport(boolean value) { doNotExport = value; return this;}

		public Builder displaySortPanel(boolean value) { displaySortPanel = value; return this;}

		public Builder data(Object value) {	data = value; return this;}

		public abstract Plot build();
	}

	public Plot(Builder builder) {
		title = builder.title;
		XLabel = builder.XLabel;
		YLabel = builder.YLabel;
		seriesName = builder.seriesName;
		showLegend = builder.showLegend;
		doNotExport = builder.doNotExport;
		sortOption = builder.displaySortPanel;
		data = builder.data;
		if (seriesName == null)
			seriesName = "";
	}

}
