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

import com.google.common.collect.ImmutableList;
import com.radixdlt.consensus.VertexMetadata;
import com.radixdlt.consensus.bft.View;
import com.radixdlt.consensus.bft.BFTNode;
import com.radixdlt.statecomputer.RadixEngineStateComputer;
import com.radixdlt.syncer.SyncExecutor.CommittedStateSyncSender;
import com.radixdlt.consensus.bft.BFTValidatorSet;
import com.radixdlt.counters.SystemCounters;
import com.radixdlt.crypto.ECPublicKey;
import com.radixdlt.identifiers.AID;
import com.radixdlt.identifiers.EUID;
import com.radixdlt.mempool.Mempool;
import com.radixdlt.middleware2.ClientAtom;
import com.radixdlt.middleware2.CommittedAtom;
import com.radixdlt.network.addressbook.Peer;
import com.radixdlt.syncer.SyncExecutor.SyncService;
import org.junit.Before;
import org.junit.Test;

public class SyncExecutorTest {

	private Mempool mempool;
	private RadixEngineStateComputer executor;
	private SyncExecutor syncExecutor;
	private CommittedStateSyncSender committedStateSyncSender;
	private EpochChangeSender epochChangeSender;

	private SystemCounters counters;
	private SyncService syncService;

	@Before
	public void setup() {
		this.mempool = mock(Mempool.class);
		// No type check issues with mocking generic here
		this.executor = mock(RadixEngineStateComputer.class);
		this.committedStateSyncSender = mock(CommittedStateSyncSender.class);
		this.epochChangeSender = mock(EpochChangeSender.class);
		this.counters = mock(SystemCounters.class);

		this.syncService = mock(SyncService.class);
		this.syncExecutor = new SyncExecutor(
			1233,
			mempool,
			executor,
			committedStateSyncSender,
			epochChangeSender,
			syncService,
			counters
		);
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
		when(this.executor.getValidatorSet(eq(genesisEpoch + 1))).thenReturn(validatorSet);

		syncExecutor.execute(committedAtom);
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

		syncExecutor.execute(committedAtom);
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

		CommittedAtom nextAtom = mock(CommittedAtom.class);
		VertexMetadata nextVertexMetadata = mock(VertexMetadata.class);
		when(nextVertexMetadata.getStateVersion()).thenReturn(1234L);

		syncExecutor.syncTo(nextVertexMetadata, ImmutableList.of(node), mock(Object.class));
		verify(committedStateSyncSender, never()).sendCommittedStateSync(anyLong(), any());

		when(nextAtom.getClientAtom()).thenReturn(mock(ClientAtom.class));
		when(nextAtom.getVertexMetadata()).thenReturn(nextVertexMetadata);
		syncExecutor.execute(nextAtom);

		verify(committedStateSyncSender, timeout(100).atLeast(1)).sendCommittedStateSync(anyLong(), any());
	}
}