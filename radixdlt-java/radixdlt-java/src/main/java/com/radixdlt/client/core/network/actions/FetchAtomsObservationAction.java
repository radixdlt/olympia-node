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

package com.radixdlt.client.core.network.actions;

import com.radixdlt.identifiers.RadixAddress;
import com.radixdlt.client.core.ledger.AtomObservation;
import com.radixdlt.client.core.network.RadixNode;
import java.util.Objects;

/**
 * A dispatchable fetch atoms action which represents an atom observed event from a specific node for an atom fetch flow.
 */
public final class FetchAtomsObservationAction implements FetchAtomsAction {
	private final String uuid;
	private final RadixAddress address;
	private final RadixNode node;
	private final AtomObservation observation;

	private FetchAtomsObservationAction(
		String uuid,
		RadixAddress address,
		RadixNode node,
		AtomObservation observation
	) {
		Objects.requireNonNull(uuid);
		Objects.requireNonNull(address);
		Objects.requireNonNull(node);
		Objects.requireNonNull(observation);

		this.uuid = uuid;
		this.address = address;
		this.node = node;
		this.observation = observation;
	}

	public static FetchAtomsObservationAction of(String uuid, RadixAddress address, RadixNode node, AtomObservation observation) {
		return new FetchAtomsObservationAction(uuid, address, node, observation);
	}

	@Override
	public String getUuid() {
		return uuid;
	}

	@Override
	public RadixAddress getAddress() {
		return address;
	}

	@Override
	public RadixNode getNode() {
		return node;
	}

	/**
	 * The atom observation associated with this action.
	 *
	 * @return the atom observation of the action
	 */
	public AtomObservation getObservation() {
		return observation;
	}

	@Override
	public String toString() {
		return "FETCH_ATOMS_OBSERVATION " + node + " " + uuid + " " + observation;
	}
}
