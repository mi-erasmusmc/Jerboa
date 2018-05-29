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
 * Provides the modules that are used in the work flow of the application.
 * These modules represent modifying and processing steps applied at patient level in order to
 * produce analysis results and/or prepare the data for subsequent analysis.
 * This package should contain the collection of modules that are developed during the lifetime of the application.
 * A specific subset of these modules can be used depending on the desired workflow and/or analysis.
 * This selection is achieved based on user defined scripts dictating which and how modules are used in the workflow.
 * Moreover, a module can define a list of modifiers to be applied prior to the processing steps of the module itself.
 *
 * @since Jerboa v3.0b1
 *
 * @see org.erasmusmc.jerboa.modifiers
 * @see org.erasmusmc.jerboa.engine.WorkFlow
 * @see org.erasmusmc.jerboa.scripts
 */
package org.erasmusmc.jerboa.modules;