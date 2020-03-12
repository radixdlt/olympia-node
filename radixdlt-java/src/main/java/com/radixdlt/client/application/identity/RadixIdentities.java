package com.radixdlt.client.application.identity;

import com.radixdlt.crypto.CryptoException;
import com.radixdlt.crypto.ECKeyPair;
import java.io.File;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.Reader;
import java.io.StringReader;
import java.io.Writer;
import java.security.GeneralSecurityException;

import com.radixdlt.crypto.encryption.PrivateKeyEncrypter;
import org.bouncycastle.util.encoders.Base64;

/**
 * Radix Identity Helper methods
 */
public class RadixIdentities {
	private RadixIdentities() {
	}

	/**
	 * Creates a radix identity from a private key
	 * @param privateKeyBase64 the private key encoded in base 64
	 * @return a radix identity
	 */
	public static RadixIdentity fromPrivateKeyBase64(String privateKeyBase64) throws CryptoException {
		final ECKeyPair myKey = new ECKeyPair(Base64.decode(privateKeyBase64));
		return new LocalRadixIdentity(myKey);
	}

	/**
	 * Creates a new radix identity which is not stored anywhere
	 * @return an unstored radix identity
	 */
	public static LocalRadixIdentity createNew() {
		return new LocalRadixIdentity(new ECKeyPair());
	}

	/**
	 * Loads or creates an unencrypted file containing a private key and returns
	 * the associated radix identity
	 * @param keyFile the file to load or create
	 * @return a radix identity
	 * @throws IOException
	 */
	public static RadixIdentity loadOrCreateFile(File keyFile) throws IOException, CryptoException {
		final ECKeyPair ecKeyPair;
		if (keyFile.exists()) {
			ecKeyPair = ECKeyPair.fromFile(keyFile);
		} else {
			ecKeyPair = new ECKeyPair();

			try (FileOutputStream io = new FileOutputStream(keyFile)) {
				io.write(ecKeyPair.getPrivateKey());
			}
		}

		return new LocalRadixIdentity(ecKeyPair);
	}

	/**
	 * Loads or creates an unencrypted file containing a private key and returns
	 * the associated radix identity
	 * @param filePath the path of the file to load or create
	 * @return a radix identity
	 * @throws IOException
	 */
	public static RadixIdentity loadOrCreateFile(String filePath) throws IOException, CryptoException {
		return loadOrCreateFile(new File(filePath));
	}

	/**
	 * Loads or creates an encrypted file containing a private key and returns
	 * the associated radix identity
	 * @param keyFile the file to load or create
	 * @param password the password to decrypt the encrypted file
	 * @return a radix identity
	 * @throws IOException
	 */
	public static RadixIdentity loadOrCreateEncryptedFile(File keyFile, String password)
			throws IOException, GeneralSecurityException, CryptoException {
		if (!keyFile.exists()) {
			try (Writer writer = new FileWriter(keyFile)) {
				return createNewEncryptedIdentity(writer, password);
			}
		} else {
			try (Reader reader = new FileReader(keyFile)) {
				return readEncryptedIdentity(reader, password);
			}
		}
	}

	/**
	 * Loads or creates an encrypted file containing a private key and returns
	 * the associated radix identity
	 * @param filePath the path of the file to load or create
	 * @param password the password to decrypt the encrypted file
	 * @return a radix identity
	 * @throws IOException
	 */
	public static RadixIdentity loadOrCreateEncryptedFile(String filePath, String password)
			throws IOException, GeneralSecurityException, CryptoException {
		return loadOrCreateEncryptedFile(new File(filePath), password);
	}

	/**
	 * Creates a new private key and encrypts it and then writes/flushes the result to a given writer
	 *
	 * @param writer the writer to write the encrypted private key to
	 * @param password the password to encrypt the private key with
	 * @return the radix identity created
	 * @throws IOException
	 */
	public static RadixIdentity createNewEncryptedIdentity(Writer writer, String password)
			throws IOException, GeneralSecurityException, CryptoException {
		String encryptedKey = PrivateKeyEncrypter.createEncryptedPrivateKey(password);
		writer.write(encryptedKey);
		writer.flush();
		return readEncryptedIdentity(new StringReader(encryptedKey), password);
	}

	/**
	 * Reads an encrypted private key from a given reader and decrypts it with a given password
	 * @param reader the reader to read the encrypted private key from
	 * @param password the password to decrypt the private key with
	 * @return the decrypted radix identity
	 * @throws IOException
	 */
	public static RadixIdentity readEncryptedIdentity(Reader reader, String password)
			throws IOException, GeneralSecurityException, CryptoException {
		final ECKeyPair key = new ECKeyPair(PrivateKeyEncrypter.decryptPrivateKey(password, reader));
		return new LocalRadixIdentity(key);
	}
}
