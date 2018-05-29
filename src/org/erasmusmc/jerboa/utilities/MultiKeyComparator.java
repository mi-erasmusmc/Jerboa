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
 * $Rev:: 4809              $:  Revision of last commit                                   *
 * $Author:: root           $:  Author of last commit                                     *
 * $Date:: 					$:  Date and time (CET) of last commit						  *
 ******************************************************************************************/
package org.erasmusmc.jerboa.utilities;

import java.util.Comparator;

import org.apache.commons.collections.comparators.NullComparator;
import org.apache.commons.collections.keyvalue.MultiKey;

/**
 * Class providing comparison functionality between two multikeys.
 * @see <a href="http://apachecommonstipsandtricks.blogspot.nl/2009/02/examples-of-bag-and-multikeys.html">
 * http://apachecommonstipsandtricks.blogspot.nl/2009/02/examples-of-bag-and-multikeys.html</a>
 *
 */
public class MultiKeyComparator implements Comparator<MultiKey>{

	//index of the key
	private int index;

	/**
	 * Constructor receiving which of the key component is to be compared.
	 * @param i - the index of the key component
	 */
	public MultiKeyComparator(int i){
		this.index = i;
	}

	/**
	 * Compares the component at index i of two multi keys.
	 * @return - -1 if o1 is "lower" than (less than, before, etc.) o2;
	 * 1 if o1 is "higher" than (greater than, after, etc.) o2;
	 *  or 0 if o1 and o2 are equal.
	 */
	public int compare(MultiKey o1, MultiKey o2){
		Object[] keys1 = o1.getKeys();
		Object[] keys2 = o2.getKeys();
		Object oo1 = null;
		try{
			oo1 = keys1[index];
		}catch (ArrayIndexOutOfBoundsException e){}

		Object oo2 = null;
		try{
			oo2 = keys2[index];
		}catch (ArrayIndexOutOfBoundsException e){}

		NullComparator nullComparator = new NullComparator(false);
		return nullComparator.compare(oo1, oo2);
	}
}