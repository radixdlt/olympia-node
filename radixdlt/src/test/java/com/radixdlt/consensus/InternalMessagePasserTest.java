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

import static org.mockito.Mockito.mock;
import static org.powermock.api.mockito.PowerMockito.when;

import com.radixdlt.crypto.Hash;
import io.reactivex.rxjava3.observers.TestObserver;
import org.junit.Test;

public class InternalMessagePasserTest {
	@Test
	public void when_send_sync_event__then_should_receive_it() {
		InternalMessagePasser internalMessagePasser = new InternalMessagePasser();
		TestObserver<Hash> testObserver = internalMessagePasser.syncedVertices().test();
		Hash hash = mock(Hash.class);
		Vertex vertex = mock(Vertex.class);
		when(vertex.getId()).thenReturn(hash);
		internalMessagePasser.sendSyncedVertex(vertex);
		testObserver.awaitCount(1);
		testObserver.assertValue(hash);
		testObserver.assertNotComplete();
	}

	@Test
	public void when_send_committed_vertex_event__then_should_receive_it() {
		InternalMessagePasser internalMessagePasser = new InternalMessagePasser();
		TestObserver<Vertex> testObserver = internalMessagePasser.committedVertices().test();
		Vertex vertex = mock(Vertex.class);
		internalMessagePasser.sendCommittedVertex(vertex);
		testObserver.awaitCount(1);
		testObserver.assertValue(vertex);
		testObserver.assertNotComplete();
	}

	@Test
	public void when_send_high_qc_event__then_should_receive_it() {
		InternalMessagePasser internalMessagePasser = new InternalMessagePasser();
		TestObserver<QuorumCertificate> testObserver = internalMessagePasser.highQCs().test();
		QuorumCertificate qc = mock(QuorumCertificate.class);
		internalMessagePasser.highQC(qc);
		testObserver.awaitCount(1);
		testObserver.assertValue(qc);
		testObserver.assertNotComplete();
	}

	@Test
	public void when_send_committed_state_sync_event__then_should_receive_it() {
		InternalMessagePasser internalMessagePasser = new InternalMessagePasser();
		TestObserver<CommittedStateSync> testObserver = internalMessagePasser.committedStateSyncs().test();
		long stateVersion = 12345;
		Object opaque = mock(Object.class);
		internalMessagePasser.sendCommittedStateSync(stateVersion, opaque);
		testObserver.awaitCount(1);
		testObserver.assertValue(s -> s.getOpaque().equals(opaque) && s.getStateVersion() == stateVersion);
		testObserver.assertNotComplete();
	}

	@Test
	public void when_send_epoch_change_event__then_should_receive_it() {
		InternalMessagePasser internalMessagePasser = new InternalMessagePasser();
		TestObserver<EpochChange> testObserver = internalMessagePasser.epochChanges().test();
		EpochChange epochChange = mock(EpochChange.class);
		internalMessagePasser.epochChange(epochChange);
		testObserver.awaitCount(1);
		testObserver.assertValue(epochChange);
		testObserver.assertNotComplete();
	}
}