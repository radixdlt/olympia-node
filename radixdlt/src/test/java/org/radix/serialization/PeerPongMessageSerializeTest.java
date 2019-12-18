package org.radix.serialization;

import org.radix.network.messages.PeerPongMessage;

/**
 * Check serialization of UDPPongMessage
 */
public class PeerPongMessageSerializeTest extends SerializeMessageObject<PeerPongMessage> {
	public PeerPongMessageSerializeTest() {
		super(PeerPongMessage.class, PeerPongMessageSerializeTest::get);
	}

	private static PeerPongMessage get() {
		return new PeerPongMessage(0L, getLocalSystem());
	}
}
