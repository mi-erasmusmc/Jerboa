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

import org.erasmusmc.jerboa.Jerboa;
import org.erasmusmc.jerboa.config.DataDefinition;
import org.erasmusmc.jerboa.config.FilePaths;
import org.erasmusmc.jerboa.config.Parameters;
import org.erasmusmc.jerboa.dataClasses.Patient;
import org.erasmusmc.jerboa.engine.Modifier;
import org.erasmusmc.jerboa.utilities.DateUtilities;
import org.erasmusmc.jerboa.utilities.ExtendedMultiKey;
import org.erasmusmc.jerboa.utilities.Logging;
import org.erasmusmc.jerboa.utilities.MultiKeyBag;
import org.erasmusmc.jerboa.utilities.StringUtilities;
import org.erasmusmc.jerboa.utilities.TimeUtilities;
import org.erasmusmc.jerboa.utilities.Wildcard;


/**
 * This modifier defines a cohort with a fixed start and/or end date.
 *
 * @see org.erasmusmc.jerboa.dataClasses.Patient
 *
 * @author bmosseveld
 *
 */
public class FixedCohortDefinition extends Modifier {

	/**
	 * If true the cohort start and end as defined by a previous cohort definition modifier
	 * is used, otherwise population start and end are used.
	 */
	public boolean chainCohortDefinitions;

	/**
	 * The start date of the cohort as yyyymmdd.
	 * When left empty it keeps the population start date or current cohort start date of the patient.
	 */
	public String cohortStartDate;

	/**
	 * The end date of the cohort as yyyymmdd.
	 * When left empty it keeps the population end date or current cohort end date of the patient.
	 */
	public String cohortEndDate;

	/**
	 * If true an attrition filename will be created or appended on called Attrition.csv
	 */
	public boolean attritionFile;


	// Patient time bag
	private MultiKeyBag patientTime;

	// Counters
	private int originalCount;
	private int countRemoved;

	// Cohort start en end
	private Integer cohortStart;
	private Integer cohortEnd;

	private String attritionFileName;   //used to save the attrition


	@Override
	public boolean init() {
		boolean initOK = true;

		if (cohortStartDate.equals("")) {
			cohortStart = -1;
		}
		else {
			cohortStart = DateUtilities.dateToDays(cohortStartDate, DateUtilities.DATE_ON_YYYYMMDD);
			if (cohortStart == null) {
				Logging.add("ERROR: Illegal cohort start date " + cohortStartDate);
				initOK = false;
			}
		}

		if (cohortEndDate.equals("")) {
			cohortEnd = -1;
		}
		else {
			cohortEnd = DateUtilities.dateToDays(cohortEndDate, DateUtilities.DATE_ON_YYYYMMDD);
			if (cohortEnd == null) {
				Logging.add("ERROR: Illegal cohort end date " + cohortEndDate);
				initOK = false;
			}
		}

		if ((initOK) && (cohortStart != -1) && (cohortEnd != -1) && (cohortStart >= cohortEnd)) {
			Logging.add("ERROR: Cohort start date >= cohort end date");
			initOK = false;
		}

		if ((!Jerboa.unitTest) && intermediateFiles) {
			String header = "SubsetID";
			header += "," + "PatientID";
			header += "," + "Birthdate";
			header += "," + "Gender";
			header += "," + "Startdate";
			header += "," + "Enddate";
			header += "," + "PopulationStart";
			header += "," + "PopulationEnd";
			header += "," + "CohortStart";
			header += "," + "CohortEnd";
			initOK &= Jerboa.getOutputManager().addFile(this.intermediateFileName);
			Jerboa.getOutputManager().writeln(this.intermediateFileName, header, false);
		}

		if (initOK && attritionFile && (!Jerboa.unitTest)) {
			attritionFileName = FilePaths.WORKFLOW_PATH+this.getParentModule()+"/"+
					Parameters.DATABASE_NAME+"_"+this.getParentModule()+
					"_"+TimeUtilities.TIME_STAMP+"_"+Parameters.VERSION+"_attrition.csv";
			Jerboa.getOutputManager().addFile(attritionFileName, 100);
		}

		patientTime = new MultiKeyBag();

		originalCount = 0;
		countRemoved = 0;

		return initOK;
	}

	@Override
	public Patient process(Patient patient) {

		if (patient != null && (chainCohortDefinitions ? patient.isInCohort() : patient.isInPopulation())) {

			// Update patient counter
			originalCount++;

			int start = (cohortStart == -1 ? (chainCohortDefinitions ? patient.getCohortStartDate() : patient.getPopulationStartDate()) : (chainCohortDefinitions ? Math.max(patient.getCohortStartDate(), cohortStart) : Math.max(patient.getPopulationStartDate(), cohortStart)));
			int end = (cohortEnd == -1 ? (chainCohortDefinitions ? patient.getCohortEndDate() : patient.getPopulationEndDate()) : (chainCohortDefinitions ? Math.min(patient.getCohortEndDate(), cohortEnd) : Math.min(patient.getPopulationEndDate(), cohortEnd)));

			if (start < end) {
				patient.setCohortStartDate(start);
				patient.setCohortEndDate(end);
				patient.setInCohort(true);

				// Update patient time
				if (intermediateStats) {
					patientTime.add(new ExtendedMultiKey("ALL", patient.cohortEndDate - patient.cohortStartDate));
				}
			}
			else {
				patient.setInCohort(false);
				countRemoved++;
			}

			if ((!Jerboa.unitTest) && intermediateFiles) {
				String record = patient.subset;
				record += "," + patient.getPatientID();
				record += "," + DateUtilities.daysToDate(patient.getBirthDate());
				record += "," + patient.getGender();
				record += "," + DateUtilities.daysToDate(patient.startDate);
				record += "," + DateUtilities.daysToDate(patient.endDate);
				record += "," + DateUtilities.daysToDate(patient.getPopulationStartDate());
				record += "," + DateUtilities.daysToDate(patient.getPopulationEndDate());
				record += "," + (patient.getCohortStartDate() == -1 ? "" : DateUtilities.daysToDate(patient.getCohortStartDate()));
				record += "," + (patient.getCohortEndDate() == -1 ? "" : DateUtilities.daysToDate(patient.getCohortEndDate()));
				Jerboa.getOutputManager().writeln(intermediateFileName, record, true);
			}
		}
		return patient;
	}

	@Override
	public void outputResults() {
		if ((!Jerboa.unitTest) && intermediateFiles) {
			Jerboa.getOutputManager().closeFile(intermediateFileName);
		}

		Logging.addNewLine();
		Logging.add("Fixed cohort definition modifier results:");
		Logging.add("-----------------------------------------");
		Logging.add("Original cohort size        : " + originalCount);
		Logging.add("New cohort size             : "+ (originalCount - countRemoved));
		Logging.add("Total patient time in cohort: " +StringUtilities.format((float)patientTime.getHistogramStats(new ExtendedMultiKey("ALL", Wildcard.INTEGER())).getSum()/(float)DateUtilities.daysPerYear)+" years ");
		Logging.addNewLine();

		if ((!Jerboa.unitTest) && attritionFile) {
			Jerboa.getOutputManager().writeln(attritionFileName, title + " (FixedCohortDefinition),", true);
			Jerboa.getOutputManager().writeln(attritionFileName, "Patients in cohort (" + cohortStartDate + "-" + cohortEndDate + ")," + (originalCount - countRemoved),true);
			Jerboa.getOutputManager().writeln(attritionFileName, " ,",true);
			Jerboa.getOutputManager().flush(attritionFileName);
		}
	}

	@Override
	public void setNeededFiles() {
		setRequiredFile(DataDefinition.PATIENTS_FILE);
	}

	@Override
	public void setNeededExtendedColumns() {}

	@Override
	public void setNeededNumericColumns() {}

}
