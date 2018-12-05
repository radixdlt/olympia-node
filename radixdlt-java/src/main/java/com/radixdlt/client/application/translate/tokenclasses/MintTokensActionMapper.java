package com.radixdlt.client.application.translate.tokenclasses;

import com.radixdlt.client.application.translate.Action;
import com.radixdlt.client.application.translate.ContextFreeActionToParticlesMapper;
import com.radixdlt.client.atommodel.quarks.FungibleQuark;
import com.radixdlt.client.atommodel.tokens.OwnedTokensParticle;
import com.radixdlt.client.core.atoms.particles.SpunParticle;

import io.reactivex.Observable;

public class MintTokensActionMapper implements ContextFreeActionToParticlesMapper {
	@Override
	public Observable<SpunParticle> mapToParticles(Action action) {
		if (!(action instanceof MintTokensAction)) {
			return Observable.empty();
		}

		MintTokensAction mintTokensAction = (MintTokensAction) action;

		OwnedTokensParticle minted = new OwnedTokensParticle(
			mintTokensAction.getAmount(),
			FungibleQuark.FungibleType.MINTED,
			mintTokensAction.getTokenClassReference().getAddress(),
			System.currentTimeMillis(),
			mintTokensAction.getTokenClassReference(),
			System.currentTimeMillis() / 60000L + 60000
		);

		return Observable.just(SpunParticle.up(minted));
	}
}
