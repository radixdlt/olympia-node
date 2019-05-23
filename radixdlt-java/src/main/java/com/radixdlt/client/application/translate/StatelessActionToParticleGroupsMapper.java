package com.radixdlt.client.application.translate;

import com.radixdlt.client.core.atoms.ParticleGroup;
import java.util.List;

/**
 * Maps a high level application action to lower level spun particles used
 * to construct an atom.
 */
public interface StatelessActionToParticleGroupsMapper {
	/**
	 * Creates new spun particles to be added to an atom given a high level
	 * action.
	 *
	 * @param action the action to map
	 * @return observable of spun particles created given an action
	 */
	List<ParticleGroup> mapToParticleGroups(Action action);
}
