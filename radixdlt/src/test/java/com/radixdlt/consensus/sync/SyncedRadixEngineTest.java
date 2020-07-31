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

import com.google.common.collect.ImmutableList;
import com.radixdlt.EpochChangeSender;
import com.radixdlt.consensus.Vertex;
import com.radixdlt.consensus.VertexMetadata;
import com.radixdlt.consensus.View;
import com.radixdlt.consensus.bft.BFTNode;
import com.radixdlt.consensus.sync.SyncedRadixEngine.CommittedStateSyncSender;
import com.radixdlt.consensus.sync.SyncedRadixEngine.SyncedRadixEngineEventSender;
import com.radixdlt.consensus.bft.BFTValidatorSet;
import com.radixdlt.counters.SystemCounters;
import com.radixdlt.crypto.ECPublicKey;
import com.radixdlt.engine.RadixEngine;
import com.radixdlt.engine.RadixEngineException;
import com.radixdlt.identifiers.AID;
import com.radixdlt.identifiers.EUID;
import com.radixdlt.mempool.Mempool;
import com.radixdlt.middleware2.ClientAtom;
import com.radixdlt.middleware2.CommittedAtom;
import com.radixdlt.middleware2.LedgerAtom;
import com.radixdlt.middleware2.store.CommittedAtomsStore;
import com.radixdlt.network.addressbook.AddressBook;
import com.radixdlt.network.addressbook.Peer;
import io.reactivex.rxjava3.core.Observable;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;
import org.junit.Before;
import org.junit.Test;

public class SyncedRadixEngineTest {

	private Mempool mempool;
	private RadixEngine<LedgerAtom> radixEngine;
	private SyncedRadixEngine syncedRadixEngine;
	private CommittedAtomsStore committedAtomsStore;
	private AddressBook addressBook;
	private StateSyncNetwork stateSyncNetwork;
	private CommittedStateSyncSender committedStateSyncSender;
	private EpochChangeSender epochChangeSender;
	private SyncedRadixEngineEventSender eventSender;
	private Function<Long, BFTValidatorSet> validatorSetMapping;
	private View epochHighView;
	private SystemCounters counters;

	@Before
	public void setup() {
		this.mempool = mock(Mempool.class);
		// No type check issues with mocking generic here
		@SuppressWarnings("unchecked")
		RadixEngine<LedgerAtom> re = mock(RadixEngine.class);
		this.radixEngine = re;
		this.committedAtomsStore = mock(CommittedAtomsStore.class);
		this.addressBook = mock(AddressBook.class);
		this.stateSyncNetwork = mock(StateSyncNetwork.class);
		this.committedStateSyncSender = mock(CommittedStateSyncSender.class);
		this.epochChangeSender = mock(EpochChangeSender.class);
		this.eventSender = mock(SyncedRadixEngineEventSender.class);
		this.counters = mock(SystemCounters.class);
		// No issues with type checking for mock
		@SuppressWarnings("unchecked")
		Function<Long, BFTValidatorSet> vsm = mock(Function.class);
		this.validatorSetMapping = vsm;
		this.epochHighView = View.of(100);
		this.syncedRadixEngine = new SyncedRadixEngine(
			mempool,
			radixEngine,
			committedAtomsStore,
			committedStateSyncSender,
			epochChangeSender,
			eventSender,
			validatorSetMapping,
			epochHighView,
			addressBook,
			stateSyncNetwork,
			counters
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

		BFTValidatorSet validatorSet = mock(BFTValidatorSet.class);
		when(this.validatorSetMapping.apply(eq(genesisEpoch + 1))).thenReturn(validatorSet);

		syncedRadixEngine.execute(committedAtom);
		verify(epochChangeSender, times(1))
			.epochChange(
				argThat(e -> e.getAncestor().equals(vertexMetadata) && e.getValidatorSet().equals(validatorSet))
			);
	}

	@Test
	public void when_insert_and_commit_vertex_with_engine_exception__then_correct_messages_are_sent() throws RadixEngineException {
		CommittedAtom committedAtom = mock(CommittedAtom.class);
		when(committedAtom.getClientAtom()).thenReturn(mock(ClientAtom.class));
		AID aid = mock(AID.class);
		when(committedAtom.getAID()).thenReturn(aid);
		VertexMetadata vertexMetadata = mock(VertexMetadata.class);
		when(vertexMetadata.getView()).then(i -> View.of(50));
		when(vertexMetadata.getStateVersion()).then(i -> 1L);
		when(committedAtom.getVertexMetadata()).thenReturn(vertexMetadata);

		RadixEngineException e = mock(RadixEngineException.class);
		doThrow(e).when(radixEngine).checkAndStore(eq(committedAtom));

		syncedRadixEngine.execute(committedAtom);
		verify(radixEngine, times(1)).checkAndStore(eq(committedAtom));
		verify(committedAtomsStore, times(1)).storeVertexMetadata(eq(vertexMetadata));
		verify(mempool, times(1)).removeCommittedAtom(aid);
		verify(eventSender, times(1)).sendStoredFailure(eq(committedAtom), eq(e));
	}

	@Test
	public void when_insert_and_commit_vertex_with_engine_exception__then_is_available_with_sync() throws RadixEngineException {
		CommittedAtom committedAtom = mock(CommittedAtom.class);
		when(committedAtom.getClientAtom()).thenReturn(mock(ClientAtom.class));
		AID aid = mock(AID.class);
		when(committedAtom.getAID()).thenReturn(aid);
		VertexMetadata vertexMetadata = mock(VertexMetadata.class);
		when(vertexMetadata.getView()).then(i -> View.of(50));
		when(vertexMetadata.getStateVersion()).then(i -> 1L);
		when(committedAtom.getVertexMetadata()).thenReturn(vertexMetadata);

		RadixEngineException e = mock(RadixEngineException.class);
		doThrow(e).when(radixEngine).checkAndStore(eq(committedAtom));

		syncedRadixEngine.execute(committedAtom);

		Peer peer = mock(Peer.class);
		when(committedAtomsStore.getCommittedAtoms(eq(1L), anyInt())).thenReturn(Collections.emptyList());
		when(stateSyncNetwork.syncResponses()).thenReturn(Observable.never());
		when(stateSyncNetwork.syncRequests()).thenReturn(Observable.just(new SyncRequest(peer, 0L)));
		syncedRadixEngine.start();
		verify(stateSyncNetwork, timeout(1000).times(1)).sendSyncResponse(eq(peer), argThat(l -> l.get(0).equals(committedAtom)));
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
		ImmutableList<CommittedAtom> committedAtomList = ImmutableList.of(committedAtom);
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
		BFTNode node = mock(BFTNode.class);
		when(node.getKey()).thenReturn(pk);
		when(addressBook.peer(eq(euid))).thenReturn(Optional.of(peer));
		when(committedAtomsStore.getStateVersion()).thenReturn(1233L);

		CommittedAtom atom = mock(CommittedAtom.class);
		VertexMetadata vertexMetadata = mock(VertexMetadata.class);
		when(vertexMetadata.getStateVersion()).thenReturn(1233L);
		when(atom.getVertexMetadata()).thenReturn(vertexMetadata);

		CommittedAtom nextAtom = mock(CommittedAtom.class);
		VertexMetadata nextVertexMetadata = mock(VertexMetadata.class);
		when(nextVertexMetadata.getStateVersion()).thenReturn(1234L);

		syncedRadixEngine.syncTo(nextVertexMetadata, Collections.singletonList(node), mock(Object.class));
		verify(committedStateSyncSender, never()).sendCommittedStateSync(anyLong(), any());

		when(nextAtom.getClientAtom()).thenReturn(mock(ClientAtom.class));
		when(nextAtom.getVertexMetadata()).thenReturn(nextVertexMetadata);
		syncedRadixEngine.execute(nextAtom);

		verify(committedStateSyncSender, timeout(100).atLeast(1)).sendCommittedStateSync(anyLong(), any());
	}
}