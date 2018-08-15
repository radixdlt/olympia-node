package com.radixdlt.client.examples;

import com.radixdlt.client.core.Bootstrap;
import com.radixdlt.client.core.RadixUniverse;
import com.radixdlt.client.core.address.RadixAddress;
import com.radixdlt.client.core.identity.RadixIdentity;
import com.radixdlt.client.core.identity.SimpleRadixIdentity;
import com.radixdlt.client.messaging.RadixMessaging;

public class RadixMessagingExample {
	private static String TO_ADDRESS_BASE58 = "JFgcgRKq6GbQqP8mZzDRhtr7K7YQM1vZiYopZLRpAeVxcnePRXX";
	private static String MESSAGE = "Hello World!";
	private static RadixMessagesQueryType queryType = RadixMessagesQueryType.BY_CONVO;

	private enum RadixMessagesQueryType {
		ALL,
		BY_CONVO
	}

	static {
		RadixUniverse.bootstrap(Bootstrap.BETANET);
	}

	public static void main(String[] args) throws Exception {
		// Display network connectivity
		RadixUniverse.getInstance()
			.getNetwork()
			.getStatusUpdates()
			.subscribe(System.out::println);

		// Identity Manager which manages user's keys, signing, encrypting and decrypting
		RadixIdentity radixIdentity = new SimpleRadixIdentity();

		// Addresses
		RadixAddress toAddress = RadixAddress.fromString(TO_ADDRESS_BASE58);

		switch(queryType) {
			case ALL:
				// Print out to console all received messages
				RadixMessaging.getInstance()
					.getAllMessagesDecrypted(radixIdentity)
					.subscribe(System.out::println);
				break;

			case BY_CONVO:
			default:
				// Group messages by other address, useful for messaging apps
				RadixMessaging.getInstance()
					.getAllMessagesDecryptedAndGroupedByParticipants(radixIdentity)
					.subscribe(convo -> {
						System.out.println("New Conversation with: " + convo.getKey());
						convo.subscribe(System.out::println);
					});
		}

		// Send a message!
		RadixMessaging.getInstance().sendMessage(MESSAGE, radixIdentity, toAddress)
			.subscribe(System.out::println);
	}
}
