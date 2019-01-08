package com.radixdlt.client.core.network.epics;

import com.radixdlt.client.core.network.RadixNetworkEpic;
import com.radixdlt.client.core.network.RadixNetworkState;
import com.radixdlt.client.core.network.RadixNodeAction;
import com.radixdlt.client.core.network.actions.FetchAtomsCancelAction;
import com.radixdlt.client.core.network.actions.FetchAtomsObservationAction;
import com.radixdlt.client.core.network.actions.FetchAtomsRequestAction;
import com.radixdlt.client.core.network.actions.FetchAtomsSubscribeAction;
import com.radixdlt.client.core.network.actions.FindANodeResultAction;
import com.radixdlt.client.core.network.actions.SubmitAtomRequestAction;
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
				.filter(a -> a instanceof FindANodeResultAction)
				.map(FindANodeResultAction.class::cast)
				.filter(a -> a.getRequest() instanceof FetchAtomsRequestAction)
				.flatMap(nodeFound -> {
					final FetchAtomsRequestAction request = (FetchAtomsRequestAction) nodeFound.getRequest();

					final WebSocketClient ws = webSockets.get(nodeFound.getNode());
					return ws.getState()
						.doOnNext(s -> {
							if (s.equals(WebSocketStatus.DISCONNECTED)) {
								ws.connect();
							}
						})
						.filter(s -> s.equals(WebSocketStatus.CONNECTED))
						.firstOrError()
						.flatMapObservable(i ->
							Observable.<RadixNodeAction>create(emitter -> {
								RadixJsonRpcClient client = new RadixJsonRpcClient(ws);
								emitter.onNext(FetchAtomsSubscribeAction.of(request.getUuid(), request.getAddress(), nodeFound.getNode()));

								Disposable d = client.observeAtoms(request.getUuid())
									.map(observation -> FetchAtomsObservationAction.of(
										request.getUuid(),
										request.getAddress(), nodeFound.getNode(), observation))
									.subscribe(emitter::onNext);
								AtomQuery atomQuery = new AtomQuery(request.getAddress());
								client.sendAtomsSubscribe(request.getUuid(), atomQuery).subscribe();

								emitter.setCancellable(() -> {
									d.dispose();
									client.cancelAtomsSubscribe(request.getUuid())
										.andThen(
											Observable.timer(5, TimeUnit.SECONDS)
												.flatMapCompletable(t -> {
													ws.close();
													return Completable.complete();
												})
										).subscribe();
								});
							}).doOnSubscribe(d -> disposables.put(request.getUuid(), d))
						);
				});

		return Observable.merge(cancelFetch, fetch);
	}
}
