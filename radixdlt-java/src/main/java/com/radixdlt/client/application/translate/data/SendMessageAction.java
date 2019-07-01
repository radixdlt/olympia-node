package com.radixdlt.client.application.translate.data;

import com.radixdlt.client.application.translate.Action;
import com.radixdlt.client.atommodel.accounts.RadixAddress;

/**
 * An Action object which sends a data transaction from one account to another.
 */
public class SendMessageAction implements Action {
	private final RadixAddress from;
	private final RadixAddress to;
	private final byte[] data;
	private final boolean encrypt;

	private SendMessageAction(byte[] data, RadixAddress from, RadixAddress to, boolean encrypt) {
		this.from = from;
		this.data = data;
		this.to = to;
		this.encrypt = encrypt;
	}

	public static SendMessageAction create(byte[] data, RadixAddress from, RadixAddress to, boolean encrypt) {
		return new SendMessageAction(data, from, to, encrypt);
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

	@Override
	public String toString() {
		return "SEND MESSAGE FROM " + from + " TO " + to;
	}
}
