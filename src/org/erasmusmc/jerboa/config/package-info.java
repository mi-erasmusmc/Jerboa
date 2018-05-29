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
 * Provides the classes that contain/handle the internal parameter configuration of the application.
 * These classes will deal with initialization files and/or parameter configuration files.
 * The parameters can vary from file paths to the API resources or internal folder structure,
 * to flags and formulas used in the internal mechanisms of the API,
 * as well as static or constant values. These parameters are to be kept centralized to ease their manipulation
 * and insure that if any of these are to be modified, the only place where their value is to be changed is within this package.
 *
 * @since Jerboa v3.0b1
 */
package org.erasmusmc.jerboa.config;