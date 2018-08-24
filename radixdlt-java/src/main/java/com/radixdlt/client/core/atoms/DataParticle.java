package com.radixdlt.client.core.atoms;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Particle which can hold arbitrary data
 */
public class DataParticle {
	/**
	 * Nullable for the timebeing as we want dson to be optimized for
	 * saving space and no way to skip empty maps in Dson yet.
	 */
	private final Map<String, Object> metaData;

	/**
	 * Arbitrary data, possibly encrypted
	 */
	private final Payload bytes;

	public DataParticle(Payload bytes, String application) {
		Objects.requireNonNull(bytes);

		this.bytes = bytes;
		if (application != null) {
			this.metaData = new HashMap<>();
			this.metaData.put("application", application);
		} else {
			this.metaData = null;
		}
	}

	public Object getMetaData(String key) {
		if (metaData == null) {
			return null;
		}

		return metaData.get(key);
	}

	public Payload getBytes() {
		return bytes;
	}
}
