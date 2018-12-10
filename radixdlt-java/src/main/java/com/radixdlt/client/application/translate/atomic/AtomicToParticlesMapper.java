package com.radixdlt.client.application.translate.atomic;

import com.radixdlt.client.application.translate.Action;
import com.radixdlt.client.application.translate.StatelessActionToParticlesMapper;
import com.radixdlt.client.core.atoms.particles.SpunParticle;
import io.reactivex.Observable;

public class AtomicToParticlesMapper implements StatelessActionToParticlesMapper {
	@Override
	public Observable<Action> sideEffects(Action action) {
		if (!(action instanceof AtomicAction)) {
			return Observable.empty();
		}

		AtomicAction atomicAction = (AtomicAction) action;

		return Observable.fromIterable(atomicAction.getActions());
	}

	@Override
	public Observable<SpunParticle> mapToParticles(Action action) {
		return Observable.empty();
	}
}
