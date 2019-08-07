package org.radix.serialization;

import org.radix.network2.addressbook.PeerTimestamps;

public class PeerTimestampsSerializeTest extends SerializeObject<PeerTimestamps> {
	public PeerTimestampsSerializeTest() {
		super(PeerTimestamps.class, PeerTimestampsSerializeTest::getPeerTimestamps);
	}

	private static PeerTimestamps getPeerTimestamps() {
		long value = System.currentTimeMillis();
		return PeerTimestamps.of(value, value + 1);
	}

	@Override
	public void testNONEIsEmpty() {
		// Not applicable to PeerTimestamps
	}
}
