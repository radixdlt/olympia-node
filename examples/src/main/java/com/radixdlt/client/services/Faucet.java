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
import io.reactivex.Observable;
import io.reactivex.Single;

import java.io.IOException;
import java.sql.Timestamp;
import java.util.AbstractMap.SimpleImmutableEntry;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

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
	 * Map to keep track of last request timestamps per user
	 */
	private final ConcurrentHashMap<RadixAddress, Long> recipientTimestamps = new ConcurrentHashMap<>();

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
	 * Given a message, will attempt to send the requestor some tokens
	 * and then sends a message indicating whether successful or not.
	 *
	 * @param message the original message received
	 * @return the reply the faucet will send to the user
	 */
	private Single<RadixMessageContent> leakFaucet(RadixMessage message) {
		RadixAddress address = message.getFrom();
		Long timestamp = System.currentTimeMillis();

		if (this.recipientTimestamps.containsKey(address) && timestamp - this.recipientTimestamps.get(address) < DELAY) {
			long timeSince = timestamp - this.recipientTimestamps.get(address);
			if (timeSince < DELAY) {
				long secondsTimeLeft = ((DELAY - timeSince) / 1000) % 60;
				long minutesTimeLeft = ((DELAY - timeSince) / 1000) / 60;
				return Single.just(message.createReply(
				"Don't be hasty! You can only make one request every 10 minutes. "
						+ minutesTimeLeft + " minutes and " + secondsTimeLeft + " seconds left."
				));
			}
		}

		return RadixWallet.getInstance().transferXRD(10 * Asset.XRD.getSubUnits(), radixIdentity, message.getFrom())
			.doOnNext(state -> System.out.println("Transaction: " + state))
			.filter(AtomSubmissionUpdate::isComplete)
			.firstOrError()
			.doOnSuccess(update -> {
				if (update.getState() == AtomSubmissionState.STORED) {
					this.recipientTimestamps.put(address, timestamp);
				}
			})
			.map(update -> update.getState() == AtomSubmissionState.STORED ? "Sent you 10 Test Rads!" : "Couldn't send you any (Reason: " + update + ")")
			.map(message::createReply)
			.onErrorReturn(throwable -> message.createReply("Couldn't send you any (Reason: " + throwable.getMessage() + ")"))
			;
	}

	/**
	 * Actually send a reply message to the requestor through the Universe
	 *
	 * @param reply reply to send to requestor
	 * @return state of the message atom submission
	 */
	private Observable<AtomSubmissionUpdate> sendReply(RadixMessageContent reply) {
		return RadixMessaging.getInstance().sendMessage(reply, radixIdentity)
			.doOnNext(state -> System.out.println("Message: " + state))
		;
	}

	/**
	 * Retrieves all recent messages sent to this faucet
	 *
	 * @return stream of recent messages to this faucet
	 */
	private Observable<RadixMessage> getRecentMessages() {
		return RadixMessaging.getInstance()
			.getAllMessagesDecrypted(radixIdentity)
			.doOnNext(message -> System.out.println(new Timestamp(System.currentTimeMillis()).toLocalDateTime() + " " + message)) // Print out all messages
			.filter(message -> !message.getFrom().equals(sourceAddress)) // Don't send ourselves money
			.filter(message -> Math.abs(message.getTimestamp() - System.currentTimeMillis()) < 60000) // Only deal with recent messages
		;
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
		this.getRecentMessages()
			.flatMapSingle(this::leakFaucet, true)
			.flatMap(this::sendReply)
			.subscribe();
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
