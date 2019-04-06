package com.radixdlt.client.application.translate.tokens;

import com.radixdlt.client.application.translate.ShardedAppStateId;
import com.radixdlt.client.atommodel.tokens.UnallocatedTokensParticle;
import com.radixdlt.client.core.atoms.ParticleGroup.ParticleGroupBuilder;
import com.radixdlt.client.core.atoms.particles.Spin;
import com.radixdlt.client.core.fungible.FungibleParticleTransitioner;
import com.radixdlt.client.core.fungible.FungibleParticleTransitioner.FungibleParticleTransition;
import java.math.BigDecimal;
import java.util.Map;

import com.radixdlt.client.atommodel.tokens.MintedTokensParticle;
import com.radixdlt.client.core.atoms.ParticleGroup;
import java.util.Map.Entry;
import java.util.stream.Collectors;
import org.radix.utils.UInt256;

import com.radixdlt.client.application.translate.Action;
import com.radixdlt.client.application.translate.ApplicationState;
import com.radixdlt.client.application.translate.StatefulActionToParticleGroupsMapper;
import com.radixdlt.client.atommodel.accounts.RadixAddress;
import com.radixdlt.client.core.atoms.particles.Particle;
import com.radixdlt.client.core.atoms.particles.SpunParticle;

import io.reactivex.Observable;

public class MintTokensActionMapper implements StatefulActionToParticleGroupsMapper {

	@Override
	public Observable<ShardedAppStateId> requiredState(Action action) {
		if (!(action instanceof MintTokensAction)) {
			return Observable.empty();
		}

		MintTokensAction mintTokensAction = (MintTokensAction) action;
		RadixAddress tokenDefinitionAddress = mintTokensAction.getTokenDefinitionReference().getAddress();

		return Observable.just(ShardedAppStateId.of(TokenDefinitionsState.class, tokenDefinitionAddress));
	}

	@Override
	public Observable<ParticleGroup> mapToParticleGroups(Action action, Observable<Observable<? extends ApplicationState>> store) {
		if (!(action instanceof MintTokensAction)) {
			return Observable.empty();
		}

		MintTokensAction mintTokensAction = (MintTokensAction) action;
		TokenDefinitionReference tokenDefinition = mintTokensAction.getTokenDefinitionReference();

		if (mintTokensAction.getAmount().compareTo(BigDecimal.ZERO) <= 0) {
			throw new IllegalArgumentException("Mint amount must be greater than 0.");
		}

		return store.firstOrError()
			.flatMap(Observable::firstOrError)
			.map(TokenDefinitionsState.class::cast)
			.map(TokenDefinitionsState::getState)
			.map(m -> getTokenStateOrError(m, tokenDefinition))
			.map(tokenState -> {
				final BigDecimal unallocatedSupply = tokenState.getUnallocatedSupply();

				if (unallocatedSupply.compareTo(mintTokensAction.getAmount()) < 0) {
					throw new TokenOverMintException(
						mintTokensAction.getTokenDefinitionReference(),
						TokenUnitConversions.subunitsToUnits(UInt256.MAX_VALUE),
						TokenUnitConversions.subunitsToUnits(UInt256.MAX_VALUE).subtract(unallocatedSupply),
						mintTokensAction.getAmount()
					);
				}

				final FungibleParticleTransitioner<UnallocatedTokensParticle, MintedTokensParticle> transitioner =
					new FungibleParticleTransitioner<>(
						(amt, consumable) -> new MintedTokensParticle(
							amt,
							consumable.getGranularity(),
							tokenDefinition.getAddress(),
							System.nanoTime(),
							tokenDefinition,
							System.currentTimeMillis() / 60000L + 60000L
						),
						mintedTokens -> mintedTokens,
						(amt, consumable) -> new UnallocatedTokensParticle(
							amt,
							consumable.getGranularity(),
							System.nanoTime(),
							tokenDefinition
						),
						unallocated -> unallocated,
						UnallocatedTokensParticle::getAmount
					);

				FungibleParticleTransition<UnallocatedTokensParticle, MintedTokensParticle> transition = transitioner.createTransition(
					tokenState.getUnallocatedTokens().entrySet().stream()
						.map(Entry::getValue)
						.collect(Collectors.toList()),
					TokenUnitConversions.unitsToSubunits(mintTokensAction.getAmount())
				);

				ParticleGroupBuilder particleGroupBuilder = ParticleGroup.builder();
				transition.getRemoved().stream().map(t -> (Particle) t).forEach(p -> particleGroupBuilder.addParticle(p, Spin.DOWN));
				transition.getMigrated().stream().map(t -> (Particle) t).forEach(p -> particleGroupBuilder.addParticle(p, Spin.UP));
				transition.getTransitioned().stream().map(t -> (Particle) t).forEach(p -> particleGroupBuilder.addParticle(p, Spin.UP));

				return particleGroupBuilder.build();
			})
			.toObservable();
	}

	private TokenState getTokenStateOrError(Map<TokenDefinitionReference, TokenState> m, TokenDefinitionReference tokenDefinition) {
		TokenState ts = m.get(tokenDefinition);
		if (ts == null) {
			throw new UnknownTokenException(tokenDefinition);
		}
		return ts;
	}

	private SpunParticle createMintedTokensParticle(BigDecimal amount, UInt256 granularity, TokenDefinitionReference tokenDefinition) {
		Particle minted = new MintedTokensParticle(
			TokenUnitConversions.unitsToSubunits(amount),
			granularity,
			tokenDefinition.getAddress(),
			System.currentTimeMillis(),
			tokenDefinition,
			System.currentTimeMillis() / 60000L + 60000);
		return SpunParticle.up(minted);
	}
}
