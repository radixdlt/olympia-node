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

import static org.assertj.core.api.AssertionsForInterfaceTypes.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.powermock.api.mockito.PowerMockito.when;

import com.google.common.collect.ImmutableList;
import com.radixdlt.consensus.VertexMetadata;
import com.radixdlt.constraintmachine.Particle;
import com.radixdlt.constraintmachine.Spin;
import com.radixdlt.counters.SystemCounters;
import com.radixdlt.identifiers.AID;
import com.radixdlt.identifiers.EUID;
import com.radixdlt.middleware2.CommittedAtom;
import com.radixdlt.middleware2.converters.AtomToBinaryConverter;
import com.radixdlt.middleware2.store.CommittedAtomsStore.AtomIndexer;
import com.radixdlt.store.LedgerEntry;
import com.radixdlt.store.LedgerEntryStore;
import com.radixdlt.store.SearchCursor;
import com.radixdlt.store.berkeley.BerkeleyLedgerEntryStore;
import io.reactivex.rxjava3.observers.TestObserver;
import java.util.Collections;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.stream.Stream;
import org.junit.Before;
import org.junit.Test;
import org.radix.atoms.events.AtomStoredEvent;

public class CommittedAtomsStoreTest {
	private CommittedAtomsStore committedAtomsStore;
	private LedgerEntryStore store;
	private AtomToBinaryConverter atomToBinaryConverter;
	private AtomIndexer atomIndexer;
	private SystemCounters counters;

	@Before
	public void setUp() {
		this.store = mock(LedgerEntryStore.class, withSettings().verboseLogging());
		this.atomToBinaryConverter = mock(AtomToBinaryConverter.class);
		this.atomIndexer = mock(AtomIndexer.class);
		this.counters = mock(SystemCounters.class);

		this.committedAtomsStore = new CommittedAtomsStore(store, atomToBinaryConverter, atomIndexer, counters);
	}

	@Test
	public void when_get_atom_containing__then_should_return_atom() {
		Particle particle = mock(Particle.class);
		when(particle.euid()).thenReturn(EUID.ONE);
		// No type check issues with mocking generic here
		@SuppressWarnings("unchecked")
		Consumer<CommittedAtom> callback = mock(Consumer.class);
		SearchCursor searchCursor = mock(SearchCursor.class);
		AID aid = mock(AID.class);
		when(searchCursor.get()).thenReturn(aid);
		when(store.search(any(), any(), any())).thenReturn(searchCursor);
		LedgerEntry ledgerEntry = mock(LedgerEntry.class);
		when(ledgerEntry.getContent()).thenReturn(new byte[0]);
		CommittedAtom committedAtom = mock(CommittedAtom.class);
		when(atomToBinaryConverter.toAtom(any())).thenReturn(committedAtom);
		when(store.get(eq(aid))).thenReturn(Optional.of(ledgerEntry));

		committedAtomsStore.getAtomContaining(particle, true, callback);
		verify(callback, times(1)).accept(eq(committedAtom));
	}


	@Test
	public void when_get_spin_and_particle_exists__then_should_return_spin() {
		Particle particle = mock(Particle.class);
		when(particle.euid()).thenReturn(EUID.ONE);
		SearchCursor searchCursor = mock(SearchCursor.class);
		AID aid = mock(AID.class);
		when(searchCursor.get()).thenReturn(aid);
		when(store.search(any(), any(), any())).thenReturn(searchCursor);
		LedgerEntry ledgerEntry = mock(LedgerEntry.class);
		when(ledgerEntry.getContent()).thenReturn(new byte[0]);
		CommittedAtom committedAtom = mock(CommittedAtom.class);
		when(atomToBinaryConverter.toAtom(any())).thenReturn(committedAtom);
		when(store.get(eq(aid))).thenReturn(Optional.of(ledgerEntry));

		assertThat(committedAtomsStore.getSpin(particle)).isEqualTo(Spin.DOWN);
	}

	@Test
	public void when_store__then_should_get_atom_stored_event() {
		CommittedAtom atom = mock(CommittedAtom.class);
		when(atom.getAID()).thenReturn(mock(AID.class));
		when(atom.getVertexMetadata()).thenReturn(mock(VertexMetadata.class));
		when(atomToBinaryConverter.toLedgerEntryContent(eq(atom))).thenReturn(new byte[0]);
		EngineAtomIndices engineAtomIndices = mock(EngineAtomIndices.class);
		when(atomIndexer.getIndices(eq(atom))).thenReturn(engineAtomIndices);
		when(engineAtomIndices.getDuplicateIndices()).thenReturn(Collections.emptySet());
		TestObserver<AtomStoredEvent> testObserver = this.committedAtomsStore.lastStoredAtom().test();
		this.committedAtomsStore.storeAtom(atom);
		testObserver.awaitCount(1);
		testObserver.assertValue(e -> e.getAtom().equals(atom));
	}

	@Test
	public void when_get_committed_atoms__should_return_atoms() {
		ImmutableList<AID> aids = ImmutableList.of(mock(AID.class), mock(AID.class), mock(AID.class), mock(AID.class));
		ImmutableList<LedgerEntry> entries = Stream.generate(() -> mock(LedgerEntry.class))
			.limit(4)
			.collect(ImmutableList.toImmutableList());
		when(this.store.getNextCommitted(eq(3L), eq(4)))
			.thenReturn(aids);
		for (int i = 0; i < aids.size(); i++) {
			when(this.store.get(eq(aids.get(i)))).thenReturn(Optional.of(entries.get(i)));
			when(entries.get(i).getContent()).thenReturn(new byte[i]);
			when(this.atomToBinaryConverter.toAtom(any())).thenReturn(mock(CommittedAtom.class));
		}

		assertThat(this.committedAtomsStore.getCommittedAtoms(3, 4)).hasSize(4);
	}
}