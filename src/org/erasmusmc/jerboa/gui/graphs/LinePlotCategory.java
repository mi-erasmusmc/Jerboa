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
import java.awt.geom.Ellipse2D;
import java.util.HashMap;
import java.util.Map;
import java.util.TreeMap;
import java.util.Map.Entry;

import org.erasmusmc.jerboa.config.PropertiesManager;
import org.erasmusmc.jerboa.utilities.StringUtilities;
import org.jfree.chart.ChartFactory;
import org.jfree.chart.JFreeChart;
import org.jfree.chart.axis.CategoryAxis;
import org.jfree.chart.axis.NumberAxis;
import org.jfree.chart.axis.SymbolAxis;
import org.jfree.chart.plot.CategoryMarker;
import org.jfree.chart.plot.CategoryPlot;
import org.jfree.chart.plot.PlotOrientation;
import org.jfree.chart.renderer.category.LineAndShapeRenderer;
import org.jfree.chart.title.TextTitle;
import org.jfree.data.category.CategoryDataset;
import org.jfree.data.category.DefaultCategoryDataset;
import org.jfree.data.general.Dataset;
import org.jfree.ui.Layer;
import org.jfree.ui.RectangleAnchor;
import org.jfree.ui.RectangleInsets;
import org.jfree.ui.TextAnchor;

/**
 * This class contains the necessary methods to create a category line plot.
 * It assumes the data is under a {@literal Map<Object, Object>} structure.
 *
 * @author MG
 *
 */
@SuppressWarnings("serial")
public class LinePlotCategory extends Plot {

	//graphical
	private CategoryPlot plot;
	private CategoryAxis domainAxis = null;
	private SymbolAxis rangeAxis = null;
	private float lineWidth = 2.0f;

	private boolean lowerMarkerLabel;

	public static boolean sortByValue;

	@Override
	public Dataset initDataset() {
		return new DefaultCategoryDataset();
	}

	/**
	 * Creates the JFreeChart object populated with data from dataset.
	 * @param dataset - the data that is to be plotted
	 * @return - a JFreeChart object representing the graph
	 */
	public JFreeChart createChart(Object dataset) {
		chart = ChartFactory.createLineChart(
				getTitle(),					// the anti matter destroying the world thingy
				XLabel,						// X axis label
				YLabel,						// Y axis label
				(CategoryDataset)dataset,	// the data
				PlotOrientation.VERTICAL,	//orientation
				showLegend,					//legend
				true,						//tool tip
				false						//URLs
				);

		plot = (CategoryPlot) chart.getPlot();
		plot.setRangePannable(true);
		plot.setDomainCrosshairVisible(true);
		plot.setRangeCrosshairVisible(true);
        plot.setRangeGridlinesVisible(false);

		if (domainAxis != null){
			Font font = plot.getDomainAxis().getLabelFont();
			domainAxis.setLabelFont(font);
			plot.setDomainAxis(domainAxis);
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
  	    LineAndShapeRenderer renderer = (LineAndShapeRenderer) plot.getRenderer();
  	    renderer.setBaseShapesVisible(true);
  	    renderer.setDrawOutlines(true);
  	    renderer.setUseFillPaint(true);
  	    renderer.setBaseFillPaint(Color.white);
        renderer.setSeriesStroke(0, new BasicStroke(lineWidth));
  	    renderer.setSeriesOutlineStroke(0, new BasicStroke(1.5f));
  	    renderer.setSeriesShape(0, new Ellipse2D.Double(-5.0, -5.0, 5.0, 5.0));

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
	@SuppressWarnings({ "unchecked", "rawtypes" })
	@Override
	public Dataset addSeriesToDataset(Object data, String seriesName){

		//sort if needed
		if (sortByValue)
			data = sortMapByValues((Map)data);
		//populate series
		try{
			for (Entry<Object, Object> entry : ((Map<Object, Object>)data).entrySet()){
				((DefaultCategoryDataset)dataset).addValue((Number) entry.getValue(), seriesName, entry.getKey().toString());
			}
		}catch(Exception e){
			cannotCreate(title, e);
		}

		setSeriesPaint(((CategoryDataset)dataset).getRowKeys().size());
		return dataset;
	}

	/**
	 * Adds value to this dataset in the series seriesName with category key.
	 * @param value - the value to be added
	 * @param key - the category
	 * @param seriesName - the name of the series for which the value is to be added
	 * @return - the updated dataset
	 */
	public Dataset addValueToDataset(Object value, Object key, String seriesName){
		try{
			((DefaultCategoryDataset)dataset).addValue((Number)value, seriesName, key.toString());
		}catch(Exception e){
			cannotCreate(title, e);
		}

		return dataset;
	}

	/**
	 * Adds a vertical line to the category plot having label as description.
	 * The marker will appear at category. Note that category is a String.
	 * @param category - the point on the X axis where the vertical line should be drawn
	 * @param label - the label describing the marker.
	 */
	@Override
	public void addMarker(Object category, String label){
		if (plot != null){
			CategoryMarker marker = new CategoryMarker((String)category, Color.BLACK, new BasicStroke(1.0f));
			marker.setDrawAsLine(true);
			marker.setLabel(label);
			marker.setLabelFont(new Font("Dialog", Font.PLAIN, 12));
			marker.setLabelAnchor(RectangleAnchor.TOP_RIGHT);
			marker.setLabelTextAnchor(TextAnchor.TOP_LEFT);
			marker.setLabelOffset(new RectangleInsets((lowerMarkerLabel ? 20 : 2), 5, 2, 5));
			lowerMarkerLabel = ! lowerMarkerLabel;
			plot.addDomainMarker(marker, Layer.BACKGROUND);
		}
	}

	@SuppressWarnings("unchecked")
	@Override
	public void sortByName(){
		this.data = new TreeMap<String, Object>((Map<String,Object>)data);
		this.dataset = createDataset();
	}

	//SPECIFIC METHODS
	/**
	 * Sets the color of a series based on the array of colors defined in the Plot
	 * super class. It also sets the width of the line based on the lineWidth parameter
	 * declared in this class. Keep in mind that the actual series is its index - 1.
	 * If the series index is larger than the actual number of colors defined in the superclass,
	 * the total number of colors is subtracted and the color at the corresponding resulting index is used.
	 * @param seriesIndex - the index of the series in the dataset
	 */
	private void setSeriesPaint(int seriesIndex){
		if (plot != null){
			int which = seriesIndex > seriesColor.length ? (seriesIndex - seriesColor.length)-1 : seriesIndex -1;
			LineAndShapeRenderer renderer = (LineAndShapeRenderer)plot.getRenderer();
			renderer.setSeriesPaint(which, seriesColor[which]) ;
			renderer.setSeriesStroke(which, new BasicStroke(lineWidth));
			plot.setRenderer(renderer);
		}
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
			Object[] values = ((Map<Object, Object>)data).values().toArray();
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

		private Object[] YAxisTicks;

		public Builder(String title) {super(title);}

		public Builder YAxisTicks(Object[] value) {
			YAxisTicks = new String[value.length];
			for (int i = 0; i < value.length; i ++)
				YAxisTicks[i] = value[i].toString();
		return this;
		}

		public Builder sortByValue(boolean value) { sortByValue = value; return this;}

		public LinePlotCategory build() {return new LinePlotCategory(this);}
	}

	public LinePlotCategory(Builder builder) {
		super(builder);

		if (builder.YAxisTicks != null)
			rangeAxis = new SymbolAxis(YLabel,(String[])builder.YAxisTicks);
		else
			rangeAxis = setRangeAxis();

//		sortOption = true;

		createPanel();
	}

	/**
	 * Main method for testing and debugging.
	 * @param args - none
	 */
	public static void main(String[] args){
		new PropertiesManager();

		Map<Object, Object> data = new HashMap<Object, Object>();
		data.put("one",2);
		data.put("two",2);
		data.put("-1",9);
		data.put("pork",7);
		data.put("corn",4);
		data.put("yes",2);
		data.put("why",20);
		data.put("mhm",1);

		Plot plot = new LinePlotCategory.Builder("Test plot")

		.sortByValue(true)
		.showLegend(true)
		.data(data)
		.seriesName("Blast")
		.XLabel("Funky")
		.YLabel("Chicken")
		.build();

		data =  new HashMap<Object, Object>();
		data.put(11,3);
		data.put(256,10);

		((LinePlotCategory)plot).addMarker("pork", "This is a marker");
		plot.addSeriesToDataset(data, "Test");

		plot.setLocationRelativeTo(null);
		plot.pack();
		plot.setVisible(true);
	}
}

