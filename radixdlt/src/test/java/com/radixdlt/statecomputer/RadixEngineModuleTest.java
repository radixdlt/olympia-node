/*
 * (C) Copyright 2020 Radix DLT Ltd
 *
 * Radix DLT Ltd licenses this file to you under the Apache License,
 * Version 2.0 (the "License"); you may not use this file except in
 * compliance with the License.  You may obtain a copy of the
 * License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND,
 * either express or implied.  See the License for the specific
 * language governing permissions and limitations under the License.
 */

package com.radixdlt.statecomputer;

import java.util.stream.IntStream;

import org.junit.Test;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.inject.AbstractModule;
import com.google.inject.Guice;
import com.google.inject.Key;
import com.google.inject.TypeLiteral;
import com.google.inject.name.Names;
import com.radixdlt.RadixEngineModule;
import com.radixdlt.consensus.bft.BFTNode;
import com.radixdlt.consensus.bft.BFTValidator;
import com.radixdlt.consensus.bft.BFTValidatorSet;
import com.radixdlt.consensus.bft.View;
import com.radixdlt.crypto.ECKeyPair;
import com.radixdlt.crypto.Hasher;
import com.radixdlt.engine.AtomChecker;
import com.radixdlt.engine.RadixEngine;
import com.radixdlt.middleware2.LedgerAtom;
import com.radixdlt.serialization.Serialization;
import com.radixdlt.store.EngineStore;
import com.radixdlt.utils.TypedMocks;
import com.radixdlt.utils.UInt256;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Tests for RadixEngineModule.
 * <p>
 * Unfortunately needs to live in {@code com.radixdlt.statecomputer} unless
 * we want to change the visibility of
 * {@code RadixEngineValidatorSetBuilder.build()}.
 */
public class RadixEngineModuleTest {
	private static class ExternalRadixEngineModule extends AbstractModule {
		private final BFTValidatorSet validatorSet;

		private ExternalRadixEngineModule(BFTValidatorSet validatorSet) {
			this.validatorSet = validatorSet;
		}

		@Override
		protected void configure() {
			bind(Integer.class).annotatedWith(Names.named("magic")).toInstance(1);
			bind(Integer.class).annotatedWith(MinValidators.class).toInstance(1);
			bind(View.class).annotatedWith(EpochCeilingView.class).toInstance(View.of(100));
			bind(Hasher.class).toInstance(mock(Hasher.class));
			bind(BFTValidatorSet.class).toInstance(this.validatorSet);
			bind(Serialization.class).toInstance(mock(Serialization.class));
			bind(new TypeLiteral<AtomChecker<LedgerAtom>>() { }).toInstance(TypedMocks.rmock(AtomChecker.class));
			EngineStore<LedgerAtom> engineStore = TypedMocks.rmock(EngineStore.class);
			when(engineStore.compute(any(), any(), any(), any()))
				.thenAnswer(inv -> inv.getArgument(1)); // Return initial value
			bind(new TypeLiteral<EngineStore<LedgerAtom>>() { }).toInstance(engineStore);
		}
	}

	@Test
	public void when_configured_with_correct_interfaces__then_radix_engine_should_be_created() {
		final var validators = ImmutableSet.of(
			BFTValidator.from(BFTNode.create(ECKeyPair.generateNew().getPublicKey()), UInt256.ONE),
			BFTValidator.from(BFTNode.create(ECKeyPair.generateNew().getPublicKey()), UInt256.ONE)
		);
		final var validatorSet = mock(BFTValidatorSet.class);
		when(validatorSet.getValidators()).thenReturn(validators);
		final var injector = Guice.createInjector(
			new RadixEngineModule(),
			new ExternalRadixEngineModule(validatorSet)
		);

		final var radixEngine = injector.getInstance(Key.get(new TypeLiteral<RadixEngine<LedgerAtom>>() { }));

		assertThat(radixEngine).isNotNull();
	}

	@Test
	public void when_engine_created__validator_set_order_not_changed() {
		final var validators = IntStream.range(0, 100)
			.mapToObj(n -> ECKeyPair.generateNew())
			.map(ECKeyPair::getPublicKey)
			.map(BFTNode::create)
			.map(node -> BFTValidator.from(node, UInt256.ONE))
			.collect(ImmutableList.toImmutableList());
		final var validatorSet = BFTValidatorSet.from(validators);
		final var injector = Guice.createInjector(
			new RadixEngineModule(),
			new ExternalRadixEngineModule(validatorSet)
		);
		final var radixEngine = injector.getInstance(Key.get(new TypeLiteral<RadixEngine<LedgerAtom>>() { }));
		assertThat(radixEngine).isNotNull(); // Precondition for the rest working correctly

		final var newValidatorSet = radixEngine.getComputedState(RadixEngineValidatorSetBuilder.class).build();

		checkIterableOrder(validators, newValidatorSet.getValidators());
	}

	private <T> void checkIterableOrder(Iterable<T> iterable1, Iterable<T> iterable2) {
		final var i1 = iterable1.iterator();
		final var i2 = iterable2.iterator();

		while (i1.hasNext() && i2.hasNext()) {
			final var o1 = i1.next();
			final var o2 = i2.next();
			assertEquals("Objects not the same", o1, o2);
		}
		assertFalse("Iterable 1 larger than iterable 2", i1.hasNext());
		assertFalse("Iterable 2 larger than iterable 1", i2.hasNext());
	}
}