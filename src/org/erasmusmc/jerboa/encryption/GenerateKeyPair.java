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

package org.erasmusmc.jerboa.encryption;

import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.ObjectOutputStream;
import java.security.Key;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.NoSuchAlgorithmException;

/**
 * Class for generating public and private encryption key pairs.
 *
 * @author MS
 *
 */
public class GenerateKeyPair {

	public static void main(String[] args) throws NoSuchAlgorithmException {
		KeyPair keyPair = generateKeyPair();
		saveKey("D:/Work/Erasmus/Encryption/src/org/erasmusmc/encryption/resources/Public.key", keyPair.getPublic());
		saveKey("D:/Work/Erasmus/Decryption/src/org/erasmusmc/decryption/resources/Private.key", keyPair.getPrivate());
		System.out.println("Done!");
	}

	/**
	 * Generates a pair (private and public) of encryption keys using the RSA algorithm.
	 * @return the pair of encryption keys
	 */
	public static KeyPair generateKeyPair(){
		KeyPair result = null;
		KeyPairGenerator keygen;
		try {
			keygen = KeyPairGenerator.getInstance("RSA");
			keygen.initialize(4096);
			result = keygen.generateKeyPair();
		} catch (NoSuchAlgorithmException e) {
			e.printStackTrace();
		}

		return result;
	}

	/**
	 * Saves the encryption key to the file filename.
	 * @param filename - the name of the output file
	 * @param key - the encryption key to be saved
	 */
	public static void saveKey(String filename, Key key) {
		try {
			FileOutputStream binFile = new FileOutputStream(filename);
			try {
				ObjectOutputStream out = new ObjectOutputStream(binFile);
				out.writeObject(key);
				out.close();

			} catch (IOException e) {
				e.printStackTrace();
			}
		} catch (FileNotFoundException e) {
			e.printStackTrace();
		}
	}
}
