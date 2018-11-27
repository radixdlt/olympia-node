package com.radixdlt.client.application.translate.data;

import com.radixdlt.client.atommodel.accounts.RadixAddress;

/**
 * An Action object which sends a data transaction from one account to another.
 */
public class SendMessageAction {
	private final RadixAddress from;
	private final RadixAddress to;
	private final byte[] data;
	private final boolean encrypt;

	public SendMessageAction(byte[] data, RadixAddress from, RadixAddress to, boolean encrypt) {
		this.from = from;
		this.data = data;
		this.to = to;
		this.encrypt = encrypt;
	}

	public boolean encrypt() {
		return encrypt;
	}

	public byte[] getData() {
		return data;
	}

	public RadixAddress getTo() {
		return to;
	}

	public RadixAddress getFrom() {
		return from;
	}
}
