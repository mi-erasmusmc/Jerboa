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
import java.text.NumberFormat;
import java.util.Arrays;
import java.util.Map;
import java.util.TreeMap;

import org.apache.commons.collections.bag.HashBag;
import org.jfree.chart.ChartFactory;
import org.jfree.chart.JFreeChart;
import org.jfree.chart.labels.StandardPieSectionLabelGenerator;
import org.jfree.chart.plot.PiePlot;
import org.jfree.data.general.Dataset;
import org.jfree.data.general.DefaultPieDataset;
import org.jfree.data.general.PieDataset;

/**
 * This class contains the necessary methods to create
 * a pie chart representation from the contents of a map.
 *
 * @author MG
 *
 */
@SuppressWarnings("serial")
public class PieChart extends Plot {

	/**
	 * Constructor receiving a builder object to ease initialization
	 * with many parameters and creating the plot panel.
	 * @param builder - the builder of this object
	 */
	public PieChart(Builder builder) {
		super(builder);
		createPanel();
	}

	//BUILDER
	public static class Builder extends Plot.Builder{

		public Builder(String title) {super(title);	}

		public PieChart build() {return new PieChart(this);}
	}

	@Override
	public Dataset initDataset() {
		return new DefaultPieDataset();
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
	@SuppressWarnings("unchecked")
	public Dataset addSeriesToDataset(Object data, String seriesName) {
		try{
			if (data instanceof HashBag){
				Object[] keySet = ((HashBag)data).uniqueSet().toArray();
				Arrays.sort(keySet);
				for (Object key : keySet)
					((DefaultPieDataset)dataset).setValue(key.toString(),
							(Number)(((HashBag)data).getCount(key)));
			}
			if (data instanceof Map<?, ?>){
				for (Object key : ((TreeMap<Object, Object>)data).keySet())
					((DefaultPieDataset)dataset).setValue(key.toString(),
							(Number)(((TreeMap<Object, Object>)data).get(key)));
			}
		}catch(Exception e){
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

		JFreeChart chart = ChartFactory.createPieChart(
				title, 					 // chart title
				(PieDataset)dataset,     // data
				showLegend,            	 // include legend
				true,					 // tool tips
				false);					 // URLs

		// TextTitle title = chart.getTitle();
		// title.setToolTipText("Title/Plot description");

		PiePlot plot = (PiePlot) chart.getPlot();
		plot.setLabelFont(new Font("Dialog", Font.PLAIN, 12));
		plot.setLabelGenerator(new StandardPieSectionLabelGenerator("{0} : {2}",
	                NumberFormat.getNumberInstance(),
	                NumberFormat.getPercentInstance()));
		plot.setNoDataMessage("No data available");
		plot.setCircular(false);
		plot.setLabelGap(0.02);
		return chart;
	}

}
