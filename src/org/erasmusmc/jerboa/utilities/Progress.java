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
 * $Rev:: 4682              $:  Revision of last commit                                   *
 * $Author:: root           $:  Author of last commit                                     *
 * $Date:: 					$:  Date and time (CET) of last commit						  *
 ******************************************************************************************/

package org.erasmusmc.jerboa.utilities;

import java.awt.Dimension;
import java.awt.Toolkit;

import javax.swing.BoxLayout;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JProgressBar;

import org.erasmusmc.jerboa.Jerboa;
import org.erasmusmc.jerboa.gui.JerboaGUI;

/**
 * This class implements a progress bar that updates only if the refreshInteval has passed.
 * By default this is not faster than once every second. This allows user feed-back during
 * the time consuming processing steps of a run.
 *
 * @author MG
 *
 */
public class Progress{

	//bar limits and progress
	private long maxValue;
	private long current = 0;
	private double completion = 0;

	//time points
	private long initTime;
	private long lastTime;

	//update
	private long refreshInterval = 2000;//ms
	private double smoothingFactor = 0.1;// 0-1 the higher the more influence of current speed

	//intermediate calculation
	private long sumProgress;
	private long progressPerSec;

	//graphical progress bar - only for GUI mode
	private JProgressBar bar;

	//run mode flag - with or without GUI
	private boolean noGUI;

	//panel to be added to
	private  JPanel panel;
	private  JLabel processingLabel;
	private  JLabel timeLabel;

	//CONSTRUCTORS
	/**
	 * Basic constructor without initialization of the progress bar.
	 */
	public Progress(){
		super();

		this.noGUI = Jerboa.inConsoleMode;
		if (!noGUI){
			initPanel();
			this.processingLabel = new JLabel(" ");
			this.timeLabel = new JLabel(" ");
			this.bar = null;
		}
	}

	/**
	 * Constructor that directly initializes the progress bar.
	 * @param upperLimit - the value at 100%
	 * @param comment - the comment line to show on top of the progress bar
	 * @param noGUI - true if the progress is needed in a no GUI context.
	 */
	public Progress(long upperLimit, String comment, boolean noGUI){
		super();
		this.noGUI = noGUI;
		init(upperLimit,comment);
	}

	/**
	 * Sets the progress upper limit and adds a comment line to the dialog box.
	 * @param upperLimit - the value which is considered to be 100 % completion
	 * @param comment - comment to be displayed if running in GUI mode
	 * @return - a JPanel with the initialized components
	 */
	public JPanel init(long upperLimit, String comment){
		//completion
		maxValue = (upperLimit != -1 && upperLimit != 0) ? upperLimit : 1;
		current = 0;
		completion = 0;

		//ETA
		sumProgress = 0;
		progressPerSec = 0;
		initTime = System.currentTimeMillis();
		lastTime = initTime;

		//GUI components
		if (!noGUI){
			if (bar != null)
				bar = null;
			bar = new JProgressBar((int)completion, 100);
			bar.setDoubleBuffered(true);
			bar.setStringPainted(true);
			bar.setPreferredSize(new Dimension(JerboaGUI.FRAME_WIDTH,15));
			bar.setMaximumSize(new Dimension(Toolkit.getDefaultToolkit().getScreenSize().width,15));

			processingLabel = new JLabel(" "+comment+": ");
			timeLabel = new JLabel("started");
			bar.setString(processingLabel.getText() + timeLabel.getText());
			initPanel();
			panel.add(bar);

			JerboaGUI.hideProgress();
			JerboaGUI.showProgress(panel);
		}

		return panel;
	}

	/**
	 * Initializes the panel on which the progress bar is fit.
	 */
	private void initPanel(){
		this.panel = new JPanel();
		this.panel.setLayout(new BoxLayout(panel, BoxLayout.X_AXIS));
		this.panel.setPreferredSize(new Dimension(JerboaGUI.FRAME_WIDTH, 20));
		this.panel.setMinimumSize(new Dimension(JerboaGUI.FRAME_WIDTH, 20));
		this.panel.setMaximumSize(new Dimension(Toolkit.getDefaultToolkit().getScreenSize().width, 20));
	}

	/**
	 * Updates the current progress to progress + 1.
	 */
	public void update(){
		update(1);
	}

	/**
	 * Updates the current progress to progress + increment.
	 * @param increment - the value increment to progress with
	 */
	public void update(long increment){
		current += increment;
		sumProgress+= increment;
		if ((System.currentTimeMillis() - lastTime)>=refreshInterval){
			//estimation based on exponential moving average of refreshInterval window
			progressPerSec = (long) ((progressPerSec*(1-smoothingFactor)+sumProgress*smoothingFactor*1000/refreshInterval));
			if (progressPerSec>0){
				long sec = (long) (maxValue-current)/progressPerSec;
				if (!noGUI){
					timeLabel.setText(TimeUtilities.convertSecondsToHHMMSS(sec));
					bar.setString(processingLabel.getText() + timeLabel.getText());
				}
			}
			lastTime = System.currentTimeMillis();
			sumProgress=0;
			completion = (double)(current*100/maxValue);

			if (!noGUI)
				bar.setValue((int)completion);
			else
				show();
		}
	}

	/**
	 * Closes the progress bar window. This is useful if
	 * the bar did not reach 100% and the process has stopped.
	 */
	public void close() {
		if (bar!= null)
			bar = null;
		if (!noGUI)
			JerboaGUI.hideProgress();
	}

	/**
	 * Displays the progress to screen in CLI mode.
	 */
	public void show(){
		//System.out.print("Progress...... "+completion+"%\r");
	}

	/**
	 * Displays the progress on the screen in CLI mode.
	 * @param toLogAlso - if true, the progress is output to the API log.
	 */
	public void show(boolean toLogAlso){
		show();
		if (!noGUI && toLogAlso)
			Logging.add("Progress...... "+completion+"%\r", true);
	}

	//GETTERS AND SETTERS
	public double getCompletion() {
		return completion;
	}

	public void setCompletion(double completion) {
		this.completion = completion;
	}

	public long getMaxValue() {
		return maxValue;
	}

	public void setMaxValue(long maxValue) {
		this.maxValue = maxValue;
	}

	public long getRefreshInterval() {
		return refreshInterval;
	}

	public void setRefreshInterval(long refreshInterval) {
		this.refreshInterval = refreshInterval;
	}

	public boolean isNoGUI() {
		return noGUI;
	}

	public void setNoGUI(boolean noGUI) {
		this.noGUI = noGUI;
	}

	//MAIN FOR DEBUGGING
	public static void main(String[] args) {
		Timer timer = new Timer();
		Progress progress = new Progress();
		timer.start();
		int count = 60000;//ms
		progress.init(count, "Progressbar checking");
		//refresh loop index and progress
		for (int i=1;i<count;i++){

			progress.update();
			try {
				Thread.sleep(1);
			} catch(InterruptedException ex) {
				Thread.currentThread().interrupt();
			}
		}
	}

}
