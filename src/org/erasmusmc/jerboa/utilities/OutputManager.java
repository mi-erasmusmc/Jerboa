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
 * Author: Mees Mosseveld (MM) - department of Medical Informatics						  *
 * 																						  *
 * $Rev::            	    $:  Revision of last commit                                	  *
 * $Author::				$:  Author of last commit                                     *
 * $Date:: 					$:  Date and time (CET) of last commit						  *
 ******************************************************************************************/
package org.erasmusmc.jerboa.utilities;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.commons.lang3.text.StrBuilder;
import org.erasmusmc.jerboa.Jerboa;

/**
 * Handles the output from the modules/filters across the application.
 *
 * @author bmosseveld
 *
 */
public class OutputManager {
	private Map<String, Long> thresholds = new HashMap<String, Long>();
	private Map<String, StrBuilder > buffers = new HashMap<String, StrBuilder >();

	// Output storage for unit tests
	private Map<String, List<String>> output = new HashMap<String, List<String>>();

	private long DEFAULT_FLUSH_THRESHOLD = 1024 * 1024; //1 MB
	private long OVERALL_FLUSH_THRESHOLD = 10 * 1024 * 1024; //10 MB

	/**
	 * Will add a file to the list of output files to be handled.
	 * @param fileName - the name of the output file to be added
	 * @return - true if the file was added successfully and opened for writing
	 */
	public boolean addFile(String fileName) {
		boolean result = false;
		if (!Jerboa.unitTest) {
			if (buffers.get(fileName) == null) {
				newFile(fileName);
				thresholds.put(fileName, DEFAULT_FLUSH_THRESHOLD);
				result = true;
			}
		}
		else {
			List<String> fileOutput = new ArrayList<String>();
			fileOutput.add("");
			output.put(fileName, fileOutput);
			result = true;
		}

		return result;
	}

	/**
	 * Will add a file to the list of output files to be handled.
	 * @param fileName - the name of the output file to be added
	 * @param thresholdMB - a custom buffer size for this output file
	 * @return - true if the file was added successfully and opened for writing
	 */
	public boolean addFile(String fileName, long thresholdMB) {
		boolean result = false;
		if (!Jerboa.unitTest) {
			if (buffers.get(fileName) == null) {
				newFile(fileName);
				thresholds.put(fileName, thresholdMB * 1024 * 1024);
				result = true;
			}
		}
		else {
			List<String> fileOutput = new ArrayList<String>();
			fileOutput.add("");
			output.put(fileName, fileOutput);
			result = true;
		}
		return result;
	}

	/**
	 * Checks if fileName is among the elements in buffers.
	 * @param fileName - the name of the file
	 * @return - true if the file already exists in the buffers; false otherwise
	 */
	public boolean hasFile(String fileName) {
		boolean result = false;
		if (!Jerboa.unitTest) {
			result = (this.buffers.get(fileName) != null);
		}
		else {
			result = (this.output.get(fileName) != null);
		}
		return result;
	}

	/**
	 * Writes data to fileName and adds an end of line separator.
	 * It can append the data to fileName or replace fileName with a new file.
	 * @param fileName - the name of the output file
	 * @param data - the data to be appended
	 * @param append - true if data should be appended to file; false otherwise
	 */
	public void writeln(String fileName, String data, boolean append) {
		write(fileName, data, true, append);
	}

	/**
	 * Writes data to fileName. It can open fileName in append mode or not.
	 * @param fileName - the name of the output file
	 * @param data - the data to be appended
	 * @param append - true if data should be appended to file; false otherwise
	 */
	public void write(String fileName, String data, boolean append) {
		write(fileName, data, false, append);
	}

	/**
	 * Writes data to fileName and optionally adds an end of line separator.
	 * It can append the data to fileName or replace fileName with a new file.
	 * @param fileName - the name of the output file
	 * @param data - the data to be appended
	 * @param newLine - true if an end of line character should be added; falso otherwise
	 * @param append - true if data should be appended to file; false otherwise
	 */
	private void write(String fileName, String data, boolean newLine, boolean append) {
		if (!Jerboa.unitTest) {
			if (!append) {
				newFile(fileName);
			}
			StrBuilder buffer = buffers.get(fileName);
			if (newLine) {
				buffer.appendln(data);
			}
			else {
				buffer.append(data);
			}
			if (getOverallBufferSize() > OVERALL_FLUSH_THRESHOLD) {
				flushAll();
			}
			else if (buffer.length() > thresholds.get(fileName)) {
				flush(fileName);
			}
		}
		else {
			List<String> fileOutput;
			if (!append) {
				fileOutput = new ArrayList<String>();
				fileOutput.add("");
				output.put(fileName, fileOutput);
			}
			fileOutput = output.get(fileName);
			fileOutput.set(fileOutput.size() - 1, fileOutput.get(fileOutput.size() - 1) + data);
			if (newLine) {
				fileOutput.add("");
			}
		}
	}

	/**
	 * Returns the buffer cumulative buffer size of all buffers used.
	 * @return - the sum of all buffers used by all output files
	 */
	private long getOverallBufferSize() {
		long overallSize = 0;
		if (!Jerboa.unitTest) {
			for (String fileName : buffers.keySet()) {
				overallSize += buffers.get(fileName).length();
			}
		}

		return overallSize;
	}

	/**
	 * Closes all buffers that are assigned to the output files.
	 * @return - true if all buffers were successfully closed
	 */
	public boolean closeAll() {
		boolean result = false;
		if (!Jerboa.unitTest) {
			for (String fileName : buffers.keySet()) {
				if (buffers.get(fileName) != null) {
					flush(fileName);
					result &= true;
				}else
					result &= false;
			}
			buffers.clear();
			thresholds.clear();
		}
		return result;
	}

	/**
	 * Closes the output buffers assigned for fileName.
	 * @param fileName - the name of the file to close
	 * @return - true if the buffer was successfully closed; false otherwise
	 */
	public boolean closeFile(String fileName) {
		boolean result = false;
		if (!Jerboa.unitTest) {
			if (buffers.get(fileName) != null) {
				flush(fileName);
				buffers.remove(fileName);
				thresholds.remove(fileName);
				result = true;
			}
		}

		return result;
	}

	/**
	 * Writes the contents of the output buffer assigned to fileName to file.
	 * @param fileName - the output file to be flushed
	 */
	public void flush(String fileName) {
		if (!Jerboa.unitTest) {
			StrBuilder buffer = buffers.get(fileName);
			if (buffer != null) {
				//if (logDebugInfo > 0) Logging.add(" -> Flush" + Parameters.NEW_LINE);
				FileUtilities.outputData(fileName, buffer, true);
			}
			newBuffer(fileName);
		}
	}

	/**
	 * Writes the contents of all the output buffers to their assigned files.
	 */
	public void flushAll() {
		if (!Jerboa.unitTest) {
			for (String fileName: buffers.keySet()) {
				if (buffers.get(fileName) != null) {
					flush(fileName);
				}
			}
		}
	}

	/**
	 * Gets the output of a file when in unit test.
	 */
	public List<String> getFile(String fileName) {
		return Jerboa.unitTest ? output.get(fileName) : null;
	}

	/**
	 * Adds a new output file to the list of output files.
	 * @param fileName
	 */
	private void newFile(String fileName) {
		newBuffer(fileName);
		//will automatically create any sub folders in the filename
		FileUtilities.writeStringToFile(fileName, buffers.get(fileName).toString(), false);
	}

	/**
	 * Adds a new buffer to the list of output buffers.
	 * @param fileName - the file assigned to the output buffer
	 */
	private void newBuffer(String fileName) {
		buffers.put(fileName, new StrBuilder());
	}

}
