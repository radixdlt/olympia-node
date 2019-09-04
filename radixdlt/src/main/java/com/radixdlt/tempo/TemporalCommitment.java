package com.radixdlt.tempo;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.radixdlt.common.AID;
import com.radixdlt.crypto.Hash;
import com.radixdlt.serialization.DsonOutput;
import com.radixdlt.serialization.SerializerConstants;
import com.radixdlt.serialization.SerializerDummy;
import com.radixdlt.serialization.SerializerId2;

import java.util.Objects;

@SerializerId2("tempo.temporal_commitment")
public final class TemporalCommitment {
	// Placeholder for the serializer ID
	@JsonProperty(SerializerConstants.SERIALIZER_NAME)
	@DsonOutput(DsonOutput.Output.ALL)
	private SerializerDummy serializer = SerializerDummy.DUMMY;

	@JsonProperty("aid")
	@DsonOutput(DsonOutput.Output.ALL)
	private final AID aid;

	@JsonProperty("aid")
	@DsonOutput(DsonOutput.Output.ALL)
	private final long logicalClock;

	@JsonProperty("aid")
	@DsonOutput(DsonOutput.Output.ALL)
	private final Hash commitment;

	private TemporalCommitment() {
		// For serializer
		aid = null;
		logicalClock = 0L;
		commitment = null;
	}

	public TemporalCommitment(AID aid, long logicalClock, Hash commitment) {
		this.aid = Objects.requireNonNull(aid);
		this.logicalClock = Objects.requireNonNull(logicalClock);
		this.commitment = Objects.requireNonNull(commitment);
	}

	public AID getAID() {
		return aid;
	}

	public long getLogicalClock() {
		return logicalClock;
	}

	public Hash getCommitment() {
		return commitment;
	}
}
