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
 * Provides the classes that are dealing with statistics gathering/processing.
 * These classes contain all the counters used during the run of the API and the methods to process these counter
 * values in order to provide descriptive statistics of the input data. It mainly makes use of the MultiKeyBag class
 * in association with the ExtendedMultiKey class for counting occurrences of unique data/feature combinations.
 *
 * @since Jerboa v3.0b1
 *
 * @see org.erasmusmc.jerboa.utilities.ExtendedMultiKey
 * @see org.erasmusmc.jerboa.utilities.MultiKeyBag
 */
package org.erasmusmc.jerboa.utilities.stats;