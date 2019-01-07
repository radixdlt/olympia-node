package com.radixdlt.client.core.network.epics;

import com.radixdlt.client.core.network.jsonrpc.AtomQuery;
import com.radixdlt.client.core.util.IncreasingRetryTimer;
import com.radixdlt.client.core.network.jsonrpc.RadixJsonRpcClient;
import com.radixdlt.client.core.network.RadixNetworkEpic;
import com.radixdlt.client.core.network.RadixNode;
import com.radixdlt.client.core.network.RadixNodeAction;
import com.radixdlt.client.core.network.websocket.WebSocketStatus;
import com.radixdlt.client.core.network.websocket.WebSocketClient;
import com.radixdlt.client.core.network.websocket.WebSocketException;
import com.radixdlt.client.core.network.actions.AtomSubmissionUpdate;
import com.radixdlt.client.core.network.actions.AtomSubmissionUpdate.AtomSubmissionState;
import com.radixdlt.client.core.network.actions.AtomsFetchUpdate;
import com.radixdlt.client.core.network.actions.AtomsFetchUpdate.AtomsFetchState;
import com.radixdlt.client.core.network.actions.GetLivePeers;
import com.radixdlt.client.core.network.actions.GetLivePeers.GetLivePeersType;
import com.radixdlt.client.core.network.actions.GetNodeData;
import com.radixdlt.client.core.network.actions.GetNodeData.GetNodeDataType;
import com.radixdlt.client.core.network.actions.JsonRpcAction;
import com.radixdlt.client.core.network.actions.JsonRpcAction.JsonRpcActionType;
import io.reactivex.Completable;
import io.reactivex.Observable;
import io.reactivex.disposables.Disposable;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

public class RadixNodeJsonRpcEpics {
	private RadixNodeJsonRpcEpics() {
		throw new IllegalStateException("Cannot instantiate.");
	}

	public static RadixNetworkEpic autoCloseWebsocket(ConcurrentHashMap<RadixNode, WebSocketClient> websockets) {
		return (actions, stateObservable) ->
			actions
				.filter(a -> a instanceof JsonRpcAction)
				.map(JsonRpcAction.class::cast)
				.filter(a -> a.getJsonRpcActionType().equals(JsonRpcActionType.RESULT))
				.delay(5, TimeUnit.SECONDS)
				.doOnNext(a -> websockets.get(a.getNode()).close())
				.ignoreElements()
				.toObservable();
	}

	public static RadixNetworkEpic livePeers(ConcurrentHashMap<RadixNode, WebSocketClient> websockets) {
		return (actions, stateObservable) ->
			actions
				.filter(a -> a instanceof GetLivePeers)
				.map(GetLivePeers.class::cast)
				.filter(u -> u.getType().equals(GetLivePeersType.GET_LIVE_PEERS_REQUEST))
				.flatMapSingle(u -> {
					final WebSocketClient ws = websockets.get(u.getNode());
					return ws.getState()
						.doOnNext(s -> {
							if (s.equals(WebSocketStatus.DISCONNECTED)) {
								ws.connect();
							}
						})
						.filter(s -> s.equals(WebSocketStatus.CONNECTED))
						.firstOrError()
						.flatMap(i -> {
							RadixJsonRpcClient jsonRpcClient = new RadixJsonRpcClient(ws);
							return jsonRpcClient.getLivePeers()
								.map(l -> GetLivePeers.result(u.getNode(), l));
						});
				});
	}

	public static RadixNetworkEpic nodeData(ConcurrentHashMap<RadixNode, WebSocketClient> websockets) {
		return (actions, stateObservable) ->
			actions
				.filter(a -> a instanceof GetNodeData)
				.map(GetNodeData.class::cast)
				.filter(u -> u.getType().equals(GetNodeDataType.GET_NODE_DATA_REQUEST))
				.flatMapSingle(u -> {
					WebSocketClient ws = websockets.get(u.getNode());
					return ws.getState()
						.doOnNext(s -> {
							if (s.equals(WebSocketStatus.DISCONNECTED)) {
								ws.connect();
							}
						})
						.filter(s -> s.equals(WebSocketStatus.CONNECTED))
						.firstOrError()
						.flatMap(i -> {
							RadixJsonRpcClient jsonRpcClient = new RadixJsonRpcClient(ws);
							return jsonRpcClient.getInfo()
								.map(data -> GetNodeData.result(u.getNode(), data));
						});
				});
	}

	public static RadixNetworkEpic submitAtom(ConcurrentHashMap<RadixNode, WebSocketClient> websockets) {
		return (actions, stateObservable) ->
			actions
				.filter(u -> u instanceof AtomSubmissionUpdate)
				.map(AtomSubmissionUpdate.class::cast)
				.filter(update -> update.getState().equals(AtomSubmissionState.SUBMITTING))
				.flatMap(u -> {
					final WebSocketClient ws = websockets.get(u.getNode());
					return ws.getState()
						.doOnNext(s -> {
							if (s.equals(WebSocketStatus.DISCONNECTED)) {
								ws.connect();
							}
						})
						.filter(s -> s.equals(WebSocketStatus.CONNECTED))
						.firstOrError()
						.flatMapObservable(i -> {
							RadixJsonRpcClient jsonRpcClient = new RadixJsonRpcClient(ws);
							return jsonRpcClient.submitAtom(u.getAtom())
								.doOnError(Throwable::printStackTrace)
								.map(nodeUpdate -> AtomSubmissionUpdate.update(
									u.getUuid(),
									u.getAtom(),
									nodeUpdate,
									u.getNode()
								))
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
								if (s.equals(WebSocketStatus.DISCONNECTED)) {
									ws.connect();
								}
							})
							.filter(s -> s.equals(WebSocketStatus.CONNECTED))
							.firstOrError()
							.flatMapObservable(i ->
								Observable.<AtomsFetchUpdate>create(emitter -> {
									RadixJsonRpcClient client = new RadixJsonRpcClient(ws);

									Disposable d = client.observeAtoms(update.getUuid()).map(
										observation -> AtomsFetchUpdate.observed(
											update.getUuid(),
											update.getAddress(),
											update.getNode(),
											observation
										))
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
