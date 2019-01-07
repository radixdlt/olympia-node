package com.radixdlt.client.core.network.epics;

import com.radixdlt.client.core.network.AtomQuery;
import com.radixdlt.client.core.network.IncreasingRetryTimer;
import com.radixdlt.client.core.network.RadixJsonRpcClient;
import com.radixdlt.client.core.network.RadixNetworkEpic;
import com.radixdlt.client.core.network.RadixNode;
import com.radixdlt.client.core.network.RadixNodeAction;
import com.radixdlt.client.core.network.RadixNodeStatus;
import com.radixdlt.client.core.network.WebSocketClient;
import com.radixdlt.client.core.network.WebSocketException;
import com.radixdlt.client.core.network.actions.AtomSubmissionUpdate;
import com.radixdlt.client.core.network.actions.AtomSubmissionUpdate.AtomSubmissionState;
import com.radixdlt.client.core.network.actions.AtomsFetchUpdate;
import com.radixdlt.client.core.network.actions.AtomsFetchUpdate.AtomsFetchState;
import com.radixdlt.client.core.network.actions.NodeUpdate;
import com.radixdlt.client.core.network.actions.NodeUpdate.NodeUpdateType;
import io.reactivex.Completable;
import io.reactivex.Observable;
import io.reactivex.disposables.Disposable;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

public class RadixNodeJsonRpcEpics {
	private RadixNodeJsonRpcEpics() {
		throw new IllegalStateException("Cannot instantiate.");
	}

	public static RadixNetworkEpic livePeers(ConcurrentHashMap<RadixNode,WebSocketClient> websockets) {
		return (actions, stateObservable) ->
			actions
				.filter(a -> a instanceof NodeUpdate)
				.map(NodeUpdate.class::cast)
				.filter(u -> u.getType().equals(NodeUpdateType.GET_LIVE_PEERS))
				.flatMap(u -> {
					final WebSocketClient ws = websockets.get(u.getNode());
					return ws.getState()
						.doOnNext(s -> {
							if (s.equals(RadixNodeStatus.DISCONNECTED)) {
								ws.connect();
							}
						})
						.filter(s -> s.equals(RadixNodeStatus.CONNECTED))
						.firstOrError()
						.flatMapObservable(i -> {
							RadixJsonRpcClient jsonRpcClient = new RadixJsonRpcClient(ws);
							return jsonRpcClient.getLivePeers()
								.toObservable()
								.flatMapIterable(p -> p)
								.map(data -> new RadixNode(data.getIp(), u.getNode().isSsl(), u.getNode().getPort()))
								.map(NodeUpdate::add);
						});
				});
	}

	public static RadixNetworkEpic nodeData(ConcurrentHashMap<RadixNode,WebSocketClient> websockets) {
		return (actions, stateObservable) ->
			actions
				.filter(a -> a instanceof NodeUpdate)
				.map(NodeUpdate.class::cast)
				.filter(u -> u.getType().equals(NodeUpdateType.GET_NODE_DATA))
				.flatMapSingle(u -> {
					WebSocketClient ws = websockets.get(u.getNode());
					return ws.getState()
						.doOnNext(s -> {
							if (s.equals(RadixNodeStatus.DISCONNECTED)) {
								ws.connect();
							}
						})
						.filter(s -> s.equals(RadixNodeStatus.CONNECTED))
						.firstOrError()
						.flatMap(i -> {
							RadixJsonRpcClient jsonRpcClient = new RadixJsonRpcClient(ws);
							return jsonRpcClient.getInfo()
								.map(data -> NodeUpdate.nodeData(u.getNode(), data));
						});
				});
	}

	public static RadixNetworkEpic submitAtom(ConcurrentHashMap<RadixNode,WebSocketClient> websockets) {
		return (actions, stateObservable) ->
			actions
				.filter(u -> u instanceof AtomSubmissionUpdate)
				.map(AtomSubmissionUpdate.class::cast)
				.filter(update -> update.getState().equals(AtomSubmissionState.SUBMITTING))
				.flatMap(u -> {
					final WebSocketClient ws = websockets.get(u.getNode());
					return ws.getState()
						.doOnNext(s -> {
							if (s.equals(RadixNodeStatus.DISCONNECTED)) {
								ws.connect();
							}
						})
						.filter(s -> s.equals(RadixNodeStatus.CONNECTED))
						.firstOrError()
						.flatMapObservable(i -> {
							RadixJsonRpcClient jsonRpcClient = new RadixJsonRpcClient(ws);
							return jsonRpcClient.submitAtom(u.getAtom())
								.doOnError(Throwable::printStackTrace)
								.map(nodeUpdate -> AtomSubmissionUpdate.update(u.getUuid(), u.getAtom(), nodeUpdate, u.getNode()))
								.retryWhen(new IncreasingRetryTimer(WebSocketException.class))
								// TODO: Better way of cleanup?
								.doFinally(() -> Observable.timer(5, TimeUnit.SECONDS).subscribe(t -> ws.close()));
						});
				});
	}

	public static RadixNetworkEpic fetchAtoms(ConcurrentHashMap<RadixNode, WebSocketClient> websockets) {
		return (actions, stateObservable) -> {
			final ConcurrentHashMap<String, Disposable> disposables = new ConcurrentHashMap<>();
			final Observable<RadixNodeAction> cancelFetch =
				actions
					.filter(u -> u instanceof AtomsFetchUpdate)
					.map(AtomsFetchUpdate.class::cast)
					.filter(u -> u.getState().equals(AtomsFetchState.ON_CANCEL))
					.doOnNext(u -> {
						Disposable d = disposables.remove(u.getUuid());
						if (d != null) {
							d.dispose();
						}
					})
					.ignoreElements()
					.toObservable();

			final Observable<RadixNodeAction> fetch =
				actions
					.filter(u -> u instanceof AtomsFetchUpdate)
					.map(AtomsFetchUpdate.class::cast)
					.filter(update -> update.getState().equals(AtomsFetchState.SUBMITTING))
					.flatMap(update -> {
						final WebSocketClient ws = websockets.get(update.getNode());
						return ws.getState()
							.doOnNext(s -> {
								if (s.equals(RadixNodeStatus.DISCONNECTED)) {
									ws.connect();
								}
							})
							.filter(s -> s.equals(RadixNodeStatus.CONNECTED))
							.firstOrError()
							.flatMapObservable(i ->
								Observable.<AtomsFetchUpdate>create(emitter -> {
									RadixJsonRpcClient client = new RadixJsonRpcClient(ws);

									Disposable d = client.observeAtoms(update.getUuid()).map(
										observation -> AtomsFetchUpdate.observed(update.getUuid(), update.getAddress(), update.getNode(), observation))
											.subscribe(emitter::onNext);
									AtomQuery atomQuery = new AtomQuery(update.getAddress());
									client.sendAtomsSubscribe(update.getUuid(), atomQuery).subscribe();

									emitter.setCancellable(() -> {
										d.dispose();
										client.cancelAtomsSubscribe(update.getUuid())
											.andThen(
												Observable.timer(5, TimeUnit.SECONDS)
													.flatMapCompletable(t -> {
														ws.close();
														return Completable.complete();
													})
											).subscribe();
									});
								}).doOnSubscribe(d -> disposables.put(update.getUuid(), d))
							);
					});

			return Observable.merge(cancelFetch, fetch);
		};
	}
}
