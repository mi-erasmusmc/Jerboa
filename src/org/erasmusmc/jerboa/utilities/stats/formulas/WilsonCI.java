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
 * $Rev:: 4429               $:  Revision of last commit                                  *
 * $Author:: root            $:  Author of last commit                                    *
 * $Date:: 2013-09-10 14:16:#$:  Date and time (CET) of last commit						  *
 ******************************************************************************************/
package org.erasmusmc.jerboa.utilities.stats.formulas;

import org.erasmusmc.jerboa.utilities.Logging;

import org.apache.commons.math3.distribution.NormalDistribution;

/**
 * This class computes the lower and upper limits of the
 *  Wilson Binomial proportion confidence interval for a certain number of observations
 *  with a certain proportion of success (positive outcomes).
 *  @see <a href="http://www-stat.wharton.upenn.edu/~tcai/paper/Binomial-StatSci.pdf">
 *  http://www-stat.wharton.upenn.edu/~tcai/paper/Binomial-StatSci.pdf</a>
 *
 * @author MG
 *
 */
public class WilsonCI{

	//error set to 5% by default for 95% confidence interval
	private double alpha = 0.05;
	//the lower limit of the CI
	private double lower;
	//the upper limit of the CI
	private double upper;

	//CONSTRUCTORS
	/**
	 * Basic constructor.
	 */
	public WilsonCI(){}

	/**
	 * Constructor that would set the error rate.
	 * Direct influence on the confidence interval.
	 * @param alpha - the error
	 * @param n the number of observations
	 * @param k the number of positive (success) outcomes
	 */
	public WilsonCI(double alpha, long n, long k){
		this.alpha = alpha;
		compute(n,k);
	}

	/**
	 * Constructor without specification of the error rate.
	 * The default value of 0.05 is used.
	 * @param n the number of observations
	 * @param k the number of positive (success) outcomes
	 */
	public WilsonCI(long n, long k){
		compute(n,k);
	}

	/**
	 * Will perform the actual computation of the lower and
	 * upper limits of the Wilson confidence interval.
	 * @param n the number of observations
	 * @param k the number of positive (success) outcomes
	 */
	public void compute(long n, long k){
		NormalDistribution normalDist = new NormalDistribution();

		//special cases
		if (k < 0 || n < 0){
			Logging.add("Confidence Interval calculation: only positive values allowed", Logging.HINT);
			return;
		}
		if (k > n){
			Logging.add("Confidence Interval calculation: the number of observations " +
					"must be superior to the number of positives", Logging.HINT);
			return;
		}
		if (n == 0)
			return;

		//building formula components
		try {
			//percentile of the standard normal distribution (based on alpha)
			double zcrit = -1.0 * normalDist.inverseCumulativeProbability(alpha/2);
			//the squared percentile
			double z2 = zcrit * zcrit;
			//proportion of successes
			double p = k/(double)n;

			//divide formula into three pieces
			double a = p + z2/2/n;
			double b = zcrit * Math.sqrt((p * (1 - p) + z2/4/n)/n);
			double c = (1 + z2/n);

			//compute the lower and upper limits
			lower = (a - b) / c;
			upper = (a + b) / c;

			//COMMENTED OUT BECAUSE IT IS NOT DONE IN SAS
			//corrections for k close to n (estimate improvement)
//			if (k == 1)
//				lower = -Math.log(1 - alpha)/n;
//			if (k == (n - 1))
//				upper = 1 + Math.log(1 - alpha)/n;

		}catch (Exception e) {
			Logging.outputStackTrace(e);
			return;
		}
	}

	//GETTERS
	public double getLowerLimit() {
		return lower;
	}

	public double getUpperLimit() {
		return upper;
	}


	//MAIN FOR TESTING
	public static void main(String[] args){

		WilsonCI wilson = new WilsonCI(0.05,95,5);

		//expected results
		//lower: 0.022688
		//upper: 0.117349

		if (wilson != null){
			System.out.println("lower CI limit: "+wilson.getLowerLimit());
			System.out.println("upper CI limit: "+wilson.getUpperLimit());
		}
	}

}