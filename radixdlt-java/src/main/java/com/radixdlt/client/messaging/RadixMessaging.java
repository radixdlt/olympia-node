package com.radixdlt.client.messaging;


import com.radixdlt.client.core.RadixUniverse;
import com.radixdlt.client.core.address.EUID;
import com.radixdlt.client.core.address.RadixAddress;
import com.radixdlt.client.core.atoms.ApplicationPayloadAtom;
import com.radixdlt.client.core.atoms.AtomBuilder;
import com.radixdlt.client.core.atoms.IdParticle;
import com.radixdlt.client.core.atoms.UnsignedAtom;
import com.radixdlt.client.core.identity.RadixIdentities;
import com.radixdlt.client.core.identity.RadixIdentity;
import com.radixdlt.client.core.ledger.RadixLedger;
import com.radixdlt.client.core.network.AtomSubmissionUpdate;
import io.reactivex.Observable;
import io.reactivex.observables.GroupedObservable;
import java.util.Objects;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class RadixMessaging {
	private static final Logger LOGGER = LoggerFactory.getLogger(RadixMessaging.class);

	public static final String APPLICATION_ID = "radix-messaging";

	public static final int MAX_MESSAGE_LENGTH = 256;

	/**
	 * Lock to protect default messaging object
	 */
	private static Object lock = new Object();
	private static RadixMessaging radixMessaging;

	public static RadixMessaging getInstance() {
		synchronized (lock) {
			if (radixMessaging == null) {
				radixMessaging = new RadixMessaging(RadixUniverse.getInstance());
			}
			return radixMessaging;
		}
	}

	private final RadixUniverse universe;
	private final RadixLedger ledger;

	RadixMessaging(RadixUniverse universe) {
		this.universe = universe;
		this.ledger = universe.getLedger();
	}


	public Observable<EncryptedMessage> getAllMessagesEncrypted(EUID euid) {
		return ledger.getAllAtoms(euid, ApplicationPayloadAtom.class)
			.filter(atom -> atom.getApplicationId().equals(APPLICATION_ID)) // Only get messaging atoms
			.map(EncryptedMessage::fromAtom);
	}

	public Observable<EncryptedMessage> getAllMessagesEncrypted(RadixAddress address) {
		return this.getAllMessagesEncrypted(address.getUID());
	}

	public Observable<RadixMessage> getAllMessagesDecrypted(RadixIdentity identity) {
		return this.getAllMessagesEncrypted(identity.getPublicKey().getUID())
			.flatMapMaybe(decryptable -> RadixIdentities.decrypt(identity, decryptable)
				.toMaybe()
				.doOnError(error -> LOGGER.error(error.toString()))
				.onErrorComplete());
	}

	public Observable<GroupedObservable<RadixAddress, RadixMessage>> getAllMessagesDecryptedAndGroupedByParticipants(RadixIdentity identity) {
		return this.getAllMessagesDecrypted(identity)
			.groupBy(msg -> msg.getFrom().getPublicKey().equals(identity.getPublicKey()) ? msg.getTo() : msg.getFrom());
	}

	public Observable<AtomSubmissionUpdate> sendMessage(RadixMessageContent content, RadixIdentity fromIdentity, EUID uniqueId) {
		Objects.requireNonNull(content);
		if (content.getContent().length() > MAX_MESSAGE_LENGTH) {
			throw new IllegalArgumentException(
				"Message must be under " + MAX_MESSAGE_LENGTH + " characters but was " + content.getContent().length()
			);
		}

		AtomBuilder atomBuilder = new AtomBuilder()
			.type(ApplicationPayloadAtom.class)
			.applicationId(RadixMessaging.APPLICATION_ID)
			.payload(content.toJson())
			.addDestination(content.getTo())
			.addDestination(content.getFrom())
			.addProtector(content.getTo().getPublicKey())
			.addProtector(content.getFrom().getPublicKey());

		if (uniqueId != null) {
			atomBuilder.addParticle(IdParticle.create("jwt", uniqueId, fromIdentity.getPublicKey()));
		}

		UnsignedAtom unsignedAtom = atomBuilder.buildWithPOWFee(ledger.getMagic(), fromIdentity.getPublicKey());

		return fromIdentity.sign(unsignedAtom)
			.flatMapObservable(ledger::submitAtom);
	}

	public Observable<AtomSubmissionUpdate> sendMessage(RadixMessageContent content, RadixIdentity fromIdentity) {
		return this.sendMessage(content, fromIdentity, null);
	}

	public Observable<AtomSubmissionUpdate> sendMessage(String message, RadixIdentity fromIdentity, RadixAddress toAddress, EUID uniqueId) {
		Objects.requireNonNull(message);
		Objects.requireNonNull(fromIdentity);
		Objects.requireNonNull(toAddress);

		RadixAddress fromAddress = universe.getAddressFrom(fromIdentity.getPublicKey());

		return this.sendMessage(new RadixMessageContent(toAddress, fromAddress, message), fromIdentity, uniqueId);
	}

	public Observable<AtomSubmissionUpdate> sendMessage(String message, RadixIdentity fromIdentity, RadixAddress toAddress) {
		return this.sendMessage(message, fromIdentity, toAddress, null);
	}
}
