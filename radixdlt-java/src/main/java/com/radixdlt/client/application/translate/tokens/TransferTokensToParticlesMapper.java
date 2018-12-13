package com.radixdlt.client.application.translate.tokens;

import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import org.radix.utils.UInt256;
import org.radix.utils.UInt256s;

import com.google.gson.JsonArray;
import com.radixdlt.client.application.identity.Data;
import com.radixdlt.client.application.translate.Action;
import com.radixdlt.client.application.translate.ApplicationState;
import com.radixdlt.client.application.translate.StatefulActionToParticlesMapper;
import com.radixdlt.client.application.translate.tokens.TokenBalanceState.Balance;
import com.radixdlt.client.atommodel.message.MessageParticle;
import com.radixdlt.client.atommodel.message.MessageParticle.MessageParticleBuilder;
import com.radixdlt.client.atommodel.quarks.FungibleQuark.FungibleType;
import com.radixdlt.client.atommodel.tokens.OwnedTokensParticle;
import com.radixdlt.client.core.RadixUniverse;
import com.radixdlt.client.core.atoms.particles.SpunParticle;
import com.radixdlt.client.core.crypto.ECKeyPair;
import com.radixdlt.client.core.crypto.EncryptedPrivateKey;
import com.radixdlt.client.core.crypto.Encryptor;

import io.reactivex.Observable;

/**
 * Maps a send message action to the particles necessary to be included in an atom.
 */
public class TransferTokensToParticlesMapper implements StatefulActionToParticlesMapper {
	private final RadixUniverse universe;

	public TransferTokensToParticlesMapper(RadixUniverse universe) {
		this.universe = universe;
	}

	private Observable<SpunParticle> mapToParticles(TransferTokensAction transfer, List<OwnedTokensParticle> currentParticles) {
		return Observable.create(emitter -> {
			UInt256 consumerTotal = UInt256.ZERO;
			final UInt256 subunitAmount = TokenClassReference.unitsToSubunits(transfer.getAmount());
			UInt256 granularity = UInt256.ZERO;
			Iterator<OwnedTokensParticle> iterator = currentParticles.iterator();
			Map<ECKeyPair, UInt256> consumerQuantities = new HashMap<>();

			// HACK for now
			// TODO: remove this, create a ConsumersCreator
			// TODO: randomize this to decrease probability of collision
			while (consumerTotal.compareTo(subunitAmount) < 0 && iterator.hasNext()) {
				final UInt256 left = subunitAmount.subtract(consumerTotal);

				OwnedTokensParticle particle = iterator.next();
				if (granularity.isZero()) {
					granularity = particle.getGranularity();
				}
				consumerTotal = consumerTotal.add(particle.getAmount());

				final UInt256 amount = UInt256s.min(left, particle.getAmount());
				particle.addConsumerQuantities(amount, transfer.getTo().toECKeyPair(), consumerQuantities);

				SpunParticle<OwnedTokensParticle> down = SpunParticle.down(particle);
				emitter.onNext(down);
			}

			final UInt256 computedGranularity = granularity;
			consumerQuantities.entrySet().stream()
				.map(entry -> new OwnedTokensParticle(
					entry.getValue(),
					computedGranularity,
					FungibleType.TRANSFERRED,
					universe.getAddressFrom(entry.getKey().getPublicKey()),
					System.nanoTime(),
					transfer.getTokenClassReference(),
					System.currentTimeMillis() / 60000L + 60000L
				))
				.map(SpunParticle::up)
				.forEach(emitter::onNext);

			emitter.onComplete();
		});
	}

	private Observable<SpunParticle> mapToAttachmentParticles(TransferTokensAction transfer) {
		return Observable.create(emitter -> {
			final Data attachment = transfer.getAttachment();
			if (attachment != null) {
				emitter.onNext(
					SpunParticle.up(
						new MessageParticleBuilder()
							.payload(attachment.getBytes())
							.from(transfer.getFrom())
							.to(transfer.getTo())
							.build()
					)
				);

				Encryptor encryptor = attachment.getEncryptor();
				if (encryptor != null) {
					JsonArray protectorsJson = new JsonArray();
					encryptor.getProtectors().stream().map(EncryptedPrivateKey::base64).forEach(protectorsJson::add);

					byte[] encryptorPayload = protectorsJson.toString().getBytes(StandardCharsets.UTF_8);
					MessageParticle encryptorParticle = new MessageParticleBuilder()
						.payload(encryptorPayload)
						.setMetaData("application", "encryptor")
						.setMetaData("contentType", "application/json")
						.from(transfer.getFrom())
						.to(transfer.getTo())
						.build();
					emitter.onNext(SpunParticle.up(encryptorParticle));
				}
			}

			emitter.onComplete();
		});
	}

	@Override
	public Observable<RequiredShardState> requiredState(Action action) {
		if (!(action instanceof TransferTokensAction)) {
			return Observable.empty();
		}

		TransferTokensAction transfer = (TransferTokensAction) action;

		return Observable.just(new RequiredShardState(TokenBalanceState.class, transfer.getFrom()));
	}

	@Override
	public Observable<Action> sideEffects(Action action, Observable<Observable<? extends ApplicationState>> store) {
		return Observable.empty();
	}

	@Override
	public Observable<SpunParticle> mapToParticles(
		Action action,
		Observable<Observable<? extends ApplicationState>> store
	) throws InsufficientFundsException {
		if (!(action instanceof TransferTokensAction)) {
			return Observable.empty();
		}

		TransferTokensAction transfer = (TransferTokensAction) action;

		return store.firstOrError()
			.flatMapObservable(s -> s)
			.map(appState -> (TokenBalanceState) appState)
			.firstOrError()
			.map(curState -> {
				final TokenClassReference tokenRef = transfer.getTokenClassReference();
				final Map<TokenClassReference, Balance> allConsumables = curState.getBalance();
				final Balance balance = Optional.ofNullable(
					allConsumables.get(transfer.getTokenClassReference())).orElse(Balance.empty(BigInteger.ONE));
				if (balance.getAmount().compareTo(transfer.getAmount()) < 0) {
					throw new InsufficientFundsException(
							tokenRef, balance.getAmount(), transfer.getAmount()
					);
				}
				return allConsumables;
			})
			.map(allConsumables ->
				Optional.ofNullable(allConsumables.get(transfer.getTokenClassReference()))
						.map(bal -> bal.unconsumedConsumables().collect(Collectors.toList()))
						.orElse(Collections.emptyList())
			)
			.flatMapObservable(tokenConsumables -> this.mapToParticles(transfer, tokenConsumables))
			.concatWith(this.mapToAttachmentParticles(transfer));
	}
}
