package com.radixdlt.client.application.translate.tokens;

import com.radixdlt.client.application.translate.ShardedParticleStateId;
import com.radixdlt.client.atommodel.tokens.TransferrableTokensParticle;
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
import java.util.Map;

import com.radixdlt.client.core.atoms.ParticleGroup;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.radix.utils.UInt256;

import com.radixdlt.client.application.translate.Action;
import com.radixdlt.client.application.translate.StatefulActionToParticleGroupsMapper;
import com.radixdlt.client.atommodel.accounts.RadixAddress;
import com.radixdlt.client.core.atoms.particles.Particle;

public class MintTokensActionMapper implements StatefulActionToParticleGroupsMapper {
	private final FungibleParticleTransitioner<UnallocatedTokensParticle, TransferrableTokensParticle> transitioner;

	public MintTokensActionMapper() {
		this.transitioner = new FungibleParticleTransitioner<>(
			(amt, consumable) -> new TransferrableTokensParticle(
				amt,
				consumable.getGranularity(),
				consumable.getAddress(),
				System.nanoTime(),
				consumable.getTokDefRef(),
				System.currentTimeMillis() / 60000L + 60000L,
				consumable.getTokenPermissions()
			),
			mintedTokens -> mintedTokens,
			(amt, consumable) -> new UnallocatedTokensParticle(
				amt,
				consumable.getGranularity(),
				System.nanoTime(),
				consumable.getTokDefRef(),
				consumable.getTokenPermissions()
			),
			unallocated -> unallocated,
			UnallocatedTokensParticle::getAmount
		);
	}

	@Override
	public Set<ShardedParticleStateId> requiredState(Action action) {
		if (!(action instanceof MintTokensAction)) {
			return Collections.emptySet();
		}

		MintTokensAction mintTokensAction = (MintTokensAction) action;
		RadixAddress tokenDefinitionAddress = mintTokensAction.getTokenDefinitionReference().getAddress();

		return Collections.singleton(ShardedParticleStateId.of(UnallocatedTokensParticle.class, tokenDefinitionAddress));
	}

	@Override
	public List<ParticleGroup> mapToParticleGroups(Action action, Stream<Particle> store) {
		if (!(action instanceof MintTokensAction)) {
			return Collections.emptyList();
		}

		MintTokensAction mintTokensAction = (MintTokensAction) action;
		RRI tokenDefinition = mintTokensAction.getTokenDefinitionReference();

		if (mintTokensAction.getAmount().compareTo(BigDecimal.ZERO) <= 0) {
			throw new IllegalArgumentException("Mint amount must be greater than 0.");
		}

		final FungibleParticleTransition<UnallocatedTokensParticle, TransferrableTokensParticle> transition;
		try {
			transition = transitioner.createTransition(
				store.map(UnallocatedTokensParticle.class::cast)
					.filter(p -> p.getTokDefRef().equals(tokenDefinition))
					.collect(Collectors.toList()),
				TokenUnitConversions.unitsToSubunits(mintTokensAction.getAmount())
			);
		} catch (NotEnoughFungibleException e) {
			if (e.getCurrent().equals(UInt256.ZERO)) {
				throw new UnknownTokenException(mintTokensAction.getTokenDefinitionReference());
			} else {
				throw new TokenOverMintException(
					mintTokensAction.getTokenDefinitionReference(),
					TokenUnitConversions.subunitsToUnits(UInt256.MAX_VALUE),
					TokenUnitConversions.subunitsToUnits(UInt256.MAX_VALUE.subtract(e.getCurrent())),
					mintTokensAction.getAmount()
				);
			}
		}


		ParticleGroupBuilder particleGroupBuilder = ParticleGroup.builder();
		transition.getRemoved().stream().map(t -> (Particle) t).forEach(p -> particleGroupBuilder.addParticle(p, Spin.DOWN));
		transition.getMigrated().stream().map(t -> (Particle) t).forEach(p -> particleGroupBuilder.addParticle(p, Spin.UP));
		transition.getTransitioned().stream().map(t -> (Particle) t).forEach(p -> particleGroupBuilder.addParticle(p, Spin.UP));

		return Collections.singletonList(particleGroupBuilder.build());
	}

	private TokenState getTokenStateOrError(Map<RRI, TokenState> m, RRI tokenDefinition) {
		TokenState ts = m.get(tokenDefinition);
		if (ts == null) {
			throw new UnknownTokenException(tokenDefinition);
		}
		return ts;
	}
}
