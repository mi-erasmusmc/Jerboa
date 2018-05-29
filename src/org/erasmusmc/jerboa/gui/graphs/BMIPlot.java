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

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.util.TreeMap;

import org.erasmusmc.jerboa.utilities.stats.HistogramStats;
import org.jfree.chart.ChartFactory;
import org.jfree.chart.ChartUtilities;
import org.jfree.chart.JFreeChart;
import org.jfree.chart.axis.ValueAxis;
import org.jfree.chart.plot.DatasetRenderingOrder;
import org.jfree.chart.plot.PlotOrientation;
import org.jfree.chart.plot.XYPlot;
import org.jfree.chart.renderer.xy.StandardXYItemRenderer;
import org.jfree.chart.renderer.xy.XYAreaRenderer;
import org.jfree.chart.renderer.xy.XYItemRenderer;
import org.jfree.chart.title.TextTitle;
import org.jfree.data.general.Dataset;
import org.jfree.data.xy.XYDataset;
import org.jfree.data.xy.XYSeries;
import org.jfree.data.xy.XYSeriesCollection;

/**
 * This class contains all the necessary methods to create a plot for the BMI measurements.
 * It will take a dataset containing reference values in order to plot growth curves
 * and a dataset containing a list of percentiles to be plotted from the actual data.
 *
 * @author MG
 *
 */
@SuppressWarnings("serial")
public class BMIPlot extends Plot {

	//the data
	private TreeMap<Object, Object> referenceData;

	private double minDomain = -1;
	private double maxDomain = -1;

	private double minRange = -1;
	private double maxRange = -1;

	private int[] percentiles;
//	private double[] XTicks;
//	private double[] YTicks;

	//not helping
	private Color[] seriesColors = new Color[]{Color.GREEN, Color.CYAN, Color.ORANGE, Color.BLACK, Color.PINK};

	/**
	 * Constructor receiving a builder object to ease initialization
	 * with many parameters and creating the plot panel.
	 * @param builder - the builder of this object
	 */
	public BMIPlot(Builder builder) {
		super(builder);
		referenceData = builder.referenceData;
		percentiles = builder.percentiles;
		minRange = builder.minRange;
		maxRange = builder.maxRange;
//		XTicks = builder.XTicks;
//		YTicks = builder.YTicks;
		createPanel();
	}

	/**
	 * Will create the growth curves from the reference values dataset.
	 * @return - the dataset
	 */
	private XYDataset createReferenceDataset() {

		XYSeriesCollection dataset = null;
		if (referenceData != null && referenceData.size() > 0){
			double[] ageValues = (double[])referenceData.get("age");
			dataset  = new XYSeriesCollection();
			if (ageValues != null && ageValues.length > 0){
				minDomain = ageValues[0];
				maxDomain = ageValues[ageValues.length-1];
				for (Object key : referenceData.keySet()){
					if (!key.toString().equals("age")){
						if (referenceData.get(key) != null){
							XYSeries series = new XYSeries(key.toString());
							double[] values = (double[])referenceData.get(key);
							for (int i = 0; i < values.length; i++)
								series.add(ageValues[i], values[i]);
							dataset.addSeries(series);
						}
					}
				}
				//add dummy series for upper limit
				XYSeries series = new XYSeries("Obese");
				for (int i = 0; i < ageValues.length; i++)
					series.add(ageValues[i], 100);
				dataset.addSeries(series);
			}
		}

		return dataset;

	}

	@Override
	public Dataset createDataset() {
		this.dataset = initDataset();
		if (percentiles.length == 0)
			percentiles = new int[]{5,25,50,75,95};
		//create series per percentile
		for (int i = 0; i < percentiles.length; i++)
			addSeriesToDataset(percentiles[i], percentiles[i]+"th perc." );

		return this.dataset;

	}

	@Override
	public Dataset initDataset() {
		return new XYSeriesCollection();
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
	public Dataset addSeriesToDataset(Object data, String seriesName) {

		if (this.data != null){
			try {
				XYSeries series = new XYSeries(seriesName);
				for (Object key : ((TreeMap<Object, Object>)this.data).keySet()){
					HistogramStats hs = (HistogramStats)((TreeMap<Object, Object>)this.data).get(key);
					//in this case, data is the value of the percentile
					double value = (double)hs.getPercentile((int)data);
					series.add((int)key, value);
				}
				((XYSeriesCollection)dataset).addSeries(series);
			}catch (Exception e) {
				cannotCreate(title, e);
			}
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

		XYDataset firstDataset = (referenceData != null ?
				createReferenceDataset() : (XYSeriesCollection)createDataset());
		chart = ChartFactory.createXYAreaChart(
				title,
				XLabel,
				YLabel,
				firstDataset,
				PlotOrientation.VERTICAL,
				showLegend,   // legend
				true,		  // tool tips
				false  		  // URLs
				);

		XYPlot plot = (XYPlot) chart.getPlot();

//		//no effect
//		plot.setDrawingSupplier(new DefaultDrawingSupplier(
//				new Paint[] {Color.GREEN, Color.YELLOW, Color.ORANGE, Color.RED},
//				DefaultDrawingSupplier.DEFAULT_OUTLINE_PAINT_SEQUENCE,
//				DefaultDrawingSupplier.DEFAULT_STROKE_SEQUENCE,
//				DefaultDrawingSupplier.DEFAULT_OUTLINE_STROKE_SEQUENCE,
//				DefaultDrawingSupplier.DEFAULT_SHAPE_SEQUENCE));

		XYItemRenderer areaRenderer = new XYAreaRenderer();
		//	plot.setForegroundAlpha(0.75f);
		plot.setBackgroundAlpha(0.75f);
		plot.setRenderer(areaRenderer);

		ValueAxis domainAxis = plot.getDomainAxis();
		domainAxis.setTickMarkPaint(Color.black);
		if (minDomain != -1 && maxDomain != -1)
			domainAxis.setRange(minDomain, maxDomain);

		ValueAxis rangeAxis = plot.getRangeAxis();
		if (minRange != -1 && maxRange != -1)
			rangeAxis.setRange(minRange, maxRange);
		rangeAxis.setTickMarkPaint(Color.black);

		if (referenceData != null && referenceData.size() > 0){
			XYDataset secondDataset = (XYDataset)createDataset();
			plot.setDataset(1,secondDataset);

			//trying to set colors.. no effect
			StandardXYItemRenderer lineRenderer = new StandardXYItemRenderer(
					StandardXYItemRenderer.LINES);
			if (percentiles.length > 0){
				for (int i = 0; i < percentiles.length; i++){
					lineRenderer.setSeriesStroke(i, new BasicStroke(2f));
					if (percentiles.length <= seriesColors.length)
						lineRenderer.setSeriesPaint(i, seriesColors[i]);
				}
			}

			plot.setRenderer(1, lineRenderer);
			plot.setDatasetRenderingOrder(DatasetRenderingOrder.FORWARD);
		}

		ChartUtilities.applyCurrentTheme(chart);

		TextTitle title = chart.getTitle();
		title.setFont(title.getFont().deriveFont(Font.BOLD,14.0f));
		chart.setTitle(title);
		if (!showLegend)
			chart.removeLegend();

		return chart;

	}

	//BUILDER
	public static class Builder extends Plot.Builder{

		private TreeMap<Object, Object> referenceData;
		private int[] percentiles;
		private double minRange;
		private double maxRange;
//		private double[] XTicks;
//		private double[] YTicks;

		public Builder(String title) {super(title);}

		public Builder referenceData(TreeMap<Object, Object> value) { referenceData = value; return this;}

		public Builder percentiles(int[] value) { percentiles = value; return this;}

		public Builder minRange(double value) { minRange = value; return this;}

		public Builder maxRange(double value) { maxRange = value; return this;}

//		public Builder XTicks(double[] value) { XTicks = value; return this;}

//		public Builder YTicks(double[] value) { YTicks = value; return this;}

		public Plot build() {return new BMIPlot(this);}
	}

}
