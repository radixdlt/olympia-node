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

import com.radixdlt.atommodel.tokens.TokenDefinitionUtils;
import com.radixdlt.atommodel.tokens.state.TokenResource;
import com.radixdlt.atommodel.tokens.state.TokensInAccount;
import com.radixdlt.atomos.CMAtomOS;
import com.radixdlt.atomos.ConstraintScrypt;
import com.radixdlt.atomos.ParticleDefinition;
import com.radixdlt.atomos.Loader;
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
import com.radixdlt.utils.UInt384;


public class TokensConstraintScryptV2 implements ConstraintScrypt {
	@Override
	public void main(Loader os) {
		registerParticles(os);
		defineTokenCreation(os);
		defineMintTransferBurn(os);
	}

	private void registerParticles(Loader os) {
		os.particle(
			TokenResource.class,
			ParticleDefinition.<TokenResource>builder()
				.staticValidation(TokenDefinitionUtils::staticCheck)
				.build()
		);

		os.particle(
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

	private void defineTokenCreation(Loader os) {
		os.procedure(new UpProcedure<>(
			CMAtomOS.REAddrClaim.class, TokenResource.class,
			u -> new Authorization(PermissionLevel.USER, (r, c) -> { }),
			(s, u, r) -> {
				if (!u.getAddr().equals(s.getAddr())) {
					throw new ProcedureException("Addresses don't match");
				}

				if (u.isMutable()) {
					return ReducerResult.complete();
				}

				return ReducerResult.incomplete(new TokensConstraintScryptV2.NeedFixedTokenSupply(s.getArg(), u));
			}
		));

		os.procedure(new UpProcedure<>(
			TokensConstraintScryptV2.NeedFixedTokenSupply.class, TokensInAccount.class,
			u -> new Authorization(PermissionLevel.USER, (r, c) -> { }),
			(s, u, r) -> {
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

	private void defineMintTransferBurn(Loader os) {
		// Mint
		os.procedure(new UpProcedure<>(
			VoidReducerState.class, TokensInAccount.class,
			u -> {
				if (u.getResourceAddr().isNativeToken()) {
					return new Authorization(PermissionLevel.SUPER_USER, (r, c) -> { });
				}
				return new Authorization(PermissionLevel.USER, (r, c) -> {
					var tokenDef = (TokenResource) r.loadAddr(null, u.getResourceAddr())
						.orElseThrow(() -> new AuthorizationException("Invalid token address: " + u.getResourceAddr()));
					tokenDef.verifyMintAuthorization(c.key());
				});
			},
			(s, u, r) -> ReducerResult.complete()
		));

		// Burn
		os.procedure(new EndProcedure<>(
			TokenHoldingBucket.class,
			s -> new Authorization(PermissionLevel.USER, (r, c) -> { }),
			TokenHoldingBucket::destroy
		));

		// Initial Withdraw
		os.procedure(new DownProcedure<>(
			TokensInAccount.class, VoidReducerState.class,
			d -> d.getSubstate().bucket().withdrawAuthorization(),
			(d, s, r) -> {
				var tokens = d.getSubstate();
				var state = new TokenHoldingBucket(
					tokens.getResourceAddr(),
					UInt384.from(tokens.getAmount())
				);
				return ReducerResult.incomplete(state);
			}
		));

		// More Withdraws
		os.procedure(new DownProcedure<>(
			TokensInAccount.class, TokenHoldingBucket.class,
			d -> d.getSubstate().bucket().withdrawAuthorization(),
			(d, s, r) -> {
				var tokens = d.getSubstate();
				var nextState = s.deposit(
					tokens.getResourceAddr(),
					tokens.getAmount()
				);
				return ReducerResult.incomplete(nextState);
			}
		));

		// Deposit
		os.procedure(new UpProcedure<>(
			TokenHoldingBucket.class, TokensInAccount.class,
			u -> new Authorization(PermissionLevel.USER, (r, c) -> { }),
			(s, u, r) -> {
				var nextState = s.withdraw(u.getResourceAddr(), u.getAmount());
				return ReducerResult.incomplete(nextState);
			}
		));
	}
}
