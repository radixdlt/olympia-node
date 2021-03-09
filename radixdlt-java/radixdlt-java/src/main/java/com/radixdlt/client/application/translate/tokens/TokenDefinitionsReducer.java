/*
 * (C) Copyright 2020 Radix DLT Ltd
 *
 * Permission is hereby granted, free of charge, to any person obtaining a
 * copy of this software and associated documentation files (the “Software”),
 * to deal in the Software without restriction, including without limitation
 * the rights to use, copy, modify, merge, publish, distribute, sublicense,
 * and/or sell copies of the Software, and to permit persons to whom the
 * Software is furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be
 * included in all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED “AS IS”, WITHOUT WARRANTY OF ANY KIND, EXPRESS
 * OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL
 * THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING
 * FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER
 * DEALINGS IN THE SOFTWARE.
 */

package com.radixdlt.client.application.translate.tokens;

import com.radixdlt.application.TokenUnitConversions;
import com.radixdlt.client.atommodel.tokens.MutableSupplyTokenDefinitionParticle.TokenTransition;
import com.radixdlt.client.atommodel.tokens.UnallocatedTokensParticle;

import com.radixdlt.client.atommodel.tokens.MutableSupplyTokenDefinitionParticle;

import com.radixdlt.client.application.translate.ParticleReducer;
import com.radixdlt.client.application.translate.tokens.TokenState.TokenSupplyType;
import com.radixdlt.client.atommodel.tokens.FixedSupplyTokenDefinitionParticle;
import com.radixdlt.client.atommodel.tokens.TokenPermission;
import com.radixdlt.client.core.atoms.particles.Particle;

/**
 * Reduces particles at an address into concrete Tokens and their states
 */
public class TokenDefinitionsReducer implements ParticleReducer<TokenDefinitionsState> {

	@Override
	public Class<TokenDefinitionsState> stateClass() {
		return TokenDefinitionsState.class;
	}

	@Override
	public TokenDefinitionsState initialState() {
		return TokenDefinitionsState.init();
	}

	@Override
	public TokenDefinitionsState reduce(TokenDefinitionsState state, Particle p) {
		if (p instanceof MutableSupplyTokenDefinitionParticle) {
			return reduceInternal(state, (MutableSupplyTokenDefinitionParticle) p);
		} else if (p instanceof FixedSupplyTokenDefinitionParticle) {
			return reduceInternal(state, (FixedSupplyTokenDefinitionParticle) p);
		} else  if (p instanceof UnallocatedTokensParticle) {
			UnallocatedTokensParticle u = (UnallocatedTokensParticle) p;
			return state.mergeUnallocated(u);
		}

		return state;
	}

	@Override
	public TokenDefinitionsState combine(TokenDefinitionsState state0, TokenDefinitionsState state1) {
		return TokenDefinitionsState.combine(state0, state1);
	}

	private TokenDefinitionsState reduceInternal(TokenDefinitionsState state, MutableSupplyTokenDefinitionParticle tokenDefinitionParticle) {
		TokenPermission mintPermission = tokenDefinitionParticle.getTokenPermissions().get(TokenTransition.MINT);

		if (!mintPermission.equals(TokenPermission.TOKEN_OWNER_ONLY) && !mintPermission.equals(TokenPermission.ALL)) {
			throw new IllegalStateException(
				"TokenDefinitionParticle with mintPermissions of " + mintPermission + " not supported.");
		}

		return state.mergeTokenClass(
			tokenDefinitionParticle.getRRI(),
			tokenDefinitionParticle.getName(),
			tokenDefinitionParticle.getSymbol(),
			tokenDefinitionParticle.getDescription(),
			tokenDefinitionParticle.getIconUrl(),
			null,
			TokenUnitConversions.subunitsToUnits(tokenDefinitionParticle.getGranularity()),
			TokenSupplyType.MUTABLE
		);
	}

	private TokenDefinitionsState reduceInternal(TokenDefinitionsState state, FixedSupplyTokenDefinitionParticle tokenDefinitionParticle) {
		return state.mergeTokenClass(
			tokenDefinitionParticle.getRRI(),
			tokenDefinitionParticle.getName(),
			tokenDefinitionParticle.getSymbol(),
			tokenDefinitionParticle.getDescription(),
			tokenDefinitionParticle.getIconUrl(),
			TokenUnitConversions.subunitsToUnits(tokenDefinitionParticle.getSupply()),
			TokenUnitConversions.subunitsToUnits(tokenDefinitionParticle.getGranularity()),
			TokenSupplyType.FIXED
		);
	}
}
