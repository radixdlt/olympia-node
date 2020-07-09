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

import com.radixdlt.client.core.atoms.Atom;
import com.radixdlt.client.core.network.RadixNode;
import java.util.Objects;
import java.util.UUID;

/**
 * The initial dispatchable action to begin an atom submission flow.
 */
public final class SubmitAtomRequestAction implements SubmitAtomAction, FindANodeRequestAction {
	private final String uuid;
	private final Atom atom;
	private final boolean completeOnStoreOnly;

	private SubmitAtomRequestAction(String uuid, Atom atom, boolean completeOnStoreOnly) {
		Objects.requireNonNull(uuid);
		Objects.requireNonNull(atom);

		this.uuid = uuid;
		this.atom = atom;
		this.completeOnStoreOnly = completeOnStoreOnly;
	}

	public static SubmitAtomRequestAction newRequest(Atom atom, boolean completeOnStoreOnly) {
		return new SubmitAtomRequestAction(UUID.randomUUID().toString(), atom, completeOnStoreOnly);
	}

	public boolean isCompleteOnStoreOnly() {
		return completeOnStoreOnly;
	}

	@Override
	public String getUuid() {
		return uuid;
	}

	@Override
	public Atom getAtom() {
		return atom;
	}

	// TODO: Get rid of this method. Maybe create a new RadixNetworkAction interface?
	@Override
	public RadixNode getNode() {
		throw new UnsupportedOperationException();
	}


	@Override
	public String toString() {
		return "SUBMIT_ATOM_REQUEST " + uuid + " " + atom.getAid();
	}
}
