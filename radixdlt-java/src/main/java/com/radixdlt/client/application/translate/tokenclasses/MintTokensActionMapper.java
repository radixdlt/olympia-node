package com.radixdlt.client.application.translate.tokenclasses;

import org.radix.utils.UInt256;

import com.radixdlt.client.application.translate.Action;
import com.radixdlt.client.application.translate.ApplicationState;
import com.radixdlt.client.application.translate.StatefulActionToParticlesMapper;
import com.radixdlt.client.atommodel.accounts.RadixAddress;
import com.radixdlt.client.atommodel.quarks.FungibleQuark;
import com.radixdlt.client.atommodel.tokens.OwnedTokensParticle;
import com.radixdlt.client.atommodel.tokens.TokenClassReference;
import com.radixdlt.client.core.atoms.particles.Particle;
import com.radixdlt.client.core.atoms.particles.SpunParticle;

import io.reactivex.Observable;

public class MintTokensActionMapper implements StatefulActionToParticlesMapper {

	@Override
	public Observable<RequiredShardState> requiredState(Action action) {
		if (!(action instanceof MintTokensAction)) {
			return Observable.empty();
		}

		MintTokensAction mintTokensAction = (MintTokensAction) action;

		RadixAddress tokenClassAddress = mintTokensAction.getTokenClassReference().getAddress();

		return Observable.just(new RequiredShardState(TokenClassesState.class, tokenClassAddress));
	}

	@Override
	public Observable<SpunParticle> mapToParticles(Action action, Observable<Observable<? extends ApplicationState>> store) {
		if (!(action instanceof MintTokensAction)) {
			return Observable.empty();
		}

		MintTokensAction mintTokensAction = (MintTokensAction) action;
		TokenClassReference tokenClass = mintTokensAction.getTokenClassReference();

		return store.firstOrError()
			.flatMap(Observable::firstOrError)
			.map(TokenClassesState.class::cast)
			.map(TokenClassesState::getState)
			.map(m -> m.get(tokenClass))
			.map(TokenState::getGranularity)
			.map(TokenClassReference::unitsToSubunits)
			.map(granularity -> createOwnedTokensParticle(mintTokensAction.getAmount(), granularity, tokenClass))
			.toObservable();
	}

	private SpunParticle createOwnedTokensParticle(UInt256 amount, UInt256 granularity, TokenClassReference tokenClass) {
		Particle minted = new OwnedTokensParticle(
			amount,
			granularity,
			FungibleQuark.FungibleType.MINTED,
			tokenClass.getAddress(),
			System.currentTimeMillis(),
			tokenClass,
			System.currentTimeMillis() / 60000L + 60000);
		return SpunParticle.up(minted);
	}
}
