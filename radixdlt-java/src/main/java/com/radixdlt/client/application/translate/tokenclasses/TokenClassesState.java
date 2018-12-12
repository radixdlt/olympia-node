package com.radixdlt.client.application.translate.tokenclasses;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import com.radixdlt.client.application.translate.ApplicationState;
import com.radixdlt.client.application.translate.tokenclasses.TokenState.TokenSupplyType;
import com.radixdlt.client.atommodel.tokens.TokenClassReference;

public class TokenClassesState implements ApplicationState {
	private final Map<TokenClassReference, TokenState> state;

	private TokenClassesState(Map<TokenClassReference, TokenState> state) {
		this.state = Collections.unmodifiableMap(state);
	}

	public static TokenClassesState init() {
		return new TokenClassesState(Collections.emptyMap());
	}

	public Map<TokenClassReference, TokenState> getState() {
		return state;
	}

	public TokenClassesState mergeTokenClass(
		TokenClassReference ref,
		String name,
		String iso,
		String description,
		BigDecimal granularity,
		TokenSupplyType tokenSupplyType
	) {
		Map<TokenClassReference, TokenState> newState = new HashMap<>(state);
		if (newState.containsKey(ref)) {
			BigDecimal totalSupply = newState.get(ref).getTotalSupply();
			newState.put(ref, new TokenState(name, iso, description, totalSupply, granularity, tokenSupplyType));
		} else {
			newState.put(ref, new TokenState(name, iso, description, BigDecimal.ZERO, granularity, tokenSupplyType));
		}

		return new TokenClassesState(newState);
	}

	public TokenClassesState mergeSupplyChange(TokenClassReference ref, BigDecimal supplyChange) {
		Map<TokenClassReference, TokenState> newState = new HashMap<>(state);
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

		return new TokenClassesState(newState);
	}
}
