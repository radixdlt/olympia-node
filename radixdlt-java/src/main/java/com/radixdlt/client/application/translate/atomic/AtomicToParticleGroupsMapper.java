package com.radixdlt.client.application.translate.atomic;

import com.radixdlt.client.application.translate.Action;
import com.radixdlt.client.application.translate.StatelessActionToParticleGroupsMapper;
import com.radixdlt.client.core.atoms.ParticleGroup;
import java.util.Collections;
import java.util.List;

public class AtomicToParticleGroupsMapper implements StatelessActionToParticleGroupsMapper {
	@Override
	public List<Action> sideEffects(Action action) {
		if (!(action instanceof AtomicAction)) {
			return Collections.emptyList();
		}

		AtomicAction atomicAction = (AtomicAction) action;

		return atomicAction.getActions();
	}

	@Override
	public List<ParticleGroup> mapToParticleGroups(Action action) {
		return Collections.emptyList();
	}
}
