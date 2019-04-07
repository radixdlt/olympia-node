package com.radixdlt.client.application.translate;

import com.radixdlt.client.core.atoms.ParticleGroup;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Maps a high level application action to lower level spun particles used
 * to construct an atom given a context requirement which this interface describes
 * via requiredState().
 */
public interface StatefulActionToParticleGroupsMapper {


	/**
	 * Retrieves the necessary application state to be used in creating new particles
	 * given a high level action. The returned Observable describes the shardable and
	 * the type of state needed to create particles.
	 *
	 * @param action the action to get the required context about
	 * @return required contexts required to create spun particles for the action
	 */
	Set<ShardedAppStateId> requiredState(Action action);

	/**
	 * Returns an observable of actions which will be added to the list
	 * actions to be included in the current transaction.
	 *
	 * @param action the current action
	 * @param store  application state requested as specified by requiredState()
	 * @return additional actions to be included
	 */
	default List<Action> sideEffects(Action action, Map<ShardedAppStateId, ? extends ApplicationState> store) {
		return Collections.emptyList();
	}

	/**
	 * Creates new particle groups to be added to an atom given a high level
	 * action and application state
	 *
	 * @param action the action to map
	 * @param store  application state in the same order as returned from requiredState()
	 * @return Particle groups created given an action
	 */
	List<ParticleGroup> mapToParticleGroups(Action action, Map<ShardedAppStateId, ? extends ApplicationState> store);
}
