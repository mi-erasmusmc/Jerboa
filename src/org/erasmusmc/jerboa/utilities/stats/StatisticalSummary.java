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
 * $Rev:: 3792              $:  Revision of last commit                                   *
 * $Author:: root           $:  Author of last commit                                     *
 * $Date:: 					$:  Date and time (CET) of last commit						  *
 ******************************************************************************************/

package org.erasmusmc.jerboa.utilities.stats;

/**
 * This class is a collection of getters and setters for basic statistics.
 *
 * @author PR
 *
 */
public class StatisticalSummary{

	public double median;
	public double mean;
	public double sum;
	public double std;
	public double variance;
	public double n;
	public double max;
	public double min;
	public double p1;
	public double p2;
	public double p5;
	public double p10;
	public double p25;
	public double p75;
	public double p90;
	public double p95;
	public double p98;
	public double p99;

	//CONSTRUCTORS
	public StatisticalSummary(){}

	public StatisticalSummary(double median, double mean ,double sum,double std, double variance,
			double n, double max, double min, double p1, double p2, double p5,
			double p10, double p25, double p75, double p90, double p95,
			double p98, double p99) {
		super();
		this.median = median;
		this.mean = mean;
		this.sum = sum;
		this.std = std;
		this.variance = variance;
		this.n = n;
		this.max = max;
		this.min = min;
		this.p1 = p1;
		this.p2 = p2;
		this.p5 = p5;
		this.p10 = p10;
		this.p25 = p25;
		this.p75 = p75;
		this.p90 = p90;
		this.p95 = p95;
		this.p98 = p98;
		this.p99 = p99;
	}

	@Override
	public String toString() {
		return "StatisticalSummary [ mean=" +mean + ", median=" + median + ", sum=" + sum+ ", std=" + std
				+ ", variance=" + variance + ", n=" + n + ", max=" + max
				+ ", min=" + min + ", p1=" + p1 + ", p2=" + p2 + ", p5=" + p5
				+ ", p10=" + p10 + ", p25=" + p25 + ", p75=" + p75 + ", p90="
				+ p90 + ", p95=" + p95 + ", p98=" + p98 + ", p99=" + p99 + "]";
	}


	//GETETRS AND SETTERS
	public void setMean(double mean) {
		this.mean = mean;
	}
	public double getMean() {
		return mean;
	}
	public double getMedian() {
		return median;
	}
	public void setMedian(double median) {
		this.median = median;
	}
	public double getSum() {
		return sum;
	}
	public void setSum(double sum) {
		this.sum = sum;
	}
	public double getStd() {
		return std;
	}
	public void setStd(double std) {
		this.std = std;
	}
	public double getVariance() {
		return variance;
	}
	public void setVariance(double variance) {
		this.variance = variance;
	}
	public double getN() {
		return n;
	}
	public void setN(double n) {
		this.n = n;
	}
	public double getMax() {
		return max;
	}
	public void setMax(double max) {
		this.max = max;
	}
	public double getMin() {
		return min;
	}
	public void setMin(double min) {
		this.min = min;
	}
	public double getP1() {
		return p1;
	}
	public void setP1(double p1) {
		this.p1 = p1;
	}
	public double getP2() {
		return p2;
	}
	public void setP2(double p2) {
		this.p2 = p2;
	}
	public double getP5() {
		return p5;
	}
	public void setP5(double p5) {
		this.p5 = p5;
	}
	public double getP10() {
		return p10;
	}
	public void setP10(double p10) {
		this.p10 = p10;
	}
	public double getP25() {
		return p25;
	}
	public void setP25(double p25) {
		this.p25 = p25;
	}
	public double getP75() {
		return p75;
	}
	public void setP75(double p75) {
		this.p75 = p75;
	}
	public double getP90() {
		return p90;
	}
	public void setP90(double p90) {
		this.p90 = p90;
	}
	public double getP95() {
		return p95;
	}
	public void setP95(double p95) {
		this.p95 = p95;
	}
	public double getP98() {
		return p98;
	}
	public void setP98(double p98) {
		this.p98 = p98;
	}
	public double getP99() {
		return p99;
	}
	public void setP99(double p99) {
		this.p99 = p99;
	}

}
