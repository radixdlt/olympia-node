package com.radixdlt.client.application.actions;

import com.radixdlt.client.application.objects.Data;
import com.radixdlt.client.atommodel.accounts.RadixAddress;

/**
 * An Application Layer Action object which stores data into an address or multiple to.
 */
public class SendMessageAction {
	private final RadixAddress from;
	private final RadixAddress to;
	private final Data data;

	public SendMessageAction(Data data, RadixAddress from, RadixAddress to) {
		this.from = from;
		this.data = data;
		this.to = to;
	}

	public Data getData() {
		return data;
	}

	public RadixAddress getTo() {
		return to;
	}

	public RadixAddress getFrom() {
		return from;
	}
}
