package org.radix.serialization;

import org.radix.modules.Modules;
import org.radix.network.messages.PeerPingMessage;

import java.security.SecureRandom;

/**
 * Check serialization of PeerPingMessage
 */
public class PeerPingMessageSerializeTest extends SerializeMessageObject<PeerPingMessage> {
	public PeerPingMessageSerializeTest() {
		super(PeerPingMessage.class, PeerPingMessageSerializeTest::get);
	}

	private static PeerPingMessage get() {
		return new PeerPingMessage(17L);
	}
}
