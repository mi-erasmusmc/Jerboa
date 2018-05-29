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
 * $Rev:: 4839              $:  Revision of last commit                                   *
 * $Author:: root           $:  Author of last commit                                     *
 * $Date:: 2013-12#$:  Date and time (CET) of last commit								  *
 ******************************************************************************************/
package org.erasmusmc.jerboa.modules;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

import org.apache.commons.collections.bag.HashBag;
import org.apache.commons.lang3.text.StrBuilder;
import org.erasmusmc.jerboa.dataClasses.Patient;
import org.erasmusmc.jerboa.engine.Module;
import org.erasmusmc.jerboa.gui.graphs.BarPlotDS;
import org.erasmusmc.jerboa.gui.graphs.BarPlotTS;
import org.erasmusmc.jerboa.gui.graphs.Graphs;
import org.erasmusmc.jerboa.gui.graphs.LinePlotDS;
import org.erasmusmc.jerboa.gui.graphs.Plot;
import org.erasmusmc.jerboa.config.DataDefinition;
import org.erasmusmc.jerboa.utilities.DateUtilities;
import org.erasmusmc.jerboa.utilities.ExtendedMultiKey;
import org.erasmusmc.jerboa.utilities.FileUtilities;
import org.erasmusmc.jerboa.utilities.Logging;
import org.erasmusmc.jerboa.utilities.MultiKeyBag;
import org.erasmusmc.jerboa.utilities.Progress;
import org.erasmusmc.jerboa.utilities.StringUtilities;
import org.erasmusmc.jerboa.utilities.Wildcard;
import org.erasmusmc.jerboa.utilities.stats.HistogramStats;
import org.erasmusmc.jerboa.utilities.Timer;

/**
 * This module aggregates the patient population data and calculates descriptive statistics.
 * The primary data extraction statistics are exported to a text file and are also shown
 * in graphical format in the GUI.
 * @author MG {@literal &} PR
 *
 */
public class DataProfiler extends Module{

	/**
	 * The size of the groups in years for the calculation of the statistics
	 */
	public int groupSize; //years

	//progress related
	private Timer timer = new Timer();
	private Progress progress = new Progress();

	//bags for extra graphs not used for statistics
	private MultiKeyBag startDatesBag;
	private MultiKeyBag endDatesBag;

	//Bags for statistics
	//Name equals the key order (index 0 = gender for all bags)
	private MultiKeyBag yearActivePatientsBag;
	private MultiKeyBag yearBirthDatesBag;
	private MultiKeyBag yearsPatientTimeBag;
	private MultiKeyBag yearAgeGroupAtStartAgeBag;
	private MultiKeyBag yearAgeGroupAtEndAgeBag;
	private MultiKeyBag yearAgeGroupAtStartOfYearBag;
	private MultiKeyBag yearAgeGroupAtStartPatientTimeBag;
	private MultiKeyBag beforeYearAgeGroupPatientTimeBag;
	private MultiKeyBag inYearAgeGroupPatientTimeBag;
	private MultiKeyBag afterYearAgeGroupPatientTimeBag;

	//output
	private StrBuilder out;

	@Override
	public void setNeededFiles() {/*NOTHING TO ADD. PATIENTS FILE SHOULD BE INPUT BY DEFAULT */ }

	@Override
	public void setNeededExtendedColumns() {/* NOTHING TO ADD */ }

	@Override
	public void setNeededNumericColumns() {
		// TODO Auto-generated method stub

	}

	@Override
	public boolean init(){

		boolean initOK = true;
		//output
		out = new StrBuilder();

		//add header to file
		this.outputFileName = this.outputFileName.replace(".csv", ".txt");
		initOK = FileUtilities.writeLineToFile(this.outputFileName,
				"Var\tName\tValue\tName\tValue\tGender\tMin\tMax\tCount\tMean\tperc25\tMedian\tperc75\tSD", false);

		//bags for graphs only
		startDatesBag = new MultiKeyBag(); 					    // gender, start date
		endDatesBag = new MultiKeyBag(); 						// gender, end date

		//age bags
		yearAgeGroupAtStartAgeBag = new MultiKeyBag(); 		    // gender, year, age group at start, age
		yearAgeGroupAtEndAgeBag = new MultiKeyBag(); 			// gender, year, age group at end, age
		yearAgeGroupAtStartOfYearBag = new MultiKeyBag();		// gender, year, age group at start of year, age

		//patient time bags
		yearAgeGroupAtStartPatientTimeBag = new MultiKeyBag();	// gender, year, age group at start, patient time
		beforeYearAgeGroupPatientTimeBag = new MultiKeyBag();	// gender, before year, age group at start of year, patient time
		inYearAgeGroupPatientTimeBag = new MultiKeyBag();		// gender, in year, age group at start of year, patient time
		afterYearAgeGroupPatientTimeBag = new MultiKeyBag();	// gender, after year, age group at start of year, patient time

		//other bags
		yearActivePatientsBag = new MultiKeyBag(); 				// gender, year, active patients
		yearBirthDatesBag = new MultiKeyBag(); 				    // gender, year, birth date
		yearsPatientTimeBag = new MultiKeyBag(); 				// gender, number of years of patient time

		return initOK;

	}

	@Override
	public Patient process(Patient p){

		//not used for the Primary Data Extraction only for graphs
		startDatesBag.add(new ExtendedMultiKey(p.gender, p.startDate));
		endDatesBag.add(new ExtendedMultiKey(p.gender, p.endDate));

		yearBirthDatesBag.add(new ExtendedMultiKey(p.gender, p.getBirthYear(), p.birthDate));
		yearsPatientTimeBag.add(new ExtendedMultiKey(p.gender,p.getPatientTimeInYears()));
		yearAgeGroupAtStartAgeBag.add(new ExtendedMultiKey(p.gender, p.getStartYear(),
				getAgegroup(p.getAgeAtStartDate()/DateUtilities.daysPerYear,this.groupSize),
				(int)(p.getAgeAtStartDate()/DateUtilities.daysPerMonth)));
		yearAgeGroupAtEndAgeBag.add(new ExtendedMultiKey(p.gender, p.getEndYear(),
				getAgegroup(p.getAgeAtEndDate()/DateUtilities.daysPerYear,this.groupSize),
				(int)(p.getAgeAtEndDate()/DateUtilities.daysPerMonth)));
		yearAgeGroupAtStartPatientTimeBag.add(new ExtendedMultiKey(p.gender,  p.getStartYear(),
				getAgegroup(p.getAgeAtStartDate()/DateUtilities.daysPerYear,this.groupSize),
				(int)(p.getPatientTime()/DateUtilities.daysPerMonth)));
		addYearAgeGroupAgeAtStartOfYear(p);
		addYearAgeGroupPatientTime(p);

		return p;
	}

	//***********************************AGGREGATION METHODS*******************************//

	/**
	 * Adds a patient to the bag that contains the age at the start of each calendar year.
	 * @param patient - a patient to be added to the bag.
	 */
	private void addYearAgeGroupAgeAtStartOfYear(Patient patient){
		int yearStart = patient.getStartYear();
		int yearEnd = patient.getEndYear();
		int ageAtStartOfYear = 0;
		String agegroup = new String();
		for (int i = yearStart; i<= yearEnd; i++){
			agegroup = getAgegroup(DateUtilities.getYearsFromDays(patient.getAgeInBeginningOfYear(i)),this.groupSize);
			if(patient.isActive(DateUtilities.dateToDays(i+"0101",DateUtilities.DATE_ON_YYYYMMDD)) &&
					(ageAtStartOfYear = patient.getAgeInBeginningOfYear(i)) != -1)
				yearAgeGroupAtStartOfYearBag.add(new ExtendedMultiKey(patient.gender, i,agegroup,
						(int)(ageAtStartOfYear/DateUtilities.daysPerMonth)));
		}
	}

	/**
	 * Adds the amount of patient time before, in, and after a year if the patient is active.
	 * Additionally the number of active patients is counted per year
	 * The age group is determined at the beginning of the year.
	 * @param patient - patient object
	 */
	private void addYearAgeGroupPatientTime(Patient patient){

		int yearStart = patient.getStartYear();
		int yearEnd = patient.getEndYear();
		String ageGroupBeginningOfYear = new String();
		int patientTime = 0;
		for (int i = yearStart; i<= yearEnd; i++){
			int ageInDays = patient.getAgeInBeginningOfYear(i);
			if (ageInDays>-1){
				ageGroupBeginningOfYear = getAgegroup(DateUtilities.getYearsFromDays(ageInDays),this.groupSize);
			} else {
				ageGroupBeginningOfYear = getAgegroup(0,this.groupSize);
			}
			patientTime = patient.getPatientTimeBeforeStartOfYear(i);
			if (patientTime>0) {
				beforeYearAgeGroupPatientTimeBag.add(new ExtendedMultiKey(patient.gender, i, ageGroupBeginningOfYear,
						 (int)Math.round(patientTime/DateUtilities.daysPerMonth)));
			}

			//add active patients
			patientTime = patient.getPatientTimeInYear(i);
			if (patientTime>0) {
				//add to active patients
				yearActivePatientsBag.add(new ExtendedMultiKey(patient.gender,i));
			}

			//After year
			patientTime = patient.getPatientTimeAfterStartOfYear(i);
			if (patientTime>0) {
				afterYearAgeGroupPatientTimeBag.add(new ExtendedMultiKey(patient.gender, i, ageGroupBeginningOfYear,
						(int)Math.round(patientTime/DateUtilities.daysPerMonth)));
			}

			//In a year
			String ageGroupEndOfYear = getAgegroup(DateUtilities.getYearsFromDays(patient.getAgeAtDate(
					DateUtilities.dateToDays(i+"1231",DateUtilities.DATE_ON_YYYYMMDD))), this.groupSize);
			if (!ageGroupBeginningOfYear.equals(ageGroupEndOfYear)){

				int birthday = patient.getBirthdayInYear(i);

				//get patient time from 1st of January this year until the birthday
				int timeUntilBirthDay = patient.getPatientTimeInYearBeforeBirthday(i, birthday);
				if (timeUntilBirthDay > 0)
					inYearAgeGroupPatientTimeBag.add(new ExtendedMultiKey(patient.gender, i, ageGroupBeginningOfYear,
						(int)Math.round(timeUntilBirthDay/DateUtilities.daysPerMonth)));

				//get patient time from birthday until 31st of December this year
				//Note! the birthday is added in the second age group as the patient becomes older on the celebration day
				int timeFromBirthDay = patient.getPatientTimeInYearAfterBirthday(i, birthday);
				if (timeFromBirthDay > 0)
					inYearAgeGroupPatientTimeBag.add(new ExtendedMultiKey(patient.gender, i, ageGroupEndOfYear,
							(int)Math.round(timeFromBirthDay/DateUtilities.daysPerMonth)));

			//same age group during the whole year
			}else{
				patientTime = patient.getPatientTimeInYear(i);
				if (patientTime > 0)
					inYearAgeGroupPatientTimeBag.add(new ExtendedMultiKey(patient.gender, i, ageGroupBeginningOfYear,
						(int)Math.round(patientTime/DateUtilities.daysPerMonth)));
			}
		}
	}

	//************************************GRAPH GENERATION******************************************//

	@Override
	public void displayGraphs(){

		Timer timer = new Timer();
		Progress progress = new Progress();

		timer.start();
		progress.init(14, "Creating population characteristics plots");
		Logging.add("Creating population graphs..", Logging.HINT);

		//birth dates
		Plot plot = new BarPlotTS.Builder("Birth dates")
		.data(aggregatePerMonth(yearBirthDatesBag, new ExtendedMultiKey(Wildcard.BYTE(),
				Wildcard.INTEGER(), Wildcard.INTEGER())))
				.XLabel("Calendar year")
				.YLabel("Number of patients")
				.showLegend(false).build();
		Graphs.addPlot(this.title, "Birth date", plot); progress.update();

		//start dates
		plot = new BarPlotTS.Builder("Date patients enter")
			.data(aggregatePerMonth(startDatesBag, new ExtendedMultiKey(Wildcard.BYTE(), Wildcard.INTEGER())))
			.XLabel("Calendar year")
			.YLabel("Number of patients")
			.showLegend(false).build();
		Graphs.addPlot(this.title, "Start dates", plot); progress.update();

		//end dates
		plot = new BarPlotTS.Builder("Date patients exit")
			.data(aggregatePerMonth(endDatesBag, new ExtendedMultiKey(Wildcard.BYTE(), Wildcard.INTEGER())))
			.XLabel("Calendar year")
			.YLabel("Number of patients")
			.showLegend(false).build();
		Graphs.addPlot(this.title, "End dates", plot); progress.update();

		//active patients
		plot = new BarPlotDS.Builder("Patients with at least one day of patient time")
			.data(yearActivePatientsBag.getHistogram(new ExtendedMultiKey(Wildcard.BYTE(), Wildcard.INTEGER())))
			.XLabel("Calendar year")
			.YLabel("Number of patients")
			.showLegend(false).build();
		Graphs.addPlot(this.title, "Active patients", plot); progress.update();

		//age at start per years
		Graphs.addPlots(this.title,"Age at start of year",
				getPlotListPerYear(yearAgeGroupAtStartOfYearBag,
						"Age at start of year" , "Age (years)", "Number of patients")); progress.update();

		//age at start per year percentiles
		Graphs.addPlots(this.title, "Age at start of year percentiles",
				getPercentilePlots(yearAgeGroupAtStartOfYearBag, "Age at start of year", "Calendar year", "Age (years)")); progress.update();

		//age at start per year
		Graphs.addPlots(this.title,"Age at start per year",
				getPlotListPerYear(yearAgeGroupAtStartAgeBag,
						"Age of patients entering in year" , "Age (years)", "Number of patients")); progress.update();

		//age at start per year percentiles
		Graphs.addPlots(this.title, "Age at start percentiles",
				getPercentilePlots(yearAgeGroupAtStartAgeBag, "Patients entering", "Calendar year", "Age (years)")); progress.update();

		//age at end
		Graphs.addPlots(this.title,"Age at end per year",
				getPlotListPerYear(yearAgeGroupAtEndAgeBag,
						"Age of patients exiting in year" , "Age (years)", "Number of patients")); progress.update();

		//age at end per year percentiles
		Graphs.addPlots(this.title, "Age at end percentiles",
				getPercentilePlots(yearAgeGroupAtEndAgeBag, "Patients exiting", "Calendar year", "Age (years)")); progress.update();

		//total patient time in a year
		Graphs.addPlots(this.title, "Total patient time in a year",
				getPatientTimeInAYearPlots(inYearAgeGroupPatientTimeBag, "Total patient time in a year", "Calendar year", "Number of years")); progress.update();

		//patient time histogram
		plot = new BarPlotDS.Builder("Patient time histogram")
			.data(convertFromMonthsToYears(yearAgeGroupAtStartPatientTimeBag.getHistogram(new ExtendedMultiKey(Wildcard.BYTE(), Wildcard.INTEGER()))))
			.XLabel("Patient time (years)")
			.YLabel("Number of patients")
			.showLegend(false).build();
		Graphs.addPlot(this.title, "Patient time", plot); progress.update();

		//patient time before a year - percentiles
		Graphs.addPlots(this.title, "Patient time before a year percentiles",
				getPercentilePlots(beforeYearAgeGroupPatientTimeBag,
						"Patient time before a year per patient", "Calendar year", "Number of years")); progress.update();
		//patient time after a year - percentiles
		Graphs.addPlots(this.title, "Patient time after a year percentiles",
				getPercentilePlots(afterYearAgeGroupPatientTimeBag, "Patient time after a year per patient",
						"Calendar year", "Number of years")); progress.update();

		//make sure the progress bar is closed
		progress.close();
		progress = null;

		timer.stopAndDisplay("Graphs created in:");
	}


	//*********************************** METHODS FOR PREPARING THE DATA FOR PLOTS **************************************//

	/**
	 * Creates a list of line plots representing the data distribution
	 * for the total population and per gender.
	 * @param bag - the bag containing the data
	 * @param title - the title of the plot
	 * @param XLabel - the X axis label
	 * @param YLabel - the Y axis label
	 * @return - the list of generated plots
	 */
	private List<Plot> getPercentilePlots(MultiKeyBag bag, String title, String XLabel, String YLabel){
		List<Plot> list = new ArrayList<Plot>();
		if (bag != null){
			list.add(createPercentilePlot(bag, DataDefinition.INVALID_GENDER, title + " - Total population", XLabel, YLabel));
			list.add(createPercentilePlot(bag, DataDefinition.FEMALE_GENDER, title + " - Female population", XLabel, YLabel));
			list.add(createPercentilePlot(bag, DataDefinition.MALE_GENDER, title + " - Male population", XLabel, YLabel));
		}

		return list;
	}

	/**
	 * Creates a line plot with different series for the first,
	 * second, third quartile and the median of the data.
	 * @param bag - the bag containing the data
	 * @param gender - the gender of the patients of interest
	 * @param title - the title of the plot
	 * @param XLabel - the X axis label
	 * @param YLabel - the Y axis label
	 * @return - the distribution plot
	 */
	private Plot createPercentilePlot(MultiKeyBag bag, byte gender, String title, String XLabel, String YLabel){
		TreeMap<Object, Object> statsMap = getStatsMap(bag, gender);
		Plot plot = new LinePlotDS.Builder(title).XLabel(XLabel).YLabel(YLabel).showLegend(true).build();
		plot.addSeriesToDataset(getPercentile(statsMap, 25), "1st quartile");
		plot.addSeriesToDataset(getPercentile(statsMap, 50), "Median");
		plot.addSeriesToDataset(getPercentile(statsMap, 999), "Mean");
		plot.addSeriesToDataset(getPercentile(statsMap, 75), "3rd quartile");

		return plot;
	}


	/**
	 * Creates a list of bar plots representing the total patient time
	 * in a certain calendar year for the total population and per gender.
	 * @param bag - the bag containing the data
	 * @param title - the title of the plot
	 * @param XLabel - the X axis label
	 * @param YLabel - the Y axis label
	 * @return - the list of generated plots
	 */
	private List<Plot> getPatientTimeInAYearPlots(MultiKeyBag bag, String title, String XLabel, String YLabel){
		List<Plot> list = new ArrayList<Plot>();
			Plot plot = new BarPlotDS.Builder(title + " - Total population").XLabel(XLabel).YLabel(YLabel)
					.data(getPatientTimeInAYear(bag, DataDefinition.INVALID_GENDER)).build();list.add(plot);
			plot = new BarPlotDS.Builder(title + " - Female population").XLabel(XLabel).YLabel(YLabel)
					.data(getPatientTimeInAYear(bag, DataDefinition.FEMALE_GENDER)).build();list.add(plot);
			plot = new BarPlotDS.Builder(title + " - Male population").XLabel(XLabel).YLabel(YLabel)
							.data(getPatientTimeInAYear(bag, DataDefinition.MALE_GENDER)).build();list.add(plot);

		return list;
	}

	/**
	 * Creates a map representation of the patient time in a calendar year
	 * for the population of a specified gender.
	 * @param bag - the data
	 * @param gender - the gender of interest
	 * @return - a map with years as keys and patient time as values
	 */
	private HashMap<Object, Object> getPatientTimeInAYear(MultiKeyBag bag, byte gender){
		HashMap<Object, Object> data = new HashMap<Object, Object>();
		if (bag != null){
			TreeMap<Object, Object> stats = getStatsMap(bag, gender);
			for (Object year : stats.keySet())
				data.put((int)year, ((HistogramStats)stats.get(year)).getSum()/DateUtilities.monthsPerYear);
		}

		return data;
	}

	/**
	 * Retrieves the value of a certain percentile from a HistogramStats object
	 * and replaces it in the map containing HistogramStats object.
	 * @param stats - the map of HistogramStats objects
	 * @param percentile - the percentile of interest
	 * @return - a sorted map of percentile values per calendar year
	 */
	private TreeMap<Object, Object> getPercentile(TreeMap<Object, Object> stats, int percentile){
		TreeMap<Object, Object> map = new TreeMap<Object, Object>();
		for (Object year : stats.keySet()){
			double value = 	(percentile == 999 ? ((HistogramStats)stats.get(year)).getMean()
						: ((HistogramStats)stats.get(year)).getPercentile(percentile))/DateUtilities.monthsPerYear;
				map.put((int)year, value);
		}

		return map;
	}

	/**
	 * Creates a map of HistogramStats objects per calendar year from the bag per year.
	 * @param bag - the data
	 * @param gender - the gender for which the statistics are to be computed
	 * @return - a sorted list (by year) of summary statistics
	 */
	private TreeMap<Object, Object> getStatsMap(MultiKeyBag bag, byte gender){
		TreeMap<Object, Object> stats = new TreeMap<Object, Object>();
		TreeSet<ExtendedMultiKey> keySet = bag.getSortedKeySet();
		if (keySet != null && keySet.size() > 0){
			Set<Object> years = bag.getKeyValuesAsObject(1);
			for (Object year : years){
				HistogramStats hs = new HistogramStats(bag.getHistogramRec
							(new ExtendedMultiKey((gender != -1 ? Byte.valueOf(gender) : Wildcard.BYTE()),
									(int)year, Wildcard.STRING())));
				stats.put((int)year, hs);
			}
		}

		return stats;
	}

	/**
	 * Adds a list of plots (one per each calendar year) to the main plot list.
	 * Note that it assumes the year is the second component in the multi key.
	 * @param bag - the multi key bag containing the data to be plotted
	 * @param title - the title of the plot
	 * @param XLabel - the label of the X axis
	 * @param YLabel - the label of the Y axis
	 * @return - a list containing plots for each calendar year found in the multi key bag
	 */
	private List<Plot> getPlotListPerYear(MultiKeyBag bag, String title, String XLabel, String YLabel){
		List<Plot> list = null;
		if (bag != null){
			//calculate min and max range for X axis
			TreeSet<Object> ages = bag.getKeyValuesAsObject(3);
			double minRange = (int)ages.first()/DateUtilities.monthsPerYear;
			double maxRange = (int)ages.last()/DateUtilities.monthsPerYear;
			//create plot per year
			Set<Object> set = bag.getKeyValuesAsObject(1);
			if (set != null && set.size() >0){
				list = new ArrayList<Plot>();
				for (Object year : set){
					Plot plot = new BarPlotDS.Builder(title+" "+year)
					.minRange(minRange)
					.maxRange(maxRange)
					.data(convertFromMonthsToYears(bag.getHistogram(new ExtendedMultiKey(Wildcard.BYTE(),(int)year))))
					.XLabel(XLabel)
					.YLabel(YLabel)
					.build();
					list.add(plot);
				}
			}else{
				Logging.add("Column YEAR not found in bag "+title, Logging.HINT);
			}
		}

		return list;
	}


	/**
	 * Uses the information stored as number of days in the last column of a bag
	 * to express the results in number of months.
	 * @param bag - the bag containing the information to be aggregated
	 * @param multiKey - the multikey corresponding to the subset of data of interest
	 * @return - a HashBag representation of the information aggregated per month
	 */
	private HashBag aggregatePerMonth(MultiKeyBag bag, ExtendedMultiKey multiKey){

		//sort info to be aggregated
		HashBag histo = bag.getHistogram(multiKey);
		Object[] keySet = histo.uniqueSet().toArray();
		Arrays.sort(keySet);

		HashBag data = new HashBag();
		int peak = 0;
		int[] reference = DateUtilities.daysToDateComponents((int)keySet[0]);
		boolean addToBag = false;
		for (Object key : keySet){
			int[] date = DateUtilities.daysToDateComponents((int)key);
			//aggregate
			if ((date[0] == reference[0]) && (date[1] == reference[1]))
				peak += histo.getCount(key);
			else
				addToBag = true;
			//check if the reference date changed or we reached the last value in the key set
			if (addToBag || (int)key == (int)keySet[keySet.length - 1]){
				data.add(reference, peak);
				//refresh peak value
				peak = histo.getCount(key);
			}
			addToBag = false;
			reference = date;
		}

		return data;
	}

	/**
	 * Converts the keys of the hash bag from months into years.
	 * @param data - the hash bag containing the data
	 * @return - a new hash bag with the keys converted from months to years
	 */
	private HashBag convertFromMonthsToYears(HashBag data){
		HashBag newBag = new HashBag();
		for (Object key : data.uniqueSet()){
			int newKey = (int)((int)(key)/DateUtilities.monthsPerYear);
			int count = data.getCount(key);
			if (newBag.contains(newKey)){
				 count += newBag.getCount(newKey);
				 newBag.remove(newKey);
			}
			newBag.add(newKey, count);
		}

		return newBag;
	}

	//***********************************OUTPUT AND FORMATTING*****************************************//
	@Override
	public void outputResults(){

		timer = new Timer();
		progress = new Progress();

		//start counter
		timer.start();
		progress.init(8, "Calculating Statistics");
		progress.show();

		outputBagStats("Age at patient start", yearAgeGroupAtStartAgeBag, false, true, true, true, true, true);
		progress.update(1);
		outputBagStats("Age at patient end", yearAgeGroupAtEndAgeBag, false, true, true, true, true, true);
		progress.update(1);
		outputBagStats("Age at start of year", yearAgeGroupAtStartOfYearBag, false, false, true, false, true, true);
		progress.update(1);

		//observation time
		outputBagStats("Observation time", yearAgeGroupAtStartPatientTimeBag, false, true, false, true, false, false);
		progress.update(1);
		outputBagStats("Observation time before a year", beforeYearAgeGroupPatientTimeBag, false, false, true, false, true, false);
		progress.update(1);
		outputBagStats("Observation time after a year", afterYearAgeGroupPatientTimeBag, false, false, true, false, true, false);
		progress.update(1);
		outputBagStats("Observation time in a year", inYearAgeGroupPatientTimeBag, false, false, true, false, true, false);
		progress.update(1);

		//counts
		outputCountPerYear("Active patients", yearActivePatientsBag,null);
		progress.update(1);
		outputCountPerYear("Birth in year", yearBirthDatesBag, new ExtendedMultiKey(Wildcard.INTEGER(),null));
		progress.update(1);
		outputCountPerYear("Observation time in years", yearsPatientTimeBag,null);
		progress.update(1);

		//make sure the progress bar is closed
		progress.close();
		progress = null;

		//display execution timers
		timer.stop();
		timer.displayTotal("Statistics computed in");

	}

	/**
	 * Adds to the output the statistics of a multi key bag with different aggregation levels passed as parameters.
	 * @param name - the name of the descriptive statistics; what they represent (e.g., variable of interest)
	 * @param bag - the bag containing the data on which statistics are to be performed
	 * @param outputCountsOnly - true if only the count should be in the output not the other statistics
	 * @param outputTotals - true if all the data of contained in @bag should be used for statistics and results output
	 * @param outputPerYear - true if statistics are to be computed per calendar year and output
	 * @param outputPerAgeGroup - true if statistics are to be computed per age groups and output
	 * @param outputPerYearAndAgeGroup - true if statistics are to be computed per calendar year and age groups and output
	 * @param outputCount - true if the statistics should reflect the count of the elements; false if the sum of the elements should be output
	 */
	private void outputBagStats(String name, MultiKeyBag bag, boolean outputCountsOnly, boolean outputTotals,
			boolean outputPerYear, boolean outputPerAgeGroup, boolean outputPerYearAndAgeGroup, boolean outputCount){

		if (!bag.getMasterBag().isEmpty()){

			try{
				//totals
				if (outputTotals)
					outputTotals(name, bag, outputCount, outputCountsOnly);

				//per year
				if (outputPerYear)
					outputPerYear(name, bag, outputCount, outputCountsOnly);

				//per age group
				if (outputPerAgeGroup){
					outputPerAgeGroup(name, bag,outputCount, outputCountsOnly);
				}

				//per year and age group
				if (outputPerYearAndAgeGroup){
					outputPerYearAndAgeGroup(name, bag, outputCount, outputCountsOnly);
				}

			}catch(Exception e){
				Logging.outputStackTrace(e);
				Logging.add("Unable to output data profiler results for "+name, Logging.ERROR);
				//the output is done per bag. if needed, move it to outputSummary() and remove the
				//clearing of the StringBuilder
			}finally{
				flush();
			}
		}
	}

	/**
	 * Adds to output the overall statistics of the bag for the total population
	 * and separately for females and males. Note that it assumes the gender
	 * is the first component of the multi key.
	 * @param name - the name of the totals to be output
	 * @param bag - the multi key bag containing the counter values
	 * @param outputCount - true if the statistics should reflect
	 * @param outputCountsOnly = true if only the count should be added
	 * the count of the elements; false if the sum of the elements should be output
	 */
	private void outputTotals(String name, MultiKeyBag bag, boolean outputCount, boolean outputCountsOnly){
		if (bag != null){
			if (outputCountsOnly) {
				addToOutput(name, null, null, "T",
						countToString(bag.getHistogramStats(new ExtendedMultiKey(Wildcard.BYTE(), Wildcard.INTEGER(), Wildcard.STRING()))));
				addToOutput(name, null, null, "F",
						countToString(bag .getHistogramStats(new ExtendedMultiKey(DataDefinition.FEMALE_GENDER, Wildcard.INTEGER(), Wildcard.STRING()))));
				addToOutput(name, null, null, "M",
						countToString(bag .getHistogramStats(new ExtendedMultiKey(DataDefinition.MALE_GENDER, Wildcard.INTEGER(), Wildcard.STRING()))));
			} else {
				addToOutput(name, null, null, "T",
						statsToString(bag .getHistogramStats(new ExtendedMultiKey(Wildcard.BYTE(), Wildcard.INTEGER(), Wildcard.STRING())),outputCount));
				addToOutput(name, null, null, "F",
						statsToString(bag .getHistogramStats(new ExtendedMultiKey(DataDefinition.FEMALE_GENDER, Wildcard.INTEGER(), Wildcard.STRING())), outputCount));
				addToOutput(name, null, null, "M",
						statsToString(bag .getHistogramStats(new ExtendedMultiKey(DataDefinition.MALE_GENDER, Wildcard.INTEGER(), Wildcard.STRING())), outputCount));
			}
		}
	}

	/**
	 * Adds to output the statistics of the bag aggregated per year for the total population
	 * and separately for females and males. Note that it assumes the gender is the
	 * first component of the multi key and the year is the second.
	 * @param name - the name of the stats per year to be output
	 * @param bag - the multi key bag containing the counter values
	 * @param outputCount - true if the statistics should reflect counts instead if sum
	 * @param outputCountsOnly = true if only the count should be added an no other statistics
	 * the count of the elements; false if the sum of the elements should be output
	 */
	private void outputPerYear(String name, MultiKeyBag bag, boolean outputCount, boolean outputCountsOnly){
		if (bag != null){
			TreeSet<Object> years = bag.getKeyValuesAsObject(1);
			for (Object year : years){
				if (outputCountsOnly) {
					addToOutput(name, "YEAR", year.toString(), null, null, "T",
							countToString(bag.getHistogramStats(new ExtendedMultiKey(Wildcard.BYTE(),year, Wildcard.STRING()))));
					addToOutput(name, "YEAR", year.toString(), null, null, "F",
							countToString(bag .getHistogramStats(new ExtendedMultiKey(DataDefinition.FEMALE_GENDER,year, Wildcard.STRING()))));
					addToOutput(name, "YEAR", year.toString(), null, null, "M",
							countToString(bag .getHistogramStats(new ExtendedMultiKey(DataDefinition.MALE_GENDER,year, Wildcard.STRING()))));
				} else {
					addToOutput(name, "YEAR", year.toString(), null, null, "T",
							statsToString(bag.getHistogramStats(new ExtendedMultiKey(Wildcard.BYTE(), year, Wildcard.STRING())), outputCount));
					addToOutput(name, "YEAR", year.toString(), null, null, "F",
							statsToString(bag.getHistogramStats(new ExtendedMultiKey(DataDefinition.FEMALE_GENDER, year, Wildcard.STRING())), outputCount));
					addToOutput(name, "YEAR", year.toString(), null, null, "M",
							statsToString(bag.getHistogramStats(new ExtendedMultiKey(DataDefinition.MALE_GENDER, year, Wildcard.STRING())), outputCount));
				}
			}
		}
	}

	/**
	 * Adds to output the counts of the bag aggregated per year for the total population
	 * and separately for females and males. Note that it assumes the gender is the
	 * first component of the multi key and the year is the second.
	 * @param name - the name of the stats per year to be output
	 * @param bag - the multi key bag containing the counter values
	 * @param postKey - the key to be added
	 * the count of the elements; false if the sum of the elements should be output
	 */
	private void outputCountPerYear(String name, MultiKeyBag bag, ExtendedMultiKey postKey){

		ExtendedMultiKey fullKeyT = null;
		ExtendedMultiKey fullKeyM = null;
		ExtendedMultiKey fullKeyF = null;

		if (bag != null){
			TreeSet<Object> years = bag.getKeyValuesAsObject(1);
			for (Object year : years){
				fullKeyT = new ExtendedMultiKey(Wildcard.BYTE(),year);
				fullKeyM = new ExtendedMultiKey(DataDefinition.MALE_GENDER,year);
				fullKeyF = new ExtendedMultiKey(DataDefinition.FEMALE_GENDER,year);
				//add the postKey to the default gender,year keys if necessary
				if (postKey != null) {
					for (int i=0;i < postKey.size(); i++){
						if (postKey.getKey(i)!=null){
							fullKeyT = fullKeyT.add(postKey.getKey(i));
							fullKeyM = fullKeyM.add(postKey.getKey(i));
							fullKeyF = fullKeyF.add(postKey.getKey(i));
						}
					}
				}
				addToOutput(name, "YEAR", year.toString(), null, null, "T","\t\t"+StringUtilities.format(bag.getCount(fullKeyT)) +"\t\t\t\t\t\t");
				addToOutput(name, "YEAR", year.toString(), null, null, "F","\t\t"+StringUtilities.format(bag.getCount(fullKeyF)) +"\t\t\t\t\t\t");
				addToOutput(name, "YEAR", year.toString(), null, null, "M","\t\t"+StringUtilities.format(bag.getCount(fullKeyM)) +"\t\t\t\t\t\t");
			}
			flush();
		}
	}

	/**
	 * Output the statistics of the bag per year and age group.
	 * @param name - long name of the variable
	 * @param bag - the bag with age groups
	 * @param outputCount - output counts (True) of sums (False)
	 * @param outputCountsOnly - true if wanted to output only counts and not sums
	 * @precondition - Age group should be in position 2 of the bag (To be changed!)
	 */
	private void outputPerYearAndAgeGroup(String name, MultiKeyBag bag,  boolean outputCount, boolean outputCountsOnly){
		if (bag != null){
			//get the all unique key objects
			TreeSet<Object> years = bag.getKeyValuesAsObject(1);
			TreeSet<Object> agegroups = bag.getKeyValuesAsObject(2);
			for (Object year: years){
				for (Object agegroup: agegroups){
					if (outputCountsOnly) {
						addToOutput(name, "YEAR", year.toString(), "AGE", agegroup.toString(), "T",
								countToString(bag.getHistogramStats(new ExtendedMultiKey(Wildcard.BYTE(),year,agegroup))));
						addToOutput(name, "YEAR", year.toString(), "AGE", agegroup.toString(),"F",
								countToString(bag.getHistogramStats(new ExtendedMultiKey(DataDefinition.FEMALE_GENDER,year,agegroup))));
						addToOutput(name, "YEAR", year.toString(), "AGE", agegroup.toString(),"M",
								countToString(bag.getHistogramStats(new ExtendedMultiKey(DataDefinition.MALE_GENDER,year,agegroup))));
					} else {
						addToOutput(name, "YEAR", year.toString(), "AGE", agegroup.toString(), "T",
								statsToString(bag.getHistogramStats(new ExtendedMultiKey(Wildcard.BYTE(),year,agegroup)), outputCount));
						addToOutput(name, "YEAR", year.toString(), "AGE", agegroup.toString(),"F",
								statsToString(bag.getHistogramStats(new ExtendedMultiKey(DataDefinition.FEMALE_GENDER,year,agegroup)), outputCount));
						addToOutput(name, "YEAR", year.toString(), "AGE", agegroup.toString(),"M",
								statsToString(bag.getHistogramStats(new ExtendedMultiKey(DataDefinition.MALE_GENDER,year,agegroup)), outputCount));
					}
				}
			}
		}
	}

	/**
	 * Output the statistics of the bag per age group.
	 * @param name - long name of the variable
	 * @param bag - the bag with age groups
	 * @param outputCount - output counts (True) of sums (False)
	 * @param outputCountsOnly - true if wanted to output only counts and not sums
	 * @precondition - Age group should be in position 2 of the bag (To be changed!)
	 */
	private void outputPerAgeGroup(String name, MultiKeyBag bag,  boolean outputCount, boolean outputCountsOnly){
		if (bag != null){
			TreeSet<Object> agegroups = bag.getKeyValuesAsObject(2);

			for (Object agegroup: agegroups){
				if (outputCountsOnly) {
					addToOutput(name,"AGE",agegroup.toString(),null,null,"T",
							countToString(bag.getHistogramStats(new ExtendedMultiKey(Wildcard.BYTE(), Wildcard.INTEGER(), agegroup))));
					addToOutput(name,"AGE",agegroup.toString(),null,null,"F",
							countToString(bag.getHistogramStats(new ExtendedMultiKey(DataDefinition.FEMALE_GENDER, Wildcard.INTEGER(),agegroup))));
					addToOutput(name,"AGE",agegroup.toString(),null,null,"M",
							countToString(bag.getHistogramStats(new ExtendedMultiKey(DataDefinition.MALE_GENDER, Wildcard.INTEGER(),agegroup))));
				} else {
					addToOutput(name,"AGE",agegroup.toString(),null,null,"T",
							statsToString(bag.getHistogramStats(new ExtendedMultiKey(Wildcard.BYTE(), Wildcard.INTEGER(), agegroup)), outputCount));
					addToOutput(name,"AGE",agegroup.toString(),null,null,"F",
							statsToString(bag.getHistogramStats(new ExtendedMultiKey(DataDefinition.FEMALE_GENDER,Wildcard.INTEGER(),agegroup)), outputCount));
					addToOutput(name,"AGE",agegroup.toString(),null,null,"M",
							statsToString(bag.getHistogramStats(new ExtendedMultiKey(DataDefinition.MALE_GENDER,Wildcard.INTEGER(),agegroup)), outputCount));
				}
			}
		}
	}

	/**
	 * Appends a formatted statistics row to the output buffer.
	 * @param varName - the name of the statistical variable in question
	 * @param firstGroup - the name of the first grouping variable
	 * @param firstValue - the value of the first grouping variable
	 * @param secondGroup - the name of the second grouping variable
	 * @param secondValue - the value of the second grouping variable
	 * @param gender - the gender
	 * @param stats	- all the statistics
	 */
	private void addToOutput(String varName, String firstGroup, String firstValue, String secondGroup, String secondValue, String gender, String stats){
		if (stats != null){
			out.appendln((varName+"\t"+(firstGroup != null && !firstGroup.equals("") ?
					firstGroup+"\t"+firstValue+"\t" : "\t\t")+(secondGroup != null && !secondGroup.equals("") ?
							secondGroup+"\t"+secondValue+"\t" : "\t\t"))+gender+"\t"+stats);
		}else{
			out.appendln(varName+"\t\t\t\t\t\t\t\t\t\t\t\t\t"); //check consistency if modifications arrive
		}
	}

	/**
	 * Appends a formatted statistics row to the output.
	 * @param varName - the name of the statistical variable in question
	 * @param firstGroup - the name of the first grouping variable
	 * @param secondGroup - the name of the first grouping variable
	 * @param gender - the gender
	 * @param stats	- all the statistics
	 */
	private void addToOutput(String varName, String firstGroup, String secondGroup, String gender, String stats){
		if (stats != null){
			out.appendln(varName+"\t"+(firstGroup != null && !firstGroup.equals("") ?
					firstGroup : "\t\t")+(secondGroup != null && !secondGroup.equals("") ? secondGroup : "\t\t")+gender+"\t"+stats);
		}else{
			out.appendln(varName+"\t\t\t\t\t\t\t\t\t\t\t\t\t");
		}
	}

	/**
	 * Returns a String representation of the statistics.
	 * @param stats - statistics of a bag
	 * @param outputCount - if true the count is added in the total column, otherwise the sum
	 * @return - String representation or empty string if there is no data
	 * @see HistogramStats
	 */
	private String statsToString(HistogramStats stats, boolean outputCount){
		if (stats != null){
			if (stats.getCount()>0) {
				return 	StringUtilities.format(stats.getMin()) +"\t"+
						StringUtilities.format(stats.getMax()) +"\t"+
						(outputCount ? StringUtilities.format(stats.getCount()) : StringUtilities.format(stats.getSum()))+"\t"+
						StringUtilities.format(stats.getMean())+"\t"+
						StringUtilities.format(stats.getPercentile(25))+"\t"+
						StringUtilities.format(stats.getPercentile(50))+"\t"+
						StringUtilities.format(stats.getPercentile(75))+"\t"+
						StringUtilities.format(stats.getStdDev());
			}
		}

		return "\t\t\t\t\t\t"; //check consistency with number of elements in stats
	}

	/**
	 * Returns a String representation of only the count not other stats.
	 * @param stats - the statistics of a bag
	 * @return - String representation or empty if there is no data
	 * @see HistogramStats
	 */
	private String countToString(HistogramStats stats){
		if (stats != null){
			return  "\t\t"+StringUtilities.format(stats.getCount()) +"\t\t\t\t\t\t";
		}

		return "\t\t\t\t\t\t\t\t";
	}

	/**
	 * Flush the output stream to file.
	 */
	private void flush() {
		FileUtilities.outputData(this.outputFileName, out, true);
		out = new StrBuilder();
	}

	/**
	 * Returns a the age group as a string based on the group size.
	 * @param years - age of patient in years
	 * @param groupsize - size of group in years
	 * @return - age group as string, e.g., "10-19"
	 */
	public static String getAgegroup(double years, int groupsize){
		int groupMin = (int) Math.floor(years/groupsize)*groupsize;
		int groupMax = groupMin + groupsize-1;
		return (groupMin < 10 ? "0"+groupMin : groupMin)+"-"+
		(groupMax < 10 ? "0"+groupMax : groupMax);
	}

	@Override
	public void clearMemory(){
		super.clearMemory();

		startDatesBag = null;
		endDatesBag = null;
		yearAgeGroupAtStartAgeBag = null;
		yearAgeGroupAtEndAgeBag = null;
		yearAgeGroupAtStartOfYearBag = null;
		yearAgeGroupAtStartPatientTimeBag = null;
		beforeYearAgeGroupPatientTimeBag = null;
		inYearAgeGroupPatientTimeBag = null;
		afterYearAgeGroupPatientTimeBag = null;
		yearActivePatientsBag = null;
		yearBirthDatesBag = null;
		yearsPatientTimeBag = null;

		out = null;

	}

}


