package com.radixdlt.client.services;

import com.radixdlt.client.assets.Asset;
import com.radixdlt.client.core.Bootstrap;
import com.radixdlt.client.core.RadixUniverse;
import com.radixdlt.client.core.address.RadixAddress;
import com.radixdlt.client.core.identity.RadixIdentity;
import com.radixdlt.client.core.identity.SimpleRadixIdentity;
import com.radixdlt.client.core.network.AtomSubmissionUpdate;
import com.radixdlt.client.core.network.AtomSubmissionUpdate.AtomSubmissionState;
import com.radixdlt.client.messaging.RadixMessage;
import com.radixdlt.client.messaging.RadixMessageContent;
import com.radixdlt.client.messaging.RadixMessaging;
import com.radixdlt.client.wallet.RadixWallet;
import io.reactivex.Completable;
import io.reactivex.Observable;
import io.reactivex.Single;

import java.io.IOException;
import java.sql.Timestamp;
import java.util.AbstractMap.SimpleImmutableEntry;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

/**
 * A service which sends tokens to whoever sends it a message through
 * a Radix Universe.
 */
public class Faucet {

	/**
	 * The amount of time a requestor must wait to make subsequent token requests
	 */
	private final static long DELAY = 1000 * 60 * 10; //10min

	/**
	 * The address of this faucet on which one can send messages to
	 */
	private final RadixAddress sourceAddress;

	/**
	 * The RadixIdentity of this faucet, an object which keeps the Chatbot's private key
	 */
	private final RadixIdentity radixIdentity;

	/**
	 * A faucet created on the default universe
	 *
	 * @param radixIdentity identity to load faucet off of
	 */
	private Faucet(RadixIdentity radixIdentity) {
		this.radixIdentity = radixIdentity;
		this.sourceAddress = RadixUniverse.getInstance().getAddressFrom(radixIdentity.getPublicKey());
	}

	/**
	 * Send XRD from this account to an address
	 *
	 * @param to the address to send to
	 * @return completable whether transfer was successful or not
	 */
	private Completable leakFaucet(RadixAddress to) {
		return RadixWallet.getInstance().transferXRD(10 * Asset.XRD.getSubUnits(), radixIdentity, to)
			.doOnNext(state -> System.out.println("Transaction: " + state))
			.filter(AtomSubmissionUpdate::isComplete)
			.firstOrError()
			.flatMapCompletable(update -> update.getState() == AtomSubmissionState.STORED ?
				Completable.complete() : Completable.error(new RuntimeException(update.toString()))
			);
	}

	/**
	 * Actually send a reply message to the requestor through the Universe
	 *
	 * @param reply reply to send to requestor
	 * @return state of the message atom submission
	 */
	private Completable sendReply(RadixMessageContent reply) {
		return RadixMessaging.getInstance().sendMessage(reply, radixIdentity)
			.doOnNext(state -> System.out.println("Message: " + state))
			.filter(AtomSubmissionUpdate::isComplete)
			.firstOrError()
			.flatMapCompletable(update -> update.getState() == AtomSubmissionState.STORED ?
				Completable.complete() : Completable.error(new RuntimeException(update.toString()))
			);
	}

	/**
	 * Start the faucet service
	 */
	public void run() {
		System.out.println("Faucet Address: " + sourceAddress);

		// Print out current balance of faucet
		RadixWallet.getInstance().getSubUnitBalance(sourceAddress, Asset.XRD)
			.subscribe(
				balance -> System.out.println("Faucet Balance: " + ((double)balance) / Asset.XRD.getSubUnits()),
				Throwable::printStackTrace
			)
		;

		// Flow Logic
		// Listen to any recent messages, send 10 XRD to the sender and then send a confirmation whether it succeeded or not
		// NOTE: this is neither idempotent nor atomic!
		RadixMessaging.getInstance()
			.getAllMessagesDecryptedAndGroupedByParticipants(radixIdentity)
			.subscribe(observableByAddress -> {
				final RadixAddress from = observableByAddress.getKey();

				final RateLimiter rateLimiter = new RateLimiter(DELAY);

				observableByAddress
					.doOnNext(System.out::println) // Print out all messages
					.filter(message -> !message.getFrom().equals(sourceAddress)) // Don't send ourselves money
					.filter(message -> Math.abs(message.getTimestamp() - System.currentTimeMillis()) < 60000) // Only deal with recent messages
					.flatMapSingle(message -> {
						if (rateLimiter.check()) {
							return this.leakFaucet(from)
								.doOnComplete(rateLimiter::reset)
								.andThen(Single.just("Sent you 10 Test Rads!"))
								.onErrorReturn(throwable -> "Couldn't send you any (Reason: " + throwable.getMessage() + ")")
								.map(message::createReply);
						} else {
							return Single.just(message.createReply(
								"Don't be hasty! You can only make one request every 10 minutes. "
								+ rateLimiter.getTimeLeftString() + " left."
							));
						}
					}, true)
					.flatMapCompletable(this::sendReply)
					.subscribe();
			});
	}

	/**
	 * Simple Rate Limiter helper class
	 */
	private static class RateLimiter {
		private final AtomicLong lastTimestamp = new AtomicLong();
		private final long millis;

		private RateLimiter(long millis) {
			this.millis = millis;
		}

		String getTimeLeftString() {
			long timeSince = System.currentTimeMillis() - lastTimestamp.get();
			long secondsTimeLeft = ((DELAY - timeSince) / 1000) % 60;
			long minutesTimeLeft = ((DELAY - timeSince) / 1000) / 60;
			return minutesTimeLeft + " minutes and " + secondsTimeLeft + " seconds";
		}

		boolean check() {
			return lastTimestamp.get() == 0 || (System.currentTimeMillis() - lastTimestamp.get() > millis);
		}

		void reset() {
			lastTimestamp.set(System.currentTimeMillis());
		}
	}

	public static void main(String[] args) throws IOException {
		if (args.length < 2) {
			System.out.println("Usage: java com.radixdlt.client.services.Faucet <highgarden|sunstone|winterfell|winterfell_local> <keyfile>");
			System.exit(-1);
		}

		RadixUniverse.bootstrap(Bootstrap.valueOf(args[0].toUpperCase()));

		RadixUniverse.getInstance()
			.getNetwork()
			.getStatusUpdates()
			.subscribe(System.out::println);

		final RadixIdentity faucetIdentity = new SimpleRadixIdentity(args[1]);
		Faucet faucet = new Faucet(faucetIdentity);
		faucet.run();
	}
}
