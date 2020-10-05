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

package com.radixdlt.sync;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.radixdlt.consensus.BFTHeader;
import com.radixdlt.consensus.LedgerHeader;
import com.radixdlt.consensus.TimestampedECDSASignatures;
import com.radixdlt.consensus.VerifiedLedgerHeaderAndProof;
import com.radixdlt.consensus.bft.BFTNode;
import com.radixdlt.crypto.Hash;
import com.radixdlt.ledger.DtoLedgerHeaderAndProof;
import com.radixdlt.ledger.VerifiedCommandsAndProof;
import com.radixdlt.middleware2.store.InMemoryCommittedEpochProofsStore;
import org.junit.Before;
import org.junit.Test;

public class RemoteSyncServiceProcessorTest {

	private RemoteSyncServiceProcessor processor;
	private CommittedReader reader;
	private InMemoryCommittedEpochProofsStore proofsStore;
	private StateSyncNetwork network;

	@Before
	public void setUp() {
		this.reader = mock(CommittedReader.class);
		this.proofsStore = mock(InMemoryCommittedEpochProofsStore.class);
		this.network =  mock(StateSyncNetwork.class);
		this.processor = new RemoteSyncServiceProcessor(reader, proofsStore, network, 1);
	}

	@Test
	public void when_remote_sync_request__then_process_it() {
		RemoteSyncRequest request = mock(RemoteSyncRequest.class);
		DtoLedgerHeaderAndProof header = mock(DtoLedgerHeaderAndProof.class);
		when(header.getOpaque0()).thenReturn(mock(BFTHeader.class));
		when(header.getOpaque1()).thenReturn(mock(BFTHeader.class));
		when(header.getOpaque3()).thenReturn(mock(Hash.class));
		when(header.getLedgerHeader()).thenReturn(mock(LedgerHeader.class));
		when(header.getSignatures()).thenReturn(mock(TimestampedECDSASignatures.class));
		when(request.getCurrentHeader()).thenReturn(header);
		BFTNode node = mock(BFTNode.class);
		when(request.getNode()).thenReturn(node);
		VerifiedCommandsAndProof verifiedCommandsAndProof = mock(VerifiedCommandsAndProof.class);
		VerifiedLedgerHeaderAndProof verifiedHeader = mock(VerifiedLedgerHeaderAndProof.class);
		when(verifiedHeader.toDto()).thenReturn(header);
		when(verifiedCommandsAndProof.getHeader()).thenReturn(verifiedHeader);
		when(reader.getNextCommittedCommands(any(), anyInt())).thenReturn(verifiedCommandsAndProof);
		processor.processRemoteSyncRequest(request);
		verify(network, times(1)).sendSyncResponse(eq(node), any());
	}

	@Test
	public void when_remote_sync_request_and_unable__then_dont_do_anything() {
		RemoteSyncRequest request = mock(RemoteSyncRequest.class);
		DtoLedgerHeaderAndProof header = mock(DtoLedgerHeaderAndProof.class);
		when(header.getOpaque0()).thenReturn(mock(BFTHeader.class));
		when(header.getOpaque1()).thenReturn(mock(BFTHeader.class));
		when(header.getOpaque3()).thenReturn(mock(Hash.class));
		when(header.getLedgerHeader()).thenReturn(mock(LedgerHeader.class));
		when(header.getSignatures()).thenReturn(mock(TimestampedECDSASignatures.class));
		when(request.getCurrentHeader()).thenReturn(header);
		processor.processRemoteSyncRequest(request);
		verify(network, never()).sendSyncResponse(any(), any());
	}

	@Test
	public void when_remote_sync_request_and_null_return__then_dont_do_anything() {
		RemoteSyncRequest request = mock(RemoteSyncRequest.class);
		DtoLedgerHeaderAndProof header = mock(DtoLedgerHeaderAndProof.class);
		when(header.getOpaque0()).thenReturn(mock(BFTHeader.class));
		when(header.getOpaque1()).thenReturn(mock(BFTHeader.class));
		when(header.getOpaque3()).thenReturn(mock(Hash.class));
		when(header.getLedgerHeader()).thenReturn(mock(LedgerHeader.class));
		when(header.getSignatures()).thenReturn(mock(TimestampedECDSASignatures.class));
		when(request.getCurrentHeader()).thenReturn(header);
		processor.processRemoteSyncRequest(request);
		when(reader.getNextCommittedCommands(any(), anyInt())).thenReturn(null);
		verify(network, never()).sendSyncResponse(any(), any());
	}
}