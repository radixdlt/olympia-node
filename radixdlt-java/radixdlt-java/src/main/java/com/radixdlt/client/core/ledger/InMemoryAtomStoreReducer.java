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

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.radixdlt.client.core.atoms.Addresses;
import com.radixdlt.client.core.atoms.Atom;
import com.radixdlt.client.core.atoms.AtomStatus;
import com.radixdlt.client.core.network.RadixNodeAction;
import com.radixdlt.client.core.network.actions.FetchAtomsObservationAction;
import com.radixdlt.client.core.network.actions.SubmitAtomStatusAction;

/**
 * Wrapper reducer class for the Atom Store
 */
public final class InMemoryAtomStoreReducer {
	private final InMemoryAtomStore atomStore;

	public InMemoryAtomStoreReducer(InMemoryAtomStore atomStore) {
		this.atomStore = atomStore;
	}

	public void reduce(RadixNodeAction action) {
		if (action instanceof FetchAtomsObservationAction) {
			FetchAtomsObservationAction fetchAtomsObservationAction = (FetchAtomsObservationAction) action;
			AtomObservation observation = fetchAtomsObservationAction.getObservation();
			this.atomStore.store(fetchAtomsObservationAction.getAddress(), observation);
		} else if (action instanceof SubmitAtomStatusAction) {

			// Soft storage of atoms so that atoms which are submitted and stored can
			// be immediately used instead of having to wait for fetch atom events.
			final SubmitAtomStatusAction submitAtomStatusAction = (SubmitAtomStatusAction) action;
			final Atom atom = submitAtomStatusAction.getAtom();

			if (submitAtomStatusAction.getStatusNotification().getAtomStatus() == AtomStatus.STORED) {
				final long timestamp = retrieveTimestamp(submitAtomStatusAction.getStatusNotification().getData());
				Addresses.ofAtom(atom).forEach(address -> {
					this.atomStore.store(address, AtomObservation.softStored(atom, timestamp));
					this.atomStore.store(address, AtomObservation.head());
				});
			}
		}
	}

	private static long retrieveTimestamp(JsonObject data) {
		if (data != null) {
			JsonElement jsonTimestamp = data.get("timestamp");
			if (jsonTimestamp != null) {
				return jsonTimestamp.getAsLong();
			}
		}
		return Long.MIN_VALUE;
	}
}
