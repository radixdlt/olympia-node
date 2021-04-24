/*
 * (C) Copyright 2021 Radix DLT Ltd
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

import com.radixdlt.consensus.BFTEventProcessor;
import com.radixdlt.consensus.BFTHeader;
import com.radixdlt.consensus.HighQC;
import com.radixdlt.consensus.LedgerHeader;
import com.radixdlt.consensus.LedgerProof;
import com.radixdlt.consensus.bft.BFTSyncer.SyncResult;
import com.radixdlt.consensus.Proposal;
import com.radixdlt.consensus.QuorumCertificate;
import org.junit.Before;
import org.junit.Test;

import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class BFTEventPreprocessorTest {

	private BFTEventPreprocessor bftEventPreprocessor;

	private final BFTEventProcessor forwardTo = mock(BFTEventProcessor.class);
	private final BFTSyncer bftSyncer = mock(BFTSyncer.class);
	private final ViewUpdate initialViewUpdate = mock(ViewUpdate.class);

	@Before
	public void setUp() {
		this.bftEventPreprocessor = new BFTEventPreprocessor(forwardTo, bftSyncer, initialViewUpdate);
	}

	@Test
	public void when_view_update__then_should_process_cached_events() {
		final var proposal = mock(Proposal.class);
		final var proposalHighQc = mock(HighQC.class);
		final var proposalHighestCommittedQc = mock(QuorumCertificate.class);
		final var proposalLedgerProof = mock(LedgerProof.class);
		when(initialViewUpdate.getCurrentView()).thenReturn(View.of(2));
		when(proposal.getView()).thenReturn(View.of(4));
		when(proposal.highQC()).thenReturn(proposalHighQc);
		when(proposalHighQc.highestCommittedQC()).thenReturn(proposalHighestCommittedQc);
		var header = mock(BFTHeader.class);
		when(header.getLedgerHeader()).thenReturn(mock(LedgerHeader.class));
		when(proposalHighestCommittedQc.getCommitted()).thenReturn(Optional.of(header));
		when(proposalLedgerProof.isEndOfEpoch()).thenReturn(false);
		when(bftSyncer.syncToQC(any(), any())).thenReturn(SyncResult.IN_PROGRESS);

		// we're at v2, proposal for v4 should get cached as sync returns IN_PROGRESS
		this.bftEventPreprocessor.processProposal(proposal);

		final var newViewUpdate = mock(ViewUpdate.class);
		when(newViewUpdate.getCurrentView()).thenReturn(View.of(4));
		when(bftSyncer.syncToQC(any(), any())).thenReturn(SyncResult.SYNCED);

		// we're going straight to v4, cached proposal should get processed
		this.bftEventPreprocessor.processViewUpdate(newViewUpdate);
		verify(forwardTo, times(1)).processProposal(proposal);
	}
}
