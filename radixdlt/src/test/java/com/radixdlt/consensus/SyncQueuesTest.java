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

package com.radixdlt.consensus;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.google.common.collect.ImmutableSet;
import com.radixdlt.consensus.SyncQueues.SyncQueue;
import com.radixdlt.counters.SystemCounters;
import com.radixdlt.crypto.ECPublicKey;
import com.radixdlt.crypto.Hash;
import org.junit.Test;

public class SyncQueuesTest {
	@Test
	public void when_check_or_add_on_empty_queue__then_should_return_true() {
		ECPublicKey key = mock(ECPublicKey.class);
		SyncQueues syncQueues = new SyncQueues(
			ImmutableSet.of(key),
			mock(SystemCounters.class)
		);

		HasSyncConsensusEvent event = mock(HasSyncConsensusEvent.class);
		when(event.getAuthor()).thenReturn(key);

		assertThat(syncQueues.checkOrAdd(event)).isTrue();
	}

	@Test
	public void when_add_then_check_or_add_on_same_author__then_should_return_false() {
		ECPublicKey key = mock(ECPublicKey.class);
		SyncQueues syncQueues = new SyncQueues(
			ImmutableSet.of(key),
			mock(SystemCounters.class)
		);

		HasSyncConsensusEvent event0 = mock(HasSyncConsensusEvent.class);
		when(event0.getAuthor()).thenReturn(key);
		HasSyncConsensusEvent event1 = mock(HasSyncConsensusEvent.class);
		when(event1.getAuthor()).thenReturn(key);
		syncQueues.add(event0);
		assertThat(syncQueues.checkOrAdd(event1)).isFalse();
	}

	@Test
	public void when_add__then_peek_on_hash_should_return_event() {
		ECPublicKey key = mock(ECPublicKey.class);
		SyncQueues syncQueues = new SyncQueues(
			ImmutableSet.of(key),
			mock(SystemCounters.class)
		);

		HasSyncConsensusEvent event0 = mock(HasSyncConsensusEvent.class);
		Hash vertexId = mock(Hash.class);
		QuorumCertificate qc = mock(QuorumCertificate.class);
		VertexMetadata vertexMetadata = mock(VertexMetadata.class);
		when(vertexMetadata.getId()).thenReturn(vertexId);
		when(qc.getProposed()).thenReturn(vertexMetadata);
		when(event0.getAuthor()).thenReturn(key);
		when(event0.getQC()).thenReturn(qc);
		syncQueues.add(event0);

		for (SyncQueue syncQueue : syncQueues.getQueues()) {
			assertThat(syncQueue.peek(vertexId)).isEqualTo(event0);
			syncQueue.pop();
			assertThat(syncQueue.peek(null)).isNull();
		}
	}
}