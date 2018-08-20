package com.radixdlt.client.application.translate;

import com.radixdlt.client.application.actions.TokenTransfer;
import com.radixdlt.client.assets.Asset;
import com.radixdlt.client.core.RadixUniverse;
import com.radixdlt.client.core.atoms.AtomBuilder;
import com.radixdlt.client.core.atoms.Consumable;
import com.radixdlt.client.core.atoms.Consumer;
import com.radixdlt.client.core.atoms.TransactionAtom;
import com.radixdlt.client.core.crypto.ECKeyPair;
import com.radixdlt.client.core.crypto.ECPublicKey;
import io.reactivex.Completable;
import java.util.AbstractMap.SimpleImmutableEntry;
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

	public TokenTransferTranslator(RadixUniverse universe, ConsumableDataSource consumableDataSource) {
		this.universe = universe;
		this.consumableDataSource = consumableDataSource;
	}

	public TokenTransfer fromAtom(TransactionAtom transactionAtom) {
		List<SimpleImmutableEntry<ECPublicKey, Long>> summary =
			transactionAtom.summary().entrySet().stream()
				.filter(entry -> entry.getValue().containsKey(Asset.XRD.getId()))
				.map(entry -> new SimpleImmutableEntry<>(entry.getKey().iterator().next(), entry.getValue().get(Asset.XRD.getId())))
				.collect(Collectors.toList());

		if (summary.size() == 1) {
			return new TokenTransfer(
				summary.get(0).getValue() <= 0L ? universe.getAddressFrom(summary.get(0).getKey()) : null,
				summary.get(0).getValue() <= 0L ? null : universe.getAddressFrom(summary.get(0).getKey()),
				Asset.XRD,
				summary.get(0).getValue()
			);
		}

		if (summary.size() > 2) {
			throw new IllegalStateException("More than two participants in token transfer. Unable to handle: " + summary);
		}

		if (summary.get(0).getValue() > 0) {
			return new TokenTransfer(
				universe.getAddressFrom(summary.get(1).getKey()),
				universe.getAddressFrom(summary.get(0).getKey()),
				Asset.XRD,
				summary.get(0).getValue()
			);
		} else {
			return new TokenTransfer(
				universe.getAddressFrom(summary.get(0).getKey()),
				universe.getAddressFrom(summary.get(1).getKey()),
				Asset.XRD,
				summary.get(1).getValue()
			);
		}
	}

	public Completable translate(TokenTransfer tokenTransfer, AtomBuilder atomBuilder) {
		atomBuilder.type(TransactionAtom.class);

		return this.consumableDataSource.getConsumables(tokenTransfer.getFrom())
			.firstOrError()
			.flatMapCompletable(unconsumedConsumables -> {

				/*
				if (payload != null) {
					atomBuilder.payload(payload);
				}
				*/

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
					.map(entry -> new Consumable(entry.getValue(), entry.getKey(), System.nanoTime(), Asset.XRD.getId())).collect(
						Collectors.toList());
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
