package com.radixdlt.tempo.sync.messages;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.radixdlt.common.AID;
import com.radixdlt.serialization.DsonOutput;
import com.radixdlt.serialization.DsonOutput.Output;
import com.radixdlt.serialization.SerializerId2;
import org.radix.atoms.Atom;
import org.radix.network.messaging.Message;

@SerializerId2("atom.sync2.delivery.response")
public class DeliveryResponseMessage extends Message {
	@JsonProperty("atom")
	@DsonOutput(Output.ALL)
	private Atom atom;

	DeliveryResponseMessage() {
		// For serializer only
	}

	public DeliveryResponseMessage(Atom atom) {
		this.atom = atom;
	}

	public Atom getAtom() {
		return atom;
	}

	@Override
	public String getCommand() {
		return "atom.sync2.delivery.response";
	}
}
