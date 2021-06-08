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

import com.radixdlt.atom.actions.BurnToken;
import com.radixdlt.atom.actions.CreateFixedToken;
import com.radixdlt.atom.actions.CreateMutableToken;
import com.radixdlt.atom.actions.MintToken;
import com.radixdlt.atom.actions.StakeTokens;
import com.radixdlt.atom.actions.TransferToken;
import com.radixdlt.atommodel.tokens.state.PreparedStake;
import com.radixdlt.atommodel.tokens.state.ResourceInBucket;
import com.radixdlt.atommodel.tokens.state.TokenResource;
import com.radixdlt.atommodel.tokens.TokenDefinitionUtils;
import com.radixdlt.atommodel.tokens.state.TokensInAccount;
import com.radixdlt.atomos.CMAtomOS;
import com.radixdlt.atomos.ParticleDefinition;
import com.radixdlt.atomos.SysCalls;
import com.radixdlt.atomos.ConstraintScrypt;
import com.radixdlt.constraintmachine.AuthorizationException;
import com.radixdlt.constraintmachine.DownProcedure;
import com.radixdlt.constraintmachine.EndProcedure;
import com.radixdlt.constraintmachine.Particle;
import com.radixdlt.constraintmachine.PermissionLevel;
import com.radixdlt.constraintmachine.ProcedureException;
import com.radixdlt.constraintmachine.ReducerResult;
import com.radixdlt.constraintmachine.ReducerState;
import com.radixdlt.constraintmachine.UpProcedure;
import com.radixdlt.constraintmachine.VoidReducerState;
import com.radixdlt.identifiers.REAddr;
import com.radixdlt.utils.UInt384;

import java.util.Optional;

/**
 * Scrypt which defines how tokens are managed.
 */
public final class TokensConstraintScryptV1 implements ConstraintScrypt {
	@Override
	public void main(SysCalls os) {
		registerParticles(os);
		defineTokenCreation(os);
		defineMintTransferBurn(os);
	}

	private void registerParticles(SysCalls os) {
		os.registerParticle(
			TokenResource.class,
			ParticleDefinition.<TokenResource>builder()
				.staticValidation(TokenDefinitionUtils::staticCheck)
				.build()
		);

		os.registerParticle(
			TokensInAccount.class,
			ParticleDefinition.<TokensInAccount>builder()
				.staticValidation(TokenDefinitionUtils::staticCheck)
				.build()
		);
	}

	private static class NeedFixedTokenSupply implements ReducerState {
		private final byte[] arg;
		private final TokenResource tokenResource;
		private NeedFixedTokenSupply(byte[] arg, TokenResource tokenResource) {
			this.arg = arg;
			this.tokenResource = tokenResource;
		}
	}

	private void defineTokenCreation(SysCalls os) {
		os.createUpProcedure(new UpProcedure<>(
			CMAtomOS.REAddrClaim.class, TokenResource.class,
			(u, r) -> PermissionLevel.USER,
			(u, r, k) -> { },
			(s, u, r) -> {
				if (!u.getAddr().equals(s.getAddr())) {
					throw new ProcedureException("Addresses don't match");
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

				return ReducerResult.incomplete(new NeedFixedTokenSupply(s.getArg(), u));
			}
		));

		os.createUpProcedure(new UpProcedure<>(
			NeedFixedTokenSupply.class, TokensInAccount.class,
			(u, r) -> PermissionLevel.USER,
			(u, r, k) -> { },
			(s, u, r) -> {
				if (!u.getResourceAddr().equals(s.tokenResource.getAddr())) {
					throw new ProcedureException("Addresses don't match.");
				}

				if (!u.getAmount().equals(s.tokenResource.getSupply().orElseThrow())) {
					throw new ProcedureException("Initial supply doesn't match.");
				}

				var action = new CreateFixedToken(
					u.getResourceAddr(),
					u.getHoldingAddr(),
					new String(s.arg),
					s.tokenResource.getName(),
					s.tokenResource.getDescription(),
					s.tokenResource.getIconUrl(),
					s.tokenResource.getUrl(),
					s.tokenResource.getSupply().orElseThrow()
				);

				return ReducerResult.complete(action);
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
			} else if (compare < 0) {
				return Optional.of(new RemainderTokens(initialParticle, tokenAddr, amount.subtract(amountToSubtract)));
			} else {
				return Optional.empty();
			}
		}
	}

	private void defineMintTransferBurn(SysCalls os) {
		// Mint
		os.createEndProcedure(new EndProcedure<>(
			UnaccountedTokens.class,
			(s, r) -> s.resourceInBucket.resourceAddr().isNativeToken() ? PermissionLevel.SYSTEM : PermissionLevel.USER,
			(s, r, k) -> {
				var tokenDef = (TokenResource) r.loadAddr(null, s.resourceInBucket.resourceAddr())
					.orElseThrow(() -> new AuthorizationException("Invalid token address: " + s.resourceInBucket.resourceAddr()));

				tokenDef.verifyMintAuthorization(k);
			},
			(s, r) -> {
				if (s.resourceInBucket.epochUnlocked().isPresent()) {
					throw new ProcedureException("Cannot mint locked tokens.");
				}

				var p = r.loadAddr(null, s.resourceInBucket.resourceAddr());
				if (p.isEmpty()) {
					throw new ProcedureException("Token does not exist.");
				}
				var particle = p.get();
				if (!(particle instanceof TokenResource)) {
					throw new ProcedureException("Rri is not a token");
				}
				var tokenDef = (TokenResource) particle;
				if (!tokenDef.isMutable()) {
					throw new ProcedureException("Can only mint mutable tokens.");
				}

				var t = (TokensInAccount) s.initialParticle;
				return Optional.of(new MintToken(s.resourceInBucket.resourceAddr(), t.getHoldingAddr(), s.amount.getLow()));
			}
		));

		// Burn
		os.createEndProcedure(new EndProcedure<>(
			RemainderTokens.class,
			(s, r) -> PermissionLevel.USER,
			(s, r, k) -> { },
			(s, r) -> {
				var p = r.loadAddr(null, s.tokenAddr);
				if (p.isEmpty()) {
					throw new ProcedureException("Token does not exist.");
				}
				var particle = p.get();
				if (!(particle instanceof TokenResource)) {
					throw new ProcedureException("Rri is not a token");
				}
				var tokenDef = (TokenResource) particle;
				if (!tokenDef.isMutable()) {
					throw new ProcedureException("Can only burn mutable tokens.");
				}

				// FIXME: These aren't 100% correct
				var t = (TokensInAccount) s.initialParticle;
				return Optional.of(new BurnToken(s.tokenAddr, t.getHoldingAddr(), s.amount.getLow()));
			}
		));

		os.createUpProcedure(new UpProcedure<>(
			VoidReducerState.class, TokensInAccount.class,
			(u, r) -> PermissionLevel.USER,
			(u, r, k) -> { },
			(s, u, r) -> {
				var state = new UnaccountedTokens(
					u,
					u.resourceInBucket(),
					UInt384.from(u.getAmount())
				);
				return ReducerResult.incomplete(state);
			}
		));

		os.createDownProcedure(new DownProcedure<>(
			TokensInAccount.class, VoidReducerState.class,
			(d, r) -> PermissionLevel.USER,
			(d, r, k) -> d.getSubstate().verifyWithdrawAuthorization(k, r),
			(d, s, r) -> {
				var state = new RemainderTokens(
					d.getSubstate(),
					d.getSubstate().getResourceAddr(),
					UInt384.from(d.getSubstate().getAmount())
				);
				return ReducerResult.incomplete(state);
			}
		));

		os.createUpProcedure(new UpProcedure<>(
			RemainderTokens.class, TokensInAccount.class,
			(u, r) -> PermissionLevel.USER,
			(u, r, k) -> { },
			(s, u, r) -> {
				if (!s.tokenAddr.equals(u.getResourceAddr())) {
					throw new ProcedureException("Not the same address.");
				}
				var amt = UInt384.from(u.getAmount());
				var nextRemainder = s.subtract(u.resourceInBucket(), amt);
				if (nextRemainder.isEmpty()) {
					// FIXME: This isn't 100% correct
					var p = s.initialParticle;
					if (p instanceof TokensInAccount) {
						var t = (TokensInAccount) p;
						var action = new TransferToken(t.getResourceAddr(), u.getHoldingAddr(), t.getHoldingAddr(), t.getAmount());
						return ReducerResult.complete(action);
					} else if (p instanceof PreparedStake) {
						var t = (PreparedStake) p;
						var action = new StakeTokens(t.getOwner(), t.getDelegateKey(), t.getAmount());
						return ReducerResult.complete(action);
					} else {
						throw new IllegalStateException();
					}
				}
				return ReducerResult.incomplete(nextRemainder.get());
			}
		));

		os.createDownProcedure(new DownProcedure<>(
			TokensInAccount.class, UnaccountedTokens.class,
			(d, r) -> PermissionLevel.USER,
			(d, r, k) -> d.getSubstate().verifyWithdrawAuthorization(k, r),
			(d, s, r) -> {
				if (!s.resourceInBucket.resourceAddr().equals(d.getSubstate().getResourceAddr())) {
					throw new ProcedureException("Not the same address.");
				}
				var amt = UInt384.from(d.getSubstate().getAmount());
				var nextRemainder = s.subtract(amt);
				if (nextRemainder.isEmpty()) {
					// FIXME: This isn't 100% correct
					var p = s.initialParticle;
					if (p instanceof TokensInAccount) {
						var t = (TokensInAccount) p;
						var action = new TransferToken(t.getResourceAddr(), d.getSubstate().getHoldingAddr(), t.getHoldingAddr(), t.getAmount());
						return ReducerResult.complete(action);
					} else if (p instanceof PreparedStake) {
						var t = (PreparedStake) p;
						var action = new StakeTokens(t.getOwner(), t.getDelegateKey(), t.getAmount());
						return ReducerResult.complete(action);
					} else {
						throw new IllegalStateException();
					}
				}

				return ReducerResult.incomplete(nextRemainder.get());
			}
		));
	}
}
