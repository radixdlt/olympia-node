package com.radixdlt.client.application.translate.tokenclasses;

import com.radixdlt.client.application.translate.ParticleReducer;
import com.radixdlt.client.application.translate.tokenclasses.TokenState.TokenSupplyType;
import com.radixdlt.client.application.translate.tokens.TokenTypeReference;
import com.radixdlt.client.atommodel.Fungible;
import com.radixdlt.client.atommodel.FungibleType;
import com.radixdlt.client.atommodel.tokens.BurnedTokensParticle;
import com.radixdlt.client.atommodel.tokens.MintedTokensParticle;
import com.radixdlt.client.atommodel.tokens.OwnedTokensParticle;
import com.radixdlt.client.atommodel.tokens.TokenParticle;
import com.radixdlt.client.atommodel.tokens.TokenPermission;
import com.radixdlt.client.core.atoms.particles.Particle;
import com.radixdlt.client.core.atoms.particles.Spin;
import com.radixdlt.client.core.atoms.particles.SpunParticle;
import org.radix.utils.UInt256s;

import java.math.BigDecimal;
import java.math.BigInteger;

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
		} else if (s.getSpin() == Spin.UP) {
			if (p instanceof MintedTokensParticle || p instanceof BurnedTokensParticle) {
				BigInteger mintedOrBurnedAmount = UInt256s.toBigInteger(((Fungible) p).getAmount());
				BigDecimal change = TokenTypeReference.subunitsToUnits(
					(p instanceof BurnedTokensParticle)
						? mintedOrBurnedAmount.negate()
						: mintedOrBurnedAmount
				);

				TokenTypeReference tokenTypeReference = p instanceof MintedTokensParticle ?
					((MintedTokensParticle) p).getTokenTypeReference() : ((BurnedTokensParticle) p).getTokenTypeReference();
				return state.mergeSupplyChange(tokenTypeReference, change);
			}
		}

		return state;
	}
}
