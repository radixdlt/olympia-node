package com.radixdlt.client.core.network.epics;

import com.radixdlt.client.core.network.RadixNetworkEpic;
import com.radixdlt.client.core.network.RadixNetworkState;
import com.radixdlt.client.core.network.RadixNodeAction;
import com.radixdlt.client.core.network.actions.FindANodeResultAction;
import com.radixdlt.client.core.network.actions.SubmitAtomReceivedAction;
import com.radixdlt.client.core.network.actions.SubmitAtomRequestAction;
import com.radixdlt.client.core.network.actions.SubmitAtomResultAction;
import com.radixdlt.client.core.network.actions.SubmitAtomSendAction;
import com.radixdlt.client.core.network.epics.WebSocketsEpic.WebSockets;
import com.radixdlt.client.core.network.jsonrpc.RadixJsonRpcClient;
import com.radixdlt.client.core.network.jsonrpc.RadixJsonRpcClient.NodeAtomSubmissionState;
import com.radixdlt.client.core.network.websocket.WebSocketClient;
import com.radixdlt.client.core.network.websocket.WebSocketException;
import com.radixdlt.client.core.network.websocket.WebSocketStatus;
import com.radixdlt.client.core.util.IncreasingRetryTimer;
import io.reactivex.Observable;
import java.util.concurrent.TimeUnit;

public class SubmitAtomEpic implements RadixNetworkEpic {
	private final WebSockets webSockets;

	public SubmitAtomEpic(WebSockets webSockets) {
		this.webSockets = webSockets;
	}

	@Override
	public Observable<RadixNodeAction> epic(Observable<RadixNodeAction> actions, Observable<RadixNetworkState> networkState) {
		return actions
			.filter(a -> a instanceof FindANodeResultAction)
			.map(FindANodeResultAction.class::cast)
			.filter(a -> a.getRequest() instanceof SubmitAtomRequestAction)
			.flatMap(u -> {
				final SubmitAtomRequestAction request = (SubmitAtomRequestAction) u.getRequest();
				final WebSocketClient ws = webSockets.get(u.getNode());
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
						return jsonRpcClient.submitAtom(request.getAtom())
							.doOnError(Throwable::printStackTrace)
							.map(nodeUpdate -> {
								if (nodeUpdate.getState().equals(NodeAtomSubmissionState.RECEIVED)) {
									return SubmitAtomReceivedAction.of(request.getUuid(), request.getAtom(), u.getNode());
								} else {
									return SubmitAtomResultAction.fromUpdate(request.getUuid(), request.getAtom(), u.getNode(), nodeUpdate);
								}
							})
							.startWith(SubmitAtomSendAction.of(request.getUuid(), request.getAtom(), u.getNode()))
							.retryWhen(new IncreasingRetryTimer(WebSocketException.class))
							// TODO: Better way of cleanup?
							.doFinally(() -> Observable.timer(5, TimeUnit.SECONDS).subscribe(t -> ws.close()));
					});
			});
	}
}
