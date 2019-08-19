package com.radixdlt.tempo.messages;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.radixdlt.serialization.DsonOutput;
import com.radixdlt.serialization.DsonOutput.Output;
import com.radixdlt.serialization.SerializerId2;
import com.radixdlt.tempo.TempoAtom;
import org.radix.network.messaging.Message;

@SerializerId2("tempo.sync.push")
public class PushMessage extends Message {
	@JsonProperty("atom")
	@DsonOutput(Output.ALL)
	private TempoAtom atom;

	PushMessage() {
		// For serializer only
	}

	public PushMessage(TempoAtom atom) {
		this.atom = atom;
	}

	public TempoAtom getAtom() {
		return atom;
	}

	@Override
	public String getCommand() {
		return "tempo.sync.push";
	}
}
