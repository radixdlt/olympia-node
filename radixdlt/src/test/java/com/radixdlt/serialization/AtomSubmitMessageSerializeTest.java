package com.radixdlt.serialization;

import org.radix.atoms.Atom;
import org.radix.atoms.messages.AtomSubmitMessage;
import com.radixdlt.crypto.ECKeyPair;
import com.radixdlt.crypto.CryptoException;

/**
 * Check serialization of AtomSubmitMessage
 */
public class AtomSubmitMessageSerializeTest extends SerializeMessageObject<AtomSubmitMessage> {
	public AtomSubmitMessageSerializeTest() {
		super(AtomSubmitMessage.class, AtomSubmitMessageSerializeTest::get);
	}

	private static AtomSubmitMessage get() {
		try {
			ECKeyPair key = new ECKeyPair();
			Atom atom = new Atom();
			atom.sign(key);
			return new AtomSubmitMessage(atom);
		} catch (CryptoException e) {
			throw new IllegalStateException("Can't create ParticleConflict", e);
		}
	}
}
