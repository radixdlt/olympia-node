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

package com.radixdlt.application.system.construction;

import static com.radixdlt.application.validators.scrypt.ValidatorUpdateRakeConstraintScrypt.RAKE_MAX;
import static com.radixdlt.atom.SubstateTypeId.*;

import com.google.common.collect.Streams;
import com.google.common.primitives.UnsignedBytes;
import com.radixdlt.application.system.scrypt.ValidatorScratchPad;
import com.radixdlt.application.system.state.EpochData;
import com.radixdlt.application.system.state.RoundData;
import com.radixdlt.application.system.state.ValidatorBFTData;
import com.radixdlt.application.system.state.ValidatorStakeData;
import com.radixdlt.application.tokens.DelegatedResourceInBucket;
import com.radixdlt.application.tokens.state.ExitingStake;
import com.radixdlt.application.tokens.state.PreparedStake;
import com.radixdlt.application.tokens.state.PreparedUnstakeOwnership;
import com.radixdlt.application.validators.state.ValidatorData;
import com.radixdlt.application.validators.state.ValidatorFeeCopy;
import com.radixdlt.application.validators.state.ValidatorOwnerCopy;
import com.radixdlt.application.validators.state.ValidatorRegisteredCopy;
import com.radixdlt.atom.SubstateTypeId;
import com.radixdlt.atom.TxBuilder;
import com.radixdlt.constraintmachine.SubstateIndex;
import com.radixdlt.crypto.ECPublicKey;
import com.radixdlt.identifiers.REAddr;
import com.radixdlt.utils.KeyComparator;
import com.radixdlt.utils.UInt256;
import java.nio.ByteBuffer;
import java.util.*;

public record EpochConstructionState(
    TxBuilder txBuilder,
    TreeMap<ECPublicKey, ValidatorScratchPad> validatorsToUpdate,
    RoundData closedRound,
    EpochData closingEpoch,
    TreeMap<ECPublicKey, TreeMap<REAddr, UInt256>> preparingStake,
    NextEpochConstructor constructor) {
  public static EpochConstructionState createState(
      NextEpochConstructor constructor, TxBuilder txBuilder) {
    return new EpochConstructionState(
        txBuilder,
        new TreeMap<>(KeyComparator.instance()),
        txBuilder.downSystem(RoundData.class),
        txBuilder.downSystem(EpochData.class),
        new TreeMap<>(KeyComparator.instance()),
        constructor);
  }

  public void upValidatorStakeData() {
    validatorsToUpdate.values().forEach(scratchPad -> txBuilder.up(scratchPad.toSubstate()));
  }

  public void finalizeConstruction() {
    txBuilder.up(new EpochData(closingEpoch.epoch() + 1));
    txBuilder.up(new RoundData(0, closedRound.timestamp()));
    txBuilder.end();
  }

  public void processExittingStake() {
    var index = exittingStakeIndex(closingEpoch());

    txBuilder
        .shutdownAll(index, EpochConstructionState::collectExittingStake)
        .forEach(stake -> txBuilder().up(stake.unlock()));
  }

  private ValidatorScratchPad stakeData(ECPublicKey publicKey) {
    return validatorsToUpdate.computeIfAbsent(
        publicKey, key -> new ValidatorScratchPad(txBuilder.down(ValidatorStakeData.class, key)));
  }

  private static SubstateIndex<ExitingStake> exittingStakeIndex(EpochData closingEpoch) {
    var unlockedStateIndex =
        ByteBuffer.allocate(2 + Long.BYTES)
            .put(SubstateTypeId.EXITING_STAKE.id())
            .put((byte) 0)
            .putLong(closingEpoch.epoch() + 1)
            .array();

    return SubstateIndex.create(unlockedStateIndex, ExitingStake.class);
  }

  private static TreeSet<ExitingStake> collectExittingStake(Iterator<ExitingStake> iterator) {
    var exit =
        new TreeSet<>(
            Comparator.comparing(ExitingStake::dataKey, UnsignedBytes.lexicographicalComparator()));
    iterator.forEachRemaining(exit::add);
    return exit;
  }

  public void processEmission() {
    txBuilder
        .shutdownAll(ValidatorBFTData.class, EpochConstructionState::collectToMap)
        .forEach(this::calculateEmission);
  }

  private void calculateEmission(ECPublicKey publicKey, ValidatorBFTData bftData) {
    if (bftData.hasNoProcessedProposals()) {
      return;
    }

    var percentageCompleted = bftData.percentageCompleted();

    // Didn't pass threshold, no rewards!
    if (percentageCompleted < constructor.minimumCompletedProposalsPercentage()) {
      return;
    }

    var nodeRewards = bftData.calculateRewards(constructor.rewardsPerProposal());

    if (nodeRewards.isZero()) {
      return;
    }

    var validatorStakeData = stakeData(publicKey);
    int rakePercentage = validatorStakeData.getRakePercentage();
    final UInt256 rakedEmissions;

    if (rakePercentage != 0) {
      var rake = nodeRewards.multiply(UInt256.from(rakePercentage)).divide(UInt256.from(RAKE_MAX));
      var validatorOwner = validatorStakeData.getOwnerAddr();

      var initStake = createStakeMap();
      initStake.put(validatorOwner, rake);

      preparingStake.put(publicKey, initStake);
      rakedEmissions = nodeRewards.subtract(rake);
    } else {
      rakedEmissions = nodeRewards;
    }
    validatorStakeData.addEmission(rakedEmissions);
  }

  public void processPreparedUnstake() {
    txBuilder
        .shutdownAll(PreparedUnstakeOwnership.class, EpochConstructionState::collectUnstake)
        .forEach(
            (publicKey, unstakes) -> {
              var curValidator = stakeData(publicKey);

              unstakes.forEach(
                  (owner, amount) -> {
                    var epochUnlocked =
                        closingEpoch().epoch() + 1 + constructor.unstakingEpochDelay();
                    txBuilder().up(curValidator.unstakeOwnership(owner, amount, epochUnlocked));
                  });

              validatorsToUpdate().put(publicKey, curValidator);
            });
  }

  private static TreeMap<ECPublicKey, TreeMap<REAddr, UInt256>> collectUnstake(
      Iterator<PreparedUnstakeOwnership> iterator) {
    return collectStake(iterator, new TreeMap<>(KeyComparator.instance()));
  }

  public void processPreparedStake() {
    txBuilder
        .shutdownAll(PreparedStake.class, iterator -> collectStake(iterator, this.preparingStake()))
        .forEach(
            (key, stakes) -> {
              var curValidator = stakeData(key);

              stakes.forEach((owner, amount) -> txBuilder.up(curValidator.stake(owner, amount)));
              validatorsToUpdate().put(key, curValidator);
            });
  }

  private static TreeMap<ECPublicKey, TreeMap<REAddr, UInt256>> collectStake(
      Iterator<? extends DelegatedResourceInBucket> iterator,
      TreeMap<ECPublicKey, TreeMap<REAddr, UInt256>> map) {
    iterator.forEachRemaining(
        preparedStake ->
            map.computeIfAbsent(preparedStake.delegateKey(), __ -> createStakeMap())
                .merge(preparedStake.owner(), preparedStake.amount(), UInt256::add));

    return map;
  }

  private static TreeMap<REAddr, UInt256> createStakeMap() {
    return new TreeMap<>(
        Comparator.comparing(REAddr::getBytes, UnsignedBytes.lexicographicalComparator()));
  }

  private <T extends ValidatorData> SubstateIndex<T> prepareIndex(
      Class<T> preparedClass, SubstateTypeId typeId) {
    var prefix =
        ByteBuffer.allocate(3 + Long.BYTES)
            .put(typeId.id())
            .put((byte) 0) // Reserved byte
            .put((byte) 1) // Optional flag
            .putLong(closingEpoch().epoch() + 1)
            .array();

    return SubstateIndex.create(prefix, preparedClass);
  }

  private static <T extends ValidatorData> TreeMap<ECPublicKey, T> collectToMap(
      Iterator<T> iterator) {
    var map = new TreeMap<ECPublicKey, T>(KeyComparator.instance());
    iterator.forEachRemaining(update -> map.put(update.validatorKey(), update));
    return map;
  }

  public void processUpdateRake() {
    var index = prepareIndex(ValidatorFeeCopy.class, VALIDATOR_RAKE_COPY);

    txBuilder
        .shutdownAll(index, EpochConstructionState::collectToMap)
        .forEach(
            (key, update) -> {
              var curValidator = stakeData(key);
              curValidator.setRakePercentage(update.curRakePercentage());
              this.txBuilder()
                  .up(
                      new ValidatorFeeCopy(
                          OptionalLong.empty(), update.validatorKey(), update.curRakePercentage()));
            });
  }

  public void processUpdateOwners() {
    var index = prepareIndex(ValidatorOwnerCopy.class, VALIDATOR_OWNER_COPY);

    txBuilder
        .shutdownAll(index, EpochConstructionState::collectToMap)
        .forEach(
            (key, update) -> {
              var curValidator = stakeData(key);
              curValidator.setOwnerAddr(update.owner());
              this.txBuilder()
                  .up(
                      new ValidatorOwnerCopy(
                          OptionalLong.empty(), update.validatorKey(), update.owner()));
            });
  }

  public void processUpdateRegisteredFlag() {
    var index = prepareIndex(ValidatorRegisteredCopy.class, VALIDATOR_REGISTERED_FLAG_COPY);

    txBuilder
        .shutdownAll(index, EpochConstructionState::collectToMap)
        .forEach(
            (key, update) -> {
              var curValidator = stakeData(key);
              curValidator.setRegistered(update.isRegistered());

              // -------------------------------------------
              // TODO: create different versions of the object, depending on the jailing data
              // -------------------------------------------

              this.txBuilder()
                  .up(
                      ValidatorRegisteredCopy.createV1(
                          OptionalLong.empty(), update.validatorKey(), update.isRegistered()));
            });
  }

  public void prepareNextValidatorSetV3() {
    var index =
        SubstateIndex.create(
            new byte[] {SubstateTypeId.VALIDATOR_STAKE_DATA.id(), 0, 1}, ValidatorStakeData.class);

    try (var cursor = txBuilder().readIndex(index, true)) {
      // TODO: Explicitly specify next validatorset
      Streams.stream(cursor)
          .map(ValidatorStakeData.class::cast)
          .limit(constructor.maxValidators())
          .filter(s -> !s.totalStake().isZero())
          .forEach(v -> txBuilder().up(new ValidatorBFTData(v.validatorKey(), 0, 0)));
    }
  }
}
