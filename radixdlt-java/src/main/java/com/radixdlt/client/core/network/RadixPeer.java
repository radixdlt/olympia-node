package com.radixdlt.client.core.network;

import io.reactivex.Maybe;
import io.reactivex.Observable;
import io.reactivex.Single;
import io.reactivex.subjects.SingleSubject;
import java.util.Set;
import okhttp3.Request;

public class RadixPeer {
	private final String location;
	private final RadixJsonRpcClient radixClient;
	private final SingleSubject<NodeRunnerData> data;
	private final boolean useSSL;
	private final int port;
	private final Observable<RadixPeerState> status;

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

		this.status = Observable
				.combineLatest(data.toObservable(), this.radixClient.getStatus(), this.radixClient.universe().toObservable(), this.radixClient.apiVersion().toObservable(),
					(peerData, clientStatus, clientUniverse, clientVersion) -> RadixPeerState.from(this))
				.cache();
	}

	public Observable<RadixPeerState> status() {
		return this.status;
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

	public SingleSubject<NodeRunnerData> getData() {
		return data;
	}

	public Single<Boolean> servesShards(Set<Long> shards) {
		return data.map(d -> d.getShards().intersects(shards));
	}

	public Single<Boolean> servesShards(Set<Long> shards) {
		return data.map(d -> d.getShards().intersects(shards));
	}

	@Override
	public String toString() {
		return location;
	}
}
