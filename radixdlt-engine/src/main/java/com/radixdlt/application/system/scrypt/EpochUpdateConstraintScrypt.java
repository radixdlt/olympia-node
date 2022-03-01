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

package com.radixdlt.application.system.scrypt;

import static com.radixdlt.application.validators.scrypt.ValidatorUpdateRakeConstraintScrypt.RAKE_MAX;

import com.google.common.collect.Streams;
import com.google.common.primitives.UnsignedBytes;
import com.radixdlt.application.system.state.EpochData;
import com.radixdlt.application.system.state.HasEpochData;
import com.radixdlt.application.system.state.RoundData;
import com.radixdlt.application.system.state.StakeOwnership;
import com.radixdlt.application.system.state.ValidatorBFTData;
import com.radixdlt.application.system.state.ValidatorStakeData;
import com.radixdlt.application.tokens.state.ExitingStake;
import com.radixdlt.application.tokens.state.PreparedStake;
import com.radixdlt.application.tokens.state.PreparedUnstakeOwnership;
import com.radixdlt.application.tokens.state.TokensInAccount;
import com.radixdlt.application.validators.state.ValidatorFeeCopy;
import com.radixdlt.application.validators.state.ValidatorOwnerCopy;
import com.radixdlt.application.validators.state.ValidatorRegisteredCopy;
import com.radixdlt.atom.REFieldSerialization;
import com.radixdlt.atom.SubstateTypeId;
import com.radixdlt.atomos.ConstraintScrypt;
import com.radixdlt.atomos.Loader;
import com.radixdlt.atomos.SubstateDefinition;
import com.radixdlt.constraintmachine.*;
import com.radixdlt.constraintmachine.REEvent.NextValidatorSetEvent;
import com.radixdlt.constraintmachine.REEvent.ValidatorBFTDataEvent;
import com.radixdlt.constraintmachine.exceptions.MismatchException;
import com.radixdlt.constraintmachine.exceptions.ProcedureException;
import com.radixdlt.crypto.ECPublicKey;
import com.radixdlt.identifiers.REAddr;
import com.radixdlt.utils.KeyComparator;
import com.radixdlt.utils.Longs;
import com.radixdlt.utils.UInt256;
import java.util.*;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collectors;

public final class EpochUpdateConstraintScrypt implements ConstraintScrypt {
  private final long maxRounds;
  private final UInt256 rewardsPerProposal;
  private final long unstakingEpochDelay;
  private int minimumCompletedProposalsPercentage;
  private int maxValidators;

  public EpochUpdateConstraintScrypt(
      long maxRounds,
      UInt256 rewardsPerProposal,
      int minimumCompletedProposalsPercentage,
      long unstakingEpochDelay,
      int maxValidators) {
    this.maxRounds = maxRounds;
    this.rewardsPerProposal = rewardsPerProposal;
    this.unstakingEpochDelay = unstakingEpochDelay;
    this.minimumCompletedProposalsPercentage = minimumCompletedProposalsPercentage;
    this.maxValidators = maxValidators;
  }

  public final class ProcessExittingStake implements ReducerState {
    private final UpdatingEpoch updatingEpoch;
    private final TreeSet<ExitingStake> exitting =
        new TreeSet<>(
            Comparator.comparing(ExitingStake::dataKey, UnsignedBytes.lexicographicalComparator()));

    ProcessExittingStake(UpdatingEpoch updatingEpoch) {
      this.updatingEpoch = updatingEpoch;
    }

    public ReducerState process(IndexedSubstateIterator<ExitingStake> indexedSubstateIterator)
        throws ProcedureException {
      var expectedEpoch = updatingEpoch.prevEpoch.epoch() + 1;
      var expectedPrefix = new byte[Long.BYTES + 1];
      Longs.copyTo(expectedEpoch, expectedPrefix, 1);
      indexedSubstateIterator.verifyPostTypePrefixEquals(expectedPrefix);
      indexedSubstateIterator
          .iterator()
          .forEachRemaining(
              e -> {
                // Sanity check
                if (e.epochUnlocked() != expectedEpoch) {
                  throw new IllegalStateException(
                      "Invalid shutdown of exitting stake update epoch expected "
                          + expectedEpoch
                          + " but was "
                          + e.epochUnlocked());
                }
                exitting.add(e);
              });
      return next();
    }

    public ReducerState unlock(TokensInAccount u) throws ProcedureException {
      var exit = exitting.first();
      exitting.remove(exit);
      if (exit.epochUnlocked() != updatingEpoch.prevEpoch.epoch() + 1) {
        throw new ProcedureException("Stake must still be locked.");
      }
      var expected = exit.unlock();
      if (!expected.equals(u)) {
        throw new ProcedureException("Expecting next state to be " + expected + " but was " + u);
      }

      return next();
    }

    public ReducerState next() {
      return exitting.isEmpty() ? new RewardingValidators(updatingEpoch) : this;
    }
  }

  private final class RewardingValidators implements ReducerState {
    private final TreeMap<ECPublicKey, ValidatorScratchPad> updatingValidators =
        new TreeMap<>(KeyComparator.instance());
    private final TreeMap<ECPublicKey, ValidatorBFTData> validatorBFTData =
        new TreeMap<>(KeyComparator.instance());
    private final TreeMap<ECPublicKey, TreeMap<REAddr, UInt256>> preparingStake =
        new TreeMap<>(KeyComparator.instance());
    private final UpdatingEpoch updatingEpoch;

    RewardingValidators(UpdatingEpoch updatingEpoch) {
      this.updatingEpoch = updatingEpoch;
    }

    // TODO: Remove context
    public ReducerState process(
        IndexedSubstateIterator<ValidatorBFTData> i, ExecutionContext context)
        throws ProcedureException {
      i.verifyPostTypePrefixIsEmpty();
      var iter = i.iterator();
      while (iter.hasNext()) {
        var validatorEpochData = iter.next();
        if (validatorBFTData.containsKey(validatorEpochData.validatorKey())) {
          throw new ProcedureException("Already inserted " + validatorEpochData.validatorKey());
        }
        validatorBFTData.put(validatorEpochData.validatorKey(), validatorEpochData);
      }

      return next(context);
    }

    ReducerState next(ExecutionContext context) {
      if (validatorBFTData.isEmpty()) {
        return new PreparingUnstake(updatingEpoch, updatingValidators, preparingStake);
      }

      var k = validatorBFTData.firstKey();
      if (updatingValidators.containsKey(k)) {
        throw new IllegalStateException(
            "Inconsistent data, there should only be a single substate per validator");
      }
      var bftData = validatorBFTData.remove(k);
      context.emitEvent(
          new ValidatorBFTDataEvent(k, bftData.completedProposals(), bftData.missedProposals()));
      if (bftData.completedProposals() + bftData.missedProposals() == 0) {
        return next(context);
      }

      var percentageCompleted =
          bftData.completedProposals()
              * 10000
              / (bftData.completedProposals() + bftData.missedProposals());

      // Didn't pass threshold, no rewards!
      if (percentageCompleted < minimumCompletedProposalsPercentage) {
        return next(context);
      }

      var nodeRewards = rewardsPerProposal.multiply(UInt256.from(bftData.completedProposals()));
      if (nodeRewards.isZero()) {
        return next(context);
      }

      return new LoadingStake(
          k,
          validatorStakeData -> {
            int rakePercentage = validatorStakeData.getRakePercentage();
            final UInt256 rakedEmissions;
            if (rakePercentage != 0) {
              var rake =
                  nodeRewards.multiply(UInt256.from(rakePercentage)).divide(UInt256.from(RAKE_MAX));
              var validatorOwner = validatorStakeData.getOwnerAddr();
              var initStake =
                  new TreeMap<REAddr, UInt256>(
                      Comparator.comparing(
                          REAddr::getBytes, UnsignedBytes.lexicographicalComparator()));
              initStake.put(validatorOwner, rake);
              preparingStake.put(k, initStake);
              rakedEmissions = nodeRewards.subtract(rake);
            } else {
              rakedEmissions = nodeRewards;
            }
            validatorStakeData.addEmission(rakedEmissions);
            updatingValidators.put(k, validatorStakeData);
            return next(context);
          });
    }
  }

  public static final class BootupValidator implements ReducerState {
    private final ValidatorBFTData expected;
    private final Supplier<ReducerState> onDone;

    BootupValidator(ValidatorStakeData validator, Supplier<ReducerState> onDone) {
      this.expected = new ValidatorBFTData(validator.validatorKey(), 0, 0);
      this.onDone = onDone;
    }

    public ReducerState bootUp(ValidatorBFTData data) throws MismatchException {
      if (!Objects.equals(this.expected, data)) {
        throw new MismatchException(this.expected, data);
      }
      return this.onDone.get();
    }
  }

  public static class StartingNextEpoch implements ReducerState {
    private final HasEpochData prevEpoch;

    StartingNextEpoch(HasEpochData prevEpoch) {
      this.prevEpoch = prevEpoch;
    }

    ReducerState nextEpoch(EpochData u) throws ProcedureException {
      if (u.epoch() != prevEpoch.epoch() + 1) {
        throw new ProcedureException(
            "Invalid next epoch: " + u.epoch() + " Expected: " + (prevEpoch.epoch() + 1));
      }
      return new StartingEpochRound();
    }
  }

  public class CreatingNextValidatorSet implements ReducerState {
    private LinkedList<ValidatorStakeData> nextValidatorSet;
    private final UpdatingEpoch updatingEpoch;

    CreatingNextValidatorSet(UpdatingEpoch updatingEpoch) {
      this.updatingEpoch = updatingEpoch;
    }

    ReducerState readIndex(
        IndexedSubstateIterator<ValidatorStakeData> substateIterator, ExecutionContext context)
        throws ProcedureException {
      substateIterator.verifyPostTypePrefixEquals(new byte[] {0, 1}); // registered validator
      this.nextValidatorSet =
          Streams.stream(substateIterator.iterator())
              .sorted(
                  Comparator.comparing(ValidatorStakeData::amount)
                      .thenComparing(ValidatorStakeData::validatorKey, KeyComparator.instance())
                      .reversed())
              .limit(maxValidators)
              .filter(v -> !v.totalStake().isZero())
              .collect(Collectors.toCollection(LinkedList::new));

      context.emitEvent(new NextValidatorSetEvent(this.nextValidatorSet));
      return next();
    }

    ReducerState next() {
      if (this.nextValidatorSet.isEmpty()) {
        return new StartingNextEpoch(updatingEpoch.prevEpoch);
      }

      var nextValidator = this.nextValidatorSet.pop();
      return new BootupValidator(nextValidator, this::next);
    }
  }

  public static final class UpdatingEpoch implements ReducerState {
    private final HasEpochData prevEpoch;

    UpdatingEpoch(HasEpochData prevEpoch) {
      this.prevEpoch = prevEpoch;
    }
  }

  public static final class StartingEpochRound implements ReducerState {}

  private final class Unstaking implements ReducerState {
    private final UpdatingEpoch updatingEpoch;
    private final TreeMap<REAddr, UInt256> unstaking;
    private final Supplier<ReducerState> onDone;
    private ValidatorScratchPad current;

    Unstaking(
        UpdatingEpoch updatingEpoch,
        ValidatorScratchPad current,
        TreeMap<REAddr, UInt256> unstaking,
        Supplier<ReducerState> onDone) {
      this.updatingEpoch = updatingEpoch;
      this.current = current;
      this.unstaking = unstaking;
      this.onDone = onDone;
    }

    ReducerState exit(ExitingStake u) throws MismatchException {
      var firstAddr = unstaking.firstKey();
      var ownershipUnstake = unstaking.remove(firstAddr);
      var epochUnlocked = updatingEpoch.prevEpoch.epoch() + unstakingEpochDelay + 1;
      var expectedExit = current.unstakeOwnership(firstAddr, ownershipUnstake, epochUnlocked);
      if (!u.equals(expectedExit)) {
        throw new MismatchException(expectedExit, u);
      }

      return unstaking.isEmpty() ? onDone.get() : this;
    }
  }

  private final class PreparingUnstake implements ReducerState {
    private final UpdatingEpoch updatingEpoch;
    private final TreeMap<ECPublicKey, TreeMap<REAddr, UInt256>> preparingUnstake =
        new TreeMap<>(KeyComparator.instance());
    private final TreeMap<ECPublicKey, TreeMap<REAddr, UInt256>> preparingStake;
    private final TreeMap<ECPublicKey, ValidatorScratchPad> updatingValidators;

    PreparingUnstake(
        UpdatingEpoch updatingEpoch,
        TreeMap<ECPublicKey, ValidatorScratchPad> updatingValidators,
        TreeMap<ECPublicKey, TreeMap<REAddr, UInt256>> preparingStake) {
      this.updatingEpoch = updatingEpoch;
      this.updatingValidators = updatingValidators;
      this.preparingStake = preparingStake;
    }

    ReducerState unstakes(IndexedSubstateIterator<PreparedUnstakeOwnership> i)
        throws ProcedureException {
      i.verifyPostTypePrefixIsEmpty();
      i.iterator()
          .forEachRemaining(
              preparedUnstakeOwned ->
                  preparingUnstake
                      .computeIfAbsent(
                          preparedUnstakeOwned.delegateKey(),
                          k ->
                              new TreeMap<>(
                                  Comparator.comparing(
                                      REAddr::getBytes, UnsignedBytes.lexicographicalComparator())))
                      .merge(
                          preparedUnstakeOwned.owner(),
                          preparedUnstakeOwned.amount(),
                          UInt256::add));
      return next();
    }

    ReducerState next() {
      if (preparingUnstake.isEmpty()) {
        return new PreparingStake(updatingEpoch, updatingValidators, preparingStake);
      }

      var k = preparingUnstake.firstKey();
      var unstakes = preparingUnstake.remove(k);

      if (!updatingValidators.containsKey(k)) {
        return new LoadingStake(
            k,
            validatorStake -> {
              updatingValidators.put(k, validatorStake);
              return new Unstaking(updatingEpoch, validatorStake, unstakes, this::next);
            });
      } else {
        var validatorStake = updatingValidators.get(k);
        return new Unstaking(updatingEpoch, validatorStake, unstakes, this::next);
      }
    }
  }

  private static final class Staking implements ReducerState {
    private final ValidatorScratchPad validatorScratchPad;
    private final TreeMap<REAddr, UInt256> stakes;
    private final Supplier<ReducerState> onDone;

    Staking(
        ValidatorScratchPad validatorScratchPad,
        TreeMap<REAddr, UInt256> stakes,
        Supplier<ReducerState> onDone) {
      this.validatorScratchPad = validatorScratchPad;
      this.stakes = stakes;
      this.onDone = onDone;
    }

    ReducerState stake(StakeOwnership stakeOwnership) throws MismatchException {
      var accountAddr = stakes.firstKey();
      var stakeAmt = stakes.remove(accountAddr);
      var expectedOwnership = validatorScratchPad.stake(accountAddr, stakeAmt);
      if (!Objects.equals(stakeOwnership, expectedOwnership)) {
        throw new MismatchException(expectedOwnership, stakeOwnership);
      }
      return stakes.isEmpty() ? onDone.get() : this;
    }
  }

  private static final class LoadingStake implements ReducerState {
    private final ECPublicKey key;
    private final Function<ValidatorScratchPad, ReducerState> onDone;

    LoadingStake(ECPublicKey key, Function<ValidatorScratchPad, ReducerState> onDone) {
      this.key = key;
      this.onDone = onDone;
    }

    ReducerState startUpdate(ValidatorStakeData stake) throws ProcedureException {
      if (!stake.validatorKey().equals(key)) {
        throw new ProcedureException("Invalid stake load");
      }
      return onDone.apply(new ValidatorScratchPad(stake));
    }

    @Override
    public String toString() {
      return String.format("%s{onDone: %s}", this.getClass().getSimpleName(), onDone);
    }
  }

  private final class PreparingStake implements ReducerState {
    private final UpdatingEpoch updatingEpoch;
    private final TreeMap<ECPublicKey, ValidatorScratchPad> validatorsScratchPad;
    private final TreeMap<ECPublicKey, TreeMap<REAddr, UInt256>> preparingStake;

    PreparingStake(
        UpdatingEpoch updatingEpoch,
        TreeMap<ECPublicKey, ValidatorScratchPad> validatorsScratchPad,
        TreeMap<ECPublicKey, TreeMap<REAddr, UInt256>> preparingStake) {
      this.validatorsScratchPad = validatorsScratchPad;
      this.updatingEpoch = updatingEpoch;
      this.preparingStake = preparingStake;
    }

    ReducerState prepareStakes(IndexedSubstateIterator<PreparedStake> i) throws ProcedureException {
      i.verifyPostTypePrefixIsEmpty();
      i.iterator()
          .forEachRemaining(
              preparedStake ->
                  preparingStake
                      .computeIfAbsent(
                          preparedStake.delegateKey(),
                          k ->
                              new TreeMap<>(
                                  Comparator.comparing(
                                      REAddr::getBytes, UnsignedBytes.lexicographicalComparator())))
                      .merge(preparedStake.owner(), preparedStake.amount(), UInt256::add));
      return next();
    }

    ReducerState next() {
      if (preparingStake.isEmpty()) {
        return new PreparingRakeUpdate(updatingEpoch, validatorsScratchPad);
      }

      var k = preparingStake.firstKey();
      var stakes = preparingStake.remove(k);
      if (!validatorsScratchPad.containsKey(k)) {
        return new LoadingStake(
            k,
            validatorStake -> {
              validatorsScratchPad.put(k, validatorStake);
              return new Staking(validatorStake, stakes, this::next);
            });
      } else {
        return new Staking(validatorsScratchPad.get(k), stakes, this::next);
      }
    }
  }

  private static final class ResetRakeUpdate implements ReducerState {
    private final ValidatorFeeCopy update;
    private final Supplier<ReducerState> next;

    ResetRakeUpdate(ValidatorFeeCopy update, Supplier<ReducerState> next) {
      this.update = update;
      this.next = next;
    }

    ReducerState reset(ValidatorFeeCopy rakeCopy) throws ProcedureException {
      if (!rakeCopy.validatorKey().equals(update.validatorKey())) {
        throw new ProcedureException("Validator keys must match.");
      }

      if (rakeCopy.curRakePercentage() != update.curRakePercentage()) {
        throw new ProcedureException("Rake percentage must match.");
      }

      if (rakeCopy.epochUpdate().isPresent()) {
        throw new ProcedureException("Reset of rake update should not have an epoch.");
      }

      return next.get();
    }
  }

  private final class PreparingRakeUpdate implements ReducerState {
    private final UpdatingEpoch updatingEpoch;
    private final TreeMap<ECPublicKey, ValidatorScratchPad> validatorsScratchPad;
    private final TreeMap<ECPublicKey, ValidatorFeeCopy> preparingRakeUpdates =
        new TreeMap<>(KeyComparator.instance());

    PreparingRakeUpdate(
        UpdatingEpoch updatingEpoch,
        TreeMap<ECPublicKey, ValidatorScratchPad> validatorsScratchPad) {
      this.updatingEpoch = updatingEpoch;
      this.validatorsScratchPad = validatorsScratchPad;
    }

    ReducerState prepareRakeUpdates(
        IndexedSubstateIterator<ValidatorFeeCopy> indexedSubstateIterator)
        throws ProcedureException {
      var expectedEpoch = updatingEpoch.prevEpoch.epoch() + 1;
      var expectedPrefix = new byte[2 + Long.BYTES];
      expectedPrefix[0] = 0;
      expectedPrefix[1] = 1;
      Longs.copyTo(expectedEpoch, expectedPrefix, 2);
      indexedSubstateIterator.verifyPostTypePrefixEquals(expectedPrefix);
      var iter = indexedSubstateIterator.iterator();
      while (iter.hasNext()) {
        var preparedRakeUpdate = iter.next();
        // Sanity check
        var epochUpdate = preparedRakeUpdate.epochUpdate();
        if (epochUpdate.orElseThrow() != expectedEpoch) {
          throw new IllegalStateException(
              "Invalid rake update epoch expected " + expectedEpoch + " but was " + epochUpdate);
        }
        preparingRakeUpdates.put(preparedRakeUpdate.validatorKey(), preparedRakeUpdate);
      }
      return next();
    }

    ReducerState next() {
      if (preparingRakeUpdates.isEmpty()) {
        return new PreparingOwnerUpdate(updatingEpoch, validatorsScratchPad);
      }

      var k = preparingRakeUpdates.firstKey();
      var validatorUpdate = preparingRakeUpdates.remove(k);
      if (!validatorsScratchPad.containsKey(k)) {
        return new LoadingStake(
            k,
            validatorStake -> {
              validatorsScratchPad.put(k, validatorStake);
              validatorStake.setRakePercentage(validatorUpdate.curRakePercentage());
              return new ResetRakeUpdate(validatorUpdate, this::next);
            });
      } else {
        validatorsScratchPad.get(k).setRakePercentage(validatorUpdate.curRakePercentage());
        return new ResetRakeUpdate(validatorUpdate, this::next);
      }
    }
  }

  private static final class ResetOwnerUpdate implements ReducerState {
    private final ECPublicKey validatorKey;
    private final Supplier<ReducerState> next;

    ResetOwnerUpdate(ECPublicKey validatorKey, Supplier<ReducerState> next) {
      this.validatorKey = validatorKey;
      this.next = next;
    }

    ReducerState reset(ValidatorOwnerCopy update) throws ProcedureException {
      if (!validatorKey.equals(update.validatorKey())) {
        throw new ProcedureException("Validator keys must match.");
      }

      if (update.epochUpdate().isPresent()) {
        throw new ProcedureException("Epoch should not be present.");
      }

      return next.get();
    }
  }

  private final class PreparingOwnerUpdate implements ReducerState {
    private final UpdatingEpoch updatingEpoch;
    private final TreeMap<ECPublicKey, ValidatorScratchPad> validatorsScratchPad;
    private final TreeMap<ECPublicKey, ValidatorOwnerCopy> preparingOwnerUpdates =
        new TreeMap<>(KeyComparator.instance());

    PreparingOwnerUpdate(
        UpdatingEpoch updatingEpoch,
        TreeMap<ECPublicKey, ValidatorScratchPad> validatorsScratchPad) {
      this.updatingEpoch = updatingEpoch;
      this.validatorsScratchPad = validatorsScratchPad;
    }

    ReducerState prepareValidatorUpdate(
        IndexedSubstateIterator<ValidatorOwnerCopy> indexedSubstateIterator)
        throws ProcedureException {
      var expectedEpoch = updatingEpoch.prevEpoch.epoch() + 1;
      var expectedPrefix = new byte[2 + Long.BYTES];
      expectedPrefix[0] = 0;
      expectedPrefix[1] = 1;
      Longs.copyTo(expectedEpoch, expectedPrefix, 2);
      indexedSubstateIterator.verifyPostTypePrefixEquals(expectedPrefix);

      var iter = indexedSubstateIterator.iterator();
      while (iter.hasNext()) {
        var preparedValidatorUpdate = iter.next();
        preparingOwnerUpdates.put(preparedValidatorUpdate.validatorKey(), preparedValidatorUpdate);
      }
      return next();
    }

    ReducerState next() {
      if (preparingOwnerUpdates.isEmpty()) {
        return new PreparingRegisteredUpdate(updatingEpoch, validatorsScratchPad);
      }

      var k = preparingOwnerUpdates.firstKey();
      var validatorUpdate = preparingOwnerUpdates.remove(k);
      if (!validatorsScratchPad.containsKey(k)) {
        return new LoadingStake(
            k,
            validatorStake -> {
              validatorsScratchPad.put(k, validatorStake);
              validatorStake.setOwnerAddr(validatorUpdate.owner());
              return new ResetOwnerUpdate(k, this::next);
            });
      } else {
        validatorsScratchPad.get(k).setOwnerAddr(validatorUpdate.owner());
        return new ResetOwnerUpdate(k, this::next);
      }
    }

    @Override
    public String toString() {
      return String.format(
          "%s{preparingOwnerUpdates=%s}", this.getClass().getSimpleName(), preparingOwnerUpdates);
    }
  }

  private static final class ResetRegisteredUpdate implements ReducerState {
    private final ValidatorRegisteredCopy update;
    private final Supplier<ReducerState> next;

    ResetRegisteredUpdate(ValidatorRegisteredCopy update, Supplier<ReducerState> next) {
      this.update = update;
      this.next = next;
    }

    ReducerState reset(ValidatorRegisteredCopy registeredCopy) throws ProcedureException {
      if (!registeredCopy.validatorKey().equals(update.validatorKey())) {
        throw new ProcedureException("Validator keys must match.");
      }

      if (registeredCopy.isRegistered() != update.isRegistered()) {
        throw new ProcedureException("Registered flags must match.");
      }

      if (registeredCopy.epochUpdate().isPresent()) {
        throw new ProcedureException("Should not have an epoch.");
      }

      return next.get();
    }
  }

  private final class PreparingRegisteredUpdate implements ReducerState {
    private final UpdatingEpoch updatingEpoch;
    private final TreeMap<ECPublicKey, ValidatorScratchPad> validatorsScratchPad;
    private final TreeMap<ECPublicKey, ValidatorRegisteredCopy> preparingRegisteredUpdates =
        new TreeMap<>(KeyComparator.instance());

    PreparingRegisteredUpdate(
        UpdatingEpoch updatingEpoch,
        TreeMap<ECPublicKey, ValidatorScratchPad> validatorsScratchPad) {
      this.updatingEpoch = updatingEpoch;
      this.validatorsScratchPad = validatorsScratchPad;
    }

    ReducerState prepareRegisterUpdates(
        IndexedSubstateIterator<ValidatorRegisteredCopy> indexedSubstateIterator)
        throws ProcedureException {
      var expectedEpoch = updatingEpoch.prevEpoch.epoch() + 1;
      var expectedPrefix = new byte[2 + Long.BYTES];
      expectedPrefix[0] = 0;
      expectedPrefix[1] = 1;
      Longs.copyTo(expectedEpoch, expectedPrefix, 2);
      indexedSubstateIterator.verifyPostTypePrefixEquals(expectedPrefix);
      var iter = indexedSubstateIterator.iterator();
      while (iter.hasNext()) {
        var preparedRegisteredUpdate = iter.next();
        preparingRegisteredUpdates.put(
            preparedRegisteredUpdate.validatorKey(), preparedRegisteredUpdate);
      }
      return next();
    }

    ReducerState next() {
      if (preparingRegisteredUpdates.isEmpty()) {
        return validatorsScratchPad.isEmpty()
            ? new CreatingNextValidatorSet(updatingEpoch)
            : new UpdatingValidatorStakes(updatingEpoch, validatorsScratchPad);
      }

      var k = preparingRegisteredUpdates.firstKey();
      var validatorUpdate = preparingRegisteredUpdates.remove(k);
      if (!validatorsScratchPad.containsKey(k)) {
        return new LoadingStake(
            k,
            validatorStake -> {
              validatorsScratchPad.put(k, validatorStake);
              validatorStake.setRegistered(validatorUpdate.isRegistered());
              return new ResetRegisteredUpdate(validatorUpdate, this::next);
            });
      } else {
        validatorsScratchPad.get(k).setRegistered(validatorUpdate.isRegistered());
        return new ResetRegisteredUpdate(validatorUpdate, this::next);
      }
    }
  }

  private final class UpdatingValidatorStakes implements ReducerState {
    private final UpdatingEpoch updatingEpoch;
    private final TreeMap<ECPublicKey, ValidatorScratchPad> validatorsScratchPad;

    UpdatingValidatorStakes(
        UpdatingEpoch updatingEpoch,
        TreeMap<ECPublicKey, ValidatorScratchPad> validatorsScratchPad) {
      this.updatingEpoch = updatingEpoch;
      this.validatorsScratchPad = validatorsScratchPad;
    }

    ReducerState updateStake(ValidatorStakeData stake) throws MismatchException {
      var k = validatorsScratchPad.firstKey();
      var expectedValidatorData = validatorsScratchPad.remove(k).toSubstate();
      if (!stake.equals(expectedValidatorData)) {
        throw new MismatchException(expectedValidatorData, stake);
      }

      return validatorsScratchPad.isEmpty() ? new CreatingNextValidatorSet(updatingEpoch) : this;
    }
  }

  private void registerGenesisTransitions(Loader os) {}

  private void epochUpdate(Loader os) {
    // Epoch Update
    os.procedure(
        new DownProcedure<>(
            EndPrevRound.class,
            EpochData.class,
            d -> new Authorization(PermissionLevel.SUPER_USER, (r, c) -> {}),
            (d, s, r, c) -> {
              // TODO: Should move this authorization instead of checking epoch > 0
              if (d.epoch() > 0 && s.getClosedRound().view() != maxRounds) {
                throw new ProcedureException(
                    "Must execute epoch update on end of round "
                        + maxRounds
                        + " but is "
                        + s.getClosedRound().view());
              }

              return ReducerResult.incomplete(new UpdatingEpoch(d));
            }));

    os.procedure(
        new ShutdownAllProcedure<>(
            ExitingStake.class,
            UpdatingEpoch.class,
            () -> new Authorization(PermissionLevel.SUPER_USER, (r, c) -> {}),
            (s, d, c, r) -> {
              var exittingStake = new ProcessExittingStake(s);
              return ReducerResult.incomplete(exittingStake.process(d));
            }));
    os.procedure(
        new UpProcedure<>(
            ProcessExittingStake.class,
            TokensInAccount.class,
            u -> new Authorization(PermissionLevel.SUPER_USER, (r, c) -> {}),
            (s, u, c, r) -> ReducerResult.incomplete(s.unlock(u))));

    os.procedure(
        new ShutdownAllProcedure<>(
            ValidatorBFTData.class,
            RewardingValidators.class,
            () -> new Authorization(PermissionLevel.SUPER_USER, (r, c) -> {}),
            (s, d, c, r) -> ReducerResult.incomplete(s.process(d, c))));

    os.procedure(
        new ShutdownAllProcedure<>(
            PreparedUnstakeOwnership.class,
            PreparingUnstake.class,
            () -> new Authorization(PermissionLevel.SUPER_USER, (r, c) -> {}),
            (s, d, c, r) -> ReducerResult.incomplete(s.unstakes(d))));
    os.procedure(
        new DownProcedure<>(
            LoadingStake.class,
            ValidatorStakeData.class,
            d -> d.bucket().withdrawAuthorization(),
            (d, s, r, c) -> ReducerResult.incomplete(s.startUpdate(d))));
    os.procedure(
        new UpProcedure<>(
            Unstaking.class,
            ExitingStake.class,
            u -> new Authorization(PermissionLevel.SUPER_USER, (r, c) -> {}),
            (s, u, c, r) -> ReducerResult.incomplete(s.exit(u))));
    os.procedure(
        new ShutdownAllProcedure<>(
            PreparedStake.class,
            PreparingStake.class,
            () -> new Authorization(PermissionLevel.SUPER_USER, (r, c) -> {}),
            (s, d, c, r) -> ReducerResult.incomplete(s.prepareStakes(d))));
    os.procedure(
        new ShutdownAllProcedure<>(
            ValidatorFeeCopy.class,
            PreparingRakeUpdate.class,
            () -> new Authorization(PermissionLevel.SUPER_USER, (r, c) -> {}),
            (s, d, c, r) -> ReducerResult.incomplete(s.prepareRakeUpdates(d))));
    os.procedure(
        new UpProcedure<>(
            ResetRakeUpdate.class,
            ValidatorFeeCopy.class,
            u -> new Authorization(PermissionLevel.SUPER_USER, (r, c) -> {}),
            (s, u, c, r) -> ReducerResult.incomplete(s.reset(u))));

    os.procedure(
        new ShutdownAllProcedure<>(
            ValidatorOwnerCopy.class,
            PreparingOwnerUpdate.class,
            () -> new Authorization(PermissionLevel.SUPER_USER, (r, c) -> {}),
            (s, d, c, r) -> ReducerResult.incomplete(s.prepareValidatorUpdate(d))));
    os.procedure(
        new UpProcedure<>(
            ResetOwnerUpdate.class,
            ValidatorOwnerCopy.class,
            u -> new Authorization(PermissionLevel.SUPER_USER, (r, c) -> {}),
            (s, u, c, r) -> ReducerResult.incomplete(s.reset(u))));

    os.procedure(
        new ShutdownAllProcedure<>(
            ValidatorRegisteredCopy.class,
            PreparingRegisteredUpdate.class,
            () -> new Authorization(PermissionLevel.SUPER_USER, (r, c) -> {}),
            (s, d, c, r) -> ReducerResult.incomplete(s.prepareRegisterUpdates(d))));
    os.procedure(
        new UpProcedure<>(
            ResetRegisteredUpdate.class,
            ValidatorRegisteredCopy.class,
            u -> new Authorization(PermissionLevel.SUPER_USER, (r, c) -> {}),
            (s, u, c, r) -> ReducerResult.incomplete(s.reset(u))));

    os.procedure(
        new UpProcedure<>(
            Staking.class,
            StakeOwnership.class,
            u -> new Authorization(PermissionLevel.SUPER_USER, (r, c) -> {}),
            (s, u, c, r) -> ReducerResult.incomplete(s.stake(u))));
    os.procedure(
        new UpProcedure<>(
            UpdatingValidatorStakes.class,
            ValidatorStakeData.class,
            u -> new Authorization(PermissionLevel.SUPER_USER, (r, c) -> {}),
            (s, u, c, r) -> ReducerResult.incomplete(s.updateStake(u))));

    os.procedure(
        new ReadIndexProcedure<>(
            CreatingNextValidatorSet.class,
            ValidatorStakeData.class,
            () -> new Authorization(PermissionLevel.SUPER_USER, (r, c) -> {}),
            (s, d, c, r) -> ReducerResult.incomplete(s.readIndex(d, c))));

    os.procedure(
        new UpProcedure<>(
            BootupValidator.class,
            ValidatorBFTData.class,
            u -> new Authorization(PermissionLevel.SUPER_USER, (r, c) -> {}),
            (s, u, c, r) -> ReducerResult.incomplete(s.bootUp(u))));

    os.procedure(
        new UpProcedure<>(
            StartingNextEpoch.class,
            EpochData.class,
            u -> new Authorization(PermissionLevel.SUPER_USER, (r, c) -> {}),
            (s, u, c, r) -> ReducerResult.incomplete(s.nextEpoch(u))));

    os.procedure(
        new UpProcedure<>(
            StartingEpochRound.class,
            RoundData.class,
            u -> new Authorization(PermissionLevel.SUPER_USER, (r, c) -> {}),
            (s, u, c, r) -> {
              if (u.view() != 0) {
                throw new ProcedureException("Epoch must start with view 0");
              }

              return ReducerResult.complete();
            }));
  }

  @Override
  public void main(Loader os) {

    os.substate(
        new SubstateDefinition<>(
            ValidatorStakeData.class,
            SubstateTypeId.VALIDATOR_STAKE_DATA.id(),
            buf -> {
              REFieldSerialization.deserializeReservedByte(buf);
              var isRegistered = REFieldSerialization.deserializeBoolean(buf);
              var amount = REFieldSerialization.deserializeUInt256(buf);
              var delegate = REFieldSerialization.deserializeKey(buf);
              var ownership = REFieldSerialization.deserializeUInt256(buf);
              var rakePercentage = REFieldSerialization.deserializeInt(buf);
              var ownerAddress = REFieldSerialization.deserializeAccountREAddr(buf);
              return ValidatorStakeData.create(
                  delegate, amount, ownership, rakePercentage, ownerAddress, isRegistered);
            },
            (s, buf) -> {
              REFieldSerialization.serializeReservedByte(buf);
              REFieldSerialization.serializeBoolean(buf, s.isRegistered());
              buf.put(s.amount().toByteArray());
              REFieldSerialization.serializeKey(buf, s.validatorKey());
              buf.put(s.totalOwnership().toByteArray());
              buf.putInt(s.rakePercentage());
              REFieldSerialization.serializeREAddr(buf, s.ownerAddr());
            },
            buf -> REFieldSerialization.deserializeKey(buf),
            (k, buf) -> REFieldSerialization.serializeKey(buf, (ECPublicKey) k),
            k -> ValidatorStakeData.createVirtual((ECPublicKey) k)));
    os.substate(
        new SubstateDefinition<>(
            StakeOwnership.class,
            SubstateTypeId.STAKE_OWNERSHIP.id(),
            buf -> {
              REFieldSerialization.deserializeReservedByte(buf);
              var delegate = REFieldSerialization.deserializeKey(buf);
              var owner = REFieldSerialization.deserializeAccountREAddr(buf);
              var amount = REFieldSerialization.deserializeNonZeroUInt256(buf);
              return new StakeOwnership(delegate, owner, amount);
            },
            (s, buf) -> {
              REFieldSerialization.serializeReservedByte(buf);
              REFieldSerialization.serializeKey(buf, s.delegateKey());
              REFieldSerialization.serializeREAddr(buf, s.owner());
              buf.put(s.amount().toByteArray());
            }));
    os.substate(
        new SubstateDefinition<>(
            ExitingStake.class,
            SubstateTypeId.EXITING_STAKE.id(),
            buf -> {
              REFieldSerialization.deserializeReservedByte(buf);
              var epochUnlocked = REFieldSerialization.deserializeNonNegativeLong(buf);
              var delegate = REFieldSerialization.deserializeKey(buf);
              var owner = REFieldSerialization.deserializeAccountREAddr(buf);
              var amount = REFieldSerialization.deserializeNonZeroUInt256(buf);
              return new ExitingStake(epochUnlocked, delegate, owner, amount);
            },
            (s, buf) -> {
              REFieldSerialization.serializeReservedByte(buf);
              buf.putLong(s.epochUnlocked());
              REFieldSerialization.serializeKey(buf, s.delegateKey());
              REFieldSerialization.serializeREAddr(buf, s.owner());
              buf.put(s.amount().toByteArray());
            }));

    registerGenesisTransitions(os);

    // Epoch update
    epochUpdate(os);
  }
}
