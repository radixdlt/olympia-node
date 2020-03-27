/*
 * (C) Copyright 2020 Radix DLT Ltd
 *
 * Radix DLT Ltd licenses this file to you under the Apache License,
 * Version 2.0 (the "License"); you may not use this file except in
 * compliance with the License.  You may obtain a copy of the
 * License at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND,
 * either express or implied.  See the License for the specific
 * language governing permissions and limitations under the License.
 */

package com.radixdlt.examples.tictactoe;

import com.radixdlt.atommodel.Atom;
import com.radixdlt.constraintmachine.Particle;
import com.radixdlt.constraintmachine.Spin;
import com.radixdlt.crypto.CryptoException;
import com.radixdlt.crypto.ECKeyPair;
import com.radixdlt.middleware.ParticleGroup;
import com.radixdlt.middleware.SpunParticle;
import org.junit.Test;

import static org.hamcrest.CoreMatchers.instanceOf;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;

public class InMemoryEngineStoreTest {
	@Test
	public void verify_that_we_can_retrieve_spin_of_stored_particle() throws CryptoException {
		InMemoryEngineStore store = new InMemoryEngineStore();
		assertNotNull(store);

		Atom atom = initialBoardAtom();
		store.storeAtom(atom);
		Particle particle = firstParticleOfAtom(atom);
		assertNotEquals(Spin.NEUTRAL, particle);
		assertThat(particle, instanceOf(TicTacToeBaseParticle.class));
	}

	private static Atom initialBoardAtom() throws CryptoException {
		// Our two tic toe players
		ECKeyPair xPlayer = ECKeyPair.generateNew();
		ECKeyPair oPlayer = ECKeyPair.generateNew();

		// Build out real particle states for each of the boards
		TicTacToeConstraintScrypt.XToMoveParticle initialBoard = TicTacToeRunner.buildInitialBoard(xPlayer, oPlayer);
		Atom atom = TicTacToeRunner.buildAtom(null, initialBoard, xPlayer);
		return atom;
	}

	private Particle firstParticleOfAtom(Atom atom) {
		ParticleGroup particleGroup = atom.getParticleGroups().get(0);
		assertNotNull(particleGroup);
		assertFalse(particleGroup.isEmpty());
		SpunParticle spunParticle = particleGroup.getParticles().get(0);
		assertNotNull(spunParticle);
		Particle particle = spunParticle.getParticle();
		assertNotNull(particle);
		return particle;
	}
}
