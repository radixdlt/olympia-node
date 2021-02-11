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
import com.radixdlt.constraintmachine.DataPointer;
import com.radixdlt.constraintmachine.Particle;
import com.radixdlt.constraintmachine.Spin;
import com.radixdlt.crypto.HashUtils;
import com.radixdlt.identifiers.EUID;
import com.radixdlt.serialization.SerializerId2;
import com.radixdlt.store.CMStore;
import com.radixdlt.store.CMStores;
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
import static org.mockito.Mockito.doAnswer;
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
		when(engineStore.compute(any(), any(), any(), any())).thenReturn(state);
		radixEngine.addStateReducer(
			Object.class,
			new StateReducer<>() {
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
			}
		);
		assertThat(radixEngine.getComputedState(Object.class)).isEqualTo(state);
	}

	@Test
	public void when_add_state_computer_and_atom_with_particles_stored__then_state_is_updated() throws RadixEngineException {
		this.virtualStore = store -> p -> Spin.NEUTRAL;
		this.radixEngine = new RadixEngine<>(
			constraintMachine,
			virtualStore,
			engineStore
		);
		Object initialState = mock(Object.class);

		Object state1 = mock(Object.class);
		Object state2 = mock(Object.class);
		when(engineStore.compute(any(), any(), any(), any())).thenReturn(initialState);
		radixEngine.addStateReducer(
			Object.class,
			new StateReducer<>() {
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
					return (o, p) -> state1;
				}

				@Override
				public BiFunction<Object, Particle, Object> inputReducer() {
					return (o, p) -> state2;
				}
			}
		);
		assertThat(radixEngine.getComputedState(Object.class)).isEqualTo(initialState);
		RadixEngineAtom radixEngineAtom = mock(RadixEngineAtom.class);
		CMInstruction cmInstruction = mock(CMInstruction.class);
		Particle particle = mock(Particle.class);
		when(cmInstruction.getMicroInstructions()).thenReturn(ImmutableList.of(
			CMMicroInstruction.checkSpinAndPush(particle, Spin.NEUTRAL),
			CMMicroInstruction.checkSpinAndPush(particle, Spin.UP)
		));
		when(engineStore.getSpin(eq(particle))).thenReturn(Spin.NEUTRAL);
		when(radixEngineAtom.getCMInstruction()).thenReturn(cmInstruction);
		when(constraintMachine.validate(any(), any(), any())).thenReturn(Optional.empty());
		radixEngine.checkAndStore(radixEngineAtom);

		assertThat(radixEngine.getComputedState(Object.class)).isEqualTo(state2);
	}

	@Test
	public void when_static_checking_an_atom_with_cm_error__then_an_exception_is_thrown() {
		when(this.constraintMachine.validate(any(), any(), any())).thenReturn(Optional.of(mock(CMError.class)));
		assertThatThrownBy(() -> radixEngine.staticCheck(mock(RadixEngineAtom.class)))
			.hasFieldOrPropertyWithValue("errorCode", RadixEngineErrorCode.CM_ERROR)
			.isInstanceOf(RadixEngineException.class);
	}

	@Test
	public void when_static_checking_an_atom_with_a_atom_checker_error__then_an_exception_is_thrown() {
		when(this.constraintMachine.validate(any(), any(), any())).thenReturn(Optional.empty());
		AtomChecker<RadixEngineAtom> atomChecker = TypedMocks.rmock(AtomChecker.class);
		RadixEngineAtom atom = mock(RadixEngineAtom.class);
		when(atomChecker.check(atom)).thenReturn(Result.error("error"));
		this.radixEngine = new RadixEngine<>(
			constraintMachine,
			virtualStore,
			engineStore,
			atomChecker
		);

		assertThatThrownBy(() -> radixEngine.staticCheck(atom))
			.hasFieldOrPropertyWithValue("errorCode", RadixEngineErrorCode.HOOK_ERROR)
			.isInstanceOf(RadixEngineException.class);
	}

	@Test
	public void when_validating_an_atom_with_particle_which_conflicts_with_virtual_state__an_internal_spin_conflict_is_returned() {
		when(this.constraintMachine.validate(any(), any(), any())).thenReturn(Optional.empty());
		doAnswer(invocation -> {
			CMStore cmStore = invocation.getArgument(0);
			return CMStores.virtualizeDefault(cmStore, p -> true, Spin.DOWN);
		}).when(virtualStore).apply(any());

		this.radixEngine = new RadixEngine<>(
			constraintMachine,
			virtualStore,
			engineStore
		);

		RadixEngineAtom atom = mock(RadixEngineAtom.class);
		ImmutableList<CMMicroInstruction> insts = ImmutableList.of(CMMicroInstruction.checkSpinAndPush(mock(IndexedParticle.class), Spin.UP));
		when(atom.getCMInstruction()).thenReturn(new CMInstruction(insts, ImmutableMap.of()));
		assertThatThrownBy(() -> radixEngine.checkAndStore(atom))
			.isInstanceOf(RadixEngineException.class)
			.matches(e -> ((RadixEngineException) e).getDataPointer().equals(DataPointer.ofParticle(0, 0)), "points to 1st particle")
			.extracting(e -> ((RadixEngineException) e).getErrorCode())
			.isEqualTo(RadixEngineErrorCode.VIRTUAL_STATE_CONFLICT);
	}
}