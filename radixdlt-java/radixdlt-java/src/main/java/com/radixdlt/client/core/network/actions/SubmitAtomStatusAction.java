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
import com.radixdlt.client.core.atoms.AtomStatusEvent;
import com.radixdlt.client.core.network.RadixNode;
import java.util.Objects;

/**
 * A dispatchable event action which signifies the end result of an atom submission flow
 */
public final class SubmitAtomStatusAction implements SubmitAtomAction {
	private final String uuid;
	private final ClientAtom atom;
	private final RadixNode node;
	private final AtomStatusEvent statusNotification;

	private SubmitAtomStatusAction(String uuid, ClientAtom atom, RadixNode node, AtomStatusEvent statusNotification) {
		this.uuid = Objects.requireNonNull(uuid);
		this.atom = Objects.requireNonNull(atom);
		this.node = Objects.requireNonNull(node);
		this.statusNotification = Objects.requireNonNull(statusNotification);
	}

	public static SubmitAtomStatusAction fromStatusNotification(
		String uuid, ClientAtom atom, RadixNode node, AtomStatusEvent statusNotification
	) {
		return new SubmitAtomStatusAction(uuid, atom, node, statusNotification);
	}

	/**
	 * The end result type of the atom submission
	 *
	 * @return The end result type
	 */
	public AtomStatusEvent getStatusNotification() {
		return this.statusNotification;
	}

	@Override
	public String getUuid() {
		return this.uuid;
	}

	@Override
	public ClientAtom getAtom() {
		return this.atom;
	}

	@Override
	public RadixNode getNode() {
		return this.node;
	}

	@Override
	public String toString() {
		return "SUBMIT_ATOM_STATUS " + this.uuid + " " + this.atom + " " + this.node + " " + this.statusNotification;
	}
}
