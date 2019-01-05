package com.radixdlt.client.core.network.epics;

import com.radixdlt.client.core.network.AtomQuery;
import com.radixdlt.client.core.network.RadixClientStatus;
import com.radixdlt.client.core.network.RadixJsonRpcClient;
import com.radixdlt.client.core.network.RadixNetwork;
import com.radixdlt.client.core.network.RadixNetworkEpic;
import com.radixdlt.client.core.network.RadixNetworkState;
import com.radixdlt.client.core.network.RadixNodeAction;
import com.radixdlt.client.core.network.WebSocketClient;
import com.radixdlt.client.core.network.actions.AtomsFetchUpdate;
import com.radixdlt.client.core.network.actions.AtomsFetchUpdate.AtomsFetchState;
import com.radixdlt.client.core.network.actions.NodeUpdate;
import io.reactivex.Completable;
import io.reactivex.Observable;
import io.reactivex.ObservableSource;
import io.reactivex.Single;
import io.reactivex.disposables.Disposable;
import io.reactivex.functions.Function;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class AtomsFetchSubscriptionEpic implements RadixNetworkEpic {
	private static final Logger LOGGER = LoggerFactory.getLogger(AtomsFetchSubscriptionEpic.class);

	private final RadixNetwork network;

	public AtomsFetchSubscriptionEpic(RadixNetwork network) {
		this.network = network;
	}

	@Override
	public Observable<RadixNodeAction> epic(Observable<RadixNodeAction> updates, Observable<RadixNetworkState> networkState) {
		Observable<RadixNodeAction> nodeConnection = updates
			.filter(u -> u instanceof AtomsFetchUpdate)
			.map(AtomsFetchUpdate.class::cast)
			.filter(u -> u.getState().equals(AtomsFetchState.SUBMITTING))
			.map(u -> NodeUpdate.startConnect(u.getNode()));

		Observable<RadixNodeAction> fetch = updates
			.filter(u -> u instanceof AtomsFetchUpdate)
			.map(AtomsFetchUpdate.class::cast)
			.filter(update -> update.getState().equals(AtomsFetchState.SUBMITTING) || update.getState().equals(AtomsFetchState.ON_CANCEL))
			.flatMapSingle(update -> {
				if (update.getState().equals(AtomsFetchState.SUBMITTING)) {
					return networkState.filter(s -> s.getPeers().get(update.getNode()).equals(RadixClientStatus.CONNECTED)).firstOrError().map(s -> update);
				} else {
					return Single.just(update);
				}
			})
			.flatMap(new Function<AtomsFetchUpdate, ObservableSource<AtomsFetchUpdate>>() {
				private ConcurrentHashMap<String, Disposable> disposables = new ConcurrentHashMap<>();

				@Override
				public ObservableSource<AtomsFetchUpdate> apply(final AtomsFetchUpdate update) {
					if (update.getState().equals(AtomsFetchState.SUBMITTING)) {
						return Observable.<AtomsFetchUpdate>create(emitter -> {
							WebSocketClient ws = network.getWsChannel(update.getNode());
							RadixJsonRpcClient client = new RadixJsonRpcClient(ws);

							Disposable d = client.observeAtoms(update.getUuid())
								.map(observation -> AtomsFetchUpdate.observed(update.getUuid(), update.getAddress(), update.getNode(), observation))
								.subscribe(emitter::onNext);
							AtomQuery atomQuery = new AtomQuery(update.getAddress());
							client.sendAtomsSubscribe(update.getUuid(), atomQuery).subscribe();

							emitter.setCancellable(() -> {
								d.dispose();
								client.cancelAtomsSubscribe(update.getUuid())
									.andThen(Observable.timer(2, TimeUnit.SECONDS).flatMapCompletable(i -> {
										ws.close();
										return Completable.complete();
									}))
									.subscribe();
							});
						})
						.doOnSubscribe(d -> disposables.put(update.getUuid(), d));

					} else if (update.getState().equals(AtomsFetchState.ON_CANCEL)) {
						Disposable d = disposables.remove(update.getUuid());
						if (d != null) {
							d.dispose();
						}
					}

					return Observable.empty();
				}
			});

		return nodeConnection.mergeWith(fetch);
	}
}
