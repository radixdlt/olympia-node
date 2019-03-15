package com.radixdlt.client.application.translate.tokenclasses;

import java.math.BigDecimal;
import java.math.BigInteger;

import com.radixdlt.client.atommodel.tokens.TokenDefinitionParticle;
import org.radix.utils.UInt256s;

import com.radixdlt.client.application.translate.ParticleReducer;
import com.radixdlt.client.application.translate.tokenclasses.TokenState.TokenSupplyType;
import com.radixdlt.client.application.translate.tokens.TokenDefinitionReference;
import com.radixdlt.client.atommodel.Fungible;
import com.radixdlt.client.atommodel.tokens.BurnedTokensParticle;
import com.radixdlt.client.atommodel.tokens.MintedTokensParticle;
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

		if (p instanceof TokenDefinitionParticle) {
			TokenDefinitionParticle tokenDefinitionParticle = (TokenDefinitionParticle) p;
			TokenPermission mintPermission = tokenDefinitionParticle.getTokenPermissions().get(MintedTokensParticle.class);

			final TokenSupplyType tokenSupplyType;
			if (mintPermission.equals(TokenPermission.SAME_ATOM_ONLY)) {
				tokenSupplyType = TokenSupplyType.FIXED;
			} else if (mintPermission.equals(TokenPermission.TOKEN_OWNER_ONLY)) {
				tokenSupplyType = TokenSupplyType.MUTABLE;
			} else if (mintPermission.equals(TokenPermission.POW) && tokenDefinitionParticle.getSymbol().equals("POW")) {
				tokenSupplyType = TokenSupplyType.MUTABLE;
			} else if (mintPermission.equals(TokenPermission.GENESIS_ONLY)) {
				tokenSupplyType = TokenSupplyType.FIXED;
			} else {
				throw new IllegalStateException("TokenDefinitionParticle with mintPermissions of " + mintPermission + " not supported.");
			}

			return state.mergeTokenClass(
				tokenDefinitionParticle.getTokenTypeReference(),
				tokenDefinitionParticle.getName(),
				tokenDefinitionParticle.getSymbol(),
				tokenDefinitionParticle.getDescription(),
				TokenDefinitionReference.subunitsToUnits(tokenDefinitionParticle.getGranularity()),
				tokenSupplyType
			);
		} else if (t.getSpinTo() == Spin.UP && (p instanceof MintedTokensParticle || p instanceof BurnedTokensParticle)) {
			BigInteger mintedOrBurnedAmount = UInt256s.toBigInteger(((Fungible) p).getAmount());
			BigDecimal change = TokenDefinitionReference.subunitsToUnits(
				(p instanceof BurnedTokensParticle)
					? mintedOrBurnedAmount.negate()
					: mintedOrBurnedAmount
			);

			TokenDefinitionReference tokenDefinitionReference = p instanceof MintedTokensParticle
				? ((MintedTokensParticle) p).getTokenTypeReference() : ((BurnedTokensParticle) p).getTokenTypeReference();
			return state.mergeSupplyChange(tokenDefinitionReference, change);
		}

		return state;
	}
}
