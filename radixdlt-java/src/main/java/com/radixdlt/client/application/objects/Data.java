package com.radixdlt.client.application.objects;

import com.radixdlt.client.core.crypto.ECKeyPair;
import com.radixdlt.client.core.crypto.ECKeyPairGenerator;
import com.radixdlt.client.core.crypto.ECPublicKey;
import com.radixdlt.client.core.crypto.Encryptor;
import com.radixdlt.client.core.crypto.Encryptor.EncryptorBuilder;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import org.bouncycastle.util.encoders.Base64;

/**
 * Application layer bytes bytes object. Can be stored and retrieved from a RadixAddress.
 */
public class Data {
	public static class DataBuilder {
		private Map<String, Object> metaData = new LinkedHashMap<>();
		private byte[] bytes;
		private EncryptorBuilder encryptorBuilder = new EncryptorBuilder();
		private boolean isPublicReadable;

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

		public DataBuilder publicReadable(boolean isPublicReadable) {
			this.isPublicReadable = isPublicReadable;
			return this;
		}

		public Data build() {
			final byte[] bytes;
			final Encryptor encryptor;

			if (isPublicReadable) {
				encryptor = null;
				bytes = this.bytes;
				metaData.put("encrypted", false);
			} else {
				if (encryptorBuilder.getNumReaders() == 0) {
					throw new IllegalStateException("Must either be publicReadable or have atleast one reader.");
				}

				ECKeyPair sharedKey = ECKeyPairGenerator.newInstance().generateKeyPair();
				encryptorBuilder.sharedKey(sharedKey);
				encryptor = encryptorBuilder.build();
				bytes = sharedKey.getPublicKey().encrypt(this.bytes);
				metaData.put("encrypted", true);
			}

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
