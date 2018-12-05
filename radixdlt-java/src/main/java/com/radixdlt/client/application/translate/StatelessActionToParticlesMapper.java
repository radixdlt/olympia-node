package com.radixdlt.client.application.translate;

import com.radixdlt.client.core.atoms.particles.SpunParticle;
import io.reactivex.Observable;

/**
 * Maps a high level application action to lower level spun particles used
 * to construct an atom.
 */
public interface StatelessActionToParticlesMapper {

	/**
	 * Creates new spun particles to be added to an atom given a high level
	 * action.
	 *
	 * @param action the action to map
	 * @return observable of spun particles created given an action
	 */
	Observable<SpunParticle> mapToParticles(Action action);
}
