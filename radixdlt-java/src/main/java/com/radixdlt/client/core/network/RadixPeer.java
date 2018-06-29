package com.radixdlt.client.core.network;

import io.reactivex.Maybe;
import io.reactivex.subjects.SingleSubject;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class RadixPeer {
	private final static Logger logger = LoggerFactory.getLogger(RadixPeer.class);

	private final String location;
	private final RadixJsonRpcClient radixClient;
	private final SingleSubject<NodeRunnerData> data;

	public RadixPeer(String location, boolean useSSL, int port) {
		this.data = SingleSubject.create();
		this.location = location;

		if (useSSL) {
			this.radixClient = new RadixJsonRpcClient(new WebSocketClient(HttpClients::get, "wss://" + location + ":" + port + "/rpc"));
		} else {
			this.radixClient = new RadixJsonRpcClient(new WebSocketClient(HttpClients::get, "ws://" + location + ":" + port + "/rpc"));
		}
	}

	public String getLocation() {
		return location;
	}

	public RadixJsonRpcClient getRadixClient() {
		return radixClient;
	}

	public RadixPeer data(NodeRunnerData data) {
		this.data.onSuccess(data);
		return this;
	}

	public Maybe<RadixPeer> servesShards(Set<Long> shards) {
		return data.map(d -> d.getShards().intersects(shards)).flatMapMaybe(intersects -> intersects ? Maybe.just(this) : Maybe.empty());
	}

	@Override
	public String toString() {
		return location;
	}
}
