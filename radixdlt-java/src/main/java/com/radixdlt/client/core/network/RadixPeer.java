package com.radixdlt.client.core.network;

import io.reactivex.Observable;
import io.reactivex.Single;
import io.reactivex.subjects.SingleSubject;
import okhttp3.Request;

import java.util.Optional;
import java.util.Set;

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

		// TODO this only yields state when all parts have given at least one value
		// - maybe should .startWith empty defaults to give step-by-step output
		this.status = Observable
				.combineLatest(data.toObservable(), this.radixClient.status(),
						this.radixClient.universe().toObservable(), this.radixClient.apiVersion().toObservable(),
					(peerData, clientStatus, clientUniverse, clientVersion) ->
						new RadixPeerState(location, port, clientStatus, peerData, clientVersion, clientUniverse))
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

	public RadixPeer setData(NodeRunnerData data) {
		this.data.onSuccess(data);
		return this;
	}

	public Single<NodeRunnerData> data() {
		return data;
	}

	public Optional<NodeRunnerData> getData() {
		return Optional.ofNullable(data.getValue());
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
