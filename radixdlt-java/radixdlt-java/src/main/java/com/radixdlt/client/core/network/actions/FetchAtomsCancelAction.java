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
import com.radixdlt.client.core.network.RadixNode;
import java.util.Objects;

/**
 * A dispatchable fetch atoms action which represents a request to cancel a fetch atoms flow
 */
public final class FetchAtomsCancelAction implements FetchAtomsAction {
	private final String uuid;
	private final RadixAddress address;

	private FetchAtomsCancelAction(String uuid, RadixAddress address) {
		Objects.requireNonNull(uuid);
		Objects.requireNonNull(address);

		this.uuid = uuid;
		this.address = address;
	}

	public static FetchAtomsCancelAction of(String uuid, RadixAddress address) {
		return new FetchAtomsCancelAction(uuid, address);
	}

	@Override
	public String getUuid() {
		return uuid;
	}

	@Override
	public RadixAddress getAddress() {
		return address;
	}

	// TODO: Get rid of this method. Maybe create a new RadixNetworkAction interface?
	@Override
	public RadixNode getNode() {
		throw new UnsupportedOperationException();
	}

	@Override
	public String toString() {
		return "FETCH_ATOMS_CANCEL " + uuid + " " + address;
	}
}
