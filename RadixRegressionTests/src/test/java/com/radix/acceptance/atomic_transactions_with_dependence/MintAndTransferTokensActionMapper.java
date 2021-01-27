package com.radix.acceptance.atomic_transactions_with_dependence;

import com.google.common.collect.ImmutableList;
import com.radixdlt.client.application.translate.ShardedParticleStateId;
import com.radixdlt.client.application.translate.StatefulActionToParticleGroupsMapper;
import com.radixdlt.client.application.translate.tokens.InsufficientFundsException;
import com.radixdlt.client.application.translate.tokens.TokenUnitConversions;
import com.radixdlt.identifiers.RadixAddress;
import com.radixdlt.client.atommodel.tokens.MutableSupplyTokenDefinitionParticle.TokenTransition;
import com.radixdlt.client.atommodel.tokens.TokenPermission;
import com.radixdlt.client.atommodel.tokens.TransferrableTokensParticle;
import com.radixdlt.client.atommodel.tokens.UnallocatedTokensParticle;
import com.radixdlt.client.core.atoms.ParticleGroup;
import com.radixdlt.client.core.atoms.ParticleGroup.ParticleGroupBuilder;
import com.radixdlt.client.core.atoms.particles.Particle;
import com.radixdlt.identifiers.RRI;
import com.radixdlt.client.core.atoms.particles.Spin;
import com.radix.acceptance.atomic_transactions_with_dependence.FungibleParticleTransitioner.FungibleParticleTransition;
import com.radixdlt.client.core.fungible.NotEnoughFungiblesException;
import com.radixdlt.utils.UInt256;

import java.util.Collections;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.BiFunction;

public class MintAndTransferTokensActionMapper implements StatefulActionToParticleGroupsMapper<MintAndTransferTokensAction> {
	private final BiFunction<
		FungibleParticleTransition<UnallocatedTokensParticle, TransferrableTokensParticle>,
		FungibleParticleTransition<TransferrableTokensParticle, TransferrableTokensParticle>,
		List<ParticleGroup>> mintAndTransferToGroupMapper;

	public MintAndTransferTokensActionMapper() {
		this((mint, transfer) -> {
			ParticleGroupBuilder mintParticleGroupBuilder = ParticleGroup.builder();
			mint.getRemoved().stream().map(t -> (Particle) t).forEach(p -> mintParticleGroupBuilder.addParticle(p, Spin.DOWN));
			mint.getMigrated().stream().map(t -> (Particle) t).forEach(p -> mintParticleGroupBuilder.addParticle(p, Spin.UP));
			mint.getTransitioned().stream().map(t -> (Particle) t).forEach(p -> mintParticleGroupBuilder.addParticle(p, Spin.UP));

			ParticleGroupBuilder transferParticleGroupBuilder = ParticleGroup.builder();
			transfer.getRemoved().stream().map(t -> (Particle) t).forEach(p -> transferParticleGroupBuilder.addParticle(p, Spin.DOWN));
			transfer.getMigrated().stream().map(t -> (Particle) t).forEach(p -> transferParticleGroupBuilder.addParticle(p, Spin.UP));
			transfer.getTransitioned().stream().map(t -> (Particle) t).forEach(p -> transferParticleGroupBuilder.addParticle(p, Spin.UP));

			return Arrays.asList(
				mintParticleGroupBuilder.build(),
				transferParticleGroupBuilder.build()
			);
		});
	}

	public MintAndTransferTokensActionMapper(
		BiFunction<
			FungibleParticleTransition<UnallocatedTokensParticle, TransferrableTokensParticle>,
			FungibleParticleTransition<TransferrableTokensParticle, TransferrableTokensParticle>,
			List<ParticleGroup>> mintAndTransferToGroupMapper) {
		this.mintAndTransferToGroupMapper = Objects.requireNonNull(mintAndTransferToGroupMapper);
	}

	@Override
	public Set<ShardedParticleStateId> requiredState(MintAndTransferTokensAction mintAndTransferTokensAction) {
		RadixAddress tokenDefinitionAddress = mintAndTransferTokensAction.getTokenDefinitionReference().getAddress();

		return Collections.singleton(ShardedParticleStateId.of(UnallocatedTokensParticle.class, tokenDefinitionAddress));
	}

	@Override
	public List<ParticleGroup> mapToParticleGroups(MintAndTransferTokensAction mintTransferAction, Stream<Particle> store) {
		RRI tokenDefinition = mintTransferAction.getTokenDefinitionReference();

		List<UnallocatedTokensParticle> unallocatedTokensParticles = store
			.map(UnallocatedTokensParticle.class::cast)
			.filter(p -> p.getTokDefRef().equals(tokenDefinition))
			.collect(Collectors.toList());

		final FungibleParticleTransition<UnallocatedTokensParticle, TransferrableTokensParticle> mintTransition =
					createMint(mintTransferAction.getAmount(), tokenDefinition, unallocatedTokensParticles);

		final TransferrableTokensParticle transferredTokensParticle = createTransfer(
			mintTransition.getTransitioned().get(0).getTokenPermissions(),
			mintTransition.getTransitioned().get(0).getGranularity(),
			mintTransferAction
		);

		final FungibleParticleTransition<TransferrableTokensParticle, TransferrableTokensParticle> transferTransition =
			new FungibleParticleTransition<>(
				ImmutableList.copyOf(mintTransition.getTransitioned()),
				ImmutableList.of(),
				ImmutableList.of(transferredTokensParticle)
			);

		return mintAndTransferToGroupMapper.apply(mintTransition, transferTransition);
	}

	private TransferrableTokensParticle createTransfer(
		Map<TokenTransition, TokenPermission> permissions,
		UInt256 granularity,
		MintAndTransferTokensAction action
	) {
		return new TransferrableTokensParticle(
			TokenUnitConversions.unitsToSubunits(action.getAmount()),
			granularity,
			action.getTo(),
			System.nanoTime(),
			action.getTokenDefinitionReference(),
			permissions
		);
	}

	private FungibleParticleTransition<UnallocatedTokensParticle, TransferrableTokensParticle> createMint(
		BigDecimal amount,
		RRI tokenDefRef,
		List<UnallocatedTokensParticle> unallocatedTokensParticles
	) {
		final FungibleParticleTransitioner<UnallocatedTokensParticle, TransferrableTokensParticle> transitioner =
			new FungibleParticleTransitioner<>(
				(amt, consumable) -> new TransferrableTokensParticle(
					amt,
					consumable.getGranularity(),
					tokenDefRef.getAddress(),
					System.nanoTime(),
					tokenDefRef,
					consumable.getTokenPermissions()
				),
				mintedTokens -> mintedTokens,
				(amt, consumable) -> new UnallocatedTokensParticle(
					amt,
					consumable.getGranularity(),
					System.nanoTime(),
					tokenDefRef,
					consumable.getTokenPermissions()
				),
				unallocated -> unallocated,
				UnallocatedTokensParticle::getAmount
			);

		final FungibleParticleTransition<UnallocatedTokensParticle, TransferrableTokensParticle> transition;
		try {
			transition = transitioner.createTransition(
				unallocatedTokensParticles,
				TokenUnitConversions.unitsToSubunits(amount)
			);
		} catch (NotEnoughFungiblesException e) {
			throw new InsufficientFundsException(tokenDefRef, TokenUnitConversions.subunitsToUnits(e.getCurrent()), amount);
		}

		return transition;
	}
}
