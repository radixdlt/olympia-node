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

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.google.common.util.concurrent.RateLimiter;
import com.radixdlt.client.application.RadixApplicationAPI;
import com.radixdlt.client.application.translate.tokens.TransferTokensAction;
import com.radixdlt.client.application.translate.unique.PutUniqueIdAction;
import com.radixdlt.identifiers.EUID;
import com.radixdlt.identifiers.Rri;
import com.radixdlt.identifiers.RadixAddress;
import com.radixdlt.utils.Pair;

import java.math.BigDecimal;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicReference;

import io.reactivex.Observable;
import io.reactivex.disposables.CompositeDisposable;
import io.reactivex.disposables.Disposable;

/**
 * A service which sends tokens to whoever sends it a message through
 * a Radix Universe.
 */
final class FaucetHandler {
	private static final Logger log = LogManager.getLogger();

	private static final String UNIQUE_TX_PREFIX = "faucet-tx-";

	private final RadixApplicationAPI api;
	private final Rri tokenRri;
	private final BigDecimal amountToSend;
	private final RateLimiter rateLimiter;

	private Disposable disposable;

	FaucetHandler(RadixApplicationAPI api, Rri tokenRri, BigDecimal amountToSend, RateLimiter rateLimiter) {
		this.tokenRri = Objects.requireNonNull(tokenRri);
		this.api = Objects.requireNonNull(api);
		this.amountToSend = Objects.requireNonNull(amountToSend);
		this.rateLimiter = Objects.requireNonNull(rateLimiter);
	}

	/**
	 * Start and run the faucet service
	 */
	void run(Observable<Pair<RadixAddress, EUID>> requestSource) {
		if (this.disposable != null) {
			return;
		}

		log.info("Faucet token: {}", this.tokenRri);

		var leakDisposable = requestSource
			.doOnNext(p -> log.info("Request {} from: {}", p.getSecond(), p.getFirst())) // Print out all messages
			.subscribe(
				p -> this.leakFaucet(p.getFirst(), p.getSecond()),
				e -> log.error("Error while processing messages", e)
			);

		this.disposable = new CompositeDisposable(leakDisposable);
	}

	/**
	 * Stop the faucet service.
	 */
	void stop() {
		if (this.disposable != null) {
			this.disposable.dispose();
			this.disposable = null;
		}
	}

	/**
	 * Send tokens from this account to an address.
	 *
	 * @param recipient the intended recipient of the tokens
	 * @param actionId the mutex to use to ensure that the transaction is
	 * 	not processed more than once if multiple faucets exist in a network
	 */
	private void leakFaucet(RadixAddress recipient, EUID actionId) {
		try {
			log.info("Sending tokens to {}", recipient);
			long start = System.currentTimeMillis();

			var transaction = this.api.createTransaction();
			transaction.setMessage(String.format("Sent you %s %s", amountToSend, tokenRri.getName()));
			transaction.stage(TransferTokensAction.create(tokenRri, api.getAddress(), recipient, amountToSend));

			long now1 = System.currentTimeMillis() - start;
			log.info("Built transaction in {}ms", now1);

			var result = transaction.commitAndPush();

			long now2 = System.currentTimeMillis() - start;
			log.info("Transaction pushed in {}ms", now2);

			result.toCompletable().subscribe(
				() -> log.info("Sent tokens to {}", recipient),
				e -> { }
			);
		} catch (Exception e) {
			log.error("While sending tokens", e);
		}
	}

	private void handleRateLimiting(final RadixAddress recipient, final EUID actionId, final Rri mutexAcquire) {
		log.info("Rate limiting requests from {}", recipient);
		var hastyMsg = this.api.createTransaction();
		hastyMsg.setMessage(
			String.format(
				"Don't be hasty! Only %s requests per minute accepted.",
				this.rateLimiter.getRate() * 60.0
			)
		);
		hastyMsg.stage(PutUniqueIdAction.create(mutexAcquire));
		hastyMsg.commitAndPush().toObservable().subscribe(
			saa -> log.debug("Rate limit {} for {}: {}", recipient, actionId, saa),
			e -> log.error("Could not send rate limit message", e)
		);
	}

	private void logBalance(AtomicReference<BigDecimal> currentBalance, BigDecimal balance) {
		if (!currentBalance.getAndSet(balance).equals(balance)) {
			log.info("Faucet balance: {}", balance);
		}
	}
}
