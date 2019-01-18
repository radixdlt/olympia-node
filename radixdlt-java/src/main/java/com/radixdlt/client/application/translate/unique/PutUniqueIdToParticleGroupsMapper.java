package com.radixdlt.client.application.translate.unique;

import com.radixdlt.client.application.translate.Action;
import com.radixdlt.client.application.translate.StatelessActionToParticleGroupsMapper;
import com.radixdlt.client.atommodel.unique.UniqueParticle;
import com.radixdlt.client.core.atoms.ParticleGroup;
import com.radixdlt.client.core.atoms.particles.Spin;
import com.radixdlt.client.core.atoms.particles.SpunParticle;
import io.reactivex.Observable;

public class PutUniqueIdToParticleGroupsMapper implements StatelessActionToParticleGroupsMapper {
	@Override
	public Observable<Action> sideEffects(Action action) {
		return Observable.empty();
	}

	@Override
	public Observable<ParticleGroup> mapToParticleGroups(Action action) {
		if (!(action instanceof PutUniqueIdAction)) {
			return Observable.empty();
		}

		PutUniqueIdAction uniqueIdAction = (PutUniqueIdAction) action;
		UniqueParticle uniqueParticle = new UniqueParticle(uniqueIdAction.getAddress(), uniqueIdAction.getUnique());
		return Observable.just(ParticleGroup.of(SpunParticle.up(uniqueParticle)));
	}
}
