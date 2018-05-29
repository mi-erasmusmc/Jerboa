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
 * $Rev:: 4804              $:  Revision of last commit                                   *
 * $Author:: root           $:  Author of last commit                                     *
 * $Date:: 					$:  Date and time (CET) of last commit						  *
 ******************************************************************************************/
package org.erasmusmc.jerboa.utilities;

import java.io.File;
import java.io.IOException;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.erasmusmc.jerboa.Jerboa;
//import oracle.jdbc.pool.OracleDataSource;

/**
 * This class creates a database connection via the JDBC driver. It is able to instantiate
 * a connection to a PostgreSQL, MySQL, MSSQL or Oracle database based on connection details retrieved from a file.
 *
 * @author MS {@literal &} MG
 *
 */
public class DBConnection {

	/**
	 * Specifies the type of DB connections. Options are:
	 * <ul>
	 *   <li>ORACLE</li>
	 *   <li>MYSQL</li>
	 *   <li>MSSQL</li>
	 *   <li>POSTGRESQL</li>
	 * </ul>
	 * Default = MYSQL
	 */
	private String connectionType = "MYSQL";

	/**
	 * Specifies the database name
	 */
	private String dbName;

	/**
	 * Specifies the DB user name
	 */
	private String user;

	/**
	 * Specifies the DB password
	 */
	private String password;

	/**
	 * Specifies the DB schema used.<BR>
	 * default = ""
	 */
	public String schema = "";

	/**
	 * Name of the database + server
	 */
	private String server;

	/**
	 * User domain (for MSSQL servers)
	 */
	private String domain;

	/**
	 * Flag to know if secure connection should be used (only PostgreSQL).
	 */
	private boolean ssl;

	/**
	 * The name of the file containing the DB connection details
	 */
	private String databaseConnectionDetails;

	/**
	 * The setting for the fetch size. If not specified or zero
	 * then all table will be read in memory first.
	 */
	public int chunkSize = 1000;

	/**
	 * Allows to rollback in the database.
	 */
	public boolean autoCommit = false;

	//CONSTRUCTOR
	public DBConnection(String connectionDetails){
		this.databaseConnectionDetails = connectionDetails;
	}

	/**
	 * Will create the actual connection to the database.
	 * @param stopIfDatabaseNotFound - true if the run should stop when the database
	 * is not found (an exception is raised); false otherwise
	 * @return - an SQL connection
	 * @throws IOException - if the settings file cannot be read
	 * @throws SQLException - if querying the schema fails
	 */
	protected Connection connect(boolean stopIfDatabaseNotFound) throws IOException, SQLException {

		Connection con = null;
		if (hasSettingsFile()){

			//load connection details
			loadDBSettings();

			//establish the connection depending on its type
			if (connectionType.equals("ORACLE"))
				Logging.add("Connection to ORACLE not supported.", Logging.ERROR);
			else if (connectionType.equals("MYSQL"))
				con = connectToMySQL();
			else if (connectionType.equals("MSSQL"))
				con = connectToMSSQL();
			else if (connectionType.equals("POSTGRESQL"))
				con = connectToPostgreSQL();
			else if (connectionType.equals("ORACLE"))
				con = connectToOracle();
			if (con != null && !con.isClosed())
				Logging.add("Connected to the database server");

			//let the user know if the schema was found
			if (exists(schema,con)){
				Logging.add("Found schema " + schema, Logging.HINT);
			}else if (stopIfDatabaseNotFound){
				Logging.add("Schema "+schema+" not found. Data extraction stopped.");
				Jerboa.stop(true);
			}
		}

		return con;
	}

	/**
	 * Establishes a connection to a MySQL server.
	 * @return - the SQL connection
	 */
	private Connection connectToMySQL() {
		initializeDriver("com.mysql.jdbc.Driver");
		String url = "jdbc:mysql://"+server+":3306/"+schema+"?useCursorFetch=true";
		return getConnection(url);
	}

	/**
	 * Establishes a connection to a MSSQL server.
	 * @return - the SQL connection
	 */
	private Connection connectToMSSQL() {
		initializeDriver("net.sourceforge.jtds.jdbc.Driver");
		String url = "jdbc:jtds:sqlserver://"+server+(domain.length()==0?"":";domain="+domain);
		return getConnection(url);
	}

	/**
	 * Establishes a connection to a PostgreSQL server.
	 * @return - the SQL connection
	 */
	private Connection connectToPostgreSQL(){
		initializeDriver("org.postgresql.Driver");
		//String url = "jdbc:postgresql://"+server+":5432/"+dbName+"?searchpath="+schema+"?user="+user+"&password"+password+"&ssl=true";
		//specifying the schema in the url apparently does not work with 9.3-1101-jdbc41 and 9.1 version of the driver
		String url = "jdbc:postgresql://"+server+":5432/"+dbName+"?user="+user+"&password="+password+(ssl ? "&ssl=true" : "");
		return getConnection(url);

	}

	/**
	 * Establishes a connection to an Oracle server.
	 * @return - the SQL connection
	 */
	//TODO must be tested - never used
	private Connection connectToOracle() {
		initializeDriver("oracle.jdbc.driver.OracleDriver");
		//jdbc:oracle:<drivertype>:<user>/<password>@<database>:<port>/<system identifier>
		//driver type: thin, oci, oci8, etc...
		String url = "jdbc:oracle:thin:"+user+"/"+password+"@//"+server+":1521/XE"; //XE service added by default in oracle 10q express edition
		return getConnection(url);
	}

	/**
	 * Checks if schema exists in the current established connection.
	 * @param schema - the schema of interest
	 * @param con - the existing connection
	 * @return - true if schema exists in the current connection; false otherwise
	 */
	protected boolean exists(String schema, Connection con) {
		String sql;
		if (connectionType.equals("MYSQL"))
			sql = "SELECT SCHEMA_NAME FROM INFORMATION_SCHEMA.SCHEMATA WHERE SCHEMA_NAME = '"+schema+"';";
		else if (connectionType.equals("POSTGRESQL"))
			sql = "set search_path to '"+schema+"';"; //or
		else if (connectionType.equals("ORACLE"))
			sql = "ALTER SESSION SET CURRENT_SCHEMA="+schema;
		else
			sql = "SELECT username FROM all_users WHERE username = '"+schema+"'";

		try {
			Statement statement = con.createStatement(ResultSet.TYPE_FORWARD_ONLY, ResultSet.CONCUR_READ_ONLY);
			if (connectionType.equals("POSTGRESQL"))
				return statement.executeUpdate(sql) == 0;
			else
				return ((ResultSet)statement.executeQuery(sql)).next();
		} catch (SQLException e) {
			Logging.add("SQL query failed: " + sql, Logging.ERROR);
			Logging.outputStackTrace(e);
		}

		return false;
	}

	/**
	 * Loads the connection details from an input file databaseConnectionDetails.
	 * @throws IOException - if the file is not found; should not occur
	 */
	private void loadDBSettings() throws IOException {

		Map<String, String> settings = new HashMap<String, String>();
		List<String> lines = FileUtilities.readLines(new File(databaseConnectionDetails));
		if (lines != null && lines.size() > 0){
			for (String line : lines){
				int indexOfHash = line.indexOf('#');
				if (indexOfHash != -1)
					line = line.substring(0,indexOfHash);

				int indexOfColon = line.indexOf(':');
				if (indexOfColon == -1)
					indexOfColon = line.indexOf('=');

				if (indexOfColon != -1)
					settings.put(line.substring(0,indexOfColon).trim().toLowerCase(), line.substring(indexOfColon+1).trim());
			}
		}
		connectionType = settings.get("connectiontype");
		if (connectionType == null)
			connectionType = settings.get("data_source_type");

		dbName = settings.get("database");
		user = settings.get("user");
		password = settings.get("password");
		schema = settings.get("schema");
		server = settings.get("server");
		domain = settings.get("domain");
		ssl = (settings.get("ssl") != null && settings.get("ssl").toLowerCase().equals("true") ? true : false);
		chunkSize = (settings.get("chunk_size") != null ? Integer.valueOf(settings.get("chunk_size")) : chunkSize);
		autoCommit = (settings.get("autocommit") != null ? Boolean.valueOf(settings.get("autocommit")) : autoCommit);
	}

	/**
	 * Tries to lead the database connectivity driver.
	 * If not found, an error message is displayed and the application stops.
	 * @param driver - the path to the driver
	 */
	protected void initializeDriver(String driver){
		try {
			Class.forName(driver);
		} catch (ClassNotFoundException e) {
			Logging.add("Connection failed. Could not find the "+connectionType+" driver.", Logging.ERROR);
			Logging.outputStackTrace(e);
			Jerboa.stop(true);
		}
	}

	/**
	 * Tries to establish a connection to the database server
	 * and return it. In case the connection fails, the user is
	 * warned and null is returned.
	 * @param url - the connection url
	 * @return - the database connection; null if cannot connect
	 */
	protected Connection getConnection(String url){
		try {
			return DriverManager.getConnection(url, user, password);
		} catch (SQLException e) {
			Logging.add("Cannot connect to the database server. Please check the credentials.", Logging.ERROR);
			Logging.outputStackTrace(e);
			Jerboa.stop(true);
		}

		return null;
	}

	/**
	 * Properly closes the connection to the server.
	 * @param connection - the connection to be closed
	 */
	//TODO not used yet. Implement and test where needed
	protected void closeConnection(Connection connection){
		try {
			Logging.add("Closing connection to server", Logging.HINT);
			connection.close();
		} catch (SQLException e) {
			Logging.add("Unable to close connection to database server");
			Logging.outputStackTrace(e);
		}
	}

	/**
	 * Checks if there is a database settings file in the workspace.
	 * @return - true if the file is found; false otherwise
	 */
	public boolean hasSettingsFile() {
		if (databaseConnectionDetails != null && !databaseConnectionDetails.equals("")){
			File dbSettings = new File(databaseConnectionDetails);
			return dbSettings.exists();
		}
		return false;
	}

	/**
	 * Returns the string defining the connection type (e.g., MySQL, PostgreSQL).
	 * @return - the type of this connection
	 */
	public String getType(){
		return connectionType;
	}

}