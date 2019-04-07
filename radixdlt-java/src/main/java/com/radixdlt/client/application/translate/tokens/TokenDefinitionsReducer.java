package com.radixdlt.client.application.translate.tokens;

import com.radixdlt.client.atommodel.tokens.TokenDefinitionParticle.TokenTransition;
import com.radixdlt.client.atommodel.tokens.UnallocatedTokensParticle;
import com.radixdlt.client.core.atoms.particles.Spin;
import com.radixdlt.client.core.ledger.ParticleTransition;
import java.math.BigDecimal;
import java.math.BigInteger;

import com.radixdlt.client.atommodel.tokens.TokenDefinitionParticle;
import org.radix.utils.UInt256s;

import com.radixdlt.client.application.translate.ParticleReducer;
import com.radixdlt.client.application.translate.tokens.TokenState.TokenSupplyType;
import com.radixdlt.client.atommodel.Fungible;
import com.radixdlt.client.atommodel.tokens.BurnedTokensParticle;
import com.radixdlt.client.atommodel.tokens.MintedTokensParticle;
import com.radixdlt.client.atommodel.tokens.TokenPermission;
import com.radixdlt.client.core.atoms.particles.Particle;
import com.radixdlt.client.core.ledger.TransitionedParticle;

/**
 * Reduces particles at an address into concrete Tokens and their states
 */
public class TokenDefinitionsReducer implements ParticleReducer<TokenDefinitionsState> {

	@Override
	public Class<TokenDefinitionsState> stateClass() {
		return TokenDefinitionsState.class;
	}

	@Override
	public TokenDefinitionsState initialState() {
		return TokenDefinitionsState.init();
	}

	@Override
	public TokenDefinitionsState reduce(TokenDefinitionsState state, TransitionedParticle t) {
		Particle p = t.getParticle();

		if (p instanceof TokenDefinitionParticle) {
			TokenDefinitionParticle tokenDefinitionParticle = (TokenDefinitionParticle) p;
			TokenPermission mintPermission = tokenDefinitionParticle.getTokenPermissions().get(TokenTransition.MINT);

			final TokenSupplyType tokenSupplyType;
			if (mintPermission.equals(TokenPermission.TOKEN_CREATION_ONLY) || mintPermission.equals(TokenPermission.NONE)) {
				tokenSupplyType = TokenSupplyType.FIXED;
			} else if (mintPermission.equals(TokenPermission.TOKEN_OWNER_ONLY) || mintPermission.equals(TokenPermission.ALL)) {
				tokenSupplyType = TokenSupplyType.MUTABLE;
			} else {
				throw new IllegalStateException(
					"TokenDefinitionParticle with mintPermissions of " + mintPermission + " not supported.");
			}

			return state.mergeTokenClass(
				tokenDefinitionParticle.getTokenDefinitionReference(),
				tokenDefinitionParticle.getName(),
				tokenDefinitionParticle.getSymbol(),
				tokenDefinitionParticle.getDescription(),
				TokenUnitConversions.subunitsToUnits(tokenDefinitionParticle.getGranularity()),
				tokenSupplyType,
				t.getSpinTo() == Spin.UP
			);
		} else if (p instanceof MintedTokensParticle || p instanceof BurnedTokensParticle) {
			if (t.getTransition() == ParticleTransition.N2U) {
				BigInteger mintedOrBurnedAmount = UInt256s.toBigInteger(((Fungible) p).getAmount());
				BigDecimal change = TokenUnitConversions.subunitsToUnits(
					(p instanceof BurnedTokensParticle)
						? mintedOrBurnedAmount.negate()
						: mintedOrBurnedAmount
				);

				TokenDefinitionReference tokenDefinitionReference = p instanceof MintedTokensParticle
					? ((MintedTokensParticle) p).getTokenDefinitionReference() : ((BurnedTokensParticle) p).getTokenDefinitionReference();
				return state.mergeSupplyChange(tokenDefinitionReference, change);
			} else if (t.getTransition() == ParticleTransition.U2N) {
				BigInteger mintedOrBurnedAmount = UInt256s.toBigInteger(((Fungible) p).getAmount());
				BigDecimal change = TokenUnitConversions.subunitsToUnits(
					(p instanceof BurnedTokensParticle)
						? mintedOrBurnedAmount
						: mintedOrBurnedAmount.negate()
				);

				TokenDefinitionReference tokenDefinitionReference = p instanceof MintedTokensParticle
					? ((MintedTokensParticle) p).getTokenDefinitionReference() : ((BurnedTokensParticle) p).getTokenDefinitionReference();
				return state.mergeSupplyChange(tokenDefinitionReference, change);
			}
		} else if (p instanceof UnallocatedTokensParticle) {
			UnallocatedTokensParticle u = (UnallocatedTokensParticle) p;
			return state.mergeUnallocated(u, t.getSpinTo() == Spin.UP);
		}

		return state;
	}
}
