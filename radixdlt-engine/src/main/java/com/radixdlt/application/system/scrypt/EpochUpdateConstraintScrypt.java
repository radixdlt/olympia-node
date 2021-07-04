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
 *
 */

package com.radixdlt.application.system.scrypt;

import com.google.common.collect.Streams;
import com.radixdlt.application.system.NextValidatorSetEvent;
import com.radixdlt.application.system.ValidatorBFTDataEvent;
import com.radixdlt.atom.REFieldSerialization;
import com.radixdlt.atom.SubstateTypeId;
import com.radixdlt.application.system.state.EpochData;
import com.radixdlt.application.system.state.HasEpochData;
import com.radixdlt.application.system.state.RoundData;
import com.radixdlt.application.system.state.StakeOwnership;
import com.radixdlt.application.system.state.ValidatorBFTData;
import com.radixdlt.application.system.state.ValidatorStakeData;
import com.radixdlt.application.tokens.state.ExittingStake;
import com.radixdlt.application.tokens.state.PreparedStake;
import com.radixdlt.application.tokens.state.PreparedUnstakeOwnership;
import com.radixdlt.application.tokens.state.TokensInAccount;
import com.radixdlt.application.validators.state.PreparedRegisteredUpdate;
import com.radixdlt.application.validators.state.ValidatorOwnerCopy;
import com.radixdlt.application.validators.state.PreparedOwnerUpdate;
import com.radixdlt.application.validators.state.ValidatorRakeCopy;
import com.radixdlt.application.validators.state.PreparedRakeUpdate;
import com.radixdlt.application.validators.state.ValidatorRegisteredCopy;
import com.radixdlt.atomos.CMAtomOS;
import com.radixdlt.atomos.ConstraintScrypt;
import com.radixdlt.atomos.Loader;
import com.radixdlt.atomos.SubstateDefinition;
import com.radixdlt.constraintmachine.Authorization;
import com.radixdlt.constraintmachine.DownProcedure;
import com.radixdlt.constraintmachine.ExecutionContext;
import com.radixdlt.constraintmachine.PermissionLevel;
import com.radixdlt.constraintmachine.ReadIndexProcedure;
import com.radixdlt.constraintmachine.exceptions.MismatchException;
import com.radixdlt.constraintmachine.exceptions.ProcedureException;
import com.radixdlt.constraintmachine.ReducerResult;
import com.radixdlt.constraintmachine.ReducerState;
import com.radixdlt.constraintmachine.IndexedSubstateIterator;
import com.radixdlt.constraintmachine.ShutdownAllProcedure;
import com.radixdlt.constraintmachine.UpProcedure;
import com.radixdlt.crypto.ECPublicKey;
import com.radixdlt.identifiers.REAddr;
import com.radixdlt.utils.KeyComparator;
import com.radixdlt.utils.Longs;
import com.radixdlt.utils.UInt256;

import java.util.Arrays;
import java.util.Comparator;
import java.util.LinkedList;
import java.util.Objects;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import static com.radixdlt.application.validators.state.PreparedRakeUpdate.RAKE_MAX;

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
		int maxValidators
	) {
		this.maxRounds = maxRounds;
		this.rewardsPerProposal = rewardsPerProposal;
		this.unstakingEpochDelay = unstakingEpochDelay;
		this.minimumCompletedProposalsPercentage = minimumCompletedProposalsPercentage;
		this.maxValidators = maxValidators;
	}

	public final class ProcessExittingStake implements ReducerState {
		private final UpdatingEpoch updatingEpoch;
		private final TreeSet<ExittingStake> exitting = new TreeSet<>(
			(o1, o2) -> Arrays.compare(o1.dataKey(), o2.dataKey())
		);

		ProcessExittingStake(UpdatingEpoch updatingEpoch) {
			this.updatingEpoch = updatingEpoch;
		}

		public ReducerState process(IndexedSubstateIterator<ExittingStake> indexedSubstateIterator) throws ProcedureException {
			var expectedEpoch = updatingEpoch.prevEpoch.getEpoch() + 1;
			var expectedPrefix = new byte[Long.BYTES + 1];
			Longs.copyTo(expectedEpoch, expectedPrefix, 1);
			indexedSubstateIterator.verifyPostTypePrefixEquals(expectedPrefix);
			indexedSubstateIterator.iterator().forEachRemaining(e -> {
				// Sanity check
				if (e.getEpochUnlocked() != expectedEpoch) {
					throw new IllegalStateException("Invalid shutdown of exitting stake update epoch expected "
						+ expectedEpoch + " but was " + e.getEpochUnlocked());
				}
				exitting.add(e);
			});
			return next();
		}

		public ReducerState unlock(TokensInAccount u) throws ProcedureException {
			var exit = exitting.first();
			exitting.remove(exit);
			if (exit.getEpochUnlocked() != updatingEpoch.prevEpoch.getEpoch() + 1) {
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
		private final TreeMap<ECPublicKey, ValidatorScratchPad> updatingValidators = new TreeMap<>(KeyComparator.instance());
		private final TreeMap<ECPublicKey, ValidatorBFTData> validatorBFTData = new TreeMap<>(KeyComparator.instance());
		private final TreeMap<ECPublicKey, TreeMap<REAddr, UInt256>> preparingStake = new TreeMap<>(KeyComparator.instance());
		private final UpdatingEpoch updatingEpoch;

		RewardingValidators(UpdatingEpoch updatingEpoch) {
			this.updatingEpoch = updatingEpoch;
		}

		// TODO: Remove context
		public ReducerState process(IndexedSubstateIterator<ValidatorBFTData> i, ExecutionContext context) throws ProcedureException {
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
				throw new IllegalStateException("Inconsistent data, there should only be a single substate per validator");
			}
			var bftData = validatorBFTData.remove(k);
			if (bftData.proposalsCompleted() + bftData.proposalsMissed() == 0) {
				return next(context);
			}

			var percentageCompleted = bftData.proposalsCompleted() * 10000
				/ (bftData.proposalsCompleted() + bftData.proposalsMissed());

			// Didn't pass threshold, no rewards!
			if (percentageCompleted < minimumCompletedProposalsPercentage) {
				return next(context);
			}

			var nodeRewards = rewardsPerProposal.multiply(UInt256.from(bftData.proposalsCompleted()));
			if (nodeRewards.isZero()) {
				return next(context);
			}

			return new LoadingStake(k, validatorStakeData -> {
				int rakePercentage = validatorStakeData.getRakePercentage();
				final UInt256 rakedEmissions;
				if (rakePercentage != 0) {
					var rake = nodeRewards
						.multiply(UInt256.from(rakePercentage))
						.divide(UInt256.from(RAKE_MAX));
					var validatorOwner = validatorStakeData.getOwnerAddr();
					var initStake = new TreeMap<REAddr, UInt256>((o1, o2) -> Arrays.compare(o1.getBytes(), o2.getBytes()));
					initStake.put(validatorOwner, rake);
					preparingStake.put(k, initStake);
					rakedEmissions = nodeRewards.subtract(rake);
				} else {
					rakedEmissions = nodeRewards;
				}
				validatorStakeData.addEmission(rakedEmissions);
				context.emitEvent(ValidatorBFTDataEvent.create(k, bftData.proposalsCompleted(), bftData.proposalsMissed()));
				updatingValidators.put(k, validatorStakeData);
				return next(context);
			});
		}
	}

	public static final class BootupValidator implements ReducerState {
		private final ValidatorBFTData expected;
		private final Supplier<ReducerState> onDone;

		BootupValidator(ValidatorStakeData validator, Supplier<ReducerState> onDone) {
			this.expected = new ValidatorBFTData(validator.getValidatorKey(), 0, 0);
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
			if (u.getEpoch() != prevEpoch.getEpoch() + 1) {
				throw new ProcedureException("Invalid next epoch: " + u.getEpoch()
					+ " Expected: " + (prevEpoch.getEpoch() + 1));
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

		ReducerState readIndex(IndexedSubstateIterator<ValidatorStakeData> substateIterator, ExecutionContext context) throws ProcedureException {
			substateIterator.verifyPostTypePrefixEquals(new byte[] {0, 1}); // registered validator
			this.nextValidatorSet = Streams.stream(substateIterator.iterator())
				.sorted(Comparator.comparing(ValidatorStakeData::getAmount)
					.thenComparing(ValidatorStakeData::getValidatorKey, KeyComparator.instance())
					.reversed()
				)
				.limit(maxValidators)
				.collect(Collectors.toCollection(LinkedList::new));

			context.emitEvent(NextValidatorSetEvent.create(this.nextValidatorSet));
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

	public static final class StartingEpochRound implements ReducerState {
	}


	private final class Unstaking implements ReducerState {
		private final UpdatingEpoch updatingEpoch;
		private final TreeMap<REAddr, UInt256> unstaking;
		private final Supplier<ReducerState> onDone;
		private ValidatorScratchPad current;

		Unstaking(
			UpdatingEpoch updatingEpoch,
			ValidatorScratchPad current,
			TreeMap<REAddr, UInt256> unstaking,
			Supplier<ReducerState> onDone
		) {
			this.updatingEpoch = updatingEpoch;
			this.current = current;
			this.unstaking = unstaking;
			this.onDone = onDone;
		}

		ReducerState exit(ExittingStake u) throws MismatchException {
			var firstAddr = unstaking.firstKey();
			var ownershipUnstake = unstaking.remove(firstAddr);
			var epochUnlocked = updatingEpoch.prevEpoch.getEpoch() + unstakingEpochDelay + 1;
			var expectedExit = current.unstakeOwnership(
				firstAddr, ownershipUnstake, epochUnlocked
			);
			if (!u.equals(expectedExit)) {
				throw new MismatchException(expectedExit, u);
			}

			return unstaking.isEmpty() ? onDone.get() : this;
		}
	}

	private final class PreparingUnstake implements ReducerState {
		private final UpdatingEpoch updatingEpoch;
		private final TreeMap<ECPublicKey, TreeMap<REAddr, UInt256>> preparingUnstake = new TreeMap<>(KeyComparator.instance());
		private final TreeMap<ECPublicKey, TreeMap<REAddr, UInt256>> preparingStake;
		private final TreeMap<ECPublicKey, ValidatorScratchPad> updatingValidators;

		PreparingUnstake(
			UpdatingEpoch updatingEpoch,
			TreeMap<ECPublicKey, ValidatorScratchPad> updatingValidators,
			TreeMap<ECPublicKey, TreeMap<REAddr, UInt256>> preparingStake
		) {
			this.updatingEpoch = updatingEpoch;
			this.updatingValidators = updatingValidators;
			this.preparingStake = preparingStake;
		}

		ReducerState unstakes(IndexedSubstateIterator<PreparedUnstakeOwnership> i) throws ProcedureException {
			i.verifyPostTypePrefixIsEmpty();
			i.iterator().forEachRemaining(preparedUnstakeOwned ->
				preparingUnstake
					.computeIfAbsent(
						preparedUnstakeOwned.getDelegateKey(),
						k -> new TreeMap<>((o1, o2) -> Arrays.compare(o1.getBytes(), o2.getBytes()))
					)
					.merge(preparedUnstakeOwned.getOwner(), preparedUnstakeOwned.getAmount(), UInt256::add)
			);
			return next();
		}

		ReducerState next() {
			if (preparingUnstake.isEmpty()) {
				return new PreparingStake(updatingEpoch, updatingValidators, preparingStake);
			}

			var k = preparingUnstake.firstKey();
			var unstakes = preparingUnstake.remove(k);

			if (!updatingValidators.containsKey(k)) {
				return new LoadingStake(k, validatorStake -> {
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

		Staking(ValidatorScratchPad validatorScratchPad, TreeMap<REAddr, UInt256> stakes, Supplier<ReducerState> onDone) {
			this.validatorScratchPad = validatorScratchPad;
			this.stakes = stakes;
			this.onDone = onDone;
		}

		ReducerState stake(StakeOwnership stakeOwnership) throws MismatchException, ProcedureException {
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
			if (!stake.getValidatorKey().equals(key)) {
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
			TreeMap<ECPublicKey, TreeMap<REAddr, UInt256>> preparingStake
		) {
			this.validatorsScratchPad = validatorsScratchPad;
			this.updatingEpoch = updatingEpoch;
			this.preparingStake = preparingStake;
		}

		ReducerState prepareStakes(IndexedSubstateIterator<PreparedStake> i) throws ProcedureException {
			i.verifyPostTypePrefixIsEmpty();
			i.iterator().forEachRemaining(preparedStake ->
				preparingStake
					.computeIfAbsent(
						preparedStake.getDelegateKey(),
						k -> new TreeMap<>((o1, o2) -> Arrays.compare(o1.getBytes(), o2.getBytes()))
					)
					.merge(preparedStake.getOwner(), preparedStake.getAmount(), UInt256::add)
			);
			return next();
		}

		ReducerState next() {
			if (preparingStake.isEmpty()) {
				return new PreparingRakeUpdate(updatingEpoch, validatorsScratchPad);
			}

			var k = preparingStake.firstKey();
			var stakes = preparingStake.remove(k);
			if (!validatorsScratchPad.containsKey(k)) {
				return new LoadingStake(k, validatorStake -> {
					validatorsScratchPad.put(k, validatorStake);
					return new Staking(validatorStake, stakes, this::next);
				});
			} else {
				return new Staking(validatorsScratchPad.get(k), stakes, this::next);
			}
		}
	}

	private static final class ResetRakeUpdate implements ReducerState {
		private final PreparedRakeUpdate update;
		private final Supplier<ReducerState> next;

		ResetRakeUpdate(PreparedRakeUpdate update, Supplier<ReducerState> next) {
			this.update = update;
			this.next = next;
		}

		ReducerState reset(ValidatorRakeCopy rakeCopy) throws ProcedureException {
			if (!rakeCopy.getValidatorKey().equals(update.getValidatorKey())) {
				throw new ProcedureException("Validator keys must match.");
			}

			if (rakeCopy.getCurRakePercentage() != update.getNextRakePercentage()) {
				throw new ProcedureException("Rake percentage must match.");
			}

			return next.get();
		}
	}

	private final class PreparingRakeUpdate implements ReducerState {
		private final UpdatingEpoch updatingEpoch;
		private final TreeMap<ECPublicKey, ValidatorScratchPad> validatorsScratchPad;
		private final TreeMap<ECPublicKey, PreparedRakeUpdate> preparingRakeUpdates = new TreeMap<>(KeyComparator.instance());

		PreparingRakeUpdate(
			UpdatingEpoch updatingEpoch,
			TreeMap<ECPublicKey, ValidatorScratchPad> validatorsScratchPad
		) {
			this.updatingEpoch = updatingEpoch;
			this.validatorsScratchPad = validatorsScratchPad;
		}

		ReducerState prepareRakeUpdates(IndexedSubstateIterator<PreparedRakeUpdate> indexedSubstateIterator) throws ProcedureException {
			var expectedEpoch = updatingEpoch.prevEpoch.getEpoch() + 1;
			var expectedPrefix = new byte[Long.BYTES + 1];
			Longs.copyTo(expectedEpoch, expectedPrefix, 1);
			indexedSubstateIterator.verifyPostTypePrefixEquals(expectedPrefix);
			var iter = indexedSubstateIterator.iterator();
			while (iter.hasNext()) {
				var preparedRakeUpdate = iter.next();
				// Sanity check
				if (preparedRakeUpdate.getEpoch() != expectedEpoch) {
					throw new IllegalStateException("Invalid rake update epoch expected " + expectedEpoch
						+ " but was " + preparedRakeUpdate.getEpoch());
				}
				preparingRakeUpdates.put(preparedRakeUpdate.getValidatorKey(), preparedRakeUpdate);
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
				return new LoadingStake(k, validatorStake -> {
					validatorsScratchPad.put(k, validatorStake);
					validatorStake.setRakePercentage(validatorUpdate.getNextRakePercentage());
					return new ResetRakeUpdate(validatorUpdate, this::next);
				});
			} else {
				validatorsScratchPad.get(k).setRakePercentage(validatorUpdate.getNextRakePercentage());
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
			if (!validatorKey.equals(update.getValidatorKey())) {
				throw new ProcedureException("Validator keys must match.");
			}

			return next.get();
		}
	}

	private final class PreparingOwnerUpdate implements ReducerState {
		private final UpdatingEpoch updatingEpoch;
		private final TreeMap<ECPublicKey, ValidatorScratchPad> validatorsScratchPad;
		private final TreeMap<ECPublicKey, PreparedOwnerUpdate> preparingOwnerUpdates = new TreeMap<>(KeyComparator.instance());

		PreparingOwnerUpdate(
			UpdatingEpoch updatingEpoch,
			TreeMap<ECPublicKey, ValidatorScratchPad> validatorsScratchPad
		) {
			this.updatingEpoch = updatingEpoch;
			this.validatorsScratchPad = validatorsScratchPad;
		}

		ReducerState prepareValidatorUpdate(IndexedSubstateIterator<PreparedOwnerUpdate> indexedSubstateIterator) throws ProcedureException {
			indexedSubstateIterator.verifyPostTypePrefixIsEmpty();
			var iter = indexedSubstateIterator.iterator();
			while (iter.hasNext()) {
				var preparedValidatorUpdate = iter.next();
				preparingOwnerUpdates.put(preparedValidatorUpdate.getValidatorKey(), preparedValidatorUpdate);
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
				return new LoadingStake(k, validatorStake -> {
					validatorsScratchPad.put(k, validatorStake);
					validatorStake.setOwnerAddr(validatorUpdate.getOwnerAddress());
					return new ResetOwnerUpdate(k, this::next);
				});
			} else {
				validatorsScratchPad.get(k).setOwnerAddr(validatorUpdate.getOwnerAddress());
				return new ResetOwnerUpdate(k, this::next);
			}
		}

		@Override
		public String toString() {
			return String.format("%s{preparingOwnerUpdates=%s}", this.getClass().getSimpleName(), preparingOwnerUpdates);
		}
	}

	private static final class ResetRegisteredUpdate implements ReducerState {
		private final PreparedRegisteredUpdate update;
		private final Supplier<ReducerState> next;

		ResetRegisteredUpdate(PreparedRegisteredUpdate update, Supplier<ReducerState> next) {
			this.update = update;
			this.next = next;
		}

		ReducerState reset(ValidatorRegisteredCopy registeredCopy) throws ProcedureException {
			if (!registeredCopy.getValidatorKey().equals(update.getValidatorKey())) {
				throw new ProcedureException("Validator keys must match.");
			}

			if (registeredCopy.isRegistered() != update.isRegistered()) {
				throw new ProcedureException("Registered flags must match.");
			}

			return next.get();
		}
	}

	private final class PreparingRegisteredUpdate implements ReducerState {
		private final UpdatingEpoch updatingEpoch;
		private final TreeMap<ECPublicKey, ValidatorScratchPad> validatorsScratchPad;
		private final TreeMap<ECPublicKey, PreparedRegisteredUpdate> preparingRegisteredUpdates = new TreeMap<>(KeyComparator.instance());

		PreparingRegisteredUpdate(
			UpdatingEpoch updatingEpoch,
			TreeMap<ECPublicKey, ValidatorScratchPad> validatorsScratchPad
		) {
			this.updatingEpoch = updatingEpoch;
			this.validatorsScratchPad = validatorsScratchPad;
		}

		ReducerState prepareRegisterUpdates(IndexedSubstateIterator<PreparedRegisteredUpdate> indexedSubstateIterator) throws ProcedureException {
			indexedSubstateIterator.verifyPostTypePrefixIsEmpty();
			var iter = indexedSubstateIterator.iterator();
			while (iter.hasNext()) {
				var preparedRegisteredUpdate = iter.next();
				preparingRegisteredUpdates.put(preparedRegisteredUpdate.getValidatorKey(), preparedRegisteredUpdate);
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
				return new LoadingStake(k, validatorStake -> {
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
		UpdatingValidatorStakes(UpdatingEpoch updatingEpoch, TreeMap<ECPublicKey, ValidatorScratchPad> validatorsScratchPad) {
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

	private static class AllocatingSystem implements ReducerState {
	}

	private void registerGenesisTransitions(Loader os) {
		// For Mainnet Genesis
		os.procedure(new UpProcedure<>(
			CMAtomOS.REAddrClaim.class, EpochData.class,
			u -> new Authorization(PermissionLevel.SYSTEM, (r, c) -> { }),
			(s, u, c, r) -> {
				if (u.getEpoch() != 0) {
					throw new ProcedureException("First epoch must be 0.");
				}

				return ReducerResult.incomplete(new AllocatingSystem());
			}
		));
		os.procedure(new UpProcedure<>(
			AllocatingSystem.class, RoundData.class,
			u -> new Authorization(PermissionLevel.SYSTEM, (r, c) -> { }),
			(s, u, c, r) -> {
				if (u.getView() != 0) {
					throw new ProcedureException("First view must be 0.");
				}
				return ReducerResult.complete();
			}
		));
	}

	private void epochUpdate(Loader os) {
		// Epoch Update
		os.procedure(new DownProcedure<>(
			EndPrevRound.class, EpochData.class,
			d -> new Authorization(PermissionLevel.SUPER_USER, (r, c) -> { }),
			(d, s, r) -> {
				// TODO: Should move this authorization instead of checking epoch > 0
				if (d.getSubstate().getEpoch() > 0 && s.getClosedRound().getView() != maxRounds) {
					throw new ProcedureException("Must execute epoch update on end of round " + maxRounds
						+ " but is " + s.getClosedRound().getView());
				}

				return ReducerResult.incomplete(new UpdatingEpoch(d.getSubstate()));
			}
		));

		os.procedure(new ShutdownAllProcedure<>(
			ExittingStake.class, UpdatingEpoch.class,
			() -> new Authorization(PermissionLevel.SUPER_USER, (r, c) -> { }),
			(s, d, c, r) -> {
				var exittingStake = new ProcessExittingStake(s);
				return ReducerResult.incomplete(exittingStake.process(d));
			}
		));
		os.procedure(new UpProcedure<>(
			ProcessExittingStake.class, TokensInAccount.class,
			u -> new Authorization(PermissionLevel.SUPER_USER, (r, c) -> { }),
			(s, u, c, r) -> ReducerResult.incomplete(s.unlock(u))
		));

		os.procedure(new ShutdownAllProcedure<>(
			ValidatorBFTData.class, RewardingValidators.class,
			() -> new Authorization(PermissionLevel.SUPER_USER, (r, c) -> { }),
			(s, d, c, r) -> ReducerResult.incomplete(s.process(d, c))
		));

		os.procedure(new ShutdownAllProcedure<>(
			PreparedUnstakeOwnership.class, PreparingUnstake.class,
			() -> new Authorization(PermissionLevel.SUPER_USER, (r, c) -> { }),
			(s, d, c, r) -> ReducerResult.incomplete(s.unstakes(d))
		));
		os.procedure(new DownProcedure<>(
			LoadingStake.class, ValidatorStakeData.class,
			d -> d.getSubstate().bucket().withdrawAuthorization(),
			(d, s, r) -> ReducerResult.incomplete(s.startUpdate(d.getSubstate()))
		));
		os.procedure(new UpProcedure<>(
			Unstaking.class, ExittingStake.class,
			u -> new Authorization(PermissionLevel.SUPER_USER, (r, c) -> { }),
			(s, u, c, r) -> ReducerResult.incomplete(s.exit(u))
		));
		os.procedure(new ShutdownAllProcedure<>(
			PreparedStake.class, PreparingStake.class,
			() -> new Authorization(PermissionLevel.SUPER_USER, (r, c) -> { }),
			(s, d, c, r) -> ReducerResult.incomplete(s.prepareStakes(d))
		));
		os.procedure(new ShutdownAllProcedure<>(
			PreparedRakeUpdate.class, PreparingRakeUpdate.class,
			() -> new Authorization(PermissionLevel.SUPER_USER, (r, c) -> { }),
			(s, d, c, r) -> ReducerResult.incomplete(s.prepareRakeUpdates(d))
		));
		os.procedure(new UpProcedure<>(
			ResetRakeUpdate.class, ValidatorRakeCopy.class,
			u -> new Authorization(PermissionLevel.SUPER_USER, (r, c) -> { }),
			(s, u, c, r) -> ReducerResult.incomplete(s.reset(u))
		));

		os.procedure(new ShutdownAllProcedure<>(
			PreparedOwnerUpdate.class, PreparingOwnerUpdate.class,
			() -> new Authorization(PermissionLevel.SUPER_USER, (r, c) -> { }),
			(s, d, c, r) -> ReducerResult.incomplete(s.prepareValidatorUpdate(d))
		));
		os.procedure(new UpProcedure<>(
			ResetOwnerUpdate.class, ValidatorOwnerCopy.class,
			u -> new Authorization(PermissionLevel.SUPER_USER, (r, c) -> { }),
			(s, u, c, r) -> ReducerResult.incomplete(s.reset(u))
		));

		os.procedure(new ShutdownAllProcedure<>(
			PreparedRegisteredUpdate.class, PreparingRegisteredUpdate.class,
			() -> new Authorization(PermissionLevel.SUPER_USER, (r, c) -> { }),
			(s, d, c, r) -> ReducerResult.incomplete(s.prepareRegisterUpdates(d))
		));
		os.procedure(new UpProcedure<>(
			ResetRegisteredUpdate.class, ValidatorRegisteredCopy.class,
			u -> new Authorization(PermissionLevel.SUPER_USER, (r, c) -> { }),
			(s, u, c, r) -> ReducerResult.incomplete(s.reset(u))
		));

		os.procedure(new UpProcedure<>(
			Staking.class, StakeOwnership.class,
			u -> new Authorization(PermissionLevel.SUPER_USER, (r, c) -> { }),
			(s, u, c, r) -> ReducerResult.incomplete(s.stake(u))
		));
		os.procedure(new UpProcedure<>(
			UpdatingValidatorStakes.class, ValidatorStakeData.class,
			u -> new Authorization(PermissionLevel.SUPER_USER, (r, c) -> { }),
			(s, u, c, r) -> ReducerResult.incomplete(s.updateStake(u))
		));

		os.procedure(new ReadIndexProcedure<>(
			CreatingNextValidatorSet.class, ValidatorStakeData.class,
			() -> new Authorization(PermissionLevel.SUPER_USER, (r, c) -> { }),
			(s, d, c, r) -> ReducerResult.incomplete(s.readIndex(d, c))
		));

		os.procedure(new UpProcedure<>(
			BootupValidator.class, ValidatorBFTData.class,
			u -> new Authorization(PermissionLevel.SUPER_USER, (r, c) -> { }),
			(s, u, c, r) -> ReducerResult.incomplete(s.bootUp(u))
		));

		os.procedure(new UpProcedure<>(
			StartingNextEpoch.class, EpochData.class,
			u -> new Authorization(PermissionLevel.SUPER_USER, (r, c) -> { }),
			(s, u, c, r) -> ReducerResult.incomplete(s.nextEpoch(u))
		));

		os.procedure(new UpProcedure<>(
			StartingEpochRound.class, RoundData.class,
			u -> new Authorization(PermissionLevel.SUPER_USER, (r, c) -> { }),
			(s, u, c, r) -> {
				if (u.getView() != 0) {
					throw new ProcedureException("Epoch must start with view 0");
				}

				return ReducerResult.complete();
			}
		));
	}

	@Override
	public void main(Loader os) {
		os.substate(
			new SubstateDefinition<>(
				EpochData.class,
				SubstateTypeId.EPOCH_DATA.id(),
				buf -> {
					REFieldSerialization.deserializeReservedByte(buf);
					var epoch = REFieldSerialization.deserializeNonNegativeLong(buf);
					return new EpochData(epoch);
				},
				(s, buf) -> {
					REFieldSerialization.serializeReservedByte(buf);
					buf.putLong(s.getEpoch());
				}
			)
		);
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
					return ValidatorStakeData.create(delegate, amount, ownership, rakePercentage, ownerAddress, isRegistered);
				},
				(s, buf) -> {
					REFieldSerialization.serializeReservedByte(buf);
					REFieldSerialization.serializeBoolean(buf, s.isRegistered());
					buf.put(s.getAmount().toByteArray());
					REFieldSerialization.serializeKey(buf, s.getValidatorKey());
					buf.put(s.getTotalOwnership().toByteArray());
					buf.putInt(s.getRakePercentage());
					REFieldSerialization.serializeREAddr(buf, s.getOwnerAddr());
				},
				s -> s.equals(ValidatorStakeData.createVirtual(s.getValidatorKey()))
			)
		);
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
					REFieldSerialization.serializeKey(buf, s.getDelegateKey());
					REFieldSerialization.serializeREAddr(buf, s.getOwner());
					buf.put(s.getAmount().toByteArray());
				}
			)
		);
		os.substate(
			new SubstateDefinition<>(
				ExittingStake.class,
				SubstateTypeId.EXITTING_STAKE.id(),
				buf -> {
					REFieldSerialization.deserializeReservedByte(buf);
					var epochUnlocked = REFieldSerialization.deserializeNonNegativeLong(buf);
					var delegate = REFieldSerialization.deserializeKey(buf);
					var owner = REFieldSerialization.deserializeAccountREAddr(buf);
					var amount = REFieldSerialization.deserializeNonZeroUInt256(buf);
					return new ExittingStake(epochUnlocked, delegate, owner, amount);
				},
				(s, buf) -> {
					REFieldSerialization.serializeReservedByte(buf);
					buf.putLong(s.getEpochUnlocked());
					REFieldSerialization.serializeKey(buf, s.getDelegateKey());
					REFieldSerialization.serializeREAddr(buf, s.getOwner());
					buf.put(s.getAmount().toByteArray());
				}
			)
		);

		registerGenesisTransitions(os);

		// Epoch update
		epochUpdate(os);
	}
}
