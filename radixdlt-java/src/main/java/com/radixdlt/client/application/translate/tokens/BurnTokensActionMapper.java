package com.radixdlt.client.application.translate.tokens;

import com.radixdlt.client.application.translate.ShardedParticleStateId;
import com.radixdlt.client.atommodel.tokens.UnallocatedTokensParticle;
import com.radixdlt.client.core.atoms.ParticleGroup.ParticleGroupBuilder;
import com.radixdlt.client.core.atoms.particles.RRI;
import com.radixdlt.client.core.atoms.particles.Spin;
import com.radixdlt.client.core.fungible.FungibleParticleTransitioner;
import com.radixdlt.client.core.fungible.FungibleParticleTransitioner.FungibleParticleTransition;
import com.radixdlt.client.core.fungible.NotEnoughFungibleException;
import java.math.BigDecimal;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import com.radixdlt.client.atommodel.tokens.TransferrableTokensParticle;
import com.radixdlt.client.core.atoms.ParticleGroup;
import com.radixdlt.client.core.atoms.particles.Particle;

import com.radixdlt.client.application.translate.Action;
import com.radixdlt.client.application.translate.StatefulActionToParticleGroupsMapper;
import com.radixdlt.client.atommodel.accounts.RadixAddress;

import java.util.stream.Stream;
import org.radix.utils.UInt256;

public class BurnTokensActionMapper implements StatefulActionToParticleGroupsMapper {
	private final FungibleParticleTransitioner<TransferrableTokensParticle, UnallocatedTokensParticle> transitioner;

	public BurnTokensActionMapper() {
		this.transitioner = new FungibleParticleTransitioner<>(
			(amt, consumable) -> new UnallocatedTokensParticle(
				amt,
				consumable.getGranularity(),
				System.nanoTime(),
				consumable.getTokenDefinitionReference(),
				consumable.getTokenPermissions()
			),
			burnedList -> burnedList,
			(amt, consumable) -> new TransferrableTokensParticle(
				amt,
				consumable.getGranularity(),
				consumable.getAddress(),
				System.nanoTime(),
				consumable.getTokenDefinitionReference(),
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
						transferredList.get(0).getTokenDefinitionReference(),
						System.currentTimeMillis() / 60000L + 60000L,
						transferredList.get(0).getTokenPermissions()
					)
				)).orElse(Collections.emptyList()),
			TransferrableTokensParticle::getAmount
		);
	}

	@Override
	public Set<ShardedParticleStateId> requiredState(Action action) {
		if (!(action instanceof BurnTokensAction)) {
			return Collections.emptySet();
		}

		BurnTokensAction burnTokensAction = (BurnTokensAction) action;

		RadixAddress address = burnTokensAction.getAddress();

		return Collections.singleton(ShardedParticleStateId.of(TransferrableTokensParticle.class, address));
	}

	@Override
	public List<ParticleGroup> mapToParticleGroups(Action action, Stream<Particle> store) {
		if (!(action instanceof BurnTokensAction)) {
			return Collections.emptyList();
		}

		BurnTokensAction burnTokensAction = (BurnTokensAction) action;

		final RRI tokenRef = burnTokensAction.getTokenDefinitionReference();
		final BigDecimal burnAmount = burnTokensAction.getAmount();

		final FungibleParticleTransition<TransferrableTokensParticle, UnallocatedTokensParticle> transition;
		try {
			transition = transitioner.createTransition(
				store.map(TransferrableTokensParticle.class::cast)
					.filter(p -> p.getTokenDefinitionReference().equals(tokenRef))
					.collect(Collectors.toList()),
				TokenUnitConversions.unitsToSubunits(burnAmount)
			);
		} catch (NotEnoughFungibleException e) {
			throw new InsufficientFundsException(tokenRef, TokenUnitConversions.subunitsToUnits(e.getCurrent()), burnAmount);
		}

		ParticleGroupBuilder particleGroupBuilder = ParticleGroup.builder();
		transition.getRemoved().stream().map(t -> (Particle) t).forEach(p -> particleGroupBuilder.addParticle(p, Spin.DOWN));
		transition.getMigrated().stream().map(t -> (Particle) t).forEach(p -> particleGroupBuilder.addParticle(p, Spin.UP));
		transition.getTransitioned().stream().map(t -> (Particle) t).forEach(p -> particleGroupBuilder.addParticle(p, Spin.UP));

		return Collections.singletonList(particleGroupBuilder.build());
	}
}
