package com.radixdlt.client.application.translate.data;

import com.radixdlt.client.atommodel.accounts.RadixAddress;
import org.bouncycastle.util.encoders.Base64;

/**
 * An application layer object representing some data found on the ledger.
 */
public class DecryptedMessage {
	public enum EncryptionState {
		/**
		 * Specifies that the data in the DecryptedMessage object WAS originally
		 * encrypted and has been successfully decrypted to it's present byte array.
		 */
		DECRYPTED,
		/**
		 * Specifies that the data in the DecryptedMessage object was NOT
		 * encrypted and the present data byte array just represents the original data.
		 */
		NOT_ENCRYPTED,
		/**
		 * Specifies that the data in the DecryptedMessage object WAS encrypted
		 * but could not be decrypted. The present data byte array represents the still
		 * encrypted data.
		 */
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
