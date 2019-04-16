package com.radixdlt.client.application.translate.tokens;

import com.radixdlt.client.application.translate.ParticleReducer;
import com.radixdlt.client.atommodel.tokens.TransferrableTokensParticle;
import com.radixdlt.client.core.atoms.particles.Particle;
import com.radixdlt.client.core.ledger.TransitionedParticle;
import java.util.stream.Stream;

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
	public TokenBalanceState reduce(TokenBalanceState state, Particle p) {
		if (p instanceof TransferrableTokensParticle) {
			return TokenBalanceState.merge(state, (TransferrableTokensParticle) p);
		}

		return state;
	}

	@Override
	public TokenBalanceState combine(TokenBalanceState state0, TokenBalanceState state1) {
		return TokenBalanceState.combine(state0, state1);
	}
}
