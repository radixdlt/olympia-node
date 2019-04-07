package com.radixdlt.client.application.translate.tokens;

import com.google.common.collect.ImmutableMap;
import com.radixdlt.client.application.translate.ApplicationState;
import com.radixdlt.client.application.translate.tokens.TokenState.TokenSupplyType;

import com.radixdlt.client.atommodel.tokens.UnallocatedTokensParticle;
import java.math.BigDecimal;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;
import org.radix.common.ID.EUID;
import org.radix.utils.UInt256;

public class TokenDefinitionsState implements ApplicationState {
	private final ImmutableMap<TokenDefinitionReference, TokenState> state;

	private TokenDefinitionsState(Map<TokenDefinitionReference, TokenState> state) {
		this.state = ImmutableMap.copyOf(state);
	}

	public static TokenDefinitionsState init() {
		return new TokenDefinitionsState(Collections.emptyMap());
	}

	public Map<TokenDefinitionReference, TokenState> getState() {
		return state;
	}

	public TokenDefinitionsState mergeUnallocated(UnallocatedTokensParticle unallocatedTokensParticle, boolean add) {
		TokenDefinitionReference ref = unallocatedTokensParticle.getTokenDefinitionReference();
		if (!state.containsKey(ref)) {
			throw new IllegalStateException("TokenParticle should have been created");
		}
		Map<TokenDefinitionReference, TokenState> newState = new HashMap<>(state);
		final TokenState tokenState = state.get(ref);
		Map<EUID, UnallocatedTokensParticle> newTokenUnallocated = new HashMap<>(tokenState.getUnallocatedTokens());
		if (add) {
			newTokenUnallocated.put(unallocatedTokensParticle.getHid(), unallocatedTokensParticle);
		} else {
			newTokenUnallocated.remove(unallocatedTokensParticle.getHid());
		}

		UInt256 unallocatedAmount = newTokenUnallocated.entrySet().stream()
			.map(Entry::getValue)
			.map(UnallocatedTokensParticle::getAmount)
			.reduce(UInt256.ZERO, UInt256::add);

		newState.put(ref, new TokenState(
			tokenState.getName(),
			tokenState.getIso(),
			tokenState.getDescription(),
			TokenUnitConversions.subunitsToUnits(UInt256.MAX_VALUE.subtract(unallocatedAmount)),
			tokenState.getGranularity(),
			tokenState.getTokenSupplyType(),
			newTokenUnallocated
		));

		return new TokenDefinitionsState(newState);
	}

	public TokenDefinitionsState mergeTokenClass(
		TokenDefinitionReference ref,
		String name,
		String iso,
		String description,
		BigDecimal granularity,
		TokenSupplyType tokenSupplyType,
		boolean add
	) {
		Map<TokenDefinitionReference, TokenState> newState = new HashMap<>(state);
		if (add) {
			if (newState.containsKey(ref)) {
				final TokenState curState = newState.get(ref);
				final BigDecimal totalSupply = curState.getTotalSupply();
				final Map<EUID, UnallocatedTokensParticle> unallocatedTokens = curState.getUnallocatedTokens();

				newState.put(ref, new TokenState(name, iso, description, totalSupply, granularity, tokenSupplyType, unallocatedTokens));
			} else {
				newState.put(ref, new TokenState(name, iso, description, BigDecimal.ZERO, granularity, tokenSupplyType, Collections.emptyMap()));
			}
		} else {
			final TokenState curState = newState.get(ref);
			newState.put(ref, new TokenState(null, ref.getSymbol(), null, curState.getTotalSupply(), null, null, curState.getUnallocatedTokens()));
		}

		return new TokenDefinitionsState(newState);
	}

	public TokenDefinitionsState mergeSupplyChange(TokenDefinitionReference ref, BigDecimal supplyChange) {
		Map<TokenDefinitionReference, TokenState> newState = new HashMap<>(state);
		if (newState.containsKey(ref)) {
			TokenState tokenState = newState.get(ref);
			newState.put(ref, new TokenState(
				tokenState.getName(),
				tokenState.getIso(),
				tokenState.getDescription(),
				tokenState.getTotalSupply() != null ? tokenState.getTotalSupply().add(supplyChange) : supplyChange,
				tokenState.getGranularity(),
				tokenState.getTokenSupplyType(),
				tokenState.getUnallocatedTokens()));

			if (newState.get(ref).getTotalSupply().compareTo(BigDecimal.ZERO) < 0) {
				System.out.println("OOOPS 2");
			}
		} else {
			if (supplyChange.compareTo(BigDecimal.ZERO) < 0) {
				System.out.println("OOOPS");
			}
			newState.put(ref, new TokenState(null, ref.getSymbol(), null, supplyChange, null, null, null));
		}

		return new TokenDefinitionsState(newState);
	}

	@Override
	public int hashCode() {
		return state.hashCode();
	}

	@Override
	public boolean equals(Object o) {
		if (!(o instanceof TokenDefinitionsState)) {
			return false;
		}

		TokenDefinitionsState t = (TokenDefinitionsState) o;
		return t.state.equals(this.state);
	}

	@Override
	public String toString() {
		return state.toString();
	}
}
