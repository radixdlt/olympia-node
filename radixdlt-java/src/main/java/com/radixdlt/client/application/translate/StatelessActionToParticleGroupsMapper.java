package com.radixdlt.client.application.translate;

import com.radixdlt.client.core.atoms.ParticleGroup;
import io.reactivex.Observable;

/**
 * Maps a high level application action to lower level spun particles used
 * to construct an atom.
 */
public interface StatelessActionToParticleGroupsMapper {

	/**
	 * Returns an observable of actions which will be added to the list
	 * actions to be included in the current transaction.
	 *
	 * @param action the current action
	 * @return additional actions to be included
	 */
	default Observable<Action> sideEffects(Action action) {
		return Observable.empty();
	}

	/**
	 * Creates new spun particles to be added to an atom given a high level
	 * action.
	 *
	 * @param action the action to map
	 * @return observable of spun particles created given an action
	 */
	Observable<ParticleGroup> mapToParticleGroups(Action action);
}
