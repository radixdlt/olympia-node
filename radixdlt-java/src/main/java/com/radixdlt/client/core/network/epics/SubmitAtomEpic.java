package com.radixdlt.client.core.network.epics;

import com.google.gson.JsonObject;
import com.radixdlt.client.core.atoms.AtomStatus;
import com.radixdlt.client.core.atoms.AtomStatusNotification;
import com.radixdlt.client.core.network.RadixNetworkEpic;
import com.radixdlt.client.core.network.RadixNetworkState;
import com.radixdlt.client.core.network.RadixNode;
import com.radixdlt.client.core.network.RadixNodeAction;
import com.radixdlt.client.core.network.actions.FindANodeResultAction;
import com.radixdlt.client.core.network.actions.SubmitAtomReceivedAction;
import com.radixdlt.client.core.network.actions.SubmitAtomRequestAction;
import com.radixdlt.client.core.network.actions.SubmitAtomResultAction;
import com.radixdlt.client.core.network.actions.SubmitAtomSendAction;
import com.radixdlt.client.core.network.epics.WebSocketsEpic.WebSockets;
import com.radixdlt.client.core.network.jsonrpc.RadixJsonRpcClient;
import com.radixdlt.client.core.network.jsonrpc.RadixJsonRpcClient.NodeAtomSubmissionState;
import com.radixdlt.client.core.network.jsonrpc.RadixJsonRpcClient.NodeAtomSubmissionUpdate;
import com.radixdlt.client.core.network.websocket.WebSocketClient;
import com.radixdlt.client.core.network.websocket.WebSocketException;
import com.radixdlt.client.core.network.websocket.WebSocketStatus;
import com.radixdlt.client.core.util.IncreasingRetryTimer;
import io.reactivex.Completable;
import io.reactivex.Observable;
import io.reactivex.Single;
import io.reactivex.disposables.Disposable;
import io.reactivex.subjects.PublishSubject;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

/**
 * Epic which submits an atom to a node over websocket and emits events as the atom
 * is accepted by the network.
 */
public final class SubmitAtomEpic implements RadixNetworkEpic {
	private static final int DELAY_CLOSE_SECS = 5;

	private final WebSockets webSockets;

	public SubmitAtomEpic(WebSockets webSockets) {
		this.webSockets = webSockets;
	}

	private Completable waitForConnection(RadixNode node) {
		final WebSocketClient ws = webSockets.getOrCreate(node);
		return ws.getState()
			.doOnNext(s -> {
				if (s.equals(WebSocketStatus.DISCONNECTED)) {
					ws.connect();
				}
			})
			.filter(s -> s.equals(WebSocketStatus.CONNECTED))
			.firstOrError()
			.ignoreElement();
	}

	private Observable<RadixNodeAction> submitAtom(SubmitAtomSendAction request, RadixNode node) {
		final WebSocketClient ws = webSockets.getOrCreate(node);
		final RadixJsonRpcClient jsonRpcClient = new RadixJsonRpcClient(ws);
		final String subscriberId = UUID.randomUUID().toString();

		return Observable.<RadixNodeAction>create(emitter -> {
			Disposable d = jsonRpcClient.observeAtomStatusNotifications(subscriberId)
				.map(statusNotification -> {
					final AtomStatus status = statusNotification.getAtomStatus();
					final JsonObject data = statusNotification.getData();
					switch(status) {
						case STORED:
							return new NodeAtomSubmissionUpdate(NodeAtomSubmissionState.STORED, data);
						case EVICTED_CONFLICT_LOSER:
						case CONFLICT_LOSER:
							return new NodeAtomSubmissionUpdate(NodeAtomSubmissionState.COLLISION, data);
						case EVICTED_FAILED_CM_VERIFICATION:
							return new NodeAtomSubmissionUpdate(NodeAtomSubmissionState.VALIDATION_ERROR, data);
						default:
							return new NodeAtomSubmissionUpdate(NodeAtomSubmissionState.UNKNOWN_ERROR, data);
					}
				})
				.map(update -> SubmitAtomResultAction.fromUpdate(
					request.getUuid(),
					request.getAtom(),
					node,
					update
				))
				.subscribe(emitter::onNext, emitter::onError, emitter::onComplete);

			emitter.setCancellable(() -> {
				d.dispose();
				jsonRpcClient.closeAtomStatusNotifications(subscriberId)
					.andThen(
						Observable.timer(DELAY_CLOSE_SECS, TimeUnit.SECONDS)
							.flatMapCompletable(t -> {
								ws.close();
								return Completable.complete();
							})
					).subscribe();
			});

			jsonRpcClient.sendGetAtomStatusNotifications(subscriberId, request.getAtom().getAid())
					.andThen(jsonRpcClient.pushAtom(request.getAtom()))
					.subscribe(
						() -> emitter.onNext(SubmitAtomReceivedAction.of(request.getUuid(), request.getAtom(), node)),
						e -> emitter.onNext(
							SubmitAtomResultAction.fromUpdate(
								request.getUuid(),
								request.getAtom(),
								node,
								new NodeAtomSubmissionUpdate(NodeAtomSubmissionState.FAILED, new JsonObject())
							))
					);
		})
			.takeUntil(e -> e instanceof SubmitAtomResultAction)
			.doFinally(() -> Observable.timer(DELAY_CLOSE_SECS, TimeUnit.SECONDS).subscribe(t -> ws.close()));
	}

	@Override
	public Observable<RadixNodeAction> epic(Observable<RadixNodeAction> actions, Observable<RadixNetworkState> networkState) {
		final Observable<RadixNodeAction> foundNode =
			actions.ofType(FindANodeResultAction.class)
				.filter(a -> a.getRequest() instanceof SubmitAtomRequestAction)
				.map(a -> {
					final SubmitAtomRequestAction request = (SubmitAtomRequestAction) a.getRequest();
					return SubmitAtomSendAction.of(request.getUuid(), request.getAtom(), a.getNode());
				});

		final Observable<RadixNodeAction> submitToNode =
			actions.ofType(SubmitAtomSendAction.class)
				.flatMap(a -> {
					final RadixNode node = a.getNode();
					return waitForConnection(node)
						.andThen(this.submitAtom(a, node));
				});

		return foundNode.mergeWith(submitToNode);
	}
}
