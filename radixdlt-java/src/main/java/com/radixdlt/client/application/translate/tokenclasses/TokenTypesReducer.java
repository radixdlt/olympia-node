package com.radixdlt.client.application.translate.tokenclasses;

import java.math.BigDecimal;
import java.math.BigInteger;

import org.radix.utils.UInt256s;

import com.radixdlt.client.application.translate.ParticleReducer;
import com.radixdlt.client.application.translate.tokenclasses.TokenState.TokenSupplyType;
import com.radixdlt.client.application.translate.tokens.TokenTypeReference;
import com.radixdlt.client.atommodel.Fungible;
import com.radixdlt.client.atommodel.tokens.BurnedTokensParticle;
import com.radixdlt.client.atommodel.tokens.MintedTokensParticle;
import com.radixdlt.client.atommodel.tokens.TokenParticle;
import com.radixdlt.client.atommodel.tokens.TokenPermission;
import com.radixdlt.client.core.atoms.particles.Particle;
import com.radixdlt.client.core.atoms.particles.Spin;

import com.radixdlt.client.core.ledger.TransitionedParticle;

/**
 * Reduces particles at an address into concrete Tokens and their states
 */
public class TokenTypesReducer implements ParticleReducer<TokenTypesState> {

	@Override
	public Class<TokenTypesState> stateClass() {
		return TokenTypesState.class;
	}

	@Override
	public TokenTypesState initialState() {
		return TokenTypesState.init();
	}

	@Override
	public TokenTypesState reduce(TokenTypesState state, TransitionedParticle t) {
		Particle p = t.getParticle();

		if (p instanceof TokenParticle) {
			TokenParticle tokenParticle = (TokenParticle) p;
			TokenPermission mintPermission = tokenParticle.getTokenPermissions().get(MintedTokensParticle.class);

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
				tokenParticle.getTokenTypeReference(),
				tokenParticle.getName(),
				tokenParticle.getSymbol(),
				tokenParticle.getDescription(),
				TokenTypeReference.subunitsToUnits(tokenParticle.getGranularity()),
				tokenSupplyType
			);
		} else if (t.getSpinTo() == Spin.UP && (p instanceof MintedTokensParticle || p instanceof BurnedTokensParticle)) {
			BigInteger mintedOrBurnedAmount = UInt256s.toBigInteger(((Fungible) p).getAmount());
			BigDecimal change = TokenTypeReference.subunitsToUnits(
				(p instanceof BurnedTokensParticle)
					? mintedOrBurnedAmount.negate()
					: mintedOrBurnedAmount
			);

			TokenTypeReference tokenTypeReference = p instanceof MintedTokensParticle
				? ((MintedTokensParticle) p).getTokenTypeReference() : ((BurnedTokensParticle) p).getTokenTypeReference();
			return state.mergeSupplyChange(tokenTypeReference, change);
		}

		return state;
	}
}
