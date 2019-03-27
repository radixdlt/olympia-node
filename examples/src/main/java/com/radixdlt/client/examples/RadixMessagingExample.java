package com.radixdlt.client.examples;

import com.radixdlt.client.application.RadixApplicationAPI;
import com.radixdlt.client.application.identity.RadixIdentities;
import com.radixdlt.client.core.Bootstrap;
import com.radixdlt.client.core.RadixUniverse;
import com.radixdlt.client.atommodel.accounts.RadixAddress;
import com.radixdlt.client.core.network.actions.NodeUniverseMismatch;
import com.radixdlt.client.dapps.messaging.RadixMessaging;

public class RadixMessagingExample {
	private static String TO_ADDRESS_BASE58 = "JFgcgRKq6GbQqP8mZzDRhtr7K7YQM1vZiYopZLRpAeVxcnePRXX";
	private static String MESSAGE = "Hello World!";
	private static RadixMessagesQueryType queryType = RadixMessagesQueryType.BY_CONVO;

	private enum RadixMessagesQueryType {
		ALL,
		BY_CONVO
	}

	public static void main(String[] args) throws Exception {
		RadixApplicationAPI api = RadixApplicationAPI.create(
			Bootstrap.LOCALHOST,
			RadixIdentities.loadOrCreateFile("my.key")
		);

		api.getNetworkController().getActions()
			.ofType(NodeUniverseMismatch.class)
			.subscribe(System.out::println);

		// Addresses
		RadixAddress toAddress = RadixAddress.from(TO_ADDRESS_BASE58);

		RadixMessaging messaging = new RadixMessaging(api);

		switch(queryType) {
			case ALL:
				// Print out to console all received messages
				messaging
					.getAllMessages()
					.subscribe(System.out::println, Throwable::printStackTrace);
				break;

			case BY_CONVO:
			default:
				// Group messages by other address, useful for messaging apps
				messaging
					.getAllMessagesGroupedByParticipants()
					.subscribe(convo -> {
						System.out.println("New Conversation with: " + convo.getKey());
						convo.subscribe(System.out::println, Throwable::printStackTrace);
					});
		}

		// Send a message!
		messaging
			.sendMessage(MESSAGE, toAddress)
			.toCompletable()
			.subscribe(() -> System.out.println("Submitted"), Throwable::printStackTrace);
	}
}
