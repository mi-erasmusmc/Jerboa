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

package org.erasmusmc.jerboa.modifiers;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.erasmusmc.jerboa.Jerboa;
import org.erasmusmc.jerboa.config.DataDefinition;
import org.erasmusmc.jerboa.dataClasses.Measurement;
import org.erasmusmc.jerboa.dataClasses.Patient;
import org.erasmusmc.jerboa.engine.Modifier;
import org.erasmusmc.jerboa.utilities.DateUtilities;


/**
 * This modifier creates a categorical measurements for the categories defined in
 * the categories parameter.
 * @author bmosseveld
 */
public class MeasurementCategories extends Modifier {

	/**
	 * The definitions of the categories.
	 * Format:
	 *
	 *   categorical measurement;category;source measurement;lower boundary;upper boundary;minAge;maxAge
	 *
	 * where
	 *
	 *   categorical measurement = The name of the categorical measurement to be created.
	 *   category                = The category of the measurement described in this rule.
	 *   source measurement      = The source measurement on which the category is based.
	 *   lower boundary          = The lower boundary for the category (inclusive).
	 *   upper boundary          = The upper boundary for the category (exclusive).
	 *   minAge                  = The minimum age in years at the date of the source measurement (inclusive)
	 *                             of the patients to whom the rule applies.
	 *   maxAge                  = The maximum age in years at the date of the source measurement (exclusive)
	 *                             of the patients to whom the rule applies.
	 */
	public List<String> categories = new ArrayList<String>();


	private Categories categoryDefinitions;

	private String fileName = "";


	@Override
	public boolean init() {
		boolean initOK = true;

		categoryDefinitions = new Categories(categories);

		if ((!Jerboa.unitTest) && intermediateFiles){
			fileName = this.intermediateFileName;
			if (Jerboa.getOutputManager().addFile(fileName)) {
				String header = "SubsetID";
				header += "," + "PatientID";
				header += "," + "Date";
				header += "," + "MeasurementType";
				header += "," + "Value";
				header += "," + "CategoricalType";
				header += "," + "CategoricalValue";
				Jerboa.getOutputManager().writeln(fileName, header, false);
			}
			else {
				initOK = false;
			}
		}

		return initOK;
	}


	@Override
	public Patient process(Patient patient) {
		if (patient.inPopulation){
			categoryDefinitions.setPatient(patient);
			List<Measurement> categoricalMeasurements = new ArrayList<Measurement>();
			for (Measurement measurement : patient.getMeasurements()) {
				List<Category> categoryList = categoryDefinitions.getCategories(measurement);
				for (Category category : categoryList) {
					Measurement categoricalMeasurment = new Measurement(measurement);
					categoricalMeasurment.setType(category.getType().toUpperCase());
					categoricalMeasurment.setValue(category.getCategory());
					categoricalMeasurements.add(categoricalMeasurment);

					if ((!Jerboa.unitTest) && intermediateFiles) {
						String record = patient.subset;
						record += "," + patient.ID;
						record += "," + DateUtilities.daysToDate(measurement.getDate());
						record += "," + measurement.getType();
						record += "," + measurement.getValue();
						record += "," + categoricalMeasurment.getType();
						record += "," + categoricalMeasurment.getValue();
						Jerboa.getOutputManager().writeln(fileName, record, true);
					}
				}
			}

			patient.getMeasurements().addAll(categoricalMeasurements);
			Collections.sort(patient.measurements);
		}
		return patient;
	}


	@Override
	public void outputResults() {
		if ((!Jerboa.unitTest) && intermediateFiles) {
			Jerboa.getOutputManager().closeFile(fileName);
		}
	}


	@Override
	public void setNeededFiles() {
		setRequiredFile(DataDefinition.PATIENTS_FILE);
		setRequiredFile(DataDefinition.MEASUREMENTS_FILE);
	}


	@Override
	public void setNeededExtendedColumns() {
	}


	@Override
	public void setNeededNumericColumns() {
		// TODO Auto-generated method stub

	}


	private class Categories {
		private Map<String, List<Category>> categories = new HashMap<String, List<Category>>();
		private Patient patient;

		public Categories(List<String> definitionList) {
			for (String definition : definitionList) {
				Category category = new Category(definition);
				if (!categories.containsKey(category.getSourceMeasurementType())) {
					categories.put(category.getSourceMeasurementType(), new ArrayList<Category>());
				}
				categories.get(category.getSourceMeasurementType()).add(category);
			}
		}


		public List<Category> getCategories(Measurement measurement) {
			List<Category> resultCategory = new ArrayList<Category>();
			if (categories.containsKey(measurement.getType())) {
				for (Category category : categories.get(measurement.getType())) {
					int age = patient.getAgeAtDateInYears(measurement.date);
					if (category.inCategory(measurement.getValue(),age)) {
						resultCategory.add(category);
					}
				}
			}
			return resultCategory;
		}

		public void setPatient(Patient patient) {
			this.patient = patient;
		}
	}


	private class Category {
		private String categoricalMeasurementType = "";
		private String category = "";
		private String sourceMeasurementType = "";
		private double lowerBoundary = Integer.MIN_VALUE;
		private double upperBoundary = Integer.MAX_VALUE;
		private int minAge = 0;
		private int maxAge = 99999;

		public Category(String definition) {
			String[] definitionSplit = definition.split(";");
			categoricalMeasurementType = definitionSplit[0];
			category = definitionSplit[1];
			sourceMeasurementType = definitionSplit[2];
			if ((definitionSplit.length > 3)  && (!definitionSplit[3].equals(""))) {
				lowerBoundary = Double.parseDouble(definitionSplit[3]);
			}
			if ((definitionSplit.length > 4)  && (!definitionSplit[4].equals(""))) {
				upperBoundary = Double.parseDouble(definitionSplit[4]);
			}
			if ((definitionSplit.length > 5)  && (!definitionSplit[5].equals(""))) {
				minAge = Integer.parseInt(definitionSplit[5]);
			}
			if ((definitionSplit.length > 6)  && (!definitionSplit[6].equals(""))) {
				maxAge = Integer.parseInt(definitionSplit[6]);
			}
		}

		public String getSourceMeasurementType() {
			return sourceMeasurementType;
		}


		public String getType() {
			return categoricalMeasurementType;
		}


		public String getCategory() {
			return category;
		}


		public boolean inCategory(String value, int Age) {
			boolean result = false;
			double doubleValue = 0;
			try {
				doubleValue = Double.parseDouble(value);
				if ((lowerBoundary <= doubleValue) && (doubleValue < upperBoundary) && minAge <= Age && Age < maxAge) {
//				if (Integer.toString(doubleValue).equals(value) && (lowerBoundary <= doubleValue) && (doubleValue < upperBoundary)) {
					return true;
				}
			}
			catch (NumberFormatException e) {
				result = false;
			}
			int intValue = 0;
			try {
				intValue = Integer.parseInt(value);
				if ((lowerBoundary <= intValue) && (intValue < upperBoundary)) {
					return true;
				}
			}
			catch (NumberFormatException e) {
				result = false;
			}
			return result;
		}
	}

}
