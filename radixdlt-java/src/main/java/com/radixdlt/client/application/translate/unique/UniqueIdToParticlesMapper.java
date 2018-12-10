package com.radixdlt.client.application.translate.unique;

import com.radixdlt.client.application.translate.Action;
import com.radixdlt.client.application.translate.StatelessActionToParticlesMapper;
import com.radixdlt.client.atommodel.unique.UniqueParticle;
import com.radixdlt.client.core.atoms.particles.SpunParticle;
import io.reactivex.Observable;

public class UniqueIdToParticlesMapper implements StatelessActionToParticlesMapper {
	@Override
	public Observable<Action> sideEffects(Action action) {
		return Observable.empty();
	}

	@Override
	public Observable<SpunParticle> mapToParticles(Action action) {
		if (!(action instanceof UniqueIdAction)) {
			return Observable.empty();
		}

		UniqueIdAction uniqueIdAction = (UniqueIdAction) action;
		UniqueParticle uniqueParticle = new UniqueParticle(uniqueIdAction.getAddress(), uniqueIdAction.getUnique());
		return Observable.just(SpunParticle.up(uniqueParticle));
	}
}
