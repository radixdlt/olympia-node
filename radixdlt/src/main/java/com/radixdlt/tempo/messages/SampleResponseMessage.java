package com.radixdlt.tempo.messages;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.common.collect.ImmutableSet;
import com.radixdlt.common.AID;
import com.radixdlt.serialization.DsonOutput;
import com.radixdlt.serialization.SerializerId2;
import org.radix.network.messaging.Message;
import org.radix.time.TemporalProof;

@SerializerId2("tempo.sample.response")
public class SampleResponseMessage extends Message {
	@JsonProperty("temporalProofs")
	@DsonOutput(DsonOutput.Output.ALL)
	private final ImmutableSet<TemporalProof> temporalProofs;

	@JsonProperty("missingAids")
	@DsonOutput(DsonOutput.Output.ALL)
	private final ImmutableSet<AID> missingAids;

	private SampleResponseMessage() {
		// For serializer
		this.temporalProofs = ImmutableSet.of();
		this.missingAids = ImmutableSet.of();
	}

	public SampleResponseMessage(ImmutableSet<TemporalProof> temporalProofs, ImmutableSet<AID> missingAids) {
		this.temporalProofs = temporalProofs;
		this.missingAids = missingAids;
	}

	public ImmutableSet<TemporalProof> getTemporalProofs() {
		return temporalProofs;
	}

	public ImmutableSet<AID> getMissingAids() {
		return missingAids;
	}

	@Override
	public String getCommand() {
		return "tempo.sample.response";
	}
}
