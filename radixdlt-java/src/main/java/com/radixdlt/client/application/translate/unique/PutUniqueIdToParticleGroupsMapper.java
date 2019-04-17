package com.radixdlt.client.application.translate.unique;

import com.radixdlt.client.application.translate.Action;
import com.radixdlt.client.application.translate.StatelessActionToParticleGroupsMapper;
import com.radixdlt.client.atommodel.rri.RRIParticle;
import com.radixdlt.client.atommodel.unique.UniqueParticle;
import com.radixdlt.client.core.atoms.ParticleGroup;
import com.radixdlt.client.core.atoms.particles.SpunParticle;

import java.util.Collections;
import java.util.List;

public class PutUniqueIdToParticleGroupsMapper implements StatelessActionToParticleGroupsMapper {
	@Override
	public List<ParticleGroup> mapToParticleGroups(Action action) {
		if (!(action instanceof PutUniqueIdAction)) {
			return Collections.emptyList();
		}

		PutUniqueIdAction uniqueIdAction = (PutUniqueIdAction) action;
		UniqueParticle uniqueParticle = new UniqueParticle(uniqueIdAction.getAddress(), uniqueIdAction.getUnique());
		RRIParticle rriParticle = new RRIParticle(uniqueParticle.getRRI());
		return Collections.singletonList(
			ParticleGroup.of(
				SpunParticle.down(rriParticle),
				SpunParticle.up(uniqueParticle)
			)
		);
	}
}
