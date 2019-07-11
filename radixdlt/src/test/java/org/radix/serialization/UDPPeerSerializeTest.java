package org.radix.serialization;

import org.radix.network.Network;
import org.radix.network.peers.UDPPeer;

/**
 * Check serialization of UDPPeer
 */
public class UDPPeerSerializeTest extends SerializeMessageObject<UDPPeer> {
	public UDPPeerSerializeTest() {
		super(UDPPeer.class, UDPPeerSerializeTest::get);
	}

	private static UDPPeer get() {
		return new UDPPeer(Network.getURI("127.0.0.1", 30000), null);
	}
}
