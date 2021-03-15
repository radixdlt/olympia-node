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

package com.radixdlt.client.application.translate.data;

import com.radixdlt.client.application.identity.RadixIdentity;
import com.radixdlt.client.application.translate.AtomToExecutedActionsMapper;
import com.radixdlt.atom.Atom;

import com.radixdlt.atom.Atoms;
import io.reactivex.Observable;

/**
 * Maps an atom to it's message, if any.
 */
public class AtomToPlaintextMessageMapper implements AtomToExecutedActionsMapper<PlaintextMessage> {
	@Override
	public Class<PlaintextMessage> actionClass() {
		return PlaintextMessage.class;
	}

	@Override
	public Observable<PlaintextMessage> map(Atom atom, RadixIdentity identity) {
		final var message = atom.getMessage();
		if (message == null) {
			return Observable.empty();
		}
		return Observable.just(new PlaintextMessage(Atoms.getAid(atom), message));
	}
}
