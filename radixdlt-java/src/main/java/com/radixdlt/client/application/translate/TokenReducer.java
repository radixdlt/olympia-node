package com.radixdlt.client.application.translate;

import com.radixdlt.client.core.atoms.TokenRef;
import com.radixdlt.client.core.atoms.particles.Consumable;
import com.radixdlt.client.core.atoms.particles.Consumable.ConsumableType;
import com.radixdlt.client.core.atoms.particles.Particle;
import com.radixdlt.client.core.atoms.particles.Spin;
import com.radixdlt.client.core.atoms.particles.TokenParticle;
import java.math.BigDecimal;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * Reduces particles at an address into concrete Tokens and their states
 */
public class TokenReducer implements ParticleReducer<Map<TokenRef, TokenState>> {
	@Override
	public Map<TokenRef, TokenState> initialState() {
		return Collections.emptyMap();
	}

	@Override
	public Map<TokenRef, TokenState> reduce(Map<TokenRef, TokenState> state, Particle p) {
		if (!(p instanceof TokenParticle
			|| (p instanceof Consumable && p.getSpin() == Spin.UP && ((Consumable) p).getType() == ConsumableType.MINTED))) {
			return state;
		}

		HashMap<TokenRef, TokenState> newMap = new HashMap<>(state);
		if (p instanceof TokenParticle) {
			TokenParticle tokenParticle = (TokenParticle) p;
			TokenState tokenState = new TokenState(
				tokenParticle.getName(),
				tokenParticle.getIso(),
				tokenParticle.getDescription(),
				BigDecimal.ZERO
			);

			newMap.merge(
				tokenParticle.getTokenRef(),
				tokenState,
				(a, b) -> new TokenState(b.getName(), b.getIso(), b.getDescription(), a.getTotalSupply())
			);
		} else {
			Consumable minted = (Consumable) p;
			TokenState tokenState = new TokenState(
				null,
				minted.getTokenRef().getIso(),
				null,
				TokenRef.subUnitsToDecimal(minted.getAmount())
			);
			newMap.merge(
				minted.getTokenRef(),
				tokenState,
				(a, b) -> new TokenState(
					a.getName(),
					a.getIso(),
					a.getDescription(),
					a.getTotalSupply().add(b.getTotalSupply())
				)
			);
		}

		return newMap;
	}
}
