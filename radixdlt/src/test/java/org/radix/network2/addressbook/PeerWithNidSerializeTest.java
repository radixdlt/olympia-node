package org.radix.network2.addressbook;

import org.radix.serialization.SerializeMessageObject;
import com.radixdlt.common.EUID;

/**
 * Check serialization of PeerWithNid
 */
public class PeerWithNidSerializeTest extends SerializeMessageObject<PeerWithNid> {
	public PeerWithNidSerializeTest() {
		super(PeerWithNid.class, PeerWithNidSerializeTest::get);
	}

	private static PeerWithNid get() {
		return new PeerWithNid(EUID.ONE);
	}
}
