package com.radixdlt.client.core.network.epics;

import com.radixdlt.client.core.network.RadixNetworkEpic;
import com.radixdlt.client.core.network.RadixNetworkState;
import com.radixdlt.client.core.network.RadixNodeAction;
import com.radixdlt.client.core.network.actions.FetchAtomsCancelAction;
import com.radixdlt.client.core.network.actions.FetchAtomsObservationAction;
import com.radixdlt.client.core.network.actions.FetchAtomsSubscribeAction;
import com.radixdlt.client.core.network.epics.WebSocketsEpic.WebSockets;
import com.radixdlt.client.core.network.jsonrpc.AtomQuery;
import com.radixdlt.client.core.network.jsonrpc.RadixJsonRpcClient;
import com.radixdlt.client.core.network.websocket.WebSocketClient;
import com.radixdlt.client.core.network.websocket.WebSocketStatus;
import io.reactivex.Completable;
import io.reactivex.Observable;
import io.reactivex.disposables.Disposable;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

public class FetchAtomsEpic implements RadixNetworkEpic {
	private final WebSockets webSockets;

	public FetchAtomsEpic(WebSockets webSockets) {
		this.webSockets = webSockets;
	}

	@Override
	public Observable<RadixNodeAction> epic(Observable<RadixNodeAction> actions, Observable<RadixNetworkState> networkState) {
		final ConcurrentHashMap<String, Disposable> disposables = new ConcurrentHashMap<>();
		final Observable<RadixNodeAction> cancelFetch =
			actions
				.filter(u -> u instanceof FetchAtomsCancelAction)
				.map(FetchAtomsCancelAction.class::cast)
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
				.filter(u -> u instanceof FetchAtomsSubscribeAction)
				.map(FetchAtomsSubscribeAction.class::cast)
				.flatMap(action -> {
					final WebSocketClient ws = webSockets.get(action.getNode());
					return ws.getState()
						.doOnNext(s -> {
							if (s.equals(WebSocketStatus.DISCONNECTED)) {
								ws.connect();
							}
						})
						.filter(s -> s.equals(WebSocketStatus.CONNECTED))
						.firstOrError()
						.flatMapObservable(i ->
							Observable.<FetchAtomsObservationAction>create(emitter -> {
								RadixJsonRpcClient client = new RadixJsonRpcClient(ws);

								Disposable d = client.observeAtoms(action.getUuid()).map(
									observation -> FetchAtomsObservationAction.of(action.getUuid(), action.getAddress(), action.getNode(), observation))
									.subscribe(emitter::onNext);
								AtomQuery atomQuery = new AtomQuery(action.getAddress());
								client.sendAtomsSubscribe(action.getUuid(), atomQuery).subscribe();

								emitter.setCancellable(() -> {
									d.dispose();
									client.cancelAtomsSubscribe(action.getUuid())
										.andThen(
											Observable.timer(5, TimeUnit.SECONDS)
												.flatMapCompletable(t -> {
													ws.close();
													return Completable.complete();
												})
										).subscribe();
								});
							}).doOnSubscribe(d -> disposables.put(action.getUuid(), d))
						);
				});

		return Observable.merge(cancelFetch, fetch);
	}
}
