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

import com.google.common.util.concurrent.RateLimiter;
import com.radixdlt.client.application.RadixApplicationAPI;
import com.radixdlt.client.application.RadixApplicationAPI.Result;
import com.radixdlt.client.application.RadixApplicationAPI.Transaction;
import com.radixdlt.client.application.identity.RadixIdentities;
import com.radixdlt.client.application.identity.RadixIdentity;
import com.radixdlt.client.application.translate.data.DecryptedMessage;
import com.radixdlt.client.application.translate.data.SendMessageAction;
import com.radixdlt.client.application.translate.tokens.TransferTokensAction;
import com.radixdlt.client.application.translate.unique.PutUniqueIdAction;
import com.radixdlt.identifiers.RadixAddress;
import com.radixdlt.client.core.BootstrapConfig;
import com.radixdlt.client.core.RadixEnv;
import com.radixdlt.identifiers.RRI;

import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.util.Objects;
import java.util.Properties;
import java.util.concurrent.TimeUnit;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import com.radixdlt.utils.RadixConstants;

import io.reactivex.observables.GroupedObservable;

/**
 * A service which sends tokens to whoever sends it a message through
 * a Radix Universe.
 */
public class Faucet {
	private static final Logger log = LogManager.getLogger();

	private static final String FAUCET_IDENTITY_KEY_ENV_NAME = "FAUCET_IDENTITY_KEY";
	private static final String FAUCET_TOKEN_RRI_ENV_NAME    = "FAUCET_TOKEN_RRI";
	private static final String FAUCET_RATE_ENV_NAME         = "FAUCET_RATE";
	private static final String FAUCET_AMOUNT_ENV_NAME       = "FAUCET_AMOUNT";

	private static final String UNIQUE_MESSAGE_PREFIX = "faucet-msg-";
	private static final String UNIQUE_SEND_TOKENS_PREFIX = "faucet-tx-";

	// Default timeout set at 1 minute.  The default test and dev universes typically
	// have 1_000_000_000 XRD initially, and draining the faucet at the rate of 10 XRD/minute
	// will take a little less than 200 years before all the XRD are gone.
	private static final double DEFAULT_RATE = 1.0 / 60.0; // 1 per minute
	private static final double DEFAULT_AMOUNT = 10.0; // 10 rads/request

	private final RadixApplicationAPI api;
	private final RRI tokenRRI;
	private final BigDecimal amountToSend;
	private final double rateLimiterQps;

	private Faucet(RadixApplicationAPI api, RRI tokenRRI, BigDecimal amountToSend, double rateLimiterQps) {
		this.tokenRRI = Objects.requireNonNull(tokenRRI);
		this.api = Objects.requireNonNull(api);
		this.amountToSend = Objects.requireNonNull(amountToSend);
		this.rateLimiterQps = rateLimiterQps;
	}

	/**
	 * Send tokens from this account to an address
	 *
	 * @param rateLimiter the rate limiter to use
	 * @param msg the msg received
	 * @return completable whether transfer was successful or not
	 */
	private void leakFaucet(RateLimiter rateLimiter, DecryptedMessage msg) {
		RRI msgMutexAcquire = RRI.of(api.getAddress(), UNIQUE_MESSAGE_PREFIX + msg.getActionId());
		RRI transferMutexAcquire = RRI.of(api.getAddress(), UNIQUE_SEND_TOKENS_PREFIX + msg.getActionId());

		if (!rateLimiter.tryAcquire()) {
			log.info("Rate limiting requests from {}", msg.getFrom());
			Transaction hastyMsg = this.api.createTransaction();
			hastyMsg.stage(SendMessageAction.create(
				api.getAddress(),
				msg.getFrom(),
				String.format("Don't be hasty! Only %s requests per minute accepted.", rateLimiter.getRate() * 60.0)
					.getBytes(RadixConstants.STANDARD_CHARSET),
				true
			));
			hastyMsg.stage(PutUniqueIdAction.create(msgMutexAcquire));
			hastyMsg.stage(PutUniqueIdAction.create(transferMutexAcquire));
			hastyMsg.commitAndPush().toObservable().subscribe(
				saa -> log.debug("Rate limit {} for {}: {}", msg.getFrom(), msg.getActionId(), saa),
				e -> log.error("Could not send rate limit message", e)
			);
			return;
		}

		log.info("Sending tokens to {}", msg.getFrom());
		Transaction transaction = this.api.createTransaction();
		transaction.stage(TransferTokensAction.create(tokenRRI, api.getAddress(), msg.getFrom(), amountToSend));
		transaction.stage(PutUniqueIdAction.create(transferMutexAcquire));
		Result result = transaction.commitAndPush();
		result.toObservable().subscribe(
			saa -> log.debug("Send tokens to {} for {}: {}", msg.getFrom(), msg.getActionId(), saa),
			e -> log.error("Could not send tokens", e)
		);
		result.toCompletable().subscribe(
			() -> {
				log.info("Sent tokens to {}", msg.getFrom());
				Transaction sentRadsMsg = this.api.createTransaction();
				byte[] msgBytes = ("Sent you " + amountToSend + " " + tokenRRI.getName()).getBytes(RadixConstants.STANDARD_CHARSET);
				sentRadsMsg.stage(SendMessageAction.create(api.getAddress(), msg.getFrom(), msgBytes, true));
				sentRadsMsg.stage(PutUniqueIdAction.create(msgMutexAcquire));
				sentRadsMsg.commitAndPush().toObservable().subscribe(
					saa -> log.debug("Send tokens message to {} for {}: {}", msg.getFrom(), msg.getActionId(), saa),
					e -> log.error("Count not send tokens message", e)
				);
			},
			e -> {
				log.info("Error sending tokens", e);
				Transaction sentRadsMsg = this.api.createTransaction();
				byte[] msgBytes = ("Could not send you any (Reason: " + e.getMessage() + ")").getBytes(RadixConstants.STANDARD_CHARSET);
				sentRadsMsg.stage(SendMessageAction.create(api.getAddress(), msg.getFrom(), msgBytes, true));
				sentRadsMsg.stage(PutUniqueIdAction.create(msgMutexAcquire));
				sentRadsMsg.commitAndPush().toObservable().subscribe(
					saa -> log.debug("Send tokens error message to {} for {}: {}", msg.getFrom(), msg.getActionId(), saa),
					ex -> log.error("Count not send tokens error message", ex)
				);
			}
		);
	}

	private void processMessages(RadixAddress sourceAddress, GroupedObservable<RadixAddress, DecryptedMessage> observableByAddress) {
		final RateLimiter rateLimiter = RateLimiter.create(this.rateLimiterQps);
		observableByAddress
			.doOnNext(dm -> log.debug("Message: {}", dm)) // Print out all messages
			.filter(message -> !message.getFrom().equals(sourceAddress)) // Don't send ourselves money
			.subscribe(
				message -> this.leakFaucet(rateLimiter, message),
				e -> log.error("While receiving messages", e)
			);
	}

	/**
	 * Start and run the faucet service
	 */
	public void run() {
		api.pull();

		final RadixAddress sourceAddress = this.api.getAddress();

		log.info("Faucet token: {}", this.tokenRRI);
		log.info("Faucet address: {}", sourceAddress);

		// Print out current balance of faucet
		api.observeBalance(tokenRRI)
			.subscribe(
				balance -> log.info("Faucet balance: {}",  balance),
				Throwable::printStackTrace
			);

		api.observeMessages()
			.groupBy(DecryptedMessage::getFrom)
			.subscribe(observer -> processMessages(sourceAddress, observer));

		try {
			TimeUnit.SECONDS.sleep(5);
		} catch (InterruptedException e) {
			// Ignored
		}
	}

	private static void logVersion() {
		String branch  = "unknown-branch";
		String commit  = "unknown-commit";
		String display = "unknown-version";
		try (InputStream is = Faucet.class.getResourceAsStream("/version.properties")) {
			if (is != null) {
				Properties p = new Properties();
				p.load(is);
				branch  = p.getProperty("VERSION_BRANCH",  branch);
				commit  = p.getProperty("VERSION_COMMIT",  commit);
				display = p.getProperty("VERSION_DISPLAY", display);
			}
		} catch (IOException e) {
			// Ignore exception
		}
		log.always().log("Radix faucet '{}' from branch '{}' commit '{}'", display, branch, commit);
	}

	public static void main(String[] args) throws Exception {
		logVersion();

		// Bootstrap configuration
		final BootstrapConfig config = RadixEnv.getBootstrapConfig();

		// Identity configuration
		final String unencryptedKey = System.getenv(FAUCET_IDENTITY_KEY_ENV_NAME);
		if (unencryptedKey == null) {
			log.fatal("Identity must be set via env var: {}=<private-key-base64>", FAUCET_IDENTITY_KEY_ENV_NAME);
			System.exit(-1);
		}
		final RadixIdentity faucetIdentity;
		try {
			faucetIdentity = RadixIdentities.fromPrivateKeyBase64(unencryptedKey);
		} catch (Exception ex) {
			log.fatal(String.format("While decoding identity key from %s", FAUCET_IDENTITY_KEY_ENV_NAME), ex);
			System.exit(-1);
			return;
		}

		// Token RRI configuration
		final String tokenRRIString = System.getenv(FAUCET_TOKEN_RRI_ENV_NAME);
		if (tokenRRIString == null) {
			log.fatal("Token RRI must be set via env var: {}=<rri-of-token>", FAUCET_TOKEN_RRI_ENV_NAME);
			System.exit(-1);
		}
		final RRI tokenRRI = RRI.from(tokenRRIString);

		// Faucet delay configuration
		final String faucetRateString = System.getenv(FAUCET_RATE_ENV_NAME);
		final double rate = faucetRateString != null ? Double.parseDouble(faucetRateString) : DEFAULT_RATE;

		// Faucet amount
		final String faucetAmountString = System.getenv(FAUCET_AMOUNT_ENV_NAME);
		final BigDecimal leakAmount = faucetAmountString == null ? BigDecimal.valueOf(DEFAULT_AMOUNT) : new BigDecimal(faucetAmountString);

		final RadixApplicationAPI api = RadixApplicationAPI.create(config, faucetIdentity);
		Faucet faucet = new Faucet(api, tokenRRI, leakAmount, rate);
		faucet.run();
	}
}
