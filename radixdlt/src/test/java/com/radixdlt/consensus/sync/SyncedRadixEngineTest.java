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

package com.radixdlt.consensus.sync;

import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.radixdlt.consensus.VertexMetadata;
import com.radixdlt.constraintmachine.DataPointer;
import com.radixdlt.crypto.ECPublicKey;
import com.radixdlt.engine.RadixEngine;
import com.radixdlt.engine.RadixEngineErrorCode;
import com.radixdlt.engine.RadixEngineException;
import com.radixdlt.identifiers.AID;
import com.radixdlt.identifiers.EUID;
import com.radixdlt.middleware2.ClientAtom;
import com.radixdlt.middleware2.CommittedAtom;
import com.radixdlt.middleware2.LedgerAtom;
import com.radixdlt.middleware2.store.CommittedAtomsStore;
import com.radixdlt.network.addressbook.AddressBook;
import com.radixdlt.network.addressbook.Peer;
import io.reactivex.rxjava3.core.Observable;
import io.reactivex.rxjava3.observers.TestObserver;
import io.reactivex.rxjava3.subjects.BehaviorSubject;
import io.reactivex.rxjava3.subjects.PublishSubject;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import org.junit.Before;
import org.junit.Test;
import org.radix.atoms.events.AtomStoredEvent;

public class SyncedRadixEngineTest {

	private RadixEngine<LedgerAtom> radixEngine;
	private SyncedRadixEngine syncedRadixEngine;
	private CommittedAtomsStore committedAtomsStore;
	private AddressBook addressBook;
	private StateSyncNetwork stateSyncNetwork;

	@Before
	public void setup() {
		this.radixEngine = mock(RadixEngine.class);
		this.committedAtomsStore = mock(CommittedAtomsStore.class);
		this.addressBook = mock(AddressBook.class);
		this.stateSyncNetwork = mock(StateSyncNetwork.class);
		this.syncedRadixEngine = new SyncedRadixEngine(
			radixEngine,
			committedAtomsStore,
			addressBook,
			stateSyncNetwork
		);
	}

	@Test
	public void when_insert_and_commit_vertex_with_engine_virtual_state_conflict__then_no_exception_should_be_thrown() throws RadixEngineException {
		CommittedAtom committedAtom = mock(CommittedAtom.class);
		when(committedAtom.getClientAtom()).thenReturn(mock(ClientAtom.class));
		when(committedAtom.getAID()).thenReturn(mock(AID.class));

		RadixEngineException e = mock(RadixEngineException.class);
		when(e.getErrorCode()).thenReturn(RadixEngineErrorCode.VIRTUAL_STATE_CONFLICT);
		when(e.getDataPointer()).thenReturn(DataPointer.ofAtom());
		doThrow(e).when(radixEngine).checkAndStore(eq(committedAtom));

		syncedRadixEngine.execute(committedAtom);
		verify(radixEngine, times(1)).checkAndStore(eq(committedAtom));
	}


	@Test
	public void when_insert_and_commit_vertex_with_engine_state_conflict__then_no_exception_should_be_thrown() throws RadixEngineException {
		RadixEngineException e = mock(RadixEngineException.class);
		when(e.getErrorCode()).thenReturn(RadixEngineErrorCode.STATE_CONFLICT);
		when(e.getDataPointer()).thenReturn(DataPointer.ofAtom());

		LedgerAtom related = mock(LedgerAtom.class);
		when(related.getAID()).thenReturn(mock(AID.class));
		when(e.getRelated()).thenReturn(related);

		CommittedAtom committedAtom = mock(CommittedAtom.class);
		when(committedAtom.getAID()).thenReturn(mock(AID.class));
		doThrow(e).when(radixEngine).checkAndStore(eq(committedAtom));

		syncedRadixEngine.execute(committedAtom);
		verify(radixEngine, times(1)).checkAndStore(eq(committedAtom));
	}

	@Test
	public void when_insert_and_commit_vertex_with_engine_missing_dependency__then_no_exception_should_be_thrown() throws RadixEngineException {
		RadixEngineException e = mock(RadixEngineException.class);
		when(e.getErrorCode()).thenReturn(RadixEngineErrorCode.MISSING_DEPENDENCY);
		when(e.getDataPointer()).thenReturn(DataPointer.ofAtom());
		CommittedAtom committedAtom = mock(CommittedAtom.class);
		doThrow(e).when(radixEngine).checkAndStore(eq(committedAtom));

		syncedRadixEngine.execute(committedAtom);
		verify(radixEngine, times(1)).checkAndStore(eq(committedAtom));
	}

	@Test
	public void when_sync_request__then_it_is_processed() {
		when(stateSyncNetwork.syncResponses()).thenReturn(Observable.never());
		Peer peer = mock(Peer.class);
		long stateVersion = 12345;
		SyncRequest syncRequest = new SyncRequest(peer, stateVersion);
		when(stateSyncNetwork.syncRequests()).thenReturn(Observable.just(syncRequest).concatWith(Observable.never()));
		List<CommittedAtom> committedAtomList = Collections.singletonList(mock(CommittedAtom.class));
		when(committedAtomsStore.getCommittedAtoms(eq(stateVersion), anyInt())).thenReturn(committedAtomList);
		syncedRadixEngine.start();
		verify(stateSyncNetwork, timeout(1000).times(1)).sendSyncResponse(eq(peer), eq(committedAtomList));
	}

	@Test
	public void when_sync_response__then_it_is_processed() throws Exception {
		when(stateSyncNetwork.syncRequests()).thenReturn(Observable.never());
		CommittedAtom committedAtom = mock(CommittedAtom.class);
		VertexMetadata vertexMetadata = mock(VertexMetadata.class);
		when(vertexMetadata.getStateVersion()).thenReturn(12345L);
		when(committedAtom.getVertexMetadata()).thenReturn(vertexMetadata);
		List<CommittedAtom> committedAtomList = Collections.singletonList(committedAtom);
		when(stateSyncNetwork.syncResponses()).thenReturn(Observable.just(committedAtomList).concatWith(Observable.never()));
		when(committedAtomsStore.getStateVersion()).thenReturn(12344L);
		syncedRadixEngine.start();
		verify(radixEngine, timeout(1000).times(1)).checkAndStore(eq(committedAtom));
	}

	@Test
	public void when_sync_to__will_complete_when_higher_or_equal_state_version() throws Exception {
		Peer peer = mock(Peer.class);
		when(peer.hasSystem()).thenReturn(true);
		ECPublicKey pk = mock(ECPublicKey.class);
		EUID euid = mock(EUID.class);
		when(pk.euid()).thenReturn(euid);
		when(addressBook.peer(eq(euid))).thenReturn(Optional.of(peer));
		when(committedAtomsStore.getStateVersion()).thenReturn(1233L);

		CommittedAtom atom = mock(CommittedAtom.class);
		VertexMetadata vertexMetadata = mock(VertexMetadata.class);
		when(vertexMetadata.getStateVersion()).thenReturn(1233L);
		when(atom.getVertexMetadata()).thenReturn(vertexMetadata);
		AtomStoredEvent atomStoredEvent = mock(AtomStoredEvent.class);
		when(atomStoredEvent.getAtom()).thenReturn(atom);
		BehaviorSubject<AtomStoredEvent> event = BehaviorSubject.createDefault(atomStoredEvent);

		when(committedAtomsStore.lastStoredAtom()).thenReturn(event);

		TestObserver<Void> testObserver = syncedRadixEngine.syncTo(1234, Collections.singletonList(pk)).test();
		testObserver.assertNotComplete();

		CommittedAtom nextAtom = mock(CommittedAtom.class);
		VertexMetadata nextVertexMetadata = mock(VertexMetadata.class);
		when(nextVertexMetadata.getStateVersion()).thenReturn(1234L);
		when(nextAtom.getVertexMetadata()).thenReturn(nextVertexMetadata);
		AtomStoredEvent nextAtomStoredEvent = mock(AtomStoredEvent.class);
		when(nextAtomStoredEvent.getAtom()).thenReturn(nextAtom);

		event.onNext(nextAtomStoredEvent);
		testObserver.await(1, TimeUnit.SECONDS);
		testObserver.assertComplete();
	}
}