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
 * 																						  *
 * $Rev:: 3792              $:  Revision of last commit                                   *
 * $Author:: root           $:  Author of last commit                                     *
 * $Date:: 					$:  Date and time (CET) of last commit						  *
 ******************************************************************************************/

package org.erasmusmc.jerboa.utilities.stats;

import java.text.DecimalFormat;
import java.util.TreeMap;

import org.erasmusmc.jerboa.config.DataDefinition;
import org.erasmusmc.jerboa.utilities.StringUtilities;
import org.erasmusmc.jerboa.utilities.stats.StatisticalSummary;

/**
 * This class computes statistics from a histogram of Integers or Doubles.
 * For optimization reasons, only in the first call a statistic is calculated.
 * In case it is constructed with an empty Map, all statistics will return NaN or 0.
 *
 * @author MG {@literal &} PR
 *
 */

public class HistogramStats{

	//data holders
	private TreeMap<Object,Integer> data;

	private long count = 0;							// the number of entries
	private double mean = Double.NaN;				// the average of the values in the list
	private double median = Double.NaN ;			// the median of the values in the list
	private double sum = Double.NaN;				// the sum of all the entries
	private double variance = Double.NaN;			// the variance of all the entries

	//number format flags
	private boolean integerType = false;			// histogram has integer values
	private boolean doubleType = false;				// histogram has double values

	//booleans to determine if a statistic is already calculated
	private boolean countSet = false;
	private boolean meanSet = false;
	private boolean medianSet = false;
	private boolean sumSet = false;
	private boolean varianceSet = false;

	//double precision
	DecimalFormat precisionFormat = StringUtilities.DECIMAL_FORMAT_FORCE_PRECISION;

	//CONSTRUCTOR
	/**
	 * Constructor receiving the data under a sorted map representation.
	 * @param data - the data used to calculate the statistics
	 */
	public HistogramStats(TreeMap<Object,Integer> data) {
		if (data != null && data.size()>0){
			this.data = data;
			if (data.firstKey() instanceof Integer) integerType = true;
			else
				if (data.firstKey() instanceof Double) doubleType = true;
				else
					throw new IllegalArgumentException("HistogramStats can only be initialized with a Map with Integer of Double keys and Integer Values");
		}
	}

	/**
	 * Create empty Histogram with NaN values
	 */
	public HistogramStats() {
	}

	/**
	 * Calculates the mean of distribution.
	 * In case of an empty histogram NaN is returned.
	 * @return - the mean
	 */
	private double calculateMean() {
		if (data != null){
			//initialize variables
			long total = 0;
			double sum = 0;
			long  frequency = 0;

			//check if integers or doubles
			for(Object key : this.data.keySet()) {
				//get frequency of an element
				frequency = data.get(key);
				//add it to total
				total += frequency;
				//update sum with current element times its frequency
				if (integerType){
					Integer intKey = (Integer) key;
					sum += (double) (intKey.intValue() * frequency);
				} else
					if (doubleType){
						Double doubleKey = (Double) key;
						sum += (double) (doubleKey.doubleValue() * frequency);

					}
			}

			mean = (double)sum/(double)total;
			meanSet = true;
			return mean;
		}
		else return Double.NaN;
	}

	/**
	 * Calculates the total sum of the histogram.
	 * Each bin is multiplied by its frequency and summed.
	 * In case of an empty histogram NaN is returned.
	 * @return - the total sum of all items
	 */
	private double calculateSum(){
		if (data != null){
			sum = 0;
			for (Object key : this.data.keySet())
				if (integerType){
					Integer intKey = (Integer) key;
					sum += (double) data.get(key)* (double) intKey.intValue();
				} else
					if (doubleType){
						Double doubleKey = (Double) key;
						sum += (double) data.get(key)* (double) doubleKey.doubleValue();
					}
			sumSet = true;
			return sum;
		}
		else return 0;
	}

	/**
	 * Counts the total number of items in the histogram:
	 * the sum of the frequency of each bin.
	 * In case of an empty histogram zero is returned.
	 * @return - the total number of items
	 */
	private long calculateCount(){
		if (data != null){
			count = 0;
			for (Object key : this.data.keySet())
				count += data.get(key);
			countSet = true;
			return count;
		}
		else return 0;
	}

	/**
	 * Calculates the variance of the histogram
	 * In case of an empty histogram zero is returned.
	 * @return - the variance of the items
	 */
	private double calculateVariance(){
		if (data != null){
			double sumOfSquares = 0;
			for (Object key : this.data.keySet()){
				if (integerType){
					Integer intKey = (Integer) key;
					sumOfSquares += ((getMean() - (double) intKey.intValue())*(getMean() - (double) intKey.intValue()))*data.get(key);
				} else
					if (doubleType){
						Double doubleKey = (Double) key;
						sumOfSquares += ((getMean() - (double) doubleKey.doubleValue())*(getMean() - (double) doubleKey.doubleValue()))*data.get(key);
					}
			}

			if (getCount()>1)
				variance = (double) sumOfSquares/ (double) (getCount()-1);
			else
				variance = 0;

			varianceSet = true;
			return variance;
		}
		else return 0;
	}
	/**
	 * Returns a statistical summary.
	 * In case the histogram is empty "NO DATA" is returned.
	 */
	public String toString(){
		if (data != null){
			return getStatsSummary().toString();
		}
		return DataDefinition.NO_DATA;
	}

	/**
	 * Returns a statistical summary.
	 * count,mean,sd,min,max.
	 * @return - a string representation of the statistical summary
	 */
	public String toStringShortSummary(){
		if (data != null){
			return 	getCount() + "," + precisionFormat.format(getMean()) + "," + precisionFormat.format(getStdDev()) + "," + precisionFormat.format(getMin()) + "," + precisionFormat.format(getMax());
		}
		return DataDefinition.NO_DATA;
	}

	/**
	 * Returns true if there is data to calculate statistics.
	 * False otherwise.
	 * @return  - true if there is at least one element of data
	 */
	public boolean hasData(){
		return data != null && data.size() > 0;
	}

	//GETTERS

	/**
	 * Returns the minimum value.
	 * In case of an empty histogram Double.NaN is returned.
	 * @return - minimum value of the histogram.
	 */
	public double getMin() {
		if (data != null){
			if (this.data.firstKey() instanceof Integer){
				Integer intKey = (Integer) this.data.firstKey();
				return (double) intKey.intValue();
			} else
				if (this.data.firstKey() instanceof Double){
					Double doubleKey = (Double) this.data.firstKey();
					return (double) doubleKey.doubleValue();
				}
		}
		return Double.NaN;
	}

	/**
	 * Returns the maximum value.
	 * In case of an empty histogram NaN is returned.
	 * @return - maximum value of the histogram.
	 */
	public double getMax() {
		if (data != null){
			if (this.data.lastKey() instanceof Integer){
				Integer intKey = (Integer) this.data.lastKey();
				return (double) intKey.intValue();
			} else
				if (this.data.firstKey() instanceof Double){
					Double doubleKey = (Double) this.data.lastKey();
					return (double) doubleKey.doubleValue();
				}
		}
		return Double.NaN;
	}

	/**
	 * Returns the number of items higher than a value
	 * In case of an empty histogram zero is returned
	 * @param value - the comparator
	 * @return - the total number of items in the histogram
	 */
	public long getHigherThan(int value) {
		if (data != null){
			int nr = 0;
			for (Object key : this.data.keySet()){
				if (integerType){
					Integer intKey = (Integer) key;
					if ((double) intKey.intValue()>value) {
						nr=nr+data.get(key);
					}
				} else
					if (doubleType){
					   Double doubleKey = (Double) key;
						if ((double) doubleKey.doubleValue()>value) {
							nr=nr+data.get(key);
						}
				}
			}
			return nr;
		}
		return 0;
	}

	/**
	 * Returns the nth percentile.
	 * In case of an empty histogram NaN is returned.
	 * @param perc - percentile to compute
	 * @return - the value of the nth percentile
	 */
	public double getPercentile(int perc) {
		count = getCount();
		if (count != 0){

			//retrieve the location of the value representing the percentile in the list
			double rank = ((double)perc/100)* (double)(count+1);
			int index = (int)rank;
			double fraction = rank - index;

			//check if we are at extremes
			if (index <= 0){
				return getMin();
			}

			if (index >= count){
				return getMax();
			}
			//retrieve the value of the percentile
			double sum = 0;
			Object indexKey = this.data.firstKey();
			Object nextKey = indexKey;
			boolean indexFound = false;
			for (Object key : this.data.keySet()){
				if (indexFound) {
					nextKey = key;
					break;
				}
				sum += data.get(key);
				if (sum >= index){
					indexKey = key;
					nextKey = indexKey;
					indexFound = true;
				}
			}

			//if we have the exact value of the percentile in the list, return it
			if (fraction == 0){
				if (integerType){
					Integer intKey = (Integer) indexKey;
					return (double) intKey.intValue();
				} else
					if (doubleType){
						Double doubleKey = (Double) indexKey;
						return (double) doubleKey.doubleValue();
					}
				//or interpolate
			}else {
				if (integerType){
					Integer intIndexKey = (Integer) indexKey;
					Integer intNextKey = (Integer) nextKey;

					if (rank < (double)sum){
						return (double) intIndexKey.intValue();
					}else{
						return (double) intIndexKey.intValue() + fraction*((double) intNextKey.intValue() - (double) intIndexKey.intValue());
					}
				} else
					if (doubleType){
						Double doubleIndexKey = (Double) indexKey;
						Double doubleNextKey = (Double) nextKey;

						if (rank < (double)sum){
							return (double) doubleIndexKey.doubleValue();
						}else{
							return (double) doubleIndexKey.doubleValue() + fraction*((double) doubleNextKey.doubleValue() - (double) doubleIndexKey.doubleValue());
						}
					}

			}
		}
		return Double.NaN;
	}


	/**
	 * Returns the variance.
	 * In case of an empty histogram NaN is returned.
	 * @return - the variance
	 */
	public double getVariance(){
		if (data != null){
			if (!varianceSet) variance = calculateVariance();
			return variance;
		} else return Double.NaN;
	}

	/**
	 * Returns the standard deviation of the values in the stats list
	 * @return - the value of the standard deviation
	 */
	public double getStdDev() {
		return Math.sqrt(getVariance());
	}

	/**
	 * Returns the 25th percentile.
	 * @return - the first quartile
	 */
	public double getFirstQuartile(){
		return getPercentile(25);
	}

	/**
	 * Returns the 50th percentile.
	 * @return - the second quartile (or mean)
	 */
	public double getSecondQuartile(){
		return getPercentile(50);
	}

	/**
	 * Returns the 75th percentile.
	 * @return - the third quartile
	 */
	public double getThirdQuartile(){
		return getPercentile(75);
	}

	/**
	 * gets a summary object containing all the statistics
	 * @return - StatisticalSummary object
	 */
	public StatisticalSummary getStatsSummary(){
		StatisticalSummary summary = new StatisticalSummary();

		summary.setMax(getMax());
		summary.setMean(getMean());
		summary.setSum(getSum());
		summary.setStd(getStdDev());
		summary.setVariance(getVariance());
		summary.setMedian(getPercentile(50));
		summary.setMin(getMin());
		summary.setN(getCount());
		summary.setP1(getPercentile(1));
		summary.setP2(getPercentile(2));
		summary.setP5(getPercentile(5));
		summary.setP10(getPercentile(10));
		summary.setP25(getPercentile(25));
		summary.setP75(getPercentile(75));
		summary.setP90(getPercentile(90));
		summary.setP95(getPercentile(95));
		summary.setP98(getPercentile(98));
		summary.setP99(getPercentile(99));
		return summary;
	}

	//ATTRIBUTE GETTERS AND SETTERS

	/**
	 * Counts the total number of items in the histogram:
	 * the sum of the frequency of each bin.
	 * In case of an empty histogram zero is returned.
	 * @return - the total number of items
	 */
	public long getCount(){
		if (!countSet) count = calculateCount();
		return count;
	}

	/**
	 * Calculates the mean of distribution.
	 * In case of an empty histogram NaN is returned.
	 * @return - the mean
	 */
	public double getMean(){
		if (!meanSet) mean = calculateMean();
		return mean;
	}

	/**
	 * Calculates the mean of distribution.
	 * In case of an empty histogram NaN is returned.
	 * @return - the mean
	 */
	public double getMedian(){
		if (!medianSet) median = getPercentile(50);
		return median;
	}

	/**
	 * Calculates the total sum of the histogram.
	 * Each bin is multiplied by its frequency and summed.
	 * In case of an empty histogram NaN is returned.
	 * @return - the total sum of all items
	 */
	public double getSum() {
		if (!sumSet) sum = calculateSum();
		return sum;
	}

}
