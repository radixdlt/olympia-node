package com.radixdlt.mock;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.common.collect.ImmutableSet;
import com.radixdlt.AtomContent;
import com.radixdlt.ledger.LedgerIndex;
import com.radixdlt.serialization.DsonOutput;
import com.radixdlt.serialization.SerializerId2;

import java.util.Objects;

@SerializerId2("mock.atom.content")
public final class MockAtomContent extends AtomContent {
	public static final byte GENERIC_KEY_PREFIX = 7;

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
		this.applicationIndices = ImmutableSet.of();
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
