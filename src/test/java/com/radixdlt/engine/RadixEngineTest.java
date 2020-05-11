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

package com.radixdlt.engine;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.radixdlt.constraintmachine.CMInstruction;
import com.radixdlt.constraintmachine.CMMicroInstruction;
import com.radixdlt.constraintmachine.ConstraintMachine;
import com.radixdlt.constraintmachine.DataPointer;
import com.radixdlt.constraintmachine.Particle;
import com.radixdlt.constraintmachine.Spin;
import com.radixdlt.crypto.Hash;
import com.radixdlt.serialization.SerializerId2;
import com.radixdlt.store.CMStores;
import com.radixdlt.store.EngineStore;
import org.assertj.core.api.Assertions;
import org.junit.Test;

import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class RadixEngineTest {

	@SerializerId2("test.indexed_particle_2")
	private class IndexedParticle extends Particle {
		@Override
		public String toString() {
			return String.format("%s", getClass().getSimpleName());
		}
	}

	@Test
	public void when_validating_an_atom_with_particle_which_conflicts_with_virtual_state__an_internal_spin_conflict_is_returned() {
		ConstraintMachine constraintMachine = mock(ConstraintMachine.class);
		when(constraintMachine.validate(any())).thenReturn(Optional.empty());
		EngineStore engineStore = mock(EngineStore.class);
		when(engineStore.supports(any())).thenReturn(true);
		RadixEngine<RadixEngineAtom> engine = new RadixEngine<>(
			constraintMachine,
			state -> CMStores.virtualizeDefault(state, p -> true, Spin.DOWN),
			engineStore
		);

		RadixEngineAtom atom = mock(RadixEngineAtom.class);
		ImmutableList<CMMicroInstruction> insts = ImmutableList.of(CMMicroInstruction.checkSpin(mock(IndexedParticle.class), Spin.UP));
		when(atom.getCMInstruction()).thenReturn(new CMInstruction(insts, Hash.random(), ImmutableMap.of()));
		Assertions.assertThatThrownBy(() -> engine.store(atom))
			.isInstanceOf(RadixEngineException.class)
			.matches(e -> ((RadixEngineException) e).getDataPointer().equals(DataPointer.ofParticle(0, 0)), "points to 1st particle")
			.extracting(e -> ((RadixEngineException) e).getErrorCode())
			.isEqualTo(RadixEngineErrorCode.VIRTUAL_STATE_CONFLICT);
	}
}