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
 * $Rev:: 4797              $:  Revision of last commit                                   *
 * $Author:: root           $:  Author of last commit                                     *
 * $Date:: 					$:  Date and time (CET) of last commit						  *
 ******************************************************************************************/
package org.erasmusmc.jerboa.gui;

import java.awt.Color;
import java.awt.Container;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

import javax.swing.BoxLayout;
import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JPanel;

import org.erasmusmc.jerboa.config.FilePaths;
import org.erasmusmc.jerboa.config.Parameters;

/**
 * This class is used to display the details about the software.
 * It is called as the About menu option is selected.
 * It displays information about the authors of the software
 * as well as the support information.
 *
 * @author MG
 *
 */
public class AboutBox extends JDialog {

  private static final long serialVersionUID = -7272048400591388564L;

  /**
   * Constructor initializing the components of the frame and displaying it.
   */
  public AboutBox(){
    setUndecorated(true);
    Container pane = getContentPane();
    pane.setLayout(new BoxLayout(pane, BoxLayout.Y_AXIS));
    pane.setBackground(Color.WHITE);

    ImageIcon logoTop = new ImageIcon(AboutBox.class.getResource(FilePaths.RESOURCE_PATH+"Jerboa-aboutTop.gif"));
    JLabel labelTop = new JLabel(logoTop);
    pane.add(labelTop);


    pane.add(new JLabel(" Version: Jerboa" + Parameters.VERSION));
    pane.add(new JLabel(" Authors: M. Gheorghe, B.M.Th.Mosseveld, P.R. Rijnbeek, M.J.Schuemie"));
    pane.add(new JLabel(" "));
    pane.add(new JLabel(" Medical Informatics, Erasmus MC, Rotterdam, The Netherlands"));
    pane.add(new JLabel(" Support: rre@erasmusmc.nl"));

    ImageIcon logoBottom = new ImageIcon(AboutBox.class.getResource(FilePaths.RESOURCE_PATH+"Jerboa-aboutBottom.gif"));
    JLabel labelBottom = new JLabel(logoBottom);
    pane.add(labelBottom);

    JPanel buttonPanel = new JPanel();
    buttonPanel.setBackground(Color.WHITE);

    JButton closeButton = new JButton("Close");
    closeButton.setAlignmentX(JButton.CENTER_ALIGNMENT);
    closeButton.addActionListener(new ActionListener(){
      public void actionPerformed(ActionEvent e) {
        closeWindow();
      }});
    buttonPanel.add(closeButton);
    pane.add(buttonPanel);
    pack();
    setLocationRelativeTo(JerboaGUI.frame);
  }

  /**
   * Will rend this dialog invisible and dispose of it.
   */
  private void closeWindow(){
    this.setVisible(false);
    this.dispose();
  }

}