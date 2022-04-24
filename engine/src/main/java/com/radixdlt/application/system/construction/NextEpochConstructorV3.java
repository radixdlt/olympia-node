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
import static com.radixdlt.atom.TxAction.*;

import com.google.common.collect.Streams;
import com.google.common.primitives.UnsignedBytes;
import com.radixdlt.application.system.scrypt.ValidatorScratchPad;
import com.radixdlt.application.system.state.EpochData;
import com.radixdlt.application.system.state.RoundData;
import com.radixdlt.application.system.state.ValidatorBFTData;
import com.radixdlt.application.system.state.ValidatorStakeData;
import com.radixdlt.application.tokens.state.ExitingStake;
import com.radixdlt.application.tokens.state.PreparedStake;
import com.radixdlt.application.tokens.state.PreparedUnstakeOwnership;
import com.radixdlt.application.validators.state.ValidatorData;
import com.radixdlt.application.validators.state.ValidatorFeeCopy;
import com.radixdlt.application.validators.state.ValidatorOwnerCopy;
import com.radixdlt.application.validators.state.ValidatorRegisteredCopy;
import com.radixdlt.atom.*;
import com.radixdlt.constraintmachine.SubstateIndex;
import com.radixdlt.crypto.ECPublicKey;
import com.radixdlt.identifiers.REAddr;
import com.radixdlt.utils.KeyComparator;
import com.radixdlt.utils.UInt256;
import java.nio.ByteBuffer;
import java.util.*;
import java.util.function.BiConsumer;
import java.util.function.Function;

public record NextEpochConstructorV3(
    UInt256 rewardsPerProposal,
    long minimumCompletedProposalsPercentage,
    long unstakingEpochDelay,
    int maxValidators)
    implements ActionConstructor<NextEpoch> {

  private static ValidatorScratchPad loadValidatorStakeData(
      TxBuilder txBuilder,
      ECPublicKey k,
      TreeMap<ECPublicKey, ValidatorScratchPad> validatorsToUpdate)
      throws TxBuilderException {
    var scratchPad = validatorsToUpdate.get(k);
    if (scratchPad == null) {
      var validatorData = txBuilder.down(ValidatorStakeData.class, k);
      scratchPad = new ValidatorScratchPad(validatorData);
      validatorsToUpdate.put(k, scratchPad);
    }

    return scratchPad;
  }

  private static <T extends ValidatorData, U extends ValidatorData> void prepare(
      TxBuilder txBuilder,
      TreeMap<ECPublicKey, ValidatorScratchPad> validatorsToUpdate,
      Class<T> preparedClass,
      byte typeId,
      long epoch,
      BiConsumer<ValidatorScratchPad, T> updater,
      Function<T, U> copy)
      throws TxBuilderException {
    var preparing = new TreeMap<ECPublicKey, T>(KeyComparator.instance());
    var buf = ByteBuffer.allocate(3 + Long.BYTES);
    buf.put(typeId);
    buf.put((byte) 0); // Reserved byte
    buf.put((byte) 1); // Optional flag
    buf.putLong(epoch);
    var index = SubstateIndex.create(buf.array(), preparedClass);
    txBuilder.shutdownAll(
        index,
        (Iterator<T> i) -> {
          i.forEachRemaining(update -> preparing.put(update.validatorKey(), update));
          return preparing;
        });
    for (var e : preparing.entrySet()) {
      var k = e.getKey();
      var update = e.getValue();
      var curValidator = loadValidatorStakeData(txBuilder, k, validatorsToUpdate);
      updater.accept(curValidator, update);
      txBuilder.up(copy.apply(update));
    }
  }

  @Override
  public void construct(NextEpoch action, TxBuilder txBuilder) throws TxBuilderException {
    var closedRound = txBuilder.downSystem(RoundData.class);
    var closingEpoch = txBuilder.downSystem(EpochData.class);

    var unlockedStateIndexBuf = ByteBuffer.allocate(2 + Long.BYTES);
    unlockedStateIndexBuf.put(SubstateTypeId.EXITING_STAKE.id());
    unlockedStateIndexBuf.put((byte) 0);
    unlockedStateIndexBuf.putLong(closingEpoch.epoch() + 1);
    var unlockedStakeIndex =
        SubstateIndex.create(unlockedStateIndexBuf.array(), ExitingStake.class);
    var exitting =
        txBuilder.shutdownAll(
            unlockedStakeIndex,
            (Iterator<ExitingStake> i) -> {
              final TreeSet<ExitingStake> exit =
                  new TreeSet<>(
                      Comparator.comparing(
                          ExitingStake::dataKey, UnsignedBytes.lexicographicalComparator()));
              i.forEachRemaining(exit::add);
              return exit;
            });
    for (var e : exitting) {
      txBuilder.up(e.unlock());
    }

    var validatorsToUpdate =
        new TreeMap<ECPublicKey, ValidatorScratchPad>(KeyComparator.instance());
    var validatorBFTData =
        txBuilder.shutdownAll(
            ValidatorBFTData.class,
            i -> {
              final TreeMap<ECPublicKey, ValidatorBFTData> bftData =
                  new TreeMap<>(KeyComparator.instance());
              i.forEachRemaining(e -> bftData.put(e.validatorKey(), e));
              return bftData;
            });
    var preparingStake =
        new TreeMap<ECPublicKey, TreeMap<REAddr, UInt256>>(KeyComparator.instance());
    for (var e : validatorBFTData.entrySet()) {
      var k = e.getKey();
      var bftData = e.getValue();
      if (bftData.completedProposals() + bftData.missedProposals() == 0) {
        continue;
      }
      var percentageCompleted =
          bftData.completedProposals()
              * 10000
              / (bftData.completedProposals() + bftData.missedProposals());

      // Didn't pass threshold, no rewards!
      if (percentageCompleted < minimumCompletedProposalsPercentage) {
        continue;
      }

      var nodeRewards = rewardsPerProposal.multiply(UInt256.from(bftData.completedProposals()));
      if (nodeRewards.isZero()) {
        continue;
      }

      var validatorStakeData = loadValidatorStakeData(txBuilder, k, validatorsToUpdate);
      int rakePercentage = validatorStakeData.getRakePercentage();
      final UInt256 rakedEmissions;
      if (rakePercentage != 0) {
        var rake =
            nodeRewards.multiply(UInt256.from(rakePercentage)).divide(UInt256.from(RAKE_MAX));
        var validatorOwner = validatorStakeData.getOwnerAddr();
        var initStake =
            new TreeMap<REAddr, UInt256>(
                Comparator.comparing(REAddr::getBytes, UnsignedBytes.lexicographicalComparator()));
        initStake.put(validatorOwner, rake);
        preparingStake.put(k, initStake);
        rakedEmissions = nodeRewards.subtract(rake);
      } else {
        rakedEmissions = nodeRewards;
      }
      validatorStakeData.addEmission(rakedEmissions);
    }

    var allPreparedUnstake =
        txBuilder.shutdownAll(
            PreparedUnstakeOwnership.class,
            i -> {
              var map =
                  new TreeMap<ECPublicKey, TreeMap<REAddr, UInt256>>(KeyComparator.instance());
              i.forEachRemaining(
                  preparedStake ->
                      map.computeIfAbsent(
                              preparedStake.delegateKey(),
                              k ->
                                  new TreeMap<>(
                                      Comparator.comparing(
                                          REAddr::getBytes,
                                          UnsignedBytes.lexicographicalComparator())))
                          .merge(preparedStake.owner(), preparedStake.amount(), UInt256::add));
              return map;
            });
    for (var e : allPreparedUnstake.entrySet()) {
      var k = e.getKey();
      var curValidator = loadValidatorStakeData(txBuilder, k, validatorsToUpdate);
      var unstakes = e.getValue();
      for (var entry : unstakes.entrySet()) {
        var addr = entry.getKey();
        var amt = entry.getValue();
        var epochUnlocked = closingEpoch.epoch() + 1 + unstakingEpochDelay;
        var exittingStake = curValidator.unstakeOwnership(addr, amt, epochUnlocked);
        txBuilder.up(exittingStake);
      }
      validatorsToUpdate.put(k, curValidator);
    }

    var allPreparedStake =
        txBuilder.shutdownAll(
            PreparedStake.class,
            i -> {
              i.forEachRemaining(
                  preparedStake ->
                      preparingStake
                          .computeIfAbsent(
                              preparedStake.delegateKey(),
                              k ->
                                  new TreeMap<>(
                                      Comparator.comparing(
                                          REAddr::getBytes,
                                          UnsignedBytes.lexicographicalComparator())))
                          .merge(preparedStake.owner(), preparedStake.amount(), UInt256::add));
              return preparingStake;
            });
    for (var e : allPreparedStake.entrySet()) {
      var k = e.getKey();
      var stakes = e.getValue();
      var curValidator = loadValidatorStakeData(txBuilder, k, validatorsToUpdate);
      for (var entry : stakes.entrySet()) {
        var addr = entry.getKey();
        var amt = entry.getValue();
        var stakeOwnership = curValidator.stake(addr, amt);
        txBuilder.up(stakeOwnership);
      }
      validatorsToUpdate.put(k, curValidator);
    }

    // Update rake
    prepare(
        txBuilder,
        validatorsToUpdate,
        ValidatorFeeCopy.class,
        SubstateTypeId.VALIDATOR_RAKE_COPY.id(),
        closingEpoch.epoch() + 1,
        (v, u) -> v.setRakePercentage(u.curRakePercentage()),
        u -> new ValidatorFeeCopy(OptionalLong.empty(), u.validatorKey(), u.curRakePercentage()));

    // Update owners
    prepare(
        txBuilder,
        validatorsToUpdate,
        ValidatorOwnerCopy.class,
        SubstateTypeId.VALIDATOR_OWNER_COPY.id(),
        closingEpoch.epoch() + 1,
        (v, u) -> v.setOwnerAddr(u.owner()),
        u -> new ValidatorOwnerCopy(OptionalLong.empty(), u.validatorKey(), u.owner()));

    // Update registered flag
    prepare(
        txBuilder,
        validatorsToUpdate,
        ValidatorRegisteredCopy.class,
        SubstateTypeId.VALIDATOR_REGISTERED_FLAG_COPY.id(),
        closingEpoch.epoch() + 1,
        (v, u) -> v.setRegistered(u.isRegistered()),
        u -> new ValidatorRegisteredCopy(OptionalLong.empty(), u.validatorKey(), u.isRegistered()));

    validatorsToUpdate.forEach((k, v) -> txBuilder.up(v.toSubstate()));

    try (var cursor =
        txBuilder.readIndex(
            SubstateIndex.create(
                new byte[] {SubstateTypeId.VALIDATOR_STAKE_DATA.id(), 0, 1},
                ValidatorStakeData.class),
            true)) {
      // TODO: Explicitly specify next validatorset
      Streams.stream(cursor)
          .map(ValidatorStakeData.class::cast)
          .limit(maxValidators)
          .filter(s -> !s.totalStake().isZero())
          .forEach(v -> txBuilder.up(new ValidatorBFTData(v.validatorKey(), 0, 0)));
    }
    txBuilder.up(new EpochData(closingEpoch.epoch() + 1));
    txBuilder.up(new RoundData(0, closedRound.timestamp()));
    txBuilder.end();
  }
}
