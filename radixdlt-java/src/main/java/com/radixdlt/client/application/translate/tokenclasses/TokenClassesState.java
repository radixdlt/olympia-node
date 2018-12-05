package com.radixdlt.client.application.translate.tokenclasses;

import com.radixdlt.client.application.translate.ApplicationState;
import com.radixdlt.client.application.translate.tokenclasses.TokenState.TokenSupplyType;
import com.radixdlt.client.atommodel.tokens.TokenClassReference;
import java.math.BigDecimal;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

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
		TokenSupplyType tokenSupplyType
	) {
		Map<TokenClassReference, TokenState> newState = new HashMap<>(state);
		if (newState.containsKey(ref)) {
			TokenState tokenState = newState.get(ref);
			newState.put(ref, new TokenState(name, iso, description, tokenState.getTotalSupply(), tokenSupplyType));
		} else {
			newState.put(ref, new TokenState(name, iso, description, BigDecimal.ZERO, tokenSupplyType));
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
				tokenState.getTokenSupplyType()));
		} else {
			newState.put(ref, new TokenState(null, ref.getSymbol(), null, supplyChange, null));
		}

		return new TokenClassesState(newState);
	}
}
