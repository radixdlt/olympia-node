package com.radixdlt.client.application.objects;

import java.util.Map;

public class UnencryptedData {
	private final Map<String, Object> metaData;
	private final byte[] data;
	private final boolean isFromEncryptedSource;

	public UnencryptedData(byte[] data, Map<String, Object> metaData, boolean isFromEncryptedSource) {
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
}
