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

import static com.radixdlt.utils.TypedMocks.rmock;
import static org.mockito.ArgumentMatchers.any;
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
import com.google.common.hash.HashCode;
import com.radixdlt.counters.SystemCounters;
import com.radixdlt.environment.RemoteEventDispatcher;
import com.radixdlt.ledger.DtoLedgerHeaderAndProof;
import com.radixdlt.ledger.VerifiedCommandsAndProof;
import com.radixdlt.sync.messages.remote.StatusResponse;
import com.radixdlt.sync.messages.remote.SyncRequest;
import com.radixdlt.sync.messages.remote.SyncResponse;

import java.util.Comparator;
import org.junit.Before;
import org.junit.Test;

public class RemoteSyncServiceTest {

	private RemoteSyncService processor;
	private CommittedReader reader;
	private RemoteEventDispatcher<StatusResponse> statusResponseDispatcher;
	private RemoteEventDispatcher<SyncResponse> syncResponseDispatcher;

	@Before
	public void setUp() {
		this.reader = mock(CommittedReader.class);
		this.statusResponseDispatcher =  rmock(RemoteEventDispatcher.class);
		this.syncResponseDispatcher =  rmock(RemoteEventDispatcher.class);

		this.processor = new RemoteSyncService(reader, statusResponseDispatcher, syncResponseDispatcher,
			mock(SystemCounters.class), rmock(Comparator.class),
			mock(VerifiedLedgerHeaderAndProof.class)
		);
	}

	@Test
	public void when_remote_sync_request__then_process_it() {
		SyncRequest request = mock(SyncRequest.class);
		DtoLedgerHeaderAndProof header = mock(DtoLedgerHeaderAndProof.class);
		when(header.getOpaque0()).thenReturn(mock(BFTHeader.class));
		when(header.getOpaque1()).thenReturn(mock(BFTHeader.class));
		when(header.getOpaque3()).thenReturn(mock(HashCode.class));
		when(header.getLedgerHeader()).thenReturn(mock(LedgerHeader.class));
		when(header.getSignatures()).thenReturn(mock(TimestampedECDSASignatures.class));
		when(request.getHeader()).thenReturn(header);
		BFTNode node = mock(BFTNode.class);
		VerifiedCommandsAndProof verifiedCommandsAndProof = mock(VerifiedCommandsAndProof.class);
		VerifiedLedgerHeaderAndProof verifiedHeader = mock(VerifiedLedgerHeaderAndProof.class);
		when(verifiedHeader.toDto()).thenReturn(header);
		when(verifiedCommandsAndProof.getHeader()).thenReturn(verifiedHeader);
		when(reader.getNextCommittedCommands(any())).thenReturn(verifiedCommandsAndProof);
		processor.syncRequestEventProcessor().process(node, SyncRequest.create(header));
		verify(syncResponseDispatcher, times(1)).dispatch(eq(node), any());
	}

	@Test
	public void when_remote_sync_request_and_unable__then_dont_do_anything() {
		SyncRequest request = mock(SyncRequest.class);
		DtoLedgerHeaderAndProof header = mock(DtoLedgerHeaderAndProof.class);
		when(header.getOpaque0()).thenReturn(mock(BFTHeader.class));
		when(header.getOpaque1()).thenReturn(mock(BFTHeader.class));
		when(header.getOpaque3()).thenReturn(mock(HashCode.class));
		when(header.getLedgerHeader()).thenReturn(mock(LedgerHeader.class));
		when(header.getSignatures()).thenReturn(mock(TimestampedECDSASignatures.class));
		when(request.getHeader()).thenReturn(header);
		processor.syncRequestEventProcessor().process(BFTNode.random(), SyncRequest.create(header));
		verify(syncResponseDispatcher, never()).dispatch(any(BFTNode.class), any());
	}

	@Test
	public void when_remote_sync_request_and_null_return__then_dont_do_anything() {
		DtoLedgerHeaderAndProof header = mock(DtoLedgerHeaderAndProof.class);
		when(header.getOpaque0()).thenReturn(mock(BFTHeader.class));
		when(header.getOpaque1()).thenReturn(mock(BFTHeader.class));
		when(header.getOpaque3()).thenReturn(mock(HashCode.class));
		when(header.getLedgerHeader()).thenReturn(mock(LedgerHeader.class));
		when(header.getSignatures()).thenReturn(mock(TimestampedECDSASignatures.class));
		processor.syncRequestEventProcessor().process(BFTNode.random(), SyncRequest.create(header));
		when(reader.getNextCommittedCommands(any())).thenReturn(null);
		verify(syncResponseDispatcher, never()).dispatch(any(BFTNode.class), any());
	}
}
