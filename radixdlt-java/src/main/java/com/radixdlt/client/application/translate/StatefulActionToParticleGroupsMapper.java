package com.radixdlt.client.application.translate;

import com.radixdlt.client.core.atoms.ParticleGroup;
import io.reactivex.Observable;

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
	 * @return observable of required contexts required to create spun particles for the action
	 */
	Observable<ShardedAppStateId> requiredState(Action action);

	/**
	 * Returns an observable of actions which will be added to the list
	 * actions to be included in the current transaction.
	 *
	 * @param action the current action
	 * @param store  application state requested as specified by requiredState()
	 * @return additional actions to be included
	 */
	default Observable<Action> sideEffects(Action action, Observable<Observable<? extends ApplicationState>> store) {
		return Observable.empty();
	}

	/**
	 * Creates new spun particles to be added to an atom given a high level
	 * action and application state
	 *
	 * @param action the action to map
	 * @param store  application state in the same order as returned from requiredState()
	 * @return observable of spun particles created given an action
	 */
	Observable<ParticleGroup> mapToParticleGroups(Action action, Observable<Observable<? extends ApplicationState>> store);
}
