package com.radixdlt.client.application.translate.tokenclasses;

import com.radixdlt.client.application.translate.tokens.TokenDefinitionReference;
import java.util.Map;

import com.radixdlt.client.atommodel.tokens.MintedTokensParticle;
import com.radixdlt.client.core.atoms.ParticleGroup;
import org.radix.utils.UInt256;

import com.radixdlt.client.application.translate.Action;
import com.radixdlt.client.application.translate.ApplicationState;
import com.radixdlt.client.application.translate.StatefulActionToParticleGroupsMapper;
import com.radixdlt.client.application.translate.tokens.UnknownTokenException;
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

		RadixAddress tokenClassAddress = mintTokensAction.getTokenDefinitionReference().getAddress();

		return Observable.just(new RequiredShardState(TokenTypesState.class, tokenClassAddress));
	}

	@Override
	public Observable<ParticleGroup> mapToParticleGroups(Action action, Observable<Observable<? extends ApplicationState>> store) {
		if (!(action instanceof MintTokensAction)) {
			return Observable.empty();
		}

		MintTokensAction mintTokensAction = (MintTokensAction) action;
		TokenDefinitionReference tokenClass = mintTokensAction.getTokenDefinitionReference();

		return store.firstOrError()
			.flatMap(Observable::firstOrError)
			.map(TokenTypesState.class::cast)
			.map(TokenTypesState::getState)
			.map(m -> getTokenStateOrError(m, tokenClass))
			.map(TokenState::getGranularity)
			.map(TokenDefinitionReference::unitsToSubunits)
			.map(granularity -> createMintedTokensParticle(mintTokensAction.getAmount(), granularity, tokenClass))
			.map(ParticleGroup::of)
			.toObservable();
	}

	private TokenState getTokenStateOrError(Map<TokenDefinitionReference, TokenState> m, TokenDefinitionReference tokenClass) {
		TokenState ts = m.get(tokenClass);
		if (ts == null) {
			throw new UnknownTokenException(tokenClass);
		}
		return ts;
	}

	private SpunParticle createMintedTokensParticle(UInt256 amount, UInt256 granularity, TokenDefinitionReference tokenClass) {
		Particle minted = new MintedTokensParticle(
			amount,
			granularity,
			tokenClass.getAddress(),
			System.currentTimeMillis(),
			tokenClass,
			System.currentTimeMillis() / 60000L + 60000);
		return SpunParticle.up(minted);
	}
}
