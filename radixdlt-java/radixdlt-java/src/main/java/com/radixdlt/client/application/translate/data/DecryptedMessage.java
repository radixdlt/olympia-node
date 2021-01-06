/*
 * (C) Copyright 2020 Radix DLT Ltd
 *
 * Permission is hereby granted, free of charge, to any person obtaining a
 * copy of this software and associated documentation files (the “Software”),
 * to deal in the Software without restriction, including without limitation
 * the rights to use, copy, modify, merge, publish, distribute, sublicense,
 * and/or sell copies of the Software, and to permit persons to whom the
 * Software is furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be
 * included in all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED “AS IS”, WITHOUT WARRANTY OF ANY KIND, EXPRESS
 * OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL
 * THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING
 * FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER
 * DEALINGS IN THE SOFTWARE.
 */

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
	private final EUID actionId;

	public DecryptedMessage(byte[] data, RadixAddress from, RadixAddress to, EncryptionState encryptionState, EUID actionId) {
		this.from = from;
		this.data = data;
		this.to = to;
		this.encryptionState = encryptionState;
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

	@Override
	public String toString() {
		return from + " -> " + to + ": " + encryptionState + " " + Base64.toBase64String(data);
	}
}
