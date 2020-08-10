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

package com.radixdlt.syncer;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.radixdlt.consensus.Vertex;
import com.radixdlt.consensus.VertexMetadata;
import com.radixdlt.consensus.bft.View;
import com.radixdlt.consensus.bft.BFTNode;
import com.radixdlt.execution.RadixEngineExecutor;
import com.radixdlt.syncer.SyncedEpochExecutor.CommittedStateSyncSender;
import com.radixdlt.consensus.bft.BFTValidatorSet;
import com.radixdlt.counters.SystemCounters;
import com.radixdlt.crypto.ECPublicKey;
import com.radixdlt.identifiers.AID;
import com.radixdlt.identifiers.EUID;
import com.radixdlt.mempool.Mempool;
import com.radixdlt.middleware2.ClientAtom;
import com.radixdlt.middleware2.CommittedAtom;
import com.radixdlt.network.addressbook.AddressBook;
import com.radixdlt.network.addressbook.Peer;
import java.util.Collections;
import java.util.Optional;
import java.util.function.Function;
import org.junit.Before;
import org.junit.Test;

public class SyncedEpochExecutorTest {

	private Mempool mempool;
	private RadixEngineExecutor executor;
	private SyncedEpochExecutor syncedEpochExecutor;
	private AddressBook addressBook;
	private StateSyncNetwork stateSyncNetwork;
	private CommittedStateSyncSender committedStateSyncSender;
	private EpochChangeSender epochChangeSender;
	private Function<Long, BFTValidatorSet> validatorSetMapping;
	private View epochHighView;
	private SystemCounters counters;

	@Before
	public void setup() {
		this.mempool = mock(Mempool.class);
		// No type check issues with mocking generic here
		this.executor = mock(RadixEngineExecutor.class);
		this.addressBook = mock(AddressBook.class);
		this.stateSyncNetwork = mock(StateSyncNetwork.class);
		this.committedStateSyncSender = mock(CommittedStateSyncSender.class);
		this.epochChangeSender = mock(EpochChangeSender.class);
		this.counters = mock(SystemCounters.class);
		// No issues with type checking for mock
		@SuppressWarnings("unchecked")
		Function<Long, BFTValidatorSet> vsm = mock(Function.class);
		this.validatorSetMapping = vsm;
		this.epochHighView = View.of(100);
		this.syncedEpochExecutor = new SyncedEpochExecutor(
			1233,
			mempool,
			executor,
			committedStateSyncSender,
			epochChangeSender,
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
		assertThat(syncedEpochExecutor.compute(vertex)).isTrue();
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

		syncedEpochExecutor.execute(committedAtom);
		verify(epochChangeSender, times(1))
			.epochChange(
				argThat(e -> e.getAncestor().equals(vertexMetadata) && e.getValidatorSet().equals(validatorSet))
			);
	}

	@Test
	public void when_insert_and_commit_vertex_with_engine_exception__then_correct_messages_are_sent() {
		CommittedAtom committedAtom = mock(CommittedAtom.class);
		when(committedAtom.getClientAtom()).thenReturn(mock(ClientAtom.class));
		AID aid = mock(AID.class);
		when(committedAtom.getAID()).thenReturn(aid);
		VertexMetadata vertexMetadata = mock(VertexMetadata.class);
		when(vertexMetadata.getView()).then(i -> View.of(50));
		when(vertexMetadata.getStateVersion()).then(i -> 1234L);
		when(committedAtom.getVertexMetadata()).thenReturn(vertexMetadata);

		syncedEpochExecutor.execute(committedAtom);
		verify(executor, times(1)).execute(eq(committedAtom));
		verify(mempool, times(1)).removeCommittedAtom(aid);
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

		CommittedAtom nextAtom = mock(CommittedAtom.class);
		VertexMetadata nextVertexMetadata = mock(VertexMetadata.class);
		when(nextVertexMetadata.getStateVersion()).thenReturn(1234L);

		syncedEpochExecutor.syncTo(nextVertexMetadata, Collections.singletonList(node), mock(Object.class));
		verify(committedStateSyncSender, never()).sendCommittedStateSync(anyLong(), any());

		when(nextAtom.getClientAtom()).thenReturn(mock(ClientAtom.class));
		when(nextAtom.getVertexMetadata()).thenReturn(nextVertexMetadata);
		syncedEpochExecutor.execute(nextAtom);

		verify(committedStateSyncSender, timeout(100).atLeast(1)).sendCommittedStateSync(anyLong(), any());
	}
}