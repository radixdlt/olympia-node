package com.radixdlt.client.core.network;

import io.reactivex.Maybe;
import io.reactivex.subjects.SingleSubject;
import java.util.Set;
import okhttp3.Request;

public class RadixPeer {

	private final String location;
	private final RadixJsonRpcClient radixClient;
	private final SingleSubject<NodeRunnerData> data;
	private final boolean useSSL;
	private final int port;

	public RadixPeer(String location, boolean useSSL, int port) {
		this.data = SingleSubject.create();
		this.location = location;
		this.useSSL = useSSL;
		this.port = port;

		if (useSSL) {
			this.radixClient = new RadixJsonRpcClient(
				new WebSocketClient(
					HttpClients::getSslAllTrustingClient,
					new Request.Builder().url("wss://" + location + ":" + port + "/rpc").build()
				)
			);
		} else {
			this.radixClient = new RadixJsonRpcClient(
				new WebSocketClient(
					HttpClients::getSslAllTrustingClient,
					new Request.Builder().url("ws://" + location + ":" + port + "/rpc").build()
				)
			);
		}
	}

	public int getPort() {
		return port;
	}

	public boolean isSsl() {
		return useSSL;
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
