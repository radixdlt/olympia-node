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

import com.radixdlt.consensus.bft.PersistentVertexStore;
import com.radixdlt.counters.SystemCounters;
import com.radixdlt.counters.SystemCountersImpl;
import com.radixdlt.environment.EventDispatcher;
import com.radixdlt.mempool.Mempool;
import com.radixdlt.mempool.MempoolAddFailure;
import com.radixdlt.mempool.MempoolAddSuccess;
import com.radixdlt.mempool.MempoolMaxSize;
import com.radixdlt.middleware2.store.RadixEngineAtomicCommitManager;
import org.junit.Test;

import com.google.common.collect.ImmutableSet;
import com.google.inject.AbstractModule;
import com.google.inject.Guice;
import com.google.inject.Key;
import com.google.inject.TypeLiteral;
import com.google.inject.name.Names;
import com.radixdlt.consensus.bft.BFTNode;
import com.radixdlt.consensus.bft.BFTValidator;
import com.radixdlt.consensus.bft.BFTValidatorSet;
import com.radixdlt.consensus.bft.View;
import com.radixdlt.crypto.ECKeyPair;
import com.radixdlt.crypto.Hasher;
import com.radixdlt.engine.AtomChecker;
import com.radixdlt.engine.RadixEngine;
import com.radixdlt.fees.NativeToken;
import com.radixdlt.identifiers.RRI;
import com.radixdlt.middleware2.LedgerAtom;
import com.radixdlt.serialization.Serialization;
import com.radixdlt.store.EngineStore;
import com.radixdlt.utils.TypedMocks;
import com.radixdlt.utils.UInt256;

import static org.assertj.core.api.Assertions.assertThat;
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
			bindConstant().annotatedWith(Names.named("magic")).to(1);
			bindConstant().annotatedWith(MinValidators.class).to(1);
			bindConstant().annotatedWith(MaxValidators.class).to(100);
			bindConstant().annotatedWith(MempoolMaxSize.class).to(10);
			bind(View.class).annotatedWith(EpochCeilingView.class).toInstance(View.of(100));
			bind(Hasher.class).toInstance(mock(Hasher.class));
			bind(BFTValidatorSet.class).toInstance(this.validatorSet);
			bind(RRI.class).annotatedWith(NativeToken.class).toInstance(mock(RRI.class));
			bind(Serialization.class).toInstance(mock(Serialization.class));
			bind(new TypeLiteral<AtomChecker<LedgerAtom>>() { }).toInstance(TypedMocks.rmock(AtomChecker.class));
			EngineStore<LedgerAtom> engineStore = TypedMocks.rmock(EngineStore.class);
			when(engineStore.compute(any(), any(), any(), any()))
				.thenAnswer(inv -> inv.getArgument(1)); // Return initial value
			bind(new TypeLiteral<EngineStore<LedgerAtom>>() { }).toInstance(engineStore);
			bind(RadixEngineAtomicCommitManager.class).toInstance(mock(RadixEngineAtomicCommitManager.class));
			bind(PersistentVertexStore.class).toInstance(mock(PersistentVertexStore.class));

			bind(Mempool.class).toInstance(mock(Mempool.class));
			bind(new TypeLiteral<EventDispatcher<MempoolAddSuccess>>() { })
					.toInstance(TypedMocks.rmock(EventDispatcher.class));
			bind(new TypeLiteral<EventDispatcher<MempoolAddFailure>>() { })
				.toInstance(TypedMocks.rmock(EventDispatcher.class));
			bind(new TypeLiteral<EventDispatcher<InvalidProposedCommand>>() { })
				.toInstance(TypedMocks.rmock(EventDispatcher.class));
			bind(SystemCounters.class).to(SystemCountersImpl.class);
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
			new RadixEngineValidatorComputersModule(),
			new ExternalRadixEngineModule(validatorSet)
		);

		final var radixEngine = injector.getInstance(Key.get(new TypeLiteral<RadixEngine<LedgerAtom>>() { }));

		assertThat(radixEngine).isNotNull();
	}
}