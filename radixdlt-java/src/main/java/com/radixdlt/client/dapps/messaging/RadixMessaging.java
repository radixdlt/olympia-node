package com.radixdlt.client.dapps.messaging;

import com.radixdlt.client.application.objects.DecryptedMessage;
import java.util.Objects;

import org.radix.utils.RadixConstants;

import com.radixdlt.client.application.RadixApplicationAPI;
import com.radixdlt.client.application.RadixApplicationAPI.Result;
import com.radixdlt.client.atommodel.accounts.RadixAddress;

import io.reactivex.Observable;
import io.reactivex.observables.GroupedObservable;

/**
 * High Level API for Instant Messaging. Currently being used by the Radix Android Mobile Wallet.
 */
public class RadixMessaging {
	public static final int MAX_MESSAGE_LENGTH = 256;

	private final RadixApplicationAPI api;
	private final RadixAddress myAddress;

	public RadixMessaging(RadixApplicationAPI api) {
		this.myAddress = api.getMyAddress();
		this.api = api;
	}

	public Observable<DecryptedMessage> getAllMessages() {
		return api.getMessages(myAddress);
	}

	public Observable<GroupedObservable<RadixAddress, DecryptedMessage>> getAllMessagesGroupedByParticipants() {
		return this.getAllMessages()
			.groupBy(msg -> msg.getFrom().getPublicKey().equals(api.getMyPublicKey()) ? msg.getTo() : msg.getFrom());
	}

	public Result sendMessage(String message, RadixAddress toAddress) {
		Objects.requireNonNull(message);
		Objects.requireNonNull(toAddress);

		if (message.length() > MAX_MESSAGE_LENGTH) {
			throw new IllegalArgumentException(
				"Message must be under " + MAX_MESSAGE_LENGTH + " characters but was " + message.length()
			);
		}

		return api.sendMessage(message.getBytes(RadixConstants.STANDARD_CHARSET), true, toAddress);
	}
}
