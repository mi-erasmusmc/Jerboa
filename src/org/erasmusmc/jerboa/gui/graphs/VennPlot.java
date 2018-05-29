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

package org.erasmusmc.jerboa.gui.graphs;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.Arrays;
import java.util.List;
import java.util.Random;
import java.util.TreeMap;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JCheckBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSeparator;
import javax.swing.ScrollPaneLayout;
import javax.swing.SwingConstants;

import org.jfree.chart.ChartPanel;
import org.jfree.chart.JFreeChart;
import org.jfree.data.general.Dataset;


@SuppressWarnings("serial")
public class VennPlot extends Plot{

	//GUI related
	private JPanel sets;
	private JPanel diagramPanel;
	private JCheckBox showValues;
	private String title;

	//Venn diagram related
	private int nbSets;
	private VennDiagram diagram;
	private TreeMap<String, Integer> values;

	//CONSTRUCTORS
	public VennPlot(List<String> algorithmNames, String title){
		this(algorithmNames, null, title);
	}

	/**
	 * Constructor accepting a list of algorithm names used in the run.
	 * It calls the constructor below with a null set of mapped values.
	 * @param algorithmNames - the names of the algorithms used in the run
	 * @param title - the title of the plot
	 */
	public VennPlot(String[] algorithmNames, String title){
		this(Arrays.asList(algorithmNames), null, title);
	}

	/**
	 * Constructor accepting a list of the algorithm names and a map of
	 * the values associated to each set and overlapping regions. Null is allowed.
	 * @param algorithmNames - the names of the algorithms used in the run
	 * @param values - the values associated to the sets/overlaps
	 * @param title - the title of the plot
	 */
	public VennPlot(List<String> algorithmNames, TreeMap<String, Integer> values, String title){
		super(title);

		this.setLayout(new BorderLayout());
		this.setPreferredSize(new Dimension(750,700));
		this.setMinimumSize(new Dimension(VennDiagram.PANEL_WIDTH,VennDiagram.PANEL_HEIGHT));

		this.nbSets = algorithmNames.size();
		this.values = values;
		this.showValues = new JCheckBox("Show values");
		this.title = title;

		if (diagramPanel != null)
			diagramPanel.removeAll();
		diagramPanel = new JPanel();
		diagramPanel.setLayout(new BoxLayout(diagramPanel, BoxLayout.X_AXIS));
		this.diagram = new VennDiagram(this.nbSets, this.values);

		createLowerPanel(algorithmNames);
		createPanel();
		pack();

	}

	@Override
	public void createPanel() {

		panel = new ChartPanel(null);
		panel.setLayout(new BorderLayout());

		chart = createChart(null);

		diagramPanel.add(diagram);
		JSeparator separator = new JSeparator(SwingConstants.VERTICAL);
		separator.setMaximumSize(new Dimension(5,VennDiagram.PANEL_HEIGHT));
		separator.setMinimumSize(new Dimension(5,VennDiagram.PANEL_HEIGHT));
		diagramPanel.add(separator);
		diagramPanel.add(createLegend(diagram.getSetlabels()));

		JLabel titleLabel = new JLabel(this.title, SwingConstants.CENTER);
		titleLabel.setBackground(Color.white);
		titleLabel.setFont(new Font("TrueType", Font.PLAIN, 24));

		panel.add(titleLabel, BorderLayout.NORTH);
		panel.add(diagramPanel, BorderLayout.CENTER);
		panel.add(sets, BorderLayout.SOUTH);

		this.setDefaultCloseOperation(DISPOSE_ON_CLOSE);
	}

	/**
	 * Creates the lower panel for this plot containing the algorithm
	 * names and the check box to show values.
	 * @param algorithmNames
	 */
	private void createLowerPanel(List<String> algorithmNames){

		this.sets = new JPanel();
		this.sets.setBorder(BorderFactory.createTitledBorder(BorderFactory.createEtchedBorder(), "Algorithms"));
		this.sets.setLayout(new BoxLayout(this.sets, BoxLayout.X_AXIS));
		this.sets.setMaximumSize(new Dimension(VennDiagram.PANEL_WIDTH + 200, 100));
		JPanel labels = new JPanel();
		labels.setLayout(new BoxLayout(labels, BoxLayout.X_AXIS));
		for (int i = 0; i < this.nbSets; i++)
			labels.add(new JLabel(" "+this.diagram.getSetlabels()[i]+"="+algorithmNames.get(i)+"    "));
		this.sets.add(labels);

		this.sets.add(Box.createHorizontalGlue());

		this.showValues.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent arg0) {
				if (showValues.isSelected())
					diagram = new VennDiagram(nbSets, values);
				else
					diagram = new VennDiagram(nbSets, null);

				refresh();
			}
		});

		//TODO: uncomment when values are passed
//		this.showValues.setEnabled(this.values != null && !this.values.isEmpty());
		this.showValues.setEnabled(true);
		this.sets.add(this.showValues);
	}

	/**
	 * Regreshes the diagram panel in order to repaint it.
	 */
	public void refresh(){
		diagramPanel.remove(0);
		diagramPanel.add(diagram, 0);
		diagramPanel.validate();
		panel.validate();
	}

	/**
	 * Creates the legend containing the values for each overlap.
	 * @param setLabels - the labels of the Venn sets
	 * @return - a scroll pane containing the values associated
	 * to each set and overlapping region
	 */
	private JScrollPane createLegend(String[] setLabels){

		JPanel labels = new JPanel();
		labels.setLayout(new BoxLayout(labels, BoxLayout.Y_AXIS));

		//TODO modify to put what is in values
		Random r = new Random();
		for (String s : VennDiagram.getSetCombinations()){
			int val = r.nextInt(1000);
			labels.add(new JLabel(s + " = " + val));
			if (this.values == null || this.values.isEmpty())
				this.values = new TreeMap<String, Integer>();
			this.values.put(s, val);
		}

		JScrollPane legend = new JScrollPane(labels);
		legend.setLayout(new ScrollPaneLayout());
		legend.setVerticalScrollBarPolicy(ScrollPaneLayout.VERTICAL_SCROLLBAR_AS_NEEDED);
		legend.setHorizontalScrollBarPolicy(ScrollPaneLayout.HORIZONTAL_SCROLLBAR_AS_NEEDED);
		legend.setPreferredSize(new Dimension(150, VennDiagram.PANEL_HEIGHT));
		legend.setMaximumSize(new Dimension(250, VennDiagram.PANEL_HEIGHT));
		legend.setMinimumSize(new Dimension(100, VennDiagram.PANEL_HEIGHT));
		legend.setBorder(BorderFactory.createTitledBorder(BorderFactory.createEtchedBorder(), "Overlap"));

		return legend;
	}

	@Override
	public JFreeChart createChart(Object dataset) {
		return null;
	}

	//GETTERS AND SETTERS
	public JPanel getDiagramPanel() {
		return diagramPanel;
	}

	public void setDiagramPanel(JPanel diagramPanel) {
		this.diagramPanel = diagramPanel;
	}

	public VennDiagram getDiagram() {
		return diagram;
	}

	public void setDiagram(VennDiagram diagram) {
		this.diagram = diagram;
	}

	@Override
	public Dataset initDataset() {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public Dataset addSeriesToDataset(Object data, String seriesName) {
		// TODO Auto-generated method stub
		return null;
	}

}
