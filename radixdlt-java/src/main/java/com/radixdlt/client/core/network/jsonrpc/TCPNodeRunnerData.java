package com.radixdlt.client.core.network.jsonrpc;

import org.radix.serialization2.SerializerId2;

@SerializerId2("network.tcp_peer")
public class TCPNodeRunnerData extends NodeRunnerData {
	TCPNodeRunnerData() {
		// No-arg constructor for serializer
	}

	public TCPNodeRunnerData(String ip, long lowShard, long highShard) {
		super(ip, lowShard, highShard);
	}

	protected TCPNodeRunnerData(RadixSystem system) {
		super(system);
	}
}
