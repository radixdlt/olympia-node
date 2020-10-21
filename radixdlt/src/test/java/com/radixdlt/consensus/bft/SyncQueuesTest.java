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

package com.radixdlt.consensus.bft;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.google.common.hash.HashCode;
import com.radixdlt.consensus.QuorumCertificate;
import com.radixdlt.consensus.ConsensusEvent;
import com.radixdlt.consensus.HighQC;
import com.radixdlt.consensus.BFTHeader;
import com.radixdlt.consensus.bft.SyncQueues.SyncQueue;
import org.junit.Test;

public class SyncQueuesTest {
	@Test
	public void when_check_or_add_on_empty_queue__then_should_return_true() {
		BFTNode node = mock(BFTNode.class);
		SyncQueues syncQueues = new SyncQueues();

		ConsensusEvent event = mock(ConsensusEvent.class);
		when(event.getAuthor()).thenReturn(node);

		assertThat(syncQueues.isEmptyElseAdd(event)).isTrue();
	}

	@Test
	public void when_add_then_check_or_add_on_same_author__then_should_return_false() {
		BFTNode node = mock(BFTNode.class);
		SyncQueues syncQueues = new SyncQueues();

		ConsensusEvent event0 = mock(ConsensusEvent.class);
		when(event0.getAuthor()).thenReturn(node);
		ConsensusEvent event1 = mock(ConsensusEvent.class);
		when(event1.getAuthor()).thenReturn(node);
		syncQueues.add(event0);
		assertThat(syncQueues.isEmptyElseAdd(event1)).isFalse();
	}

	@Test
	public void when_add__then_peek_on_hash_should_return_event() {
		BFTNode node = mock(BFTNode.class);
		SyncQueues syncQueues = new SyncQueues();

		ConsensusEvent event0 = mock(ConsensusEvent.class);
		HashCode vertexId = mock(HashCode.class);
		QuorumCertificate qc = mock(QuorumCertificate.class);
		HighQC highQC = mock(HighQC.class);
		when(highQC.highestQC()).thenReturn(qc);
		BFTHeader header = mock(BFTHeader.class);
		when(header.getVertexId()).thenReturn(vertexId);
		when(qc.getProposed()).thenReturn(header);
		when(event0.getAuthor()).thenReturn(node);
		when(event0.highQC()).thenReturn(highQC);
		syncQueues.add(event0);

		for (SyncQueue syncQueue : syncQueues.getQueues()) {
			assertThat(syncQueue.peek(vertexId)).isEqualTo(event0);
			syncQueue.pop();
			assertThat(syncQueue.peek(null)).isNull();
		}
	}
}