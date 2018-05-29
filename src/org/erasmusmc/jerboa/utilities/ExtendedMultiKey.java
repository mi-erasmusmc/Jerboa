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
 * $Rev:: 4809              $:  Revision of last commit                                   *
 * $Author:: root           $:  Author of last commit                                     *
 * $Date:: 					$:  Date and time (CET) of last commit						  *
 ******************************************************************************************/
package org.erasmusmc.jerboa.utilities;

import java.util.ArrayList;
import java.util.List;

import org.apache.commons.collections.keyvalue.MultiKey;
import org.erasmusmc.jerboa.Jerboa;

/**
 * This class enhances the functionality of the MultiKey object from the Apache library.
 * A maximum of five key components are allowed. If this maximum size is to be modified,
 * the propagation should be closely followed at least in the MultiKeybag and MultiKeyMap classes.
 *
 * @see org.erasmusmc.jerboa.utilities.MultiKeyBag
 * @see org.erasmusmc.jerboa.utilities.MultiKeyMap
 *
 *
 * @author PR {@literal &} MG
 *
 */
public class ExtendedMultiKey extends MultiKey{


	private static final long serialVersionUID = 1L;
	public static final short MAX_COMPONENTS = 5;

	//CONSTRUCTORS - up to 5 keys

	public ExtendedMultiKey(Object key1, Object key2) {
		super(key1, key2);
	}

	public ExtendedMultiKey(Object key1, Object key2, Object key3) {
		super(key1, key2, key3);
	}

	public ExtendedMultiKey(Object key1, Object key2, Object key3, Object key4) {
		super(key1, key2, key3, key4);
	}

	public ExtendedMultiKey(Object key1, Object key2, Object key3, Object key4,
			Object key5) {
		super(key1, key2, key3, key4, key5);
	}

	public ExtendedMultiKey(Object[] keys){
		super(keys);
	}

	/** Checks if the subSetKey is a subset of this MultiKey.
	 * Wild card strings are allowed.
	 * @param subSetKey - the subSetKey to find, the order matters.
	 * @return - true if subSetKey represents a subset of this key.
	 */
    public boolean isSubset(ExtendedMultiKey subSetKey){
    	boolean keyFound = true;
		int keyIndex = 0;
		while (keyFound && (keyIndex < subSetKey.size())) {
			keyFound = subSetKey.getKey(keyIndex) instanceof Wildcard ||
					this.getKey(keyIndex).equals(subSetKey.getKey(keyIndex));
			keyIndex++;
		}

    	return keyFound;
    }

    /**
     * Returns the first index at which the key component
     * of the multiKey represents a wild card. If no wild card
     * is specified in this multi key, then -1 is returned.
     * @return - the index of the first wild card in this multi key
     */
    public int getWildCardIndex(){
    	int index = -1;
    	if (this != null && this.size() > 1)
    		for (Object key : this.getKeys())
    			if (key instanceof Wildcard)
    				return getIndex(key);
    	return index;
    }

    /**
     * Creates a list of the wild card indices found in the multikey.
     * @return - a list of wildcard indices; the list can be empty but not null
     */
    public List<Integer> getWildCardIndices(){
    	List<Integer> indices = new ArrayList<Integer>();
    	if (this != null && this.size() > 1){
    		int i=0;
    		for (Object key : this.getKeys()){
    			if (key instanceof Wildcard)
    				indices.add(i);
    			i++;
    		}
    	}

    	return indices;
    }

    /**
     * Returns the number of wild cards.
     * @return - number of wild cards.
     */
    public int getNrWildcards(){
    	int count = 0;
    	if (this != null && this.size() > 1)
    		for (Object key : this.getKeys())
    			if (key instanceof Wildcard)
    				count++;;
    	return count;
    }

    /**
     * Sets a key component at a specified index in this multi key
     * with the keyComponent object. Note that the type of the
     * keyComponent is cast to the object previously found at that
     * index in the key.
     * @param index - the index of the key component to be set
     * @param keyComponent - the object replacing the key component
     * @return - the new multi key
     */
    public ExtendedMultiKey setKeyComponent(int index, Object keyComponent){
    	if ((this != null && this.size() > 1)
    			&& index < this.size()){
    		Object[] newKey = this.getKeys();
    		@SuppressWarnings("rawtypes")
    			Class cls = this.getKey(index) instanceof Wildcard ?
    					((Wildcard)this.getKey(index)).type :
    						this.getKey(index).getClass();
 			try{
 				newKey[index] = keyComponent instanceof Wildcard ? keyComponent : cls.cast(keyComponent);
    		}catch (ClassCastException e){
    				Logging.add("Unable to cast from " +cls.getName()+" to "+
    					keyComponent.getClass().getName(), Logging.ERROR);
    				Logging.outputStackTrace(e);
    				Jerboa.stop(true);
    		}
    		return new ExtendedMultiKey(newKey);
   		}

    	return this;
    }

    /**
     * Return a new ExtendedMultiKey with the key Object added at the end.
     * Throws exception if the new multi key would have more than 5 components.
     * @param key - the new key component to be added
     * @return - the new longer multikey
     */
    public ExtendedMultiKey add(Object key){
    	ExtendedMultiKey newKey;
      	if (this != null && this.size() > 1 && this.size() <= MAX_COMPONENTS-1){
      		switch (this.size()) {
      			case 1: newKey =  new ExtendedMultiKey(this.getKeys()[0],key);return newKey;
     			case 2: newKey =  new ExtendedMultiKey(this.getKeys()[0],this.getKeys()[1],key);return newKey;
     			case 3: newKey =  new ExtendedMultiKey(this.getKeys()[0],this.getKeys()[1],this.getKeys()[2],key);return newKey;
    			case 4: newKey =  new ExtendedMultiKey(this.getKeys()[0],this.getKeys()[1],this.getKeys()[2],this.getKeys()[3],key); return newKey;
      		}
      	}

        if (this.size() >= MAX_COMPONENTS) throw new IllegalArgumentException("Objects cannot be added to an ExtendedMultiKey that already has 5 components");

      	return null;

    }

    /** Checks if the last key is an integer or a double.
     * @return boolean - true if last key is an integer or a double; false otherwise
     */
    public boolean isLastNumeric(){
    	if (this.getKey(this.size()-1) instanceof Integer ||
    			this.getKey(this.size()-1) instanceof Double ){
    		return true;
    	} else

        return false;
    }

	/**
	 * Retrieves the index of the component key in multiKey.
	 * @param key - the key component of interest
	 * @return - the index of key in multiKey
	 */
	public int getIndex(Object key){
		if (key != null){
			for (int i = 0; i < this.size(); i++)
				if (this.getKey(i).equals(key))
					return i;
		}

		return -1;
	}

	/**
	 * Formatted to string method.
	 * @return - a comma-separated string representation of the MultiKey
	 */
	public String toStringFormat(){
		DelimitedStringBuilder result = new DelimitedStringBuilder();
		if (this.size() != 0){
			for (int i = 0; i < this.size(); i++)
				result.append(this.getKey(i).toString());
			return result.toString();
		} else
			return "";
	}

}
