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
 * $Rev:: 3699              $:  Revision of last commit                                   *
 * $Author:: root           $:  Author of last commit                                     *
 * $Date:: 					$:  Date and time (CET) of last commit						  *
 ******************************************************************************************/
package org.erasmusmc.jerboa.gui.graphs;

import java.awt.Font;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.TreeMap;

import org.erasmusmc.jerboa.config.PropertiesManager;
import org.erasmusmc.jerboa.utilities.StringUtilities;

import org.jfree.chart.ChartFactory;
import org.jfree.chart.JFreeChart;
import org.jfree.chart.axis.AxisLocation;
import org.jfree.chart.axis.CategoryAxis;
import org.jfree.chart.axis.CategoryLabelPositions;
import org.jfree.chart.axis.NumberAxis;
import org.jfree.chart.labels.StandardCategoryItemLabelGenerator;
import org.jfree.chart.labels.StandardCategoryToolTipGenerator;
import org.jfree.chart.plot.CategoryPlot;
import org.jfree.chart.plot.PlotOrientation;
import org.jfree.chart.renderer.category.BarRenderer;
import org.jfree.chart.renderer.category.StandardBarPainter;
import org.jfree.chart.title.TextTitle;
import org.jfree.data.category.CategoryDataset;
import org.jfree.data.category.DefaultCategoryDataset;
import org.jfree.data.category.SlidingCategoryDataset;
import org.jfree.data.general.Dataset;

/**
 * This class contains the necessary methods to create a scrolling horizontal
 * bar plot representation of categorical data. It assumes that every
 * series to be added to the dataset is under a {@literal Map<Object, Object>} representation.
 *
 * @author MG
 *
 */
@SuppressWarnings("serial")
public class BarPlotCategory extends ScrollablePlot{

	private boolean verticalLabels;
	private CategoryDataset copyDataset;

	/**
	 * Constructor receiving a builder object to ease initialization
	 * with many parameters and creating the plot panel.
	 * @param builder - the builder of this object
	 */
	public BarPlotCategory(Builder builder) {
		super(builder);
		this.verticalLabels = builder.verticalLabels;
		//invert the axis label as the plot is rotated
		if (!forceVertical){
			String temp = XLabel;
			this.XLabel = this.YLabel;
			this.YLabel = temp;
		}

//		sortOption = true;
		createPanel();
	}

	//BUILDER
	public static class Builder extends ScrollablePlot.Builder{

		private boolean verticalLabels;

		public Builder(String title) {super(title);}
		public Builder verticalLabels(boolean value) { verticalLabels = value; return this;}

		public BarPlotCategory build() {return new BarPlotCategory(this);}
	}

	@Override
	public Dataset initDataset() {
		return new DefaultCategoryDataset();
	}

	@Override
	public boolean isDatasetEmpty(Dataset dataset) {
		return ((CategoryDataset)this.dataset).getRowCount() == 0 ||
				((CategoryDataset)this.dataset).getColumnCount() == 0;
	}

	/**
	 * Graph constructor. Initializes the graph components
	 * and feeds it with the data.
	 * @param dataset - the data to be plotted
	 * @return - the panel holding the plot
	 */
	@Override
	public JFreeChart createChart(Object dataset) {
		chart = ChartFactory.createBarChart(
				title,							// the anti matter destroying the world thingy
				XLabel,							// X axis label
				YLabel,							// Y axis label
				(SlidingCategoryDataset)dataset,
				forceVertical ? PlotOrientation.VERTICAL : PlotOrientation.HORIZONTAL,	//orientation
				showLegend,						//legend
				true,							//tool tip
				false							//URLs
				);

		CategoryPlot plot = (CategoryPlot) chart.getPlot();
		plot.setDomainGridlinesVisible(true);
		plot.setRangeAxisLocation(AxisLocation.BOTTOM_OR_LEFT);
		plot.setRangePannable(true);
		plot.setRangeZeroBaselineVisible(true);

		//set the renderer
		BarRenderer renderer = (BarRenderer)plot.getRenderer();
		renderer.setItemMargin(0);
		renderer.setMaximumBarWidth(0.20);
		renderer.setShadowVisible(false);
		renderer.setBarPainter(new StandardBarPainter());
		renderer.setDrawBarOutline(false);
		plot.setRenderer(renderer);

		//tool tip
		renderer = (BarRenderer) plot.getRenderer();
		renderer.setBaseItemLabelGenerator(
				new StandardCategoryItemLabelGenerator());
		renderer.setBaseToolTipGenerator(new StandardCategoryToolTipGenerator(
				"{0}, {1} = {2}", StringUtilities.DECIMAL_FORMAT));

		// set the range axis to display integers only...
		NumberAxis rangeAxis = (NumberAxis) plot.getRangeAxis();
		rangeAxis.setStandardTickUnits(NumberAxis.createIntegerTickUnits());

		if (verticalLabels){
			CategoryAxis domainAxis = (CategoryAxis)plot.getDomainAxis();
			domainAxis.setCategoryLabelPositions(CategoryLabelPositions.UP_90);
		}

		TextTitle title = chart.getTitle();
		title.setFont(title.getFont().deriveFont(Font.BOLD,16.0f));
		chart.setTitle(title);
		if (!showLegend)
			chart.removeLegend();

		return chart;

	}


	/**
	 * Will add to this dataset the data passed as parameter.
	 * The type of data and of the dataset is to be defined by the user
	 * and casted according to the plot type.
	 * @param data - the data to be added to the dataset
	 * @param seriesName - the name of the series
	 * @return - the dataset with the new data added
	 */
	@Override
	@SuppressWarnings("unchecked")
	public Dataset addSeriesToDataset(Object data, String seriesName){

		//initialize dataset or retrieve existing one
		CategoryDataset dataset;
		if (this.dataset instanceof SlidingCategoryDataset){
			dataset = ((SlidingCategoryDataset)this.dataset).getUnderlyingDataset();
		}else{
			dataset = (DefaultCategoryDataset)this.dataset;
		}

		//sort if needed
		if (sortByValue)
			data = sortData(data);
		//populate series
		try{
			for (Entry<Object, Object> entry : ((Map<Object, Object>)data).entrySet()){
				if ((onValue != null && !onValue.equals("")) && !entry.getKey().toString().equals(onValue))
					continue;
				((DefaultCategoryDataset)dataset).addValue((Number) entry.getValue(), seriesName, entry.getKey().toString());
			}
			this.dataset = dataset;
		}catch(Exception e){
			cannotCreate(title, e);
		}finally{
			refresh();
		}

		this.copyDataset = dataset;
		return dataset;
	}

	@SuppressWarnings("unchecked")
	@Override
	public void sortByName(){
		CategoryDataset current;
		//get current dataset
		if (this.dataset instanceof SlidingCategoryDataset)
			current = ((SlidingCategoryDataset)this.dataset).getUnderlyingDataset();
		else
			current = (DefaultCategoryDataset)this.dataset;
		//get series and values and sort them
		List<String> series = new ArrayList<String>(forceVertical ? current.getColumnKeys() : current.getRowKeys());
		Collections.sort(series);
		List<String> categories = new ArrayList<String>(forceVertical ? current.getRowKeys() : current.getColumnKeys());
		Collections.sort(categories);

		//populate the dataset with values sorted by name
		this.dataset = initDataset();
		for (String s : series)
			for (String cat : categories)
				if (current.getValue(s, cat) != null)
					((DefaultCategoryDataset)this.dataset).addValue(current.getValue(s, cat), s, cat);
	}

	@Override
	public void sortInactive(){
		this.dataset = this.copyDataset;
	}

	/**
	 * Main method for debug and testing.
	 * @param args - none
	 */
	public static void main(String[] args){
		new PropertiesManager();

		TreeMap<Object, Object> data = new TreeMap<Object, Object>();
		data.put("pork",2);
		data.put("bla",23);
		data.put("chicken",42);
		data.put("cat",67);
		data.put("zebra",25);
		data.put("dog",42);
		data.put("pig",2);
		data.put("frog",23);
		data.put("skunk",42);
		data.put("jerboa",167);
		data.put("bullshit",25);
		data.put("tralala",42);

		Plot plot = new BarPlotCategory.Builder("Test plot")
		.sortByValue(true)
		.showLegend(true)
		.seriesName("Mhm")
		.data(data)
		.XLabel("Funky")
		.YLabel("Chicken")
		.build();

		data = new TreeMap<Object, Object>();
		data.put("ffff",11);
		data.put("aaa",12);
		data.put("d",13);
		data.put("cccc",14);
		data.put("bb",11);
		data.put("ttttttt",12);
		data.put("v",13);
		data.put("sht",14);
		data.put("kk",11);
		data.put("phh",12);
		data.put("qqq",13);
		data.put("zzz",14);

		plot.addSeriesToDataset(data, "Test");

		plot.setLocationRelativeTo(null);
		plot.pack();
		plot.setVisible(true);
	}

}
