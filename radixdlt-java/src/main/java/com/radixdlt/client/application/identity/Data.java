package com.radixdlt.client.application.identity;

import com.radixdlt.crypto.ECKeyPair;
import com.radixdlt.crypto.ECPublicKey;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

import com.radixdlt.crypto.encryption.ECIESException;
import com.radixdlt.crypto.encryption.Encryptor;
import org.bouncycastle.util.encoders.Base64;

/**
 * Application layer bytes bytes object. Can be stored and retrieved from a RadixAddress.
 */
public class Data {
	public static class DataBuilder {
		private Map<String, Object> metaData = new LinkedHashMap<>();
		private byte[] bytes;
		private Encryptor.EncryptorBuilder encryptorBuilder = new Encryptor.EncryptorBuilder();
		private boolean unencrypted = false;

		public DataBuilder() {
		}

		public DataBuilder metaData(String key, Object value) {
			metaData.put(key, value);
			return this;
		}

		public DataBuilder bytes(byte[] bytes) {
			this.bytes = bytes;
			return this;
		}

		public DataBuilder addReader(ECPublicKey reader) {
			encryptorBuilder.addReader(reader);
			return this;
		}

		public DataBuilder unencrypted() {
			this.unencrypted = true;
			return this;
		}

		public Data build() {
			if (this.bytes == null) {
				throw new IllegalStateException("Must include bytes.");
			}

			final byte[] bytes;
			final Encryptor encryptor;

			if (unencrypted) {
				encryptor = null;
				bytes = this.bytes;
			} else {
				if (encryptorBuilder.getNumReaders() == 0) {
					throw new IllegalStateException("Must either be unencrypted or have at least one reader.");
				}

				ECKeyPair sharedKey = new ECKeyPair();
				encryptorBuilder.sharedKey(sharedKey);
				encryptor = encryptorBuilder.build();
				try {
					bytes = sharedKey.getPublicKey().encrypt(this.bytes);
				} catch (ECIESException e) {
					throw new IllegalStateException("Expected to always be able to encrypt", e);
				}
			}
			metaData.put("encrypted", unencrypted);

			return new Data(bytes, metaData, encryptor);
		}
	}

	// TODO: Cleanup this interface
	public static Data raw(byte[] bytes, Map<String, Object> metaData, Encryptor encryptor) {
		return new Data(bytes, metaData, encryptor);
	}

	private final Map<String, Object> metaData;
	private final byte[] bytes;
	private final Encryptor encryptor;

	private Data(byte[] bytes, Map<String, Object> metaData, Encryptor encryptor) {
		this.bytes = bytes;
		this.metaData = metaData;
		this.encryptor = encryptor;
	}

	// TODO: make unmodifiable
	public byte[] getBytes() {
		return bytes;
	}

	public Encryptor getEncryptor() {
		return encryptor;
	}

	public Map<String, Object> getMetaData() {
		return Collections.unmodifiableMap(metaData);
	}

	@Override
	public String toString() {
		boolean encrypted = (Boolean) metaData.get("encrypted");

		return encrypted ? ("encrypted: " + Base64.toBase64String(bytes)) : ("unencrypted: " + new String(bytes));
	}
}
