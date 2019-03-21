package com.radixdlt.client.application.translate.tokens;

import java.math.BigDecimal;
import java.util.Map;

import com.radixdlt.client.atommodel.tokens.MintedTokensParticle;
import com.radixdlt.client.core.atoms.ParticleGroup;
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
	public Observable<RequiredShardState> requiredState(Action action) {
		if (!(action instanceof MintTokensAction)) {
			return Observable.empty();
		}

		MintTokensAction mintTokensAction = (MintTokensAction) action;
		RadixAddress tokenDefinitionAddress = mintTokensAction.getTokenDefinitionReference().getAddress();

		return Observable.just(new RequiredShardState(TokenDefinitionsState.class, tokenDefinitionAddress));
	}

	@Override
	public Observable<ParticleGroup> mapToParticleGroups(Action action, Observable<Observable<? extends ApplicationState>> store) {
		if (!(action instanceof MintTokensAction)) {
			return Observable.empty();
		}

		MintTokensAction mintTokensAction = (MintTokensAction) action;
		TokenDefinitionReference tokenDefinition = mintTokensAction.getTokenDefinitionReference();

		return store.firstOrError()
			.flatMap(Observable::firstOrError)
			.map(TokenDefinitionsState.class::cast)
			.map(TokenDefinitionsState::getState)
			.map(m -> getTokenStateOrError(m, tokenDefinition))
			.map(TokenState::getGranularity)
			.map(TokenUnitConversions::unitsToSubunits)
			.map(granularity -> createMintedTokensParticle(mintTokensAction.getAmount(), granularity, tokenDefinition))
			.map(ParticleGroup::of)
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
