package com.radixdlt.ledger;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.common.collect.ImmutableSet;
import com.radixdlt.common.AID;
import com.radixdlt.serialization.DsonOutput;
import com.radixdlt.serialization.SerializerConstants;
import com.radixdlt.serialization.SerializerDummy;
import com.radixdlt.serialization.SerializerId2;

import java.util.Objects;

@SerializerId2("ledger.entry")
public class LedgerEntry {

	// Placeholder for the serializer ID
	@JsonProperty(SerializerConstants.SERIALIZER_NAME)
	@DsonOutput(DsonOutput.Output.ALL)
	SerializerDummy serializer = SerializerDummy.DUMMY;

	@JsonProperty("content")
	@DsonOutput(value = {DsonOutput.Output.ALL})
	private byte[] content;

	@JsonProperty("aid")
	@DsonOutput(value = {DsonOutput.Output.ALL})
	private AID aid;

	private LedgerEntry() {
		// For serializer
	}

	public LedgerEntry(byte[] content, AID aid) {
		this.content = Objects.requireNonNull(content, "content is required");
		this.aid = Objects.requireNonNull(aid, "aid is required");
	}

	public byte[] getContent() {
		return this.content;
	}

	public AID getAID() {
		return this.aid;
	}

	@Override
	public boolean equals(Object o) {
		if (this == o) {
			return true;
		}
		if (o == null || getClass() != o.getClass()) {
			return false;
		}
		LedgerEntry radixLedgerEntry = (LedgerEntry) o;
		return aid.equals(radixLedgerEntry.aid);
	}

	@Override
	public int hashCode() {
		return aid.hashCode();
	}

	@Override
	public String toString() {
		return String.format("LedgerEntry{aid=%s}", aid);
	}
}
