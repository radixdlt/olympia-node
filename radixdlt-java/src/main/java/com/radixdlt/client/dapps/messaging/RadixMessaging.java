package com.radixdlt.client.dapps.messaging;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.radixdlt.client.application.objects.EncryptedData;
import com.radixdlt.client.application.RadixApplicationAPI;
import com.radixdlt.client.application.RadixApplicationAPI.Result;
import com.radixdlt.client.core.address.EUID;
import com.radixdlt.client.core.address.RadixAddress;
import com.radixdlt.client.core.crypto.ECSignature;
import com.radixdlt.client.core.identity.RadixIdentity;
import io.reactivex.Maybe;
import io.reactivex.Observable;
import io.reactivex.observables.GroupedObservable;
import java.util.Map;
import java.util.Objects;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class RadixMessaging {
	private static final Logger LOGGER = LoggerFactory.getLogger(RadixMessaging.class);

	public static final String APPLICATION_ID = "radix-messaging";

	public static final int MAX_MESSAGE_LENGTH = 256;

	private final RadixApplicationAPI api;
	private final RadixIdentity identity;
	private final RadixAddress myAddress;
	private final JsonParser parser = new JsonParser();

	public RadixMessaging(RadixApplicationAPI api) {
		this.identity = api.getIdentity();
		this.myAddress = api.getAddress();
		this.api = api;
	}

	public Observable<RadixMessage> getAllMessages() {
		return api.getDecryptableData(myAddress)
			.filter(decryptedData -> APPLICATION_ID.equals(decryptedData.getMetaData().get("application")))
			.flatMapMaybe(decryptedData -> {
				try {
					JsonObject jsonObject = parser.parse(new String(decryptedData.getData())).getAsJsonObject();
					RadixAddress from = new RadixAddress(jsonObject.get("from").getAsString());
					RadixAddress to = new RadixAddress(jsonObject.get("to").getAsString());
					String content = jsonObject.get("content").getAsString();
					Object signaturesUnchecked = decryptedData.getMetaData().get("signatures");
					Map<String, ECSignature> signatures = (Map<String, ECSignature>) signaturesUnchecked;
					ECSignature signature = signatures.get(from.getUID().toString());
					if (signature == null) {
						throw new RuntimeException("Unsigned message");
					}
					Long timestamp = (Long) decryptedData.getMetaData().get("timestamp");
					return Maybe.just(new RadixMessage(from, to, content, timestamp));
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

		EncryptedData encryptedData = new EncryptedData.EncryptedDataBuilder()
			.data(messageJson.toString().getBytes())
			.metaData("application", RadixMessaging.APPLICATION_ID)
			.addReader(toAddress.getPublicKey())
			.addReader(myAddress.getPublicKey())
			.build();

		return api.storeData(encryptedData, toAddress, myAddress);
	}

	public Result sendMessage(String message, RadixAddress toAddress) {
		return this.sendMessage(message, toAddress, null);
	}
}
