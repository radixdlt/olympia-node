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
import com.google.common.collect.ImmutableSet;
import com.radixdlt.atommodel.system.SystemConstraintScrypt;
import com.radixdlt.atomos.CMAtomOS;
import com.radixdlt.atomos.Result;
import com.radixdlt.constraintmachine.CMError;
import com.radixdlt.constraintmachine.CMInstruction;
import com.radixdlt.constraintmachine.CMMicroInstruction;
import com.radixdlt.constraintmachine.ConstraintMachine;
import com.radixdlt.constraintmachine.Particle;
import com.radixdlt.constraintmachine.Spin;
import com.radixdlt.crypto.HashUtils;
import com.radixdlt.identifiers.EUID;
import com.radixdlt.serialization.SerializerId2;
import com.radixdlt.store.CMStore;
import com.radixdlt.store.EngineStore;
import com.radixdlt.store.InMemoryEngineStore;
import com.radixdlt.test.utils.TypedMocks;

import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.BiFunction;
import java.util.function.Supplier;
import java.util.function.UnaryOperator;
import org.junit.Before;
import org.junit.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class RadixEngineTest {

	@SerializerId2("test.indexed_particle_2")
	private final class IndexedParticle extends Particle {
		@Override
		public Set<EUID> getDestinations() {
			return ImmutableSet.of();
		}

		@Override
		public String toString() {
			return String.format("%s", getClass().getSimpleName());
		}

		@Override
		public int hashCode() {
			return Objects.hash(this.getDestinations());
		}

		@Override
		public boolean equals(Object o) {
			if (!(o instanceof IndexedParticle)) {
				return false;
			}
			final var that = (IndexedParticle) o;
			return Objects.equals(this.getDestinations(), that.getDestinations());
		}
	}

	private ConstraintMachine constraintMachine;
	private EngineStore<RadixEngineAtom> engineStore;
	private UnaryOperator<CMStore> virtualStore;
	private RadixEngine<RadixEngineAtom> radixEngine;

	@Before
	public void setup() {
		this.constraintMachine = mock(ConstraintMachine.class);
		this.engineStore = TypedMocks.rmock(EngineStore.class);
		this.virtualStore = TypedMocks.rmock(UnaryOperator.class);
		this.radixEngine = new RadixEngine<>(
			constraintMachine,
			virtualStore,
			engineStore
		);
	}

	@Test
	public void empty_particle_group_should_throw_error() {
		// Arrange
		CMAtomOS cmAtomOS = new CMAtomOS();
		cmAtomOS.load(new SystemConstraintScrypt());
		ConstraintMachine cm = new ConstraintMachine.Builder()
			.setParticleStaticCheck(cmAtomOS.buildParticleStaticCheck())
			.setParticleTransitionProcedures(cmAtomOS.buildTransitionProcedures())
			.build();
		RadixEngine<RadixEngineAtom> engine = new RadixEngine<>(
			cm,
			cmAtomOS.buildVirtualLayer(),
			new InMemoryEngineStore<>()
		);

		// Act
		// Assert
		CMInstruction cmInstruction = new CMInstruction(
			ImmutableList.of(CMMicroInstruction.particleGroup()),
			ImmutableMap.of()
		);
		assertThatThrownBy(() -> engine.checkAndStore(new BaseAtom(cmInstruction, HashUtils.zero256())))
			.isInstanceOf(RadixEngineException.class);
	}

	@Test
	public void when_add_state_computer__then_store_is_accessed_for_initial_computation() {
		Object state = mock(Object.class);
		when(engineStore.compute(any(), any(), any())).thenReturn(state);
		radixEngine.addStateReducer(
			new StateReducer<>() {
				public Class<Object> stateClass() {
					return Object.class;
				}

				@Override
				public Class<Particle> particleClass() {
					return Particle.class;
				}

				@Override
				public Supplier<Object> initial() {
					return () -> mock(Object.class);
				}

				@Override
				public BiFunction<Object, Particle, Object> outputReducer() {
					return (o, p) -> o;
				}

				@Override
				public BiFunction<Object, Particle, Object> inputReducer() {
					return (o, p) -> o;
				}
			},
			true
		);
		assertThat(radixEngine.getComputedState(Object.class)).isEqualTo(state);
	}
}