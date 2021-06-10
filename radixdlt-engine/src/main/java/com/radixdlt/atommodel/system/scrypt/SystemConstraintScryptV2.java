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

import com.radixdlt.atom.RESerializer;
import com.radixdlt.atommodel.system.state.EpochData;
import com.radixdlt.atommodel.system.state.HasEpochData;
import com.radixdlt.atommodel.system.state.RoundData;
import com.radixdlt.atommodel.system.state.ValidatorStake;
import com.radixdlt.atommodel.system.state.StakeOwnership;
import com.radixdlt.atommodel.system.state.ValidatorEpochData;
import com.radixdlt.atommodel.tokens.TokenDefinitionUtils;
import com.radixdlt.atommodel.tokens.state.ExittingStake;
import com.radixdlt.atommodel.tokens.state.PreparedStake;
import com.radixdlt.atommodel.tokens.state.PreparedUnstakeOwnership;
import com.radixdlt.atommodel.tokens.state.TokensInAccount;
import com.radixdlt.atomos.CMAtomOS;
import com.radixdlt.atomos.ConstraintScrypt;
import com.radixdlt.atomos.SubstateDefinition;
import com.radixdlt.atomos.Loader;
import com.radixdlt.constraintmachine.Authorization;
import com.radixdlt.constraintmachine.PermissionLevel;
import com.radixdlt.constraintmachine.ProcedureException;
import com.radixdlt.constraintmachine.ReducerResult;
import com.radixdlt.constraintmachine.ReducerState;
import com.radixdlt.constraintmachine.DownProcedure;
import com.radixdlt.constraintmachine.ShutdownAllProcedure;
import com.radixdlt.constraintmachine.UpProcedure;
import com.radixdlt.constraintmachine.VoidReducerState;
import com.radixdlt.crypto.ECPublicKey;
import com.radixdlt.identifiers.REAddr;
import com.radixdlt.utils.UInt256;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.function.Function;

public class SystemConstraintScryptV2 implements ConstraintScrypt {

	public static final UInt256 REWARDS_PER_PROPOSAL = TokenDefinitionUtils.SUB_UNITS.multiply(UInt256.TEN);

	public static class UpdateValidatorEpochData implements ReducerState {
	}

	private static class UpdatingValidatorEpochData implements ReducerState {
		private final ValidatorEpochData current;
		private UpdatingValidatorEpochData(ValidatorEpochData current) {
			this.current = current;
		}

		public void update(ValidatorEpochData next) throws ProcedureException {
			if (!next.validatorKey().equals(current.validatorKey())) {
				throw new ProcedureException("Must update same validator key");
			}
			if (current.proposalsCompleted() + 1 != next.proposalsCompleted()) {
				throw new ProcedureException("Must only increment proposals completed");
			}
		}
	}

	public static final class ProcessExittingStake implements ReducerState {
		private final UpdatingEpoch updatingEpoch;
		private final TreeSet<ExittingStake> exitting = new TreeSet<>(
			(o1, o2) -> Arrays.compare(o1.dataKey(), o2.dataKey())
		);

		ProcessExittingStake(UpdatingEpoch updatingEpoch) {
			this.updatingEpoch = updatingEpoch;
		}

		public ReducerState process(Iterator<ExittingStake> i) throws ProcedureException {
			i.forEachRemaining(exitting::add);

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

	public static final class RewardingValidators implements ReducerState {
		private final TreeMap<ECPublicKey, ValidatorStake> curStake = new TreeMap<>(
			(o1, o2) -> Arrays.compare(o1.getBytes(), o2.getBytes())
		);
		private final TreeMap<ECPublicKey, Long> proposalsCompleted = new TreeMap<>(
			(o1, o2) -> Arrays.compare(o1.getBytes(), o2.getBytes())
		);
		private final UpdatingEpoch updatingEpoch;

		RewardingValidators(UpdatingEpoch updatingEpoch) {
			this.updatingEpoch = updatingEpoch;
		}

		public ReducerState process(Iterator<ValidatorEpochData> i) throws ProcedureException {
			while (i.hasNext()) {
				var validatorEpochData = i.next();
				if (proposalsCompleted.containsKey(validatorEpochData.validatorKey())) {
					throw new ProcedureException("Already inserted " + validatorEpochData.validatorKey());
				}
				proposalsCompleted.put(validatorEpochData.validatorKey(), validatorEpochData.proposalsCompleted());
			}

			return next();
		}

		ReducerState next() {
			if (proposalsCompleted.isEmpty()) {
				return new PreparingUnstake(updatingEpoch, curStake);
			}

			var k = proposalsCompleted.firstKey();
			if (curStake.containsKey(k)) {
				throw new IllegalStateException();
			}
			var numProposals = proposalsCompleted.remove(k);
			var emission = SystemConstraintScryptV2.REWARDS_PER_PROPOSAL.multiply(UInt256.from(numProposals));
			return new LoadingStake(k, amt -> {
				curStake.put(k, amt.addEmission(emission));
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

		ReducerState nextValidator(ValidatorEpochData u) throws ProcedureException {
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

	private static final class RoundClosed implements ReducerState {
		private final RoundData prev;
		private RoundClosed(RoundData prev) {
			this.prev = prev;
		}
	}

	private static final class Unstaking implements ReducerState {
		private final UpdatingEpoch updatingEpoch;
		private final TreeMap<REAddr, UInt256> unstaking;
		private final Function<ValidatorStake, ReducerState> onDone;
		private ValidatorStake current;

		Unstaking(
			UpdatingEpoch updatingEpoch,
			ValidatorStake current,
			TreeMap<REAddr, UInt256> unstaking,
			Function<ValidatorStake, ReducerState> onDone
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

		private final TreeMap<ECPublicKey, ValidatorStake> curStake;

		PreparingUnstake(UpdatingEpoch updatingEpoch, TreeMap<ECPublicKey, ValidatorStake> curStake) {
			this.updatingEpoch = updatingEpoch;
			this.curStake = curStake;
		}

		ReducerState unstakes(Iterator<PreparedUnstakeOwnership> preparedUnstakeIterator) {
			preparedUnstakeIterator.forEachRemaining(preparedUnstakeOwned ->
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
				return new PreparingStake(updatingEpoch, curStake);
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
		private ValidatorStake validatorStake;
		private final TreeMap<REAddr, UInt256> stakes;
		private final Function<ValidatorStake, ReducerState> onDone;

		Staking(ValidatorStake validatorStake, TreeMap<REAddr, UInt256> stakes, Function<ValidatorStake, ReducerState> onDone) {
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
				throw new ProcedureException(stakeOwnership.getOwner() + " is not the first addr (" + accountAddr + ")");
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
		private final Function<ValidatorStake, ReducerState> onDone;

		LoadingStake(ECPublicKey key, Function<ValidatorStake, ReducerState> onDone) {
			this.key = key;
			this.onDone = onDone;
		}

		ReducerState startUpdate(ValidatorStake stake) throws ProcedureException {
			if (!stake.getValidatorKey().equals(key)) {
				throw new ProcedureException("Invalid stake load");
			}
			return onDone.apply(stake);
		}
	}

	private static final class PreparingStake implements ReducerState {
		private final UpdatingEpoch updatingEpoch;
		private final TreeMap<ECPublicKey, ValidatorStake> curStake;
		private final TreeMap<ECPublicKey, TreeMap<REAddr, UInt256>> preparingStake = new TreeMap<>(
			(o1, o2) -> Arrays.compare(o1.getBytes(), o2.getBytes())
		);
		PreparingStake(UpdatingEpoch updatingEpoch, TreeMap<ECPublicKey, ValidatorStake> curStake) {
			this.curStake = curStake;
			this.updatingEpoch = updatingEpoch;
		}

		ReducerState prepareStakes(Iterator<PreparedStake> preparedStakeIterator) {
			preparedStakeIterator.forEachRemaining(preparedStake ->
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
				return curStake.isEmpty()
					? new CreatingNextValidatorSet(updatingEpoch)
					: new UpdatingValidatorStakes(updatingEpoch, curStake);
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

	private static final class UpdatingValidatorStakes implements ReducerState {
		private final UpdatingEpoch updatingEpoch;
		private final TreeMap<ECPublicKey, ValidatorStake> curStake;
		UpdatingValidatorStakes(UpdatingEpoch updatingEpoch, TreeMap<ECPublicKey, ValidatorStake> curStake) {
			this.updatingEpoch = updatingEpoch;
			this.curStake = curStake;
		}

		ReducerState updateStake(ValidatorStake stake) throws ProcedureException {
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
			u -> new Authorization(PermissionLevel.SUPER_USER, (r, c) -> { }),
			(s, u, c, r) -> {
				if (u.getEpoch() != 0) {
					throw new ProcedureException("First epoch must be 0.");
				}

				return ReducerResult.incomplete(new AllocatingSystem());
			}
		));
		os.procedure(new UpProcedure<>(
			AllocatingSystem.class, RoundData.class,
			u -> new Authorization(PermissionLevel.SUPER_USER, (r, c) -> { }),
			(s, u, c, r) -> {
				if (u.getView() != 0) {
					throw new ProcedureException("First view must be 0.");
				}
				return ReducerResult.complete();
			}
		));
	}


	private void roundUpdate(Loader os) {
		// Round update
		os.procedure(new DownProcedure<>(
			RoundData.class, VoidReducerState.class,
			d -> new Authorization(PermissionLevel.SUPER_USER, (r, c) -> { }),
			(d, s, r) -> ReducerResult.incomplete(new RoundClosed(d.getSubstate()))
		));
		os.procedure(new UpProcedure<>(
			RoundClosed.class, RoundData.class,
			u -> new Authorization(PermissionLevel.SUPER_USER, (r, c) -> { }),
			(s, u, c, r) -> {
				var curData = s.prev;
				if (curData.getView() >= u.getView()) {
					throw new ProcedureException("Next view must be greater than previous.");
				}

				return ReducerResult.incomplete(new UpdateValidatorEpochData());
			}
		));
		os.procedure(new DownProcedure<>(
			ValidatorEpochData.class, UpdateValidatorEpochData.class,
			d -> new Authorization(PermissionLevel.SUPER_USER, (r, c) -> { }),
			(d, s, r) -> ReducerResult.incomplete(new UpdatingValidatorEpochData(d.getSubstate()))
		));
		os.procedure(new UpProcedure<>(
			UpdatingValidatorEpochData.class, ValidatorEpochData.class,
			u -> new Authorization(PermissionLevel.SUPER_USER, (r, c) -> { }),
			(s, u, c, r) -> {
				s.update(u);
				return ReducerResult.complete();
			}
		));
	}

	private void epochUpdate(Loader os) {
		// Epoch Update
		os.procedure(new DownProcedure<>(
			EpochData.class, RoundClosed.class,
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
			ValidatorEpochData.class, RewardingValidators.class,
			() -> new Authorization(PermissionLevel.SUPER_USER, (r, c) -> { }),
			(i, s, r) -> ReducerResult.incomplete(s.process(i))
		));

		os.procedure(new ShutdownAllProcedure<>(
			PreparedUnstakeOwnership.class, PreparingUnstake.class,
			() -> new Authorization(PermissionLevel.SUPER_USER, (r, c) -> { }),
			(i, s, r) -> ReducerResult.incomplete(s.unstakes(i))
		));
		os.procedure(new DownProcedure<>(
			ValidatorStake.class, LoadingStake.class,
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
		os.procedure(new UpProcedure<>(
			Staking.class, StakeOwnership.class,
			u -> new Authorization(PermissionLevel.SUPER_USER, (r, c) -> { }),
			(s, u, c, r) -> ReducerResult.incomplete(s.stake(u))
		));
		os.procedure(new UpProcedure<>(
			UpdatingValidatorStakes.class, ValidatorStake.class,
			u -> new Authorization(PermissionLevel.SUPER_USER, (r, c) -> { }),
			(s, u, c, r) -> ReducerResult.incomplete(s.updateStake(u))
		));

		os.procedure(new UpProcedure<>(
			CreatingNextValidatorSet.class, ValidatorEpochData.class,
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
				Set.of(RESerializer.SubstateType.ROUND_DATA.id()),
				(b, buf) -> {
					var view = RESerializer.deserializeNonNegativeLong(buf);
					var timestamp = RESerializer.deserializeNonNegativeLong(buf);
					return new RoundData(view, timestamp);
				},
				p -> p.getView() == 0 && p.getTimestamp() == 0
			)
		);
		os.substate(
			new SubstateDefinition<>(
				EpochData.class,
				Set.of(RESerializer.SubstateType.EPOCH_DATA.id()),
				(b, buf) -> {
					var epoch = RESerializer.deserializeNonNegativeLong(buf);
					return new EpochData(epoch);
				}
			)
		);
		os.substate(
			new SubstateDefinition<>(
				ValidatorStake.class,
				Set.of(RESerializer.SubstateType.STAKE.id()),
				(b, buf) -> {
					var delegate = RESerializer.deserializeKey(buf);
					var amount = RESerializer.deserializeUInt256(buf);
					var ownership = RESerializer.deserializeUInt256(buf);
					return ValidatorStake.create(delegate, amount, ownership);
				},
				s -> s.getAmount().isZero() && s.getTotalOwnership().isZero()
			)
		);
		os.substate(
			new SubstateDefinition<>(
				StakeOwnership.class,
				Set.of(RESerializer.SubstateType.STAKE_OWNERSHIP.id()),
				(b, buf) -> {
					var delegate = RESerializer.deserializeKey(buf);
					var owner = RESerializer.deserializeREAddr(buf);
					var amount = RESerializer.deserializeNonZeroUInt256(buf);
					return new StakeOwnership(delegate, owner, amount);
				}
			)
		);
		os.substate(
			new SubstateDefinition<>(
				ExittingStake.class,
				Set.of(RESerializer.SubstateType.EXITTING_STAKE.id()),
				(b, buf) -> {
					var epochUnlocked = RESerializer.deserializeNonNegativeLong(buf);
					var delegate = RESerializer.deserializeKey(buf);
					var owner = RESerializer.deserializeREAddr(buf);
					var amount = RESerializer.deserializeNonZeroUInt256(buf);
					return new ExittingStake(delegate, owner, epochUnlocked, amount);
				}
			)
		);
		os.substate(
			new SubstateDefinition<>(
				ValidatorEpochData.class,
				Set.of(RESerializer.SubstateType.VALIDATOR_EPOCH_DATA.id()),
				(b, buf) -> {
					var key = RESerializer.deserializeKey(buf);
					var proposalsCompleted = RESerializer.deserializeNonNegativeLong(buf);
					return new ValidatorEpochData(key, proposalsCompleted);
				}
			)
		);

		registerGenesisTransitions(os);

		// Epoch update
		epochUpdate(os);

		// Round update
		roundUpdate(os);
	}
}
