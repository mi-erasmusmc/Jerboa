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

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

import org.apache.commons.collections.keyvalue.MultiKey;


/**
 * This class is a customized HashMap able to store {@literal <key, value>} pairs with a
 * composite key. Several objects can be used as forming one key.
 * The key is translated into an ExtendedMultiKey and its value affected.
 * Note that the number of objects composing a key is limited by the limits of the ExtendedMultiKey.
 *
 * @see org.erasmusmc.jerboa.utilities.ExtendedMultiKey
 *
 * @author MG
 *
 */
@SuppressWarnings({"rawtypes", "serial" })
public class MultiKeyMap extends HashMap{

	//the number of key components for this key
	private int nbKeyComponents;

	//true each time a new value is added in the list
	private boolean updateKeyValues;

	//sets of unique key component values
	private TreeMap<Integer, TreeSet<Object>> keyValues;

	//will hold subsets of values from this map
	private List<Object> values;

	//will hold the sum of a subset of values from this map
	private double sum;

	//CONSTRUCTORS
	/**
	 * Basic constructor initializing the object attributes.
	 */
	public MultiKeyMap(){
		super();

		this.nbKeyComponents = 1;
		this.updateKeyValues = false;
		this.keyValues = new TreeMap<Integer, TreeSet<Object>>();
	}

	/**
	 * Constructor receiving another map.
	 * @param map - the multi key map to be used
	 */
	public MultiKeyMap(MultiKeyMap map){
		super();

		this.nbKeyComponents = map.nbKeyComponents;
		this.updateKeyValues = map.updateKeyValues;
		this.keyValues = map.keyValues;
	}

	/**
	 * Retrieves the values that are associated with multiKey.
	 * Wild cards are allowed. If multi key does not contain any
	 * wild card, then the list will contain only one element.
	 * @param multiKey - the multi key of interest
	 * @return - a list of the values corresponding to multi key
	 */
	public List<Object> getValues(ExtendedMultiKey multiKey){
		this.values = new ArrayList<Object>();
		return addValues(multiKey);
	}

	/**
	 * Returns the sorted set of unique key values corresponding to the keyIndex in the multi key
	 * under an Object representation.
	 * @param keyIndex - the index of the multi key component of interest
	 * @return - a set of unique key component values under a String representation
	 */
	public TreeSet<Object> getKeyValues(int keyIndex){
		if (updateKeyValues)
			for (int i = 0; i < nbKeyComponents; i ++)
				if (keyValues.get(i) != null)
					extractKeyComponentValues(i);

		if (keyValues.get(keyIndex) == null)
			extractKeyComponentValues(keyIndex);

		return keyValues.get(keyIndex);
	}

	/**
	 * Retrieves the sum of the elements having this multiKey.
	 * Wild cards are allowed. If no wild card is present
	 * the count of the element having this multiKey in the bag is returned.
	 * @param multiKey - the multi key of interest (wild cards allowed)
	 * @return - the sum of the elements under the multiKey or count if no wild cards present
	 */
	public double getSum(ExtendedMultiKey multiKey){
		this.sum = 0;
		return sum(multiKey);
	}

	/**
	 * Returns a multi key map that represents a subset of this map, formed by
	 * elements that at keyComponentIndex have the keyComponent value.
	 * @param keyComponentIndex - the index of the component in the multi key
	 * @param keyComponent - the key component of interest
	 * @return - a multi key bag that represents the wanted subset
	 */
	@SuppressWarnings("unchecked")
	public MultiKeyMap getSubMap(int keyComponentIndex, Object keyComponent){
		if (keyComponentIndex <= nbKeyComponents){
			MultiKeyMap subBag = new MultiKeyMap();
			for (MultiKey key : (Set<MultiKey>)this.keySet())
				if (((Object)key.getKey(keyComponentIndex)).equals(keyComponent))
					subBag.put(key, get(key));
			subBag.nbKeyComponents = this.nbKeyComponents;
			return subBag;
		}
		return null;
	}

	//SPECIFIC METHODS
	/**
	 * Will populate the map containing all unique key values for each of the
	 * key component under an Object representation.
	 * This method should be called only once and the extractKeys flag set to false.
	 * @param index - the index of the key component
	 */
	private void extractKeyComponentValues(int index){
		keyValues.put(index, getKeyComponentValues(index));
		updateKeyValues = false;
	}

	/**
	 * Returns the sorted set of unique key values as objects corresponding to the keyIndex in the multi key.
	 * @param keyIndex - the index of the multi key component of interest
	 * @return - a set of unique key component values under an Object representation
	 */
	@SuppressWarnings({"unchecked" })
	public TreeSet<Object> getKeyComponentValues(int keyIndex){
		if (keyIndex >= 0 && keyIndex < 4){ //a key of 5 elements is maximum supported
			//retrieve unique values of the key component at keyIndex
			TreeSet<Object> uniqueValues = new TreeSet<Object>();
			for (MultiKey key : (Set<MultiKey>)this.keySet())
				uniqueValues.add(key.getKey(keyIndex));
			return uniqueValues;
		}

		return null;
	}

	/**
	 * Recursive method to create a list of values that are
	 * associated with multiKey. Wild cards are allowed.
	 * If no wild card is found in the multi key, then the list
	 * returned will contain only one value.
	 * @param multiKey - the key of interest
	 * @return - the list of values associated wil the multi key
	 */
	private List<Object> addValues(ExtendedMultiKey multiKey){
		int wildCardIndex = multiKey.getWildCardIndex();
		if (wildCardIndex != -1){
			TreeSet<Object> keyValues = getKeyValues(wildCardIndex);
			for (Object key : keyValues){
				ExtendedMultiKey newKey =
						multiKey.setKeyComponent(wildCardIndex, key);
				addValues(newKey);
			}
		}
		//add the element only if not null
		if (this.get(multiKey) != null)
			this.values.add(this.get(multiKey));
		return this.values;
	}

	/**
	 * Recursive method to calculate the sum of the elements having this
	 * multiKey in the bag. Wild cards are allowed. If no wild card is
	 * present the count of the element having this multiKey in the bag
	 * is returned.
	 * @param multiKey - the multi key of interest
	 * @param wildCardIndex - the index of the first wild card
	 * 						  encountered in the multi key
	 * @return - the sum of the elements under the multiKey
	 */
	private double sum(ExtendedMultiKey multiKey){
		int wildCardIndex = multiKey.getWildCardIndex();
		if (wildCardIndex != -1){
			TreeSet<Object> keyValues = getKeyValues(wildCardIndex);
			for (Object key : keyValues){
				ExtendedMultiKey newKey = multiKey.setKeyComponent(wildCardIndex, key);
				sum(newKey);
			}
		}

		return this.sum += this.get(multiKey) != null ? (double)this.get(multiKey) : 0;
	}

	//ADD ELEMENTS
	@SuppressWarnings("unchecked")
	public final void put(final ExtendedMultiKey key, final Object value) {
		nbKeyComponents = key.size();
		this.updateKeyValues = true;
		super.put(key, value);
	}

	@SuppressWarnings("unchecked")
	public final void put(final Object key1, final Object key2, final Object value) {
		ExtendedMultiKey key = new ExtendedMultiKey(key1, key2);
		nbKeyComponents = key.size();
		this.updateKeyValues = true;
		super.put(key, value);
	}

	@SuppressWarnings("unchecked")
	public final void put(final Object key1, final Object key2,
						  final Object key3, final Object value) {
		ExtendedMultiKey key = new ExtendedMultiKey(key1, key2, key3);
		nbKeyComponents = key.size();
		this.updateKeyValues = true;
		super.put(key, value);
	}

	@SuppressWarnings("unchecked")
	public final void put(final Object key1, final Object key2,
						  final Object key3, final Object key4, final Object value) {
		ExtendedMultiKey key = new ExtendedMultiKey(key1, key2, key3, key4);
		nbKeyComponents = key.size();
		this.updateKeyValues = true;
		super.put(key, value);
	}

	//GETTERS
	public final Object get(final ExtendedMultiKey key) {
		return super.get(key);
	}

	public final Object get(final Object key1, final Object key2) {
		return super.get(new ExtendedMultiKey(key1, key2));
	}

	public final Object get(final Object key1, final Object key2,
							final Object key3) {
		return super.get(new ExtendedMultiKey(key1, key2, key3));
	}

	public final Object get(final Object key1, final Object key2,
							final Object key3, final Object key4) {
		return super.get(new ExtendedMultiKey(key1, key2, key3, key4));
	}

}
