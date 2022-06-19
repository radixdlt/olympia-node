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

package com.radixdlt.application.system.scrypt.epoch.state.v3;

import static com.radixdlt.application.validators.scrypt.ValidatorUpdateRakeConstraintScrypt.RAKE_MAX;

import com.radixdlt.application.system.scrypt.EpochUpdateConfig;
import com.radixdlt.application.system.scrypt.EpochUpdateConstraintScryptV3;
import com.radixdlt.application.system.scrypt.ValidatorScratchPad;
import com.radixdlt.application.system.scrypt.epoch.state.LoadingStake;
import com.radixdlt.application.system.scrypt.epoch.state.UpdatingEpoch;
import com.radixdlt.application.system.state.ValidatorBFTData;
import com.radixdlt.constraintmachine.ExecutionContext;
import com.radixdlt.constraintmachine.IndexedSubstateIterator;
import com.radixdlt.constraintmachine.REEvent.ValidatorBFTDataEvent;
import com.radixdlt.constraintmachine.ReducerState;
import com.radixdlt.constraintmachine.exceptions.ProcedureException;
import com.radixdlt.crypto.ECPublicKey;
import com.radixdlt.identifiers.REAddr;
import com.radixdlt.utils.KeyComparator;
import com.radixdlt.utils.UInt256;
import java.util.NavigableMap;
import java.util.TreeMap;

public final class RewardingValidatorsV3 implements ReducerState {
  private final NavigableMap<ECPublicKey, ValidatorScratchPad> updatingValidators =
      new TreeMap<>(KeyComparator.instance());
  private final NavigableMap<ECPublicKey, ValidatorBFTData> validatorBFTData =
      new TreeMap<>(KeyComparator.instance());
  private final NavigableMap<ECPublicKey, NavigableMap<REAddr, UInt256>> preparingStake =
      new TreeMap<>(KeyComparator.instance());
  private final EpochUpdateConfig config;
  private final UpdatingEpoch updatingEpoch;

  RewardingValidatorsV3(EpochUpdateConfig config, UpdatingEpoch updatingEpoch) {
    this.config = config;
    this.updatingEpoch = updatingEpoch;
  }

  // TODO: Remove context
  public ReducerState process(
      IndexedSubstateIterator<ValidatorBFTData> iterator, ExecutionContext context)
      throws ProcedureException {

    iterator.verifyPostTypePrefixIsEmpty();

    iterator.forEachThrowing(
        validatorEpochData -> {
          if (validatorBFTData.containsKey(validatorEpochData.validatorKey())) {
            throw new ProcedureException("Already inserted " + validatorEpochData.validatorKey());
          }
          validatorBFTData.put(validatorEpochData.validatorKey(), validatorEpochData);
        });

    return next(context);
  }

  ReducerState next(ExecutionContext context) {
    if (validatorBFTData.isEmpty()) {
      return PreparingUnstakeV3.create(config, updatingEpoch, updatingValidators, preparingStake);
    }

    var publicKey = validatorBFTData.firstKey();

    if (updatingValidators.containsKey(publicKey)) {
      throw new IllegalStateException(
          "Inconsistent data, there should only be a single substate per validator");
    }

    var bftData = validatorBFTData.remove(publicKey);

    context.emitEvent(ValidatorBFTDataEvent.fromData(bftData));

    if (bftData.hasNoProcessedProposals()) {
      return next(context);
    }

    var percentageCompleted = bftData.percentageCompleted();

    // Didn't pass threshold, no rewards!
    if (percentageCompleted < config.minimumCompletedProposalsPercentage()) {
      return next(context);
    }

    var nodeRewards = bftData.calculateRewards(config.rewardsPerProposal());

    if (nodeRewards.isZero()) {
      return next(context);
    }

    return new LoadingStake(
        publicKey,
        validatorStakeData -> onDone(context, publicKey, nodeRewards, validatorStakeData));
  }

  private ReducerState onDone(
      ExecutionContext context,
      ECPublicKey publicKey,
      UInt256 nodeRewards,
      ValidatorScratchPad validatorStakeData) {
    var rakedEmissions = calculateRakedEmissions(publicKey, nodeRewards, validatorStakeData);

    validatorStakeData.addEmission(rakedEmissions);
    updatingValidators.put(publicKey, validatorStakeData);

    return next(context);
  }

  private UInt256 calculateRakedEmissions(
      ECPublicKey publicKey, UInt256 nodeRewards, ValidatorScratchPad validatorStakeData) {
    var rakePercentage = validatorStakeData.getRakePercentage();

    if (rakePercentage == 0) {
      return nodeRewards;
    }

    var rake = nodeRewards.multiply(UInt256.from(rakePercentage)).divide(UInt256.from(RAKE_MAX));
    var validatorOwner = validatorStakeData.getOwnerAddr();

    preparingStake.put(publicKey, createStakeMap(validatorOwner, rake));

    return nodeRewards.subtract(rake);
  }

  public static NavigableMap<REAddr, UInt256> createStakeMap(REAddr validatorOwner, UInt256 rake) {
    var map = new TreeMap<REAddr, UInt256>(EpochUpdateConstraintScryptV3.STAKE_COMPARATOR);

    map.put(validatorOwner, rake);

    return map;
  }
}
