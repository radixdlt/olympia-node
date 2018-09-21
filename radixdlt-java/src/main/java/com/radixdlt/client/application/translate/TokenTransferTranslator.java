package com.radixdlt.client.application.translate;

import com.radixdlt.client.application.actions.TokenTransfer;
import com.radixdlt.client.application.objects.Data;
import com.radixdlt.client.assets.Asset;
import com.radixdlt.client.core.RadixUniverse;
import com.radixdlt.client.core.address.RadixAddress;
import com.radixdlt.client.core.atoms.AtomBuilder;
import com.radixdlt.client.core.atoms.Consumable;
import com.radixdlt.client.core.atoms.Consumer;
import com.radixdlt.client.core.atoms.TransactionAtom;
import com.radixdlt.client.core.crypto.ECKeyPair;
import com.radixdlt.client.core.crypto.ECPublicKey;
import com.radixdlt.client.core.crypto.EncryptedPrivateKey;
import io.reactivex.Completable;
import io.reactivex.Observable;
import java.util.AbstractMap.SimpleImmutableEntry;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

public class TokenTransferTranslator {
	private final RadixUniverse universe;
	private final Function<RadixAddress, Observable<Collection<Consumable>>> particleStore;

	public TokenTransferTranslator(RadixUniverse universe, Function<RadixAddress, Observable<Collection<Consumable>>> particleStore) {
		this.universe = universe;
		this.particleStore = particleStore;
	}

	public TokenTransfer fromAtom(TransactionAtom transactionAtom) {
		List<SimpleImmutableEntry<ECPublicKey, Long>> summary =
			transactionAtom.summary().entrySet().stream()
				.filter(entry -> entry.getValue().containsKey(Asset.TEST.getId()))
				.map(entry -> new SimpleImmutableEntry<>(entry.getKey().iterator().next(), entry.getValue().get(Asset.TEST.getId())))
				.collect(Collectors.toList());

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

		final Data attachment;
		if (transactionAtom.getPayload() != null) {
			final List<EncryptedPrivateKey> protectors;
			if (transactionAtom.getEncryptor() != null && transactionAtom.getEncryptor().getProtectors() != null) {
				protectors = transactionAtom.getEncryptor().getProtectors();
			} else {
				protectors = Collections.emptyList();
			}
			Map<String, Object> metaData = new HashMap<>();
			metaData.put("encrypted", !protectors.isEmpty());
			attachment = Data.raw(transactionAtom.getPayload().getBytes(), metaData, protectors);
		} else {
			attachment = null;
		}

		return TokenTransfer.create(from, to, Asset.TEST, Math.abs(summary.get(0).getValue()), attachment, transactionAtom.getTimestamp());
	}

	public Completable translate(TokenTransfer tokenTransfer, AtomBuilder atomBuilder) {
		atomBuilder.type(TransactionAtom.class);

		return this.particleStore.apply(tokenTransfer.getFrom())
			.firstOrError()
			.flatMapCompletable(unconsumedConsumables -> {

				if (tokenTransfer.getAttachment() != null) {
					atomBuilder.payload(tokenTransfer.getAttachment().getBytes());
					if (!tokenTransfer.getAttachment().getProtectors().isEmpty()) {
						atomBuilder.protectors(tokenTransfer.getAttachment().getProtectors());
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

					atomBuilder.addParticle(newConsumer);
				}

				if (consumerTotal < tokenTransfer.getSubUnitAmount()) {
					return Completable.error(new InsufficientFundsException(
						tokenTransfer.getTokenClass(), consumerTotal, tokenTransfer.getSubUnitAmount()
					));
				}

				List<Consumable> consumables = consumerQuantities.entrySet().stream()
					.map(entry -> new Consumable(entry.getValue(), entry.getKey(), System.nanoTime(), Asset.TEST.getId()))
					.collect(Collectors.toList());
				atomBuilder.addParticles(consumables);

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
