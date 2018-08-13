package com.radixdlt.client.examples;

import com.radixdlt.client.core.Bootstrap;
import com.radixdlt.client.core.RadixUniverse;
import com.radixdlt.client.core.address.RadixAddress;
import com.radixdlt.client.core.identity.RadixIdentity;
import com.radixdlt.client.core.identity.SimpleRadixIdentity;
import com.radixdlt.client.messaging.RadixMessage;
import com.radixdlt.client.messaging.RadixMessaging;
import io.reactivex.Completable;
import java.sql.Timestamp;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * This is an example of a ChatBot service which uses the RadixMessaging module
 * to chat with users in a Radix Universe.
 */
public class ChatBot {

	/**
	 * The Chatbot's RadixIdentity, an object which keeps the Chatbot's private key
	 */
	private final RadixIdentity identity;

	private final RadixMessaging messaging;

	/**
	 * The chat algorithm to run on each new conversation
	 *
	 * TODO: make this asynchronous via Observers/Observables
	 */
	private final Supplier<Function<String,String>> chatBotAlgorithmSupplier;

	public ChatBot(RadixIdentity identity, Supplier<Function<String,String>> chatBotAlgorithmSupplier) {
		this.identity = identity;
		this.messaging = new RadixMessaging(identity, RadixUniverse.getInstance());
		this.chatBotAlgorithmSupplier = chatBotAlgorithmSupplier;
	}

	/**
	 * Connect to the network and begin running the service
	 */
	public void run() {
		RadixAddress address = RadixUniverse.getInstance().getAddressFrom(identity.getPublicKey());

		System.out.println("Chatbot address: " + address);

		// Subscribe/Decrypt messages
		messaging
			.getAllMessagesGroupedByParticipants()
			.flatMapCompletable(convo -> convo
				.doOnNext(message -> System.out.println("Received at " + new Timestamp(System.currentTimeMillis()) + ": " + message)) // Print messages
				.filter(message -> !message.getFrom().equals(address)) // Don't reply to ourselves!
				.filter(message -> Math.abs(message.getTimestamp() - System.currentTimeMillis()) < 60000) // Only reply to recent messages
				.flatMapCompletable(new io.reactivex.functions.Function<RadixMessage, Completable>() {
					Function<String,String> chatBotAlgorithm = chatBotAlgorithmSupplier.get();

					@Override
					public Completable apply(RadixMessage message) {
						return messaging.sendMessage(chatBotAlgorithm.apply(message.getContent()), message.getFrom()).toCompletable();
					}
				})
			).subscribe(
				System.out::println,
				Throwable::printStackTrace
			);
	}

	public static void main(String[] args) throws Exception {
		RadixUniverse.bootstrap(Bootstrap.WINTERFELL_LOCAL);

		RadixUniverse.getInstance()
			.getNetwork()
			.getStatusUpdates()
			.subscribe(System.out::println);

		// Setup Identity of Chatbot
		RadixIdentity radixIdentity = new SimpleRadixIdentity("chatbot.key");

		ChatBot chatBot = new ChatBot(radixIdentity, () -> new Function<String, String>() {
			int messageCount = 0;

			@Override
			public String apply(String s) {
				switch(messageCount++) {
					case 0: return "Who dis?";
					case 1: return "Howdy " + s;
					case 5: return "Chillz out yo";
					default: return "I got nothing more to say";
				}
			}
		});
		chatBot.run();
	}
}
