/*
 * (C) Copyright 2021 Radix DLT Ltd
 *
 * Radix DLT Ltd licenses this file to you under the Apache License,
 * Version 2.0 (the "License"); you may not use this file except in
 * compliance with the License.  You may obtain a copy of the
 * License at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND,
 * either express or implied.  See the License for the specific
 * language governing permissions and limitations under the License.
 */

package com.radix.test.utils;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.math.BigDecimal;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import com.radixdlt.constraintmachine.Spin;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.google.common.collect.ImmutableList;
import com.radixdlt.client.application.RadixApplicationAPI;
import com.radixdlt.client.application.identity.RadixIdentity;
import com.radixdlt.atommodel.unique.UniqueParticle;
import com.radixdlt.client.core.RadixEnv;
import com.radixdlt.client.core.atoms.Atom;
import com.radixdlt.client.core.ledger.AtomObservation;
import com.radixdlt.identifiers.EUID;
import com.radixdlt.identifiers.RRI;
import com.radixdlt.identifiers.RadixAddress;
import io.reactivex.Observable;
import io.reactivex.disposables.Disposable;
import static org.assertj.core.api.Assertions.assertThat;

public final class TokenUtilities {
	private static final Logger log = LogManager.getLogger();
	private static final String FAUCET_UNIQUE_SEND_TOKENS_PREFIX = "faucet-tx-";

	// Number of times to retry if faucet fails
	private static final int MAX_RETRY_COUNT = 10;

	private TokenUtilities() {
		throw new IllegalStateException("Can't construct");
	}

	public static boolean isFaucetAtomObservation(AtomObservation atomObs) {
		// Atom must have a UniqueParticle, and the name must start with one of the faucet prefixes
		return atomObs.hasAtom() && atomObs.getAtom().particles(Spin.UP)
			.filter(UniqueParticle.class::isInstance)
			.map(UniqueParticle.class::cast)
			.map(UniqueParticle::getRRI)
			.findAny()
			.map(rri -> rri.getName().startsWith(FAUCET_UNIQUE_SEND_TOKENS_PREFIX))
			.orElse(false);
	}

	public static boolean isNotFaucetAtomObservation(AtomObservation atomObs) {
		return !isFaucetAtomObservation(atomObs);
	}

	public static void requestTokensFor(RadixIdentity identity) {
		requestTokensFor(RadixApplicationAPI.create(RadixEnv.getBootstrapConfig(), identity));
	}

	public static synchronized void requestTokensFor(RadixApplicationAPI api) {
		// Ensure balances up to date
		api.pullOnce(api.getAddress()).blockingAwait();
		final RRI tokenRri = api.getNativeTokenRef();
		final BigDecimal initialBalance = api.getBalances().getOrDefault(tokenRri, BigDecimal.ZERO);
		log.debug("RequestTokens: initial balance {}", initialBalance);

		// Keep updating balances
		Disposable d = api.pull();
		Atom dummyAtom = Atom.create(ImmutableList.of());
		try {
			long waitDelayMs = 1000L;
			delayForMs(waitDelayMs);
			for (int i = 0; i < MAX_RETRY_COUNT; ++i) {
				EUID requestId = requestTokens(api.getAddress());

				// Wait until we see the TX from the ledger
				Atom txAtom = api.getAtomStore().getAtomObservations(api.getAddress())
					.filter(AtomObservation::hasAtom)
					.map(AtomObservation::getAtom)
					.filter(atom -> hasTxId(atom, requestId))
					.timeout(waitDelayMs, TimeUnit.MILLISECONDS, Observable.just(dummyAtom))
					.blockingFirst();

				if (txAtom != dummyAtom) {
					String msg = getMessageFrom(txAtom);
					if (msg != null && msg.startsWith("Sent you ")) {
						api.pullOnce(api.getAddress()).blockingAwait();
						final BigDecimal finalBalance = api.getBalances().getOrDefault(tokenRri, BigDecimal.ZERO);
						log.debug("RequestTokens: balance now {}", finalBalance);
						assertThat(finalBalance).isGreaterThan(initialBalance);
						return;
					}
					// Probably a hasty message or faucet unsynced.  We need to back off here.
					log.info("Got message from faucet: {}", msg);
					delayForMs(waitDelayMs);
				}
				waitDelayMs += 1000L;
				log.info("Faucet failed, retrying with {}ms wait...", waitDelayMs);
			}
			throw new AssertionError("Retried too many times");
		} finally {
			d.dispose();
		}
	}

	private static String getMessageFrom(Atom txAtom) {
		return txAtom.getMessage();
	}

	private static boolean hasTxId(Atom atom, EUID requestId) {
		String txId = FAUCET_UNIQUE_SEND_TOKENS_PREFIX + requestId;
    	return atom.particles(Spin.UP)
	    	.filter(UniqueParticle.class::isInstance)
	    	.map(UniqueParticle.class::cast)
	    	.anyMatch(up -> up.getRRI().getName().equals(txId));
	}

	private static void delayForMs(long waitDelayMs) {
		try {
			TimeUnit.MILLISECONDS.sleep(waitDelayMs);
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
			throw new AssertionError("Unexpected InterruptedException", e);
		}
	}

	private static EUID requestTokens(RadixAddress address) {
		try {
			String faucetHost = Optional.ofNullable(System.getenv("FAUCET_HOST")).orElse("http://localhost:8079");
			URL url = new URL(faucetHost + "/api/v1/getTokens/" + address);
			HttpURLConnection con = (HttpURLConnection) url.openConnection();
			con.setRequestMethod("GET");
			con.setConnectTimeout(5000);
			con.setReadTimeout(5000);
			int status = con.getResponseCode();
			final String result;
			try (BufferedReader in = new BufferedReader(new InputStreamReader(con.getInputStream()))) {
				result = in.lines().collect(Collectors.joining("\n"));
			}
			if (status == 200) {
				return EUID.valueOf(result);
			}
			throw new IllegalStateException(String.format("Could not request tokens (%s): %s", status, result));
		} catch (IOException e) {
			// Just going to ignore these and timeout
			log.info("Ignoring IOException while requesting tokens: {}", e.getMessage());
		}
		return EUID.ZERO;
	}
}
