package com.radixdlt.client.application.translate.atomic;

import com.radixdlt.client.application.translate.Action;
import com.radixdlt.client.application.translate.StatelessActionToParticleGroupsMapper;
import com.radixdlt.client.core.atoms.ParticleGroup;
import io.reactivex.Observable;

public class AtomicToParticleGroupsMapper implements StatelessActionToParticleGroupsMapper {
	@Override
	public Observable<Action> sideEffects(Action action) {
		if (!(action instanceof AtomicAction)) {
			return Observable.empty();
		}

		AtomicAction atomicAction = (AtomicAction) action;

		return Observable.fromIterable(atomicAction.getActions());
	}

	@Override
	public Observable<ParticleGroup> mapToParticleGroups(Action action) {
		return Observable.empty();
	}
}
