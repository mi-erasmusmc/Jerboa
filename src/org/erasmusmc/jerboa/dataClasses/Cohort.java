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
 ******************************************************************************************/

package org.erasmusmc.jerboa.dataClasses;

import java.util.Comparator;

import org.erasmusmc.jerboa.utilities.DateUtilities;

/**
 * This is class defines the attributes of a cohort in the history of a patient.
 * It is used to allow multiple cohorts in the patient object
 * @author PR
 *
 */
public class Cohort implements Comparable<Cohort> {

	//attributes
	public String patientId;
	public String type;
	public String prescriberType;
	public String prescriberId;
	public int cohortStartDate;
	public int cohortEndDate;


	//CONSTRUCTORS

	/**
	 * Basic constructor.
	 */
	public Cohort(){
		super();
	}

	/**
	 * Constructor accepting another episode as parameter.
	 * @param cohort- the cohort to be used to initialize.
	 */
	public Cohort(Cohort cohort) {
		super();
		this.patientId = cohort.patientId;
		this.type = cohort.type;
		this.cohortStartDate = cohort.cohortStartDate;
		this.cohortEndDate = cohort.cohortEndDate;
		this.prescriberType = cohort.prescriberType;
		this.prescriberId = cohort.prescriberId;
	}


	//TO STRING METHODS
	/**
	 * Returns a character string representation of the
	 * common columns in all Episode type objects.
	 */
	@Override
	public String toString(){
		return (patientId+","+type+","+DateUtilities.daysToDate(cohortStartDate)+","+DateUtilities.daysToDate(cohortEndDate)+ ","+prescriberId+","+prescriberType);
	}

	//COMPARATORS
	/**
	 * Compares two cohorts based on their start date
	 * @param e - the cohort to be compared with this cohort
	 * @return - if the cohort start dates are equal returns 0; if this cohort started
	 * at a later date than cohort c, then it is considered greater and return value is 1;
	 * This cohort is considered earlier than Cohort c if result is negative.
	 */
	@Override
	public int compareTo(Cohort c) {
		return this.cohortStartDate - c.cohortStartDate;
	}

	/**
	 * Inner class used in order to perform sorting based on cohort types.
	 */
	public static class CompareType implements Comparator<Cohort>{

		@Override
		public int compare(Cohort c1, Cohort c2) {
			return c1.type.compareTo(c2.type);
		}
	}


	/**
	 * Inner class used in order to perform sorting based on cohort start date and etype.
	 */
	public static class CompareByDateAndType implements Comparator<Cohort>{

		@Override
		public int compare(Cohort c1, Cohort c2) {
			return  (c1.cohortStartDate == c2.cohortStartDate ?
				new Cohort.CompareType().compare(c1, c2) :
				c1.cohortStartDate - c2.cohortStartDate);
		}
	}

	/**
	 * Inner class used in order to perform sorting based on cohort type and cohort start date.
	 */
	public static class CompareByTypeAndDate implements Comparator<Cohort>{

		@Override
		public int compare(Cohort c1, Cohort c2) {
			return  new Cohort.CompareType().compare(c1, c2) == 0 ?
				c1.cohortStartDate - c2.cohortStartDate : new Cohort.CompareType().compare(c1, c2);
		}
	}

	//SPECIFIC METHODS
	/**
	 * Checks if the date occurs in the cohort.
	 * @param startDate - start date (inclusive)
	 * @param endDate - end date (inclusive)
	 * @return - true if the occurs between startDate and endDate; false otherwise
	 */
	public boolean isInCohort(int date){
		return (date >= cohortStartDate) && (date <= cohortEndDate);
	}

	/**
	 * Checks if the date occurs in this interval.
	 * @param startDate - start date
	 * @param endDate - end date
	 * @param includeStartDate - true if the day of start should be taken into consideration
	 * @param includeEndDate - true if the day of end should be taken into consideration
	 * @return - true if the date occurs between startDate and endDate with the specified
	 * conditions; false otherwise
	 */
	public boolean isInPeriod(int date, boolean includeStartDate, boolean includeEndDate){
		return ((includeStartDate ? date >= cohortStartDate : date > cohortStartDate) &&
				(includeEndDate ? date <= cohortEndDate : date < cohortEndDate));
	}

	//GETTERS AND SETTERS
	public String getPatientId() {
		return patientId;
	}

	public void setPatientId(String patientId) {
		this.patientId = patientId;
	}

	public String getType() {
		return type;
	}

	public void setType(String type) {
		this.type = type;
	}

	public int getCohortStartDate() {
		return cohortStartDate;
	}

	public void setCohortStartDate(int cohortStartDate) {
		this.cohortStartDate = cohortStartDate;
	}

	public int getCohortEndDate() {
		return cohortEndDate;
	}

	public void setCohortEndDate(int cohortEndDate) {
		this.cohortEndDate = cohortEndDate;
	}

	public String getPrescriberType() {
		return prescriberType;
	}

	public void setPrescriberType(String prescriberType) {
		this.prescriberType = prescriberType;
	}

	public String getPrescriberId() {
		return prescriberId;
	}

	public void setPrescriberId(String prescriberId) {
		this.prescriberId = prescriberId;
	}

}