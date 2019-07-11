package org.radix.serialization;

import org.radix.network.handshake.VersionMessage;

/**
 * Check serialization of VersionMessage
 */
public class VersionMessageSerializeTest extends SerializeMessageObject<VersionMessage> {
	public VersionMessageSerializeTest() {
		super(VersionMessage.class, VersionMessageSerializeTest::get);
	}

	private static VersionMessage get() {
		return new VersionMessage();
	}
}
