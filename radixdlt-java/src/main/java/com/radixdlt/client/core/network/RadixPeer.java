package com.radixdlt.client.core.network;

import io.reactivex.Observable;
import io.reactivex.Single;
import io.reactivex.subjects.BehaviorSubject;
import io.reactivex.subjects.SingleSubject;
import okhttp3.Request;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Optional;
import java.util.Set;

public class RadixPeer {
	private final Logger logger = LoggerFactory.getLogger(RadixPeer.class);
	private final String location;
	private RadixJsonRpcClient radixClient;
	private final SingleSubject<NodeRunnerData> data;
	private final boolean useSSL;
	private final int port;
	private BehaviorSubject<RadixPeerState> status;
	private boolean connected;

	public RadixPeer(String location, boolean useSSL, int port) {
		this.data = SingleSubject.create();
		this.location = location;
		this.useSSL = useSSL;
		this.port = port;
		this.status = BehaviorSubject.createDefault(new RadixPeerState(location, port, RadixClientStatus.WAITING, null, null, null));
	}

	public RadixJsonRpcClient connect() {
		this.connect(this.location, this.useSSL, this.port);

		return this.radixClient;
	}

	private synchronized void connect(String location, boolean useSSL, int port) {
		if (this.connected) {
			return;
		}

		logger.info(String.format("Connecting to %s:%d %s", location, port, useSSL ? "with SSL" : "without SSL"));

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
		Observable.combineLatest(data.toObservable(), this.radixClient.status(),
				this.radixClient.universe().toObservable(), this.radixClient.apiVersion().toObservable(),
				(peerData, clientStatus, clientUniverse, clientVersion) ->
						new RadixPeerState(location, port, clientStatus, peerData, clientVersion, clientUniverse))
				.subscribe(this.status);

		this.connected = true;
	}

	public synchronized  void close() {
		if (!this.connected) {
			return;
		}

		logger.info(String.format("Closing connection to %s:%d %s", location, port, useSSL ? "with SSL" : "without SSL"));

		this.radixClient.tryClose();
		this.connected = false;
	}

	public synchronized boolean isConnected() {
		return connected;
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

	public Optional<RadixJsonRpcClient> getRadixClient() {
		return Optional.ofNullable(this.radixClient);
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

	@Override
	public String toString() {
		return location;
	}
}
