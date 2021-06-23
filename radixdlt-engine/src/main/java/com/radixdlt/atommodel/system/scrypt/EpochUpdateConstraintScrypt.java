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

package com.radixdlt.atommodel.system.scrypt;

import com.radixdlt.atom.REFieldSerialization;
import com.radixdlt.atom.SubstateTypeId;
import com.radixdlt.atommodel.system.state.EpochData;
import com.radixdlt.atommodel.system.state.HasEpochData;
import com.radixdlt.atommodel.system.state.RoundData;
import com.radixdlt.atommodel.system.state.StakeOwnership;
import com.radixdlt.atommodel.system.state.ValidatorBFTData;
import com.radixdlt.atommodel.system.state.ValidatorStakeData;
import com.radixdlt.atommodel.tokens.TokenDefinitionUtils;
import com.radixdlt.atommodel.tokens.state.ExittingStake;
import com.radixdlt.atommodel.tokens.state.PreparedStake;
import com.radixdlt.atommodel.tokens.state.PreparedUnstakeOwnership;
import com.radixdlt.atommodel.tokens.state.TokensInAccount;
import com.radixdlt.atommodel.validators.state.ValidatorOwnerCopy;
import com.radixdlt.atommodel.validators.state.PreparedValidatorUpdate;
import com.radixdlt.atommodel.validators.state.RakeCopy;
import com.radixdlt.atommodel.validators.state.PreparedRakeUpdate;
import com.radixdlt.atomos.CMAtomOS;
import com.radixdlt.atomos.ConstraintScrypt;
import com.radixdlt.atomos.Loader;
import com.radixdlt.atomos.SubstateDefinition;
import com.radixdlt.constraintmachine.Authorization;
import com.radixdlt.constraintmachine.DownProcedure;
import com.radixdlt.constraintmachine.PermissionLevel;
import com.radixdlt.constraintmachine.ProcedureException;
import com.radixdlt.constraintmachine.ReducerResult;
import com.radixdlt.constraintmachine.ReducerState;
import com.radixdlt.constraintmachine.ShutdownAll;
import com.radixdlt.constraintmachine.ShutdownAllProcedure;
import com.radixdlt.constraintmachine.UpProcedure;
import com.radixdlt.crypto.ECPublicKey;
import com.radixdlt.identifiers.REAddr;
import com.radixdlt.utils.UInt256;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.function.Function;
import java.util.function.Supplier;

import static com.radixdlt.atommodel.validators.state.PreparedRakeUpdate.RAKE_MAX;

public final class EpochUpdateConstraintScrypt implements ConstraintScrypt {

	public static final UInt256 REWARDS_PER_PROPOSAL = TokenDefinitionUtils.SUB_UNITS.multiply(UInt256.TEN);

	public static final class ProcessExittingStake implements ReducerState {
		private final UpdatingEpoch updatingEpoch;
		private final TreeSet<ExittingStake> exitting = new TreeSet<>(
			(o1, o2) -> Arrays.compare(o1.dataKey(), o2.dataKey())
		);

		ProcessExittingStake(UpdatingEpoch updatingEpoch) {
			this.updatingEpoch = updatingEpoch;
		}

		public ReducerState process(ShutdownAll<ExittingStake> i) throws ProcedureException {
			i.verifyPostTypePrefixIsEmpty();
			i.iterator().forEachRemaining(exitting::add);
			return next();
		}

		public ReducerState unlock(TokensInAccount u) throws ProcedureException {
			var exit = exitting.first();
			exitting.remove(exit);
			if (exit.getEpochUnlocked() != updatingEpoch.prevEpoch.getEpoch()) {
				throw new ProcedureException("Stake must still be locked.");
			}
			var expected = exit.unlock();
			if (!expected.equals(u)) {
				throw new ProcedureException("Expecting next state to be " + expected + " but was " + u);
			}

			return next();
		}

		public ReducerState nextExit(ExittingStake u) throws ProcedureException {
			var first = exitting.first();
			var ownershipUnstake = exitting.remove(first);
			if (!u.equals(first)) {
				throw new ProcedureException("Exitting stake must be equivalent.");
			}
			if (u.getEpochUnlocked() == updatingEpoch.prevEpoch.getEpoch()) {
				throw new ProcedureException("Expecting stake to be unlocked.");
			}
			return next();
		}

		public ReducerState next() {
			return exitting.isEmpty() ? new RewardingValidators(updatingEpoch) : this;
		}
	}

	private static final class RewardingValidators implements ReducerState {
		private final TreeMap<ECPublicKey, ValidatorStakeData> curStake = new TreeMap<>(
			(o1, o2) -> Arrays.compare(o1.getBytes(), o2.getBytes())
		);
		private final TreeMap<ECPublicKey, Long> proposalsCompleted = new TreeMap<>(
			(o1, o2) -> Arrays.compare(o1.getBytes(), o2.getBytes())
		);
		private final TreeMap<ECPublicKey, TreeMap<REAddr, UInt256>> preparingStake = new TreeMap<>(
			(o1, o2) -> Arrays.compare(o1.getBytes(), o2.getBytes())
		);
		private final UpdatingEpoch updatingEpoch;

		RewardingValidators(UpdatingEpoch updatingEpoch) {
			this.updatingEpoch = updatingEpoch;
		}

		public ReducerState process(ShutdownAll<ValidatorBFTData> i) throws ProcedureException {
			i.verifyPostTypePrefixIsEmpty();
			var iter = i.iterator();
			while (iter.hasNext()) {
				var validatorEpochData = iter.next();
				if (proposalsCompleted.containsKey(validatorEpochData.validatorKey())) {
					throw new ProcedureException("Already inserted " + validatorEpochData.validatorKey());
				}
				proposalsCompleted.put(validatorEpochData.validatorKey(), validatorEpochData.proposalsCompleted());
			}

			return next();
		}

		ReducerState next() {
			if (proposalsCompleted.isEmpty()) {
				return new PreparingUnstake(updatingEpoch, curStake, preparingStake);
			}

			var k = proposalsCompleted.firstKey();
			if (curStake.containsKey(k)) {
				throw new IllegalStateException();
			}
			var numProposals = proposalsCompleted.remove(k);
			var nodeEmission = REWARDS_PER_PROPOSAL.multiply(UInt256.from(numProposals));

			return new LoadingStake(k, validatorStakeData -> {
				int rakePercentage = validatorStakeData.getRakePercentage().orElse(RAKE_MAX);
				final UInt256 rakedEmissions;
				if (rakePercentage != 0 && !nodeEmission.isZero()) {
					var rake = nodeEmission
						.multiply(UInt256.from(rakePercentage))
						.divide(UInt256.from(RAKE_MAX));
					var validatorOwner = validatorStakeData.getOwnerAddr().orElseGet(() -> REAddr.ofPubKeyAccount(k));
					var initStake = new TreeMap<REAddr, UInt256>((o1, o2) -> Arrays.compare(o1.getBytes(), o2.getBytes()));
					initStake.put(validatorOwner, rake);
					preparingStake.put(k, initStake);
					rakedEmissions = nodeEmission.subtract(rake);
				} else {
					rakedEmissions = nodeEmission;
				}
				curStake.put(k, validatorStakeData.addEmission(rakedEmissions));
				return next();
			});
		}
	}

	public static final class CreatingNextValidatorSet implements ReducerState {
		private final Set<ECPublicKey> validators = new HashSet<>();
		private final UpdatingEpoch updatingEpoch;

		CreatingNextValidatorSet(UpdatingEpoch updatingEpoch) {
			this.updatingEpoch = updatingEpoch;
		}

		ReducerState nextValidator(ValidatorBFTData u) throws ProcedureException {
			if (validators.contains(u.validatorKey())) {
				throw new ProcedureException("Already in set: " + u.validatorKey());
			}
			if (u.proposalsCompleted() != 0) {
				throw new ProcedureException("Proposals completed must be 0");
			}
			validators.add(u.validatorKey());
			return this;
		}

		ReducerState nextEpoch(EpochData epochData) throws ProcedureException {
			return updatingEpoch.nextEpoch(epochData);
		}
	}

	public static final class UpdatingEpoch implements ReducerState {
		private final HasEpochData prevEpoch;

		UpdatingEpoch(HasEpochData prevEpoch) {
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

	public static final class StartingEpochRound implements ReducerState {
	}


	private static final class Unstaking implements ReducerState {
		private final UpdatingEpoch updatingEpoch;
		private final TreeMap<REAddr, UInt256> unstaking;
		private final Function<ValidatorStakeData, ReducerState> onDone;
		private ValidatorStakeData current;

		Unstaking(
			UpdatingEpoch updatingEpoch,
			ValidatorStakeData current,
			TreeMap<REAddr, UInt256> unstaking,
			Function<ValidatorStakeData, ReducerState> onDone
		) {
			this.updatingEpoch = updatingEpoch;
			this.current = current;
			this.unstaking = unstaking;
			this.onDone = onDone;
		}

		ReducerState exit(ExittingStake u) throws ProcedureException {
			var firstAddr = unstaking.firstKey();
			var ownershipUnstake = unstaking.remove(firstAddr);
			var nextValidatorAndExit = current.unstakeOwnership(
				firstAddr, ownershipUnstake, updatingEpoch.prevEpoch.getEpoch()
			);
			this.current = nextValidatorAndExit.getFirst();
			var expectedExit = nextValidatorAndExit.getSecond();
			if (!u.equals(expectedExit)) {
				throw new ProcedureException("Invalid exit expected " + expectedExit + " but was " + u);
			}

			return unstaking.isEmpty() ? onDone.apply(current) : this;
		}
	}

	private static final class PreparingUnstake implements ReducerState {
		private final UpdatingEpoch updatingEpoch;
		private final TreeMap<ECPublicKey, TreeMap<REAddr, UInt256>> preparingUnstake = new TreeMap<>(
			(o1, o2) -> Arrays.compare(o1.getBytes(), o2.getBytes())
		);
		private final TreeMap<ECPublicKey, TreeMap<REAddr, UInt256>> preparingStake;
		private final TreeMap<ECPublicKey, ValidatorStakeData> curStake;

		PreparingUnstake(
			UpdatingEpoch updatingEpoch,
			TreeMap<ECPublicKey, ValidatorStakeData> curStake,
			TreeMap<ECPublicKey, TreeMap<REAddr, UInt256>> preparingStake
		) {
			this.updatingEpoch = updatingEpoch;
			this.curStake = curStake;
			this.preparingStake = preparingStake;
		}

		ReducerState unstakes(ShutdownAll<PreparedUnstakeOwnership> i) throws ProcedureException {
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
				return new PreparingStake(updatingEpoch, curStake, preparingStake);
			}

			var k = preparingUnstake.firstKey();
			var unstakes = preparingUnstake.remove(k);

			if (!curStake.containsKey(k)) {
				return new LoadingStake(k, validatorStake ->
					new Unstaking(updatingEpoch, validatorStake, unstakes, s -> {
						curStake.put(k, s);
						return next();
					})
				);
			} else {
				var validatorStake = curStake.get(k);
				return new Unstaking(updatingEpoch, validatorStake, unstakes, s -> {
					curStake.put(k, s);
					return next();
				});
			}
		}
	}

	private static final class Staking implements ReducerState {
		private ValidatorStakeData validatorStake;
		private final TreeMap<REAddr, UInt256> stakes;
		private final Function<ValidatorStakeData, ReducerState> onDone;

		Staking(ValidatorStakeData validatorStake, TreeMap<REAddr, UInt256> stakes, Function<ValidatorStakeData, ReducerState> onDone) {
			this.validatorStake = validatorStake;
			this.stakes = stakes;
			this.onDone = onDone;
		}

		ReducerState stake(StakeOwnership stakeOwnership) throws ProcedureException {
			if (!Objects.equals(validatorStake.getValidatorKey(), stakeOwnership.getDelegateKey())) {
				throw new ProcedureException("Invalid update");
			}
			var accountAddr = stakes.firstKey();
			if (!Objects.equals(stakeOwnership.getOwner(), accountAddr)) {
				throw new ProcedureException(stakeOwnership + " is not the first addr in " + stakes);
			}
			var stakeAmt = stakes.remove(accountAddr);
			var nextValidatorAndOwnership = validatorStake.stake(accountAddr, stakeAmt);
			this.validatorStake = nextValidatorAndOwnership.getFirst();
			var expectedOwnership = nextValidatorAndOwnership.getSecond();
			if (!Objects.equals(stakeOwnership, expectedOwnership)) {
				throw new ProcedureException(
					String.format("Amount (%s) does not match what is prepared (%s)", stakeOwnership.getAmount(), stakeAmt)
				);
			}
			return stakes.isEmpty() ? onDone.apply(this.validatorStake) : this;
		}
	}

	private static final class LoadingStake implements ReducerState {
		private final ECPublicKey key;
		private final Function<ValidatorStakeData, ReducerState> onDone;

		LoadingStake(ECPublicKey key, Function<ValidatorStakeData, ReducerState> onDone) {
			this.key = key;
			this.onDone = onDone;
		}

		ReducerState startUpdate(ValidatorStakeData stake) throws ProcedureException {
			if (!stake.getValidatorKey().equals(key)) {
				throw new ProcedureException("Invalid stake load");
			}
			return onDone.apply(stake);
		}
	}

	private static final class PreparingStake implements ReducerState {
		private final UpdatingEpoch updatingEpoch;
		private final TreeMap<ECPublicKey, ValidatorStakeData> curStake;
		private final TreeMap<ECPublicKey, TreeMap<REAddr, UInt256>> preparingStake;

		PreparingStake(
			UpdatingEpoch updatingEpoch,
			TreeMap<ECPublicKey, ValidatorStakeData> curStake,
			TreeMap<ECPublicKey, TreeMap<REAddr, UInt256>> preparingStake
		) {
			this.curStake = curStake;
			this.updatingEpoch = updatingEpoch;
			this.preparingStake = preparingStake;
		}

		ReducerState prepareStakes(ShutdownAll<PreparedStake> i) throws ProcedureException {
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
				return new PreparingRakeUpdate(updatingEpoch, curStake);
			}

			var k = preparingStake.firstKey();
			var stakes = preparingStake.remove(k);
			if (!curStake.containsKey(k)) {
				return new LoadingStake(k, validatorStake ->
					new Staking(validatorStake, stakes, updated -> {
						curStake.put(k, updated);
						return this.next();
					})
				);
			} else {
				return new Staking(curStake.get(k), stakes, updated -> {
					curStake.put(k, updated);
					return this.next();
				});
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

		ReducerState reset(RakeCopy rakeCopy) throws ProcedureException {
			if (!rakeCopy.getValidatorKey().equals(update.getValidatorKey())) {
				throw new ProcedureException("Validator keys must match.");
			}

			if (rakeCopy.getCurRakePercentage() != update.getNextRakePercentage()) {
				throw new ProcedureException("Rake percentage must match.");
			}

			return next.get();
		}
	}

	private static final class PreparingRakeUpdate implements ReducerState {
		private final UpdatingEpoch updatingEpoch;
		private final TreeMap<ECPublicKey, ValidatorStakeData> validatorsToUpdate;
		private final TreeMap<ECPublicKey, PreparedRakeUpdate> preparingRakeUpdates =
			new TreeMap<>((o1, o2) -> Arrays.compare(o1.getBytes(), o2.getBytes()));

		PreparingRakeUpdate(
			UpdatingEpoch updatingEpoch,
			TreeMap<ECPublicKey, ValidatorStakeData> validatorsToUpdate
		) {
			this.updatingEpoch = updatingEpoch;
			this.validatorsToUpdate = validatorsToUpdate;
		}

		ReducerState prepareRakeUpdates(ShutdownAll<PreparedRakeUpdate> shutdownAll) throws ProcedureException {
			var expectedEpoch = updatingEpoch.prevEpoch.getEpoch() + 1;
			shutdownAll.verifyPostTypePrefixEquals(expectedEpoch);
			var iter = shutdownAll.iterator();
			while (iter.hasNext()) {
				var preparedRakeUpdate = iter.next();
				preparingRakeUpdates.put(preparedRakeUpdate.getValidatorKey(), preparedRakeUpdate);
			}
			return next();
		}

		ReducerState next() {
			if (preparingRakeUpdates.isEmpty()) {
				return new PreparingValidatorUpdate(updatingEpoch, validatorsToUpdate);
			}

			var k = preparingRakeUpdates.firstKey();
			var validatorUpdate = preparingRakeUpdates.remove(k);
			if (!validatorsToUpdate.containsKey(k)) {
				return new LoadingStake(k, validatorStake -> {
					var updatedValidator = validatorStake.setRakePercentage(validatorUpdate.getNextRakePercentage());
					validatorsToUpdate.put(k, updatedValidator);
					return new ResetRakeUpdate(validatorUpdate, this::next);
				});
			} else {
				var updatedValidator = validatorsToUpdate.get(k)
					.setRakePercentage(validatorUpdate.getNextRakePercentage());
				validatorsToUpdate.put(k, updatedValidator);
				return new ResetRakeUpdate(validatorUpdate, this::next);
			}
		}
	}
	private static final class ResetValidatorUpdate implements ReducerState {
		private final ECPublicKey validatorKey;
		private final Supplier<ReducerState> next;

		ResetValidatorUpdate(ECPublicKey validatorKey, Supplier<ReducerState> next) {
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

	private static final class PreparingValidatorUpdate implements ReducerState {
		private final UpdatingEpoch updatingEpoch;
		private final TreeMap<ECPublicKey, ValidatorStakeData> validatorsToUpdate;
		private final TreeMap<ECPublicKey, PreparedValidatorUpdate> preparingValidatorUpdates =
			new TreeMap<>((o1, o2) -> Arrays.compare(o1.getBytes(), o2.getBytes()));

		PreparingValidatorUpdate(
			UpdatingEpoch updatingEpoch,
			TreeMap<ECPublicKey, ValidatorStakeData> validatorsToUpdate
		) {
			this.updatingEpoch = updatingEpoch;
			this.validatorsToUpdate = validatorsToUpdate;
		}

		ReducerState prepareValidatorUpdate(ShutdownAll<PreparedValidatorUpdate> shutdownAll) throws ProcedureException {
			shutdownAll.verifyPostTypePrefixIsEmpty();
			var iter = shutdownAll.iterator();
			while (iter.hasNext()) {
				var preparedValidatorUpdate = iter.next();
				preparingValidatorUpdates.put(preparedValidatorUpdate.getValidatorKey(), preparedValidatorUpdate);
			}
			return next();
		}

		ReducerState next() {
			if (preparingValidatorUpdates.isEmpty()) {
				return validatorsToUpdate.isEmpty()
					? new CreatingNextValidatorSet(updatingEpoch)
					: new UpdatingValidatorStakes(updatingEpoch, validatorsToUpdate);
			}

			var k = preparingValidatorUpdates.firstKey();
			var validatorUpdate = preparingValidatorUpdates.remove(k);
			if (!validatorsToUpdate.containsKey(k)) {
				return new LoadingStake(k, validatorStake -> {
					var updatedValidator = validatorStake.setOwnerAddr(validatorUpdate.getOwnerAddress());
					validatorsToUpdate.put(k, updatedValidator);
					return new ResetValidatorUpdate(k, this::next);
				});
			} else {
				var updatedValidator = validatorsToUpdate.get(k)
					.setOwnerAddr(validatorUpdate.getOwnerAddress());
				validatorsToUpdate.put(k, updatedValidator);
				return new ResetValidatorUpdate(k, this::next);
			}
		}
	}

	private static final class UpdatingValidatorStakes implements ReducerState {
		private final UpdatingEpoch updatingEpoch;
		private final TreeMap<ECPublicKey, ValidatorStakeData> curStake;
		UpdatingValidatorStakes(UpdatingEpoch updatingEpoch, TreeMap<ECPublicKey, ValidatorStakeData> curStake) {
			this.updatingEpoch = updatingEpoch;
			this.curStake = curStake;
		}

		ReducerState updateStake(ValidatorStakeData stake) throws ProcedureException {
			var k = curStake.firstKey();
			if (!stake.getValidatorKey().equals(k)) {
				throw new ProcedureException("First key does not match.");
			}

			var expectedUpdate = curStake.remove(k);
			if (!stake.equals(expectedUpdate)) {
				throw new ProcedureException("Stake amount does not match Expected: " + expectedUpdate + " Actual: " + stake);
			}

			return curStake.isEmpty() ? new CreatingNextValidatorSet(updatingEpoch) : this;
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
			RoundClosed.class, EpochData.class,
			d -> new Authorization(PermissionLevel.SUPER_USER, (r, c) -> { }),
			(d, s, r) -> ReducerResult.incomplete(new UpdatingEpoch(d.getSubstate()))
		));

		os.procedure(new ShutdownAllProcedure<>(
			ExittingStake.class, UpdatingEpoch.class,
			() -> new Authorization(PermissionLevel.SUPER_USER, (r, c) -> { }),
			(i, s, r) -> {
				var exittingStake = new ProcessExittingStake(s);
				return ReducerResult.incomplete(exittingStake.process(i));
			}
		));
		os.procedure(new UpProcedure<>(
			ProcessExittingStake.class, ExittingStake.class,
			u -> new Authorization(PermissionLevel.SUPER_USER, (r, c) -> { }),
			(s, u, c, r) -> ReducerResult.incomplete(s.nextExit(u))
		));
		os.procedure(new UpProcedure<>(
			ProcessExittingStake.class, TokensInAccount.class,
			u -> new Authorization(PermissionLevel.SUPER_USER, (r, c) -> { }),
			(s, u, c, r) -> ReducerResult.incomplete(s.unlock(u))
		));

		os.procedure(new ShutdownAllProcedure<>(
			ValidatorBFTData.class, RewardingValidators.class,
			() -> new Authorization(PermissionLevel.SUPER_USER, (r, c) -> { }),
			(i, s, r) -> ReducerResult.incomplete(s.process(i))
		));

		os.procedure(new ShutdownAllProcedure<>(
			PreparedUnstakeOwnership.class, PreparingUnstake.class,
			() -> new Authorization(PermissionLevel.SUPER_USER, (r, c) -> { }),
			(i, s, r) -> ReducerResult.incomplete(s.unstakes(i))
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
			(i, s, r) -> ReducerResult.incomplete(s.prepareStakes(i))
		));
		os.procedure(new ShutdownAllProcedure<>(
			PreparedRakeUpdate.class, PreparingRakeUpdate.class,
			() -> new Authorization(PermissionLevel.SUPER_USER, (r, c) -> { }),
			(i, s, r) -> ReducerResult.incomplete(s.prepareRakeUpdates(i))
		));
		os.procedure(new UpProcedure<>(
			ResetRakeUpdate.class, RakeCopy.class,
			u -> new Authorization(PermissionLevel.SUPER_USER, (r, c) -> { }),
			(s, u, c, r) -> ReducerResult.incomplete(s.reset(u))
		));

		os.procedure(new ShutdownAllProcedure<>(
			PreparedValidatorUpdate.class, PreparingValidatorUpdate.class,
			() -> new Authorization(PermissionLevel.SUPER_USER, (r, c) -> { }),
			(i, s, r) -> ReducerResult.incomplete(s.prepareValidatorUpdate(i))
		));
		os.procedure(new UpProcedure<>(
			ResetValidatorUpdate.class, ValidatorOwnerCopy.class,
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

		os.procedure(new UpProcedure<>(
			CreatingNextValidatorSet.class, ValidatorBFTData.class,
			u -> new Authorization(PermissionLevel.SUPER_USER, (r, c) -> { }),
			(s, u, c, r) -> ReducerResult.incomplete(s.nextValidator(u))
		));

		os.procedure(new UpProcedure<>(
			CreatingNextValidatorSet.class, EpochData.class,
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
				RoundData.class,
				Set.of(SubstateTypeId.ROUND_DATA.id()),
				(b, buf) -> {
					var view = REFieldSerialization.deserializeNonNegativeLong(buf);
					var timestamp = REFieldSerialization.deserializeNonNegativeLong(buf);
					return new RoundData(view, timestamp);
				},
				(s, buf) -> {
					buf.put(SubstateTypeId.ROUND_DATA.id());
					buf.putLong(s.getView());
					buf.putLong(s.getTimestamp());
				},
				p -> p.getView() == 0 && p.getTimestamp() == 0
			)
		);
		os.substate(
			new SubstateDefinition<>(
				EpochData.class,
				Set.of(SubstateTypeId.EPOCH_DATA.id()),
				(b, buf) -> {
					var epoch = REFieldSerialization.deserializeNonNegativeLong(buf);
					return new EpochData(epoch);
				},
				(s, buf) -> {
					buf.put(SubstateTypeId.EPOCH_DATA.id());
					buf.putLong(s.getEpoch());
				}
			)
		);
		os.substate(
			new SubstateDefinition<>(
				ValidatorStakeData.class,
				Set.of(SubstateTypeId.STAKE_V1.id(), SubstateTypeId.STAKE_V2.id()),
				(b, buf) -> {
					var delegate = REFieldSerialization.deserializeKey(buf);
					var amount = REFieldSerialization.deserializeUInt256(buf);
					var ownership = REFieldSerialization.deserializeUInt256(buf);
					if (b.equals(SubstateTypeId.STAKE_V1.id())) {
						return ValidatorStakeData.createV1(delegate, amount, ownership);
					} else {
						var rakePercentage = REFieldSerialization.deserializeInt(buf);
						var ownerAddress = REFieldSerialization.deserializeREAddr(buf);
						return ValidatorStakeData.createV2(delegate, amount, ownership, rakePercentage, ownerAddress);
					}
				},
				(s, buf) -> {
					if (s.getRakePercentage().isEmpty()) {
						buf.put(SubstateTypeId.STAKE_V1.id());
					} else {
						buf.put(SubstateTypeId.STAKE_V2.id());
					}
					REFieldSerialization.serializeKey(buf, s.getValidatorKey());
					buf.put(s.getAmount().toByteArray());
					buf.put(s.getTotalOwnership().toByteArray());
					if (s.getRakePercentage().isPresent()) {
						buf.putInt(s.getRakePercentage().orElseThrow());
						REFieldSerialization.serializeREAddr(buf, s.getOwnerAddr().orElseThrow());
					}
				},
				s -> s.getAmount().isZero() && s.getTotalOwnership().isZero()
					&& s.getRakePercentage().isEmpty() && s.getOwnerAddr().isEmpty()
			)
		);
		os.substate(
			new SubstateDefinition<>(
				StakeOwnership.class,
				Set.of(SubstateTypeId.STAKE_OWNERSHIP.id()),
				(b, buf) -> {
					var delegate = REFieldSerialization.deserializeKey(buf);
					var owner = REFieldSerialization.deserializeREAddr(buf);
					var amount = REFieldSerialization.deserializeNonZeroUInt256(buf);
					return new StakeOwnership(delegate, owner, amount);
				},
				(s, buf) -> {
					buf.put(SubstateTypeId.STAKE_OWNERSHIP.id());
					REFieldSerialization.serializeKey(buf, s.getDelegateKey());
					REFieldSerialization.serializeREAddr(buf, s.getOwner());
					buf.put(s.getAmount().toByteArray());
				}
			)
		);
		os.substate(
			new SubstateDefinition<>(
				ExittingStake.class,
				Set.of(SubstateTypeId.EXITTING_STAKE.id()),
				(b, buf) -> {
					var epochUnlocked = REFieldSerialization.deserializeNonNegativeLong(buf);
					var delegate = REFieldSerialization.deserializeKey(buf);
					var owner = REFieldSerialization.deserializeREAddr(buf);
					var amount = REFieldSerialization.deserializeNonZeroUInt256(buf);
					return new ExittingStake(delegate, owner, epochUnlocked, amount);
				},
				(s, buf) -> {
					buf.put(SubstateTypeId.EXITTING_STAKE.id());
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
