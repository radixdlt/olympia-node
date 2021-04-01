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

import com.radixdlt.atom.TxLowLevelBuilder;
import com.radixdlt.atommodel.system.SystemConstraintScrypt;
import com.radixdlt.atomos.CMAtomOS;
import com.radixdlt.constraintmachine.ConstraintMachine;
import com.radixdlt.constraintmachine.Particle;
import com.radixdlt.store.EngineStore;
import com.radixdlt.store.InMemoryEngineStore;
import com.radixdlt.test.utils.TypedMocks;

import java.util.List;
import java.util.function.BiFunction;
import java.util.function.Predicate;
import java.util.function.Supplier;
import org.junit.Before;
import org.junit.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class RadixEngineTest {
	private ConstraintMachine constraintMachine;
	private EngineStore<Void> engineStore;
	private Predicate<Particle> virtualStore;
	private RadixEngine<Void> radixEngine;

	@Before
	public void setup() {
		this.constraintMachine = mock(ConstraintMachine.class);
		this.engineStore = TypedMocks.rmock(EngineStore.class);
		this.radixEngine = new RadixEngine<>(
			constraintMachine,
			engineStore
		);
	}

	@Test
	public void empty_particle_group_should_throw_error() {
		// Arrange
		CMAtomOS cmAtomOS = new CMAtomOS();
		cmAtomOS.load(new SystemConstraintScrypt());
		ConstraintMachine cm = new ConstraintMachine.Builder()
			.setVirtualStoreLayer(cmAtomOS.virtualizedUpParticles())
			.setParticleStaticCheck(cmAtomOS.buildParticleStaticCheck())
			.setParticleTransitionProcedures(cmAtomOS.buildTransitionProcedures())
			.build();
		RadixEngine<Void> engine = new RadixEngine<>(cm, new InMemoryEngineStore<>());

		// Act
		// Assert
		var atom = TxLowLevelBuilder.newBuilder()
			.particleGroup()
			.buildWithoutSignature();
		assertThatThrownBy(() -> engine.execute(List.of(atom)))
			.isInstanceOf(RadixEngineException.class);
	}

	@Test
	public void when_add_state_computer__then_store_is_accessed_for_initial_computation() {
		Object state = mock(Object.class);
		when(engineStore.reduceUpParticles(any(), any(), any())).thenReturn(state);
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