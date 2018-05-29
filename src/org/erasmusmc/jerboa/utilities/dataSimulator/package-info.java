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

/**
 * This package contains all the necessary classes in order to generate
 * simulated data into different input file types (e.g., patient population, events, prescriptions).
 * The package is self contained, in terms of usage. A main method is to be used in order to
 * generate the data files. There is a set of parameters that can be tweaked in order to manipulate
 * the population size, frequency of events, prescriptions, etc.
 * The simulated data is output into CSV format.
 *
 * @since Jerboa v3.0b1
 *
 */
package org.erasmusmc.jerboa.utilities.dataSimulator;