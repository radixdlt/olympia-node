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
import com.radixdlt.atommodel.tokens.state.DeprecatedStake;
import com.radixdlt.atommodel.tokens.state.ResourceInBucket;
import com.radixdlt.atommodel.tokens.state.TokenDefinitionParticle;
import com.radixdlt.atommodel.tokens.TokenDefinitionUtils;
import com.radixdlt.atommodel.tokens.state.TokensParticle;
import com.radixdlt.atomos.CMAtomOS;
import com.radixdlt.atomos.ParticleDefinition;
import com.radixdlt.atomos.SysCalls;
import com.radixdlt.atomos.ConstraintScrypt;
import com.radixdlt.constraintmachine.DownProcedure;
import com.radixdlt.constraintmachine.EndProcedure;
import com.radixdlt.constraintmachine.Particle;
import com.radixdlt.constraintmachine.PermissionLevel;
import com.radixdlt.constraintmachine.ReducerResult2;
import com.radixdlt.constraintmachine.ReducerState;
import com.radixdlt.constraintmachine.UpProcedure;
import com.radixdlt.constraintmachine.VoidReducerState;
import com.radixdlt.identifiers.REAddr;
import com.radixdlt.utils.UInt384;

import java.util.Optional;

/**
 * Scrypt which defines how tokens are managed.
 */
public class TokensConstraintScrypt implements ConstraintScrypt {
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
				.rriMapper(TokenDefinitionParticle::getAddr)
				.build()
		);

		os.registerParticle(
			TokensParticle.class,
			ParticleDefinition.<TokensParticle>builder()
				.allowTransitionsFromOutsideScrypts()
				.staticValidation(TokenDefinitionUtils::staticCheck)
				.rriMapper(TokensParticle::getResourceAddr)
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
			return TypeToken.of(NeedFixedTokenSupply.class);
		}
	}

	private void defineTokenCreation(SysCalls os) {
		os.createUpProcedure(new UpProcedure<>(
			CMAtomOS.REAddrClaim.class, TokenDefinitionParticle.class,
			(u, r) -> PermissionLevel.USER,
			(u, r, k) -> true,
			(s, u, r) -> {
				if (!u.getAddr().equals(s.getAddr())) {
					return ReducerResult2.error("Addresses don't match");
				}

				if (u.isMutable()) {
					var action = new CreateMutableToken(
						new String(s.getArg()),
						u.getName(),
						u.getDescription(),
						u.getIconUrl(),
						u.getUrl()
					);
					return ReducerResult2.complete(action);
				}

				return ReducerResult2.incomplete(new NeedFixedTokenSupply(s.getArg(), u));
			}
		));

		os.createUpProcedure(new UpProcedure<>(
			NeedFixedTokenSupply.class, TokensParticle.class,
			(u, r) -> PermissionLevel.USER,
			(u, r, k) -> true,
			(s, u, r) -> {
				if (!u.getResourceAddr().equals(s.tokenDefinitionParticle.getAddr())) {
					return ReducerResult2.error("Addresses don't match.");
				}

				if (!u.getAmount().equals(s.tokenDefinitionParticle.getSupply().orElseThrow())) {
					return ReducerResult2.error("Initial supply doesn't match.");
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

				return ReducerResult2.complete(action);
			}
		));
	}

	// TODO: Remove so that up particles cannot be created first
	public static class UnaccountedTokens implements ReducerState {
		private final Particle initialParticle;
		private final ResourceInBucket resourceInBucket;
		private final UInt384 amount;

		public UnaccountedTokens(Particle initialParticle, ResourceInBucket resourceInBucket, UInt384 amount) {
			this.initialParticle = initialParticle;
			this.resourceInBucket = resourceInBucket;
			this.amount = amount;
		}

		public Particle initialParticle() {
			return initialParticle;
		}

		public ResourceInBucket resourceInBucket() {
			return resourceInBucket;
		}

		public Optional<ReducerState> subtract(UInt384 amountAccounted) {
			var compare = amountAccounted.compareTo(amount);
			if (compare > 0) {
				return Optional.of(new RemainderTokens(initialParticle, resourceInBucket.resourceAddr(), amountAccounted.subtract(amount)));
			} else if (compare < 0) {
				return Optional.of(new UnaccountedTokens(initialParticle, resourceInBucket, amount.subtract(amountAccounted)));
			} else {
				return Optional.empty();
			}
		}

		@Override
		public TypeToken<? extends ReducerState> getTypeToken() {
			return TypeToken.of(UnaccountedTokens.class);
		}
	}

	public static class RemainderTokens implements ReducerState {
		private final REAddr tokenAddr;
		private final UInt384 amount;
		private final Particle initialParticle;

		public RemainderTokens(
			Particle initialParticle, // TODO: Remove, Hack for now
			REAddr tokenAddr,
			UInt384 amount
		) {
			this.initialParticle = initialParticle;
			this.tokenAddr = tokenAddr;
			this.amount = amount;
		}

		public UInt384 amount() {
			return amount;
		}

		public Particle initialParticle() {
			return initialParticle;
		}

		private Optional<ReducerState> subtract(ResourceInBucket resourceInBucket, UInt384 amountToSubtract) {
			var compare = amountToSubtract.compareTo(amount);
			if (compare > 0) {
				return Optional.of(new UnaccountedTokens(initialParticle, resourceInBucket, amountToSubtract.subtract(amount)));
			} else if (compare < 0){
				return Optional.of(new RemainderTokens(initialParticle, tokenAddr, amount.subtract(amountToSubtract)));
			} else {
				return Optional.empty();
			}
		}

		@Override
		public TypeToken<? extends ReducerState> getTypeToken() {
			return TypeToken.of(RemainderTokens.class);
		}
	}

	private void defineMintTransferBurn(SysCalls os) {
		// Mint
		os.createEndProcedure(new EndProcedure<>(
			UnaccountedTokens.class,
			(s, r) -> s.resourceInBucket.resourceAddr().isNativeToken() ? PermissionLevel.SYSTEM : PermissionLevel.USER,
			(s, r, k) -> {
				var tokenDef = (TokenDefinitionParticle) r.loadAddr(null, s.resourceInBucket.resourceAddr()).orElseThrow();
				return k.flatMap(p -> tokenDef.getMinter().map(p::equals)).orElse(false);
			},
			(s, r) -> {
				if (s.resourceInBucket.epochUnlocked().isPresent()) {
					return ReducerResult2.error("Cannot mint locked tokens.");
				}

				var p = r.loadAddr(null, s.resourceInBucket.resourceAddr());
				if (p.isEmpty()) {
					return ReducerResult2.error("Token does not exist.");
				}
				var particle = p.get();
				if (!(particle instanceof TokenDefinitionParticle)) {
					return ReducerResult2.error("Rri is not a token");
				}
				var tokenDef = (TokenDefinitionParticle) particle;
				if (!tokenDef.isMutable()) {
					return ReducerResult2.error("Can only mint mutable tokens.");
				}

				var t = (TokensParticle) s.initialParticle;
				var action = new MintToken(s.resourceInBucket.resourceAddr(), t.getHoldingAddr(), s.amount.getLow());
				return ReducerResult2.complete(action);
			}
		));

		// Burn
		os.createEndProcedure(new EndProcedure<>(
			RemainderTokens.class,
			(s, r) -> PermissionLevel.USER,
			(s, r, k) -> true,
			(s, r) -> {
				var p = r.loadAddr(null, s.tokenAddr);
				if (p.isEmpty()) {
					return ReducerResult2.error("Token does not exist.");
				}
				var particle = p.get();
				if (!(particle instanceof TokenDefinitionParticle)) {
					return ReducerResult2.error("Rri is not a token");
				}
				var tokenDef = (TokenDefinitionParticle) particle;
				if (!tokenDef.isMutable()) {
					return ReducerResult2.error("Can only burn mutable tokens.");
				}

				// FIXME: These aren't 100% correct
				var t = (TokensParticle) s.initialParticle;
				var action = new BurnToken(s.tokenAddr, t.getHoldingAddr(), s.amount.getLow());
				return ReducerResult2.complete(action);
			}
		));

		os.createUpProcedure(new UpProcedure<>(
			VoidReducerState.class, TokensParticle.class,
			(u, r) -> PermissionLevel.USER,
			(u, r, k) -> true,
			(s, u, r) -> {
				var state = new UnaccountedTokens(
					u,
					u.resourceInBucket(),
					UInt384.from(u.getAmount())
				);
				return ReducerResult2.incomplete(state);
			}
		));

		os.createDownProcedure(new DownProcedure<>(
			TokensParticle.class, VoidReducerState.class,
			(d, r) -> PermissionLevel.USER,
			(d, r, k) -> d.getSubstate().allowedToWithdraw(k, r),
			(d, s, r) -> {
				var state = new RemainderTokens(
					d.getSubstate(),
					d.getSubstate().getResourceAddr(),
					UInt384.from(d.getSubstate().getAmount())
				);
				return ReducerResult2.incomplete(state);
			}
		));

		os.createUpProcedure(new UpProcedure<>(
			RemainderTokens.class, TokensParticle.class,
			(u, r) -> PermissionLevel.USER,
			(u, r, k) -> true,
			(s, u, r) -> {
				if (!s.tokenAddr.equals(u.getResourceAddr())) {
					return ReducerResult2.error("Not the same address.");
				}
				var amt = UInt384.from(u.getAmount());
				var nextRemainder = s.subtract(u.resourceInBucket(), amt);
				if (nextRemainder.isEmpty()) {
					// FIXME: This isn't 100% correct
					var p = s.initialParticle;
					if (p instanceof TokensParticle) {
						var t = (TokensParticle) p;
						var action = new TransferToken(t.getResourceAddr(), u.getHoldingAddr(), t.getHoldingAddr(), t.getAmount());
						return ReducerResult2.complete(action);
					} else if (p instanceof DeprecatedStake) {
						var t = (DeprecatedStake) p;
						var action = new StakeTokens(t.getOwner(), t.getDelegateKey(), t.getAmount());
						return ReducerResult2.complete(action);
					} else {
						throw new IllegalStateException();
					}
				}
				return ReducerResult2.incomplete(nextRemainder.get());
			}
		));

		os.createDownProcedure(new DownProcedure<>(
			TokensParticle.class, UnaccountedTokens.class,
			(d, r) -> PermissionLevel.USER,
			(d, r, k) -> d.getSubstate().allowedToWithdraw(k, r),
			(d, s, r) -> {
				if (!s.resourceInBucket.resourceAddr().equals(d.getSubstate().getResourceAddr())) {
					return ReducerResult2.error("Not the same address.");
				}
				var amt = UInt384.from(d.getSubstate().getAmount());
				var nextRemainder = s.subtract(amt);
				if (nextRemainder.isEmpty()) {
					// FIXME: This isn't 100% correct
					var p = s.initialParticle;
					if (p instanceof TokensParticle) {
						var t = (TokensParticle) p;
						var action = new TransferToken(t.getResourceAddr(), d.getSubstate().getHoldingAddr(), t.getHoldingAddr(), t.getAmount());
						return ReducerResult2.complete(action);
					} else if (p instanceof DeprecatedStake) {
						var t = (DeprecatedStake) p;
						var action = new StakeTokens(t.getOwner(), t.getDelegateKey(), t.getAmount());
						return ReducerResult2.complete(action);
					} else {
						throw new IllegalStateException();
					}
				}

				return ReducerResult2.incomplete(nextRemainder.get());
			}
		));
	}
}
