package com.radixdlt.client.application.objects;

import com.radixdlt.client.core.identity.RadixIdentity;
import io.reactivex.Maybe;
import java.util.Map;

public class UnencryptedData {
	private final Map<String, Object> metaData;
	private final byte[] data;
	private final boolean isFromEncryptedSource;

	private UnencryptedData(byte[] data, Map<String, Object> metaData, boolean isFromEncryptedSource) {
		this.data = data;
		this.metaData = metaData;
		this.isFromEncryptedSource = isFromEncryptedSource;
	}

	/**
	 * @return whether this bytes came from an encrypted source
	 */
	public boolean isFromEncryptedSource() {
		return isFromEncryptedSource;
	}

	public Map<String, Object> getMetaData() {
		return metaData;
	}

	// TODO: make immutable
	public byte[] getData() {
		return data;
	}

	/**
	 * Transforms a possibly encrypted bytes object into an unencrypted one.
	 * If decryption fails then return an empty Maybe.
	 * @param data bytes to transform
	 * @param identity identity to decrypt with
	 * @return either the unencrypted version of the bytes or an empty Maybe
	 */
	public static Maybe<UnencryptedData> fromData(Data data, RadixIdentity identity) {
		boolean encrypted = (Boolean) data.getMetaData().get("encrypted");

		if (encrypted) {
			return identity.decrypt(data)
				.map(bytes -> new UnencryptedData(bytes, data.getMetaData(), true))
				.toMaybe().onErrorComplete();
		} else {
			return Maybe.just(new UnencryptedData(data.getBytes(), data.getMetaData(), false));
		}
	}
}
