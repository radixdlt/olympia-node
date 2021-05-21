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
import com.radixdlt.atom.actions.BurnToken;
import com.radixdlt.atom.actions.CreateFixedToken;
import com.radixdlt.atom.actions.CreateMutableToken;
import com.radixdlt.atom.actions.MintToken;
import com.radixdlt.atom.actions.StakeTokens;
import com.radixdlt.atom.actions.TransferToken;
import com.radixdlt.atom.actions.Unknown;
import com.radixdlt.atommodel.tokens.TokenDefinitionUtils;
import com.radixdlt.atommodel.tokens.state.DeprecatedStake;
import com.radixdlt.atommodel.tokens.state.ResourceInBucket;
import com.radixdlt.atommodel.tokens.state.TokenDefinitionParticle;
import com.radixdlt.atommodel.tokens.state.TokensParticle;
import com.radixdlt.atomos.CMAtomOS;
import com.radixdlt.atomos.ConstraintScrypt;
import com.radixdlt.atomos.ParticleDefinition;
import com.radixdlt.atomos.SysCalls;
import com.radixdlt.constraintmachine.DownProcedure;
import com.radixdlt.constraintmachine.EndProcedure;
import com.radixdlt.constraintmachine.Particle;
import com.radixdlt.constraintmachine.PermissionLevel;
import com.radixdlt.constraintmachine.ReducerResult;
import com.radixdlt.constraintmachine.ReducerState;
import com.radixdlt.constraintmachine.UpProcedure;
import com.radixdlt.constraintmachine.VoidReducerState;
import com.radixdlt.identifiers.REAddr;
import com.radixdlt.utils.UInt256;
import com.radixdlt.utils.UInt384;

import java.util.Optional;

public class TokensConstraintScryptV2 implements ConstraintScrypt {
	@Override
	public void main(SysCalls os) {
		registerParticles(os);
		defineTokenCreation(os);
		defineMintTransferBurn(os);
	}

	private void registerParticles(SysCalls os) {
		os.registerParticle(
			TokenDefinitionParticle.class,
			ParticleDefinition.<TokenDefinitionParticle>builder()
				.staticValidation(TokenDefinitionUtils::staticCheck)
				.build()
		);

		os.registerParticle(
			TokensParticle.class,
			ParticleDefinition.<TokensParticle>builder()
				.staticValidation(TokenDefinitionUtils::staticCheck)
				.build()
		);
	}

	private static class NeedFixedTokenSupply implements ReducerState {
		private final byte[] arg;
		private final TokenDefinitionParticle tokenDefinitionParticle;
		private NeedFixedTokenSupply(byte[] arg, TokenDefinitionParticle tokenDefinitionParticle) {
			this.arg = arg;
			this.tokenDefinitionParticle = tokenDefinitionParticle;
		}

		@Override
		public TypeToken<? extends ReducerState> getTypeToken() {
			return TypeToken.of(TokensConstraintScryptV2.NeedFixedTokenSupply.class);
		}
	}

	private void defineTokenCreation(SysCalls os) {
		os.createUpProcedure(new UpProcedure<>(
			CMAtomOS.REAddrClaim.class, TokenDefinitionParticle.class,
			(u, r) -> PermissionLevel.USER,
			(u, r, k) -> true,
			(s, u, r) -> {
				if (!u.getAddr().equals(s.getAddr())) {
					return ReducerResult.error("Addresses don't match");
				}

				if (u.isMutable()) {
					var action = new CreateMutableToken(
						new String(s.getArg()),
						u.getName(),
						u.getDescription(),
						u.getIconUrl(),
						u.getUrl()
					);
					return ReducerResult.complete(action);
				}

				return ReducerResult.incomplete(new TokensConstraintScryptV2.NeedFixedTokenSupply(s.getArg(), u));
			}
		));

		os.createUpProcedure(new UpProcedure<>(
			TokensConstraintScryptV2.NeedFixedTokenSupply.class, TokensParticle.class,
			(u, r) -> PermissionLevel.USER,
			(u, r, k) -> true,
			(s, u, r) -> {
				if (!u.getResourceAddr().equals(s.tokenDefinitionParticle.getAddr())) {
					return ReducerResult.error("Addresses don't match.");
				}

				if (!u.getAmount().equals(s.tokenDefinitionParticle.getSupply().orElseThrow())) {
					return ReducerResult.error("Initial supply doesn't match.");
				}

				var action = new CreateFixedToken(
					u.getResourceAddr(),
					u.getHoldingAddr(),
					new String(s.arg),
					s.tokenDefinitionParticle.getName(),
					s.tokenDefinitionParticle.getDescription(),
					s.tokenDefinitionParticle.getIconUrl(),
					s.tokenDefinitionParticle.getUrl(),
					s.tokenDefinitionParticle.getSupply().orElseThrow()
				);

				return ReducerResult.complete(action);
			}
		));
	}

	public static class TemporaryBucket implements ReducerState {
		private final REAddr tokenAddr;
		private final UInt384 amount;

		public TemporaryBucket(
			REAddr tokenAddr,
			UInt384 amount
		) {
			this.tokenAddr = tokenAddr;
			this.amount = amount;
		}

		public REAddr resourceAddr() {
			return tokenAddr;
		}

		public UInt384 amount() {
			return amount;
		}

		public TemporaryBucket deposit(UInt256 amountToAdd) {
			return new TemporaryBucket(tokenAddr, UInt384.from(amountToAdd).add(amount));
		}

		public Optional<TemporaryBucket> withdraw(UInt256 amountToWithdraw) {
			var withdraw384 = UInt384.from(amountToWithdraw);
			if (amount.compareTo(withdraw384) < 0) {
				return Optional.empty();
			}
			return Optional.of(new TemporaryBucket(tokenAddr, amount.subtract(withdraw384)));
		}

		@Override
		public TypeToken<? extends ReducerState> getTypeToken() {
			return TypeToken.of(TemporaryBucket.class);
		}
	}

	private void defineMintTransferBurn(SysCalls os) {
		// Mint
		os.createUpProcedure(new UpProcedure<>(
			VoidReducerState.class, TokensParticle.class,
			(u, r) -> u.getResourceAddr().isNativeToken() ? PermissionLevel.SYSTEM : PermissionLevel.USER,
			(u, r, k) -> {
				var tokenDef = (TokenDefinitionParticle) r.loadAddr(null, u.getResourceAddr()).orElseThrow();
				return k.flatMap(p -> tokenDef.getMinter().map(p::equals)).orElse(false);
			},
			(s, u, r) -> ReducerResult.complete(new MintToken(u.getResourceAddr(), u.getHoldingAddr(), u.getAmount()))
		));

		// Burn
		os.createEndProcedure(new EndProcedure<>(
			TemporaryBucket.class,
			(s, r) -> PermissionLevel.USER,
			(s, r, k) -> true,
			(s, r) -> {
				if (!s.amount.isZero()) {
					var p = r.loadAddr(null, s.tokenAddr);
					if (p.isEmpty()) {
						return ReducerResult.error("Token does not exist.");
					}
					var particle = p.get();
					if (!(particle instanceof TokenDefinitionParticle)) {
						return ReducerResult.error("Rri is not a token");
					}
					var tokenDef = (TokenDefinitionParticle) particle;
					if (!tokenDef.isMutable()) {
						return ReducerResult.error("Can only burn mutable tokens.");
					}
				}

				return ReducerResult.complete(Unknown.create());
			}
		));

		// Initial Withdraw
		os.createDownProcedure(new DownProcedure<>(
			TokensParticle.class, VoidReducerState.class,
			(d, r) -> PermissionLevel.USER,
			(d, r, k) -> d.getSubstate().allowedToWithdraw(k, r),
			(d, s, r) -> {
				var state = new TemporaryBucket(
					d.getSubstate().getResourceAddr(),
					UInt384.from(d.getSubstate().getAmount())
				);
				return ReducerResult.incomplete(state);
			}
		));

		// More Withdraws
		os.createDownProcedure(new DownProcedure<>(
			TokensParticle.class, TemporaryBucket.class,
			(d, r) -> PermissionLevel.USER,
			(d, r, k) -> d.getSubstate().allowedToWithdraw(k, r),
			(d, s, r) -> {
				if (!s.tokenAddr.equals(d.getSubstate().getResourceAddr())) {
					return ReducerResult.error("Not the same token");
				}
				var nextState = s.deposit(d.getSubstate().getAmount());
				return ReducerResult.incomplete(nextState);
			}
		));

		// Deposit
		os.createUpProcedure(new UpProcedure<>(
			TemporaryBucket.class, TokensParticle.class,
			(u, r) -> PermissionLevel.USER,
			(u, r, k) -> true,
			(s, u, r) -> {
				var nextState = s.withdraw(u.getAmount());
				if (nextState.isEmpty()) {
					return ReducerResult.error("Not enough funds for depositing");
				}
				return ReducerResult.incomplete(nextState.get());
			}
		));
	}
}
