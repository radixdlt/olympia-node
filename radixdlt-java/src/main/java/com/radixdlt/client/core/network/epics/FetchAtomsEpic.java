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

import com.radixdlt.identifiers.RadixAddress;
import com.radixdlt.client.core.ledger.AtomObservation;
import com.radixdlt.client.core.network.RadixNetworkEpic;
import com.radixdlt.client.core.network.RadixNetworkState;
import com.radixdlt.client.core.network.RadixNode;
import com.radixdlt.client.core.network.RadixNodeAction;
import com.radixdlt.client.core.network.WebSockets;
import com.radixdlt.client.core.network.actions.FetchAtomsCancelAction;
import com.radixdlt.client.core.network.actions.FetchAtomsObservationAction;
import com.radixdlt.client.core.network.actions.FetchAtomsRequestAction;
import com.radixdlt.client.core.network.actions.FetchAtomsSubscribeAction;
import com.radixdlt.client.core.network.actions.FindANodeResultAction;
import com.radixdlt.client.core.network.jsonrpc.AtomQuery;
import com.radixdlt.client.core.network.jsonrpc.RadixJsonRpcClient;
import com.radixdlt.client.core.network.jsonrpc.RadixJsonRpcClient.NotificationType;
import com.radixdlt.client.core.network.websocket.WebSocketClient;
import com.radixdlt.client.core.network.websocket.WebSocketStatus;
import io.reactivex.Completable;
import io.reactivex.Observable;
import io.reactivex.disposables.Disposable;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

/**
 * Epic which emits atoms on a FETCH_ATOMS_REQUEST query forever until a FETCH_ATOMS_CANCEL action occurs.
 */
public final class FetchAtomsEpic implements RadixNetworkEpic {
	private static final int DELAY_CLOSE_SECS = 5;

	private final WebSockets webSockets;

	public FetchAtomsEpic(WebSockets webSockets) {
		this.webSockets = webSockets;
	}

	private Completable waitForConnection(RadixNode node) {
		final WebSocketClient ws = webSockets.getOrCreate(node);
		return ws.getState().doOnNext(s -> {
			if (s.equals(WebSocketStatus.DISCONNECTED)) {
				ws.connect();
			}
		})
		.filter(s -> s.equals(WebSocketStatus.CONNECTED))
		.firstOrError()
		.ignoreElement();
	}

	private Observable<RadixNodeAction> fetchAtoms(FetchAtomsRequestAction request, RadixNode node) {
		final WebSocketClient ws = webSockets.getOrCreate(node);
		final String uuid = request.getUuid();
		final RadixAddress address = request.getAddress();
		final RadixJsonRpcClient client = new RadixJsonRpcClient(ws);

		return client.observeAtoms(uuid)
			.<RadixNodeAction>flatMap(n -> {
				if (n.getType() == NotificationType.START) {
					AtomQuery atomQuery = new AtomQuery(address);
					return client.sendAtomsSubscribe(uuid, atomQuery).andThen(Observable.empty());
				} else {
					AtomObservation observation = n.getEvent();
					return Observable.just(FetchAtomsObservationAction.of(uuid, address, node, observation));
				}
			})
			.doOnDispose(() ->
				client.cancelAtomsSubscribe(uuid)
					.andThen(
						Observable.timer(DELAY_CLOSE_SECS, TimeUnit.SECONDS)
							.flatMapCompletable(t -> {
								ws.close();
								return Completable.complete();
							})
					).subscribe()
			)
			.startWith(FetchAtomsSubscribeAction.of(uuid, address, node));
	}

	@Override
	public Observable<RadixNodeAction> epic(Observable<RadixNodeAction> actions, Observable<RadixNetworkState> networkState) {
		// TODO: move these disposables into it's own observable
		final ConcurrentHashMap<String, Disposable> disposables = new ConcurrentHashMap<>();

		final Observable<RadixNodeAction> cancelFetch =
			actions.ofType(FetchAtomsCancelAction.class)
				.doOnNext(u -> {
					Disposable d = disposables.remove(u.getUuid());
					if (d != null) {
						d.dispose();
					}
				})
				.ignoreElements()
				.toObservable();

		final Observable<RadixNodeAction> fetch =
			actions.ofType(FindANodeResultAction.class)
				.filter(a -> a.getRequest() instanceof FetchAtomsRequestAction)
				.flatMap(nodeFound -> {
					final FetchAtomsRequestAction request = (FetchAtomsRequestAction) nodeFound.getRequest();
					final RadixNode node = nodeFound.getNode();
					return waitForConnection(node)
						.andThen(this.fetchAtoms(request, node))
						.doOnSubscribe(d -> disposables.put(request.getUuid(), d));
				});

		return Observable.merge(cancelFetch, fetch);
	}
}
