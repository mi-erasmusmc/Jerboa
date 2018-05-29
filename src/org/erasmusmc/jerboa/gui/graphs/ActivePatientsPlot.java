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

import java.text.DecimalFormat;
import java.text.SimpleDateFormat;
import java.util.TreeSet;
import java.awt.Font;

import org.jfree.chart.ChartFactory;
import org.jfree.chart.ChartUtilities;
import org.jfree.chart.JFreeChart;
import org.jfree.chart.axis.DateAxis;
import org.jfree.chart.axis.DateTickMarkPosition;
import org.jfree.chart.labels.StandardXYToolTipGenerator;
import org.jfree.chart.plot.PlotOrientation;
import org.jfree.chart.plot.XYPlot;
import org.jfree.chart.renderer.xy.ClusteredXYBarRenderer;
import org.jfree.chart.title.TextTitle;
import org.jfree.data.general.Dataset;
import org.jfree.data.time.TimeSeries;
import org.jfree.data.time.TimeSeriesCollection;
import org.jfree.data.time.Year;
import org.jfree.data.xy.IntervalXYDataset;


import org.erasmusmc.jerboa.config.DataDefinition;
import org.erasmusmc.jerboa.utilities.ExtendedMultiKey;
import org.erasmusmc.jerboa.utilities.MultiKeyBag;

/**
 * This class contains the necessary methods to create a graphical representation
 * of the active patient population from the input dataset. The number of active patients
 * per calendar year, grouped by gender is shown as a clustered bar plot along their observation period.
 *
 * @author MG
 *
 */
@SuppressWarnings("serial")
public class ActivePatientsPlot extends Plot {

	/**
	 * Constructor receiving the bag containing the counts of the active patients and the title of the plot.
	 * @param bag - the data to be plotted
	 * @param title - the title of the plot
	 */
	public ActivePatientsPlot(MultiKeyBag bag, String title) {
		super(title);
		this.data = bag;
		this.doNotExport = true;
		createPanel();
	}

	@Override
	public Dataset initDataset() {
		return new TimeSeriesCollection();
	}

	/**
	 * Graph constructor. Initializes the chart object and feeds the data present in dataset.
	 * @param dataset - the list of active patients per years
	 * @return - the chart holding the bar plot of the patient population
	 */
	@Override
	public JFreeChart createChart(Object dataset) {
		JFreeChart chart = ChartFactory.createXYBarChart(
				title,
				"Year",
				true,
				"Nb. patients",
				(IntervalXYDataset)dataset,
				PlotOrientation.VERTICAL,
				true,
				false,
				false
				);

		//set the renderer
		XYPlot plot = (XYPlot) chart.getPlot();
		plot.setDomainPannable(true);
		plot.setRangePannable(true);
		ClusteredXYBarRenderer renderer = new ClusteredXYBarRenderer(0.0, false);
		plot.setRenderer(renderer);
		renderer.setDrawBarOutline(false);

		StandardXYToolTipGenerator generator = new StandardXYToolTipGenerator(
				"{1} = {2}", new SimpleDateFormat("yyyy"), new DecimalFormat("0"));
		renderer.setBaseToolTipGenerator(generator);
		renderer.setMargin(0.10);

		//set axis
		DateAxis axis = (DateAxis) plot.getDomainAxis();
		axis.setTickMarkPosition(DateTickMarkPosition.MIDDLE);
		axis.setLowerMargin(0.01);
		axis.setUpperMargin(0.01);

		ChartUtilities.applyCurrentTheme(chart);

		TextTitle title = chart.getTitle();
		title.setFont(title.getFont().deriveFont(Font.BOLD,18.0f));
		chart.setTitle(title);

		return chart;
	}

	@Override
	public Dataset addSeriesToDataset(Object data, String seriesName) {
		TimeSeries males = new TimeSeries("Males", "Year", "Count");
		TimeSeries females = new TimeSeries("Females", "Year", "Count");

		//retrieve the sorted key set
		TreeSet<ExtendedMultiKey> keySet = ((MultiKeyBag)data).getSortedKeySet();
		try {
			for (ExtendedMultiKey key : keySet){
				//add them to the series
				if (key.getKey(0).equals(DataDefinition.MALE_GENDER))
					males.add(new Year(Integer.valueOf(key.getKey(1).toString())),((MultiKeyBag)data).getCount(key));
				else
					females.add(new Year(Integer.valueOf(key.getKey(1).toString())),((MultiKeyBag)data).getCount(key));
			}
		}catch (Exception e) {
			cannotCreate("Active patients", e);
		}

		((TimeSeriesCollection)dataset).addSeries(females);
		((TimeSeriesCollection)dataset).addSeries(males);

		return dataset;
	}

}