package com.radixdlt.client.application.translate.tokens;

import com.radixdlt.client.application.translate.ShardedAppStateId;
import com.radixdlt.client.atommodel.accounts.RadixAddress;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import com.radixdlt.client.atommodel.tokens.ConsumableTokens;
import com.radixdlt.client.atommodel.tokens.TransferredTokensParticle;
import com.radixdlt.client.core.atoms.particles.Particle;
import java.util.stream.Stream;
import org.radix.utils.UInt256;
import org.radix.utils.UInt256s;

import com.google.gson.JsonArray;
import com.radixdlt.client.application.identity.Data;
import com.radixdlt.client.application.translate.Action;
import com.radixdlt.client.application.translate.ApplicationState;
import com.radixdlt.client.application.translate.StatefulActionToParticleGroupsMapper;
import com.radixdlt.client.application.translate.tokens.TokenBalanceState.Balance;
import com.radixdlt.client.atommodel.message.MessageParticle;
import com.radixdlt.client.atommodel.message.MessageParticle.MessageParticleBuilder;
import com.radixdlt.client.core.atoms.ParticleGroup;
import com.radixdlt.client.core.atoms.particles.SpunParticle;
import com.radixdlt.client.core.crypto.EncryptedPrivateKey;
import com.radixdlt.client.core.crypto.Encryptor;
import io.reactivex.Observable;

/**
 * Maps a send message action to the particles necessary to be included in an atom.
 */
public class TransferTokensToParticleGroupsMapper implements StatefulActionToParticleGroupsMapper {
	public TransferTokensToParticleGroupsMapper() {
	}

	private List<SpunParticle> mapToParticles(TransferTokensAction transfer, List<ConsumableTokens> currentParticles) {
		List<SpunParticle> spunParticles = new ArrayList<>();

		UInt256 consumerTotal = UInt256.ZERO;
		final UInt256 subunitAmount = TokenUnitConversions.unitsToSubunits(transfer.getAmount());
		UInt256 granularity = UInt256.ZERO;
		Iterator<ConsumableTokens> iterator = currentParticles.iterator();
		Map<RadixAddress, UInt256> consumerQuantities = new HashMap<>();

		// HACK for now
		// TODO: remove this, create a ConsumersCreator
		// TODO: randomize this to decrease probability of collision
		while (consumerTotal.compareTo(subunitAmount) < 0 && iterator.hasNext()) {
			final UInt256 left = subunitAmount.subtract(consumerTotal);

			ConsumableTokens particle = iterator.next();
			if (granularity.isZero()) {
				granularity = particle.getGranularity();
			}
			consumerTotal = consumerTotal.add(particle.getAmount());

			final UInt256 amount = UInt256s.min(left, particle.getAmount());
			addConsumerQuantities(particle.getAmount(), particle.getAddress(), transfer.getTo(),
				amount, consumerQuantities);

			spunParticles.add(SpunParticle.down(((Particle) particle)));
		}

		final UInt256 computedGranularity = granularity;
		consumerQuantities.entrySet().stream()
			.map(entry -> new TransferredTokensParticle(
				entry.getValue(),
				computedGranularity,
				entry.getKey(),
				System.nanoTime(),
				transfer.getTokenDefinitionReference(),
				System.currentTimeMillis() / 60000L + 60000L
			))
			.map(SpunParticle::up)
			.forEach(spunParticles::add);

		return spunParticles;
	}

	// TODO this and same method in BurnTokensActionMapper could be moved to a utility class, abstractions not clear yet
	private static void addConsumerQuantities(UInt256 amount, RadixAddress oldOwner, RadixAddress newOwner,
	                                          UInt256 usedAmount, Map<RadixAddress, UInt256> consumerQuantities) {
		if (usedAmount.compareTo(amount) > 0) {
			throw new IllegalArgumentException(
				"Unable to create consumable with amount " + usedAmount + " (available: " + amount + ")"
			);
		}

		if (amount.equals(usedAmount)) {
			consumerQuantities.merge(newOwner, amount, UInt256::add);
			return;
		}

		consumerQuantities.merge(newOwner, usedAmount, UInt256::add);
		consumerQuantities.merge(oldOwner, amount.subtract(usedAmount), UInt256::add);
	}

	private List<SpunParticle> mapToAttachmentParticles(TransferTokensAction transfer) {
		final Data attachment = transfer.getAttachment();
		if (attachment != null) {
			List<SpunParticle> spunParticles = new ArrayList<>();
			spunParticles.add(
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
					.metaData("application", "encryptor")
					.metaData("contentType", "application/json")
					.from(transfer.getFrom())
					.to(transfer.getTo())
					.build();
				spunParticles.add(SpunParticle.up(encryptorParticle));
			}

			return spunParticles;
		} else {
			return Collections.emptyList();
		}
	}

	@Override
	public Observable<ShardedAppStateId> requiredState(Action action) {
		if (!(action instanceof TransferTokensAction)) {
			return Observable.empty();
		}

		TransferTokensAction transfer = (TransferTokensAction) action;

		return Observable.just(ShardedAppStateId.of(TokenBalanceState.class, transfer.getFrom()));
	}

	@Override
	public Observable<Action> sideEffects(Action action, Observable<Observable<? extends ApplicationState>> store) {
		return Observable.empty();
	}

	@Override
	public Observable<ParticleGroup> mapToParticleGroups(
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
				final TokenDefinitionReference tokenRef = transfer.getTokenDefinitionReference();
				final Map<TokenDefinitionReference, Balance> allConsumables = curState.getBalance();
				final Balance balance = Optional.ofNullable(
					allConsumables.get(transfer.getTokenDefinitionReference())).orElse(Balance.empty(BigInteger.ONE));
				if (balance.getAmount().compareTo(transfer.getAmount()) < 0) {
					throw new InsufficientFundsException(
						tokenRef, balance.getAmount(), transfer.getAmount()
					);
				}
				return allConsumables;
			})
			.map(allConsumables ->
				Optional.ofNullable(allConsumables.get(transfer.getTokenDefinitionReference()))
					.map(bal -> bal.unconsumedTransferrable().collect(Collectors.toList()))
					.orElse(Collections.emptyList())
		)
			.map(tokenConsumables -> {
				List<SpunParticle> transferParticles = this.mapToParticles(transfer, tokenConsumables);
				List<SpunParticle> attachmentParticles = this.mapToAttachmentParticles(transfer);
				return Stream.concat(transferParticles.stream(), attachmentParticles.stream()).collect(Collectors.toList());
			})
			.map(ParticleGroup::of)
			.toObservable();
	}
}
