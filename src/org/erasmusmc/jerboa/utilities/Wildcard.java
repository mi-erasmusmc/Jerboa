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
 * $Rev:: 3961              $:  Revision of last commit                                   *
 * $Author:: root           $:  Author of last commit                                     *
 * $Date:: 2013-05#			$:  Date and time (CET) of last commit						  *
 ******************************************************************************************/
package org.erasmusmc.jerboa.utilities;

/**
* Allows the use of wild cards on the key components of a
* multi key bag or multi key map. Note that the objects corresponding to
* this wild card index in the bag/map will be cast to the type of wild card used.
*
* @see org.erasmusmc.jerboa.utilities.MultiKeyBag
* @see org.erasmusmc.jerboa.utilities.MultiKeyMap
*
* @author MG
*
*/
public class Wildcard extends Object{

	@SuppressWarnings("rawtypes")
	public Class type;

	//CONSTRUCTORS
	/**
	 * Basic constructor.
	 */
	public Wildcard(){
		super();
	}

	/**
	 * Constructor receiving the type of the wild card.
	 * @param type - the object class of this wild card
	 */
	public Wildcard(@SuppressWarnings("rawtypes") Class type){
		super();
		this.type = type;
	}

	//STATIC METHODS TO ALLOW CALL WITHOUT "new"
	public static Wildcard BYTE(){
		return new Wildcard(Byte.class);
	}

	public static Wildcard INTEGER(){
		return new Wildcard(Integer.class);
	}

	public static Wildcard DOUBLE(){
		return new Wildcard(Double.class);
	}

	public static Wildcard STRING(){
		return new Wildcard(String.class);
	}
}

