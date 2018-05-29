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

package org.erasmusmc.jerboa.utilities;
/******************************************************************************************
 * Jerboa software version 3.0 - Copyright Erasmus University Medical Center, Rotterdam   *
 *																				          *
 * Author: Marius Gheorghe (MG) - department of Medical Informatics						  *
 * 																						  *
 * $Rev:: 3699              $:  Revision of last commit                                   *
 * $Author:: root           $:  Author of last commit                                     *
 * $Date:: 					$:  Date and time (CET) of last commit						  *
 ******************************************************************************************/

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import org.apache.commons.lang3.text.StrBuilder;
import org.erasmusmc.jerboa.Jerboa;
import org.erasmusmc.jerboa.gui.JerboaGUI;
import org.erasmusmc.jerboa.gui.graphs.Graphs;
import org.erasmusmc.jerboa.gui.graphs.LinePlotCategory;
import org.erasmusmc.jerboa.gui.graphs.Plot;

/**
 * This class creates a profile of the memory usage wherever it is used in the application.
 * The user can choose to profile the free, used, total and/or maximum memory.
 * It provides graphical as well as textual output. Its main use is for debugging purposes.
 *
 * @author MG
 *
 */
public class MemoryBenchmark {

	//choices
	public static final int USED_MEMORY = 0;
	public static final int FREE_MEMORY = 1;
	public static final int ALLOCATED_MEMORY = 2;
	public static final int MAXIMUM_MEMORY = 3;
	public static final int ALL = 4;

	private int unit = 1024; //in KB by default

	private Plot plot;
	private LinkedHashMap<Entry, Object> data = new LinkedHashMap<Entry, Object>();

	//builder parameters
	private boolean inMB = true;
	private int which = USED_MEMORY;
	private boolean verbose = Jerboa.inConsoleMode;
	private boolean withPlots = true;
	private boolean showAsResultViewer;

	//CONSTRUCTOR
	public MemoryBenchmark(Builder builder) {

		which = builder.which;
		inMB = builder.inMB;
		verbose = builder.verbose;
		withPlots = builder.withPlots;
		showAsResultViewer = builder.displayInViewer;

		if (inMB)
			unit *= 1024;

		if (which == -1)
			which = ALL;

		if (Jerboa.inConsoleMode)
			withPlots = false;
	}

	/**
	 * Displays on the standard output the memory usage at the current time.
	 * Which information is displayed depends on the choice
	 * the user made during the object initialization.
	 * Note that only if verbose is true or in console mode
	 * this method will print to the console.
	 */
	public void displayUsage(){
		if (verbose || Jerboa.inConsoleMode){
			Logging.addNewLine();
			Logging.add("Current memory usage in "+(inMB ? "MB" : "KB"));
			Logging.add("-------------------------------------------");
			switch (which){
			case USED_MEMORY :
				Logging.add("Used Memory: "+ MemoryUtilities.getUsedMemory());
				break;
			case FREE_MEMORY :
				Logging.add("Free Memory: "+ MemoryUtilities.getFreeMemory());
			break;
			case ALLOCATED_MEMORY :
				Logging.add("Allocated Memory: " + MemoryUtilities.getAllocatedMemory());
			break;
			case MAXIMUM_MEMORY :
				Logging.add("Max Memory: " + MemoryUtilities.getMaxMemory());
			break;
			case ALL :
				Logging.add("Free Memory: "+ MemoryUtilities.getFreeMemory());
				Logging.add("Used Memory: "+ MemoryUtilities.getUsedMemory());
				Logging.add("Allocated Memory: " + MemoryUtilities.getAllocatedMemory());
				Logging.add("Max Memory: " + MemoryUtilities.getMaxMemory());
			break;
			}
			Logging.add("-------------------------------------------");
		}
	}

	/**
	 * Adds to the data structure the new values of the memory usage
	 * depending on which type was selected and updates the plots.
	 * @param mark - the X axis tick mark to be added in the map
	 */
	public void update(String mark){
		switch (which){
		case USED_MEMORY :
			data.put(new Entry(USED_MEMORY, mark),MemoryUtilities.getUsedMemory());
			updatePlot(mark);
			break;
		case FREE_MEMORY :
			data.put(new Entry(FREE_MEMORY, mark),MemoryUtilities.getFreeMemory());
			updatePlot(mark);
		break;
		case ALLOCATED_MEMORY :
			data.put(new Entry(ALLOCATED_MEMORY, mark),MemoryUtilities.getAllocatedMemory());
			updatePlot(mark);
		break;
		case MAXIMUM_MEMORY :
			data.put(new Entry(MAXIMUM_MEMORY, mark),MemoryUtilities.getMaxMemory());
			updatePlot(mark);
		break;
		case ALL :
			data.put(new Entry(USED_MEMORY, mark),MemoryUtilities.getUsedMemory());
			data.put(new Entry(FREE_MEMORY, mark),MemoryUtilities.getFreeMemory());
			data.put(new Entry(ALLOCATED_MEMORY, mark),MemoryUtilities.getAllocatedMemory());
			data.put(new Entry(MAXIMUM_MEMORY, mark),MemoryUtilities.getMaxMemory());
			updatePlots(mark);
		break;
		}
	}

	/**
	 * Returns a string representation of the information contained in data.
	 * Which information is returned depends on the choice the user made
	 * while initializing the object.
	 */
	public String toString(){
		StrBuilder out = new StrBuilder();
		switch (which){
		case USED_MEMORY :
			out.appendln("Used memory measurements in "+(inMB ? "MB" : "KB")+": ");
			out.appendln(histogramToString(getHistogram(USED_MEMORY)));
			break;
		case FREE_MEMORY :
			out.appendln("Free memory measurements in "+(inMB ? "MB" : "KB")+": ");
			out.appendln(histogramToString(getHistogram(FREE_MEMORY)));
		break;
		case ALLOCATED_MEMORY :
			out.appendln("Allocated memory measurements in "+(inMB ? "MB" : "KB")+": ");
			out.appendln(histogramToString(getHistogram(ALLOCATED_MEMORY)));
		break;
		case MAXIMUM_MEMORY :
			out.appendln("Maximum memory measurements in "+(inMB ? "MB" : "KB")+": ");
			out.appendln(histogramToString(getHistogram(MAXIMUM_MEMORY)));
		break;
		case ALL :
			out.appendln("Used memory measurements in "+(inMB ? "MB" : "KB")+": ");
			out.appendln(histogramToString(getHistogram(USED_MEMORY)));
			out.appendln("-------------------------------------------");
			out.appendln("Free memory measurements in "+(inMB ? "MB" : "KB")+": ");
			out.appendln(histogramToString(getHistogram(FREE_MEMORY)));
			out.appendln("-------------------------------------------");
			out.appendln("Allocated memory measurements in "+(inMB ? "MB" : "KB")+": ");
			out.appendln(histogramToString(getHistogram(ALLOCATED_MEMORY)));
			out.appendln("-------------------------------------------");
			out.appendln("Maximum memory measurements in "+(inMB ? "MB" : "KB")+": ");
			out.appendln(histogramToString(getHistogram(MAXIMUM_MEMORY)));
		break;
		}
		out.appendln("-------------------------------------------");

		return out.toString();

	}

	/**
	 * Creates line plots for the chosen memory usage type.
	 * If the displayInViewer flag is set to true, then the plots are added in
	 * the Graphs manager of the application and displayed in a tab on the GUI.
	 * The default behavior is to create a frame for each memory usage plot.
	 * @param which - the type of memory usage to be plotted
	 */
	public void plot(int which){
	if (!Jerboa.inConsoleMode){
			switch (which){
			case USED_MEMORY :
				createPlot("Used memory");
				break;
			case FREE_MEMORY :
				createPlot("Free memory");
				break;
			case ALLOCATED_MEMORY :
				createPlot("Allocated memory");
				break;
			case MAXIMUM_MEMORY :
				createPlot("Maximum memory");
				break;
			case ALL :
				createPlot("Memory usage");
				break;
			}
		}
	}

	/**
	 * Wrapper of the plot(which) method.
	 */
	public void plot(){
		plot(which);
	}

	/**
	 * Wrapper of the plot(which) method to display plots
	 * for all the types of memory usage.
	 */
	public void plotAll(){
		plot(ALL);
	}

	/**
	 * Adds a vertical line as a marker on the plot.
	 * @param value - the value on X axis at which the marker should appear
	 * @param label - the description of the marker
	 */
	public void putMarker(String value, String label){
		plot.addMarker(value, label);
	}

	//SPECIFIC METHODS
	/**
	 * Returns a string representation of a histogram of memory usage.
	 * @param histogram - the histogram to be represented as string
	 * @return - a string representation of histogram
	 */
	private String histogramToString(Map<Object, Object> histogram){
		if (histogram != null && histogram.size() > 0){
			StrBuilder s = new StrBuilder();
			for (Object key : histogram.keySet())
				s.appendln("at mark \""+(String)key+"\" value "+histogram.get(key).toString());
			return s.toString();
		}

		return "NO DATA";
	}

	/**
	 * Returns the histogram of the memory usage values which are of interest.
	 * @param which - the type of memory usage. One of the four declared in the
	 * beginning of the class or all.
	 * @return - a map containing the histogram of memory usage
	 */
	private Map<Object, Object> getHistogram(int which){
		if (data != null && data.size() > 0){
			LinkedHashMap<Object, Object> histo = new LinkedHashMap<Object, Object>();
			for (Entry entry : data.keySet())
				if (entry.getUsageType() == which)
					histo.put(entry.getMark(), data.get(entry));
			return histo;
		}

		return null;
	}

	/**
	 * Initializes and populates the dataset of the plot with the
	 * required information from data.
	 * @param title - the title of the plot.
	 */
	private void createPlot(String title){
		if (withPlots){
			if (which != ALL){
				plot = new LinePlotCategory.Builder(title+" in "+(inMB ? "MB" : "KB")).
						showLegend(false).XLabel("Measurements").YLabel("Value").data(getHistogram(which)).build();
			}else{
				plot = new LinePlotCategory.Builder("Memory usage in "+(inMB ? "MB" : "KB")).
						showLegend(true).XLabel("Measurements").YLabel("Value").build();
				plot.addSeriesToDataset(getHistogram(USED_MEMORY), "Used");
//				plot.addSeriesToDataset(getHistogram(FREE_MEMORY), "Free");
				plot.addSeriesToDataset(getHistogram(ALLOCATED_MEMORY), "Allocated");
//				plot.addSeriesToDataset(getHistogram(MAXIMUM_MEMORY), "Maximum");
			}

			showPlot();
		}
	}

	/**
	 * Updates one plot depending which memory usage type was selected.
	 * A new value is added to the plot dataset and the plot refreshed.
	 * If the plot was not yet created, it will be created.
	 * @param mark - the X tick mark label
	 */
	private void updatePlot(String mark){
		if (withPlots){
			if (plot == null){
				plot = new LinePlotCategory.Builder(which == USED_MEMORY ? "Used" : which == FREE_MEMORY ?
						"Free" : which == ALLOCATED_MEMORY ? "Allocated" : "Maximum"+" memory in "+(inMB ? "MB" : "KB")).
						showLegend(false).XLabel("Measurements").YLabel("Value").data(getHistogram(which)).build();
				showPlot();
			}else{
				long data = Long.MIN_VALUE;
				switch (which){
				case USED_MEMORY :
					data = MemoryUtilities.getUsedMemory();
					break;
				case FREE_MEMORY :
					data = MemoryUtilities.getFreeMemory();
					break;
				case ALLOCATED_MEMORY :
					data = MemoryUtilities.getAllocatedMemory();
					break;
				case MAXIMUM_MEMORY :
					data = MemoryUtilities.getMaxMemory();
					break;
				}
				((LinePlotCategory)plot).addValueToDataset(data, mark, "");
			}
		}
	}

	/**
	 * Updates all the plots depending on the desired memory usage type.
	 * If the plot was not yet created, it will be created.
	 * @param mark - the X tick mark label
	 */
	private void updatePlots(String mark){
		if (withPlots){
			if (which != ALL){
				updatePlot(mark);
			}else{
				if (plot == null){
					plot = new LinePlotCategory.Builder("Memory usage in "+(inMB ? "MB" : "KB")).
							showLegend(true).XLabel("Measurements").YLabel("Value").build();
					plot.addSeriesToDataset(getHistogram(USED_MEMORY), "Used");
//					plot.addSeriesToDataset(getHistogram(FREE_MEMORY), "Free");
					plot.addSeriesToDataset(getHistogram(ALLOCATED_MEMORY), "Allocated");
//					plot.addSeriesToDataset(getHistogram(MAXIMUM_MEMORY), "Maximum");
				}else{
					((LinePlotCategory)plot).addValueToDataset(MemoryUtilities.getUsedMemory(), mark, "Used");
//					((LinePlotCategory)plot).addValueToDataset(MemoryUtilities.getFreeMemory(), mark, "Free");
					((LinePlotCategory)plot).addValueToDataset(MemoryUtilities.getAllocatedMemory(), mark, "Allocated");
//					((LinePlotCategory)plot).addValueToDataset(getMaxMemory(), mark, "Maximum");
				}
				showPlot();
			}
		}
	}

	/**
	 * Prepares and displays a plot as an independent frame.
	 */
	private void showPlot(){
		if (!showAsResultViewer){
			plot.setLocationRelativeTo(JerboaGUI.frame);
			plot.pack();
			plot.setVisible(true);
		}else{
			if (Graphs.getViewer("Memory usage") == null)
					Graphs.addPlot("Memory usage", "Memory usage", plot);
		}
	}

	//BUILDER
	public static class Builder{

		private int which = -1;
		private boolean inMB;
		private boolean verbose;
		private boolean withPlots = true;
		private boolean displayInViewer;

		public Builder() {}

		public Builder which(int value) { which = value; return this;}
		public Builder inMB(boolean value) { inMB = value; return this;}
		public Builder verbose(boolean value) { verbose = value; return this;}
		public Builder withPlots(boolean value) { withPlots = value; return this;}
		public Builder displayInViewer(boolean value) { displayInViewer = value; return this;}

		public MemoryBenchmark build() {return new MemoryBenchmark(this);}
	}

	//NESTED CLASSES
	/**
	 * This class automatically samples the memory usage
	 * based on the defined interval.
	 *
	 * @author MG
	 *
	 */
	public static class Automated{

		public static MemoryBenchmark mb;

		/**
		 * Periodically updates the memory plots if the Graphs thread is alive.
		 */
		static Runnable memoryRunnable = new Runnable() {
			public void run() {
				if (Jerboa.getRunThread() != null && Jerboa.getRunThread().isAlive() ||
						Graphs.getExportThread() != null && Graphs.getExportThread().isAlive())
					mb.update(DateUtilities.getCurrentTime());
	    	}
		};

		/**
		 * Retrieves the memory values at a fixed interval of time.
		 * @param interval - the time interval in seconds at which memory values should be sampled
		 * @param verbose - true if information should be printed on the console; false otherwise
		 * @param inViewer - true if the memory benchmarking is running in viewer (GUI mode)
		 */
		public static void sample(int interval, boolean verbose, boolean inViewer){
			mb = new MemoryBenchmark.Builder().displayInViewer(inViewer).inMB(true).
					verbose(verbose).which(MemoryBenchmark.ALL).build();
			mb.update(DateUtilities.getCurrentTime());
			ScheduledExecutorService executor = Executors.newScheduledThreadPool(1);
			executor.scheduleAtFixedRate(memoryRunnable, 0, interval, TimeUnit.SECONDS);
		}

		/**
		 * Update the values in the map at the key equal to mark.
		 * @param mark -which memory value to be updated
		 */
		public static void update(String mark){
			if (mb != null)
				mb.update(mark);
		}

		/**
		 * Adds a graphical marker on the plots.
		 * @param label - the description of the marker in the plot
		 */
		public static void putMarker(String label){
			if (mb != null){
				String mark = DateUtilities.getCurrentTime();
				mb.update(mark);
				mb.putMarker(mark, label);
			}
		}
	}

	/**
	 * Utility class for the data structure necessary for this object.
	 *
	 * @author MG
	 *
	 */
	public class Entry{

		private int usageType;
		private String mark;

		public Entry(int usage, String mark){
			this.usageType = usage;
			this.mark = mark;
		}

		public int getUsageType() {
			return usageType;
		}

		public String getMark() {
			return mark;
		}
	}

	//GETTERS AND SETTERS
	public void getCurrentUsage(){
		displayUsage();
	}

	/**
	 * Displays the current memory usage if verbose flag is set to
	 * true and stores the data for later usage. This method can be
	 * considered a light version of the update method. It does not
	 * have any impact on the plots.
	 * @param atMark - the key to store the values in the data map
	 */
	public void getUsage(String atMark){

		if (verbose){ displayUsage();}

		//store data for later use
		data.put(new Entry(USED_MEMORY, atMark), MemoryUtilities.getUsedMemory());
		data.put(new Entry(FREE_MEMORY, atMark), MemoryUtilities.getFreeMemory());
		data.put(new Entry(ALLOCATED_MEMORY, atMark), MemoryUtilities.getAllocatedMemory());
		data.put(new Entry(MAXIMUM_MEMORY, atMark), MemoryUtilities.getMaxMemory());
	}

	public boolean isInMB() {
		return inMB;
	}

	public int getWhich() {
		return which;
	}

	public boolean isVerbose() {
		return verbose;
	}

	public boolean isDisplayInViewer() {
		return showAsResultViewer;
	}

	public int getUnit() {
		return unit;
	}

	public void setUnit(int unit) {
		this.unit = unit;
	}

	/**
	 * Main method for testing and debugging
	 * @param args - none
	 */
	public static void main(String[] args){

		MemoryBenchmark mb = new MemoryBenchmark.Builder().verbose(false).withPlots(true).displayInViewer(false).build();
		mb.update("start");
		mb.displayUsage();

		mb.update(DateUtilities.getCurrentTime());
		mb.putMarker(DateUtilities.getCurrentTime(), "test");

		mb.update("end");
		mb.displayUsage();

		//or

	//	MemoryBenchmark.Automated.sample(3, true, true);
	//	MemoryBenchmark.Automated.update("now");

	}

}
