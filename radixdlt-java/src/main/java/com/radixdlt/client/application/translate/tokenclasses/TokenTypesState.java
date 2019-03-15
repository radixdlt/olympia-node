package com.radixdlt.client.application.translate.tokenclasses;

import com.radixdlt.client.application.translate.ApplicationState;
import com.radixdlt.client.application.translate.tokenclasses.TokenState.TokenSupplyType;
import com.radixdlt.client.application.translate.tokens.TokenDefinitionReference;
import java.math.BigDecimal;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

public class TokenTypesState implements ApplicationState {
	private final Map<TokenDefinitionReference, TokenState> state;

	private TokenTypesState(Map<TokenDefinitionReference, TokenState> state) {
		this.state = Collections.unmodifiableMap(state);
	}

	public static TokenTypesState init() {
		return new TokenTypesState(Collections.emptyMap());
	}

	public Map<TokenDefinitionReference, TokenState> getState() {
		return state;
	}

	public TokenTypesState mergeTokenClass(
		TokenDefinitionReference ref,
		String name,
		String iso,
		String description,
		BigDecimal granularity,
		TokenSupplyType tokenSupplyType
	) {
		Map<TokenDefinitionReference, TokenState> newState = new HashMap<>(state);
		if (newState.containsKey(ref)) {
			BigDecimal totalSupply = newState.get(ref).getTotalSupply();
			newState.put(ref, new TokenState(name, iso, description, totalSupply, granularity, tokenSupplyType));
		} else {
			newState.put(ref, new TokenState(name, iso, description, BigDecimal.ZERO, granularity, tokenSupplyType));
		}

		return new TokenTypesState(newState);
	}

	public TokenTypesState mergeSupplyChange(TokenDefinitionReference ref, BigDecimal supplyChange) {
		Map<TokenDefinitionReference, TokenState> newState = new HashMap<>(state);
		if (newState.containsKey(ref)) {
			TokenState tokenState = newState.get(ref);
			newState.put(ref, new TokenState(
				tokenState.getName(),
				tokenState.getIso(),
				tokenState.getDescription(),
				tokenState.getTotalSupply() != null ? tokenState.getTotalSupply().add(supplyChange) : supplyChange,
				tokenState.getGranularity(),
				tokenState.getTokenSupplyType()));
		} else {
			newState.put(ref, new TokenState(null, ref.getSymbol(), null, null, supplyChange, null));
		}

		return new TokenTypesState(newState);
	}
}
