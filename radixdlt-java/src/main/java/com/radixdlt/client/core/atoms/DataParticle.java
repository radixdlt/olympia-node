package com.radixdlt.client.core.atoms;

import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;

/**
 * Particle which can hold arbitrary data
 */
public class DataParticle {
	public static class DataParticleBuilder {
		private final Map<String, Object> metaData = new TreeMap<>();
		private Payload bytes;

		public DataParticleBuilder setMetaData(String key, Object value) {
			metaData.put(key, value);
			return this;
		}

		public DataParticleBuilder payload(Payload bytes) {
			this.bytes = bytes;
			return this;
		}

		public DataParticle build() {
			return new DataParticle(bytes, metaData.isEmpty() ? null : metaData);
		}
	}

	/**
	 * Nullable for the timebeing as we want dson to be optimized for
	 * saving space and no way to skip empty maps in Dson yet.
	 */
	private final Map<String, Object> metaData;

	/**
	 * Arbitrary data, possibly encrypted
	 */
	private final Payload bytes;

	private DataParticle(Payload bytes, Map<String, Object> metaData) {
		Objects.requireNonNull(bytes);

		this.bytes = bytes;
		this.metaData = metaData;
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
