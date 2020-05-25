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

package com.radixdlt.middleware2.store;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.powermock.api.mockito.PowerMockito.when;

import com.radixdlt.identifiers.AID;
import com.radixdlt.middleware2.LedgerAtom;
import com.radixdlt.middleware2.converters.AtomToBinaryConverter;
import com.radixdlt.middleware2.store.CommittedAtomsStore.AtomIndexer;
import com.radixdlt.store.LedgerEntryStore;
import io.reactivex.rxjava3.observers.TestObserver;
import java.util.Collections;
import org.junit.Before;
import org.junit.Test;
import org.radix.atoms.events.AtomStoredEvent;

public class CommittedAtomsStoreTest {
	private CommittedAtomsStore committedAtomsStore;
	private LedgerEntryStore store;
	private AtomToBinaryConverter atomToBinaryConverter;
	private AtomIndexer atomIndexer;

	@Before
	public void setUp() {
		this.store = mock(LedgerEntryStore.class);
		this.atomToBinaryConverter = mock(AtomToBinaryConverter.class);
		this.atomIndexer = mock(AtomIndexer.class);
		this.committedAtomsStore = new CommittedAtomsStore(store, atomToBinaryConverter, atomIndexer);
	}

	@Test
	public void when_store__then_should_get_atom_stored_event() {
		LedgerAtom atom = mock(LedgerAtom.class);
		when(atom.getAID()).thenReturn(mock(AID.class));
		when(atomToBinaryConverter.toLedgerEntryContent(eq(atom))).thenReturn(new byte[0]);
		EngineAtomIndices engineAtomIndices = mock(EngineAtomIndices.class);
		when(atomIndexer.getIndices(eq(atom))).thenReturn(engineAtomIndices);
		when(engineAtomIndices.getDuplicateIndices()).thenReturn(Collections.emptySet());
		TestObserver<AtomStoredEvent> testObserver = this.committedAtomsStore.lastStoredAtom().test();
		this.committedAtomsStore.storeAtom(atom);
		testObserver.awaitCount(1);
		testObserver.assertValue(e -> e.getAtom().equals(atom));
	}

}