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

package com.radixdlt.client.core.ledger;

import com.radixdlt.client.core.network.RadixNetworkController;
import com.radixdlt.client.core.network.actions.FetchAtomsAction;
import com.radixdlt.client.core.network.actions.FetchAtomsCancelAction;
import com.radixdlt.client.core.network.actions.FetchAtomsRequestAction;
import com.radixdlt.identifiers.RadixAddress;
import io.reactivex.Observable;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Module responsible for fetches and merges of new atoms into the Atom Store.
 */
public class RadixAtomPuller implements AtomPuller {

	/**
	 * Atoms retrieved from the network
	 */
	private final ConcurrentHashMap<RadixAddress, Observable<Object>> cache = new ConcurrentHashMap<>();

	/**
	 * The mechanism by which to fetch atoms
	 */
	private final RadixNetworkController controller;

	public RadixAtomPuller(RadixNetworkController controller) {
		this.controller = controller;
	}

	/**
	 * Fetches atoms and pushes them into the atom store. Multiple pulls on the same address
	 * will return a disposable to the same observable. As long as there is one subscriber to an
	 * address this will continue fetching and storing atoms.
	 *
	 * @param address shard address to get atoms from
	 * @return disposable to dispose to stop fetching
	 */
	@Override
	public Observable<Object> pull(RadixAddress address) {
		return cache.computeIfAbsent(
			address,
			addr ->
				Observable.create(emitter -> {
					final FetchAtomsAction initialAction = FetchAtomsRequestAction.newRequest(addr);

					emitter.setCancellable(() -> {
						controller.dispatch(FetchAtomsCancelAction.of(initialAction.getUuid(), initialAction.getAddress()));
					});

					controller.dispatch(initialAction);
				})
				.publish()
				.refCount()
		);
	}
}
