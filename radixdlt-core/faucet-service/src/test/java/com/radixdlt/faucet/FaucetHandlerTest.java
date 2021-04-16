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

import java.math.BigDecimal;
import java.util.Random;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.InOrder;

import com.google.common.util.concurrent.RateLimiter;
import com.radixdlt.client.application.RadixApplicationAPI;
import com.radixdlt.client.application.RadixApplicationAPI.Result;
import com.radixdlt.client.application.RadixApplicationAPI.Transaction;
import com.radixdlt.client.application.translate.tokens.TransferTokensAction;
import com.radixdlt.client.application.translate.unique.PutUniqueIdAction;
import com.radixdlt.crypto.ECKeyPair;
import com.radixdlt.identifiers.EUID;
import com.radixdlt.identifiers.RRI;
import com.radixdlt.identifiers.RadixAddress;
import com.radixdlt.utils.Pair;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import io.reactivex.Completable;
import io.reactivex.Observable;

public class FaucetHandlerTest {

	private Random random = new Random();
	private RadixApplicationAPI api;
	private RadixAddress from;
	private RadixAddress to;
	private RRI tokenRRI;
	private EUID actionId;
	private FaucetHandler handler;
	private Transaction transaction;
	private Result result;
	private RateLimiter rateLimiter;

	@Before
	public void setup() {
		this.api = mock(RadixApplicationAPI.class);
		this.from = new RadixAddress((byte) 0, ECKeyPair.generateNew().getPublicKey());
		this.to = new RadixAddress((byte) 0, ECKeyPair.generateNew().getPublicKey());
		this.tokenRRI = RRI.of(to, "XRD");
		this.actionId = randomEuid();
		this.transaction = mock(Transaction.class);
		this.result = mock(Result.class);
		this.rateLimiter = mock(RateLimiter.class);

		this.handler = new FaucetHandler(this.api, this.tokenRRI, BigDecimal.valueOf(1), this.rateLimiter);

		when(this.api.getAddress()).thenReturn(this.from);
		when(this.api.createTransaction()).thenReturn(this.transaction);

		when(this.transaction.commitAndPush()).thenReturn(this.result);
	}

	@After
	public void tearDown() {
		if (this.handler != null) {
			handler.stop();
		}
	}

	@Test
	public void testGoodRequest() {
		when(this.result.toCompletable()).thenReturn(Completable.complete());
		when(this.rateLimiter.tryAcquire()).thenReturn(true);
		this.handler.run(Observable.just(Pair.of(this.to, this.actionId)));

	    InOrder order = inOrder(this.transaction);
	    order.verify(this.transaction, times(1)).setMessage(any());
	    order.verify(this.transaction, times(1)).stage(any(TransferTokensAction.class));
	    order.verify(this.transaction, times(1)).stage(any(PutUniqueIdAction.class));
	    order.verify(this.transaction, times(1)).commitAndPush();
		verifyNoMoreInteractions(this.transaction);
	}

	@Test
	public void testRateLimited() {
		when(this.result.toObservable()).thenReturn(Observable.empty());
		when(this.rateLimiter.tryAcquire()).thenReturn(false);
		this.handler.run(Observable.just(Pair.of(this.to, this.actionId)));

	    InOrder order = inOrder(this.transaction);
	    order.verify(this.transaction, times(1)).setMessage(any());
		order.verify(this.transaction, times(1)).stage(any(PutUniqueIdAction.class));
		order.verify(this.transaction, times(1)).commitAndPush();
		verifyNoMoreInteractions(this.transaction);
	}

	@Test
	public void testCantSend() {
		when(this.result.toObservable()).thenReturn(Observable.empty());
		when(this.result.toCompletable()).thenReturn(Completable.error(new Exception("test exception")));
		when(this.rateLimiter.tryAcquire()).thenReturn(true);
		this.handler.run(Observable.just(Pair.of(this.to, this.actionId)));

	    InOrder order = inOrder(this.transaction);
	    order.verify(this.transaction, times(1)).setMessage(any());
	    order.verify(this.transaction, times(1)).stage(any(TransferTokensAction.class));
	    order.verify(this.transaction, times(1)).stage(any(PutUniqueIdAction.class));
	    order.verify(this.transaction, times(1)).commitAndPush();
	    order.verify(this.transaction, times(1)).setMessage(any());
	    order.verify(this.transaction, times(1)).stage(any(PutUniqueIdAction.class));
	    order.verify(this.transaction, times(1)).commitAndPush();
		verifyNoMoreInteractions(this.transaction);
	}

	private EUID randomEuid() {
		byte[] value = new byte[EUID.BYTES];
		this.random.nextBytes(value);
		return new EUID(value);
	}
}
