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
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.google.common.collect.ImmutableList;
import com.google.inject.AbstractModule;
import com.google.inject.Guice;
import com.google.inject.Injector;
import com.google.inject.Key;
import com.google.inject.TypeLiteral;
import com.google.inject.name.Names;
import com.radixdlt.atommodel.Atom;
import com.radixdlt.crypto.ECKeyPair;
import com.radixdlt.network.addressbook.AddressBook;
import com.radixdlt.properties.RuntimeProperties;
import com.radixdlt.serialization.Serialization;
import com.radixdlt.statecomputer.CommittedAtom;
import com.radixdlt.statecomputer.RadixEngineStateComputer.CommittedAtomSender;
import com.radixdlt.store.EngineStore;
import com.radixdlt.store.LedgerEntry;
import com.radixdlt.store.LedgerEntryStore;
import com.radixdlt.universe.Universe;
import org.junit.Test;

public class RadixEngineStoreModuleTest {
	private static class ExternalRadixEngineStoreModule extends AbstractModule {
		@Override
		protected void configure() {
			bind(Serialization.class).toInstance(mock(Serialization.class));
			Universe universe = mock(Universe.class);
			when(universe.getGenesis()).thenReturn(ImmutableList.of(new Atom()));
			bind(Universe.class).toInstance(universe);
			bind(RuntimeProperties.class).toInstance(mock(RuntimeProperties.class));
			bind(AddressBook.class).toInstance(mock(AddressBook.class));
			bind(CommittedAtomSender.class).toInstance(mock(CommittedAtomSender.class));
			LedgerEntryStore ledgerEntryStore = mock(LedgerEntryStore.class);
			when(ledgerEntryStore.getNextCommittedLedgerEntries(anyLong(), anyInt()))
				.thenReturn(ImmutableList.of(mock(LedgerEntry.class)));
			bind(LedgerEntryStore.class).toInstance(ledgerEntryStore);
			bind(ECKeyPair.class).annotatedWith(Names.named("self")).toInstance(ECKeyPair.generateNew());
		}
	}

	@Test
	public void when_configured_with_correct_interfaces__then_state_computer_should_be_created() {
		Injector injector = Guice.createInjector(
			new RadixEngineStoreModule(1),
			new ExternalRadixEngineStoreModule()
		);

		EngineStore<CommittedAtom> store = injector.getInstance(Key.get(new TypeLiteral<EngineStore<CommittedAtom>>() { }));
		assertThat(store).isNotNull();
	}
}