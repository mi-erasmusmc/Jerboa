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

package org.erasmusmc.jerboa.engine;

/******************************************************************************************
 * Jerboa software version 3.0 - Copyright Erasmus University Medical Center, Rotterdam   *
 *																				          *
 * Author: Marius Gheorghe (MG) - department of Medical Informatics						  *
 * 																						  *
 * $Rev:: 4437              $:  Revision of last commit                                   *
 * $Author:: root           $:  Author of last commit                                     *
 * $Date:: 					$:  Date and time (CET) of last commit						  *
 ******************************************************************************************/


import org.apache.commons.io.FilenameUtils;
import org.erasmusmc.jerboa.Jerboa;
import org.erasmusmc.jerboa.config.FilePaths;
import org.erasmusmc.jerboa.config.Parameters;
import org.erasmusmc.jerboa.engine.ScriptParser.Settings;
import org.erasmusmc.jerboa.utilities.TimeUtilities;

/**
 * Generic class for the modifiers. This class is to be extended by any modifier in the software.
 * It extends the Worker superclass.
 *
 * @author MG
 *
 */
public abstract class Modifier extends Worker{

	private Settings modifierParameters;

	private String parentModule;

	/**
	 * Basic constructor.
	 */
	public Modifier(){
		super();
	}

	//GETTERS AND SETTERS
	/**
	 * Creates the path and name for the intermediate
	 * and final result output of the modifier.
	 */
	@Override
	public void setOutputFileNames(){

		if (!Jerboa.unitTest) {
			String modifierName = (outputFileName != null && !outputFileName.equals("")) ? this.title + "_" + outputFileName : this.getTitle();
			String extension = ".csv";
			if (modifierName.contains(".")){
				extension = "." + FilenameUtils.getExtension(modifierName);
				modifierName = FilenameUtils.getName(modifierName);
			}

			this.outputFileName = FilePaths.WORKFLOW_PATH + this.parentModule + "/" + Parameters.DATABASE_NAME + "_" + modifierName +
					"_" + TimeUtilities.TIME_STAMP + "_" + Parameters.VERSION + extension;
			this.intermediateFileName = FilePaths.INTERMEDIATE_PATH + this.parentModule + "/" + Parameters.DATABASE_NAME + "_" + modifierName + extension;
		}
		else {
			this.outputFileName = "Output";
			this.intermediateFileName = "Intermediate";
		}
	}

	public Settings getModifierParameters() {
		return modifierParameters;
	}

	/**
	 * Will set the parameters of the modifiers required by the module
	 * invoking this modifier, prior to the data processing.
	 * @param modifierParameters - the modifier settings
	 */
	public void setModifierParameters(Settings modifierParameters) {
		this.modifierParameters = modifierParameters;
		if (Jerboa.isInDebugMode)
			printParameterMapping(modifierParameters.parameters);
		setParameters(modifierParameters.parameters);
	}

	public String getParentModule() {
		return parentModule;
	}

	public void setParentModule(String parentModule) {
		this.parentModule = parentModule;
	}

}