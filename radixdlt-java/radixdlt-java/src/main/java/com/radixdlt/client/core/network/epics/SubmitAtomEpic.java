/*
 * (C) Copyright 2020 Radix DLT Ltd
 *
 * Permission is hereby granted, free of charge, to any person obtaining a
 * copy of this software and associated documentation files (the “Software”),
 * to deal in the Software without restriction, including without limitation
 * the rights to use, copy, modify, merge, publish, distribute, sublicense,
 * and/or sell copies of the Software, and to permit persons to whom the
 * Software is furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be
 * included in all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED “AS IS”, WITHOUT WARRANTY OF ANY KIND, EXPRESS
 * OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL
 * THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING
 * FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER
 * DEALINGS IN THE SOFTWARE.
 */

package com.radixdlt.client.core.network.epics;

import com.radixdlt.client.core.atoms.AtomStatus;
import com.radixdlt.client.core.atoms.AtomStatusEvent;
import com.radixdlt.client.core.network.RadixNetworkEpic;
import com.radixdlt.client.core.network.RadixNetworkState;
import com.radixdlt.client.core.network.RadixNode;
import com.radixdlt.client.core.network.RadixNodeAction;
import com.radixdlt.client.core.network.WebSockets;
import com.radixdlt.client.core.network.actions.FindANodeResultAction;
import com.radixdlt.client.core.network.actions.SubmitAtomCompleteAction;
import com.radixdlt.client.core.network.actions.SubmitAtomReceivedAction;
import com.radixdlt.client.core.network.actions.SubmitAtomRequestAction;
import com.radixdlt.client.core.network.actions.SubmitAtomStatusAction;
import com.radixdlt.client.core.network.actions.SubmitAtomSendAction;
import com.radixdlt.client.core.network.jsonrpc.RadixJsonRpcClient;
import com.radixdlt.client.core.network.jsonrpc.RadixJsonRpcClient.NotificationType;
import com.radixdlt.client.core.network.jsonrpc.SubmitAtomException;
import com.radixdlt.client.core.network.websocket.WebSocketClient;
import com.radixdlt.client.core.network.websocket.WebSocketStatus;
import io.reactivex.Completable;
import io.reactivex.Observable;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

/**
 * Epic which submits an atom to a node over websocket and emits events as the atom
 * is accepted by the network.
 */
public final class SubmitAtomEpic implements RadixNetworkEpic {
	private static final int DELAY_CLOSE_SECS = 5;

	private final WebSockets webSockets;
	private final int timeoutSecs = 30;

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

		return jsonRpcClient.observeAtomStatusNotifications(subscriberId)
			.flatMap(notification -> {
				if (notification.getType().equals(NotificationType.START)) {
					return jsonRpcClient.sendGetAtomStatusNotifications(subscriberId, request.getAtom().getAid())
						.andThen(jsonRpcClient.pushAtom(request.getAtom()))
						.andThen(Observable.<RadixNodeAction>just(
							SubmitAtomReceivedAction.of(request.getUuid(), request.getAtom(), node)
						))
						.onErrorResumeNext(e -> {
							if (e instanceof SubmitAtomException) {
								SubmitAtomException submitAtomException = (SubmitAtomException) e;
								return Observable.<RadixNodeAction>just(
									SubmitAtomStatusAction.fromStatusNotification(
										request.getUuid(),
										request.getAtom(),
										node,
										new AtomStatusEvent(
											AtomStatus.EVICTED_INVALID_ATOM,
											submitAtomException.getData()
										)),
									SubmitAtomCompleteAction.of(
										request.getUuid(),
										request.getAtom(),
										node
									));
							} else {
								return Observable.error(e);
							}
						});
				} else {
					AtomStatusEvent statusNotification = notification.getEvent();
					RadixNodeAction statusEvent = SubmitAtomStatusAction.fromStatusNotification(
						request.getUuid(),
						request.getAtom(),
						node,
						statusNotification
					);
					if (statusNotification.getAtomStatus() == AtomStatus.STORED || !request.isCompleteOnStoreOnly()) {
						return Observable.just(
							statusEvent,
							SubmitAtomCompleteAction.of(request.getUuid(), request.getAtom(), node)
						);
					} else {
						return Observable.just(statusEvent);
					}
				}
			})
			.doOnDispose(() -> {
				jsonRpcClient.closeAtomStatusNotifications(subscriberId)
					.andThen(
						Observable.timer(DELAY_CLOSE_SECS, TimeUnit.SECONDS)
							.flatMapCompletable(t -> {
								ws.close();
								return Completable.complete();
							})
					).subscribe();
			})
			.timeout(
				timeoutSecs,
				TimeUnit.SECONDS,
				Observable.just(SubmitAtomCompleteAction.of(request.getUuid(), request.getAtom(), node))
			)
			.takeUntil(e -> e instanceof SubmitAtomCompleteAction)
			.doFinally(() -> Observable.timer(DELAY_CLOSE_SECS, TimeUnit.SECONDS).subscribe(t -> ws.close()));
	}

	@Override
	public Observable<RadixNodeAction> epic(Observable<RadixNodeAction> actions, Observable<RadixNetworkState> networkState) {
		final Observable<RadixNodeAction> foundNode =
			actions.ofType(FindANodeResultAction.class)
				.filter(a -> a.getRequest() instanceof SubmitAtomRequestAction)
				.map(a -> {
					final SubmitAtomRequestAction request = (SubmitAtomRequestAction) a.getRequest();
					return SubmitAtomSendAction.of(
						request.getUuid(),
						request.getAtom(),
						a.getNode(),
						request.isCompleteOnStoreOnly());
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
