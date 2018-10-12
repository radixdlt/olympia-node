package com.radixdlt.client.application.translate;

import com.radixdlt.client.core.atoms.particles.AtomFeeConsumable;
import com.radixdlt.client.core.atoms.particles.Consumable;
import com.radixdlt.client.core.atoms.particles.Particle;

public class TokenBalanceReducer implements ParticleReducer<TokenBalanceState> {
	@Override
	public TokenBalanceState initialState() {
		return new TokenBalanceState();
	}

	@Override
	public TokenBalanceState reduce(TokenBalanceState state, Particle p) {
		if (!(p instanceof Consumable) || p instanceof AtomFeeConsumable) {
			return state;
		}

		return TokenBalanceState.merge(state, (Consumable) p);
	}
}
