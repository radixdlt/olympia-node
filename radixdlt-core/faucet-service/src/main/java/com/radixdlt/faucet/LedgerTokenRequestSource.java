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

import com.radixdlt.client.application.RadixApplicationAPI;
import com.radixdlt.identifiers.RadixAddress;
import com.radixdlt.identifiers.EUID;
import java.util.Objects;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.radixdlt.utils.Pair;
import io.reactivex.Observable;

/**
 * Token request source via Ledger.
 */
final class LedgerTokenRequestSource implements TokenRequestSource {
	private static final Logger log = LogManager.getLogger();

	private final RadixApplicationAPI api;
	private final Observable<Pair<RadixAddress, EUID>> source;

	static LedgerTokenRequestSource create(RadixApplicationAPI api) {
		return new LedgerTokenRequestSource(api);
	}

	private LedgerTokenRequestSource(RadixApplicationAPI api) {
		this.api = Objects.requireNonNull(api);

		final RadixAddress sourceAddress = this.api.getAddress();

		log.info("Faucet address: {}", sourceAddress);

		this.source =  this.api.observeMessages()
			.filter(msg -> sourceAddress.equals(msg.getTo())) // Only messages sent to us
			.filter(msg -> notFromSelf(sourceAddress, msg.getFrom(), msg.getActionId())) // Don't send ourselves money
			.map(msg -> Pair.of(msg.getFrom(), msg.getActionId()));
	}

	private boolean notFromSelf(RadixAddress sourceAddress, RadixAddress address, EUID requestId) {
		boolean fromSelf = sourceAddress.equals(address);
		if (fromSelf) {
			log.debug("Ignoring request {} from self: {}", requestId, address);
		}
		return !fromSelf;
	}

	/**
	 * Get requests from ledger.
	 */
	@Override
	public Observable<Pair<RadixAddress, EUID>> requestSource() {
		return this.source;
	}
}
