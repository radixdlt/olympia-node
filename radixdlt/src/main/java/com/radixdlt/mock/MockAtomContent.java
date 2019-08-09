package com.radixdlt.mock;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.common.collect.ImmutableSet;
import com.radixdlt.ledger.LedgerIndex;
import com.radixdlt.serialization.DsonOutput;
import com.radixdlt.serialization.SerializerConstants;
import com.radixdlt.serialization.SerializerDummy;
import com.radixdlt.serialization.SerializerId2;

import java.util.Objects;

@SerializerId2("mock.atom.content")
final class MockAtomContent {
	// Placeholder for the serializer ID
	@JsonProperty(SerializerConstants.SERIALIZER_NAME)
	@DsonOutput(DsonOutput.Output.ALL)
	private SerializerDummy serializer = SerializerDummy.DUMMY;

	@JsonProperty("key")
	@DsonOutput(DsonOutput.Output.ALL)
	private LedgerIndex key;

	@JsonProperty("value")
	@DsonOutput(DsonOutput.Output.ALL)
	private byte[] value;

	@JsonProperty("applicationIndices")
	@DsonOutput(DsonOutput.Output.ALL)
	private ImmutableSet<LedgerIndex> applicationIndices;

	private MockAtomContent() {
		// For serializer
	}

	public MockAtomContent(LedgerIndex key, byte[] value) {
		this(key, value, ImmutableSet.of());
	}

	public MockAtomContent(LedgerIndex key, byte[] value, ImmutableSet<LedgerIndex> applicationIndices) {
		this.key = Objects.requireNonNull(key, "key is required");
		this.value = Objects.requireNonNull(value, "value is required");
		this.applicationIndices = applicationIndices;
	}

	public LedgerIndex getKey() {
		return key;
	}

	public byte[] getValue() {
		return value;
	}

	public ImmutableSet<LedgerIndex> getApplicationIndices() {
		return applicationIndices;
	}
}
