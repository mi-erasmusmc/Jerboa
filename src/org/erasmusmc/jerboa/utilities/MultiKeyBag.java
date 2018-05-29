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

import java.util.*;

import org.apache.commons.collections.*;
import org.apache.commons.collections.keyvalue.*;
import org.apache.commons.collections.bag.*;
import org.apache.commons.collections.bag.HashBag;
import org.apache.commons.lang3.*;
import org.apache.commons.math3.stat.descriptive.DescriptiveStatistics;
import org.apache.commons.beanutils.*;

import org.erasmusmc.jerboa.utilities.MultiKeyComparator;
import org.erasmusmc.jerboa.utilities.Wildcard;
import org.erasmusmc.jerboa.utilities.stats.HistogramStats;
import org.erasmusmc.jerboa.utilities.stats.StatisticalSummary;

/**
 * This class is used for counting occurrences of objects or combinations
 * of objects, defined by an extended key. Each component of the key
 * represents an object. A maximum of 5 key components can be used.
 *
 * @author MG {@literal &} PR
 *
 */
public class MultiKeyBag {

	//the decorator key
	private String[] multiKey;

	//maximum size of the keys in the bag
	private int maxSize;

	//true each time a new value is added in the list
	private boolean updateKeyValues;

	//sets of unique key component values
	private TreeMap<Integer, TreeSet<Object>> keyObjects;
	private TreeMap<Integer, TreeSet<String>> keyStrings;

	//the bags
	private Bag masterBag;
	private Bag decoratedBag;

	private boolean LOG = false;
	private Timer timer;

	//CONSTRUCTORS
	/**
	 *Basic constructor.
	 */
	public MultiKeyBag() {
		this.masterBag = new HashBag();
		this.maxSize = 1;
		this.updateKeyValues = false;

		this.keyObjects = new TreeMap<Integer, TreeSet<Object>>();
		this.keyStrings = new TreeMap<Integer, TreeSet<String>>();
		if (LOG) {
			this.timer = new Timer();
		}
	}

	/**
	 * Constructor initializing the bag with a decorator based on
	 * transformer. The transformer transforms a bean to a subset
	 * of its properties used as multiKey.
	 * @param multiKeyStr - the key defining the transformation
	 */
	public MultiKeyBag(final String[] multiKeyStr) {
		this.masterBag = new HashBag();
		this.multiKey = multiKeyStr;
		this.maxSize = this.multiKey.length;
		this.decoratedBag = TransformedBag.decorate(masterBag,
				new PropertiesMultiKeyTransformer(this.multiKey));

		this.updateKeyValues = false;

		this.keyObjects = new TreeMap<Integer, TreeSet<Object>>();
		this.keyStrings = new TreeMap<Integer, TreeSet<String>>();
		if (LOG) {
			this.timer = new Timer();
		}
	}

	/**
	 * Add data directly to the master bag based on the key.
	 * It updates the maxSize of the keys in the back used for sorting
	 * @param data - the data to be added in the bag
	 */
	public final void add(final Object data) {
		if (data instanceof ExtendedMultiKey) {
			ExtendedMultiKey asMultiKey = (ExtendedMultiKey) data;
			this.maxSize = Math.max(this.maxSize, asMultiKey.size());
		}

		this.updateKeyValues = true;
		masterBag.add(data);
	}

	/**
	 * Add a number of copies of the data directly to the master bag.
	 * @param data - the data to be added in the bag
	 * @param nbCopies - the number of copies
	 */
	public final void add(final Object data, final int nbCopies) {
		if (data instanceof ExtendedMultiKey) {
			ExtendedMultiKey asMultiKey = (ExtendedMultiKey) data;
			this.maxSize = Math.max(this.maxSize, asMultiKey.size());
		}

		this.updateKeyValues = true;
		masterBag.add(data, nbCopies);
	}

	//GETTERS
	/**
	 * Returns the set of unsorted unique multi keys of the bag.
	 * @return - a set of unique key combinations
	 */
	@SuppressWarnings({ "unchecked" })
	public Set<ExtendedMultiKey> getUniqueSet() {
		return masterBag.uniqueSet();
	}

	/**
	 * Returns the set of unique multi keys that the bag contains.
	 * @return - a sorted set of unique key combinations
	 */
	@SuppressWarnings({ "rawtypes", "unchecked" })
	public TreeSet<ExtendedMultiKey> getSortedKeySet() {
		// add each component of the multi key in the comparator
		Collection<Comparator> comparators = new ArrayList<Comparator>();
		for (int i = 0; i < maxSize; i++) {
			comparators.add(new MultiKeyComparator(i));
		}
		Comparator comparator = ComparatorUtils.chainedComparator(comparators);

		//retrieve the components of the multi key
		Set<ExtendedMultiKey> set = new TreeSet<ExtendedMultiKey>(comparator);

		set.addAll(masterBag.uniqueSet());
		return (TreeSet) set;
	}

	/**
	 * Returns the set of unique multi keys that the bag contains.
	 * @param index - the index by which the sorting should be done first
	 * @return - a set of unique key combinations
	 */
	@SuppressWarnings({ "rawtypes", "unchecked" })
	public TreeSet<ExtendedMultiKey> getSortedKeySet(int index){
		// add each component of the multi key in the comparator
		if (index <= 4) {
			Collection<Comparator> comparators = new ArrayList<Comparator>();
			comparators.add(new MultiKeyComparator(index));
			for (int i = 0; i < maxSize; i++) {
				if (i != index) {
					comparators.add(new MultiKeyComparator(i));
				}
			}
			Comparator comparator =
					   ComparatorUtils.chainedComparator(comparators);

			//retrieve the components of the multi key
			Set<ExtendedMultiKey> set = new TreeSet<ExtendedMultiKey>(comparator);
			set.addAll(masterBag.uniqueSet());
			return (TreeSet)set;
		}

		return null;
	}


	/**
	 * Determines if bag contains a specific multikey.
	 * Wild cards are allowed.
	 * @param multiKey - the multikey of interest
	 * @throws IllegalArgumentException if size of the key is not equal to the keys in the bag.
	 * @return - true the bag contains the multikey; false otherwise
	 */
	public boolean contains(ExtendedMultiKey multiKey) {
		if (masterBag.size()>0) {
			if (multiKey.size() != maxSize) {
				throw new IllegalArgumentException("contains() can only be called with a key of size equal to the multiKey size");
			}
			boolean init = false;
			return recursiveContains(multiKey,init);
		} else
			return false;
	}

	/**
	 * Returns the sorted set of unique key values corresponding to the
	 * keyIndex in the multi key under a String representation.
	 * @param keyIndex - the index of the multi key component of interest
	 * @return - a set of unique key component values under a String
	 *  	     representation
	 */
	public final TreeSet<String> getKeyValuesAsString(final int keyIndex){
		if (updateKeyValues)
			for (int i = 0; i < maxSize; i++)
				if (keyStrings.get(i) != null)
					extractKeyComponentValuesAsString(i);

		if (keyStrings.get(keyIndex) == null)
			extractKeyComponentValuesAsString(keyIndex);

		return keyStrings.get(keyIndex);
	}

	/**
	 * Returns the sorted set of unique key values corresponding to the
	 * keyIndex in the multi key under an Object representation.
	 * @param keyIndex - the index of the multi key component of interest
	 * @return - a set of unique key component values under a String
	 * 	         representation
	 */
	public TreeSet<Object> getKeyValuesAsObject(int keyIndex) {
		if (updateKeyValues)
			extractKeyComponentValuesAsObjects();

		if (keyObjects.get(keyIndex) == null)
			extractKeyComponentValuesAsObjects(keyIndex);

		return keyObjects.get(keyIndex);
	}

	/**
	 * Returns a multi key bag that represents a subset of this bag, formed by
	 * elements that at keyComponentIndex have the keyComponent value.
	 * @param keyComponentIndex - the index of the component in the multi key
	 * @param keyComponent - the key component of interest
	 * @return - a multi key bag that represents the wanted subset
	 */
	public MultiKeyBag getSubBag(int keyComponentIndex, Object keyComponent) {
		if (keyComponentIndex < 4) {
			TreeSet<ExtendedMultiKey> keySet = getSortedKeySet(keyComponentIndex);
			MultiKeyBag subBag = new MultiKeyBag();
			for (ExtendedMultiKey key : keySet){
				if (key.getKey(keyComponentIndex).equals(keyComponent)){
					long count = getCount(key);
					//make sure there are occurrences of the formed key
					if (count != 0)
						subBag.add(key, (int) count);
				}
			}
			return subBag;
		}

		return null;
	}

	/**
	 * Returns a map containing as keys the values of the key component found at
	 * keyComponentIndex and as values the sum of frequencies for each of the key
	 * component values. The multiKey can contain wild cards. The multi key component
	 * found at keyComponentIndex is iteratively replaced by each of it's possible values
	 * and the count using the new multi key is added in the map.
	 * @param keyComponentIndex - the index of the component in the multi key
	 * @param multiKey - the extended multi key for which we want the sub map; wild cards allowed
	 * @return - a sorted map containing the different values for the key component at
	 * keyComponentIndex and as values the frequencies for each value
	 */
	public TreeMap<Object, Object> getSubBagAsMap(int keyComponentIndex, ExtendedMultiKey multiKey) {
		if (keyComponentIndex < 4) {
			TreeMap<Object, Object> subMap = new TreeMap<Object, Object>();
			Set<Object> keySet = getKeyComponentValuesAsObjects(keyComponentIndex);
			for (Object keyValue : keySet){
				multiKey = multiKey.setKeyComponent(keyComponentIndex, keyValue);
				long count = getCount(multiKey);
				//make sure we have occurences of the formed multikey
				if (count != 0)
					subMap.put(keyValue, count);
			}

			return subMap;
		}

		return null;
	}

	/**
	 * Returns a hash bag containing as keys the values of the key component found at
	 * keyComponentIndex and as values the sum of frequencies for each of the key
	 * component values. The multiKey can contain wild cards. The multi key component
	 * found at keyComponentIndex is iteratively replaced by each of it's possible values
	 * and the count using the new multi key is added in the map.
	 * @param keyComponentIndex - the index of the component in the multi key
	 * @param multiKey - the extended multi key for which we want the sub bag; wild cards allowed
	 * @return - a hash bag containing the different values for the key component at
	 * keyComponentIndex and as values the frequencies for each value
	 */
	public HashBag getSubBagAsHashBag(int keyComponentIndex, ExtendedMultiKey multiKey) {
		if (keyComponentIndex < 4) {
			HashBag subBag = new HashBag();
			Set<Object> keySet = getKeyComponentValuesAsObjects(keyComponentIndex);
			for (Object keyValue : keySet){
				multiKey = multiKey.setKeyComponent(keyComponentIndex, keyValue);
				subBag.add(keyValue, (int)getCount(multiKey));
			}
			return subBag;
		}

		return null;
	}

	/** Returns an unsorted histogram if the multi key bag has an integer as last key
	 * @param multiSubKey - sub key for the histogram. The length should be one lower
	 * than the size of key in this bag, e.g., if the bag contains gender, age group,
	 * patient time then multiSubKey should be: Male,"10-20"
	 * @return an unsorted hash bag with integers as key and frequency as value
	 */
	public HashBag getHistogram(ExtendedMultiKey multiSubKey) {
		//Set<ExtendedMultiKey> keySet = getSortedKeySet();
		@SuppressWarnings("unchecked")
		Set<ExtendedMultiKey> keySet = masterBag.uniqueSet();

		HashBag frequencyBag = new HashBag();
		boolean found = false;
		for (ExtendedMultiKey key : keySet) {
			if (key.isSubset(multiSubKey)) {
				if(key.isLastNumeric()) {
					//multiply frequency of the key
					frequencyBag.add(key.getKey(key.size() - 1), getMasterBag().getCount(key));
				} else {
					//add a single item
					frequencyBag.add(getMasterBag().getCount(key));
				}
				found = true;
			}
		}

		return (found ? frequencyBag : null);
	}

	/** Returns a sorted histogram of the subbag defined by the multiSubKey
	 * @param multiSubKey - sub key for the histogram. The length should can be one lower
	 * than the size of key in this bag, e.g., if the bag contains gender, age group,
	 * patient time then multiSubKey can be: Male,"10-20".
	 * A full key is also allowed if the last key is a Double of Integer Wildcard, e.g.,
	 * Male, "10-20", Widcard.INTEGER(). This is allows wildcard selection for multikeys of size two!
	 * @return a sorted hash bag with integers or doubles as key and frequency as value
	 */
	public TreeMap<Object,Integer> getHistogramRec(ExtendedMultiKey multiSubKey) {

		extractKeyComponentValuesAsObjects();
		Set<Object> keyValues = null;
		TreeMap<Object,Integer> frequencyBag = null; //holds the final histogram

		if ((multiSubKey.getKey(multiSubKey.size()-1) instanceof Wildcard && multiSubKey.size() == maxSize) ||
			(multiSubKey.size() == maxSize-1)){
			frequencyBag = new TreeMap<Object,Integer>();
			//get all the numeric values
			keyValues = getKeyValuesAsObject(maxSize-1);
			double doubleKey;
			for (Object key : keyValues) {
				ExtendedMultiKey fullKey = null;
				//if the multikey is full size fill in the numeric value otherwise add it
				if (multiSubKey.size() == maxSize) {
					fullKey = multiSubKey.setKeyComponent(maxSize-1, key);
				} else {
					fullKey = multiSubKey.add(key);
				}
				long frequency = getCount(fullKey);
				if (key instanceof Integer){
					Integer intKey = (Integer) key;
					doubleKey = intKey.intValue();
				}
				else {
					doubleKey = (Double) key;
				}
				if (frequency > 0) {
					frequencyBag.put(doubleKey, (int) frequency); //TODO long to int conversion dangerous!?
				}
			}
		} else {
			throw new IllegalArgumentException("GetHistogramRec: check the size and content of multikey "+ multiSubKey);
		}

		return frequencyBag;
	}

	/**
	 * Returns a StatisticalSummary object containing the descriptive
	 * statistics of a subset of this bag defined by multiSubKey.
	 * @param multiSubKey - the key defining the subset of data wanted.
	 * Wild cards are allowed.
	 * @return - the descriptive statistics of the subset
	 */
	public StatisticalSummary getStatisticalSummary(ExtendedMultiKey multiSubKey){
		HistogramStats stats=null;
		TreeMap<Object,Integer> histogram = getHistogramRec(multiSubKey);
		if (histogram != null)
			stats = new HistogramStats(histogram);

		return (histogram != null ? stats.getStatsSummary() : null);

	}

	/**
	 * Returns a HistogramStats object containing the descriptive
	 * statistics of a subset of this bag defined by multiSubKey.
	 * @param multiSubKey - the key defining the subset of data wanted.
	 * Wild cards are allowed.
	 * @return - the descriptive statistics of the subset or null of
	 * the multiSubKey is not found
	 */
	public HistogramStats getHistogramStats(ExtendedMultiKey multiSubKey) {

		if (this.masterBag.size()>0){

			if (LOG){
				timer.start();
				@SuppressWarnings("unused")
				HashBag histogram = getHistogram(multiSubKey);
				timer.stop();
				System.out.println("The histogram created in " + timer.getTotal());
				timer.start();
			}

			TreeMap<Object,Integer> histogram = getHistogramRec(multiSubKey);

			if (LOG){
				timer.stop();
				System.out.println("The recursive histogram created in "+timer.getTotal());
			}

			if (histogram != null && histogram.size() > 0) {
				return new HistogramStats(histogram);
			}
		}

		//return a NaN HistrogramStats object
		return new HistogramStats();
	}

	/**
	 * Retrieves the sum of the elements having this multiKey.
	 * Wild cards are allowed. If no wild card is present
	 * the count of the element having this multiKey in the bag is returned.
	 * @param multiKey - the multi key of interest (wild cards allowed)
	 * @return - the sum of the elements under the multiKey or count if no wild cards present
	 */
	public long getCount(ExtendedMultiKey multiKey){
		return count(multiKey);
	}



	//********************************* PRIVATE METHODS ****************************************//

	/**
	 * Determines recursively if the bag contains a multikey.
	 * Wild cards are allowed.
	 * @param multiKey - the multikey of interest
	 * @return - true the bag contains the multikey; false otherwise
	 */
	private boolean recursiveContains(ExtendedMultiKey multiKey, boolean contains) {
		int wildCardIndex = multiKey.getWildCardIndex();
		if (wildCardIndex != -1) {
			TreeSet<Object> keyValues = getKeyValuesAsObject(wildCardIndex);
			for (Object key : keyValues) {
				if (contains) {
					break;
				}
				ExtendedMultiKey newKey = multiKey.setKeyComponent(wildCardIndex, key);
				contains = recursiveContains(newKey,contains);
			}
		}

		return contains || masterBag.contains(multiKey);
	}

	/** Returns a StatisticalSummary if the multi key bag has an integer as last key.
	 * @param multiSubKey - sub key for the stats, asterisks are allowed as "*".
	 * The length should be lower than the size of key in this bag, e.g.,
	 * if the bag contains gender,age group, patient time then multiSubKey could be: Male,"10-20"
	 * @return a StatisticalSummary object or Null in case the Bag contains multi keys without an integer as last key
	 */
	private StatisticalSummary getStatsApache(ExtendedMultiKey multiSubKey){

		StatisticalSummary summary = new StatisticalSummary();
		DescriptiveStatistics stats = new DescriptiveStatistics();

		//Set<ExtendedMultiKey> keySet = getSortedKeySet();
		@SuppressWarnings("unchecked")
		Set<ExtendedMultiKey> keySet = masterBag.uniqueSet();

		boolean found = false;

		for (ExtendedMultiKey key : keySet) {
			//System.out.println("key "+key);
			if (key.isSubset(multiSubKey)) {
				found = true;
				int value = (Integer) key.getKey(key.size() - 1);
				int frequency = getMasterBag().getCount(key);
				for (int i=1; i <= frequency; i++){
					stats.addValue(value);
				}
			}
		}

		if (found) {
			summary.setMax(stats.getMax());
			summary.setMean(stats.getMean());
			summary.setSum(stats.getSum());
			summary.setStd(stats.getStandardDeviation());
			summary.setVariance(stats.getVariance());
			summary.setMedian(stats.getPercentile(50));
			summary.setMin(stats.getMin());
			summary.setN(stats.getN());
			summary.setP1(stats.getPercentile(1));
			summary.setP2(stats.getPercentile(2));
			summary.setP5(stats.getPercentile(5));
			summary.setP10(stats.getPercentile(10));
			summary.setP25(stats.getPercentile(25));
			summary.setP75(stats.getPercentile(75));
			summary.setP90(stats.getPercentile(90));
			summary.setP95(stats.getPercentile(95));
			summary.setP98(stats.getPercentile(98));
			summary.setP99(stats.getPercentile(99));
			return summary;
		}

		return null;
	}

	/**
	 * Retrieves the count of occurrences of multiKey.
	 * Makes use of the recursive count method of this class.
	 * @param multiKey - the multi key of interest
	 * @return - the number of occurrences for multiKey
	 */
	private long count(ExtendedMultiKey multiKey){
		long init = 0;
		return recursiveCount(multiKey, init);
		//return iterativeCount(multiKey);
	}

	/**
	 * Count the number of elements with multiKey.
	 * Wild cards are allowed. NOTE: that the recursion
	 * could result in keys that have no values! A check
	 * should be put in place for formed keys with no occurence
	 * in the bag (i.e., count is 0)
	 * @param multiKey - the multikey of interest
	 * @param count - updated counter using recursion
	 * @return - the total number of elements
	 */
	private long recursiveCount(ExtendedMultiKey multiKey, long count){
		//get the index of the first wildcard in this key
		int wildCardIndex = multiKey.getWildCardIndex();

		//if there is a wildcard then instantiate all values
		//any other wildcards are instantiated automatically by tail recursion
		if (wildCardIndex != -1){
			TreeSet<Object> keyValues = getKeyValuesAsObject(wildCardIndex);
			if (keyValues != null) {
				for (Object key : keyValues){
					ExtendedMultiKey newKey = multiKey.setKeyComponent(wildCardIndex, key);
					count = recursiveCount(newKey,count);
				}
			}else {
				count = 0;
			}
		}

		//get the count of the fully instantiated key
		return count += masterBag.getCount(multiKey);
	}

	/**
	 * Count the number of elements with multiKey.
	 * Wild cards are allowed. NOT FINISHED YET!
	 * @param multiKey - the multikey of interest
	 * @return - the total number of elements
	 */
	@SuppressWarnings("unused")
	private long iterativeCount(ExtendedMultiKey multiKey){
		long count = 0;
		int  numberOfWildcards = multiKey.getNrWildcards();
		int  wildCardIndex;
		List<Integer> wildcardIndices = multiKey.getWildCardIndices();

		switch (numberOfWildcards) {
		case 0: return masterBag.getCount(multiKey);

		case 1: wildCardIndex = wildcardIndices.get(0);
		for (Object key : getKeyValuesAsObject(wildCardIndex)){
			ExtendedMultiKey multiKeyNew = multiKey.setKeyComponent(wildCardIndex, key);
			count += masterBag.getCount(multiKeyNew);
		}
		return count;

		case 2: for (int i=0;i<wildcardIndices.size();i++){
			wildCardIndex = wildcardIndices.get(i);
		}
		int index1 = wildcardIndices.get(0);
		int index2 = wildcardIndices.get(1);
		for (Object key1 : getKeyValuesAsObject(index1)){
			ExtendedMultiKey multiKeyNew1 = multiKey.setKeyComponent(index1, key1);
			for (Object key2 : getKeyValuesAsObject(index2)){
				ExtendedMultiKey multiKeyNew2 = multiKeyNew1.setKeyComponent(index2, key2);
				count += masterBag.getCount(multiKeyNew2);
			}
		}
		return count;
		default: return count;
		}
	}

	/**
	 * Will populate the map containing all unique key values for each
	 * of the key component under an Object representation.
	 * This method should be called only once and the extractKeys
	 * flag set to false.
	 * @param index - the index of the key component in multi key
	 */
	private void extractKeyComponentValuesAsObjects(int index){
		keyObjects.put(index, getKeyComponentValuesAsObjects(index));
		updateKeyValues = false;
	}

	/**
	 * Will populate the map containing all unique key values for each of the
	 * key component under a String representation.
	 * This method should be called only once and the extractKeys flag set to false.
	 * @param index - the index of the key component in multi key
	 */
	private void extractKeyComponentValuesAsString(int index){
		keyStrings.put(index, getKeyComponentValuesAsString(index));
		updateKeyValues = false;
	}

	/**
	 * Returns the sorted set of unique key values as strings corresponding to the keyIndex in the multi key.
	 * @param keyIndex - the index of the multi key component of interest
	 * @return - a sorted set of unique key component values as String representation
	 */
	@SuppressWarnings({"unchecked" })
	private TreeSet<String> getKeyComponentValuesAsString(int keyIndex){
		if (keyIndex >= 0 && keyIndex <= 4){ //a key of 5 elements is maximum supported
			//retrieve unique values of the key component at keyIndex
			TreeSet<String> uniqueValues = new TreeSet<String>();
			for (MultiKey key : (Set<MultiKey>)masterBag.uniqueSet())
				uniqueValues.add(key.getKey(keyIndex).toString());
			return uniqueValues;
		}

		return null;
	}

	/**
	 * Returns the sorted set of unique key values as objects corresponding to the keyIndex in the multi key.
	 * @param keyIndex - the index of the multi key component of interest
	 * @return - a sorted set of unique key component values under an Object representation
	 */
	@SuppressWarnings({"unchecked" })
	private TreeSet<Object> getKeyComponentValuesAsObjects(int keyIndex){
		if (keyIndex >= 0 && keyIndex <= 4){ //a key of 5 elements is maximum supported
			//retrieve unique values of the key component at keyIndex
			TreeSet<Object> uniqueValues = new TreeSet<Object>();
			for (MultiKey key : (Set<MultiKey>)masterBag.uniqueSet())
				uniqueValues.add(key.getKey(keyIndex));
			return uniqueValues;
		}

		return null;
	}

	/**
	 * Extracts all individual components of the MultiKeys.
	 */
	@SuppressWarnings({"unchecked" })
	private void extractKeyComponentValuesAsObjects(){
		//TODO: how to do this in an array to loop over?

		if (updateKeyValues) {
			TreeSet<Object> uniqueValuesKey0 = new TreeSet<Object>();
			TreeSet<Object> uniqueValuesKey1 = new TreeSet<Object>();
			TreeSet<Object> uniqueValuesKey2 = new TreeSet<Object>();
			TreeSet<Object> uniqueValuesKey3 = new TreeSet<Object>();
			TreeSet<Object> uniqueValuesKey4 = new TreeSet<Object>();
			Set<ExtendedMultiKey> keySet = masterBag.uniqueSet();

			for (MultiKey key : keySet){
				for (int i = 0; i < key.size(); i++) {
					Object component = new Object();
					component = key.getKey(i);
					switch (i) {
					case 0: uniqueValuesKey0.add(component); break;
					case 1: uniqueValuesKey1.add(component); break;
					case 2: uniqueValuesKey2.add(component); break;
					case 3: uniqueValuesKey3.add(component); break;
					case 4: uniqueValuesKey4.add(component); break;
					default: break;
					}
				}
			}
			for (int i = 0; i < 5; i++) {
				switch (i) {
				case 0: keyObjects.put(i, uniqueValuesKey0); break;
				case 1: keyObjects.put(i, uniqueValuesKey1); break;
				case 2: keyObjects.put(i, uniqueValuesKey2); break;
				case 3: keyObjects.put(i, uniqueValuesKey3); break;
				case 4: keyObjects.put(i, uniqueValuesKey4); break;
				default: break;
				}
			}

			updateKeyValues = false;
		}
	}

	//******************* END OF PRIVATE METHODS ***************************//

	/**
	 * Prints the contents of the bag.
	 * @return - a string representation of the contents of the bag
	 */
	@Override
	public final String toString() {
		StringBuilder out = new StringBuilder();
		out.append(System.lineSeparator());

		Set<ExtendedMultiKey> set = getSortedKeySet();

		for (ExtendedMultiKey iMultiKey : set) {
			out.append("[" + StringUtils.join(iMultiKey.getKeys(), ',')
					       + "] = "
		                   + masterBag.getCount(iMultiKey)
		                   + java.lang.System.lineSeparator());
		}

		return out.toString();
	}


	//********************* NESTED CLASSES **********************************//

	/**
	 * Nested class implementing the object property Transformer
	 * used in the decorator pattern.
	 * @see http://apachecommonstipsandtricks.blogspot.nl/2009/02/examples-of-bag-and-multikeys.html
	 */
	private static class PropertiesMultiKeyTransformer implements Transformer{
		//list of the methods to be transformed
		String[] methodNames;

		//CONSTRUCTOR
		private PropertiesMultiKeyTransformer(String[] methodNames){
			this.methodNames = methodNames;
		}

		/**
		 * Transforms the properties of a multikey.
		 * @param o - the object to be transformed
		 * @return - the transformed object
		 */
		@Override
		public Object transform(Object o){
			List<Object> ooos = new ArrayList<Object>();
			for (String methodName : methodNames){
				try	{
					ooos.add(PropertyUtils.getProperty(o, methodName));
				}catch (Exception e) {
					throw new FunctorException(e);
				}
			}

			return new MultiKey(ooos.toArray(new Object[ooos.size()]));
		}
	}

	//ATTRIBUTE GETTERS AND SETTERS
	public String[] getMultiKeyStr() {
		return multiKey;
	}

	public void setMultiKey(String[] multiKeyStr) {
		this.multiKey = multiKeyStr;
	}

	public Bag getMasterBag() {
		return masterBag;
	}

	public void setMasterBag(Bag uberBag) {
		this.masterBag = uberBag;
	}

	public Bag getBag() {
		return decoratedBag;
	}

	public void setBag(Bag decoratedBag) {
		this.decoratedBag = decoratedBag;
	}

	public int getSize() {
		return masterBag.size();
	}

	private static final long MEGABYTE = 1024L * 1024L;

	public static long bytesToMegabytes(long bytes) {
		return bytes / MEGABYTE;
	}

	/**
	 * Main method for testing and debugging.
	 * @param args - none
	 */
	public static void main(String[] args) {

		MultiKeyBag bag = new MultiKeyBag();
	//	RandomDataGenerator generator = new RandomDataGenerator();

		Timer timer = new Timer();
	//	int dataSize = 1500000;
		int uniquePatientTime = 36500;
		int nrYears = 60;

		//add some data
		timer.start();

		// Get the Java runtime
	//	Runtime runtime = Runtime.getRuntime();
		for (int year = 1900; year < 1900 + nrYears; year++) {
			System.out.println("year: " + year);
			for (int patientTime = 0; patientTime < uniquePatientTime; patientTime++) {
				//bag.add(new ExtendedMultiKey("Male",year,"10-20",generator.nextInt(0, uniquePatientTime)));
				//bag.add(new ExtendedMultiKey("Male",year,"20-30",generator.nextInt(0, uniquePatientTime)));
				bag.add(new ExtendedMultiKey("Male",year,"10-20",patientTime));
				bag.add(new ExtendedMultiKey("Male",year,"20-30",patientTime));
				bag.add(new ExtendedMultiKey("Female",year,"10-20",patientTime));
				bag.add(new ExtendedMultiKey("Female",year,"20-30",patientTime));
			}

		}

		timer.stop();
		System.out.println("The bag, created in " + timer.toString());

		//System.out.println(bag.toString());

		//compare using Apache Statistics
		timer.start();

		StatisticalSummary stats = new StatisticalSummary();
		stats = bag.getStatsApache(
				new ExtendedMultiKey("Male", Wildcard.INTEGER(), "10-20"));
		if (stats != null) {
			System.out.println("Male 10-20: " + stats.toString());
		}

		stats = bag.getStatsApache(
				new ExtendedMultiKey("Female", 1900, Wildcard.STRING()));
		if (stats != null) {
			System.out.println("Female 1900: " + stats.toString());
		}

		timer.stop();
		System.out.println("The stats, created in " + timer.toString());

		//test using HistogramStats
		timer.start();

		System.out.println("Stats created by MultiKeyBagstats2:");
		StatisticalSummary stats2 = new StatisticalSummary();

		stats2 = bag.getStatisticalSummary(
				new ExtendedMultiKey("Male", Wildcard.INTEGER(), "10-20"));
		if (stats2 != null) {
			System.out.println("Male 10-20: " + stats2.toString());
		}

		stats2 = bag.getStatisticalSummary(
				new ExtendedMultiKey("Female", 1900, Wildcard.STRING()));
		if (stats2 != null) {
			System.out.println("Female 1900 : " + stats2.toString());
		}

		System.out.println("no wildcard");
		HistogramStats bagStats = bag.getHistogramStats(
				new ExtendedMultiKey("Male",1901, "10-20"));
		bagStats = bag.getHistogramStats(new ExtendedMultiKey("Male",1901, "10-20"));
		bagStats = bag.getHistogramStats(new ExtendedMultiKey("Male",1901, "10-20"));

		System.out.println("one wildcard");
		bagStats = bag.getHistogramStats(new ExtendedMultiKey("Male",Wildcard.INTEGER(), "10-20"));
		bagStats = bag.getHistogramStats(new ExtendedMultiKey("Male",Wildcard.INTEGER(), "10-20"));
		bagStats = bag.getHistogramStats(new ExtendedMultiKey("Male",Wildcard.INTEGER(), "10-20"));

		System.out.println("two wildcards");
		bagStats = bag.getHistogramStats(new ExtendedMultiKey("Male",Wildcard.INTEGER(), Wildcard.STRING()));
		bagStats = bag.getHistogramStats(new ExtendedMultiKey("Male",Wildcard.INTEGER(), Wildcard.STRING()));
		bagStats = bag.getHistogramStats(new ExtendedMultiKey("Male",Wildcard.INTEGER(), Wildcard.STRING()));
		System.out.println("Mean: " + bagStats.getMean());
		System.out.println("SD: " + bagStats.getStdDev());
		timer.stop();
		System.out.println("The stats, created in "+timer.toString());

		timer.start();
		//		System.out.println("Count: " + bagStats.getHigherThan(500));
		timer.stop();
		System.out.println("Count done in "+timer.toString());

		//Check contains
		System.out.println("Contains: " + bag.contains(new ExtendedMultiKey("Male",Wildcard.INTEGER(), "10-20",Wildcard.INTEGER())));
	}
}
