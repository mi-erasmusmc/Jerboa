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
import java.text.SimpleDateFormat;

import org.apache.commons.collections.bag.HashBag;
import org.erasmusmc.jerboa.config.PropertiesManager;
import org.erasmusmc.jerboa.utilities.StringUtilities;

import org.jfree.chart.ChartFactory;
import org.jfree.chart.JFreeChart;
import org.jfree.chart.axis.DateAxis;
import org.jfree.chart.axis.DateTickMarkPosition;
import org.jfree.chart.labels.StandardXYToolTipGenerator;
import org.jfree.chart.plot.PlotOrientation;
import org.jfree.chart.plot.XYPlot;
import org.jfree.chart.renderer.xy.StandardXYBarPainter;
import org.jfree.chart.renderer.xy.XYBarRenderer;
import org.jfree.chart.title.TextTitle;
import org.jfree.data.general.Dataset;
import org.jfree.data.time.Day;
import org.jfree.data.time.Month;
import org.jfree.data.time.TimeSeries;
import org.jfree.data.time.TimeSeriesCollection;
import org.jfree.data.xy.IntervalXYDataset;

/**
 * This class contains the necessary methods to create a time series bar plot.
 * It assumes the data is under a HashBag representation.
 * !!! Note that the year interval is from -9999 to 9999 but there is no day support
 * for years prior to 1900 as JFreechart date and time are based on the excel
 * date representation.
 *
 * @author MG
 *
 */
@SuppressWarnings("serial")
public class BarPlotTS extends Plot{

	//the first day supported by the JFreechart SerialDate is 1st of January 1900.
	//months and years prior to that are supported.
	public final static int FIRST_DAY_SUPPORTED = 1900;

	/**
	 * Constructor receiving a builder object to ease initialization
	 * with many parameters and creating the plot panel.
	 * @param builder - the builder of this object
	 */
	public BarPlotTS(Builder builder) {
		super(builder);
		createPanel();
	}

	//BUILDER
	public static class Builder extends Plot.Builder{

		public Builder(String title) {super(title);}

		public Plot build() {return new BarPlotTS(this);}
	}

	@Override
	public Dataset initDataset() {
		return new TimeSeriesCollection();
	}

	/**
	 * Will add to the plot dataset the data passed as parameter.
	 * The type of data and of the dataset is to be defined by the user
	 * and casted according to the plot type.
	 * @param data - the data to be added to the dataset
	 * @param seriesName - the name of the series
	 * @return - the dataset with the new data added
	 */
	@Override
	public Dataset addSeriesToDataset(Object data, String seriesName){

		try {
			TimeSeries series = new TimeSeries(seriesName, XLabel, YLabel);
			Object[] keySet = ((HashBag)data).uniqueSet().toArray();
			for (Object key : keySet){
				int[] date = (int[])key;
				((TimeSeries)series).add(date[2] < FIRST_DAY_SUPPORTED ?  new Month(date[1], date[0]) :
					new Day(date[2], date[1], date[0]), ((HashBag)data).getCount(key));
			}

			if (series.getItemCount() > 0)
				((TimeSeriesCollection)dataset).addSeries((TimeSeries)series);

		}catch (Exception e) {
			cannotCreate(title, e);
		}

		return dataset;
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
				true,						// flag for date series
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

		//set axis
		DateAxis axis = (DateAxis) plot.getDomainAxis();
		axis.setTickMarkPosition(DateTickMarkPosition.START);
		axis.setLowerMargin(0.01);
		axis.setUpperMargin(0.01);

		XYBarRenderer renderer = (XYBarRenderer)plot.getRenderer();
		renderer.setShadowVisible(false);
		renderer.setBarPainter(new StandardXYBarPainter());
		renderer.setDrawBarOutline(false);
		renderer.setMargin(0.20);
		plot.setRenderer(renderer);

		StandardXYToolTipGenerator generator;
		generator  = new StandardXYToolTipGenerator(
				"{1} = {2}", new SimpleDateFormat("yyyy/MM/dd"), StringUtilities.DECIMAL_FORMAT);
		renderer.setBaseToolTipGenerator(generator);
		renderer.setMargin(0.1);
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
		data.add(new int[]{2010, 05, 23},2);
		data.add(new int[]{2010, 05, 24});
		data.add(new int[]{2011, 05, 03});
		data.add(new int[]{2011, 06, 13},3);
		data.add(new int[]{2012, 12, 02});
		data.add(new int[]{2013, 01, 01});
		data.add(new int[]{2013, 02, 03});
		data.add(new int[]{2013, 10, 20});

		Plot plot = new BarPlotTS.Builder("Test plot")
	//	.data(data)
		.XLabel("First")
		.YLabel("Second")
		.build();

		data = new HashBag();
		data.add(new int[]{2000, 05, 05},2);
		data.add(new int[]{2005, 05, 24},3);

		plot.addSeriesToDataset(data, "A series");

		plot.setLocationRelativeTo(null);
		plot.pack();
		plot.setVisible(true);
	}

}
