package com.radixdlt.client.application.translate.tokens;

import com.radixdlt.client.application.translate.ParticleReducer;
import com.radixdlt.client.atommodel.tokens.ConsumableTokens;
import com.radixdlt.client.core.atoms.particles.SpunParticle;
import com.radixdlt.client.core.atoms.particles.Particle;

/**
 * Reduces particles at an address to it's token balances
 */
public class TokenBalanceReducer implements ParticleReducer<TokenBalanceState> {

	@Override
	public Class<TokenBalanceState> stateClass() {
		return TokenBalanceState.class;
	}

	@Override
	public TokenBalanceState initialState() {
		return new TokenBalanceState();
	}

	@Override
	public TokenBalanceState reduce(TokenBalanceState state, SpunParticle s) {
		Particle particle = s.getParticle();
		if (particle instanceof ConsumableTokens) {
			return TokenBalanceState.merge(state, (ConsumableTokens) particle, s.getSpin());
		}

		return state;
	}
}
