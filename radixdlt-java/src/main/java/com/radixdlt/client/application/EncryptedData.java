package com.radixdlt.client.application;

import com.radixdlt.client.core.crypto.ECKeyPair;
import com.radixdlt.client.core.crypto.ECKeyPairGenerator;
import com.radixdlt.client.core.crypto.ECPublicKey;
import com.radixdlt.client.core.crypto.EncryptedPrivateKey;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Application layer encrypted data object. Can be stored and retrieved from a RadixAddress.
 */
public class EncryptedData {
	public static class EncryptedDataBuilder {
		private Map<String, String> metaData = new HashMap<>();
		private byte[] data;
		private List<ECPublicKey> readers = new ArrayList<>();

		public EncryptedDataBuilder() {
		}

		public EncryptedDataBuilder metaData(String key, String value) {
			metaData.put(key, value);
			return this;
		}

		public EncryptedDataBuilder data(byte[] data) {
			this.data = data;
			return this;
		}

		public EncryptedDataBuilder addReader(ECPublicKey reader) {
			readers.add(reader);
			return this;
		}

		public EncryptedData build() {
			ECKeyPair sharedKey = ECKeyPairGenerator.newInstance().generateKeyPair();
			List<EncryptedPrivateKey> protectors = readers.stream().map(sharedKey::encryptPrivateKey).collect(Collectors.toList());
			byte[] encrypted = sharedKey.getPublicKey().encrypt(data);

			return new EncryptedData(encrypted, metaData, protectors);
		}
	}

	private final Map<String, String> metaData;
	private final byte[] encrypted;
	private final List<EncryptedPrivateKey> protectors;

	private EncryptedData(byte[] encrypted, Map<String, String> metaData, List<EncryptedPrivateKey> protectors) {
		this.encrypted = encrypted;
		this.metaData = metaData;
		this.protectors = protectors;
	}

	public byte[] getEncrypted() {
		return encrypted;
	}

	public List<EncryptedPrivateKey> getProtectors() {
		return protectors;
	}

	public Map<String, String> getMetaData() {
		return metaData;
	}
}
