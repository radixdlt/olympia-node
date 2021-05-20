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
import com.radixdlt.atom.actions.CreateFixedToken;
import com.radixdlt.atom.actions.CreateMutableToken;
import com.radixdlt.atom.actions.TransferToken;
import com.radixdlt.atommodel.tokens.state.TokenDefinitionParticle;
import com.radixdlt.atommodel.tokens.TokenDefinitionUtils;
import com.radixdlt.atommodel.tokens.state.TokensParticle;
import com.radixdlt.atomos.CMAtomOS;
import com.radixdlt.atomos.ParticleDefinition;
import com.radixdlt.atomos.SysCalls;
import com.radixdlt.atomos.ConstraintScrypt;
import com.radixdlt.atomos.Result;
import com.radixdlt.constraintmachine.PermissionLevel;
import com.radixdlt.constraintmachine.ReducerResult2;
import com.radixdlt.constraintmachine.ReducerState;
import com.radixdlt.constraintmachine.UpProcedure;

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

	private void defineMintTransferBurn(SysCalls os) {
		// Mint
		os.executeRoutine(new AllocateTokensRoutine());

		// Transfers
		os.executeRoutine(new CreateFungibleTransitionRoutine<>(
			TokensParticle.class,
			TokensParticle.class,
			(i, o, r) -> Result.success(),
			(i, r, pubKey) -> i.getSubstate().allowedToWithdraw(pubKey, r),
			(i, o, r) -> {
				var p = (TokenDefinitionParticle) r.loadAddr(null, i.getResourceAddr()).orElseThrow();
				// FIXME: This isn't 100% correct
				return new TransferToken(p.getAddr(), i.getHoldingAddr(), o.getHoldingAddr(), o.getAmount());
			}
		));

		// Burns
		os.executeRoutine(new DeallocateTokensRoutine());
	}
}
