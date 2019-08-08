package org.radix.network2.transport;

import java.util.Objects;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.radixdlt.serialization.DsonOutput;
import com.radixdlt.serialization.SerializerConstants;
import com.radixdlt.serialization.SerializerDummy;
import com.radixdlt.serialization.SerializerId2;
import com.radixdlt.serialization.DsonOutput.Output;

@SerializerId2("network.transport_info")
public final class TransportInfo {

	// Placeholder for the serializer ID
	@JsonProperty(SerializerConstants.SERIALIZER_NAME)
	@DsonOutput(Output.ALL)
	private SerializerDummy serializer = SerializerDummy.DUMMY;

	@JsonProperty("name")
	@DsonOutput(Output.ALL)
	private String name;

	@JsonProperty("metadata")
	@DsonOutput(Output.ALL)
	private TransportMetadata metadata;

	public static TransportInfo of(String name, TransportMetadata metadata) {
		return new TransportInfo(name, metadata);
	}

	TransportInfo() {
		// For serializer only
	}

	private TransportInfo(String name, TransportMetadata metadata) {
		this.name = Objects.requireNonNull(name);
		this.metadata = Objects.requireNonNull(metadata);
	}

	public String name() {
		return this.name;
	}

	public TransportMetadata metadata() {
		return this.metadata;
	}

	@Override
	public int hashCode() {
		return Objects.hash(this.name, this.metadata);
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj) {
			return true;
		}
		if (obj instanceof TransportInfo) {
			TransportInfo other = (TransportInfo) obj;
			return Objects.equals(this.name, other.name) && Objects.equals(this.metadata, other.metadata);
		}
		return false;
	}

	@Override
	public String toString() {
		return String.format("%s[name=%s, metadata=%s]", getClass().getSimpleName(), name, metadata);
	}
}
