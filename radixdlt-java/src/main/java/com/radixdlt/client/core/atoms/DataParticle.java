package com.radixdlt.client.core.atoms;

/**
 * Particle which can hold arbitrary data
 */
public class DataParticle {

	/**
	 * Temporary property specifying the application this data particle
	 * was meant for. Will change into some kind of metaData in the future.
	 */
	private final String application;

	/**
	 * Arbitrary data, possibly encrypted
	 */
	private final Payload bytes;

	public DataParticle(Payload bytes, String application) {
		this.application = application;
		this.bytes = bytes;
	}

	public String getApplication() {
		return application;
	}

	public Payload getBytes() {
		return bytes;
	}
}
