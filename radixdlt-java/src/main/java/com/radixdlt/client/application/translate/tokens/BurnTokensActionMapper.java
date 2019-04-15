package com.radixdlt.client.application.translate.tokens;

import com.radixdlt.client.application.translate.ShardedAppStateId;
import com.radixdlt.client.atommodel.tokens.UnallocatedTokensParticle;
import com.radixdlt.client.core.atoms.ParticleGroup.ParticleGroupBuilder;
import com.radixdlt.client.core.atoms.particles.RRI;
import com.radixdlt.client.core.atoms.particles.Spin;
import com.radixdlt.client.core.fungible.FungibleParticleTransitioner;
import com.radixdlt.client.core.fungible.FungibleParticleTransitioner.FungibleParticleTransition;
import java.math.BigDecimal;
import java.util.Collections;
import java.util.Map;
import java.util.stream.Collectors;

import com.radixdlt.client.atommodel.tokens.TransferrableTokensParticle;
import com.radixdlt.client.core.atoms.ParticleGroup;
import com.radixdlt.client.core.atoms.particles.Particle;

import com.radixdlt.client.application.translate.Action;
import com.radixdlt.client.application.translate.ApplicationState;
import com.radixdlt.client.application.translate.StatefulActionToParticleGroupsMapper;
import com.radixdlt.client.application.translate.tokens.TokenBalanceState.Balance;
import com.radixdlt.client.atommodel.accounts.RadixAddress;

import io.reactivex.Observable;
import org.radix.utils.UInt256;

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
		final Map<RRI, Balance> allConsumables = curState.getBalance();
		final RRI tokenRef = burnTokensAction.getTokenDefinitionReference();
		final BigDecimal burnAmount = burnTokensAction.getAmount();

		final Balance bal = allConsumables.get(tokenRef);
		if (bal == null) {
			throw new InsufficientFundsException(tokenRef, BigDecimal.ZERO, burnAmount);
		}

		final BigDecimal balance = bal.getAmount();
		if (balance.compareTo(burnAmount) < 0) {
			throw new InsufficientFundsException(tokenRef, balance, burnAmount);
		}

		final FungibleParticleTransitioner<TransferrableTokensParticle, UnallocatedTokensParticle> transitioner =
			new FungibleParticleTransitioner<>(
				(amt, consumable) -> new UnallocatedTokensParticle(
					amt,
					consumable.getGranularity(),
					System.nanoTime(),
					burnTokensAction.getTokenDefinitionReference(),
					consumable.getTokenPermissions()
				),
				burnedList -> burnedList,
				(amt, consumable) -> new TransferrableTokensParticle(
					amt,
					consumable.getGranularity(),
					consumable.getAddress(),
					System.nanoTime(),
					burnTokensAction.getTokenDefinitionReference(),
					System.currentTimeMillis() / 60000L + 60000L,
					consumable.getTokenPermissions()
				),
				transferredList -> transferredList.stream()
					.map(TransferrableTokensParticle::getAmount)
					.reduce(UInt256::add)
					.map(amt -> Collections.singletonList(
						new TransferrableTokensParticle(
							amt,
							transferredList.get(0).getGranularity(),
							transferredList.get(0).getAddress(),
							System.nanoTime(),
							burnTokensAction.getTokenDefinitionReference(),
							System.currentTimeMillis() / 60000L + 60000L,
							transferredList.get(0).getTokenPermissions()
						)
					)).orElse(Collections.emptyList()),
				TransferrableTokensParticle::getAmount
			);

		FungibleParticleTransition<TransferrableTokensParticle, UnallocatedTokensParticle> transition = transitioner.createTransition(
			bal.unconsumedTransferrable().collect(Collectors.toList()),
			TokenUnitConversions.unitsToSubunits(burnAmount)
		);

		ParticleGroupBuilder particleGroupBuilder = ParticleGroup.builder();
		transition.getRemoved().stream().map(t -> (Particle) t).forEach(p -> particleGroupBuilder.addParticle(p, Spin.DOWN));
		transition.getMigrated().stream().map(t -> (Particle) t).forEach(p -> particleGroupBuilder.addParticle(p, Spin.UP));
		transition.getTransitioned().stream().map(t -> (Particle) t).forEach(p -> particleGroupBuilder.addParticle(p, Spin.UP));

		return particleGroupBuilder.build();
	}
}
