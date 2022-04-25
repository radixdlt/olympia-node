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

package com.radixdlt.monitoring;

import static com.radixdlt.atom.SubstateTypeId.VALIDATOR_BFT_DATA;

import com.google.inject.Inject;
import com.radixdlt.application.system.state.ValidatorBFTData;
import com.radixdlt.consensus.LedgerProof;
import com.radixdlt.consensus.QuorumCertificate;
import com.radixdlt.consensus.bft.*;
import com.radixdlt.consensus.epoch.EpochChange;
import com.radixdlt.consensus.epoch.EpochView;
import com.radixdlt.consensus.liveness.EpochLocalTimeoutOccurrence;
import com.radixdlt.constraintmachine.REEvent.ValidatorBFTDataEvent;
import com.radixdlt.constraintmachine.SystemMapKey;
import com.radixdlt.engine.RadixEngine;
import com.radixdlt.environment.EventProcessor;
import com.radixdlt.ledger.LedgerUpdate;
import com.radixdlt.statecomputer.LedgerAndBFTProof;
import com.radixdlt.statecomputer.REOutput;
import com.radixdlt.store.LastEpochProof;
import com.radixdlt.store.LastProof;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicMarkableReference;
import java.util.concurrent.atomic.AtomicReference;

/** Manages system information to be consumed by clients such as the api. */
public final class InMemorySystemInfo {
  private final AtomicReference<EpochLocalTimeoutOccurrence> lastTimeout = new AtomicReference<>();
  private final AtomicReference<EpochView> currentView =
      new AtomicReference<>(EpochView.of(0L, View.genesis()));
  private final AtomicReference<QuorumCertificate> highQC = new AtomicReference<>();
  private final AtomicMarkableReference<Optional<ValidatorBFTDataEvent>> missedProposals =
      new AtomicMarkableReference<>(Optional.empty(), false);
  private final AtomicReference<LedgerProof> ledgerProof;
  private final AtomicReference<LedgerProof> epochsLedgerProof;
  private final BFTNode self;
  private final RadixEngine<LedgerAndBFTProof> radixEngine;

  @Inject
  public InMemorySystemInfo(
      @LastProof LedgerProof lastProof,
      @LastEpochProof LedgerProof lastEpochProof,
      @Self BFTNode self,
      RadixEngine<LedgerAndBFTProof> radixEngine) {
    this.ledgerProof = new AtomicReference<>(lastProof);
    this.epochsLedgerProof = new AtomicReference<>(lastEpochProof);
    this.self = self;
    this.radixEngine = radixEngine;
  }

  public void processTimeout(EpochLocalTimeoutOccurrence timeout) {
    lastTimeout.set(timeout);
  }

  public void processView(EpochView epochView) {
    currentView.set(epochView);
  }

  public EventProcessor<LedgerUpdate> ledgerUpdateEventProcessor() {
    return update -> {
      this.ledgerProof.set(update.getTail());
      var epochChange = update.getStateComputerOutput().getInstance(EpochChange.class);
      if (epochChange != null) {
        epochsLedgerProof.set(update.getTail());
      }

      update.getStateComputerOutput().getInstance(REOutput.class).getProcessedTxns().stream()
          .flatMap(processedTxn -> processedTxn.getEvents().stream())
          .filter(ValidatorBFTDataEvent.class::isInstance)
          .map(ValidatorBFTDataEvent.class::cast)
          .filter(event -> event.validatorKey().equals(self.getKey()))
          .map(Optional::of)
          .forEach(event -> missedProposals.set(event, true));
    };
  }

  public Optional<ValidatorBFTDataEvent> getValidatorBFTData() {
    if (!missedProposals.isMarked()) {
      // There were no relevant events yet
      missedProposals.set(getProposalStats(), true);
    }

    return missedProposals.getReference();
  }

  private Optional<ValidatorBFTDataEvent> getProposalStats() {
    var validatorBFTKey =
        SystemMapKey.ofSystem(VALIDATOR_BFT_DATA.id(), self.getKey().getCompressedBytes());

    return radixEngine.read(
        reader ->
            reader
                .get(validatorBFTKey)
                .map(ValidatorBFTData.class::cast)
                .map(ValidatorBFTDataEvent::fromData));
  }

  public EventProcessor<BFTHighQCUpdate> bftHighQCEventProcessor() {
    return update -> this.highQC.set(update.getHighQC().highestQC());
  }

  public EventProcessor<BFTCommittedUpdate> bftCommittedUpdateEventProcessor() {
    return update -> {
      this.highQC.set(update.vertexStoreState().getHighQC().highestQC());
    };
  }

  public LedgerProof getCurrentProof() {
    return ledgerProof.get();
  }

  public LedgerProof getEpochProof() {
    return epochsLedgerProof.get();
  }

  public EpochView getCurrentView() {
    return this.currentView.get();
  }

  public EpochLocalTimeoutOccurrence getLastTimeout() {
    return this.lastTimeout.get();
  }

  public QuorumCertificate getHighestQC() {
    return this.highQC.get();
  }
}
