package org.radix.serialization;

import java.util.Arrays;

import org.radix.network.messages.PeersMessage;
import org.radix.network2.addressbook.Peer;
import org.radix.network2.addressbook.PeerImpl;

/**
 * Check serialization of PeersMessage
 */
public class PeersMessageSerializeTest extends SerializeMessageObject<PeersMessage> {
	public PeersMessageSerializeTest() {
		super(PeersMessage.class, PeersMessageSerializeTest::get);
	}

	private static PeersMessage get() {
		PeersMessage pm = new PeersMessage();
		Peer p1 = new PeerImpl();
		Peer p2 = new PeerImpl();
		Peer p3 = new PeerImpl();
		pm.setPeers(Arrays.asList(p1, p2, p3));
		return pm;
	}
}
