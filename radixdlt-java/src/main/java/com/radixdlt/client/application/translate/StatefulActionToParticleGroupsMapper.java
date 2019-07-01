package com.radixdlt.client.application.translate;

import com.radixdlt.client.core.atoms.ParticleGroup;
import com.radixdlt.client.core.atoms.particles.Particle;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

/**
 * Maps a high level application action to lower level spun particles used
 * to construct an atom given a context requirement which this interface describes
 * via requiredState().
 */
public interface StatefulActionToParticleGroupsMapper<T extends Action> {
	/**
	 * Retrieves the necessary application state to be used in creating new particles
	 * given a high level action. The returned Observable describes the shardable and
	 * the type of state needed to create particles.
	 *
	 * @param action the action to get the required context about
	 * @return required contexts required to create spun particles for the action
	 */
	Set<ShardedParticleStateId> requiredState(T action);

	/**
	 * Creates new particle groups to be added to an atom given a high level
	 * action and application state
	 *
	 * @param action the action to map
	 * @param store particles as requested by requiredState()
	 * @return Particle groups created given an action
	 */
	List<ParticleGroup> mapToParticleGroups(T action, Stream<Particle> store);
}
