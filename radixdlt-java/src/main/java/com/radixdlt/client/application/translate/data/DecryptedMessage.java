package com.radixdlt.client.application.translate.data;

import com.radixdlt.identifiers.RadixAddress;
import org.bouncycastle.util.encoders.Base64;
import com.radixdlt.identifiers.EUID;

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
	private final EUID actionId;

	public DecryptedMessage(byte[] data, RadixAddress from, RadixAddress to, EncryptionState encryptionState, long timestamp, EUID actionId) {
		this.from = from;
		this.data = data;
		this.to = to;
		this.encryptionState = encryptionState;
		this.timestamp = timestamp;
		this.actionId = actionId;
	}

	/**
	 * The unique id for the this message action
	 * @return euid for the action
	 */
	public EUID getActionId() {
		return actionId;
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
