package com.radixdlt.client.application.translate;

import com.radixdlt.client.application.actions.TransferTokensAction;
import com.radixdlt.client.application.identity.RadixIdentity;
import com.radixdlt.client.application.objects.Data;
import com.radixdlt.client.application.objects.TokenTransfer;
import com.radixdlt.client.assets.Asset;
import com.radixdlt.client.core.RadixUniverse;
import com.radixdlt.client.core.address.RadixAddress;
import com.radixdlt.client.core.atoms.AtomBuilder;
import com.radixdlt.client.core.atoms.Consumable;
import com.radixdlt.client.core.atoms.Consumer;
import com.radixdlt.client.core.atoms.TransactionAtom;
import com.radixdlt.client.core.crypto.CryptoException;
import com.radixdlt.client.core.crypto.ECKeyPair;
import com.radixdlt.client.core.crypto.ECPublicKey;
import com.radixdlt.client.core.crypto.EncryptedPrivateKey;
import com.radixdlt.client.core.ledger.ParticleStore;
import io.reactivex.Completable;
import io.reactivex.Observable;
import io.reactivex.Single;
import java.util.AbstractMap.SimpleImmutableEntry;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

public class TokenTransferTranslator {
	private final RadixUniverse universe;
	private final ParticleStore particleStore;
	private final ConcurrentHashMap<RadixAddress, AddressTokenReducer> cache = new ConcurrentHashMap<>();

	public TokenTransferTranslator(RadixUniverse universe, ParticleStore particleStore) {
		this.universe = universe;
		this.particleStore = particleStore;
	}

	public Single<TokenTransfer> fromAtom(TransactionAtom transactionAtom, RadixIdentity identity) {
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
			to = summary.get(0).getValue() < 0L ? null : universe.getAddressFrom(summary.get(0).getKey());
		} else {
			if (summary.get(0).getValue() > 0) {
				from = universe.getAddressFrom(summary.get(1).getKey());
				to = universe.getAddressFrom(summary.get(0).getKey());
			} else {
				from = universe.getAddressFrom(summary.get(0).getKey());
				to = universe.getAddressFrom(summary.get(1).getKey());
			}
		}


		final long subUnitAmount = Math.abs(summary.get(0).getValue());

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

			final long timestamp = transactionAtom.getTimestamp();
			return Single.just(attachment)
				.flatMap(identity::decrypt)
				.map(unencrypted -> new TokenTransfer(from, to, Asset.TEST, subUnitAmount, unencrypted, timestamp))
				.onErrorResumeNext(e -> {
					if (e instanceof CryptoException) {
						return Single.just(
							new TokenTransfer(from, to, Asset.TEST, subUnitAmount, null, timestamp)
						);
					} else {
						return Single.error(e);
					}
				});
		} else {
			return Single.just(
				new TokenTransfer(from, to, Asset.TEST, subUnitAmount, null, transactionAtom.getTimestamp())
			);
		}
	}

	public Observable<AddressTokenState> getTokenState(RadixAddress address) {
		return cache.computeIfAbsent(address, addr -> new AddressTokenReducer(addr, particleStore)).getState();
	}

	public Completable translate(TransferTokensAction transferTokensAction, AtomBuilder atomBuilder) {
		atomBuilder.type(TransactionAtom.class);

		return getTokenState(transferTokensAction.getFrom())
			.map(AddressTokenState::getUnconsumedConsumables)
			.firstOrError()
			.flatMapCompletable(unconsumedConsumables -> {

				if (transferTokensAction.getAttachment() != null) {
					atomBuilder.payload(transferTokensAction.getAttachment().getBytes());
					if (!transferTokensAction.getAttachment().getProtectors().isEmpty()) {
						atomBuilder.protectors(transferTokensAction.getAttachment().getProtectors());
					}
				}

				long consumerTotal = 0;
				Iterator<Consumable> iterator = unconsumedConsumables.iterator();
				Map<Set<ECKeyPair>, Long> consumerQuantities = new HashMap<>();

				// HACK for now
				// TODO: remove this, create a ConsumersCreator
				// TODO: randomize this to decrease probability of collision
				while (consumerTotal < transferTokensAction.getSubUnitAmount() && iterator.hasNext()) {
					final long left = transferTokensAction.getSubUnitAmount() - consumerTotal;

					Consumer newConsumer = iterator.next().toConsumer();
					consumerTotal += newConsumer.getQuantity();

					final long amount = Math.min(left, newConsumer.getQuantity());
					newConsumer.addConsumerQuantities(amount, Collections.singleton(transferTokensAction.getTo().toECKeyPair()),
						consumerQuantities);

					atomBuilder.addParticle(newConsumer);
				}

				if (consumerTotal < transferTokensAction.getSubUnitAmount()) {
					return Completable.error(new InsufficientFundsException(
						transferTokensAction.getTokenClass(), consumerTotal, transferTokensAction.getSubUnitAmount()
					));
				}

				List<Consumable> consumables = consumerQuantities.entrySet().stream()
					.map(entry -> new Consumable(entry.getValue(), entry.getKey(), System.nanoTime(), Asset.TEST.getId()))
					.collect(Collectors.toList());
				atomBuilder.addParticles(consumables);

				return Completable.complete();
			});
	}
}
