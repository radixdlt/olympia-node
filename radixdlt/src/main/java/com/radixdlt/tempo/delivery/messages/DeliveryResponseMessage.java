package com.radixdlt.tempo.delivery.messages;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.radixdlt.serialization.DsonOutput;
import com.radixdlt.serialization.DsonOutput.Output;
import com.radixdlt.serialization.SerializerId2;
import com.radixdlt.tempo.TempoAtom;
import org.radix.network.messaging.Message;

@SerializerId2("tempo.sync.delivery.response")
public class DeliveryResponseMessage extends Message {
	@JsonProperty("atom")
	@DsonOutput(Output.ALL)
	private TempoAtom atom;

	DeliveryResponseMessage() {
		// For serializer only
	}

	public DeliveryResponseMessage(TempoAtom atom) {
		this.atom = atom;
	}

	public TempoAtom getAtom() {
		return atom;
	}

	@Override
	public String getCommand() {
		return "tempo.sync.delivery.response";
	}
}
