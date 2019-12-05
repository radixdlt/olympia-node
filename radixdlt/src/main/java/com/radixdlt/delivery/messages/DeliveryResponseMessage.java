package com.radixdlt.delivery.messages;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.radixdlt.serialization.DsonOutput;
import com.radixdlt.serialization.DsonOutput.Output;
import com.radixdlt.serialization.SerializerId2;
import com.radixdlt.store.LedgerEntry;
import org.radix.network.messaging.Message;

@SerializerId2("tempo.sync.delivery.response")
public class DeliveryResponseMessage extends Message {
	@JsonProperty("ledgerEntry")
	@DsonOutput(Output.ALL)
	private LedgerEntry ledgerEntry;

	DeliveryResponseMessage() {
		// For serializer only
	}

	public DeliveryResponseMessage(LedgerEntry ledgerEntry) {
		this.ledgerEntry = ledgerEntry;
	}

	public LedgerEntry getLedgerEntry() {
		return ledgerEntry;
	}

	@Override
	public String getCommand() {
		return "tempo.sync.delivery.response";
	}
}
