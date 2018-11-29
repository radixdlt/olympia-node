package com.radixdlt.client.application.translate.tokenclasses;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import org.radix.utils.UInt256s;

import com.radixdlt.client.application.translate.ParticleReducer;
import com.radixdlt.client.application.translate.tokenclasses.TokenState.TokenSupplyType;
import com.radixdlt.client.atommodel.quarks.FungibleQuark.FungibleType;
import com.radixdlt.client.atommodel.tokens.OwnedTokensParticle;
import com.radixdlt.client.atommodel.tokens.TokenClassReference;
import com.radixdlt.client.atommodel.tokens.TokenParticle;
import com.radixdlt.client.atommodel.tokens.TokenPermission;
import com.radixdlt.client.core.atoms.particles.Particle;
import com.radixdlt.client.core.atoms.particles.Spin;
import com.radixdlt.client.core.atoms.particles.SpunParticle;

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
			TokenPermission mintPermission = tokenParticle.getTokenPermissions().get(FungibleType.MINTED);

			final TokenSupplyType tokenSupplyType;
			if (mintPermission.equals(TokenPermission.SAME_ATOM_ONLY)) {
				tokenSupplyType = TokenSupplyType.FIXED;
			} else if (mintPermission.equals(TokenPermission.TOKEN_OWNER_ONLY)) {
				tokenSupplyType = TokenSupplyType.MUTABLE;
			} else {
				throw new IllegalStateException("TokenParticle with mintPermissions of " + mintPermission + " not supported.");
			}

			TokenState tokenState = new TokenState(
				tokenParticle.getName(),
				tokenParticle.getSymbol(),
				tokenParticle.getDescription(),
				BigDecimal.ZERO,
				tokenSupplyType
			);

			newMap.merge(
				tokenParticle.getTokenClassReference(),
				tokenState,
				(a, b) -> new TokenState(b.getName(), b.getIso(), b.getDescription(), a.getTotalSupply(), b.getTokenSupplyType())
			);
		} else {
			OwnedTokensParticle mintedOrBurned = (OwnedTokensParticle) p;
			BigInteger mintedOrBurnedAmount = UInt256s.toBigInteger(mintedOrBurned.getAmount());

			TokenState tokenState = new TokenState(
				null,
				mintedOrBurned.getTokenClassReference().getSymbol(),
				null,
				TokenClassReference.subunitsToUnits(
					(mintedOrBurned.getType() == FungibleType.BURNED) ? mintedOrBurnedAmount.negate() : mintedOrBurnedAmount
				),
				null
			);
			newMap.merge(
				mintedOrBurned.getTokenClassReference(),
				tokenState,
				(a, b) -> new TokenState(
					a.getName(),
					a.getIso(),
					a.getDescription(),
					a.getTotalSupply().add(b.getTotalSupply()),
					a.getTokenSupplyType()
				)
			);
		}

		return newMap;
	}
}
