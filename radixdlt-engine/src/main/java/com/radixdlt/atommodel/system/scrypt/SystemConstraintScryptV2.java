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

import com.radixdlt.atom.actions.Unknown;
import com.radixdlt.atommodel.system.state.EpochData;
import com.radixdlt.atommodel.system.state.HasEpochData;
import com.radixdlt.atommodel.system.state.RoundData;
import com.radixdlt.atommodel.system.state.ValidatorStake;
import com.radixdlt.atommodel.system.state.StakeOwnership;
import com.radixdlt.atommodel.system.state.ValidatorEpochData;
import com.radixdlt.atommodel.tokens.TokenDefinitionUtils;
import com.radixdlt.atommodel.tokens.state.PreparedStake;
import com.radixdlt.atommodel.tokens.state.PreparedUnstakeOwned;
import com.radixdlt.atommodel.tokens.state.TokensInAccount;
import com.radixdlt.atomos.CMAtomOS;
import com.radixdlt.atomos.ConstraintScrypt;
import com.radixdlt.atomos.ParticleDefinition;
import com.radixdlt.atomos.Result;
import com.radixdlt.atomos.SysCalls;
import com.radixdlt.constraintmachine.AuthorizationException;
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
import java.util.function.Function;

public class SystemConstraintScryptV2 implements ConstraintScrypt {
	public static final int EPOCHS_LOCKED = 1; // Must go through one full epoch before being unlocked

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

	public SystemConstraintScryptV2() {
		// Nothing here right now
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

		ReducerState unstake(TokensInAccount u) throws ProcedureException {
			if (!u.getResourceAddr().isNativeToken()) {
				throw new ProcedureException("Can only destake to the native token.");
			}
			var epochUnlocked = u.getEpochUnlocked()
				.orElseThrow(() -> new ProcedureException("Exiting from stake must be locked."));

			var firstAddr = unstaking.firstKey();
			if (!Objects.equals(u.getHoldingAddr(), firstAddr)) {
				throw new ProcedureException("Unstaking incorrect addr");
			}
			var ownershipUnstake = unstaking.remove(firstAddr);
			var nextValidatorAndAmt = current.unstakeOwnership(ownershipUnstake);
			this.current = nextValidatorAndAmt.getFirst();
			var expectedAmt = nextValidatorAndAmt.getSecond();
			if (!expectedAmt.equals(u.getAmount())) {
				throw new ProcedureException("Invalid amount");
			}

			var expectedEpochUnlock = updatingEpoch.prevEpoch.getEpoch() + 1 + EPOCHS_LOCKED;
			if (expectedEpochUnlock != epochUnlocked) {
				throw new ProcedureException("Incorrect epoch unlock: " + epochUnlocked
					+ " should be: " + expectedEpochUnlock);
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

		ReducerState unstakes(Iterator<PreparedUnstakeOwned> preparedUnstakeIterator) {
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
				throw new ProcedureException("Stake amount does not match.");
			}

			return curStake.isEmpty() ? new CreatingNextValidatorSet(updatingEpoch) : this;
		}
	}

	private static class AllocatingSystem implements ReducerState {
	}

	private void registerGenesisTransitions(SysCalls os) {
		// For Mainnet Genesis
		os.createUpProcedure(new UpProcedure<>(
			CMAtomOS.REAddrClaim.class, EpochData.class,
			(u, r) -> PermissionLevel.SYSTEM,
			(u, r, pubKey) -> {
				if (pubKey.isPresent()) {
					throw new AuthorizationException("System update should not be signed.");
				}
			},
			(s, u, r) -> {
				if (u.getEpoch() != 0) {
					throw new ProcedureException("First epoch must be 0.");
				}

				return ReducerResult.incomplete(new AllocatingSystem());
			}
		));
		os.createUpProcedure(new UpProcedure<>(
			AllocatingSystem.class, RoundData.class,
			(u, r) -> PermissionLevel.SYSTEM,
			(u, r, pubKey) -> {
				if (pubKey.isPresent()) {
					throw new AuthorizationException("System update should not be signed.");
				}
			},
			(s, u, r) -> {
				if (u.getView() != 0) {
					throw new ProcedureException("First view must be 0.");
				}
				return ReducerResult.complete();
			}
		));
	}


	private void roundUpdate(SysCalls os) {
		// Round update
		os.createDownProcedure(new DownProcedure<>(
			RoundData.class, VoidReducerState.class,
			(d, r) -> PermissionLevel.SUPER_USER,
			(d, r, pubKey) -> {
				if (pubKey.isPresent()) {
					throw new AuthorizationException("System update should not be signed.");
				}
			},
			(d, s, r) -> ReducerResult.incomplete(new RoundClosed(d.getSubstate()))
		));
		os.createUpProcedure(new UpProcedure<>(
			RoundClosed.class, RoundData.class,
			(u, r) -> PermissionLevel.SUPER_USER,
			(u, r, pubKey) -> { },
			(s, u, r) -> {
				var curData = s.prev;
				if (curData.getView() >= u.getView()) {
					throw new ProcedureException("Next view must be greater than previous.");
				}

				return ReducerResult.incomplete(new UpdateValidatorEpochData());
			}
		));
		os.createDownProcedure(new DownProcedure<>(
			ValidatorEpochData.class, UpdateValidatorEpochData.class,
			(d, r) -> PermissionLevel.SUPER_USER,
			(d, r, pubKey) -> {
				if (pubKey.isPresent()) {
					throw new AuthorizationException("System update should not be signed.");
				}
			},
			(d, s, r) -> ReducerResult.incomplete(new UpdatingValidatorEpochData(d.getSubstate()))
		));
		os.createUpProcedure(new UpProcedure<>(
			UpdatingValidatorEpochData.class, ValidatorEpochData.class,
			(u, r) -> PermissionLevel.SUPER_USER,
			(u, r, pubKey) -> {
				if (pubKey.isPresent()) {
					throw new AuthorizationException("System update should not be signed.");
				}
			},
			(s, u, r) -> {
				s.update(u);
				return ReducerResult.complete(Unknown.create());
			}
		));
	}

	private void epochUpdate(SysCalls os) {
		// Epoch Update
		os.createDownProcedure(new DownProcedure<>(
			EpochData.class, RoundClosed.class,
			(d, r) -> PermissionLevel.SUPER_USER,
			(d, r, pubKey) -> {
				if (pubKey.isPresent()) {
					throw new AuthorizationException("System update should not be signed.");
				}
			},
			(d, s, r) -> ReducerResult.incomplete(new UpdatingEpoch(d.getSubstate()))
		));

		os.createShutDownAllProcedure(new ShutdownAllProcedure<>(
			ValidatorEpochData.class, UpdatingEpoch.class,
			r -> PermissionLevel.SUPER_USER,
			(r, k) -> {
				if (k.isPresent()) {
					throw new AuthorizationException("System update should not be signed.");
				}
			},
			(i, s, r) -> {
				var rewardingValidators = new RewardingValidators(s);
				return ReducerResult.incomplete(rewardingValidators.process(i));
			}
		));

		os.createShutDownAllProcedure(new ShutdownAllProcedure<>(
			PreparedUnstakeOwned.class, PreparingUnstake.class,
			r -> PermissionLevel.SUPER_USER,
			(r, k) -> {
				if (k.isPresent()) {
					throw new AuthorizationException("System update should not be signed.");
				}
			},
			(i, s, r) -> ReducerResult.incomplete(s.unstakes(i))
		));
		os.createDownProcedure(new DownProcedure<>(
			ValidatorStake.class, LoadingStake.class,
			(d, r) -> PermissionLevel.SUPER_USER,
			(d, r, k) -> { },
			(d, s, r) -> ReducerResult.incomplete(s.startUpdate(d.getSubstate()))
		));
		os.createUpProcedure(new UpProcedure<>(
			Unstaking.class, TokensInAccount.class,
			(u, r) -> PermissionLevel.SUPER_USER,
			(u, r, k) -> { },
			(s, u, r) -> ReducerResult.incomplete(s.unstake(u))
		));
		os.createShutDownAllProcedure(new ShutdownAllProcedure<>(
			PreparedStake.class, PreparingStake.class,
			r -> PermissionLevel.SUPER_USER,
			(r, k) -> {
				if (k.isPresent()) {
					throw new AuthorizationException("System update should not be signed.");
				}
			},
			(i, s, r) -> ReducerResult.incomplete(s.prepareStakes(i))
		));
		os.createUpProcedure(new UpProcedure<>(
			Staking.class, StakeOwnership.class,
			(u, r) -> PermissionLevel.SUPER_USER,
			(u, r, k) -> { },
			(s, u, r) -> ReducerResult.incomplete(s.stake(u))
		));
		os.createUpProcedure(new UpProcedure<>(
			UpdatingValidatorStakes.class, ValidatorStake.class,
			(u, r) -> PermissionLevel.SUPER_USER,
			(u, r, k) -> { },
			(s, u, r) -> ReducerResult.incomplete(s.updateStake(u))
		));

		os.createUpProcedure(new UpProcedure<>(
			CreatingNextValidatorSet.class, ValidatorEpochData.class,
			(u, r) -> PermissionLevel.SUPER_USER,
			(u, r, pubKey) -> { },
			(s, u, r) -> ReducerResult.incomplete(s.nextValidator(u))
		));

		os.createUpProcedure(new UpProcedure<>(
			CreatingNextValidatorSet.class, EpochData.class,
			(u, r) -> PermissionLevel.SUPER_USER,
			(u, r, pubKey) -> { },
			(s, u, r) -> ReducerResult.incomplete(s.nextEpoch(u))
		));

		os.createUpProcedure(new UpProcedure<>(
			StartingEpochRound.class, RoundData.class,
			(u, r) -> PermissionLevel.SUPER_USER,
			(u, r, pubKey) -> { },
			(s, u, r) -> {
				if (u.getView() != 0) {
					throw new ProcedureException("Epoch must start with view 0");
				}

				return ReducerResult.complete();
			}
		));
	}

	@Override
	public void main(SysCalls os) {
		os.registerParticle(RoundData.class, ParticleDefinition.<RoundData>builder()
			.staticValidation(p -> {
				if (p.getTimestamp() < 0) {
					return Result.error("Timestamp is less than 0");
				}
				if (p.getView() < 0) {
					return Result.error("View is less than 0");
				}
				return Result.success();
			})
			.virtualizeUp(p -> p.getView() == 0 && p.getTimestamp() == 0)
			.build()
		);
		os.registerParticle(EpochData.class, ParticleDefinition.<EpochData>builder()
			.staticValidation(p -> p.getEpoch() < 0 ? Result.error("Epoch is less than 0") : Result.success())
			.build()
		);
		os.registerParticle(
			ValidatorStake.class,
			ParticleDefinition.<ValidatorStake>builder()
				.virtualizeUp(p -> p.getAmount().isZero())
				.build()
		);
		os.registerParticle(
			StakeOwnership.class,
			ParticleDefinition.<StakeOwnership>builder()
				.staticValidation(s -> {
					if (s.getAmount().isZero()) {
						return Result.error("amount must not be zero");
					}
					return Result.success();
				})
				.build()
		);
		os.registerParticle(
			ValidatorEpochData.class,
			ParticleDefinition.<ValidatorEpochData>builder()
				.staticValidation(s -> {
					if (s.proposalsCompleted() < 0) {
						return Result.error("proposals completed must be >= 0");
					}
					return Result.success();
				})
				.build()
		);

		registerGenesisTransitions(os);

		// Epoch update
		epochUpdate(os);

		// Round update
		roundUpdate(os);
	}
}
