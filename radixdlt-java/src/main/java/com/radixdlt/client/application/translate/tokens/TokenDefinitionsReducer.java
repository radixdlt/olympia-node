package com.radixdlt.client.application.translate.tokens;

import com.radixdlt.client.atommodel.tokens.VariableTokenDefinitionParticle.TokenTransition;
import com.radixdlt.client.atommodel.tokens.UnallocatedTokensParticle;

import com.radixdlt.client.atommodel.tokens.VariableTokenDefinitionParticle;

import com.radixdlt.client.application.translate.ParticleReducer;
import com.radixdlt.client.application.translate.tokens.TokenState.TokenSupplyType;
import com.radixdlt.client.atommodel.tokens.FixedTokenDefinitionParticle;
import com.radixdlt.client.atommodel.tokens.TokenPermission;
import com.radixdlt.client.core.atoms.particles.Particle;

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
	public TokenDefinitionsState reduce(TokenDefinitionsState state, Particle p) {
		if (p instanceof VariableTokenDefinitionParticle) {
			return reduceInternal(state, (VariableTokenDefinitionParticle) p);
		} else if (p instanceof FixedTokenDefinitionParticle) {
			return reduceInternal(state, (FixedTokenDefinitionParticle) p);
		} else  if (p instanceof UnallocatedTokensParticle) {
			UnallocatedTokensParticle u = (UnallocatedTokensParticle) p;
			return state.mergeUnallocated(u);
		}

		return state;
	}

	@Override
	public TokenDefinitionsState combine(TokenDefinitionsState state0, TokenDefinitionsState state1) {
		return TokenDefinitionsState.combine(state0, state1);
	}

	private TokenDefinitionsState reduceInternal(TokenDefinitionsState state, VariableTokenDefinitionParticle tokenDefinitionParticle) {
		TokenPermission mintPermission = tokenDefinitionParticle.getTokenPermissions().get(TokenTransition.MINT);

		if (!mintPermission.equals(TokenPermission.TOKEN_OWNER_ONLY) && !mintPermission.equals(TokenPermission.ALL)) {
			throw new IllegalStateException(
				"TokenDefinitionParticle with mintPermissions of " + mintPermission + " not supported.");
		}

		return state.mergeTokenClass(
			tokenDefinitionParticle.getRRI(),
			tokenDefinitionParticle.getName(),
			tokenDefinitionParticle.getSymbol(),
			tokenDefinitionParticle.getDescription(),
			tokenDefinitionParticle.getIconUrl(),
			null,
			TokenUnitConversions.subunitsToUnits(tokenDefinitionParticle.getGranularity()),
			TokenSupplyType.MUTABLE
		);
	}

	private TokenDefinitionsState reduceInternal(TokenDefinitionsState state, FixedTokenDefinitionParticle tokenDefinitionParticle) {
		return state.mergeTokenClass(
			tokenDefinitionParticle.getRRI(),
			tokenDefinitionParticle.getName(),
			tokenDefinitionParticle.getSymbol(),
			tokenDefinitionParticle.getDescription(),
			tokenDefinitionParticle.getIconUrl(),
			TokenUnitConversions.subunitsToUnits(tokenDefinitionParticle.getSupply()),
			TokenUnitConversions.subunitsToUnits(tokenDefinitionParticle.getGranularity()),
			TokenSupplyType.FIXED
		);
	}
}
