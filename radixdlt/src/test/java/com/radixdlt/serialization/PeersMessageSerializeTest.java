package com.radixdlt.serialization;

import java.util.Arrays;

import org.radix.network.Network;
import org.radix.network.messages.PeersMessage;
import org.radix.network.peers.Peer;

/**
 * Check serialization of PeersMessage
 */
public class PeersMessageSerializeTest extends SerializeMessageObject<PeersMessage> {
	public PeersMessageSerializeTest() {
		super(PeersMessage.class, PeersMessageSerializeTest::get);
	}

	private static PeersMessage get() {
		PeersMessage pm = new PeersMessage();
		Peer p1 = new Peer(Network.getURI("127.0.0.1", 30000));
		Peer p2 = new Peer(Network.getURI("127.0.0.2", 30000));
		Peer p3 = new Peer(Network.getURI("127.0.0.3", 30000));
		pm.setPeers(Arrays.asList(p1, p2, p3));
		return pm;
	}
}
