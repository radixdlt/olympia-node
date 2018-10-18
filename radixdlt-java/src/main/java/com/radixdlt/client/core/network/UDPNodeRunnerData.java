package com.radixdlt.client.core.network;

import org.radix.serialization2.SerializerId2;

@SerializerId2("UDPPEER")
public class UDPNodeRunnerData extends NodeRunnerData {
	UDPNodeRunnerData() {
		// No-arg constructor for serializer
	}

	public UDPNodeRunnerData(String ip, long lowShard, long highShard) {
		super(ip, lowShard, highShard);
	}

	protected UDPNodeRunnerData(RadixSystem system) {
		super(system);
	}
}
