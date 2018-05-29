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

package org.erasmusmc.jerboa.gui.graphs;


import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.GridBagLayout;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.MouseWheelEvent;
import java.awt.event.MouseWheelListener;
import java.util.Comparator;
import java.util.Map;
import java.util.Map.Entry;
import java.util.TreeMap;

import javax.swing.JCheckBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollBar;
import javax.swing.SwingConstants;
import javax.swing.border.EtchedBorder;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;

import org.erasmusmc.jerboa.utilities.Logging;
import org.jfree.chart.ChartPanel;
import org.jfree.data.DefaultKeyedValues2D;
import org.jfree.data.category.CategoryDataset;
import org.jfree.data.category.DefaultCategoryDataset;
import org.jfree.data.category.SlidingCategoryDataset;
import org.jfree.data.general.Dataset;

/**
 * This generic class represents a template for each type of graphical representation
 * that needs scrolling functionality for the plot itself.
 * It is to be extended by any new type of scrollable plot.
 *
 * @author MG
 *
 */
@SuppressWarnings("serial")
public abstract class ScrollablePlot extends Plot{

	//plot orientation
	public boolean forceVertical;

	//plot only data for a specific value
	public String onValue;

	//flag for sorting by value - normally the data should be sorted by category
	public boolean sortByValue;

	//the maximum entries to be displayed until scroll is activated
	public static final int SCROLL_THRESHOLD = 20; //used in the builder also
	public int maxEntriesToDisplay = SCROLL_THRESHOLD;

	//ze scroll bar
	private JScrollBar scroller;

	//keeping track of the number of entries in the dataset
	private int nbEntries;

	public static final Color[] seriesColor = new Color[]{Color.BLUE, Color.RED,
		Color.GREEN, Color.YELLOW, Color.CYAN, Color.BLACK, Color.MAGENTA, Color.ORANGE};

	/**
	 * Basic constructor receiving the title of the plot.
	 * @param title - the title to be displayed on the plot
	 */
	public ScrollablePlot(String title){
		super(title);
	}

	//METHODS TO BE OVERIDDEN
	/**
	 * Creates a scrollable chart panel object to be fed to the GUI.
	 * The graphical component is initialized and ready to use.
	 */
	@Override
	public void createPanel() {

		createDataset();

		if (!isDatasetEmpty(this.dataset)){
			refresh();
		}else{
			this.panel = new ChartPanel(null);
			this.panel.setLayout(new GridBagLayout());
			this.panel.add(new JLabel("No data to plot for: \n "+title), SwingConstants.CENTER);
		}

		this.setPreferredSize(new Dimension(600, 350));
		this.setMinimumSize(new Dimension(600, 350));
		this.setDefaultCloseOperation(DISPOSE_ON_CLOSE);
		this.add(panel, BorderLayout.CENTER, 0);
	}

	/**
	 * Configures the chart panel with the eventual scroll bar and sorting option.
	 */
	public void populatePanel(){

		panel.setZoomInFactor(new Double(0.5));
		panel.setZoomOutFactor(new Double(0.5));
		panel.setZoomAroundAnchor(true);
		panel.setLayout(new BorderLayout());

		//add the scroll bar and events (arrows + mouse wheel)
		if (nbEntries > maxEntriesToDisplay){
			this.scroller = new JScrollBar(forceVertical ? SwingConstants.HORIZONTAL : SwingConstants.VERTICAL,
					0, maxEntriesToDisplay, 0, nbEntries);
			this.scroller.getModel().addChangeListener(new ChangeListener() {
				@Override
				public void stateChanged(ChangeEvent e) {
					scroll();
				}
			});
			panel.addMouseWheelListener(new MouseWheelListener() {
				@Override
				public void mouseWheelMoved(MouseWheelEvent e) {
					scroller.setValue(e.getWheelRotation() < 0 ? scroller.getValue()-1 : scroller.getValue()+1);
					scroll();
				}
			});
			panel.add(scroller, forceVertical ? BorderLayout.SOUTH : BorderLayout.EAST);
		}
		//add sort button if needed
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
					refresh();
				}});
			sortPanel.setLayout(new BorderLayout());
			sortPanel.setBorder(new EtchedBorder());
			sortPanel.add(sortByName, BorderLayout.EAST);
			this.panel.add(sortPanel, BorderLayout.NORTH);
		}

		this.add(panel, BorderLayout.CENTER, 0);
		this.panel.revalidate();
	}

	/**
	 * Will set the new index in the displayed
	 * data according to the scrolling value.
	 */
	public void scroll(){
//		if (!(dataset instanceof SlidingCategoryDataset))
//			refresh();
		((SlidingCategoryDataset)dataset).setFirstCategoryIndex(scroller.getValue());
	}

	/**
	 * Recreates the chart with the updated dataset.
	 */
	public void refresh(){
		this.nbEntries = getNumberOfEntries();
		this.dataset = new SlidingCategoryDataset((CategoryDataset) dataset, 0, maxEntriesToDisplay);
		this.chart = createChart(this.dataset);
		this.panel = new ChartPanel(this.chart);
		populatePanel();
	}

	//TO BE IMPLEMENTED
	/**
	 * Checks if the dataset contains any data. This method
	 * is to be written specifically for each plot type and
	 * depending on the type of dataset used in that plot type.
	 * The reason it exists, is that there aren't methods in the
	 * JFreeChart library to check if a dataset is empty
	 * for all/any type of dataset.
	 * @param dataset - this dataset
	 * @return - true if the dataset is empty; false otherwise
	 */
	public abstract boolean isDatasetEmpty(Dataset dataset);

	//UTILITIES
	/**
	 * Sorts the data according to its values.
	 * It assumes that the data structure is a map consisting
	 * of Strings as keys and Integers or Doubles as values.
	 * @param data - the data to be sorted
	 * @return - the sorted data
	 */
	@SuppressWarnings("unchecked")
	public Object sortData(Object data){
		try{
			//check if there is data
			Entry<Object, Object> firstEntry = ((TreeMap<Object, Object>)data).firstEntry();
			if (firstEntry != null){
				Object value = firstEntry.getValue();
				if (value instanceof Double)
					data = sortMapByValues((Map<String,Double>)data);
				else if (value instanceof Integer)
					data = sortMapByValues((Map<String,Integer>)data);
				else if (value instanceof Long)
					data = sortMapByValues((Map<String,Long>)data);
				else
					Logging.add("Could not sort data by value in plot "+
							this.title+". Incompatible type", Logging.HINT);
			}
		}catch (ClassCastException e){
			Logging.outputStackTrace(e);
		}

		return data;
	}

	/**
	 * Sorts a map (including a tree map) descending by its values.
	 * @param map - the map to be sorted
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
	/**
	 * Returns the number of entries in the dataset depending
	 * on the orientation of the plot.
	 * @return - the number of rows or columns in the dataset
	 */
	public int getNumberOfEntries(){
		return forceVertical ? ((DefaultCategoryDataset)dataset).getRowCount()
				: ((DefaultCategoryDataset)dataset).getColumnCount();
	}

	//BUILDER
	public abstract static class Builder extends Plot.Builder{

		public Builder(String title) {super(title);}

		private boolean forceVertical;
		private boolean sortByValue;
		private String onValue;
		private int maxEntriesToDisplay = ScrollablePlot.SCROLL_THRESHOLD;

		public Builder forceVertical(boolean value) { forceVertical = value; return this;}

		public Builder sortByValue(boolean value) { sortByValue = value; return this;}

		public Builder onValue(String value) { onValue = value; return this;}

		public Builder maxEntriesToDisplay(int value) { maxEntriesToDisplay = value; return this;}

		public abstract ScrollablePlot build();
	}

	public ScrollablePlot(Builder builder) {
		super(builder);
		forceVertical = builder.forceVertical;
		sortByValue = builder.sortByValue;
		onValue = builder.onValue;
		maxEntriesToDisplay = builder.maxEntriesToDisplay;
	}

	//NOT USED FOR NOW AS FOR THIS PLOT TREEMAPS ARE PASSED WITH KEYS SORTED BY DEFAULT
	/**
	 * Overrides DefaultCategoryDataset in order to sort the entries by the Y label.
	 *
	 * @author MG
	 *
	 */
	public class MyDefaultCategoryDataset extends DefaultCategoryDataset{

		private DefaultKeyedValues2D data;

		//CONSTRUCTORS
		/**
		 * Basic constructor.
		 */
		public MyDefaultCategoryDataset() {
			this(false);
		}

		/**
		 *Constructor for an empty data set.
		 * @param sortRows - true if the rows should be sorted
		 */
		public MyDefaultCategoryDataset(boolean sortRows) {
			this.data = new DefaultKeyedValues2D(sortRows);
		}

		//GETTER
		public DefaultKeyedValues2D getData() {
			return data;
		}
	}

}
