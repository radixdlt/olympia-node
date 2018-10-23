package com.radixdlt.client.core.atoms.particles.quarks;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.radixdlt.client.core.atoms.MetadataMap;
import org.radix.serialization2.DsonOutput;
import org.radix.serialization2.SerializerId2;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Collections;
import java.util.Map;
import java.util.TreeMap;

/**
 * Particle which is responsible for holding arbitrary data, possibly encrypted
 */
@SerializerId2("DATAQUARK")
public final class DataQuark extends Quark {

	/**
	 * Metadata, aka data about the data (e.g. contentType).
	 * Will consider down the line whether this is worth putting
	 * into a more concrete class (e.g. MetaData.java).
	 */
	@JsonProperty("metaData")
	@DsonOutput(DsonOutput.Output.ALL)
	private Map<String, String> metaData = new TreeMap<>();

	/**
	 * Arbitrary data
	 */
	@JsonProperty("bytes")
	@DsonOutput(DsonOutput.Output.ALL)
	private byte[] bytes;

	private DataQuark() {
	}

	public DataQuark(String stringData) {
		this.bytes = stringData.getBytes(StandardCharsets.UTF_8);
	}

	public DataQuark(byte[] bytes) {
		this.bytes = Arrays.copyOf(bytes, bytes.length);
	}

	public DataQuark(String stringData, String contentType) {
		this(stringData.getBytes(StandardCharsets.UTF_8), contentType);
	}

	public DataQuark(byte[] bytes, String contentType) {
		this.bytes = Arrays.copyOf(bytes, bytes.length);
		this.metaData = new MetadataMap();
		this.metaData.put("contentType", contentType);
	}

	public DataQuark(byte[] bytes, Map<String, String> metaData) {
		this.bytes = bytes;

		if (metaData != null) {
			this.metaData = new MetadataMap();
			this.metaData.putAll(metaData);
		}
	}


	// TODO: Make immutable
	public byte[] getBytes() {
		return this.bytes;
	}

	public Map<String, String> getMetaData() {
		// TODO hack can only be null when instantiated through Gson
		return this.metaData == null ? null : Collections.unmodifiableMap(this.metaData);
	}
}
