package com.radixdlt.client.messaging;


import com.radixdlt.client.application.EncryptedData;
import com.radixdlt.client.application.RadixApplicationAPI;
import com.radixdlt.client.application.RadixApplicationAPI.Result;
import com.radixdlt.client.core.RadixUniverse;
import com.radixdlt.client.core.address.EUID;
import com.radixdlt.client.core.address.RadixAddress;
import com.radixdlt.client.core.atoms.ApplicationPayloadAtom;
import com.radixdlt.client.core.identity.RadixIdentities;
import com.radixdlt.client.core.identity.RadixIdentity;
import com.radixdlt.client.core.ledger.RadixLedger;
import io.reactivex.Observable;
import io.reactivex.observables.GroupedObservable;
import java.util.Objects;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class RadixMessaging {
	private static final Logger LOGGER = LoggerFactory.getLogger(RadixMessaging.class);

	public static final String APPLICATION_ID = "radix-messaging";

	public static final int MAX_MESSAGE_LENGTH = 256;

	private final RadixUniverse universe;
	private final RadixLedger ledger;
	private final RadixApplicationAPI api;
	private final RadixIdentity identity;

	public RadixMessaging(RadixIdentity identity, RadixUniverse universe) {
		this.universe = universe;
		this.ledger = universe.getLedger();
		this.identity = identity;
		this.api = new RadixApplicationAPI(identity, ledger);
	}


	public Observable<EncryptedMessage> getAllMessagesEncrypted(EUID euid) {
		return ledger.getAllAtoms(euid, ApplicationPayloadAtom.class)
			.filter(atom -> atom.getApplicationId().equals(APPLICATION_ID)) // Only get messaging atoms
			.map(EncryptedMessage::fromAtom);
	}

	public Observable<RadixMessage> getAllMessages() {
		return this.getAllMessagesEncrypted(identity.getPublicKey().getUID())
			.flatMapMaybe(decryptable -> RadixIdentities.decrypt(identity, decryptable)
				.toMaybe()
				.doOnError(error -> LOGGER.error(error.toString()))
				.onErrorComplete());
	}

	public Observable<GroupedObservable<RadixAddress, RadixMessage>> getAllMessagesGroupedByParticipants() {
		return this.getAllMessages()
			.groupBy(msg -> msg.getFrom().getPublicKey().equals(identity.getPublicKey()) ? msg.getTo() : msg.getFrom());
	}

	public Result sendMessage(RadixMessageContent content, EUID uniqueId) {
		Objects.requireNonNull(content);
		if (content.getContent().length() > MAX_MESSAGE_LENGTH) {
			throw new IllegalArgumentException(
				"Message must be under " + MAX_MESSAGE_LENGTH + " characters but was " + content.getContent().length()
			);
		}

		if (uniqueId != null) {
			throw new IllegalArgumentException("Unique ids not supported");
		}

		EncryptedData encryptedData = new EncryptedData.EncryptedDataBuilder()
			.data(content.toJson().getBytes())
			.metaData("application", RadixMessaging.APPLICATION_ID)
			.addReader(content.getTo().getPublicKey())
			.addReader(content.getFrom().getPublicKey())
			.build();

		return api.storeData(encryptedData, content.getTo(), content.getFrom());

	}

	public Result sendMessage(RadixMessageContent content) {
		return this.sendMessage(content, null);
	}

	public Result sendMessage(String message, RadixAddress toAddress, EUID uniqueId) {
		Objects.requireNonNull(message);
		Objects.requireNonNull(toAddress);

		RadixAddress fromAddress = universe.getAddressFrom(identity.getPublicKey());

		return this.sendMessage(new RadixMessageContent(toAddress, fromAddress, message), uniqueId);
	}

	public Result sendMessage(String message, RadixAddress toAddress) {
		return this.sendMessage(message, toAddress, null);
	}
}
