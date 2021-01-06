/*
 * (C) Copyright 2020 Radix DLT Ltd
 *
 * Permission is hereby granted, free of charge, to any person obtaining a
 * copy of this software and associated documentation files (the “Software”),
 * to deal in the Software without restriction, including without limitation
 * the rights to use, copy, modify, merge, publish, distribute, sublicense,
 * and/or sell copies of the Software, and to permit persons to whom the
 * Software is furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be
 * included in all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED “AS IS”, WITHOUT WARRANTY OF ANY KIND, EXPRESS
 * OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL
 * THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING
 * FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER
 * DEALINGS IN THE SOFTWARE.
 */

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
	List<ParticleGroup> mapToParticleGroups(T action, Stream<Particle> store) throws StageActionException;
}
