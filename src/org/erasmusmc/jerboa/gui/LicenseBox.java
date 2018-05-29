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
 * $Rev:: 4515              $:  Revision of last commit                                   *
 * $Author:: root           $:  Author of last commit                                     *
 * $Date:: 					$:  Date and time (CET) of last commit						  *
 ******************************************************************************************/
package org.erasmusmc.jerboa.gui;

import java.awt.Font;
import java.awt.Image;
import java.awt.Toolkit;
import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URL;

import javax.swing.Icon;
import javax.swing.ImageIcon;
import javax.swing.JOptionPane;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;

import org.erasmusmc.jerboa.Jerboa;
import org.erasmusmc.jerboa.config.FilePaths;
import org.erasmusmc.jerboa.config.Parameters;
import org.erasmusmc.jerboa.config.PropertiesManager;
import org.erasmusmc.jerboa.utilities.ErrorHandler;
import org.erasmusmc.jerboa.utilities.FileUtilities;
import org.erasmusmc.jerboa.utilities.Logging;

/**
 * This class will display the license text if the application is
 * running for the first time on a system.
 * Code parts from
 * @see <a href="http://www.eldritch.org/erskin/GPLAboutDialog/GPLAboutDialog.java">
 * http://www.eldritch.org/erskin/GPLAboutDialog/GPLAboutDialog.java</a>
 *
 * @author MG
 *
 */
public class LicenseBox {

	//logos
	private Icon licenseLogo;
	private Icon jerboaLogo;

	/**
	 * Basic constructor to initialize the frame and display it.
	 */
	public LicenseBox() {
		try{
			URL url = Jerboa.class.getResource(FilePaths.LICENSE_LOGO_PATH);
			Image img = Toolkit.getDefaultToolkit().getImage(url);
			licenseLogo = new ImageIcon(img, null);
			url = Jerboa.class.getResource(FilePaths.LOGO_PATH);
			img = Toolkit.getDefaultToolkit().getImage(url);
			jerboaLogo = new ImageIcon(img, null);
		}catch(Exception e){
			new ErrorHandler("Could not fetch logo for license box");
		}
		display();
	}

	/**
	 * Displays an about dialog box and offers to display the GNU GPL.
	 */
	public void display() {
		String message =
				"Jerboa Software "+Parameters.VERSION + "\n" +
						"Clinical Information Processing Software" + "\n\n" +
						"\n\u00A9"+" Erasmus University Medical Center\n" +
						" Rotterdam, The Netherlands\n" +
						"\n\nThis program is Open Source software, or more" +
						"\nspecifically, free software. You can redistribute" +
						"\nit and/or modify it under the terms of the GNU" +
						"\nGeneral Public License 2 (GPL2) as published by the " +
						"\nFree Software Foundation.\n";

		int viewGPL;
		Object[] optionButtons = { "View License" };
		viewGPL = JOptionPane.showOptionDialog(JerboaGUI.frame,	message, "Jerboa GNU License", 0, JOptionPane.INFORMATION_MESSAGE, licenseLogo,
				optionButtons, optionButtons[0]);

		if (viewGPL == JOptionPane.CLOSED_OPTION){
			try{
				FileUtilities.delete(new File(FilePaths.PROPERTIES_FILE));
			}catch(IOException e){
				Logging.add("Unable to delete the properties file", Logging.ERROR);
			}
			System.exit(0);
		}

		if(viewGPL == JOptionPane.YES_OPTION) {
			// Set up the scroll pane to hold the GPL once we read it
			JTextArea textArea = new JTextArea(15, 50);
			textArea.setEditable(false);
			textArea.setLineWrap(true);
			textArea.setFont(new Font("Courier", Font.PLAIN, 10));

			JScrollPane scrollPane = new JScrollPane(textArea);

			boolean loadedGPL = false;
			BufferedReader inGPL = null;

			while((viewGPL == JOptionPane.YES_OPTION) &&
					(loadedGPL == false)) {

				// Then try and read it locally
				try {
					InputStream is = Class.class.getResourceAsStream(FilePaths.LICENSE_PATH);
					inGPL = new BufferedReader(new InputStreamReader(is));
					textArea.read(inGPL, "GNU General Public License");

					inGPL.close();
					loadedGPL = true;
				}
				catch(Exception e) {
					if(inGPL != null) {
						try { inGPL.close(); }
						catch(IOException closeException) {}
					}
				}
			}
			// display license
			if(loadedGPL == true) {
				Object[] buttons = {"I agree", "I decline"};
				scrollPane.setPreferredSize(
						textArea.getPreferredScrollableViewportSize());
				int choice = JOptionPane.showOptionDialog(JerboaGUI.frame,
						scrollPane, "GNU General Public License", 0, JOptionPane.INFORMATION_MESSAGE, jerboaLogo, buttons, buttons[0]);
				if (choice == JOptionPane.CLOSED_OPTION || choice == JOptionPane.NO_OPTION){
					try{
						FileUtilities.delete(new File(FilePaths.PROPERTIES_FILE));
					}catch(IOException e){
						Logging.add("Unable to delete the properties file", Logging.ERROR);
					}
					System.exit(0);
				}else{
					PropertiesManager.setFirstRun(false);
				}
			}
		}
	}

}

