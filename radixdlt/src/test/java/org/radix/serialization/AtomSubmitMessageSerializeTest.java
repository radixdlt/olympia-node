package org.radix.serialization;

import com.radixdlt.common.Atom;
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
			return new AtomSubmitMessage(atom, 1);
		} catch (CryptoException e) {
			throw new IllegalStateException("Can't create ParticleConflict", e);
		}
	}
}
