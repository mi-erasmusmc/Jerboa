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

import java.util.ArrayList;
import java.util.List;

import org.erasmusmc.jerboa.dataClasses.Patient;

/**
 * A class to contain the age group definition from a Jerboa script.
 *
 * @author bmosseveld
 *
 */
public class AgeGroupDefinition{

	/**
	 * The list of all age groups in the definition.
	 */
	private List<AgeGroup> ageGroupList = new ArrayList<AgeGroup>();

	private boolean ok = true;

	/**
	 * Constructor that converts the age group definition from a Jerboa script to the AgeGroupDefinition object.
	 * @param scriptDefinition - The {@literal ArrayList<String>} object containing the age group definition as created by the script parser.
	 */
	public AgeGroupDefinition(List<String> scriptDefinition) {
		this(scriptDefinition, true);
	}

	/**
	 * Converts the age group definition from a Jerboa script to the AgeGroupDefinition object.
	 * @param scriptDefinition - The {@literal ArrayList<String>} object containing the age group definition as created by the script parser.
	 * @param allowOverlap     - If true overlap between age groups is allowed, otherwise not.
	 */
	public AgeGroupDefinition(List<String> scriptDefinition, boolean allowOverlap) {
		for (String ageGroupDefintion : scriptDefinition) {
			AgeGroup ageGroup = new AgeGroup(ageGroupDefintion);
			if (ageGroup.isOk()) {
				if (!allowOverlap) {
					AgeGroup overlappingAgeGroup = null;
					for (AgeGroup checkAgeGroup : ageGroupList) {
						if ((ageGroup.minAge < checkAgeGroup.maxAge) && (ageGroup.maxAge > checkAgeGroup.minAge)) {
							overlappingAgeGroup = checkAgeGroup;
							ok = false;
							break;
						}
					}
					if (!ok) {
						Logging.add("Age group '" + ageGroup + "' overlaps with age group" + overlappingAgeGroup + ".");
					}
				}
				if (ok) {
					ageGroupList.add(ageGroup);
				}
			}
			else {
				ok = false;
			}
		}
	}

	/**
	 * Adds an age group.
	 */
	public void add(String definition) {
		ageGroupList.add(new AgeGroup(definition));
	}


	/**
	 * Returns if the age group definition succeeded.
	 * Needed too test if there are no overlapping age groups.
	 * @return - true if the age definition was successful; false otherwise
	 */
	public boolean isOK() {
		return ok;
	}

	/**
	 * Returns the list of all AGEGroup objects in the AgeGroupDefinition.
	 * @return - A list containing all age groups.
	 */
	public List<AgeGroup> getAgeGroups() {
		return ageGroupList;
	}

	/**
	 * Get all age groups that include the given age.
	 * REMARK:
	 *    Assumes that the passed age, the minimum age of all age groups,
	 *    and the maximum age of all age groups are all of the same unit.
	 *
	 * @param age - The age for which the age groups should be selected.
	 * @return - A list containing all age groups that include the given age.
	 */
	public List<AgeGroup> getAgeGroups(int age) {
		List<AgeGroup> result = new ArrayList<AgeGroup>();
		for (AgeGroup ageGroup : ageGroupList) {
			if (ageGroup.inAgeGroup(age)) {
				result.add(ageGroup);
			}
		}
		return result;
	}


	/**
	 * Get all age groups of patient at a certain date.
	 * @param patient - the patient of interest
	 * @param date - the date in numbers of days to calculate the age groups
	 * @return - A list containing all age groups that include the given age.
	 */
	public List<AgeGroup> getAgeGroups(Patient patient, int date) {
		List<AgeGroup> result = new ArrayList<AgeGroup>();
		for (AgeGroup ageGroup : ageGroupList) {
			if (ageGroup.inAgeGroup(patient, date)) {
				result.add(ageGroup);
			}
		}

		return result;
	}

	/**
	 * Get all age groups that include the given age.
	 * @param age - The age in years for which the age groups should be selected.
	 * @return - A list containing all age groups that include the given age.
	 */
	public List<AgeGroup> getAgeGroupsEstimated(long age) {
		List<AgeGroup> result = new ArrayList<AgeGroup>();
		for (AgeGroup ageGroup : ageGroupList) {
			if (ageGroup.inAgeGroupEstimated(age)) {
				result.add(ageGroup);
			}
		}

		return result;
	}

	/**
	 * Get the age groups with the specified label.
	 * @param label - The label of the age group.
	 * @return - The age group; null if not in list
	 */
	public AgeGroup getAgeGroup(String label) {
		for (AgeGroup ageGroup : ageGroupList) {
			if (ageGroup.getLabel().equals(label)) {
				return ageGroup;
			}
		}

		return null;
	}

	/**
	 * Get the number of age groups in the definition.
	 * @return - The number of age groups in the definition.
	 */
	public int getCount() {
		return ageGroupList.size();
	}

	/**
	 * Get the index of the ageGroup.
	 * @param ageGroup - the age group of interest
	 * @return - the index in the list of ageGroup
	 */
	public int getIndex(AgeGroup ageGroup) {
		return ageGroupList.indexOf(ageGroup);
	}

	//TODO: makes this check not dependent on the order in the script
	/**
	 * Checks if the ageGroups are continuous or not.
	 * Assumes the age groups are in order in the script!
	 * @return - true if the age groups are not continuous; false otherwise
	 */
	public boolean hasGaps(){
		boolean first = true;
		boolean result = false;
		int lastMax = -1;
		for (AgeGroup ageGroup : ageGroupList) {
			if (!first && ageGroup.minAge != lastMax) {
				result = true;
				break;
			} else
				lastMax = ageGroup.maxAge;
			first = false;
		}

		return result;
	}

	/**
	 * Inner utility class. Object containing the definition of an age group.
	 *
	 * @author bmosseveld
	 *
	 */
	public class AgeGroup  implements Comparable<AgeGroup>{

		public static final int DAYS  = 0;
		public static final int YEARS = 1;
		private boolean ok = true;

		/**
		 * The label of the age group.
		 */
		private String label = "";

		/**
		 * The minimum age (inclusive) for the age group.
		 */
		private int minAge = 0;

		/**
		 * The minimum age unit
		 */
		private int minUnit = YEARS;

		/**
		 * The maximum age (exclusive) for the age group.
		 */
		private int maxAge = 999;

		/**
		 * The maximum age unit
		 */
		private int maxUnit = YEARS;

		/**
		 * The estimated minimum age in days.
		 */
		private long estimatedMinAge = 0;

		/**
		 * The estimated maximum age in days.
		 */
		private long estimatedMaxAge = 999999999;

		/**
		 * Constructor that creates an age group object given its definition from teh Jerboa script.
		 * @param definition - The age group definition fro the Jerboa script.
		 */
		public AgeGroup(String definition) {
			String[] definitionSplit = definition.split(";");

			String minAgeString = definitionSplit[0];
			String unitCharacter = minAgeString.substring(minAgeString.length() - 1).toUpperCase();
			if (unitCharacter.equals("D")) {
				minUnit = DAYS;
				minAgeString = minAgeString.substring(0, minAgeString.length() - 1);
			}
			else if (unitCharacter.equals("Y")) {
				minUnit = YEARS;
				minAgeString = minAgeString.substring(0, minAgeString.length() - 1);
			}
			try {
				minAge = Integer.parseInt(minAgeString);
			}
			catch (NumberFormatException e) {
				ok = false;
				Logging.add("Illegal minimum age " + minAgeString + " in age group " + definition);
			}

			String maxAgeString = definitionSplit[1];
			unitCharacter = maxAgeString.substring(maxAgeString.length() - 1).toUpperCase();
			if (unitCharacter.equals("D")) {
				maxUnit = DAYS;
				maxAgeString = maxAgeString.substring(0, maxAgeString.length() - 1);
			}
			else if (unitCharacter.equals("Y")) {
				maxUnit = YEARS;
				maxAgeString = maxAgeString.substring(0, maxAgeString.length() - 1);
			}
			try {
				maxAge = Integer.parseInt(maxAgeString);
			}
			catch (NumberFormatException e) {
				Logging.add("Illegal maximum age " + maxAgeString + " in age group " + definition);
			}

			estimatedMinAge = Math.round(minAge * 365.25);
			estimatedMaxAge = Math.round(maxAge * 365.25);
			label  = definitionSplit[2];
		}

		/**
		 * Test if the given age is within the age group.
		 * REMARK:
		 *    Assumes that the passed age, the minimum age of the age group,
		 *    and the maximum age of the age group are all of the same unit.
		 *
		 * @param age - The age.
		 * @return - true if the age is within the age group, otherwise false.
		 */
		public boolean inAgeGroup(int age) {
			return ((age >= minAge) && (age < maxAge));
		}

		/**
		 * Test if the age of a given patient is within the age group at the specified date.
		 * @param patient - The patient object.
		 * @param date    - The date for which the age of the patient should be checked.
		 * @return - true if the age of the patient is within the age group, otherwise false.
		 */
		public boolean inAgeGroup(Patient patient, int date) {
			int minCompareAge;
			int maxCompareAge;

			if (minUnit == DAYS) {
				minCompareAge = patient.getAgeAtDate(date);
			}
			else {
				minCompareAge = patient.getAgeAtDateInYears(date);
			}

			if (maxUnit == DAYS) {
				maxCompareAge = patient.getAgeAtDate(date);
			}
			else {
				maxCompareAge = patient.getAgeAtDateInYears(date);
			}

			return ((minCompareAge >= minAge) && (maxCompareAge < maxAge));
		}

		/**
		 * Test if the given age is within the age group.
		 * @param age - The age in years.
		 * @return - true if the age is within the age group, otherwise false.
		 */
		public boolean inAgeGroupEstimated(long age) {
			return ((age >= estimatedMinAge) && (age < estimatedMaxAge));
		}

		/**
		 * Get the label of the age group.
		 * @return - The label of the age group.
		 */
		public String getLabel() {
			return label;
		}

		/**
		 * Get the minimum age of the age group.
		 * @return - The minimum age of the age group.
		 */
		public int getMinAge() {
			return minAge;
		}

		/**
		 * Get the maximum age of the age group.
		 * @return - The maximum age of the age group.
		 */
		public int getMaxAge() {
			return maxAge;
		}

		/**
		 * Check if the definition of the age group is ok.
		 * @return - true if teh age group definition is ok, othewise false.
		 */
		public boolean isOk() {
			return ok;
		}


		/**
		 * Get the definition of the age group as string
		 * @return - The string describing the age group.
		 */
		public String toString() {
			return minAge + ";" + maxAge + ";" + label + " " + estimatedMinAge + "-" + estimatedMaxAge;
		}

		@Override
		public int compareTo(AgeGroup o) {
			return this.minAge - o.minAge;
		}
	}

}
