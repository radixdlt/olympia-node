package com.radixdlt.client.application.translate.tokens;

import com.radixdlt.client.application.translate.ParticleReducer;
import com.radixdlt.client.atommodel.tokens.ConsumableTokens;
import com.radixdlt.client.atommodel.tokens.TransferredTokensParticle;
import com.radixdlt.client.core.atoms.particles.Particle;
import com.radixdlt.client.core.ledger.TransitionedParticle;

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
	public TokenBalanceState reduce(TokenBalanceState state, TransitionedParticle t) {
		Particle particle = t.getParticle();
		if (particle instanceof TransferredTokensParticle) {
			return TokenBalanceState.merge(state, (TransferredTokensParticle) particle, t.getSpinTo());
		}

		return state;
	}
}
