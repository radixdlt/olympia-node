package com.radixdlt.client.dapps.messaging;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.radixdlt.client.application.objects.Data;
import com.radixdlt.client.application.RadixApplicationAPI;
import com.radixdlt.client.application.RadixApplicationAPI.Result;
import com.radixdlt.client.application.objects.Data.DataBuilder;
import com.radixdlt.client.core.address.EUID;
import com.radixdlt.client.core.address.RadixAddress;
import com.radixdlt.client.core.crypto.ECSignature;
import com.radixdlt.client.application.identity.RadixIdentity;
import io.reactivex.Maybe;
import io.reactivex.Observable;
import io.reactivex.observables.GroupedObservable;
import java.util.Map;
import java.util.Objects;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * High Level API for Instant Messaging. Currently being used by the Radix Android Mobile Wallet.
 */
public class RadixMessaging {
	private static final Logger LOGGER = LoggerFactory.getLogger(RadixMessaging.class);

	public static final String APPLICATION_ID = "radix-messaging";

	public static final int MAX_MESSAGE_LENGTH = 256;

	private final RadixApplicationAPI api;
	private final RadixIdentity identity;
	private final RadixAddress myAddress;
	private final JsonParser parser = new JsonParser();

	public RadixMessaging(RadixApplicationAPI api) {
		this.identity = api.getMyIdentity();
		this.myAddress = api.getMyAddress();
		this.api = api;
	}

	public Observable<RadixMessage> getAllMessages() {
		return api.getReadableData(myAddress)
			.filter(data -> APPLICATION_ID.equals(data.getMetaData().get("application")))
			.flatMapMaybe(data -> {
				try {
					JsonObject jsonObject = parser.parse(new String(data.getData())).getAsJsonObject();
					RadixAddress from = new RadixAddress(jsonObject.get("from").getAsString());
					RadixAddress to = new RadixAddress(jsonObject.get("to").getAsString());
					String content = jsonObject.get("content").getAsString();
					Object signaturesUnchecked = data.getMetaData().get("signatures");
					Map<String, ECSignature> signatures = (Map<String, ECSignature>) signaturesUnchecked;
					ECSignature signature = signatures.get(from.getUID().toString());
					if (signature == null) {
						throw new RuntimeException("Unsigned message");
					}
					Long timestamp = (Long) data.getMetaData().get("timestamp");
					return Maybe.just(new RadixMessage(from, to, content, timestamp, data.isFromEncryptedSource()));
				} catch (Exception e) {
					LOGGER.warn(e.getMessage());
					return Maybe.empty();
				}
			});
	}

	public Observable<GroupedObservable<RadixAddress, RadixMessage>> getAllMessagesGroupedByParticipants() {
		return this.getAllMessages()
			.groupBy(msg -> msg.getFrom().getPublicKey().equals(identity.getPublicKey()) ? msg.getTo() : msg.getFrom());
	}

	public Result sendMessage(String message, RadixAddress toAddress, EUID uniqueId) {
		Objects.requireNonNull(message);
		Objects.requireNonNull(toAddress);

		if (message.length() > MAX_MESSAGE_LENGTH) {
			throw new IllegalArgumentException(
				"Message must be under " + MAX_MESSAGE_LENGTH + " characters but was " + message.length()
			);
		}

		if (uniqueId != null) {
			throw new IllegalArgumentException("Unique ids not supported");
		}

		JsonObject messageJson = new JsonObject();
		messageJson.addProperty("from", myAddress.toString());
		messageJson.addProperty("to", toAddress.toString());
		messageJson.addProperty("content", message);

		Data data = new DataBuilder()
			.bytes(messageJson.toString().getBytes())
			.metaData("application", RadixMessaging.APPLICATION_ID)
			.addReader(toAddress.getPublicKey())
			.addReader(myAddress.getPublicKey())
			.build();

		return api.storeData(data, toAddress, myAddress);
	}

	public Result sendMessage(String message, RadixAddress toAddress) {
		return this.sendMessage(message, toAddress, null);
	}
}
