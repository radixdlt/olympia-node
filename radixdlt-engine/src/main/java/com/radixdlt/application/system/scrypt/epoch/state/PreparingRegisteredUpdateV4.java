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

package com.radixdlt.application.system.scrypt.epoch.state;

import static com.radixdlt.application.system.construction.epoch.v4.EpochConstructionStateV4.*;

import com.radixdlt.application.system.construction.epoch.v4.StakeAccumulator;
import com.radixdlt.application.system.scrypt.EpochUpdateConfig;
import com.radixdlt.application.system.scrypt.ValidatorScratchPad;
import com.radixdlt.application.validators.state.ValidatorRegisteredCopy;
import com.radixdlt.application.validators.state.ValidatorRegisteredCopy.ValidatorRegisteredCopyV2;
import com.radixdlt.constraintmachine.IndexedSubstateIterator;
import com.radixdlt.constraintmachine.ReducerState;
import com.radixdlt.constraintmachine.exceptions.ProcedureException;
import com.radixdlt.crypto.ECPublicKey;
import com.radixdlt.utils.KeyComparator;
import java.util.NavigableMap;
import java.util.TreeMap;
import java.util.TreeSet;

public final class PreparingRegisteredUpdateV4 extends ExpectedEpochChecker {
  private final NavigableMap<ECPublicKey, ValidatorScratchPad> validatorsScratchPad;
  private final NavigableMap<ECPublicKey, ValidatorRegisteredCopy> preparingRegisteredUpdates =
      new TreeMap<>(KeyComparator.instance());

  private ProcessingPhase phase = ProcessingPhase.START;

  private enum ProcessingPhase {
    START,
    VALIDATE_REGISTERED,
    EXIT
  }

  PreparingRegisteredUpdateV4(
      EpochUpdateConfig config,
      UpdatingEpoch updatingEpoch,
      NavigableMap<ECPublicKey, ValidatorScratchPad> validatorsScratchPad) {
    super(config, updatingEpoch);
    this.validatorsScratchPad = validatorsScratchPad;
  }

  public ReducerState prepareRegisterUpdates(
      IndexedSubstateIterator<ValidatorRegisteredCopy> indexedSubstateIterator)
      throws ProcedureException {

    verifyPrefix(indexedSubstateIterator);

    indexedSubstateIterator.forEachRemaining(
        preparedRegisteredUpdate ->
            preparingRegisteredUpdates.put(
                preparedRegisteredUpdate.validatorKey(), preparedRegisteredUpdate));
    return next();
  }

  ReducerState next() {
    return switch (phase) {
      case START -> loadStakes();
      case VALIDATE_REGISTERED -> validateRegistered();
      case EXIT -> exitToNextState();
    };
  }

  private ReducerState loadStakes() {
    var allKnownKeys = new TreeSet<>(validatorsScratchPad.keySet());

    allKnownKeys.removeAll(preparingRegisteredUpdates.keySet());

    if (allKnownKeys.isEmpty()) {
      processSentencing();
      phase = ProcessingPhase.VALIDATE_REGISTERED;
      return next();
    }

    var publicKey = allKnownKeys.first();

    return new LoadingStake(
        publicKey,
        validatorStake -> {
          validatorsScratchPad.put(publicKey, validatorStake);
          return next();
        });
  }

  private void processSentencing() {
    // Load jailing data
    loadSentencingData();

    var epoch = getEpoch();
    var totalValidatingStake = StakeAccumulator.create();
    var eligibleJailedStake = StakeAccumulator.create();

    updateSentencingDataAndCalculateStakes(epoch, totalValidatingStake, eligibleJailedStake);
    markAsJailed(totalValidatingStake, eligibleJailedStake);
  }

  private void markAsJailed(
      StakeAccumulator totalValidatingStake, StakeAccumulator eligibleJailedStake) {
    validatorsScratchPad.values().stream()
        .filter(ValidatorScratchPad::isJailingCandidate)
        .sorted(MISSED_PROPOSALS_COMPARATOR)
        .forEach(
            candidate -> jailSingleValidator(candidate, totalValidatingStake, eligibleJailedStake));
  }

  private void jailSingleValidator(
      ValidatorScratchPad data,
      StakeAccumulator totalValidatingStake,
      StakeAccumulator eligibleJailedStake) {
    var rate =
        eligibleJailedStake
            .value()
            .add(data.totalStake())
            .divide(totalValidatingStake.value().subtract(data.totalStake()));

    if (rate.compareTo(RATE_LIMIT) > 0) {
      // Jailed stake exceeds limit, ignore jailing
      return;
    }

    data.setRegistered(false);
    data.getSentencing().jail(getEpoch());

    eligibleJailedStake.add(data.totalStake());
    totalValidatingStake.subtract(data.totalStake());
  }

  private long getEpoch() {
    // TODO: Check if +1L is necessary
    return updatingEpoch().prevEpoch().epoch();
  }

  private void updateSentencingDataAndCalculateStakes(
      long epoch, StakeAccumulator totalValidatingStake, StakeAccumulator eligibleJailedStake) {
    for (var scratchPad : validatorsScratchPad.values()) {
      var wasRegistered = scratchPad.isRegistered();
      totalValidatingStake.add(scratchPad.totalStake());

      if (!scratchPad.isV2()) {
        // Ignore V1 updates
        continue;
      }

      if (wasRegistered) {
        scratchPad.getSentencing().decrementProbationEpochs();
      } else {
        var jailPeriodEnded = scratchPad.getSentencing().checkEndOfJail(epoch);

        // Check if validators' stake should be taken into account as eligible jailing stake.
        if (scratchPad.getSentencing().jailedEpoch() != 0L) {
          var jailedWithin = epoch - scratchPad.getSentencing().jailedEpoch();

          if (jailedWithin <= ELIGIBLE_JAILING_STAKE_INTERVAL) {
            // Validator is jailed within last 1500 epochs and still not registered
            eligibleJailedStake.add(scratchPad.totalStake());
          }
        }

        if (jailPeriodEnded) {
          scratchPad.getSentencing().leaveJail();

          if (scratchPad.getSentencing().registrationPending() || scratchPad.isNextRegistered()) {
            // Registration pending or was requested, mark for registration
            scratchPad.setRegistered(true);
          }
        } else if (scratchPad.isNextRegistered()) {
          // Can't register now, but should register later
          scratchPad.getSentencing().postponeRegistration();
        }
      }
    }
  }

  private void loadSentencingData() {
    for (var singleValidator : validatorsScratchPad.values()) {
      var update = preparingRegisteredUpdates.get(singleValidator.getValidatorKey());

      if (update instanceof ValidatorRegisteredCopyV2 v2) {
        // Prepare for jailing validation
        singleValidator.getSentencing().loadFrom(v2);
      } else {
        // Set directly, as there is no jailing
        singleValidator.setRegistered(update.isRegistered());
      }
    }
  }

  private ReducerState validateRegistered() {
    var publicKey = preparingRegisteredUpdates.firstKey();
    var validatorUpdate = preparingRegisteredUpdates.remove(publicKey);
    var validatorStake = validatorsScratchPad.get(publicKey);

    validatorStake.setNextRegistered(validatorUpdate.isRegistered());

    if (preparingRegisteredUpdates.isEmpty()) {
      phase = ProcessingPhase.EXIT;
    }

    return new ResetRegisteredUpdateV4(validatorUpdate, validatorStake, this::next);
  }

  private ReducerState exitToNextState() {
    return validatorsScratchPad.isEmpty()
        ? new CreatingNextValidatorSet(config(), updatingEpoch())
        : new UpdatingValidatorStakes(config(), updatingEpoch(), validatorsScratchPad);
  }
}
