package com.radixdlt.client.application.translate.tokenclasses;

import com.radixdlt.client.application.translate.ParticleReducer;
import com.radixdlt.client.atommodel.quarks.FungibleQuark.FungibleType;
import com.radixdlt.client.atommodel.tokens.TokenClassReference;
import com.radixdlt.client.core.atoms.particles.Particle;
import com.radixdlt.client.core.atoms.particles.Spin;
import com.radixdlt.client.core.atoms.particles.SpunParticle;
import com.radixdlt.client.atommodel.tokens.TokenParticle;
import com.radixdlt.client.atommodel.tokens.OwnedTokensParticle;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * Reduces particles at an address into concrete Tokens and their states
 */
public class TokenReducer implements ParticleReducer<Map<TokenClassReference, TokenState>> {
	@Override
	public Map<TokenClassReference, TokenState> initialState() {
		return Collections.emptyMap();
	}

	@Override
	public Map<TokenClassReference, TokenState> reduce(Map<TokenClassReference, TokenState> state, SpunParticle s) {
		Particle p = s.getParticle();
		if (!(p instanceof TokenParticle
			|| (p instanceof OwnedTokensParticle && s.getSpin() == Spin.UP
				&& ((OwnedTokensParticle) p).getType() != FungibleType.TRANSFERRED))) {
			return state;
		}

		HashMap<TokenClassReference, TokenState> newMap = new HashMap<>(state);
		if (p instanceof TokenParticle) {
			TokenParticle tokenParticle = (TokenParticle) p;
			TokenState tokenState = new TokenState(
				tokenParticle.getName(),
				tokenParticle.getSymbol(),
				tokenParticle.getDescription(),
				BigDecimal.ZERO
			);

			newMap.merge(
				tokenParticle.getTokenClassReference(),
				tokenState,
				(a, b) -> new TokenState(b.getName(), b.getIso(), b.getDescription(), a.getTotalSupply())
			);
		} else {
			OwnedTokensParticle mintedOrBurned = (OwnedTokensParticle) p;

			TokenState tokenState = new TokenState(
				null,
				mintedOrBurned.getTokenClassReference().getSymbol(),
				null,
				TokenClassReference.subUnitsToDecimal(
					(mintedOrBurned.getType() == FungibleType.BURNED ? -1 : 1) * mintedOrBurned.getAmount()
				)
			);
			newMap.merge(
				mintedOrBurned.getTokenClassReference(),
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
