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

package com.radixdlt.atommodel.tokens.scrypt;

import com.google.common.reflect.TypeToken;
import com.radixdlt.atom.actions.StakeTokens;
import com.radixdlt.atom.actions.UnstakeTokens;
import com.radixdlt.atommodel.system.state.SystemParticle;
import com.radixdlt.atommodel.tokens.state.DeprecatedStake;
import com.radixdlt.atommodel.tokens.TokenDefinitionUtils;
import com.radixdlt.atommodel.tokens.state.TokensParticle;
import com.radixdlt.atomos.ConstraintScrypt;
import com.radixdlt.atomos.ParticleDefinition;
import com.radixdlt.atomos.SysCalls;
import com.radixdlt.constraintmachine.AuthorizationException;
import com.radixdlt.constraintmachine.DownProcedure;
import com.radixdlt.constraintmachine.Particle;
import com.radixdlt.constraintmachine.PermissionLevel;
import com.radixdlt.constraintmachine.ProcedureException;
import com.radixdlt.constraintmachine.ReducerResult;
import com.radixdlt.constraintmachine.ReducerState;
import com.radixdlt.constraintmachine.UpProcedure;
import com.radixdlt.constraintmachine.VoidReducerState;
import com.radixdlt.crypto.ECPublicKey;
import com.radixdlt.identifiers.REAddr;
import com.radixdlt.utils.UInt256;
import com.radixdlt.utils.UInt384;

import java.util.Objects;
import java.util.Optional;

public final class StakingConstraintScryptV2 implements ConstraintScrypt {
	public static final int EPOCHS_LOCKED = 2; // Must go through one full epoch before being unlocked

	@Override
	public void main(SysCalls os) {
		os.registerParticle(
			DeprecatedStake.class,
			ParticleDefinition.<DeprecatedStake>builder()
				.staticValidation(TokenDefinitionUtils::staticCheck)
				.build()
		);

		defineStaking(os);
	}

	public static class RemainderStake implements ReducerState {
		private final Particle initialParticle;
		private final UInt256 amount;
		private final REAddr owner;
		private final ECPublicKey delegate;

		RemainderStake(Particle initialParticle, UInt256 amount, REAddr owner, ECPublicKey delegate) {
			this.initialParticle = initialParticle;
			this.amount = amount;
			this.owner = owner;
			this.delegate = delegate;
		}

		public Particle initialParticle() {
			return initialParticle;
		}

		public UInt256 amount() {
			return amount;
		}

		public REAddr owner() {
			return owner;
		}

		public ECPublicKey delegate() {
			return delegate;
		}

		@Override
		public TypeToken<? extends ReducerState> getTypeToken() {
			return TypeToken.of(RemainderStake.class);
		}
	}

	public static class UnaccountedStake implements ReducerState {
		private final DeprecatedStake initialParticle;
		private final UInt384 amount;

		public UnaccountedStake(DeprecatedStake initialParticle, UInt384 amount) {
			this.initialParticle = initialParticle;
			this.amount = amount;
		}

		public UInt384 amount() {
			return amount;
		}

		public DeprecatedStake initialParticle() {
			return initialParticle;
		}

		public Optional<ReducerState> subtract(UInt384 amountAccounted) {
			var compare = amountAccounted.compareTo(amount);
			if (compare > 0) {
				return Optional.of(new TokensConstraintScryptV1.RemainderTokens(
					initialParticle, REAddr.ofNativeToken(), amountAccounted.subtract(amount))
				);
			} else if (compare < 0) {
				return Optional.of(new UnaccountedStake(initialParticle, amount.subtract(amountAccounted)));
			} else {
				return Optional.empty();
			}
		}

		@Override
		public TypeToken<? extends ReducerState> getTypeToken() {
			return TypeToken.of(UnaccountedStake.class);
		}
	}

	private void defineStaking(SysCalls os) {
		// Stake
		os.createUpProcedure(new UpProcedure<>(
			VoidReducerState.class, DeprecatedStake.class,
			(u, r) -> PermissionLevel.USER,
			(u, r, k) -> { },
			(s, u, r) -> {
				var state = new UnaccountedStake(
					u,
					UInt384.from(u.getAmount())
				);
				return ReducerResult.incomplete(state);
			}
		));
		os.createDownProcedure(new DownProcedure<>(
			TokensParticle.class, UnaccountedStake.class,
			(d, r) -> PermissionLevel.USER,
			(d, r, k) -> d.getSubstate().verifyWithdrawAuthorization(k, r),
			(d, s, r) -> {
				if (!d.getSubstate().getResourceAddr().isNativeToken()) {
					throw new ProcedureException("Not the same address.");
				}
				var amt = UInt384.from(d.getSubstate().getAmount());
				var nextRemainder = s.subtract(amt);
				if (nextRemainder.isEmpty()) {
					// FIXME: This isn't 100% correct
					var p = s.initialParticle;
					var action = new StakeTokens(d.getSubstate().getHoldingAddr(), p.getDelegateKey(), p.getAmount());
					return ReducerResult.complete(action);
				}

				return ReducerResult.incomplete(nextRemainder.get());
			}
		));

		// Unstake
		os.createDownProcedure(new DownProcedure<>(
			DeprecatedStake.class, TokensConstraintScryptV1.UnaccountedTokens.class,
			(d, r) -> PermissionLevel.USER,
			(d, r, k) -> {
				try {
					d.getSubstate().getOwner().verifyWithdrawAuthorization(k);
				} catch (REAddr.BucketWithdrawAuthorizationException e) {
					throw new AuthorizationException(e.getMessage());
				}
			},
			(d, s, r) -> {
				if (!s.resourceInBucket().isNativeToken()) {
					throw new ProcedureException("Can only destake to the native token.");
				}

				if (!Objects.equals(d.getSubstate().getOwner(), s.resourceInBucket().holdingAddress())) {
					throw new ProcedureException("Must unstake to self");
				}

				var epochUnlocked = s.resourceInBucket().epochUnlocked();
				if (epochUnlocked.isEmpty()) {
					throw new ProcedureException("Exiting from stake must be locked.");
				}

				var system = (SystemParticle) r.loadAddr(null, REAddr.ofSystem()).orElseThrow();
				if (system.getEpoch() + EPOCHS_LOCKED != epochUnlocked.get()) {
					throw new ProcedureException("Incorrect epoch unlock: " + epochUnlocked.get()
						+ " should be: " + (system.getEpoch() + EPOCHS_LOCKED));
				}

				var nextRemainder = s.subtract(UInt384.from(d.getSubstate().getAmount()));
				if (nextRemainder.isEmpty()) {
					// FIXME: This isn't 100% correct
					var p = (TokensParticle) s.initialParticle();
					var action = new UnstakeTokens(p.getHoldingAddr(), d.getSubstate().getDelegateKey(), p.getAmount());
					return ReducerResult.complete(action);
				}

				if (nextRemainder.get() instanceof TokensConstraintScryptV1.RemainderTokens) {
					TokensConstraintScryptV1.RemainderTokens remainderTokens = (TokensConstraintScryptV1.RemainderTokens) nextRemainder.get();
					var stakeRemainder = new RemainderStake(
						remainderTokens.initialParticle(),
						remainderTokens.amount().getLow(),
						d.getSubstate().getOwner(),
						d.getSubstate().getDelegateKey()
					);
					return ReducerResult.incomplete(stakeRemainder);
				} else {
					return ReducerResult.incomplete(nextRemainder.get());
				}
			}
		));

		// For change
		os.createUpProcedure(new UpProcedure<>(
			RemainderStake.class, DeprecatedStake.class,
			(u, r) -> PermissionLevel.USER,
			(u, r, k) -> { },
			(s, u, r) -> {
				if (!u.getAmount().equals(s.amount)) {
					throw new ProcedureException("Remainder must be filled exactly.");
				}

				if (!u.getDelegateKey().equals(s.delegate)) {
					throw new ProcedureException("Delegate key does not match.");
				}

				if (!u.getOwner().equals(s.owner)) {
					throw new ProcedureException("Owners don't match.");
				}

				// FIXME: This isn't 100% correct
				var t = (TokensParticle) s.initialParticle;
				var action = new UnstakeTokens(t.getHoldingAddr(), u.getDelegateKey(), t.getAmount());
				return ReducerResult.complete(action);
			}
		));
	}
}
