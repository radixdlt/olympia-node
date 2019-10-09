package com.radixdlt.tempo.consensus.messages;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.radixdlt.common.EUID;
import com.radixdlt.serialization.DsonOutput;
import com.radixdlt.serialization.SerializerId2;
import com.radixdlt.tempo.consensus.Sample;
import org.radix.network.messaging.Message;

import java.util.Objects;

@SerializerId2("tempo.consensus.sampling.response")
public class SampleResponseMessage extends Message {
	@JsonProperty("tag")
	@DsonOutput(DsonOutput.Output.ALL)
	private final EUID tag;

	@JsonProperty("sample")
	@DsonOutput(DsonOutput.Output.ALL)
	private final Sample sample;

	private SampleResponseMessage() {
		// For serializer
		this.tag = null;
		this.sample = null;
	}

	public SampleResponseMessage(EUID tag, Sample sample) {
		this.tag = Objects.requireNonNull(tag);
		this.sample = Objects.requireNonNull(sample);
	}

	public EUID getTag() {
		return tag;
	}

	public Sample getSample() {
		return sample;
	}

	@Override
	public String getCommand() {
		return "tempo.consensus.sampling.response";
	}
}
