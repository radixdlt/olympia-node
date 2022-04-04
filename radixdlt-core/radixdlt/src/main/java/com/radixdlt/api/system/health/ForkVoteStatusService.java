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

package com.radixdlt.api.system.health;

import com.google.common.collect.Streams;
import com.google.common.hash.HashCode;
import com.google.inject.Inject;
import com.radixdlt.application.validators.state.ValidatorSystemMetadata;
import com.radixdlt.atom.SubstateTypeId;
import com.radixdlt.consensus.bft.BFTNode;
import com.radixdlt.consensus.bft.Self;
import com.radixdlt.constraintmachine.SubstateIndex;
import com.radixdlt.serialization.DeserializeException;
import com.radixdlt.statecomputer.LedgerAndBFTProof;
import com.radixdlt.statecomputer.forks.CandidateForkVote;
import com.radixdlt.statecomputer.forks.CurrentForkView;
import com.radixdlt.statecomputer.forks.Forks;
import com.radixdlt.store.EngineStore;
import java.util.Objects;

public final class ForkVoteStatusService {
  public enum ForkVoteStatus {
    VOTE_REQUIRED,
    NO_ACTION_NEEDED
  }

  private final BFTNode self;
  private final EngineStore<LedgerAndBFTProof> engineStore;
  private final Forks forks;
  private final CurrentForkView currentForkView;

  @Inject
  public ForkVoteStatusService(
      @Self BFTNode self,
      EngineStore<LedgerAndBFTProof> engineStore,
      Forks forks,
      CurrentForkView currentForkView) {
    this.self = Objects.requireNonNull(self);
    this.engineStore = Objects.requireNonNull(engineStore);
    this.forks = Objects.requireNonNull(forks);
    this.currentForkView = Objects.requireNonNull(currentForkView);
  }

  public ForkVoteStatus forkVoteStatus() {
    final var currentFork = currentForkView.currentForkConfig();

    if (forks.getCandidateFork().isEmpty()
        || forks.getCandidateFork().get().name().equals(currentFork.name())) {
      return ForkVoteStatus.NO_ACTION_NEEDED;
    }

    final var expectedCandidateForkVote =
        CandidateForkVote.create(self.getKey(), forks.getCandidateFork().get());

    final var substateDeserialization =
        currentFork.engineRules().parser().getSubstateDeserialization();

    try (var validatorMetadataCursor =
        engineStore.openIndexedCursor(
            SubstateIndex.create(
                SubstateTypeId.VALIDATOR_SYSTEM_META_DATA.id(), ValidatorSystemMetadata.class))) {

      final var maybeSelfForkVote =
          Streams.stream(validatorMetadataCursor)
              .map(
                  s -> {
                    try {
                      return (ValidatorSystemMetadata)
                          substateDeserialization.deserialize(s.getData());
                    } catch (DeserializeException e) {
                      throw new IllegalStateException(
                          "Failed to deserialize ValidatorMetaData substate");
                    }
                  })
              .filter(vm -> vm.validatorKey().equals(self.getKey()))
              .findAny()
              .map(substate -> HashCode.fromBytes(substate.data()))
              .map(CandidateForkVote::new);

      return maybeSelfForkVote.filter(expectedCandidateForkVote::equals).isPresent()
          ? ForkVoteStatus.NO_ACTION_NEEDED
          : ForkVoteStatus.VOTE_REQUIRED;
    }
  }
}
