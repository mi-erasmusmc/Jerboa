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

import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.ObjectInputStream;
import java.io.OutputStream;
import java.security.Key;
import java.security.interfaces.RSAKey;

import javax.crypto.Cipher;
import javax.crypto.CipherInputStream;
import javax.crypto.CipherOutputStream;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;

import org.erasmusmc.jerboa.config.FilePaths;

/**
 * Class for encrypting and decrypting a stream of data.
 * It makes use of RSA key pairs.
 *
 * @author MM
 *
 */
public class Encrypt {

	/**
	 * Get a stream to write an encrypted output file using a given public key.
	 * @param outputFilename - The file name of the encrypted output file.
	 * @param publicKeyName  - The name of the public key that is used for the encryption.
	 * @return               - The stream that can be used to write the encrypted file.
	 *
	 * Possible publicKeyName values:
	 *		EU-ADR (default)
	 *		PUBLIC
	 *		...to be completed
	 * As new values are added, update this function.
	 */
	public OutputStream getEncryptedStream(String outputFilename, String publicKeyName) {
		Key publicKey = null;
		CipherOutputStream encryptedStream = null;

		if (publicKeyName.equals("EU-ADR"))
			publicKey = loadKey(Encrypt.class.getResourceAsStream(FilePaths.ENCRYPTION_EU_ADR));
		else if (publicKeyName.equals("PUBLIC"))
			publicKey = loadKey(Encrypt.class.getResourceAsStream(FilePaths.ENCRYPTION_PUBLIC));

		try {
			//Step 1: generate random symmetric key (AES algorithm):
			KeyGenerator kgen = KeyGenerator.getInstance("AES");
			kgen.init(128);
			SecretKey aesKey = kgen.generateKey();

			//Step 2: Create encoding cipher using public key (RSA algorithm):
			Cipher rsaCipher = Cipher.getInstance("RSA/ECB/PKCS1Padding");
			rsaCipher.init(Cipher.ENCRYPT_MODE, publicKey);

			//Step 3: Open file stream:
			FileOutputStream file = new FileOutputStream(outputFilename);

			//Step 4: Encode symmetric key using encoding cipher, and write to file:
			file.write(rsaCipher.doFinal(aesKey.getEncoded()));

			//Step 5: Open encrypted stream using symmetric key (AES algorithm):
			Cipher cipher = Cipher.getInstance("AES");
			cipher.init(Cipher.ENCRYPT_MODE, aesKey);
			encryptedStream = new CipherOutputStream(file, cipher);
		} catch (Exception e){
			e.printStackTrace();
		}

		return encryptedStream;
	}

	/**
	 * Get a stream to read from an encrypted file using a given private key.
	 * @param source         - The name of the encrypted file.
	 * @param privateKeyFile - The name of the file containing the private key.
	 * @return               - The decrypted stream to be read from the encrypted file.
	 */
	public InputStream getDecryptedStream(String source, String privateKeyFile){
		CipherInputStream decryptedStream = null;

		try {
			// Step 1: Get the private key from file
			Key privateKey = loadKey(privateKeyFile);

			// Step 2: Generate cipher using private key (RSA algorithm):
			Cipher rsaCipher = Cipher.getInstance("RSA/ECB/PKCS1Padding");
			rsaCipher.init(Cipher.DECRYPT_MODE, privateKey);

			// Step 3: open file:
			FileInputStream textFileStream = new FileInputStream(source);

			// Step 4: read encrypted symmetric key, and decrypt using private key:
			int keySize = ((RSAKey)privateKey).getModulus().bitLength();
			byte[] encKey = new byte[keySize/8];
			textFileStream.read(encKey);
			Key aesKey = new SecretKeySpec(rsaCipher.doFinal(encKey), "AES");

			// Step 5: create decryption stream (AES algorithm):
			Cipher aesCipher = Cipher.getInstance("AES");
			aesCipher.init(Cipher.DECRYPT_MODE, aesKey);
			decryptedStream = new CipherInputStream(textFileStream, aesCipher);
		} catch (Exception e){
			e.printStackTrace();
		}

		return decryptedStream;
	}

	/**
	 * Loads a public encryption key from fileName.
	 * @param filename - the name of the file containing the encryption key
	 * @return - the encryption key
	 * @throws FileNotFoundException - if file not found
	 */
	private Key loadKey(String filename) throws FileNotFoundException {
		FileInputStream binFile = new FileInputStream(filename);
		return loadKey(binFile);
	}

	/**
	 * Loads an encryption key from an input stream.
	 * @param stream - the input stream containing the encryption key
	 * @return - the encryption key; null if stream is not valid
	 */
	private Key loadKey(InputStream stream) {
		Key result = null;
		try {
			ObjectInputStream inp = new ObjectInputStream(stream);
			try {
				result = (Key)inp.readObject();
			} catch (ClassNotFoundException e) {
				e.printStackTrace();
			}
		} catch (IOException e) {
			e.printStackTrace();
		}

		return result;
	}

}
