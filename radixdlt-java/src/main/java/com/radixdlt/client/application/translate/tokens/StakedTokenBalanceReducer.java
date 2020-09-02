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

import com.radixdlt.client.application.translate.ParticleReducer;
import com.radixdlt.client.atommodel.tokens.StakedTokensParticle;
import com.radixdlt.client.core.atoms.particles.Particle;

/**
 * Reduces particles at an address to its stake balance
 */
public class StakedTokenBalanceReducer implements ParticleReducer<StakedTokenBalanceState> {

	@Override
	public Class<StakedTokenBalanceState> stateClass() {
		return StakedTokenBalanceState.class;
	}

	@Override
	public StakedTokenBalanceState initialState() {
		return new StakedTokenBalanceState();
	}

	@Override
	public StakedTokenBalanceState reduce(StakedTokenBalanceState state, Particle p) {
		if (p instanceof StakedTokensParticle) {
			return StakedTokenBalanceState.merge(state, (StakedTokensParticle) p);
		}

		return state;
	}

	@Override
	public StakedTokenBalanceState combine(StakedTokenBalanceState state0, StakedTokenBalanceState state1) {
		return StakedTokenBalanceState.combine(state0, state1);
	}
}
