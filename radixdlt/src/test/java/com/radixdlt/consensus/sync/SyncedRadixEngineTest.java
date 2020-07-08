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

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.radixdlt.EpochChangeSender;
import com.radixdlt.consensus.Vertex;
import com.radixdlt.consensus.VertexMetadata;
import com.radixdlt.consensus.View;
import com.radixdlt.consensus.sync.SyncedRadixEngine.CommittedStateSyncSender;
import com.radixdlt.consensus.validators.ValidatorSet;
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
import io.reactivex.rxjava3.subjects.BehaviorSubject;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;
import org.junit.Before;
import org.junit.Test;
import org.radix.atoms.events.AtomStoredEvent;

public class SyncedRadixEngineTest {

	private RadixEngine<LedgerAtom> radixEngine;
	private SyncedRadixEngine syncedRadixEngine;
	private CommittedAtomsStore committedAtomsStore;
	private AddressBook addressBook;
	private StateSyncNetwork stateSyncNetwork;
	private CommittedStateSyncSender committedStateSyncSender;
	private EpochChangeSender epochChangeSender;
	private Function<Long, ValidatorSet> validatorSetMapping;
	private View epochHighView;

	@Before
	public void setup() {
		// No type check issues with mocking generic here
		@SuppressWarnings("unchecked")
		RadixEngine<LedgerAtom> re = mock(RadixEngine.class);
		this.radixEngine = re;
		this.committedAtomsStore = mock(CommittedAtomsStore.class);
		this.addressBook = mock(AddressBook.class);
		this.stateSyncNetwork = mock(StateSyncNetwork.class);
		this.committedStateSyncSender = mock(CommittedStateSyncSender.class);
		this.epochChangeSender = mock(EpochChangeSender.class);
		this.validatorSetMapping = mock(Function.class);
		this.epochHighView = View.of(100);
		this.syncedRadixEngine = new SyncedRadixEngine(
			radixEngine,
			committedAtomsStore,
			committedStateSyncSender,
			epochChangeSender,
			validatorSetMapping,
			epochHighView,
			addressBook,
			stateSyncNetwork
		);
	}

	@Test
	public void when_compute_vertex_metadata_equal_to_high_view__then_should_return_true() {
		Vertex vertex = mock(Vertex.class);
		when(vertex.getView()).thenReturn(epochHighView);
		assertThat(syncedRadixEngine.compute(vertex)).isTrue();
	}

	@Test
	public void when_execute_end_of_epoch_atom__then_should_send_epoch_change() {
		CommittedAtom committedAtom = mock(CommittedAtom.class);
		VertexMetadata vertexMetadata = mock(VertexMetadata.class);
		long genesisEpoch = 123;
		when(vertexMetadata.getEpoch()).thenReturn(genesisEpoch);
		when(vertexMetadata.isEndOfEpoch()).thenReturn(true);
		when(committedAtom.getVertexMetadata()).thenReturn(vertexMetadata);

		ValidatorSet validatorSet = mock(ValidatorSet.class);
		when(this.validatorSetMapping.apply(eq(genesisEpoch + 1))).thenReturn(validatorSet);

		syncedRadixEngine.execute(committedAtom);
		verify(epochChangeSender, times(1))
			.epochChange(
				argThat(e -> e.getAncestor().equals(vertexMetadata) && e.getValidatorSet().equals(validatorSet))
			);
	}

	@Test
	public void when_insert_and_commit_vertex_with_engine_virtual_state_conflict__then_no_exception_should_be_thrown() throws RadixEngineException {
		CommittedAtom committedAtom = mock(CommittedAtom.class);
		when(committedAtom.getClientAtom()).thenReturn(mock(ClientAtom.class));
		when(committedAtom.getAID()).thenReturn(mock(AID.class));
		VertexMetadata vertexMetadata = mock(VertexMetadata.class);
		when(vertexMetadata.getView()).thenReturn(View.of(50));
		when(committedAtom.getVertexMetadata()).thenReturn(vertexMetadata);

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
		VertexMetadata vertexMetadata = mock(VertexMetadata.class);
		when(vertexMetadata.getView()).thenReturn(View.of(50));
		when(committedAtom.getVertexMetadata()).thenReturn(vertexMetadata);
		when(committedAtom.getAID()).thenReturn(mock(AID.class));
		when(committedAtom.getClientAtom()).thenReturn(mock(ClientAtom.class));
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
		VertexMetadata vertexMetadata = mock(VertexMetadata.class);
		when(vertexMetadata.getView()).thenReturn(View.of(50));
		when(committedAtom.getVertexMetadata()).thenReturn(vertexMetadata);
		when(committedAtom.getClientAtom()).thenReturn(mock(ClientAtom.class));
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
		when(vertexMetadata.getView()).thenReturn(View.of(50));
		when(committedAtom.getVertexMetadata()).thenReturn(vertexMetadata);
		when(committedAtom.getClientAtom()).thenReturn(mock(ClientAtom.class));
		List<CommittedAtom> committedAtomList = Collections.singletonList(committedAtom);
		when(stateSyncNetwork.syncResponses()).thenReturn(Observable.just(committedAtomList).concatWith(Observable.never()));
		when(committedAtomsStore.getStateVersion()).thenReturn(12344L);
		syncedRadixEngine.start();
		verify(radixEngine, timeout(1000).times(1)).checkAndStore(eq(committedAtom));
	}

	@Test
	public void when_sync_to__will_complete_when_higher_or_equal_state_version() {
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

		CommittedAtom nextAtom = mock(CommittedAtom.class);
		VertexMetadata nextVertexMetadata = mock(VertexMetadata.class);
		when(nextVertexMetadata.getStateVersion()).thenReturn(1234L);

		syncedRadixEngine.syncTo(nextVertexMetadata, Collections.singletonList(pk), mock(Object.class));
		verify(committedStateSyncSender, never()).sendCommittedStateSync(anyLong(), any());

		when(nextAtom.getVertexMetadata()).thenReturn(nextVertexMetadata);
		AtomStoredEvent nextAtomStoredEvent = mock(AtomStoredEvent.class);
		when(nextAtomStoredEvent.getAtom()).thenReturn(nextAtom);
		event.onNext(nextAtomStoredEvent);

		verify(committedStateSyncSender, timeout(100).atLeast(1)).sendCommittedStateSync(anyLong(), any());
	}
}