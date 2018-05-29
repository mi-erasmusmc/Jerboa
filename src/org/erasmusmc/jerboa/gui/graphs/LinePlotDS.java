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
import java.util.Map;
import java.util.TreeMap;
import java.util.Map.Entry;

import org.erasmusmc.jerboa.config.PropertiesManager;
import org.erasmusmc.jerboa.utilities.StringUtilities;
import org.jfree.chart.ChartFactory;
import org.jfree.chart.JFreeChart;
import org.jfree.chart.axis.NumberAxis;
import org.jfree.chart.axis.SymbolAxis;
import org.jfree.chart.plot.Marker;
import org.jfree.chart.plot.PlotOrientation;
import org.jfree.chart.plot.ValueMarker;
import org.jfree.chart.plot.XYPlot;
import org.jfree.chart.renderer.xy.XYItemRenderer;
import org.jfree.chart.title.TextTitle;
import org.jfree.data.general.Dataset;
import org.jfree.data.xy.XYDataset;
import org.jfree.data.xy.XYSeries;
import org.jfree.data.xy.XYSeriesCollection;
import org.jfree.ui.Layer;
import org.jfree.ui.RectangleAnchor;
import org.jfree.ui.TextAnchor;

/**
 * This class contains the necessary methods to create a line plot representation
 * of a discrete series. It assumes the data is under a {@literal Map<Object, Object>} structure.
 *
 * @author MG
 *
 */
@SuppressWarnings("serial")
public class LinePlotDS extends Plot {

	//graphical
	private XYPlot plot;
	private SymbolAxis domainAxis = null;
	private SymbolAxis rangeAxis = null;
	private float lineWidth = 2.0f;

	@Override
	public Dataset initDataset() {
		return new XYSeriesCollection();
	}

	/**
	 * Creates the JFreeChart object populated with data from dataset.
	 * @param dataset - the data that is to be plotted
	 * @return - a JFreeChart object representing the graph
	 */
	public JFreeChart createChart(Object dataset) {
		chart = ChartFactory.createXYLineChart(
				getTitle(),					// the anti matter destroying the world thingy
				XLabel,						// X axis label
				YLabel,						// Y axis label
				(XYDataset)dataset,			// the data
				PlotOrientation.VERTICAL,	//orientation
				showLegend,					//legend
				true,						//tool tip
				false						//URLs
				);

		plot = (XYPlot) chart.getPlot();
		plot.setDomainPannable(true);
		plot.setRangePannable(true);
		plot.setDomainCrosshairVisible(true);
		plot.setRangeCrosshairVisible(true);

		if (domainAxis != null){
			Font font = plot.getDomainAxis().getLabelFont();
			domainAxis.setLabelFont(font);
			plot.setDomainAxis(domainAxis);
		}else{
			NumberAxis numberAxis = (NumberAxis)plot.getDomainAxis();
			numberAxis.setUpperMargin(0.12D);
			numberAxis.setStandardTickUnits(NumberAxis.createIntegerTickUnits());
			numberAxis.setNumberFormatOverride(StringUtilities.DECIMAL_FORMAT);
		}
		if (rangeAxis != null){
			Font font = new Font("Dialog", Font.PLAIN, 12);
			rangeAxis.setLabelFont(font);
			plot.setRangeAxis(rangeAxis);
		}else{
			NumberAxis numberAxis = (NumberAxis)plot.getRangeAxis();
			numberAxis.setNumberFormatOverride(StringUtilities.DECIMAL_FORMAT);
			numberAxis.setAutoRangeIncludesZero(false);
		}

		//if only one series set the stroke
		XYItemRenderer renderer = plot.getRenderer();
		renderer.setSeriesStroke(0, new BasicStroke(lineWidth));

		TextTitle title = chart.getTitle();
		title.setFont(title.getFont().deriveFont(Font.BOLD,14.0f));
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
	@SuppressWarnings("unchecked")
	@Override
	public Dataset addSeriesToDataset(Object data, String seriesName){
		XYSeries series = new XYSeries(seriesName);
		try {
			int loop = 0;
			for (Entry<Object, Object> entry : ((Map<Object, Object>)data).entrySet()){
				//values for the X axis ticks can be other than numbers
				if (domainAxis != null)
					//so add a dummy series
					series.add(loop++, (Number)entry.getValue());
				//values for the X axis ticks can be other than numbers
				else if (rangeAxis != null)
					//so add a dummy series
					series.add((Number)entry.getKey(), loop++);
				else
					series.add((Number)entry.getKey(), (Number)entry.getValue());
			}

		}catch (Exception e) {
			cannotCreate(title,e);
		}

		((XYSeriesCollection)dataset).addSeries(series);
		setSeriesPaint(((XYSeriesCollection)dataset).getSeriesCount());

		return dataset;
	}

	//SPECIFIC METHODS
	/**
	 * Adds a vertical line to the discrete series plot having label as description.
	 * The marker will appear at value. Note that value is of double type.
	 * @param value - the point on the X axis where the vertical line should be drawn
	 * @param label - the label describing the marker.
	 */
	@Override
	public void addMarker(Object value, String label){
		if (plot != null){
			Marker marker = new ValueMarker((double)value, Color.BLACK, new BasicStroke(1.0f));
			marker.setLabel(label);
			marker.setLabelFont(new Font("Dialog", Font.PLAIN, 12));
			marker.setLabelAnchor(RectangleAnchor.TOP_RIGHT);
			marker.setLabelTextAnchor(TextAnchor.TOP_LEFT);
			plot.addDomainMarker(marker, Layer.BACKGROUND);
		}
	}

	/**
	 * Sets the color of a series based on the array of colors defined in the Plot
	 * super class. It also sets the width of the line based on the lineWidth parameter
	 * declared in this class. Keep in mind that the actual series is its index - 1.
	 * If the series index is larger than the actual number of colors defined in the superclass,
	 * the colors used will be from index 1 (cyclic).
	 * @param seriesIndex - the index of the series in the dataset
	 */
	private void setSeriesPaint(int seriesIndex){
		if (plot != null){
			int which = seriesIndex > seriesColor.length ? (seriesIndex - seriesColor.length)-1 : seriesIndex -1;
			XYItemRenderer renderer = plot.getRenderer();
			renderer.setSeriesPaint(which, seriesColor[which]) ;
			renderer.setSeriesStroke(which, new BasicStroke(lineWidth));
			plot.setRenderer(renderer);
		}
	}

	/**
	 * Sets the ticks for the X axis as they can be other than numbers.
	 * This is necessary as using a DefaultCategoryDataset with CategoryAxis will
	 * try to display all the labels and if they are too long only "... ..." are shown.
	 * Overriding classes is necessary to make the chart skip some labels.
	 * @return - a new SymbolAxis with the ticks to be displayed on the X axis.
	 */
	@SuppressWarnings("unchecked")
	private SymbolAxis setDomainAxis(){
		if (data != null){
			Object[] keys = ((TreeMap<Object, Object>)data).keySet().toArray();
			if (keys[0] instanceof Integer || keys[0] instanceof Double|| keys[0] instanceof Long){
				return null;
			}else{
				String[] ticks = new String[keys.length];
				for (int i = 0; i < keys.length; i ++)
					ticks[i] = keys[i].toString();
				return new SymbolAxis(XLabel, ticks);
			}
		}

		return null;
	}

	/**
	 * Sets the ticks for the Y axis as they can be other than numbers.
	 * This is necessary as using a DefaultCategoryDataset with CategoryAxis will
	 * try to display all the labels and if they are too long only "... ..." are shown.
	 * Overriding classes is necessary to make the chart skip some labels.
	 * @return - a new SymbolAxis with the ticks to be displayed on the X axis.
	 */
	@SuppressWarnings("unchecked")
	private SymbolAxis setRangeAxis(){
		if (data != null){
			Object[] values = ((TreeMap<Object, Object>)data).values().toArray();
			if (values[0] instanceof Integer || values[0] instanceof Double|| values[0] instanceof Long){
				return null;
			}else{
				String[] ticks = new String[values.length];
				for (int i = 0; i < values.length; i ++)
					ticks[i] = values[i].toString();
				return new SymbolAxis(XLabel, ticks);
			}
		}

		return null;
	}

	//BUILDER
	public static class Builder extends Plot.Builder{

		private Object[] XAxisTicks;
		private Object[] YAxisTicks;

		public Builder(String title) {super(title);}

		public Builder XAxisTicks(Object[] value) {
			XAxisTicks = new String[value.length];
			for (int i = 0; i < value.length; i ++)
				XAxisTicks[i] = value[i].toString();
		return this;
		}

		public Builder YAxisTicks(Object[] value) {
			YAxisTicks = new String[value.length];
			for (int i = 0; i < value.length; i ++)
				YAxisTicks[i] = value[i].toString();
		return this;
		}

		public LinePlotDS build() {return new LinePlotDS(this);}
	}

	public LinePlotDS(Builder builder) {
		super(builder);
		if (builder.XAxisTicks != null)
			domainAxis = new SymbolAxis(XLabel,(String[])builder.XAxisTicks);
		else
			domainAxis = setDomainAxis();

		if (builder.YAxisTicks != null)
			rangeAxis = new SymbolAxis(YLabel,(String[])builder.YAxisTicks);
		else
			rangeAxis = setRangeAxis();
		createPanel();
	}

	/**
	 * Main method for debug and testing.
	 * @param args - none
	 */
	public static void main(String[] args){
		new PropertiesManager();

		Map<Object, Object> data = new TreeMap<Object, Object>();
		data.put(3.2,2);
		data.put(13.2,2);
		data.put(1243.2,9);
		data.put(33.2,7);
		data.put(73.2,4);
		data.put(43.2,2);
		data.put(53.2,20);
		data.put(23.2,1);

		Plot plot = new LinePlotDS.Builder("Test plot")

		.data(data)
		.seriesName("Bla")
		.XLabel("Funky")
		.YLabel("Chicken")
		.build();

		data =  new TreeMap<Object, Object>();
		data.put(11,3);
		data.put(256,10);

		plot.addMarker(35.0, "Shht");
		plot.addSeriesToDataset(data, "Test");

		plot.setLocationRelativeTo(null);
		plot.pack();
		plot.setVisible(true);
	}

}

