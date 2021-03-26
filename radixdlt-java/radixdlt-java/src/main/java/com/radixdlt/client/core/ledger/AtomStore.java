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

package com.radixdlt.client.core.ledger;

import com.radixdlt.atom.Atom;
import com.radixdlt.atom.TxLowLevelBuilder;
import com.radixdlt.identifiers.RadixAddress;

import com.radixdlt.atom.ParticleGroup;
import com.radixdlt.constraintmachine.Particle;
import io.reactivex.Observable;
import java.util.stream.Stream;
import javax.annotation.Nullable;

/**
 * The interface in which a client retrieves the state of the ledger.
 * Particle conflict handling along with Atom DELETEs and STOREs all
 * occur at this layer.
 */
public interface AtomStore {

	/**
	 * Temporary interface for propagating when the current store
	 * is synced with some node on a given address.
	 * TODO: This is probably the wrong place for this and will probably
	 * TODO: move this into a different layer but good enough for now
	 *
	 * @param address The address to check for sync
	 * @return a never ending observable which emits when local store is
	 * synced with some origin
	 */
	Observable<Long> onSync(RadixAddress address);

	/**
	 * Retrieve the current set of validated atoms at a given shardable
	 *
	 * @param address the address to get the atoms under
	 * @return a stream of all stored atoms of the current local view
	 */
	Stream<Atom> getStoredAtoms(RadixAddress address);

	/**
	 * Retrieve a never ending observable of atom observations (STORED and DELETED)
	 * which are then processed by the local store.
	 *
	 * @param address the address to get the updates from
	 * @return a never ending observable of updates
	 */
	Observable<AtomObservation> getAtomObservations(RadixAddress address);

	/**
	 * Retrieve the current set of validated up particles at a given shardable.
	 * If uuid is provided also retrieves and staged particles under that uuid.
	 *
	 * @param address the address to get the particles under
	 * @param uuid uuid of staged particles to include
	 * @return a stream of all up particles of the current local view
	 */
	Stream<Particle> getUpParticles(RadixAddress address, @Nullable String uuid);

	/**
	 * Adds the particle group to the staging area for the given uuid
	 *
	 * @param uuid the uuid to add the particle group to
	 * @param particleGroup the particle group to add to staging area
	 */
	void stageParticleGroup(String uuid, ParticleGroup particleGroup);

	/**
	 * Retrieves all staged particle groups and clears the staging area
	 * for the given uuid.
	 * TODO: Cleanup interface
	 *
	 * @param uuid uuid to retrieve the staged particle groups for
	 * @return all staged particle groups in the order they were staged
	 */
	TxLowLevelBuilder getStagedAndClear(String uuid);

	/**
	 * Retrieves all staged particle groups without clearing the staging area
	 * for the given uuid.
	 * TODO: Cleanup interface
	 *
	 * @param uuid uuid to retrieve the staged particle groups for
	 * @return all staged particle groups in the order they were staged
	 */
	TxLowLevelBuilder getStaged(String uuid);
}
