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
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Set;

import org.apache.commons.collections.Bag;
import org.apache.commons.lang3.text.StrBuilder;
import org.erasmusmc.jerboa.utilities.Item;
import org.erasmusmc.jerboa.utilities.stats.HistogramStats;

/**
 * TODO
 * @author PR
 *
 */
public class ItemList implements Iterable<Item> {
	
	private ArrayList<Item> itemList;
	
	//Use the label or the description in toString
	private boolean useLabel; 
	
	//parameter validation
	private int nrParameters;
	
	public ItemList(boolean useLabel,int nrParameters){
		itemList = new ArrayList<Item>();
		this.useLabel = useLabel;
		this.nrParameters = nrParameters;
	}
	
	/* 
	 * Constructor defining only the number of mandatory parameters
	 */
	public ItemList(int nrParameters){
		itemList = new ArrayList<Item>();
		this.useLabel = false;
		this.nrParameters = nrParameters;
	}
	
	/**
	 * Parses all ";" separated definition strings from the script.
	 * The minimal parts and order should be:
	 * 
	 * Lookup; Label; Description; 
	 * 
	 * Any additional fields are added to the parameters list of the Item 
	 *
	 * If Label is empty the Lookup will be used as label
	 * 
	 * Example: "MI;;Myocardial Infarction" -> label = MI;parameters is null
	 * 			
	 * @param items - list of items to be parsed
	 */
	public void parse(List<String> items){
		
		for (String item: items) {
			String[] itemSplit = item.split(";");
			//check parameters
			if (itemSplit.length<nrParameters+3){
				throw new InvalidParameterException(itemSplit[0] + " is not completely defined (" +(nrParameters+3)+" fields necessary) check script");
			}

			//parse mandatory parameters
			Item newItem = new Item();
			newItem.addLookup(itemSplit[0].toUpperCase());
			if (!itemSplit[1].isEmpty())
				newItem.setLabel(itemSplit[1]);
			else
				newItem.setLabel(itemSplit[0]);

			newItem.setUseLabel(useLabel);

			newItem.setDescription(itemSplit[2]);

			//parse the remaining parameters
			if (itemSplit.length>3){
				for (int i=3;i<itemSplit.length;i++){
					newItem.addParameter(itemSplit[i]);				
				}
			}	
			
			itemList.add(newItem);		
		}
	}
	
	/**
	 * Parses all ";" separated definition strings from the script.
	 * The order should be:
	 * 
	 * Lookup; Label; Description; Value
	 * 
	 * If Label is empty the Lookup will be used as label	
	 * 
	 * If the value is missing it is treated as a continuous valued item
	 * 
	 * 
	 * Example: "Smoking;Smoking;Smoking Status;CURRENT,PAST,NEVER" -> item is created per value 
	 * 			"BMI;BMI index;Body Mass Index -> one continuous valued item is created
	 * 			
	 * @param items - the items to be parsed
	 * @param useLabel - use the label or the description for this list
	 * @param nrParameters - used for to perform parsing checkon the extra parameters
	 * @return itemList
	 */
	public void parseWithValue(List<String> items){

		for (String item: items) {
			String[] itemSplit = item.split(";");
			//check parameters

			if (itemSplit.length<nrParameters+4){
				throw new InvalidParameterException(itemSplit[0] + " is not completely defined (" +(nrParameters+4)+" fields necessary) check script");
			}

			String[] valueSplit = itemSplit[3].split(",");

			for (String value: valueSplit){
				Item newItem = new Item();
				newItem.addLookup(itemSplit[0]);

				if (!itemSplit[1].isEmpty())
					newItem.setLabel(itemSplit[1]);
				else
					newItem.setLabel(itemSplit[0]);

				newItem.setUseLabel(useLabel);

				newItem.setDescription(itemSplit[2]);

				//parse the remaining parameters
				if (itemSplit.length>4){
					for (int i=4;i<itemSplit.length;i++){
						newItem.addParameter(itemSplit[i]);				
					}
				}	

				newItem.setValue(value);
				itemList.add(newItem);		
			}
		}
	}

	
	/**
	 * Parses a ; separated string to an Item containing the parameters and lookup
	 * label will bet set equal to the lookup
	 * for example: ATC;windowStart;windowEnd
	 * 
	 * @return itemList
	 */
	public void parseParamList(List<String> items){
		for (String item: items) {
			String[] itemSplit = item.split(";");
			//check parameters
			if (itemSplit.length<nrParameters){
				throw new InvalidParameterException(itemSplit[0] + " is not completely defined (" +(nrParameters)+" fields necessary)");
			}

			//parse mandatory parameters
			Item newItem = new Item();
			newItem.addLookup(itemSplit[0]);
			newItem.setLabel(itemSplit[0]);

			//parse the remaining parameters\
			for (int i=0;i<itemSplit.length;i++){
				newItem.addParameter(itemSplit[i]);				
			}
			itemList.add(newItem);		
		}
	}

	/**
	 * Adds a new item to the list
	 * @param name - lookup value
	 * @param label
	 * @param description 
	 * @param parameters 
	 */
	public void add(String name, String label, String description, List<String> parameters){
		Item newItem = new Item();
		newItem.addLookup(name);
		newItem.setLabel(label);
		newItem.setDescription(description);
		newItem.setUseLabel(useLabel);
		if (parameters != null && !parameters.isEmpty())
			newItem.setParameters(parameters);
		itemList.add(newItem);
	}
	
	/**
	 * Adds a new item to the list
	 * @param name
	 * @param label 
	 * @param description 
	 * @param value 
	 * @param parameters  
	 */
	public void addWithValue(String name, String label, String description, String Value, List<String> parameters){
		Item newItem = new Item();
		newItem.addLookup(name);
		newItem.setLabel(label);
		newItem.setDescription(description);
		newItem.setUseLabel(useLabel);
		if (parameters != null && !parameters.isEmpty())
			newItem.setParameters(parameters);
		newItem.setValue(Value);
		itemList.add(newItem);
	}
	
	/**
	 * Adds an item to the list (not cloned!)
	 * @param item
	 */
	public void add(Item item){
		itemList.add(item);
	}
	
	/**
	 * Removes an item from the list (not cloned!)
	 * @param item
	 */
	public void remove(Item item){
		itemList.remove(item);
	}
	
	/**
	 * Will add items for all keys in the bag using 
	 * @param bag - the bag to add
	 * @param name - the name used value and name of item
	 * @param nrPatients - the total number of patients used for percentage
	 */
	public void add(Bag bag, String name, int nrPatients){
		for (Object key : bag.uniqueSet()){
			Item newItem = new Item();
			newItem.addLookup(name);
			newItem.setValue((String) key);
			newItem.setDescription(name);
			newItem.setCount(bag.getCount(key));
			newItem.setTotal(nrPatients);
			newItem.setUseLabel(useLabel);
			itemList.add(newItem);
		}
	}
	
	/**
	 * Will add items for all keys in a MultiKeyBag.
	 * The multikey should be of the format ["Name","Value"] 
	 * @param bag - the bag to add
	 * @param nrPatients - the total number of patients used for percentage
	 */
	public void add(MultiKeyBag bag, int nrPatients){
		//TODO: check only size two allowed maybe switch to array?
		for (ExtendedMultiKey key : bag.getSortedKeySet(0)){
			Item newItem = new Item();
			newItem.addLookup((String) key.getKey(0));
			newItem.setValue((String) key.getKey(1));
			newItem.setDescription((String) key.getKey(0));
			newItem.setCount(bag.getCount(key));
			newItem.setTotal(nrPatients); 
			newItem.setUseLabel(useLabel);
			itemList.add(newItem);
		}
	}
	
	/**
	 * Checks if there are multikeys [Description, Value] that are not
	 * yet in the list and adds them. Can be used to for example add
	 * items that were not defined in the script but are in the input file.
	 * The lookup for these items will be description+" OTHER"
	 * 
	 * @param bag - MultiKeyBag with keys of size two [Description,Value]
	 * @param replace - first erase the itemList
	 * @throws - IllegalArgumentException if size of a key is not woe
	 */
	public void addMissingItems(MultiKeyBag bag, boolean replace){
		Set<ExtendedMultiKey> keySet = bag.getUniqueSet();

		if (replace)
			itemList.clear();
		
		for (ExtendedMultiKey key : keySet){
			if (key.size()!=2){
				throw new IllegalArgumentException("size of the bag should be 2 [Description Value]");
			}
			Object description = key.getKey(0);
			Object value = key.getKey(1);
			boolean found = false;
			for (Item item: itemList){
				if (item.getDescription().equals(description) &&
					item.getValue().equals(value)){
					found = true;
					break;
				}
			}

			if (!found){
				Item item = new Item();
				item.addLookup((String) description+" OTHER");	
				if (value instanceof String)
				    item.setValue((String) value);
				if (value instanceof Double)
				    item.setValue(Double.toString((Double) value));
	
				item.setDescription((String) description);
				item.setLabel((String) description);
				item.setUseLabel(useLabel);
				
				itemList.add(item);
			}
		}
	}
	
	public void addResults(MultiKeyBag bag, ExtendedMultiKey key){
		for (Item item : this.itemList){
			HistogramStats bagStats= bag.getHistogramStats(key);
			item.setCount(bagStats.getCount());
			item.setMedian(bagStats.getMedian());
			item.setMean(bagStats.getMean());
			item.setSD(bagStats.getStdDev());
			item.setMin(bagStats.getMin());
			item.setMax(bagStats.getMax());		
		}
	}
	
	public void clear(){
		this.itemList.clear();
	}
	
	
	public void addUnknownItem(){
		this.add("UNKNOWN", "UNKNOWN", "UNKNOWN", null);
	}
	
	public void addNoneItem(){
		this.add("NONE", "NONE", "NONE", null);
	}
	
	public void addUndefinedItem(){
		this.add("UNDEFINED", "UNDEFINED", "UNDEFINED", null);
	}

	public List<Item> getItemList(){
		return itemList;
	}
	
	public int size(){
		return itemList.size();
	}
	
	/**
	 * Sort the itemList by label of description dependent on useLabel setting
	 */
	public void sort(){
		Collections.sort(itemList);
	}
	
	/**
	 * Return the item at index. Null if index is out of bound
	 * @param index - the index
	 * @return - Item
	 */
	public Item get(int index){
		if (index>=0 && index<itemList.size()){
			return itemList.get(index);
		} else
			return null;
	}
	
	/**
	 * Returns a string representation
	 */
	public String toString(){
		StringBuilder result = new StringBuilder("\n");
		for (Item item : itemList){
			result.append(item.toString()+"\n");
		}
		return result.toString();
	}
	
	/**
	 * Outputs an itemList to file
	 * @param header - the header of the table
	 * @param continues - determines if only the continuous or non-continuous should
	 * 					  be added
	 * @param filename - name of to the file to append to
	 * @return true if the file was writable
	 */
	public boolean itemListToFile(String header, boolean continuous, String fileName){
		if (itemList.size()>0){
			StrBuilder out = new StrBuilder();

			//put header
			if (!header.isEmpty()){
				out.appendNewLine();
				out.appendln(header);
				out.appendNewLine();
				if (continuous)
					out.appendln("----------"+"\tN\tmean\tsd\tCIlow\tCIup\tmedian\tP25\tP75\tmin\tmax");
				else
					out.appendln("----------"+"\tn (%)\tCI");
			}	
			
			for (Item item : itemList){
				if ((continuous && item.getValue().equals("CONTINUOUS")) || ( 
				   !continuous && !item.getValue().equals("CONTINUOUS")))
				out.append(item.toString()+System.lineSeparator());
			}		
			return FileUtilities.writeStringToFile(fileName,out.toString(),true);
		}
		return true;
	}
	
	/**
	 * Outputs an itemList to file
	 * @param header - the header of the table
	 * @param continues - determines if only the continuous or non-continuous should
	 * 					  be added
	 * @param precisionFormat - determines the precision used to write the numbers 
	 * @param filename - name of to the file to append to
	 * @return true if the file was writable
	 */
	public boolean itemListToFile(String header, boolean continuous, DecimalFormat precisionFormat, String fileName){
		if (itemList.size()>0){
			StrBuilder out = new StrBuilder();

			//put header
			if (!header.isEmpty()){
				out.appendNewLine();
				out.appendln(header);
				out.appendNewLine();
				if (continuous)
					out.appendln("----------"+"\tN\tmean\tsd\tCIlow\tCIup\tmedian\tP25\tP75\tmin\tmax");
				else
					out.appendln("----------"+"\tn (%)\tCI");
			}	
			
			for (Item item : itemList){
				if ((continuous && item.getValue().equals("CONTINUOUS")) || ( 
				   !continuous && !item.getValue().equals("CONTINUOUS")))
				out.appendln(item.toString(precisionFormat));
			}		
			return FileUtilities.writeStringToFile(fileName,out.toString(),true);
		}
		return true;
	}


	public Iterator<Item> iterator() {
		return new ItemIterator(this.itemList);
	}

	/**
	 * Inner Class for the item Iterator. Remove not implemented
	 * @author PR
	 *e
	 */
	private class ItemIterator implements Iterator<Item> {
		private ArrayList<Item> itemList;
		private int index;

		public ItemIterator(ArrayList<Item> itemList) {
			index = 0;
			this.itemList = itemList;
		}

		public boolean hasNext() {
			return !(itemList.size() == index);
		}

		public Item next() {
			if(this.hasNext()) {
				return itemList.get(index++);
			}
			throw new NoSuchElementException("There are no elements size = " + itemList.size());
		}

		public void remove() {
			throw new UnsupportedOperationException();
		}
	}
	
	/**
	 * Returns a comma-delimited set of all unique labels
	 * @param prefix         - will be added to beginning of the labels for example "Hist".
	 * @param addUnitColumn  - when true each label is followed by a unit label.
	 * @return
	 */
	public DelimitedStringBuilder getLabels(String prefix, boolean addUnitColumn){

		DelimitedStringBuilder labels = new DelimitedStringBuilder();
		HashSet<String> uniqueLabels = new HashSet<String>();
		for (Item item : itemList){
			if (!uniqueLabels.contains(item.getLabel())){
				labels.append(prefix+item.getLabel());
				if (addUnitColumn) {
					labels.append(prefix+"UNIT"+item.getLabel());
				}
				uniqueLabels.add(item.getLabel());
			}
		}
		return labels;
	}
	
	/**
	 * Returns a comma-delimited set of all unique labels/
	 * Two items will be returned Year<Label> and Mont<Label>
	 * @return
	 */
	public DelimitedStringBuilder getExtendedLabels(ArrayList<String> extLabels){

		DelimitedStringBuilder labels = new DelimitedStringBuilder();
		HashSet<String> uniqueLabels = new HashSet<String>();
		for (Item item : itemList){
			if (!uniqueLabels.contains(item.getLabel())){
				for (String extLabel : extLabels){
					labels.append(extLabel+item.getLabel());
				}
				uniqueLabels.add(item.getLabel());
			}
		}
		return labels;
	}

}
