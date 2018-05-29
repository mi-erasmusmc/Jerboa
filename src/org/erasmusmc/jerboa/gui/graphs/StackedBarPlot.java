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

import java.util.TreeMap;

import org.jfree.chart.ChartFactory;
import org.jfree.chart.JFreeChart;
import org.jfree.chart.axis.CategoryAxis;
import org.jfree.chart.axis.CategoryLabelPositions;
import org.jfree.chart.plot.CategoryPlot;
import org.jfree.chart.plot.PlotOrientation;
import org.jfree.chart.renderer.category.StackedBarRenderer;
import org.jfree.chart.renderer.category.StandardBarPainter;
import org.jfree.data.category.CategoryDataset;
import org.jfree.data.category.DefaultCategoryDataset;
import org.jfree.data.general.Dataset;

/**
 * This class contains all the necessary methods to create a stacked bar plot.
 * It assumes the data is under a {@literal TreeMap<Object, TreeMap<Object, Object>} representation.
 * Each bar is created from one entry of the TreeMap containing as value another
 * {@literal TreeMap<Object, Object>} with the values forming a stacked bar.
 *
 * @author MG
 *
 */
@SuppressWarnings("serial")
public class StackedBarPlot extends Plot {

	/**
	 * Constructor receiving a builder object to ease initialization
	 * with many parameters and creating the plot panel.
	 * @param builder - the builder of this object
	 */
	public StackedBarPlot(Builder builder) {
		super(builder);
		createPanel();
	}

	//BUILDER
	public static class Builder extends Plot.Builder{

		public Builder(String title) {super(title);}

		public StackedBarPlot build() {return new StackedBarPlot(this);}
	}

	@Override
	public Dataset initDataset() {
		return new DefaultCategoryDataset();
	}

	/**
	 * Graph constructor. Initializes the graph components
	 * and feeds it with the data.
	 * @param dataset - the data to be plotted
	 * @return - the panel holding the plot
	 */
	@Override
	public JFreeChart createChart(Object dataset) {

		chart = ChartFactory.createStackedBarChart(
				title,                      // chart title
				XLabel,                     // domain axis label
				YLabel,                     // range axis label
				(CategoryDataset)dataset,   // data
				PlotOrientation.VERTICAL,   // the plot orientation
				showLegend,                 // legend
				true,                       // tooltips
				false                       // urls
				);

		CategoryPlot plot = (CategoryPlot) chart.getPlot();

		CategoryAxis domainAxis = plot.getDomainAxis();
		domainAxis.setCategoryLabelPositions(CategoryLabelPositions.DOWN_90);

		StackedBarRenderer renderer = (StackedBarRenderer)plot.getRenderer();
		renderer.setDrawBarOutline(false);
		renderer.setMaximumBarWidth(0.20);
		renderer.setBarPainter(new StandardBarPainter());

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
	public Dataset addSeriesToDataset(Object data, String seriesName) {
		try{
			for (Object key : ((TreeMap<Object, Object>)data).keySet()){
				Object subMap = ((TreeMap<Object, Object>)data).get(key);
				for (Object subkey : ((TreeMap<Object, Object>)subMap).keySet()){
					((DefaultCategoryDataset)dataset).addValue((Number) ((TreeMap<Object, Object>)subMap).get(subkey),
							key.toString(), subkey.toString());
				}
			}
		}catch(Exception e){
			cannotCreate(title, e);
		}

		return dataset;
	}

}
