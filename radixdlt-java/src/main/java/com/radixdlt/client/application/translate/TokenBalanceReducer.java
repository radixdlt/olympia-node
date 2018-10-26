package com.radixdlt.client.application.translate;

import com.radixdlt.client.atommodel.tokens.FeeParticle;
import com.radixdlt.client.core.atoms.particles.SpunParticle;
import com.radixdlt.client.atommodel.tokens.TransferParticle;
import com.radixdlt.client.core.atoms.particles.Particle;

/**
 * Reduces particles at an address to it's token balances
 */
public class TokenBalanceReducer implements ParticleReducer<TokenBalanceState> {
	@Override
	public TokenBalanceState initialState() {
		return new TokenBalanceState();
	}

	@Override
	public TokenBalanceState reduce(TokenBalanceState state, SpunParticle s) {
		Particle p = s.getParticle();
		if (!(p instanceof TransferParticle) || p instanceof FeeParticle) {
			return state;
		}

		return TokenBalanceState.merge(state, (SpunParticle<TransferParticle>) s);
	}
}
