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
 * Author: Peter Rijnbeek (PR) - department of Medical Informatics						  *
 * 																						  *
 * $Rev:: 3961              $:  Revision of last commit                                   *
 * $Author:: root           $:  Author of last commit                                     *
 * $Date:: 2013-05#			$:  Date and time (CET) of last commit						  *
 ******************************************************************************************/
package org.erasmusmc.jerboa.utilities;

import java.security.InvalidParameterException;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.erasmusmc.jerboa.config.Parameters;
import org.erasmusmc.jerboa.utilities.stats.formulas.WilsonCI;

/**
 * The item class is used to hold all data for an item in an output table, e.g., priorATC counts
 * It parses items in a list in the script, for example:
 * comorbidities = MI;MI;Myocardial Infactions;-365;20
 *
 * This will be parsed to:
 * lookup = MI (code or multiple codes)
 * label = MI (can be chosen for the output by useLabel)
 * description = Myocardial Infarction (default used for table output)
 * parameters = [-365,20]
 *
 * @author PR
 *
 */
@SuppressWarnings("unused")

public class Item implements Comparable<Item>{
	private List<String> lookup = new ArrayList<String>();
	private String description;
	private String label;
	private List<String> parameters = new ArrayList<String>();		//List of parameters of this item, for example time window
	private String value ="";

	private long total;
	private long count;
	private double mean;
	private double median;
	private double sd = 0;
	private double ciLow;
	private double ciHigh;
	private double ciMeanLow;
	private double ciMeanHigh;
	private double P25;
	private double P75;
	private double min;
	private double max;

	private boolean useLabel = false;

	public Item(){

	}

//	public String toString(){
//		return lookup.toString() +";"+ description +";"+ value +";" + count;
//	}

	/**
	 * Return a formatted String with statistics.
	 * If the value is "continuous" mean, median, sd, min, max is returned
	 * otherwise (n,%)
	 */
	public String toString(){
		String tail = "";
		if (value.equals("CONTINUOUS")){
			tail = " \t" + StringUtilities.format(count) + "\t" +
					StringUtilities.format(mean) + "\t" +
					StringUtilities.format(sd)+ "\t" +
					StringUtilities.format(getCiMeanLow())+ "\t" +
					StringUtilities.format(getCiMeanHigh())+ "\t" +
					StringUtilities.format(median) + "\t" +
					StringUtilities.format(P25) + "\t" +
					StringUtilities.format(P75) + "\t" +
					StringUtilities.format(min) + "\t" +
					StringUtilities.format(max);
			return getHead() + tail;

		} else
			tail = "\t" + count + " (" + StringUtilities.format(this.getPercentage()) + ")\t" +
					StringUtilities.format(this.getCiLow()*100) + "-"+StringUtilities.format(this.getCiHigh()*100)+"";

		if (value.isEmpty())
			return getHead() + tail;
		else
			return getHead() + " " + value + tail;

	}
	public String toString(DecimalFormat precisionFormat){
		String tail = "";
		if (value.equals("CONTINUOUS")){
			tail = " \t" + precisionFormat.format(count) + "\t" +
					precisionFormat.format(mean) + "\t" +
					precisionFormat.format(sd)+ "\t" +
					precisionFormat.format(getCiMeanLow())+ "\t" +
					precisionFormat.format(getCiMeanHigh())+ "\t" +
					precisionFormat.format(median) + "\t" +
					precisionFormat.format(P25) + "\t" +
					precisionFormat.format(P75) + "\t" +
					precisionFormat.format(min) + "\t" +
					precisionFormat.format(max);
			return getHead() + tail;

		} else
			tail = "\t" + count + " (" + precisionFormat.format(this.getPercentage()) + ")\t" +
					precisionFormat.format(this.getCiLow()*100) + "-"+precisionFormat.format(this.getCiHigh()*100)+"";

		if (value.isEmpty())
			return getHead() + tail;
		else
			return getHead() + " " + value + tail;

	}

	private String getHead(){
		if (useLabel) {
				return label;
			} else
				return description;
	}

	public void calculateCI(){
		WilsonCI wilson = new WilsonCI(0.05,(int) this.total, this.count);
		this.ciHigh = wilson.getUpperLimit();
		this.ciLow = wilson.getLowerLimit();
	}

	public void calculateCIMean(){
		this.ciMeanHigh = mean + 1.96 * getSD()/Math.sqrt(count);
		this.ciMeanLow =  mean - 1.96 * getSD()/Math.sqrt(count);
	}

	/**
	 * compareTo uses either the label or description to sort
	 */
	@Override public int compareTo(Item item) {

		if (useLabel){
			String string1 = this.getLabel() + this.getValue();
			String string2 = item.getLabel() + item.getValue();
			return string1.compareTo(string2);
		}

		String string1 = this.getDescription() + this.getValue();
		String string2 = item.getDescription() + item.getValue();
		return string1.compareTo(string2);
	}



	//GETTERS AND SETTERS FOR STATS
	public void setLookup(List<String> lookup){
		this.lookup = lookup;
	}
	public void addLookup(String lookup){
		String[] lookupSplit = lookup.split(",");
		this.lookup = Arrays.asList(lookupSplit);
	}

	public void setCount(long count){
		this.count = count;
	}

	public void setTotal(Long total){
		this.total = total;
	}

	public void setUseLabel(boolean useLabel){
		this.useLabel = useLabel;
	}

	public void setLabel(String label){
		this.label = label;
	}

	public void setDescription(String description){
		this.description = description;
	}

	public String getLabel(){
		return label;
	}

	public List<String> getLookup(){
		return this.lookup;
	}

	public String getDescription(){
		return this.description;
	}

	public long getCount(){
		return this.count;
	}

	public double getPercentage(){
		return (double) count *100 / (double) total ;
	}

	public double getMean() {
		return mean;
	}
	public void setMean(double mean) {
		this.mean = mean;
	}
	public double getSD() {
		return sd;
	}
	public void setSD(double sd) {
		this.sd = sd;
	}

	public String getValue() {
		return value;
	}
	public void setValue(String value) {
		this.value = value;
	}

	public Double getMax() {
		return max;
	}

	public void setMax(Double max) {
		this.max = max;
	}

	public Double getMin() {
		return min;
	}

	public void setMin(Double min) {
		this.min = min;
	}

	public Double getP25() {
		return P25;
	}

	public void setP25(Double p25) {
		this.P25 = p25;
	}

	public Double getP75() {
		return P75;
	}

	public void setP75(Double p75) {
		this.P75 = p75;
	}

	public List<String> getParameters() {
		return parameters;
	}

	public void setParameters(List<String> parameters) {
		this.parameters = new ArrayList<String>(parameters);
	}

	public void addParameter(String parameters) {
		this.parameters.add(parameters);
	}

	public double getMedian() {
		return median;
	}

	public void setMedian(double median) {
		this.median = median;
	}

	public long getTotal() {
		return total;
	}

	public void setTotal(long total) {
		this.total = total;
	}

	public double getSd() {
		return sd;
	}

	public void setSd(double sd) {
		this.sd = sd;
	}

	public double getCiLow() {
		calculateCI();
		return ciLow;
	}

	public double getCiMeanLow() {
		calculateCIMean();
		return ciMeanLow;
	}

	public double getCiMeanHigh() {
		calculateCIMean();
		return ciMeanHigh;
	}

	public void setCiLow(double ciLow) {
		this.ciLow = ciLow;
	}

	public double getCiHigh() {
		calculateCI();
		return ciHigh;
	}

	public void setCiHigh(double ciHigh) {
		this.ciHigh = ciHigh;
	}

	public void setMin(double min) {
		this.min = min;
	}

	public void setMax(double max) {
		this.max = max;
	}


}