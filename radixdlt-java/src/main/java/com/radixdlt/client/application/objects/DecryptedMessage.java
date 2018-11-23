package com.radixdlt.client.application.objects;

import com.radixdlt.client.atommodel.accounts.RadixAddress;
import org.bouncycastle.util.encoders.Base64;

public class DecryptedMessage {
	public enum EncryptionState {
		DECRYPTED,
		NOT_ENCRYPTED,
		CANNOT_DECRYPT,
	}

	private final RadixAddress from;
	private final RadixAddress to;
	private final byte[] data;
	private final EncryptionState encryptionState;
	private final long timestamp;

	public DecryptedMessage(byte[] data, RadixAddress from, RadixAddress to, EncryptionState encryptionState, long timestamp) {
		this.from = from;
		this.data = data;
		this.to = to;
		this.encryptionState = encryptionState;
		this.timestamp = timestamp;
	}

	public EncryptionState getEncryptionState() {
		return encryptionState;
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

	public long getTimestamp() {
		return timestamp;
	}

	@Override
	public String toString() {
		return timestamp + " " + from + " -> " + to + ": " + encryptionState + " " + Base64.toBase64String(data);
	}
}
