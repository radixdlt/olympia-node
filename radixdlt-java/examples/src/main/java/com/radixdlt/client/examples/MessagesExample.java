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

package com.radixdlt.client.examples;

import com.radixdlt.client.application.RadixApplicationAPI;
import com.radixdlt.client.application.RadixApplicationAPI.Result;
import com.radixdlt.client.application.RadixApplicationAPI.Transaction;
import com.radixdlt.client.application.identity.RadixIdentities;
import com.radixdlt.client.application.identity.RadixIdentity;
import com.radixdlt.client.application.translate.unique.PutUniqueIdAction;
import com.radixdlt.identifiers.RRI;
import com.radixdlt.client.core.Bootstrap;

/**
 * Example showing how a message might be submitted to the ledger.
 * <p>
 * Note that this example is not currently completely functional,
 * as it creates a fresh identity which does not have any native
 * tokens required to pay fees for atom submission.
 */
public final class MessagesExample {
	private MessagesExample() {
		throw new IllegalStateException("Can't construct");
	}

	public static void main(String[] args) {
		// Create a new public key identity
		final RadixIdentity radixIdentity = RadixIdentities.createNew();

		// Initialize api layer
		final RadixApplicationAPI api = RadixApplicationAPI.create(Bootstrap.LOCALHOST, radixIdentity);

		// Sync with network
		api.pull();

		System.out.println("My address:    " + api.getAddress());
		System.out.println("My public key: " + api.getPublicKey());

		// Print out all past and future messages
		api.observeMessages().subscribe(System.out::println);

		// Send a message in an atom.  Note that the atom must not be empty,
		// so we include a random unique to make sure this isn't the case.
		final RRI rri = RRI.of(api.getAddress(), "test message");
		final Transaction t = api.createTransaction();
		t.setMessage("Hello!");
		t.stage(PutUniqueIdAction.create(rri));
		final Result result = t.commitAndPush();
		result.blockUntilComplete();
	}
}
