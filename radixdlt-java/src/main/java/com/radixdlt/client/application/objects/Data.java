package com.radixdlt.client.application.objects;

import com.radixdlt.client.core.crypto.ECKeyPair;
import com.radixdlt.client.core.crypto.ECKeyPairGenerator;
import com.radixdlt.client.core.crypto.ECPublicKey;
import com.radixdlt.client.core.crypto.EncryptedPrivateKey;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.bouncycastle.util.encoders.Base64;

/**
 * Application layer bytes bytes object. Can be stored and retrieved from a RadixAddress.
 */
public class Data {
	public static class DataBuilder {
		private Map<String, Object> metaData = new HashMap<>();
		private byte[] bytes;
		private List<ECPublicKey> readers = new ArrayList<>();
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
			readers.add(reader);
			return this;
		}

		public DataBuilder publicReadable(boolean isPublicReadable) {
			this.isPublicReadable = isPublicReadable;
			return this;
		}

		public Data build() {
			final byte[] bytes;
			final List<EncryptedPrivateKey> protectors;

			if (isPublicReadable) {
				protectors = Collections.emptyList();
				bytes = this.bytes;
				metaData.put("encrypted", false);
			} else {
				if (readers.isEmpty()) {
					throw new IllegalStateException("Must either be publicReadable or have atleast one reader.");
				}

				ECKeyPair sharedKey = ECKeyPairGenerator.newInstance().generateKeyPair();
				protectors = readers.stream().map(sharedKey::encryptPrivateKey).collect(Collectors.toList());
				bytes = sharedKey.getPublicKey().encrypt(this.bytes);
				metaData.put("encrypted", true);
			}

			return new Data(bytes, metaData, protectors);
		}
	}

	// TODO: Cleanup this interface
	public static Data raw(byte[] bytes, Map<String, Object> metaData, List<EncryptedPrivateKey> protectors) {
		return new Data(bytes, metaData, protectors);
	}

	private final Map<String, Object> metaData;
	private final byte[] bytes;
	private final List<EncryptedPrivateKey> protectors;

	private Data(byte[] bytes, Map<String, Object> metaData, List<EncryptedPrivateKey> protectors) {
		this.bytes = bytes;
		this.metaData = metaData;
		this.protectors = protectors;
	}

	// TODO: make unmodifiable
	public byte[] getBytes() {
		return bytes;
	}

	public List<EncryptedPrivateKey> getProtectors() {
		return protectors;
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
