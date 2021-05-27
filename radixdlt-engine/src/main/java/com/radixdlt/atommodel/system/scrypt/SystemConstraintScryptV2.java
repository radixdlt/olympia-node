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
import com.radixdlt.atommodel.system.state.StakeShares;
import com.radixdlt.atommodel.system.state.SystemParticle;
import com.radixdlt.atommodel.system.state.ValidatorEpochData;
import com.radixdlt.atommodel.tokens.TokenDefinitionUtils;
import com.radixdlt.atommodel.tokens.state.PreparedStake;
import com.radixdlt.atommodel.tokens.state.PreparedUnstakeOwned;
import com.radixdlt.atommodel.tokens.state.TokensParticle;
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
import com.radixdlt.store.ReadableAddrs;
import com.radixdlt.utils.UInt256;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;
import java.util.function.Function;
import java.util.function.Supplier;

import static com.radixdlt.atommodel.tokens.scrypt.StakingConstraintScryptV3.EPOCHS_LOCKED;

public class SystemConstraintScryptV2 implements ConstraintScrypt {

	public static final UInt256 REWARDS_PER_PROPOSAL = TokenDefinitionUtils.SUB_UNITS.multiply(UInt256.TEN);

	private static class UpdateValidatorEpochData implements ReducerState {
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

	private Result staticCheck(SystemParticle systemParticle) {
		if (systemParticle.getEpoch() < 0) {
			return Result.error("Epoch is less than 0");
		}

		if (systemParticle.getTimestamp() < 0) {
			return Result.error("Timestamp is less than 0");
		}

		if (systemParticle.getView() < 0) {
			return Result.error("View is less than 0");
		}

		// FIXME: Need to validate view, but need additional state to do that successfully

		return Result.success();
	}

	private static final class TransitionToV2Epoch implements ReducerState {
		private final SystemParticle sys;

		private TransitionToV2Epoch(SystemParticle sys) {
			this.sys = sys;
		}
	}

	private static final class Validators implements ReducerState {
		private final Set<ECPublicKey> validatorKeys = new HashSet<>();
	}


	private static final class ReadyForEpochUpdate implements ReducerState {
	}

	private static final class UpdatingEpoch implements ReducerState {
		private final EpochData prev;
		private UpdatingEpoch(EpochData prev) {
			this.prev = prev;
		}
	}

	private static final class UpdatingEpochRound implements ReducerState {
	}

	private static final class UpdatingEpochRound2 implements ReducerState {
	}

	private static final class UpdatingRound implements ReducerState {
		private final RoundData prev;
		private UpdatingRound(RoundData prev) {
			this.prev = prev;
		}
	}

	private static final class Unstaking implements ReducerState {
		private final TreeMap<REAddr, UInt256> unstaking;
		private final Supplier<ReducerState> onDone;
		Unstaking(TreeMap<REAddr, UInt256> unstaking, Supplier<ReducerState> onDone) {
			this.unstaking = unstaking;
			this.onDone = onDone;
		}

		ReducerState unstake(TokensParticle u, ReadableAddrs r) throws ProcedureException {
			if (!u.getResourceAddr().isNativeToken()) {
				throw new ProcedureException("Can only destake to the native token.");
			}
			if (u.getEpochUnlocked().isEmpty()) {
				throw new ProcedureException("Exiting from stake must be locked.");
			}

			var firstAddr = unstaking.firstKey();
			if (!Objects.equals(u.getHoldingAddr(), firstAddr)) {
				throw new ProcedureException("Unstaking incorrect addr");
			}
			var expectedAmt = unstaking.remove(firstAddr);
			if (!expectedAmt.equals(u.getAmount())) {
				throw new ProcedureException("Invalid amount");
			}

			var systemState = (HasEpochData) r.loadAddr(null, REAddr.ofSystem()).orElseThrow();
			if (systemState.getEpoch() + EPOCHS_LOCKED != u.getEpochUnlocked().get()) {
				throw new ProcedureException("Incorrect epoch unlock: " + u.getEpochUnlocked().get()
					+ " should be: " + (systemState.getEpoch() + EPOCHS_LOCKED));
			}

			return unstaking.isEmpty() ? onDone.get() : this;
		}
	}

	private static final class PreparingUnstake implements ReducerState {
		private final TreeMap<ECPublicKey, TreeMap<REAddr, UInt256>> preparingUnstake = new TreeMap<>(
			(o1, o2) -> Arrays.compare(o1.getBytes(), o2.getBytes())
		);

		private final TreeMap<ECPublicKey, UInt256> curStake = new TreeMap<>(
			(o1, o2) -> Arrays.compare(o1.getBytes(), o2.getBytes())
		);

		PreparingUnstake() {
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
				return new PreparingStake(curStake);
			}

			var k = preparingUnstake.firstKey();
			if (curStake.containsKey(k)) {
				throw new IllegalStateException();
			}
			var unstakes = preparingUnstake.remove(k);
			var totalUnstakeAmt = unstakes.values().stream().reduce(UInt256::add).orElseThrow();

			return new LoadingStake(k, amt -> {
				if (amt.compareTo(totalUnstakeAmt) < 0) {
					throw new IllegalStateException();
				}
				curStake.put(k, amt.subtract(totalUnstakeAmt));
				return new Unstaking(unstakes, this::next);
			});
		}
	}

	private static final class Staking implements ReducerState {
		private final ECPublicKey validatorKey;
		private final TreeMap<REAddr, UInt256> stakes;
		private final Supplier<ReducerState> onDone;

		Staking(ECPublicKey validatorKey, TreeMap<REAddr, UInt256> stakes, Supplier<ReducerState> onDone) {
			this.validatorKey = validatorKey;
			this.stakes = stakes;
			this.onDone = onDone;
		}

		ReducerState stake(StakeShares stakeShares) throws ProcedureException {
			if (!Objects.equals(validatorKey, stakeShares.getDelegateKey())) {
				throw new ProcedureException("Invalid update");
			}
			var accountAddr = stakes.firstKey();
			if (!Objects.equals(stakeShares.getOwner(), accountAddr)) {
				throw new ProcedureException(stakeShares.getOwner() + " is not the first addr (" + accountAddr + ")");
			}
			var stakeAmt = stakes.remove(accountAddr);
			if (!Objects.equals(stakeAmt, stakeShares.getAmount())) {
				throw new ProcedureException(
					String.format("Amount (%s) does not match what is prepared (%s)", stakeShares.getAmount(), stakeAmt)
				);
			}
			return stakes.isEmpty() ? onDone.get() : this;
		}
	}

	private static final class LoadingStake implements ReducerState {
		private final ECPublicKey key;
		private final Function<UInt256, ReducerState> onDone;

		LoadingStake(ECPublicKey key, Function<UInt256, ReducerState> onDone) {
			this.key = key;
			this.onDone = onDone;
		}

		ReducerState startUpdate(ValidatorStake stake) throws ProcedureException {
			if (!stake.getValidatorKey().equals(key)) {
				throw new ProcedureException("Invalid stake load");
			}
			return onDone.apply(stake.getAmount());
		}
	}

	private static final class PreparingStake implements ReducerState {
		private final TreeMap<ECPublicKey, UInt256> curStake;
		private final TreeMap<ECPublicKey, TreeMap<REAddr, UInt256>> preparingStake = new TreeMap<>(
			(o1, o2) -> Arrays.compare(o1.getBytes(), o2.getBytes())
		);
		PreparingStake(TreeMap<ECPublicKey, UInt256> curStake) {
			this.curStake = curStake;
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
				return curStake.isEmpty() ? new ReadyForEpochUpdate() : new UpdatingValidatorStakes(curStake);
			}

			var k = preparingStake.firstKey();
			var stakes = preparingStake.remove(k);
			var totalPrepared = stakes.values().stream().reduce(UInt256::add).orElseThrow();
			if (!curStake.containsKey(k)) {
				return new LoadingStake(k, amt -> {
					curStake.put(k, amt.add(totalPrepared));
					return new Staking(k, stakes, this::next);
				});
			} else {
				curStake.merge(k, totalPrepared, UInt256::add);
				return new Staking(k, stakes, this::next);
			}
		}
	}

	private static final class UpdatingValidatorStakes implements ReducerState {
		private final TreeMap<ECPublicKey, UInt256> curStake;
		UpdatingValidatorStakes(TreeMap<ECPublicKey, UInt256> curStake) {
			this.curStake = curStake;
		}

		ReducerState updateStake(ValidatorStake stake) throws ProcedureException {
			var k = curStake.firstKey();
			if (!stake.getValidatorKey().equals(k)) {
				throw new ProcedureException("First key does not match.");
			}

			var expectedUpdate = curStake.remove(k);
			if (!stake.getAmount().equals(expectedUpdate)) {
				throw new ProcedureException("Stake amount does not match.");
			}

			return curStake.isEmpty() ? new ReadyForEpochUpdate() : this;
		}
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

				return ReducerResult.complete();
			}
		));
	}

	private void registerBetanetV2ToV3Transitions(SysCalls os) {
		os.createDownProcedure(new DownProcedure<>(
			SystemParticle.class, ReadyForEpochUpdate.class,
			(d, r) -> PermissionLevel.SUPER_USER,
			(d, r, pubKey) -> {
				if (pubKey.isPresent()) {
					throw new AuthorizationException("System update should not be signed.");
				}
			},
			(d, s, r) -> ReducerResult.incomplete(new TransitionToV2Epoch(d.getSubstate()))
		));
		os.createUpProcedure(new UpProcedure<>(
			TransitionToV2Epoch.class, EpochData.class,
			(u, r) -> PermissionLevel.SUPER_USER,
			(u, r, pubKey) -> {
				if (pubKey.isPresent()) {
					throw new AuthorizationException("System update should not be signed.");
				}
			},
			(s, u, r) -> {
				var curState = s.sys;
				if (curState.getEpoch() + 1 != u.getEpoch()) {
					throw new ProcedureException("Must increment epoch");
				}

				return ReducerResult.incomplete(new UpdatingEpochRound());
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
			(d, s, r) -> ReducerResult.incomplete(new UpdatingRound(d.getSubstate()))
		));
		os.createUpProcedure(new UpProcedure<>(
			UpdatingRound.class, RoundData.class,
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


	private void emissionsAndNextValidatorSet(SysCalls os) {
		os.createShutDownAllProcedure(new ShutdownAllProcedure<>(
			ValidatorEpochData.class, VoidReducerState.class,
			r -> PermissionLevel.SUPER_USER,
			(r, k) -> {
				if (k.isPresent()) {
					throw new AuthorizationException("System update should not be signed.");
				}
			},
			(i, s, r) -> {
				i.forEachRemaining(e -> { }); // FIXME: This is a hack and required for substates to be updated
				return ReducerResult.incomplete(new Validators());
			}
		));

		os.createUpProcedure(new UpProcedure<>(
			Validators.class, ValidatorEpochData.class,
			(u, r) -> PermissionLevel.SUPER_USER,
			(u, r, pubKey) -> { },
			(s, u, r) -> {
				if (s.validatorKeys.contains(u.validatorKey())) {
					throw new ProcedureException("Already in set: " + u.validatorKey());
				}
				if (u.proposalsCompleted() != 0) {
					throw new ProcedureException("Proposals completed must be 0");
				}

				return ReducerResult.incomplete(s);
			}
		));
	}


	private void prepareStake(SysCalls os) {
		os.createShutDownAllProcedure(new ShutdownAllProcedure<>(
			PreparedUnstakeOwned.class, Validators.class,
			r -> PermissionLevel.SUPER_USER,
			(r, k) -> {
				if (k.isPresent()) {
					throw new AuthorizationException("System update should not be signed.");
				}
			},
			(i, s, r) -> {
				var prepared = new PreparingUnstake();
				return ReducerResult.incomplete(prepared.unstakes(i));
			}
		));
		os.createDownProcedure(new DownProcedure<>(
			ValidatorStake.class, LoadingStake.class,
			(d, r) -> PermissionLevel.SUPER_USER,
			(d, r, k) -> { },
			(d, s, r) -> ReducerResult.incomplete(s.startUpdate(d.getSubstate()))
		));
		os.createUpProcedure(new UpProcedure<>(
			Unstaking.class, TokensParticle.class,
			(u, r) -> PermissionLevel.SUPER_USER,
			(u, r, k) -> { },
			(s, u, r) -> ReducerResult.incomplete(s.unstake(u, r))
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
			Staking.class, StakeShares.class,
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
	}

	private void epochUpdate(SysCalls os) {
		// Epoch Update
		os.createDownProcedure(new DownProcedure<>(
			EpochData.class, ReadyForEpochUpdate.class,
			(d, r) -> PermissionLevel.SUPER_USER,
			(d, r, pubKey) -> {
				if (pubKey.isPresent()) {
					throw new AuthorizationException("System update should not be signed.");
				}
			},
			(d, s, r) -> ReducerResult.incomplete(new UpdatingEpoch(d.getSubstate()))
		));
		os.createUpProcedure(new UpProcedure<>(
			UpdatingEpoch.class, EpochData.class,
			(u, r) -> PermissionLevel.SUPER_USER,
			(u, r, pubKey) -> { },
			(s, u, r) -> {
				if (s.prev.getEpoch() == 0) {
					return ReducerResult.incomplete(new UpdatingEpochRound());
				}
				if (u.getEpoch() != s.prev.getEpoch() + 1) {
					throw new ProcedureException("Invalid next epoch: " + u.getEpoch() + " Expected: " + (s.prev.getEpoch() + 1));
				}
				return ReducerResult.incomplete(new UpdatingEpochRound());
			}
		));
		os.createDownProcedure(new DownProcedure<>(
			RoundData.class, UpdatingEpochRound.class,
			(d, r) -> PermissionLevel.SUPER_USER,
			(d, r, pubKey) -> {
				if (pubKey.isPresent()) {
					throw new AuthorizationException("System update should not be signed.");
				}
			},
			(d, s, r) -> ReducerResult.incomplete(new UpdatingEpochRound2())
		));
		os.createUpProcedure(new UpProcedure<>(
			UpdatingEpochRound2.class, RoundData.class,
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
		os.registerParticle(SystemParticle.class, ParticleDefinition.<SystemParticle>builder()
			.staticValidation(this::staticCheck)
			.virtualizeUp(p -> p.getView() == 0 && p.getEpoch() == 0 && p.getTimestamp() == 0)
			.build()
		);
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
			StakeShares.class,
			ParticleDefinition.<StakeShares>builder()
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
		// Transition to V2 Epoch
		// TODO: Remove for mainnet
		registerBetanetV2ToV3Transitions(os);

		// Epoch update
		emissionsAndNextValidatorSet(os);
		prepareStake(os);
		epochUpdate(os);

		// Round update
		roundUpdate(os);
	}
}
