/* Copyright 2021 Radix Publishing Ltd incorporated in Jersey (Channel Islands).
 *
 * Licensed under the Radix License, Version 1.0 (the "License"); you may not use this
 * file except in compliance with the License. You may obtain a copy of the License at:
 *
 * radixfoundation.org/licenses/LICENSE-v1
 *
 * The Licensor hereby grants permission for the Canonical version of the Work to be
 * published, distributed and used under or by reference to the Licensor’s trademark
 * Radix ® and use of any unregistered trade names, logos or get-up.
 *
 * The Licensor provides the Work (and each Contributor provides its Contributions) on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied,
 * including, without limitation, any warranties or conditions of TITLE, NON-INFRINGEMENT,
 * MERCHANTABILITY, or FITNESS FOR A PARTICULAR PURPOSE.
 *
 * Whilst the Work is capable of being deployed, used and adopted (instantiated) to create
 * a distributed ledger it is your responsibility to test and validate the code, together
 * with all logic and performance of that code under all foreseeable scenarios.
 *
 * The Licensor does not make or purport to make and hereby excludes liability for all
 * and any representation, warranty or undertaking in any form whatsoever, whether express
 * or implied, to any entity or person, including any representation, warranty or
 * undertaking, as to the functionality security use, value or other characteristics of
 * any distributed ledger nor in respect the functioning or value of any tokens which may
 * be created stored or transferred using the Work. The Licensor does not warrant that the
 * Work or any use of the Work complies with any law or regulation in any territory where
 * it may be implemented or used or that it will be appropriate for any specific purpose.
 *
 * Neither the licensor nor any current or former employees, officers, directors, partners,
 * trustees, representatives, agents, advisors, contractors, or volunteers of the Licensor
 * shall be liable for any direct or indirect, special, incidental, consequential or other
 * losses of any kind, in tort, contract or otherwise (including but not limited to loss
 * of revenue, income or profits, or loss of use or data, or loss of reputation, or loss
 * of any economic or other opportunity of whatsoever nature or howsoever arising), arising
 * out of or in connection with (without limitation of any use, misuse, of any ledger system
 * or use made or its functionality or any performance or operation of any code or protocol
 * caused by bugs or programming or logic errors or otherwise);
 *
 * A. any offer, purchase, holding, use, sale, exchange or transmission of any
 * cryptographic keys, tokens or assets created, exchanged, stored or arising from any
 * interaction with the Work;
 *
 * B. any failure in a transmission or loss of any token or assets keys or other digital
 * artefacts due to errors in transmission;
 *
 * C. bugs, hacks, logic errors or faults in the Work or any communication;
 *
 * D. system software or apparatus including but not limited to losses caused by errors
 * in holding or transmitting tokens by any third-party;
 *
 * E. breaches or failure of security including hacker attacks, loss or disclosure of
 * password, loss of private key, unauthorised use or misuse of such passwords or keys;
 *
 * F. any losses including loss of anticipated savings or other benefits resulting from
 * use of the Work or any changes to the Work (however implemented).
 *
 * You are solely responsible for; testing, validating and evaluation of all operation
 * logic, functionality, security and appropriateness of using the Work for any commercial
 * or non-commercial purpose and for any reproduction or redistribution by You of the
 * Work. You assume all risks associated with Your use of the Work and the exercise of
 * permissions under this License.
 */

package com.radixdlt.consensus.liveness;

import static com.radixdlt.utils.TypedMocks.rmock;
import static org.mockito.Mockito.*;
import static org.mockito.Mockito.times;

import com.radixdlt.consensus.HighQC;
import com.radixdlt.consensus.QuorumCertificate;
import com.radixdlt.consensus.bft.BFTNode;
import com.radixdlt.consensus.bft.View;
import com.radixdlt.consensus.bft.ViewUpdate;
import com.radixdlt.environment.EventDispatcher;
import org.junit.Before;
import org.junit.Test;

public class PacemakerStateTest {

  private EventDispatcher<ViewUpdate> viewUpdateSender = rmock(EventDispatcher.class);
  private ProposerElection proposerElection = mock(ProposerElection.class);

  private PacemakerState pacemakerState;

  @Before
  public void setUp() {
    when(proposerElection.getProposer(any())).thenReturn(BFTNode.random());
    ViewUpdate viewUpdate =
        ViewUpdate.create(View.genesis(), mock(HighQC.class), BFTNode.random(), BFTNode.random());
    this.pacemakerState =
        new PacemakerState(viewUpdate, this.proposerElection, this.viewUpdateSender);
  }

  @Test
  public void when_process_qc_for_wrong_view__then_ignored() {
    HighQC highQC = mock(HighQC.class);
    when(highQC.getHighestView()).thenReturn(View.of(1));

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
    when(highQC.getHighestView()).thenReturn(View.of(0));

    this.pacemakerState.processQC(highQC);
    verify(viewUpdateSender, times(1))
        .dispatch(argThat(v -> v.getCurrentView().equals(View.of(1))));

    when(highQC.getHighestView()).thenReturn(View.of(1));
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
    when(highQC.getHighestView()).thenReturn(view);
    return highQC;
  }
}
