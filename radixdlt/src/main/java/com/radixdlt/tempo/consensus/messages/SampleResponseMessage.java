package com.radixdlt.tempo.consensus.messages;

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
	@JsonProperty("collectedSamples")
	@DsonOutput(DsonOutput.Output.ALL)
	private final ImmutableSet<TemporalProof> collectedSamples;

	@JsonProperty("unavailableAids")
	@DsonOutput(DsonOutput.Output.ALL)
	private final ImmutableSet<AID> unavailableAids;

	@JsonProperty("tag")
	@DsonOutput(DsonOutput.Output.ALL)
	private final EUID tag;

	private SampleResponseMessage() {
		// For serializer
		this.collectedSamples = ImmutableSet.of();
		this.unavailableAids = ImmutableSet.of();
		this.tag = null;
	}

	public SampleResponseMessage(ImmutableSet<TemporalProof> collectedSamples, ImmutableSet<AID> unavailableAids, EUID tag) {
		this.collectedSamples = collectedSamples;
		this.unavailableAids = unavailableAids;
		this.tag = tag;
	}

	public ImmutableSet<TemporalProof> getCollectedSamples() {
		return collectedSamples;
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
