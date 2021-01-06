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

package com.radixdlt.faucet;

import java.util.Random;

import org.junit.Before;
import org.junit.Test;

import com.radixdlt.client.application.RadixApplicationAPI;
import com.radixdlt.client.application.translate.data.DecryptedMessage;
import com.radixdlt.client.application.translate.data.DecryptedMessage.EncryptionState;
import com.radixdlt.crypto.ECKeyPair;
import com.radixdlt.identifiers.EUID;
import com.radixdlt.identifiers.RadixAddress;
import com.radixdlt.utils.Pair;

import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.reactivex.Observable;
import io.reactivex.observers.TestObserver;

public class LedgerTokenRequestSourceTest {
	private static final byte[] EMPTY_BYTES = new byte[] { };

	private Random random = new Random();
	private TestObserver<Pair<RadixAddress, EUID>> observer;
	private RadixApplicationAPI api;
	private RadixAddress from;
	private RadixAddress to;
	private EUID actionId;

	@Before
	public void setup() {
		this.api = mock(RadixApplicationAPI.class);
		this.from = new RadixAddress((byte) 0, ECKeyPair.generateNew().getPublicKey());
		this.to = new RadixAddress((byte) 0, ECKeyPair.generateNew().getPublicKey());
		this.actionId = randomEuid();
		this.observer = TestObserver.create();

		when(this.api.getAddress()).thenReturn(to);
	}

	@Test
	public void testGoodMessage() {
		DecryptedMessage msg = new DecryptedMessage(EMPTY_BYTES, this.from, this.to, EncryptionState.DECRYPTED, this.actionId);
		when(this.api.observeMessages()).thenReturn(Observable.just(msg));
		LedgerTokenRequestSource ltrs = LedgerTokenRequestSource.create(this.api);
		ltrs.requestSource().subscribe(this.observer);

		assertTrue(this.observer.awaitTerminalEvent());
		this.observer
			.assertNoTimeout()
			.assertNoErrors()
			.assertValueCount(1)
			.assertValue(Pair.of(from, actionId));
	}

	@Test
	public void testMessageFromSelf() {
		DecryptedMessage msg = new DecryptedMessage(EMPTY_BYTES, this.to, this.to, EncryptionState.DECRYPTED, this.actionId);
		when(this.api.observeMessages()).thenReturn(Observable.just(msg));
		LedgerTokenRequestSource ltrs = LedgerTokenRequestSource.create(this.api);
		ltrs.requestSource().subscribe(this.observer);

		assertTrue(this.observer.awaitTerminalEvent());
		this.observer
			.assertNoTimeout()
			.assertNoErrors()
			.assertNoValues();
	}

	private EUID randomEuid() {
		byte[] value = new byte[EUID.BYTES];
		this.random.nextBytes(value);
		return new EUID(value);
	}
}
