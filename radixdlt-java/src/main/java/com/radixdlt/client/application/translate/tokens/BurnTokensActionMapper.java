package com.radixdlt.client.application.translate.tokens;

import com.radixdlt.client.application.translate.ShardedAppStateId;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import com.radixdlt.client.atommodel.tokens.BurnedTokensParticle;
import com.radixdlt.client.atommodel.tokens.ConsumableTokens;
import com.radixdlt.client.atommodel.tokens.TransferredTokensParticle;
import com.radixdlt.client.core.atoms.ParticleGroup;
import com.radixdlt.client.core.atoms.particles.Particle;
import org.radix.utils.UInt256;
import org.radix.utils.UInt256s;

import com.radixdlt.client.application.translate.Action;
import com.radixdlt.client.application.translate.ApplicationState;
import com.radixdlt.client.application.translate.StatefulActionToParticleGroupsMapper;
import com.radixdlt.client.application.translate.tokens.TokenBalanceState.Balance;
import com.radixdlt.client.atommodel.accounts.RadixAddress;
import com.radixdlt.client.core.atoms.particles.SpunParticle;

import io.reactivex.Observable;

public class BurnTokensActionMapper implements StatefulActionToParticleGroupsMapper {
	public BurnTokensActionMapper() {
	}

	@Override
	public Observable<ShardedAppStateId> requiredState(Action action) {
		if (!(action instanceof BurnTokensAction)) {
			return Observable.empty();
		}

		BurnTokensAction burnTokensAction = (BurnTokensAction) action;

		RadixAddress address = burnTokensAction.getAddress();

		return Observable.just(ShardedAppStateId.of(TokenBalanceState.class, address));
	}

	@Override
	public Observable<ParticleGroup> mapToParticleGroups(Action action, Observable<Observable<? extends ApplicationState>> store) {
		if (!(action instanceof BurnTokensAction)) {
			return Observable.empty();
		}

		BurnTokensAction burnTokensAction = (BurnTokensAction) action;
		return store.firstOrError()
			.flatMapObservable(s -> s)
			.map(TokenBalanceState.class::cast)
			.firstOrError()
			.toObservable()
			.flatMap(state -> Observable.just(this.map(burnTokensAction, state)));
	}

	private ParticleGroup map(BurnTokensAction burnTokensAction, TokenBalanceState curState) {
		final Map<TokenDefinitionReference, Balance> allConsumables = curState.getBalance();

		final TokenDefinitionReference tokenRef = burnTokensAction.getTokenDefinitionReference();
		final BigDecimal burnAmount = burnTokensAction.getAmount();
		final Balance bal = allConsumables.get(tokenRef);
		if (bal == null) {
			throw new InsufficientFundsException(tokenRef, BigDecimal.ZERO, burnAmount);
		}
		final BigDecimal balance = bal.getAmount();
		if (balance.compareTo(burnAmount) < 0) {
			throw new InsufficientFundsException(tokenRef, balance, burnAmount);
		}
		final UInt256 granularity = TokenUnitConversions.unitsToSubunits(bal.getGranularity());

		final List<ConsumableTokens> unconsumedConsumables =
			Optional.ofNullable(allConsumables.get(burnTokensAction.getTokenDefinitionReference()))
				.map(b -> b.unconsumedTransferrable().collect(Collectors.toList()))
				.orElse(Collections.emptyList());

		List<SpunParticle> particles = new ArrayList<>();

		UInt256 consumerTotal = UInt256.ZERO;
		final UInt256 subunitAmount = TokenUnitConversions.unitsToSubunits(burnTokensAction.getAmount());
		Iterator<ConsumableTokens> iterator = unconsumedConsumables.iterator();
		Map<RadixAddress, UInt256> newUpQuantities = new HashMap<>();

		// HACK for now
		// TODO: remove this, create a ConsumersCreator
		// TODO: randomize this to decrease probability of collision
		while (consumerTotal.compareTo(subunitAmount) < 0 && iterator.hasNext()) {
			final UInt256 left = subunitAmount.subtract(consumerTotal);

			ConsumableTokens particle = iterator.next();
			UInt256 particleAmount = particle.getAmount();
			consumerTotal = consumerTotal.add(particleAmount);

			final UInt256 amount = UInt256s.min(left, particleAmount);
			addConsumerQuantities(particle.getAmount(), particle.getAddress(), null, amount, newUpQuantities);

			particles.add(SpunParticle.down(((Particle) particle)));
		}

		newUpQuantities.entrySet().stream()
			.map(e -> {
				if (e.getKey() == null) {
					return new BurnedTokensParticle(
						e.getValue(),
						granularity,
						burnTokensAction.getTokenDefinitionReference().getAddress(),
						System.nanoTime(),
						burnTokensAction.getTokenDefinitionReference(),
						System.currentTimeMillis() / 60000L + 60000L
					);
				} else {
					return new TransferredTokensParticle(
						e.getValue(),
						granularity,
						e.getKey(),
						System.nanoTime(),
						burnTokensAction.getTokenDefinitionReference(),
						System.currentTimeMillis() / 60000L + 60000L
					);
				}
			})
			.map(SpunParticle::up)
			.forEach(particles::add);
		return ParticleGroup.of(particles);
	}

	// TODO this and same method in TransferTokensToParticleGroupsMapper could be moved to a utility class, abstractions not clear yet
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
}
