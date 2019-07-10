package com.radixdlt.serialization;

import org.radix.atoms.Atom;
import org.radix.atoms.messages.AtomDeliveryMessage;
import com.radixdlt.crypto.ECKeyPair;
import com.radixdlt.crypto.CryptoException;

/**
 * Check serialization of AtomDeliveryMessage
 */
public class AtomDeliveryMessageSerializeTest extends SerializeMessageObject<AtomDeliveryMessage> {
	public AtomDeliveryMessageSerializeTest() {
		super(AtomDeliveryMessage.class, AtomDeliveryMessageSerializeTest::get);
	}

	private static AtomDeliveryMessage get() {
		try {
			ECKeyPair key = new ECKeyPair();
			Atom atom = new Atom();
			atom.sign(key);
			return new AtomDeliveryMessage(atom);
		} catch (CryptoException e) {
			throw new IllegalStateException("Can't create AtomDeliveryMessage", e);
		}
	}
}
