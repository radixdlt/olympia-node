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

import com.radixdlt.atommodel.tokens.state.DeprecatedResourceInBucket;
import com.radixdlt.atommodel.tokens.state.TokenResource;
import com.radixdlt.atommodel.tokens.TokenDefinitionUtils;
import com.radixdlt.atommodel.tokens.state.TokensInAccount;
import com.radixdlt.atomos.CMAtomOS;
import com.radixdlt.atomos.SubstateDefinition;
import com.radixdlt.atomos.Loader;
import com.radixdlt.atomos.ConstraintScrypt;
import com.radixdlt.constraintmachine.Authorization;
import com.radixdlt.constraintmachine.AuthorizationException;
import com.radixdlt.constraintmachine.DownProcedure;
import com.radixdlt.constraintmachine.EndProcedure;
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
	public void main(Loader os) {
		registerParticles(os);
		defineTokenCreation(os);
		defineMintTransferBurn(os);
	}

	private void registerParticles(Loader os) {
		os.substate(
			new SubstateDefinition<>(
				TokenResource.class,
				TokenDefinitionUtils::staticCheck
			)
		);

		os.substate(
			new SubstateDefinition<>(
				TokensInAccount.class,
				TokenDefinitionUtils::staticCheck
			)
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

	private void defineTokenCreation(Loader os) {
		os.procedure(new UpProcedure<>(
			CMAtomOS.REAddrClaim.class, TokenResource.class,
			u -> new Authorization(PermissionLevel.USER, (r, c) -> { }),
			(s, u, c, r) -> {
				if (!u.getAddr().equals(s.getAddr())) {
					throw new ProcedureException("Addresses don't match");
				}

				if (u.isMutable()) {
					return ReducerResult.complete();
				}

				return ReducerResult.incomplete(new NeedFixedTokenSupply(s.getArg(), u));
			}
		));

		os.procedure(new UpProcedure<>(
			NeedFixedTokenSupply.class, TokensInAccount.class,
			u -> new Authorization(PermissionLevel.USER, (r, c) -> { }),
			(s, u, c, r) -> {
				if (!u.getResourceAddr().equals(s.tokenResource.getAddr())) {
					throw new ProcedureException("Addresses don't match.");
				}

				if (!u.getAmount().equals(s.tokenResource.getSupply().orElseThrow())) {
					throw new ProcedureException("Initial supply doesn't match.");
				}

				return ReducerResult.complete();
			}
		));
	}

	// TODO: Remove so that up particles cannot be created first
	public static class UnaccountedTokens implements ReducerState {
		private final DeprecatedResourceInBucket resourceInBucket;
		private final UInt384 amount;

		public UnaccountedTokens(DeprecatedResourceInBucket resourceInBucket, UInt384 amount) {
			this.resourceInBucket = resourceInBucket;
			this.amount = amount;
		}

		public DeprecatedResourceInBucket resourceInBucket() {
			return resourceInBucket;
		}

		public Optional<ReducerState> subtract(UInt384 amountAccounted) {
			var compare = amountAccounted.compareTo(amount);
			if (compare > 0) {
				return Optional.of(new RemainderTokens(resourceInBucket.resourceAddr(), amountAccounted.subtract(amount)));
			} else if (compare < 0) {
				return Optional.of(new UnaccountedTokens(resourceInBucket, amount.subtract(amountAccounted)));
			} else {
				return Optional.empty();
			}
		}
	}

	public static class RemainderTokens implements ReducerState {
		private final REAddr tokenAddr;
		private final UInt384 amount;

		public RemainderTokens(
			REAddr tokenAddr,
			UInt384 amount
		) {
			this.tokenAddr = tokenAddr;
			this.amount = amount;
		}

		public UInt384 amount() {
			return amount;
		}

		private Optional<ReducerState> subtract(DeprecatedResourceInBucket resourceInBucket, UInt384 amountToSubtract) {
			var compare = amountToSubtract.compareTo(amount);
			if (compare > 0) {
				return Optional.of(new UnaccountedTokens(resourceInBucket, amountToSubtract.subtract(amount)));
			} else if (compare < 0) {
				return Optional.of(new RemainderTokens(tokenAddr, amount.subtract(amountToSubtract)));
			} else {
				return Optional.empty();
			}
		}
	}

	private void defineMintTransferBurn(Loader os) {
		// Mint
		os.procedure(new EndProcedure<>(
			UnaccountedTokens.class,
			s -> {
				var level = s.resourceInBucket.resourceAddr().isNativeToken() ? PermissionLevel.SYSTEM : PermissionLevel.USER;
				return new Authorization(
					level,
					(r, c) -> {
						var tokenDef = (TokenResource) r.loadAddr(null, s.resourceInBucket.resourceAddr())
							.orElseThrow(() -> new AuthorizationException("Invalid token address: " + s.resourceInBucket.resourceAddr()));

						tokenDef.verifyMintAuthorization(c.key());
					}
				);
			},
			(s, c, r) -> {
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
			}
		));

		// Burn
		os.procedure(new EndProcedure<>(
			RemainderTokens.class,
			s -> new Authorization(PermissionLevel.USER, (r, c) -> { }),
			(s, c, r) -> {
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
			}
		));

		os.procedure(new UpProcedure<>(
			VoidReducerState.class, TokensInAccount.class,
			u -> new Authorization(PermissionLevel.USER, (r, c) -> { }),
			(s, u, c, r) -> {
				var state = new UnaccountedTokens(
					u.deprecatedResourceInBucket(),
					UInt384.from(u.getAmount())
				);
				return ReducerResult.incomplete(state);
			}
		));

		os.procedure(new DownProcedure<>(
			TokensInAccount.class, VoidReducerState.class,
			d -> d.getSubstate().bucket().withdrawAuthorization(),
			(d, s, r) -> {
				var state = new RemainderTokens(
					d.getSubstate().getResourceAddr(),
					UInt384.from(d.getSubstate().getAmount())
				);
				return ReducerResult.incomplete(state);
			}
		));

		os.procedure(new UpProcedure<>(
			RemainderTokens.class, TokensInAccount.class,
			u -> new Authorization(PermissionLevel.USER, (r, c) -> { }),
			(s, u, c, r) -> {
				if (!s.tokenAddr.equals(u.getResourceAddr())) {
					throw new ProcedureException("Not the same address.");
				}
				var amt = UInt384.from(u.getAmount());
				var nextRemainder = s.subtract(u.deprecatedResourceInBucket(), amt);
				if (nextRemainder.isEmpty()) {
					return ReducerResult.complete();
				}
				return ReducerResult.incomplete(nextRemainder.get());
			}
		));

		os.procedure(new DownProcedure<>(
			TokensInAccount.class, UnaccountedTokens.class,
			d -> d.getSubstate().bucket().withdrawAuthorization(),
			(d, s, r) -> {
				if (!s.resourceInBucket.resourceAddr().equals(d.getSubstate().getResourceAddr())) {
					throw new ProcedureException("Not the same address.");
				}
				var amt = UInt384.from(d.getSubstate().getAmount());
				var nextRemainder = s.subtract(amt);
				if (nextRemainder.isEmpty()) {
					return ReducerResult.complete();
				}

				return ReducerResult.incomplete(nextRemainder.get());
			}
		));
	}
}
