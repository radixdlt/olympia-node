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

import com.google.common.reflect.TypeToken;
import com.radixdlt.atom.actions.SystemNextEpoch;
import com.radixdlt.atom.actions.Unknown;
import com.radixdlt.atommodel.system.state.EpochData;
import com.radixdlt.atommodel.system.state.RoundData;
import com.radixdlt.atommodel.system.state.Stake;
import com.radixdlt.atommodel.system.state.StakeShare;
import com.radixdlt.atommodel.system.state.SystemParticle;
import com.radixdlt.atommodel.tokens.TokenDefinitionUtils;
import com.radixdlt.atommodel.tokens.state.PreparedStake;
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
import java.util.Iterator;
import java.util.Objects;
import java.util.TreeMap;

public class SystemConstraintScryptV2 implements ConstraintScrypt {

	public static final UInt256 REWARDS_PER_PROPOSAL = TokenDefinitionUtils.SUB_UNITS.multiply(UInt256.TEN);

	private static class Inflation implements ReducerState {
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

	private static final class AllPreparedStake implements ReducerState {
		private UInt256 preparedStake = UInt256.ZERO;
		private final TreeMap<ECPublicKey, TreeMap<REAddr, UInt256>> allPreparedStake = new TreeMap<>(
			(o1, o2) -> Arrays.compare(o1.getBytes(), o2.getBytes())
		);

		AllPreparedStake(Iterator<PreparedStake> preparedStakeIterator) {
			preparedStakeIterator.forEachRemaining(preparedStake ->
				allPreparedStake
					.computeIfAbsent(
						preparedStake.getDelegateKey(),
						k -> new TreeMap<>((o1, o2) -> Arrays.compare(o1.getBytes(), o2.getBytes()))
					)
					.merge(preparedStake.getOwner(), preparedStake.getAmount(), UInt256::add)
			);
		}

		void accountFor(StakeShare stakeShare) throws ProcedureException {
			var e = allPreparedStake.firstEntry();
			if (!Objects.equals(e.getKey(), stakeShare.getDelegateKey())) {
				throw new ProcedureException(stakeShare.getDelegateKey() + " is not the first key (" + e.getKey() + ")");
			}
			var stakes = e.getValue();
			var accountAddr = stakes.firstKey();
			if (!Objects.equals(stakeShare.getOwner(), accountAddr)) {
				throw new ProcedureException(stakeShare.getOwner() + " is not the first addr (" + accountAddr + ")");
			}
			var stakeAmt = stakes.remove(accountAddr);
			if (!Objects.equals(stakeAmt, stakeShare.getAmount())) {
				throw new ProcedureException(
					String.format("Amount (%s) does not match what is prepared (%s)", stakeShare.getAmount(), stakeAmt)
				);
			}
			preparedStake = preparedStake.add(stakeAmt);
		}

		boolean take(Stake stake) throws ProcedureException {
			var firstKey = allPreparedStake.firstKey();
			if (!Objects.equals(firstKey, stake.getValidatorKey())) {
				throw new ProcedureException(stake.getValidatorKey() + " is not the first key (" + firstKey + ")");
			}
			var stakes = allPreparedStake.remove(firstKey);
			if (!stakes.isEmpty()) {
				throw new ProcedureException("Stakes has not been emptied.");
			}
			if (!Objects.equals(preparedStake, stake.getAmount())) {
				throw new ProcedureException(
					String.format("Amount (%s) does not match what is prepared (%s)", stake.getAmount(), preparedStake)
				);
			}
			preparedStake = UInt256.ZERO;
			return allPreparedStake.isEmpty();
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
			SystemParticle.class, VoidReducerState.class,
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
			Stake.class,
			ParticleDefinition.<Stake>builder().build()
		);
		os.registerParticle(
			StakeShare.class,
			ParticleDefinition.<StakeShare>builder()
				.staticValidation(s -> {
					if (s.getAmount().isZero()) {
						return Result.error("amount must not be zero");
					}
					return Result.success();
				})
				.build()
		);

		registerGenesisTransitions(os);
		// Transition to V2 Epoch
		// TODO: Remove for mainnet
		registerBetanetV2ToV3Transitions(os);

		// Epoch Update
		os.createShutDownAllProcedure(new ShutdownAllProcedure<>(
			PreparedStake.class, VoidReducerState.class,
			r -> PermissionLevel.SUPER_USER,
			(r, k) -> {
				if (k.isPresent()) {
					throw new AuthorizationException("System update should not be signed.");
				}
			},
			(i, s, r) -> {
				var prepared = new AllPreparedStake(i);
				return prepared.allPreparedStake.isEmpty()
					? ReducerResult.incomplete(new ReadyForEpochUpdate())
					: ReducerResult.incomplete(prepared);
			}
		));
		os.createUpProcedure(new UpProcedure<>(
			AllPreparedStake.class, StakeShare.class,
			(u, r) -> PermissionLevel.SUPER_USER,
			(u, r, k) -> { },
			(s, u, r) -> {
				s.accountFor(u);
				return ReducerResult.incomplete(s);
			}
		));
		os.createUpProcedure(new UpProcedure<>(
			AllPreparedStake.class, Stake.class,
			(u, r) -> PermissionLevel.SUPER_USER,
			(u, r, k) -> { },
			(s, u, r) -> s.take(u) ? ReducerResult.incomplete(new ReadyForEpochUpdate()) : ReducerResult.incomplete(s)
		));

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
					return ReducerResult.incomplete(new UpdatingEpochRound(), new SystemNextEpoch(0));
				}
				if (u.getEpoch() != s.prev.getEpoch() + 1) {
					throw new ProcedureException("Invalid next epoch: " + u.getEpoch() + " Expected: " + (s.prev.getEpoch() + 1));
				}
				return ReducerResult.incomplete(new UpdatingEpochRound(), new SystemNextEpoch(0));
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

				return ReducerResult.incomplete(new Inflation(), Unknown.create());
			}
		));
		os.createUpProcedure(new UpProcedure<>(
			Inflation.class, Stake.class,
			(u, r) -> PermissionLevel.SUPER_USER,
			(u, r, pubKey) -> {
				if (pubKey.isPresent()) {
					throw new AuthorizationException("System update should not be signed.");
				}
			},
			(s, u, r) -> {
				if (!u.getAmount().equals(REWARDS_PER_PROPOSAL)) {
					throw new ProcedureException("Rewards must be " + REWARDS_PER_PROPOSAL);
				}
				return ReducerResult.complete(Unknown.create());
			}
		));
	}
}
