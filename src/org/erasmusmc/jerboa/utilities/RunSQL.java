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
 * $Rev:: 4682              $:  Revision of last commit                                   *
 * $Author:: root           $:  Author of last commit                                     *
 * $Date:: 					$:  Date and time (CET) of last commit						  *
 ******************************************************************************************/
package org.erasmusmc.jerboa.utilities;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map.Entry;

import javax.swing.JOptionPane;

import org.erasmusmc.jerboa.Jerboa;
import org.erasmusmc.jerboa.config.FilePaths;
import org.erasmusmc.jerboa.engine.ScriptParser.Pair;
import org.erasmusmc.jerboa.engine.ScriptParser.Settings;
import org.erasmusmc.jerboa.gui.JerboaGUI;
import org.erasmusmc.jerboa.utilities.dataSimulator.WriteCSVFile;

/**
 * This class contains the necessary methods to execute SQL queries
 * parsed from input files and write the results of those queries to file(s).
 *
 * @author MG {@literal &} MS
 *
 */
public class RunSQL{

	/**
	 * Contains a list of SQL scripts to
	 * prepare the (intermediate) tables in the DB.
	 */
	private List<String> prepareData;

	/**
	 * Contains pairs of SQL files to fetch data from DB
	 * and output file name to export data (separated by ';').
	 * EXAMPLE: DataFetch1.sql;Patients.txt
	 */
	private List<String> fetchData;

	/**
	 * Map of the parsed entries of fetchData.
	 */
	private HashMap<String, String> outputData;

	//details about the DB connection
	private String connectionDetails;
	private DBConnection connection;

	//user feed-back
	public Timer timer;
	public Progress progress;

	//run mode flag
	public boolean noGUI;
	public boolean IOisOK = true;

	//CONSTRUCTORS
	/**
	 * Constructor passing the location of the database connection settings file
	 * and a list of files containing the SQL queries to be executed.
	 * @param connectionDetails - the file with the DB connection details
	 * @param prepareData - the files containing SQL queries
	 */
	public RunSQL(String connectionDetails, List<String> prepareData){
		this.connection = new DBConnection(connectionDetails);
		this.prepareData = prepareData;
	}

	/**
	 * Constructor passing the location of the database connection settings file
	 * and a list of files containing the SQL queries to be executed, as well as
	 * the path to output the query results of each input file. Note that the indexes in
	 * the inputFiles list and outputFiles list should correspond (e.g., the results of the
	 * first input file in inputFiles will be output to the first file in outputFiles list).
	 * @param connectionDetails - the file with the DB connection details
	 * @param prepareData - the files containing SQL queries
	 * @param fetchData - the name of the files where the query results are to be output
	 */
	public RunSQL(String connectionDetails, List<String> prepareData, List<String> fetchData){
		this.connection = new DBConnection(connectionDetails);
		this.prepareData = prepareData;
		this.fetchData = fetchData;
		this.outputData = parseFetchData(this.fetchData);
	}

	/**
	 * Constructor passing the connection details and name of
	 * input/output files parsed from the script file.
	 * @param settings - the parsed settings from the script file
	 */
	public RunSQL(Settings settings){
		if (!setParameters(settings)){
			if (noGUI)
				System.out.println("Incorrect parameter settings for RunSQL.");
			else
				Logging.add("Unable to run the SQL queries.\nIncorrect parameter settings.", Logging.ERROR);
		}

		this.connection = new DBConnection(this.connectionDetails);
	}

	/**
	 * Main method for testing/debugging.
	 * @param args - none
	 */
	public static void main(String[] args){

		List<String> inputFileNames = new ArrayList<String>();
		List<String> outputFileNames = new ArrayList<String>();

		String connectionDetails = "D:/Work/TestData/DB/dbSettings.txt";
		inputFileNames.add("D:/Work/TestData/DB/GetPatients.sql");
		inputFileNames.add("D:/Work/TestData/DB/GetPilotDiabEvents.sql");

		outputFileNames.add("D:/Work/TestData/DB/Patients.txt");
		outputFileNames.add("D:/Work/TestData/DB/Events.txt");

		RunSQL sql = new RunSQL(connectionDetails, inputFileNames, outputFileNames);
		sql.noGUI = true;

		try{
			sql.run();
		}catch(Exception e){
			System.out.println("An error occurred. Check if the schema name is valid and/or the queries.");
		}
	}

	/**
	 * Will read queries one by one from the SQL input files and execute them.
	 * The results are fetched making use of the last query in the input file
	 * and they are output to the outputFileName.
	 * @throws Exception - if the database connection cannot be established or input files cannot be read
	 */
	public void run() throws Exception{

		if (IOisOK){

			timer = new Timer(noGUI);
			Connection con = null;

			if (!connection.hasSettingsFile()){
				if (!Jerboa.inConsoleMode)
					new ErrorHandler("No database connection settings file found in current workspace");
				else
					System.out.println("No database connection settings file found in current workspace");
				Jerboa.stop(true);
			}

			int userSelection = -1;
			if (noGUI)
				System.out.println("Reading and executing queries");
			else{
				//show dialog as normal, selected index will be returned.
				userSelection = JOptionPane.showConfirmDialog(JerboaGUI.frame, "Data will be extracted. Files present in the working space"+
						" having the same name "+ "\n" +"as the output files will be overwritten. Do you want to continue?", "Warning", JOptionPane.YES_NO_OPTION,
						JOptionPane.WARNING_MESSAGE);
			}

			if (userSelection == -1 || userSelection == JOptionPane.CLOSED_OPTION || userSelection == JOptionPane.NO_OPTION){
				Jerboa.stop();
				return;
			}

			Logging.add("Started data extraction", Logging.HINT, true);
			Logging.addWithTimeStamp("Extracting data");

			timer.start();

			//establish connection
			con = connection.connect(false); //false = do not stop if schema is not found
			con.setAutoCommit(connection.getType().equals("POSTGRESQL") ? true : connection.autoCommit); //!! check if set to false with PostgreSQL while fetching results

			//TODO check if still necessary; normally done in DBConnection.java
			if (connection.schema != null && !connection.schema.equals("")){
				try{
					Statement useStatement = con.createStatement(ResultSet.TYPE_FORWARD_ONLY, ResultSet.CONCUR_READ_ONLY);
					if (connection.getType().equals("MYSQL") || connection.getType().equals("MSSQL"))
						useStatement.executeQuery("USE "+connection.schema+";");
					else if (connection.getType().equals("POSTGRESQL") || connection.getType().equals("ORACLE"))
						useStatement.executeUpdate("SET "+(connection.getType().equals("POSTGRESQL") ? "search_path TO " : "SCHEMA ")+"'"+connection.schema+"';");
				}catch(Exception e){
					displayError("Unable to select schema "+connection.schema);
				}
			}

			//execute SQL scripts
			runPrepareDataFiles(con);
			runFetchDataFiles(con);

		//IO not OK
		}else{
			displayError("Invalid I/O specification. \nCheck if input files are specified " +
					"in the script and corresponding output files.");
		}

		timer.stopAndDisplay("Data extracted in ");
	}

	/**
	 * Runs sequentially all the SQL files that are for data preparation.
	 * @param con - the DB connection
	 */
	private void runPrepareDataFiles(Connection con){

		progress = new Progress(prepareData.size(), "Querying data", noGUI);
		for (int i = 0; i < prepareData.size(); i ++){
			if (prepareData.get(i) != null && !prepareData.get(i).equals("")){
				try{
					//open the SQL statements file
					BufferedReader reader = FileUtilities.openFile(prepareData.get(i));

					//read queries one by one and execute
					String sqlStatement = readQuery(reader);
					int nbQuery = 1;
					//execute all queries besides the select queries
					while (sqlStatement != null && !sqlStatement.equals("") &&
							!sqlStatement.toLowerCase().startsWith("select")){
						Statement statement = con.createStatement(ResultSet.TYPE_FORWARD_ONLY, ResultSet.CONCUR_READ_ONLY);
						try{
							statement.execute(sqlStatement);
						}catch(Exception e){
							if (noGUI){
								System.out.println("An error occurred while running query: "+sqlStatement);
								System.out.println("Error stack: ");
								e.printStackTrace();
							}else{
								Logging.add("An error occurred while running query number "+nbQuery+" from file "+
										prepareData.get(i).substring(prepareData.get(i).lastIndexOf("/")+1)+":"+
										System.lineSeparator()+""+sqlStatement, Logging.ERROR);
								Logging.outputStackTrace(e);
							}
							Jerboa.stop(true);
						}
						sqlStatement = readQuery(reader);
						nbQuery++;

						//user feedback
						if (noGUI && (nbQuery % 10) == 0)
							System.out.println("Performed "+nbQuery+" queries");

					}//end while

					progress.update();

				}catch(Exception e){
					Logging.outputStackTrace(e);
					displayError("Unable to read the SQL file "+prepareData.get(i).substring(prepareData.get(i).lastIndexOf("/")+1));
					break;
				}
				//input file name empty
			}else{
				displayError("The SQL file at index "+i+" was skipped."+
						"\nCheck if the SQL file name is correct.");
			}

		} //end for
	}

	/**
	 * Runs sequentially all the SQL files that are for result fetching
	 * and outputs them to file. Note that the mapping result fetching/file output is
	 * considered to be one to one.
	 * @param con - the DB connection
	 */
	private void runFetchDataFiles(Connection con){

		//check if PostreSQL connection and turn off autocommit for the result fetching
		try{
			if (connection.getType().equals("POSTGRESQL"))
				con.setAutoCommit(false);
		}catch(Exception e){
			Logging.add("Unable to turn autocommit off.", Logging.ERROR);
			Logging.outputStackTrace(e);
		}

		progress = new Progress(outputData.size(), "Fetching results", noGUI);

		//execute scripts one by one
		for (Entry<String, String> entry : outputData.entrySet()){
			//should never occur at this point.. but just in case
			if ((entry.getKey() != null && !entry.getKey().equals("")) &&
					(entry.getValue() != null && !entry.getValue().equals(""))){
				try{
					//open the SQL statements file
					BufferedReader reader = FileUtilities.openFile(entry.getKey());

					//read queries one by one and execute
					String sqlStatement = readQuery(reader);
					boolean isFirst = true;
					while (sqlStatement != null && !sqlStatement.equals("")){
						fetchResults(con, sqlStatement, entry.getValue(), isFirst);
						isFirst = isFirst ? !isFirst : isFirst;
						sqlStatement = readQuery(reader);
					}

					progress.update();

				}catch(Exception e){
					Logging.outputStackTrace(e);
					displayError("Unable to read the SQL file "+entry.getKey());
					break;
				}
				//input file name empty
			}else{
				displayError("The SQL file "+entry.getKey()+ " was skipped"+
						"\nCheck if the SQL file name is correct.");
			}

		} //end for
	}

	/**
	 * Will set the list of input files and output files, as well as
	 * the connection settings file from the list of settings present in the script file.
	 * @param settings - the settings parsed from the script file.
	 * @return - true if all the parameter settings were correct; false otherwise
	 */
	private boolean setParameters(Settings settings){
		if (settings != null && settings.parameters != null &&
				settings.parameters.size() > 0){

			this.connectionDetails = null;
			this.prepareData = new ArrayList<String>();
			this.fetchData = new ArrayList<String>();

			for (Pair<String, String> pair : settings.parameters){
				if (pair != null && pair.name != null && pair.value != null){
					if (pair.name.equals("connectionsettings"))
						connectionDetails = FilePaths.WORKING_PATH+"/"+pair.value.trim();
					if (pair.name.equals("preparedata"))
						this.prepareData.add(FilePaths.WORKING_PATH+"/"+pair.value.trim());
					if (pair.name.equals("fetchdata"))
						this.fetchData.add(FilePaths.WORKING_PATH+"/"+pair.value.trim());
				}
			}

			this.outputData = parseFetchData(this.fetchData);

			return (connectionDetails != null &&
					prepareData.size() > 0 || fetchData.size() > 0) &&
					(outputData.size() > 0 && IOisOK);
		}

		return false;
	}

	/**
	 * Reads one query from the SQLfile.
	 * @param reader - the buffered reader of the input SQL file
	 * @throws IOException if the file does not exist or in use
	 * @return - a SQL statement
	 */
	public String readQuery(BufferedReader reader) throws IOException {

		List<String> sqlStatement = new ArrayList<String>();
		String line = reader.readLine();
		String sql = null;
		while (line != null){
			//check if not comment line or empty
			if (!line.startsWith("--") && !line.startsWith("/*") && !line.equals("")){
				sqlStatement.add(line);
				//check if we reached the end of a SQL statement
				if (line.trim().endsWith(";")) {
					sql = StringUtilities.join(sqlStatement, "\n").trim();
					sql = sql.replaceAll(";$", "");
					if (!sql.equals(""))
						break;
				}
			}

			//update line
			line = reader.readLine();
		}

		return sql;
	}

	/**
	 * Will parse the input list of SQL statements, fetching data and
	 * their respective output file names. The mapping is considered one to one.
	 * If a list entry does not have two elements, a warning message is raised
	 * and the entry omitted.
	 * @param fetchData - the list of queries for fetching data
	 * @return - the data ready to be output
	 */
	private HashMap<String, String> parseFetchData(List<String> fetchData){
		HashMap<String, String> outputData = new HashMap<String, String>();
		if (fetchData == null || fetchData.isEmpty())
			Logging.add("There are no data fetching files declared", Logging.HINT);

		for (String s : fetchData){
			String[] split = s.split(";");
			if (split.length < 2){
				Logging.add("Missing info for the data fetching: "+s+
						" check if both input and output file are specified.", Logging.ERROR);
				IOisOK = false;
			}
			split[1] = FilePaths.WORKING_PATH+"/"+split[1].trim();
			outputData.put(split[0].trim(), split[1]);
		}

		return outputData;
	}

	/**
	 * Will retrieve and output to CSV file the results of the sqlStatement.
	 * If a file with the same name as outputFileName is present in the working folder,
	 * it will be overwritten.
	 * @param connection - the active connection to the database
	 * @param sqlStatement - the SQL statement to be executed
	 * @param outputFileName - the name of the file which will contain the output
	 * @param isFirstQuery - true if it is the first query from a SQL file. Needed to know if appending to file or not
	 */
	private void fetchResults(Connection connection, String sqlStatement, String outputFileName, boolean isFirstQuery){
		try {
			Logging.add("Fetching results and output to "+outputFileName, Logging.HINT, true);

			File f = new File(outputFileName);
			if(f.exists() && isFirstQuery)
				f.delete();

			Statement statement = connection.createStatement(ResultSet.TYPE_FORWARD_ONLY, ResultSet.CONCUR_READ_ONLY);
			statement.setFetchSize(this.connection.chunkSize); // if not specified, the entire table is loaded first in memory

			ResultSet resultSet = statement.executeQuery(sqlStatement);
			ResultSetMetaData metaData = resultSet.getMetaData();

			WriteCSVFile out = new WriteCSVFile(outputFileName);
			List<String> header = new ArrayList<String>();
			for (int i = 1; i < metaData.getColumnCount() + 1; i++) {
				String columnName = metaData.getColumnName(i);
				header.add(columnName);
			}

			out.write(header);

			while (resultSet.next()) {
				List<String> row = new ArrayList<String>();
				for (int i = 1; i < metaData.getColumnCount() + 1; i++)
					row.add(resultSet.getString(i));
				out.write(row);
			}
			out.close();
		} catch (SQLException e) {
			Logging.add("SQL statement failed:" + sqlStatement, Logging.ERROR);
			Logging.outputStackTrace(e);
		}
	}

	/**
	 * Displays the errorMessage either on the console or in the GUI.
	 * @param errorMessage - the message to be displayed
	 */
	private void displayError(String errorMessage){
		if (noGUI)
			System.out.println(errorMessage);
		else
			Logging.add(errorMessage, Logging.ERROR);
		Jerboa.stop();
	}

}
