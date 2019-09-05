package com.radixdlt.tempo;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.radixdlt.common.AID;
import com.radixdlt.crypto.ECPublicKey;
import com.radixdlt.crypto.ECSignature;
import com.radixdlt.crypto.Hash;
import com.radixdlt.serialization.DsonOutput;
import com.radixdlt.serialization.SerializerConstants;
import com.radixdlt.serialization.SerializerDummy;
import com.radixdlt.serialization.SerializerId2;

import java.util.Objects;

@SerializerId2("tempo.unsigned_temporal_commitment")
public final class UnsignedTemporalCommitment {
	// Placeholder for the serializer ID
	@JsonProperty(SerializerConstants.SERIALIZER_NAME)
	@DsonOutput(value = {DsonOutput.Output.API, DsonOutput.Output.WIRE, DsonOutput.Output.PERSIST})
	private SerializerDummy serializer = SerializerDummy.DUMMY;

	@JsonProperty("aid")
	@DsonOutput(DsonOutput.Output.ALL)
	private final AID aid;

	@JsonProperty("logicalClock")
	@DsonOutput(DsonOutput.Output.ALL)
	private final long logicalClock;

	@JsonProperty("commitment")
	@DsonOutput(DsonOutput.Output.ALL)
	private final Hash commitment;

	private UnsignedTemporalCommitment() {
		// For serializer
		aid = null;
		logicalClock = 0L;
		commitment = null;
	}

	public UnsignedTemporalCommitment(AID aid, long logicalClock, Hash commitment) {
		this.aid = Objects.requireNonNull(aid);
		this.logicalClock = Objects.requireNonNull(logicalClock);
		this.commitment = Objects.requireNonNull(commitment);
	}

	public TemporalCommitment sign(ECSignature signature, ECPublicKey owner) {
		return new TemporalCommitment(aid, logicalClock, commitment, owner, signature);
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
