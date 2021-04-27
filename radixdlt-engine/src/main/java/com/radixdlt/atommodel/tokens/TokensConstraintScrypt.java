/*
 * (C) Copyright 2020 Radix DLT Ltd
 *
 * Radix DLT Ltd licenses this file to you under the Apache License,
 * Version 2.0 (the "License"); you may not use this file except in
 * compliance with the License.  You may obtain a copy of the
 * License at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND,
 * either express or implied.  See the License for the specific
 * language governing permissions and limitations under the License.
 */

package com.radixdlt.atommodel.tokens;

import com.google.common.annotations.VisibleForTesting;
import com.radixdlt.atom.actions.TransferToken;
import com.radixdlt.atommodel.routines.AllocateTokensRoutine;
import com.radixdlt.atommodel.routines.DeallocateTokensRoutine;
import com.radixdlt.atomos.ParticleDefinition;
import com.radixdlt.atomos.SysCalls;
import com.radixdlt.atomos.ConstraintScrypt;
import com.radixdlt.atomos.Result;
import com.radixdlt.atommodel.routines.CreateFungibleTransitionRoutine;

import java.util.Objects;

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

	private void defineTokenCreation(SysCalls os) {
		os.createTransitionFromRRICombined(
			TokenDefinitionParticle.class,
			TokensParticle.class,
			t -> !t.isMutable(),
			TokensConstraintScrypt::checkCreateTransferrable
		);
	}

	private void defineMintTransferBurn(SysCalls os) {
		// Mint
		os.executeRoutine(new AllocateTokensRoutine());

		// Transfers
		os.executeRoutine(new CreateFungibleTransitionRoutine<>(
			TokensParticle.class,
			TokensParticle.class,
			TokensParticle::getAmount,
			TokensParticle::getAmount,
			(i, o) -> Result.success(),
			(i, o, index, pubKey) -> pubKey.map(i.getSubstate().getHoldingAddr()::allowToWithdrawFrom).orElse(false),
			(i, o, index) -> {
				var p = (TokenDefinitionParticle) index.loadRri(null, i.getResourceAddr()).orElseThrow();
				// FIXME: This isn't 100% correct
				return new TransferToken(p.getAddr(), i.getHoldingAddr(), o.getHoldingAddr(), o.getAmount());
			}
		));

		// Burns
		os.executeRoutine(new DeallocateTokensRoutine());
	}

	@VisibleForTesting
	static Result checkCreateTransferrable(TokenDefinitionParticle tokDef, TokensParticle transferrable) {
		if (!Objects.equals(tokDef.getSupply().orElseThrow(), transferrable.getAmount())) {
			return Result.error("Supply and amount are not equal.");
		}

		return Result.success();
	}
}
