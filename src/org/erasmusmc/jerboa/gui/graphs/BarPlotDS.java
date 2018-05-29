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
import java.util.HashMap;

import org.apache.commons.collections.bag.HashBag;
import org.erasmusmc.jerboa.config.PropertiesManager;
import org.erasmusmc.jerboa.utilities.StringUtilities;

import org.jfree.chart.ChartFactory;
import org.jfree.chart.JFreeChart;
import org.jfree.chart.axis.NumberAxis;
import org.jfree.chart.labels.StandardXYToolTipGenerator;
import org.jfree.chart.plot.PlotOrientation;
import org.jfree.chart.plot.XYPlot;
import org.jfree.chart.renderer.xy.ClusteredXYBarRenderer;
import org.jfree.chart.renderer.xy.StandardXYBarPainter;
import org.jfree.chart.renderer.xy.XYBarRenderer;
import org.jfree.chart.title.TextTitle;
import org.jfree.data.general.Dataset;
import org.jfree.data.xy.IntervalXYDataset;
import org.jfree.data.xy.XYSeries;
import org.jfree.data.xy.XYSeriesCollection;

/**
 * This class contains the necessary methods to create a discrete series bar plot.
 * The data can be under a HashBag (allows only integers as values)
 * or a HashMap (allows also doubles) representation.
 *
 * @author MG
 *
 */
@SuppressWarnings("serial")
public class BarPlotDS extends Plot{

	//flags
	private boolean isHashMap = true;
	private boolean sortValues;
	private boolean clustered;

	//range
	private double minRange = Double.MIN_VALUE;
	private double maxRange = Double.MAX_VALUE;

	/**
	 * Constructor receiving a builder object to ease initialization
	 * with many parameters and creating the plot panel.
	 * @param builder - the builder of this object
	 */
	public BarPlotDS(Builder builder) {
		super(builder);
		sortValues = builder.sortValues;
		clustered = builder.clustered;
		minRange = builder.minRange;
		maxRange = builder.maxRange;
		createPanel();
	}

	//BUILDER
	public static class Builder extends Plot.Builder{

		public Builder(String title) {super(title);}

		private boolean sortValues;
		private boolean clustered;
		private double minRange = Double.MIN_VALUE;
		private double maxRange = Double.MAX_VALUE;

		public Builder sortValues(boolean value) { sortValues = value; return this;}
		public Builder clustered(boolean value) { clustered = value; return this;}
		public Builder minRange(double value) { minRange = value; return this;}
		public Builder maxRange(double value) { maxRange = value; return this;}

		public Plot build() {return new BarPlotDS(this);}
	}

	@Override
	public Dataset initDataset() {
		return new XYSeriesCollection();
	}

	/**
	 * Will add to the plot dataset the data passed as parameter.
	 * The type of data and of the dataset is to be defined by the user
	 * and casted according to the plot type.
	 * @param data - the data to be added to the dataset
	 * @param seriesName - the name of the series
	 * @return - the dataset with the new data added
	 */
	@SuppressWarnings("rawtypes")
	@Override
	public Dataset addSeriesToDataset(Object data, String seriesName){

		try {
			XYSeries series = new XYSeries(seriesName, sortValues);
			isHashMap = data instanceof HashMap; //a HashBag cannot contain double values

			Object[] keySet = isHashMap ? ((HashMap)data).keySet().toArray() : ((HashBag)data).uniqueSet().toArray();
	//		Arrays.sort(keySet);
			for (Object key : keySet)
				series.add((Number)key, isHashMap ? (Number)(((HashMap)data).get(key)) : (Number)(((HashBag)data).getCount(key)));

			if (series.getItemCount() > 0){
				((XYSeriesCollection)dataset).addSeries((XYSeries)series);
				if (series.getX(0) instanceof Double)
					((XYSeriesCollection)dataset).setIntervalWidth(0.1d);
			}
		}catch (Exception e) {
			cannotCreate(title, e);
		}

		return (IntervalXYDataset)dataset;
	}

	/**
	 * Graph constructor. Initializes the graph components
	 * and feeds it with the data.
	 * @param dataset - the data to be plotted
	 * @return - the panel holding the plot
	 */
	@Override
	public JFreeChart createChart(Object dataset) {
		chart = ChartFactory.createXYBarChart(
				getTitle(),					// the anti matter destroying the world thingy
				XLabel,						// X axis label
				false,						// flag for date series
				YLabel,						// Y axis label
				(IntervalXYDataset)dataset,	// the data
				PlotOrientation.VERTICAL,	//orientation
				showLegend,					//legend
				true,						//tool tip
				false						//URLs
				);

		TextTitle title = chart.getTitle();
		title.setFont(title.getFont().deriveFont(Font.BOLD,14.0f));
		chart.setTitle(title);

		//set the renderer
		XYPlot plot = (XYPlot) chart.getPlot();
		plot.setDomainPannable(true);
		plot.setRangePannable(true);

		NumberAxis axis = (NumberAxis)plot.getDomainAxis();
		axis.setStandardTickUnits(NumberAxis.createIntegerTickUnits());
		axis.setNumberFormatOverride(StringUtilities.DECIMAL_FORMAT);
		if (minRange != Double.MIN_VALUE && maxRange != Double.MAX_VALUE)
			axis.setRange(minRange*0.95, maxRange*1.05); //allow a bit of extra range

		StandardXYToolTipGenerator generator;
		generator = new StandardXYToolTipGenerator(
				"{1} = {2}", StringUtilities.DECIMAL_FORMAT, StringUtilities.DECIMAL_FORMAT);

		if (clustered){
			ClusteredXYBarRenderer renderer = new ClusteredXYBarRenderer(0.2, false);
			renderer.setShadowVisible(false);
			renderer.setBarPainter(new StandardXYBarPainter());
			renderer.setDrawBarOutline(false);
			renderer.setBaseToolTipGenerator(generator);
			renderer.setMargin(0.10);
			plot.setRenderer(renderer);
		}else{
			XYBarRenderer renderer = (XYBarRenderer)plot.getRenderer();
			renderer.setShadowVisible(false);
			renderer.setBarPainter(new StandardXYBarPainter());
			renderer.setDrawBarOutline(false);
			renderer.setBaseToolTipGenerator(generator);
			renderer.setMargin(0.10);
			plot.setRenderer(renderer);
		}

		if (!showLegend)
			chart.removeLegend();

		return chart;
	}

	/**
	 * Main method for testing and debugging.
	 * @param args - none
	 */
	public static void main(String[] args){
		new PropertiesManager();

		HashBag data = new HashBag();
		data.add(3.2,2);
		data.add(13.2,2);
		data.add(1243.2,9);
		data.add(33.2,7);
		data.add(73.2,4);
		data.add(43.2);
		data.add(43.2);
		data.add(53.2,20);
		data.add(23.2,1);

		Plot plot = new BarPlotDS.Builder("Test plot")
		.sortValues(true)
		.data(data)
		.XLabel("Funky")
		.YLabel("Chicken")
		.build();

		data = new HashBag();
		data.add(11);
		data.add(256,10);

		if (((BarPlotDS)plot).sortValues)
			data.add(5690, 100);

		plot.addSeriesToDataset(data, "Test");

		plot.setLocationRelativeTo(null);
		plot.pack();
		plot.setVisible(true);
	}

}
