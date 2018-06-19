package com.radixdlt.client.messaging;

import com.radixdlt.client.core.atoms.ApplicationPayloadAtom;
import com.radixdlt.client.core.atoms.EncryptedPayload;
import com.radixdlt.client.core.identity.Decryptable;

public class EncryptedMessage implements Decryptable<RadixMessage> {
	private ApplicationPayloadAtom atom;

	public EncryptedMessage(ApplicationPayloadAtom atom) {
		this.atom = atom;
	}

	public long getTimestamp() {
		return atom.getTimestamp();
	}

	@Override
	public EncryptedPayload getEncrypted() {
		return atom.getEncryptedPayload();
	}

	@Override
	public RadixMessage deserialize(byte[] decrypted) {
		return new RadixMessage(RadixMessageContent.fromJson(new String(decrypted)), atom);
	}

	public static EncryptedMessage fromAtom(ApplicationPayloadAtom atom) {
		return new EncryptedMessage(atom);
	}

	@Override
	public String toString() {
		return "Encrypted atom(" + atom + ")";
	}
}
