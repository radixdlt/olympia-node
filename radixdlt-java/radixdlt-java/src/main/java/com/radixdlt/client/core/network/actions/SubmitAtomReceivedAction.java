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

import com.radixdlt.atom.Atom;
import com.radixdlt.client.core.network.RadixNode;
import java.util.Objects;

/**
 * A dispatchable action which signifies that an atom being submitted was received by the node.
 */
public class SubmitAtomReceivedAction implements SubmitAtomAction {
	private final String uuid;
	private final Atom atom;
	private final RadixNode node;

	private SubmitAtomReceivedAction(String uuid, Atom atom, RadixNode node) {
		Objects.requireNonNull(uuid);
		Objects.requireNonNull(atom);
		Objects.requireNonNull(node);

		this.uuid = uuid;
		this.atom = atom;
		this.node = node;
	}

	public static SubmitAtomReceivedAction of(String uuid, Atom atom, RadixNode node) {
		return new SubmitAtomReceivedAction(uuid, atom, node);
	}

	@Override
	public String getUuid() {
		return this.uuid;
	}

	@Override
	public Atom getAtom() {
		return this.atom;
	}

	@Override
	public RadixNode getNode() {
		return this.node;
	}

	@Override
	public String toString() {
		return "SUBMIT_ATOM_RECEIVED " + uuid + " " + atom.getAid() + " " + node;
	}
}
