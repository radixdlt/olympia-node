package com.radixdlt.client.application.translate;

import com.google.gson.JsonArray;
import com.google.gson.JsonParser;
import com.radixdlt.client.application.actions.TokenTransfer;
import com.radixdlt.client.application.objects.Data;
import com.radixdlt.client.assets.Asset;
import com.radixdlt.client.core.RadixUniverse;
import com.radixdlt.client.core.address.RadixAddress;
import com.radixdlt.client.core.atoms.Atom;
import com.radixdlt.client.core.atoms.AtomBuilder;
import com.radixdlt.client.core.atoms.Consumable;
import com.radixdlt.client.core.atoms.Consumer;
import com.radixdlt.client.core.atoms.DataParticle;
import com.radixdlt.client.core.atoms.EncryptorParticle;
import com.radixdlt.client.core.atoms.Payload;
import com.radixdlt.client.core.crypto.ECKeyPair;
import com.radixdlt.client.core.crypto.ECPublicKey;
import com.radixdlt.client.core.crypto.EncryptedPrivateKey;
import com.radixdlt.client.core.crypto.Encryptor;
import com.radixdlt.client.core.serialization.RadixJson;
import io.reactivex.Completable;
import java.nio.charset.StandardCharsets;
import java.util.AbstractMap.SimpleImmutableEntry;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

public class TokenTransferTranslator {
	private final RadixUniverse universe;
	private final ConsumableDataSource consumableDataSource;
	private static final JsonParser parser = new JsonParser();

	public TokenTransferTranslator(RadixUniverse universe, ConsumableDataSource consumableDataSource) {
		this.universe = universe;
		this.consumableDataSource = consumableDataSource;
	}

	public TokenTransfer fromAtom(Atom atom) {
		List<SimpleImmutableEntry<ECPublicKey, Long>> summary =
			atom.summary().entrySet().stream()
				.filter(entry -> entry.getValue().containsKey(Asset.TEST.getId()))
				.map(entry -> new SimpleImmutableEntry<>(entry.getKey().iterator().next(), entry.getValue().get(Asset.TEST.getId())))
				.collect(Collectors.toList());

		if (summary.isEmpty()) {
			throw new IllegalStateException("Invalid atom: " + RadixJson.getGson().toJson(atom));
		}

		if (summary.size() > 2) {
			throw new IllegalStateException("More than two participants in token transfer. Unable to handle: " + summary);
		}

		final RadixAddress from;
		final RadixAddress to;
		if (summary.size() == 1) {
			from = summary.get(0).getValue() <= 0L ? universe.getAddressFrom(summary.get(0).getKey()) : null;
			to = summary.get(0).getValue() <= 0L ? null : universe.getAddressFrom(summary.get(0).getKey());
		} else {
			if (summary.get(0).getValue() > 0) {
				from = universe.getAddressFrom(summary.get(1).getKey());
				to = universe.getAddressFrom(summary.get(0).getKey());
			} else {
				from = universe.getAddressFrom(summary.get(0).getKey());
				to = universe.getAddressFrom(summary.get(1).getKey());
			}
		}

		// Construct attachment from atom
		final Data attachment;
		if (atom.getDataParticle() != null) {
			Map<String, Object> metaData = new HashMap<>();
			metaData.put("encrypted", atom.getEncryptor() != null);

			final Encryptor encryptor;
			if (atom.getEncryptor() != null) {
				JsonArray protectorsJson = parser.parse(atom.getEncryptor().getBytes().toUtf8()).getAsJsonArray();
				List<EncryptedPrivateKey> protectors = new ArrayList<>();
				protectorsJson.forEach(protectorJson -> protectors.add(EncryptedPrivateKey.fromBase64(protectorJson.getAsString())));
				encryptor = new Encryptor(protectors);
			} else {
				encryptor = null;
			}
			attachment = Data.raw(atom.getDataParticle().getBytes().getBytes(), metaData, encryptor);
		} else {
			attachment = null;
		}

		return TokenTransfer.create(from, to, Asset.TEST, Math.abs(summary.get(0).getValue()), attachment, atom.getTimestamp());
	}

	public Completable translate(TokenTransfer tokenTransfer, AtomBuilder atomBuilder) {
		return this.consumableDataSource.getConsumables(tokenTransfer.getFrom())
			.firstOrError()
			.flatMapCompletable(unconsumedConsumables -> {

				// Translate attachment to corresponding atom structure
				final Data attachment = tokenTransfer.getAttachment();
				if (attachment != null) {

					atomBuilder.setDataParticle(new DataParticle(new Payload(attachment.getBytes()), null));
					Encryptor encryptor = attachment.getEncryptor();
					if (encryptor != null) {
						JsonArray protectorsJson = new JsonArray();
						encryptor.getProtectors().stream().map(EncryptedPrivateKey::base64).forEach(protectorsJson::add);

						Payload encryptorPayload = new Payload(protectorsJson.toString().getBytes(StandardCharsets.UTF_8));
						DataParticle encryptorParticle = new DataParticle(encryptorPayload, "encryptor");
						atomBuilder.setEncryptorParticle(encryptorParticle);
					}
				}

				long consumerTotal = 0;
				Iterator<Consumable> iterator = unconsumedConsumables.iterator();
				Map<Set<ECKeyPair>, Long> consumerQuantities = new HashMap<>();

				// HACK for now
				// TODO: remove this, create a ConsumersCreator
				// TODO: randomize this to decrease probability of collision
				while (consumerTotal < tokenTransfer.getSubUnitAmount() && iterator.hasNext()) {
					final long left = tokenTransfer.getSubUnitAmount() - consumerTotal;

					Consumer newConsumer = iterator.next().toConsumer();
					consumerTotal += newConsumer.getQuantity();

					final long amount = Math.min(left, newConsumer.getQuantity());
					newConsumer.addConsumerQuantities(amount, Collections.singleton(tokenTransfer.getTo().toECKeyPair()),
						consumerQuantities);

					atomBuilder.addConsumer(newConsumer);
				}

				if (consumerTotal < tokenTransfer.getSubUnitAmount()) {
					return Completable.error(new InsufficientFundsException(
						tokenTransfer.getTokenClass(), consumerTotal, tokenTransfer.getSubUnitAmount()
					));
				}

				List<Consumable> consumables = consumerQuantities.entrySet().stream()
					.map(entry -> new Consumable(entry.getValue(), entry.getKey(), System.nanoTime(), Asset.TEST.getId()))
					.collect(Collectors.toList());
				atomBuilder.addConsumables(consumables);

				return Completable.complete();

				/*
				if (withPOWFee) {
					// TODO: Replace this with public key of processing node runner
					return atomBuilder.buildWithPOWFee(ledger.getMagic(), fromAddress.getPublicKey());
				} else {
					return atomBuilder.build();
				}
				*/
			});
	}
}
