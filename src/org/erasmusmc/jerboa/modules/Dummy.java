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

package org.erasmusmc.jerboa.modules;

import org.erasmusmc.jerboa.dataClasses.Patient;
import org.erasmusmc.jerboa.engine.Module;

public class Dummy extends Module {

	@Override
	public boolean init() {
		return true;
	}

	@Override
	public Patient process(Patient patient) {
		return patient;
	}

	@Override
	public void outputResults() {
	}


	@Override
	public void setNeededFiles() {
	}

	@Override
	public void setNeededExtendedColumns() {
	}

	@Override
	public void setNeededNumericColumns() {
	}

	public static void main(String[] args) {
	}

}
