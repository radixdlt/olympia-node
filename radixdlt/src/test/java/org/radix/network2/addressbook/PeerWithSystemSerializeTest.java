package org.radix.network2.addressbook;

import org.radix.serialization.SerializeMessageObject;

/**
 * Check serialization of PeerWithSystem
 */
public class PeerWithSystemSerializeTest extends SerializeMessageObject<PeerWithSystem> {
	public PeerWithSystemSerializeTest() {
		super(PeerWithSystem.class, PeerWithSystemSerializeTest::get);
	}

	private static PeerWithSystem get() {
		return new PeerWithSystem();
	}
}
