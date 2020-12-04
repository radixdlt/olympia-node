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

package com.radixdlt.consensus.liveness;

import com.radixdlt.consensus.HighQC;
import com.radixdlt.consensus.QuorumCertificate;
import com.radixdlt.consensus.bft.BFTNode;
import com.radixdlt.consensus.bft.View;
import com.radixdlt.consensus.bft.ViewUpdate;
import com.radixdlt.environment.EventDispatcher;
import org.junit.Before;
import org.junit.Test;

import static com.radixdlt.utils.TypedMocks.rmock;
import static org.mockito.Mockito.*;
import static org.mockito.Mockito.times;

public class PacemakerStateTest {

	private EventDispatcher<ViewUpdate> viewUpdateSender = rmock(EventDispatcher.class);
	private ProposerElection proposerElection = mock(ProposerElection.class);

	private PacemakerState pacemakerState;

	@Before
	public void setUp() {
		when(proposerElection.getProposer(any())).thenReturn(BFTNode.random());
		this.pacemakerState = new PacemakerState(this.proposerElection, this.viewUpdateSender);
	}

	@Test
	public void when_process_qc_for_wrong_view__then_ignored() {
		HighQC highQC = mock(HighQC.class);
		QuorumCertificate qc = mock(QuorumCertificate.class);
		when(qc.getView()).thenReturn(View.of(1));
		when(highQC.highestQC()).thenReturn(qc);
		when(highQC.highestCommittedQC()).thenReturn(qc);

		// Move ahead for a bit so we can send in a QC for a lower view
		this.pacemakerState.processQC(highQCFor(View.of(0)));
		this.pacemakerState.processQC(highQCFor(View.of(1)));
		this.pacemakerState.processQC(highQCFor(View.of(2)));

		verify(viewUpdateSender, times(1))
			.dispatch(argThat(v -> v.getCurrentView().equals(View.of(1))));
		verify(viewUpdateSender, times(1))
			.dispatch(argThat(v -> v.getCurrentView().equals(View.of(2))));
		verify(viewUpdateSender, times(1))
			.dispatch(argThat(v -> v.getCurrentView().equals(View.of(3))));

		this.pacemakerState.processQC(highQC);
		verifyNoMoreInteractions(viewUpdateSender);
	}

	@Test
	public void when_process_qc_for_current_view__then_processed() {
		HighQC highQC = mock(HighQC.class);
		QuorumCertificate qc = mock(QuorumCertificate.class);
		when(qc.getView()).thenReturn(View.of(0));
		when(highQC.highestQC()).thenReturn(qc);
		when(highQC.highestCommittedQC()).thenReturn(qc);

		this.pacemakerState.processQC(highQC);
		verify(viewUpdateSender, times(1))
			.dispatch(argThat(v -> v.getCurrentView().equals(View.of(1))));

		when(qc.getView()).thenReturn(View.of(1));
		this.pacemakerState.processQC(highQC);
		verify(viewUpdateSender, times(1))
			.dispatch(argThat(v -> v.getCurrentView().equals(View.of(2))));
	}

    @Test
    public void when_process_qc_with_a_high_tc__then_should_move_to_tc_view() {
        HighQC highQC = mock(HighQC.class);
        QuorumCertificate qc = mock(QuorumCertificate.class);
        when(qc.getView()).thenReturn(View.of(3));
        when(highQC.getHighestView()).thenReturn(View.of(5));
        when(highQC.highestCommittedQC()).thenReturn(qc);

        this.pacemakerState.processQC(highQC);
        verify(viewUpdateSender, times(1))
			.dispatch(argThat(v -> v.getCurrentView().equals(View.of(6))));
    }

    private HighQC highQCFor(View view) {
		HighQC highQC = mock(HighQC.class);
		QuorumCertificate hqc = mock(QuorumCertificate.class);
		QuorumCertificate cqc = mock(QuorumCertificate.class);
		when(hqc.getView()).thenReturn(view);
		when(cqc.getView()).thenReturn(View.of(0));
		when(highQC.highestQC()).thenReturn(hqc);
		when(highQC.highestCommittedQC()).thenReturn(cqc);
		return highQC;
	}
}
