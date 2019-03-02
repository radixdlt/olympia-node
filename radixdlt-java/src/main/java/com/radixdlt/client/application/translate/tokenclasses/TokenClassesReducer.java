package com.radixdlt.client.application.translate.tokenclasses;

import java.math.BigDecimal;
import java.math.BigInteger;

import org.radix.utils.UInt256s;

import com.radixdlt.client.application.translate.ParticleReducer;
import com.radixdlt.client.application.translate.tokenclasses.TokenState.TokenSupplyType;
import com.radixdlt.client.atommodel.FungibleType;
import com.radixdlt.client.atommodel.tokens.OwnedTokensParticle;
import com.radixdlt.client.application.translate.tokens.TokenTypeReference;
import com.radixdlt.client.atommodel.tokens.TokenParticle;
import com.radixdlt.client.atommodel.tokens.TokenPermission;
import com.radixdlt.client.core.atoms.particles.Particle;
import com.radixdlt.client.core.atoms.particles.Spin;
import com.radixdlt.client.core.atoms.particles.SpunParticle;

/**
 * Reduces particles at an address into concrete Tokens and their states
 */
public class TokenClassesReducer implements ParticleReducer<TokenClassesState> {

	@Override
	public Class<TokenClassesState> stateClass() {
		return TokenClassesState.class;
	}

	@Override
	public TokenClassesState initialState() {
		return TokenClassesState.init();
	}

	@Override
	public TokenClassesState reduce(TokenClassesState state, SpunParticle s) {
		Particle p = s.getParticle();
		if (!(p instanceof TokenParticle
			|| (p instanceof OwnedTokensParticle && s.getSpin() == Spin.UP
				&& ((OwnedTokensParticle) p).getType() != FungibleType.TRANSFERRED))) {
			return state;
		}

		if (p instanceof TokenParticle) {
			TokenParticle tokenParticle = (TokenParticle) p;
			TokenPermission mintPermission = tokenParticle.getTokenPermissions().get(FungibleType.MINTED);

			final TokenSupplyType tokenSupplyType;
			if (mintPermission.equals(TokenPermission.SAME_ATOM_ONLY)) {
				tokenSupplyType = TokenSupplyType.FIXED;
			} else if (mintPermission.equals(TokenPermission.TOKEN_OWNER_ONLY)) {
				tokenSupplyType = TokenSupplyType.MUTABLE;
			} else if (mintPermission.equals(TokenPermission.POW) && tokenParticle.getSymbol().equals("POW")) {
				tokenSupplyType = TokenSupplyType.MUTABLE;
			} else if (mintPermission.equals(TokenPermission.GENESIS_ONLY)) {
				tokenSupplyType = TokenSupplyType.FIXED;
			} else {
				throw new IllegalStateException("TokenParticle with mintPermissions of " + mintPermission + " not supported.");
			}

			return state.mergeTokenClass(
				tokenParticle.getTokenClassReference(),
				tokenParticle.getName(),
				tokenParticle.getSymbol(),
				tokenParticle.getDescription(),
				TokenTypeReference.subunitsToUnits(tokenParticle.getGranularity()),
				tokenSupplyType
			);
		} else {
			OwnedTokensParticle mintedOrBurned = (OwnedTokensParticle) p;
			BigInteger mintedOrBurnedAmount = UInt256s.toBigInteger(mintedOrBurned.getAmount());
			BigDecimal change = TokenTypeReference.subunitsToUnits(
				(mintedOrBurned.getType() == FungibleType.BURNED)
					? mintedOrBurnedAmount.negate()
					: mintedOrBurnedAmount
			);

			return state.mergeSupplyChange(mintedOrBurned.getTokenClassReference(), change);
		}
	}
}
