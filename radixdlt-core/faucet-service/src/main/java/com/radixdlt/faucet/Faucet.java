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
import com.radixdlt.client.application.identity.RadixIdentities;
import com.radixdlt.client.application.identity.RadixIdentity;
import com.radixdlt.client.core.RadixEnv;
import com.radixdlt.client.core.ledger.AtomObservation;

import java.io.IOException;
import java.math.BigDecimal;
import java.util.Optional;
import java.util.Properties;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

import io.reactivex.schedulers.Schedulers;

import static java.util.Optional.ofNullable;

/**
 * A service which sends tokens to whoever sends it a message through
 * a Radix Universe.
 */
public class Faucet {
	private Faucet() { }

	private static final Logger log = LogManager.getLogger();

	private static final String FAUCET_API_PORT_ENV_NAME = "FAUCET_API_PORT";
	private static final String FAUCET_IDENTITY_KEY_ENV_NAME = "FAUCET_IDENTITY_KEY";
	private static final String FAUCET_TOKEN_RRI_ENV_NAME = "FAUCET_TOKEN_RRI";
	private static final String FAUCET_RATE_ENV_NAME = "FAUCET_RATE";
	private static final String FAUCET_AMOUNT_ENV_NAME = "FAUCET_AMOUNT";

	private static final double DEFAULT_RATE = 0.5; // 1 every 2 seconds
	private static final BigDecimal DEFAULT_AMOUNT = BigDecimal.valueOf(10); // 10 rads/request
	private static final int DEFAULT_API_PORT = 8079;

	private static void logVersion() {
		var branch = "unknown-branch";
		var commit = "unknown-commit";
		var display = "unknown-version";

		try (var is = Faucet.class.getResourceAsStream("/version.properties")) {
			if (is == null) {
				throw new IOException("Resource /version.properties is unavailable");
			}
			var p = new Properties();
			p.load(is);

			branch = p.getProperty("VERSION_BRANCH", branch);
			commit = p.getProperty("VERSION_COMMIT", commit);
			display = p.getProperty("VERSION_DISPLAY", display);
		} catch (IOException e) {
			// Ignore exception
		}
		log.always().log("Radix faucet '{}' from branch '{}' commit '{}'", display, branch, commit);
	}

	private static void syncToHead(RadixApplicationAPI api) {
		final var atomCount = new AtomicLong(1L); // Yes, this is what I want
		final var atomObserverDisposable = api.getAtomStore()
			.getAtomObservations(api.getAddress())
			.subscribeOn(Schedulers.computation())
			.subscribe(
				atomObs -> updateAtomObservations(atomCount, atomObs),
				e -> log.error("Error while observing atom updates", e)
			);
		try {
			log.info("Syncing...");
			api.pullOnce(api.getAddress()).blockingAwait();
			log.info("Sync complete, {} atoms. Starting.", atomCount.get());
		} finally {
			atomObserverDisposable.dispose();
		}
	}

	private static void updateAtomObservations(AtomicLong atomCount, AtomObservation atomObs) {
		if (atomObs.isStore()) {
			long currentCount = atomCount.getAndIncrement();
			if (currentCount % 200 == 0) {
				log.info("Pulled {} atoms, continuing...", currentCount);
			}
		}
	}

	private static <T> T fail(String message, Object... params) {
		log.fatal(message, params);
		System.exit(-1);
		return null;
	}

	public static void main(String[] args) throws Exception {
		logVersion();

		var api = loadApi();
		var rateLimiter = prepareRateLimiter();
		var leakAmount = retrieveLeakAmount();

		// Wait for threads to start
		try {
			TimeUnit.SECONDS.sleep(5);
		} catch (InterruptedException e) {
			// Ignored
			Thread.currentThread().interrupt();
		}
	}

	private static BigDecimal retrieveLeakAmount() {
		return envVar(FAUCET_AMOUNT_ENV_NAME).map(BigDecimal::new).orElse(DEFAULT_AMOUNT);
	}

	private static RateLimiter prepareRateLimiter() {
		return RateLimiter.create(envVar(FAUCET_RATE_ENV_NAME).map(Double::parseDouble).orElse(DEFAULT_RATE));
	}

	private static RadixApplicationAPI loadApi() {
		// Bootstrap configuration
		final var config = RadixEnv.getBootstrapConfig();

		// Identity configuration
		final var faucetIdentity = envVar(FAUCET_IDENTITY_KEY_ENV_NAME)
			.map(Faucet::toIdentity)
			.orElseGet(() -> fail("Identity must be set via env var: {}=<private-key-base64>", FAUCET_IDENTITY_KEY_ENV_NAME));

		return RadixApplicationAPI.create(config, faucetIdentity);
	}

	private static Optional<String> envVar(final String keyEnvName) {
		return ofNullable(System.getenv(keyEnvName));
	}

	private static RadixIdentity toIdentity(final String unencryptedKey) {
		try {
			return RadixIdentities.fromPrivateKeyBase64(unencryptedKey);
		} catch (Exception ex) {
			log.fatal(String.format("Error while decoding identity key from %s", FAUCET_IDENTITY_KEY_ENV_NAME), ex);
			System.exit(-1);
			return null;
		}
	}
}
