package com.radixdlt.client.core.network.epics;

import com.radixdlt.client.core.network.actions.AtomSubmissionUpdate;
import com.radixdlt.client.core.network.actions.AtomSubmissionUpdate.AtomSubmissionState;
import com.radixdlt.client.core.network.IncreasingRetryTimer;
import com.radixdlt.client.core.network.RadixNodeStatus;
import com.radixdlt.client.core.network.RadixJsonRpcClient;
import com.radixdlt.client.core.network.RadixNetwork;
import com.radixdlt.client.core.network.RadixNetworkEpic;
import com.radixdlt.client.core.network.RadixNetworkState;
import com.radixdlt.client.core.network.RadixNodeAction;
import com.radixdlt.client.core.network.WebSocketClient;
import com.radixdlt.client.core.network.WebSocketException;
import com.radixdlt.client.core.network.actions.NodeUpdate;
import io.reactivex.Observable;
import java.util.concurrent.TimeUnit;

public class AtomSubmitSendToNodeEpic implements RadixNetworkEpic {
	private final RadixNetwork network;

	public AtomSubmitSendToNodeEpic(RadixNetwork network) {
		this.network = network;
	}

	@Override
	public Observable<RadixNodeAction> epic(Observable<RadixNodeAction> updates, Observable<RadixNetworkState> networkState) {
		Observable<RadixNodeAction> nodeConnect = updates
			.filter(u -> u instanceof AtomSubmissionUpdate)
			.map(AtomSubmissionUpdate.class::cast)
			.filter(update -> update.getState().equals(AtomSubmissionState.SUBMITTING))
			.map(u -> NodeUpdate.startConnect(u.getNode()));

		Observable<RadixNodeAction> submission = updates
			.filter(u -> u instanceof AtomSubmissionUpdate)
			.map(AtomSubmissionUpdate.class::cast)
			.filter(update -> update.getState().equals(AtomSubmissionState.SUBMITTING))
			.flatMapSingle(update ->
				networkState.filter(s -> s.getPeers().get(update.getNode()).equals(RadixNodeStatus.CONNECTED)).firstOrError().map(s -> update)
			)
			.flatMap(update -> {
				WebSocketClient ws = network.getWsChannel(update.getNode());
				return new RadixJsonRpcClient(ws).submitAtom(update.getAtom())
					.doOnError(Throwable::printStackTrace)
					.map(nodeUpdate -> AtomSubmissionUpdate.update(update.getUuid(), update.getAtom(), nodeUpdate, update.getNode()))
					.retryWhen(new IncreasingRetryTimer(WebSocketException.class))
					// TODO: Better way of cleanup?
					.doFinally(() -> Observable.timer(2, TimeUnit.SECONDS).subscribe(i -> ws.close()));
			});

		return nodeConnect.mergeWith(submission);
	}
}
