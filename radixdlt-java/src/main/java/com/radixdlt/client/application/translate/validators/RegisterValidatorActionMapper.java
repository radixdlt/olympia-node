package com.radixdlt.client.application.translate.validators;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.radixdlt.client.application.translate.ShardedParticleStateId;
import com.radixdlt.client.application.translate.StageActionException;
import com.radixdlt.client.application.translate.StatefulActionToParticleGroupsMapper;
import com.radixdlt.client.atommodel.validators.RegisteredValidatorParticle;
import com.radixdlt.client.atommodel.validators.UnregisteredValidatorParticle;
import com.radixdlt.client.core.atoms.ParticleGroup;
import com.radixdlt.client.core.atoms.particles.Particle;
import com.radixdlt.client.core.atoms.particles.SpunParticle;

import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

/**
 * Action mapper for {@link RegisterValidatorAction}s, implemented using {@link ValidatorRegistrationState}.
 */
public class RegisterValidatorActionMapper implements StatefulActionToParticleGroupsMapper<RegisterValidatorAction> {
	@Override
	public Set<ShardedParticleStateId> requiredState(RegisterValidatorAction action) {
		return ImmutableSet.of(
			ShardedParticleStateId.of(RegisteredValidatorParticle.class, action.getValidator()),
			ShardedParticleStateId.of(UnregisteredValidatorParticle.class, action.getValidator())
		);
	}

	@Override
	public List<ParticleGroup> mapToParticleGroups(RegisterValidatorAction action, Stream<Particle> store) throws StageActionException {
		ValidatorRegistrationState currentState = ValidatorRegistrationState.from(store, action.getValidator());
		return ImmutableList.of(ParticleGroup.of(
			SpunParticle.down(currentState.asParticle()),
			SpunParticle.up(currentState.register())
		));
	}

}
