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

import com.radixdlt.atom.ClientAtom;
import com.radixdlt.client.core.network.RadixNode;
import com.radixdlt.client.core.network.RadixNodeAction;
import java.util.Objects;

/**
 * A dispatchable action which signifies to send an atom to a node
 */
public final class SubmitAtomSendAction implements SubmitAtomAction, RadixNodeAction {
	private final String uuid;
	private final ClientAtom atom;
	private final RadixNode node;
	private final boolean completeOnStoreOnly;

	private SubmitAtomSendAction(String uuid, ClientAtom atom, RadixNode node, boolean completeOnStoreOnly) {
		Objects.requireNonNull(uuid);
		Objects.requireNonNull(atom);
		Objects.requireNonNull(node);

		this.uuid = uuid;
		this.atom = atom;
		this.node = node;
		this.completeOnStoreOnly = completeOnStoreOnly;
	}

	public static SubmitAtomSendAction of(String uuid, ClientAtom atom, RadixNode node, boolean completeOnStoreOnly) {
		return new SubmitAtomSendAction(uuid, atom, node, completeOnStoreOnly);
	}

	public boolean isCompleteOnStoreOnly() {
		return completeOnStoreOnly;
	}

	@Override
	public String getUuid() {
		return uuid;
	}

	@Override
	public ClientAtom getAtom() {
		return atom;
	}

	@Override
	public RadixNode getNode() {
		return node;
	}

	@Override
	public String toString() {
		return "SUBMIT_ATOM_SEND " + uuid + " " + atom + " " + node;
	}
}
