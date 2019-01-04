package com.radixdlt.client.core.network;

import com.radixdlt.client.core.network.AtomSubmissionUpdate.AtomSubmissionState;
import io.reactivex.Observable;
import java.util.concurrent.TimeUnit;

public class SubmitToNodeEpic implements AtomSubmissionEpic {
	private final RadixNetwork network;

	public SubmitToNodeEpic(RadixNetwork network) {
		this.network = network;
	}

	@Override
	public Observable<AtomSubmissionUpdate> epic(Observable<AtomSubmissionUpdate> updates, Observable<RadixNetworkState> networkState) {
		return updates
			.filter(update -> update.getState().equals(AtomSubmissionState.SUBMITTING))
			.flatMapSingle(update -> {
				network.connect(update.getNode());
				return networkState.filter(s -> s.getPeers().get(update.getNode()).equals(RadixClientStatus.CONNECTED)).firstOrError().map(s -> update);
			})
			.flatMap(update -> {
				WebSocketClient ws = network.getWsChannel(update.getNode());
				return new RadixJsonRpcClient(ws).submitAtom(update.getAtom())
					.doOnError(Throwable::printStackTrace)
					.map(nodeUpdate -> AtomSubmissionUpdate.fromNodeUpdate(update.getAtom(), nodeUpdate, update.getNode()))
					.retryWhen(new IncreasingRetryTimer(WebSocketException.class))
					// TODO: Better way of cleanup?
					.doFinally(() -> Observable.timer(2, TimeUnit.SECONDS).subscribe(i -> ws.close()));
			});
	}
}
