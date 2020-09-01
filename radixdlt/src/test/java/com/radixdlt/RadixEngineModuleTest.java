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

package com.radixdlt;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.google.common.collect.ImmutableSet;
import com.google.inject.AbstractModule;
import com.google.inject.Guice;
import com.google.inject.Injector;
import com.google.inject.TypeLiteral;
import com.google.inject.name.Names;
import com.radixdlt.consensus.bft.BFTNode;
import com.radixdlt.consensus.bft.BFTValidator;
import com.radixdlt.consensus.bft.BFTValidatorSet;
import com.radixdlt.consensus.bft.View;
import com.radixdlt.crypto.ECKeyPair;
import com.radixdlt.middleware2.LedgerAtom;
import com.radixdlt.serialization.Serialization;
import com.radixdlt.statecomputer.CommittedCommandsReader;
import com.radixdlt.statecomputer.RadixEngineStateComputer;
import com.radixdlt.statecomputer.RadixEngineStateComputer.CommittedAtomSender;
import com.radixdlt.store.EngineStore;
import com.radixdlt.utils.TypedMocks;

import org.junit.Test;

public class RadixEngineModuleTest {
	private static class ExternalRadixEngineModule extends AbstractModule {
		@Override
		protected void configure() {
			bind(Serialization.class).toInstance(mock(Serialization.class));
			bind(CommittedAtomSender.class).toInstance(mock(CommittedAtomSender.class));
			bind(ECKeyPair.class).annotatedWith(Names.named("self")).toInstance(ECKeyPair.generateNew());
			bind(new TypeLiteral<EngineStore<LedgerAtom>>() { }).toInstance(TypedMocks.rmock(EngineStore.class));
			bind(CommittedCommandsReader.class).toInstance(mock(CommittedCommandsReader.class));
			bind(Integer.class).annotatedWith(Names.named("magic")).toInstance(1);
			BFTValidatorSet validatorSet = mock(BFTValidatorSet.class);
			BFTValidator validator1 = mock(BFTValidator.class);
			when(validator1.getNode()).thenReturn(BFTNode.create(ECKeyPair.generateNew().getPublicKey()));
			BFTValidator validator2 = mock(BFTValidator.class);
			when(validator2.getNode()).thenReturn(BFTNode.create(ECKeyPair.generateNew().getPublicKey()));
			when(validatorSet.getValidators()).thenReturn(ImmutableSet.of(validator1, validator2));
			bind(BFTValidatorSet.class).toInstance(validatorSet);
		}
	}

	@Test
	public void when_configured_with_correct_interfaces__then_state_computer_should_be_created() {
		Injector injector = Guice.createInjector(
			new RadixEngineModule(View.of(1), true),
			new ExternalRadixEngineModule()
		);

		RadixEngineStateComputer computer = injector.getInstance(RadixEngineStateComputer.class);
		assertThat(computer).isNotNull();
	}
}