package com.radixdlt.tempo.messages;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.common.collect.ImmutableSet;
import com.radixdlt.common.AID;
import com.radixdlt.common.EUID;
import com.radixdlt.serialization.DsonOutput;
import com.radixdlt.serialization.SerializerId2;
import org.radix.network.messaging.Message;
import org.radix.time.TemporalProof;

@SerializerId2("tempo.sample.response")
public class SampleResponseMessage extends Message {
	@JsonProperty("temporalProofs")
	@DsonOutput(DsonOutput.Output.ALL)
	private final ImmutableSet<TemporalProof> temporalProofs;

	@JsonProperty("unavailableAids")
	@DsonOutput(DsonOutput.Output.ALL)
	private final ImmutableSet<AID> unavailableAids;

	@JsonProperty("tag")
	@DsonOutput(DsonOutput.Output.ALL)
	private final EUID tag;

	private SampleResponseMessage() {
		// For serializer
		this.temporalProofs = ImmutableSet.of();
		this.unavailableAids = ImmutableSet.of();
		this.tag = null;
	}

	public SampleResponseMessage(ImmutableSet<TemporalProof> temporalProofs, ImmutableSet<AID> unavailableAids, EUID tag) {
		this.temporalProofs = temporalProofs;
		this.unavailableAids = unavailableAids;
		this.tag = tag;
	}

	public ImmutableSet<TemporalProof> getTemporalProofs() {
		return temporalProofs;
	}

	public ImmutableSet<AID> getUnavailableAids() {
		return unavailableAids;
	}

	public EUID getTag() {
		return tag;
	}

	@Override
	public String getCommand() {
		return "tempo.sample.response";
	}
}
