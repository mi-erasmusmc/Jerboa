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
 * Provides the modifiers/data modifiers/enhancers that are used in the modules run by the workflow defined in the script file.
 * These modifiers are to be applied on the input data in order to reduce the dimensionality of the data, alter or enhance the
 * patient history or set certain flags suggesting if the data is fit or not for the processing steps.
 * This package should contain the collection of modifiers that are developed during the lifetime of the application.
 * A specific selection of these modifiers is used per module depending on the desired study limitations/properties.
 * This selection is achieved based on user defined scripts dictating which and how modifiers are used in modules.
 *
 * @since Jerboa v3.0b1
 *
 * @see org.erasmusmc.jerboa.engine.WorkFlow
 * @see org.erasmusmc.jerboa.modules
 */
package org.erasmusmc.jerboa.modifiers;