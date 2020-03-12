package com.radixdlt.client.application.translate.data;

import com.radixdlt.client.application.translate.Action;
import com.radixdlt.identifiers.RadixAddress;
import java.util.Objects;

/**
 * An Action object which sends a data transaction from one account to another.
 */
public final class SendMessageAction implements Action {
	private final RadixAddress from;
	private final RadixAddress to;
	private final byte[] data;
	private final boolean encrypt;

	private SendMessageAction(RadixAddress from, RadixAddress to, byte[] data, boolean encrypt) {
		this.from = Objects.requireNonNull(from);
		this.data = Objects.requireNonNull(data);
		this.to = Objects.requireNonNull(to);
		this.encrypt = encrypt;
	}

	public static SendMessageAction create(RadixAddress from, RadixAddress to, byte[] data, boolean encrypt) {
		return new SendMessageAction(from, to, data, encrypt);
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
