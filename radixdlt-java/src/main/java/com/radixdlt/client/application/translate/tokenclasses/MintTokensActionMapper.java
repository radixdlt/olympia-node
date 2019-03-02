package com.radixdlt.client.application.translate.tokenclasses;

import com.radixdlt.client.application.translate.tokens.TokenTypeReference;
import java.util.Map;

import com.radixdlt.client.atommodel.FungibleType;
import com.radixdlt.client.core.atoms.ParticleGroup;
import org.radix.utils.UInt256;

import com.radixdlt.client.application.translate.Action;
import com.radixdlt.client.application.translate.ApplicationState;
import com.radixdlt.client.application.translate.StatefulActionToParticleGroupsMapper;
import com.radixdlt.client.application.translate.tokens.UnknownTokenException;
import com.radixdlt.client.atommodel.accounts.RadixAddress;
import com.radixdlt.client.atommodel.tokens.OwnedTokensParticle;
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

		RadixAddress tokenClassAddress = mintTokensAction.getTokenTypeReference().getAddress();

		return Observable.just(new RequiredShardState(TokenClassesState.class, tokenClassAddress));
	}

	@Override
	public Observable<ParticleGroup> mapToParticleGroups(Action action, Observable<Observable<? extends ApplicationState>> store) {
		if (!(action instanceof MintTokensAction)) {
			return Observable.empty();
		}

		MintTokensAction mintTokensAction = (MintTokensAction) action;
		TokenTypeReference tokenClass = mintTokensAction.getTokenTypeReference();

		return store.firstOrError()
			.flatMap(Observable::firstOrError)
			.map(TokenClassesState.class::cast)
			.map(TokenClassesState::getState)
			.map(m -> getTokenStateOrError(m, tokenClass))
			.map(TokenState::getGranularity)
			.map(TokenTypeReference::unitsToSubunits)
			.map(granularity -> createOwnedTokensParticle(mintTokensAction.getAmount(), granularity, tokenClass))
			.map(ParticleGroup::of)
			.toObservable();
	}

	private TokenState getTokenStateOrError(Map<TokenTypeReference, TokenState> m, TokenTypeReference tokenClass) {
		TokenState ts = m.get(tokenClass);
		if (ts == null) {
			throw new UnknownTokenException(tokenClass);
		}
		return ts;
	}

	private SpunParticle createOwnedTokensParticle(UInt256 amount, UInt256 granularity, TokenTypeReference tokenClass) {
		Particle minted = new OwnedTokensParticle(
			amount,
			granularity,
			FungibleType.MINTED,
			tokenClass.getAddress(),
			System.currentTimeMillis(),
			tokenClass,
			System.currentTimeMillis() / 60000L + 60000);
		return SpunParticle.up(minted);
	}
}
