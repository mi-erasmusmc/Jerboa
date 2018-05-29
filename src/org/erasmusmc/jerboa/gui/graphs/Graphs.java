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
 * $Rev:: 3728              $:  Revision of last commit                                   *
 * $Author:: root           $:  Author of last commit                                     *
 * $Date:: 					$:  Date and time (CET) of last commit						  *
 ******************************************************************************************/
package org.erasmusmc.jerboa.gui.graphs;

import java.util.HashMap;
import java.util.List;

import org.erasmusmc.jerboa.Jerboa;
import org.erasmusmc.jerboa.gui.JerboaGUI;
import org.erasmusmc.jerboa.modules.viewers.ResultViewer;
import org.erasmusmc.jerboa.utilities.Logging;
import org.erasmusmc.jerboa.utilities.MemoryBenchmark;

/**
 * This class acts like a manager for all the result viewers on the GUI.
 * It keeps a list of all initialized result viewers and provides the
 * functionality to add on the fly another plot to any existing viewer.
 * It also allows the user to export to PDF all generated graphs.
 *
 * @author MG
 *
 */
public class Graphs {

	//the thread used to export to PDF
	private static Thread export;

	//list of viewers
	public static HashMap<String, ResultViewer> viewers = new HashMap<String, ResultViewer>();

	/**
	 * Will add a plot to the result viewer having viewerTitle. If the viewer
	 * does not exist in the list, a new result viewer is instantiated with
	 * the viewer title and the plot will be added in the list of plots of
	 * the result viewer with the index indexLabel. If this entry is not present
	 * in the list of plots in the result viewer, a new entry is created and plot
	 * is added to it.
	 * @param viewerTitle - the title of the result viewer
	 * @param indexLabel - the label of the plot in the list of plots
	 * @param plot - the plot object to be added
	 */
	public static void addPlot(String viewerTitle, String indexLabel, Plot plot){
		if (plot != null){
			if (!Jerboa.inConsoleMode){
				if((viewerTitle != null && !viewerTitle.equals("")) && Graphs.viewers.get(viewerTitle) == null)
					Graphs.viewers.put(viewerTitle, new ResultViewer(viewerTitle));
				Graphs.viewers.get(viewerTitle).addPlot(indexLabel, plot);
			}
		}
	}

	/**
	 * Will add a list of plots to the result viewer having viewerTitle. If the viewer
	 * does not exist in the list, a new result viewer is instantiated with
	 * the viewer title and the plots will be added in the list of plots of
	 * the result viewer with the index indexLabel. If this entry is not present
	 * in the list of plots in the result viewer, a new entry is created and the plots
	 * are assigned to it.
	 * @param viewerTitle - the title of the result viewer
	 * @param indexLabel - the label of the plot in the list of plots
	 * @param plots - the list of plots to be added in the viewer
	 */
	public static void addPlots(String viewerTitle, String indexLabel, List<Plot> plots){
		if (plots != null && plots.size() > 0){
			if (!Jerboa.inConsoleMode){
				if((viewerTitle != null && !viewerTitle.equals("")) && Graphs.viewers.get(viewerTitle) == null)
					Graphs.viewers.put(viewerTitle, new ResultViewer(viewerTitle));
				Graphs.viewers.get(viewerTitle).addPlotList(indexLabel, plots);
			}
		}
	}

	/**
	 * Will export all the generated plots to PDF.
	 * The export is done per viewer. It makes use of a thread in
	 * order to wait until the work flow is finished.
	 */
	public static void exportGraphs() {
		 export = new Thread(new Runnable(){
	          public void run(){
	        	  //normally if it reaches here, it is always in GUI mode..for now
	        	  if (!Jerboa.inConsoleMode)
	        		  JerboaGUI.busy();
	        	  try {
	                  Jerboa.getWorkFlow().join();
	              } catch (InterruptedException e) {
	                  Logging.outputStackTrace(e);
	              }

	        	  if (Jerboa.getWorkFlow().hasRanModulesSuccessfully()){
	        		  MemoryBenchmark.Automated.putMarker("Exporting graphs");
	        		  //export per viewer
	        		  if (!Graphs.viewers.isEmpty()){
	        			  Logging.add("Exporting graphs to PDF");
	        			  for (ResultViewer viewer : Graphs.viewers.values())
	        				  viewer.saveChartsToPDF();
	        		  }
	        		  Graphs.viewers = new HashMap<String, ResultViewer>();
	        		  Logging.addWithTimeStamp("Done");
	        	  }

	           	//should always be in GUI mode
	           	if (!Jerboa.inConsoleMode)
	        		  JerboaGUI.done();
	          }
        });
		export.start();
	}

	//GETTERS
	public static Thread getExportThread(){
		return Graphs.export;
	}

	public static ResultViewer getViewer(String title){
		return Graphs.viewers.get(title);
	}

}
