package com.radixdlt.client.application.translate.tokens;

import com.google.common.collect.ImmutableMap;
import com.radixdlt.client.application.translate.ApplicationState;
import com.radixdlt.client.application.translate.tokens.TokenState.TokenSupplyType;

import com.radixdlt.client.atommodel.tokens.UnallocatedTokensParticle;
import com.radixdlt.client.core.atoms.particles.RRI;
import java.math.BigDecimal;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;
import org.radix.common.ID.EUID;
import org.radix.utils.UInt256;

public class TokenDefinitionsState implements ApplicationState {
	private final ImmutableMap<RRI, TokenState> state;

	private TokenDefinitionsState(Map<RRI, TokenState> state) {
		this.state = ImmutableMap.copyOf(state);
	}

	public static TokenDefinitionsState init() {
		return new TokenDefinitionsState(Collections.emptyMap());
	}

	public Map<RRI, TokenState> getState() {
		return state;
	}

	public TokenDefinitionsState mergeUnallocated(UnallocatedTokensParticle unallocatedTokensParticle, boolean add) {
		RRI ref = unallocatedTokensParticle.getTokDefRef();
		if (!state.containsKey(ref)) {
			throw new IllegalStateException("TokenParticle should have been created");
		}
		Map<RRI, TokenState> newState = new HashMap<>(state);
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
		RRI ref,
		String name,
		String iso,
		String description,
		BigDecimal granularity,
		TokenSupplyType tokenSupplyType,
		boolean add
	) {
		Map<RRI, TokenState> newState = new HashMap<>(state);
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
			newState.put(ref, new TokenState(null, ref.getName(), null, curState.getTotalSupply(), null, null, curState.getUnallocatedTokens()));
		}

		return new TokenDefinitionsState(newState);
	}

	public TokenDefinitionsState mergeSupplyChange(RRI ref, BigDecimal supplyChange) {
		Map<RRI, TokenState> newState = new HashMap<>(state);
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
		} else {
			newState.put(ref, new TokenState(null, ref.getName(), null, supplyChange, null, null, null));
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
