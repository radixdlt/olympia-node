package com.radixdlt.client.application.translate.tokens;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.radixdlt.client.application.translate.Action;
import com.radixdlt.client.application.translate.ApplicationState;
import com.radixdlt.client.application.translate.ShardedAppStateId;
import com.radixdlt.client.application.translate.StatefulActionToParticleGroupsMapper;
import com.radixdlt.client.application.translate.tokens.TokenState.TokenSupplyType;
import com.radixdlt.client.atommodel.accounts.RadixAddress;
import com.radixdlt.client.atommodel.tokens.TokenDefinitionParticle.TokenTransition;
import com.radixdlt.client.atommodel.tokens.TokenPermission;
import com.radixdlt.client.atommodel.tokens.TransferrableTokensParticle;
import com.radixdlt.client.atommodel.tokens.UnallocatedTokensParticle;
import com.radixdlt.client.core.atoms.ParticleGroup;
import com.radixdlt.client.core.atoms.ParticleGroup.ParticleGroupBuilder;
import com.radixdlt.client.core.atoms.particles.Particle;
import com.radixdlt.client.core.atoms.particles.RRI;
import com.radixdlt.client.core.atoms.particles.Spin;
import com.radixdlt.client.core.fungible.FungibleParticleTransitioner;
import com.radixdlt.client.core.fungible.FungibleParticleTransitioner.FungibleParticleTransition;
import com.radixdlt.client.core.fungible.NotEnoughFungibleException;
import java.util.Collections;
import java.util.Map.Entry;
import java.util.Set;
import java.util.stream.Collectors;
import org.radix.utils.UInt256;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.BiFunction;

public class MintAndTransferTokensActionMapper implements StatefulActionToParticleGroupsMapper {
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
	public Set<ShardedAppStateId> requiredState(Action action) {
		if (!(action instanceof MintAndTransferTokensAction)) {
			return Collections.emptySet();
		}

		MintAndTransferTokensAction mintAndTransferTokensAction = (MintAndTransferTokensAction) action;
		RadixAddress tokenDefinitionAddress = mintAndTransferTokensAction.getTokenDefinitionReference().getAddress();

		return Collections.singleton(ShardedAppStateId.of(TokenDefinitionsState.class, tokenDefinitionAddress));
	}

	@Override
	public List<ParticleGroup> mapToParticleGroups(Action action, Map<ShardedAppStateId, ? extends ApplicationState> store) {
		if (!(action instanceof MintAndTransferTokensAction)) {
			return Collections.emptyList();
		}

		MintAndTransferTokensAction mintTransferAction = (MintAndTransferTokensAction) action;
		RRI tokenDefinition = mintTransferAction.getTokenDefinitionReference();
		ShardedAppStateId shardedAppStateId = ShardedAppStateId.of(TokenDefinitionsState.class, tokenDefinition.getAddress());

		TokenDefinitionsState state = (TokenDefinitionsState) store.get(shardedAppStateId);
		TokenState tokenState = getTokenStateOrError(state.getState(), tokenDefinition);

		final FungibleParticleTransition<UnallocatedTokensParticle, TransferrableTokensParticle> mintTransition =
					createMint(mintTransferAction.getAmount(), tokenDefinition, tokenState);

		final TransferrableTokensParticle transferredTokensParticle = createTransfer(
			tokenState.getTokenSupplyType() == TokenSupplyType.FIXED
				? ImmutableMap.of(
					TokenTransition.MINT, TokenPermission.TOKEN_CREATION_ONLY,
					TokenTransition.BURN, TokenPermission.TOKEN_CREATION_ONLY)
				: ImmutableMap.of(
					TokenTransition.MINT, TokenPermission.TOKEN_OWNER_ONLY,
					TokenTransition.BURN, TokenPermission.TOKEN_OWNER_ONLY),
			TokenUnitConversions.unitsToSubunits(tokenState.getGranularity()),
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

	private TokenState getTokenStateOrError(Map<RRI, TokenState> m, RRI tokenDefinition) {
		TokenState ts = m.get(tokenDefinition);
		if (ts == null) {
			throw new UnknownTokenException(tokenDefinition);
		}
		return ts;
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
			System.currentTimeMillis() / 60000L + 60000L,
			permissions
		);
	}

	private FungibleParticleTransition<UnallocatedTokensParticle, TransferrableTokensParticle> createMint(
		BigDecimal amount,
		RRI tokenDefRef,
		TokenState tokenState
	) {
		final FungibleParticleTransitioner<UnallocatedTokensParticle, TransferrableTokensParticle> transitioner =
			new FungibleParticleTransitioner<>(
				(amt, consumable) -> new TransferrableTokensParticle(
					amt,
					consumable.getGranularity(),
					tokenDefRef.getAddress(),
					System.nanoTime(),
					tokenDefRef,
					System.currentTimeMillis() / 60000L + 60000L,
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
				tokenState.getUnallocatedTokens().entrySet().stream()
					.map(Entry::getValue)
					.collect(Collectors.toList()),
				TokenUnitConversions.unitsToSubunits(amount)
			);
		} catch (NotEnoughFungibleException e) {
			throw new InsufficientFundsException(tokenDefRef, TokenUnitConversions.subunitsToUnits(e.getCurrent()), amount);
		}

		return transition;
	}
}
