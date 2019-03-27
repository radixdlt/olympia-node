package com.radixdlt.client.application.translate.tokens;

import com.google.common.collect.ImmutableMap;
import com.radixdlt.client.atommodel.tokens.UnallocatedTokensParticle;
import java.math.BigDecimal;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Objects;
import org.radix.common.ID.EUID;
import org.radix.utils.UInt256;

/**
 * The state and data of a token at a given moment in time
 */
public class TokenState {
	public enum TokenSupplyType {
		FIXED,
		MUTABLE
	}

	private final String name;
	private final String iso;
	private final String description;
	private final BigDecimal totalSupply;
	private final BigDecimal granularity;
	private final TokenSupplyType tokenSupplyType;
	private final Map<EUID, UnallocatedTokensParticle> unallocatedTokens;

	public TokenState(
		String name,
		String iso,
		String description,
		BigDecimal totalSupply,
		BigDecimal granularity,
		TokenSupplyType tokenSupplyType,
		Map<EUID, UnallocatedTokensParticle> unallocatedTokens
	) {
		this.name = name;
		this.iso = iso;
		this.description = description;
		this.totalSupply = totalSupply;
		this.granularity = granularity;
		this.tokenSupplyType = tokenSupplyType;
		this.unallocatedTokens = ImmutableMap.copyOf(unallocatedTokens);
	}

	public String getName() {
		return name;
	}

	public String getIso() {
		return iso;
	}

	public String getDescription() {
		return description;
	}

	public BigDecimal getTotalSupply() {
		return totalSupply;
	}

	public BigDecimal getGranularity() {
		return this.granularity;
	}

	public TokenSupplyType getTokenSupplyType() {
		return tokenSupplyType;
	}

	public BigDecimal getMaxSupply() {
		return totalSupply;
	}

	public Map<EUID, UnallocatedTokensParticle> getUnallocatedTokens() {
		return unallocatedTokens;
	}

	public BigDecimal getUnallocatedSupply() {
		return TokenUnitConversions.subunitsToUnits(
			unallocatedTokens.entrySet().stream()
				.map(Entry::getValue)
				.map(UnallocatedTokensParticle::getAmount)
				.reduce(UInt256.ZERO, UInt256::add)
		);
	}

	@Override
	public int hashCode() {
		return Objects.hash(name, iso, description, tokenSupplyType, totalSupply);
	}

	@Override
	public boolean equals(Object o) {
		if (!(o instanceof TokenState)) {
			return false;
		}

		TokenState tokenState = (TokenState) o;
		return Objects.equals(this.name, tokenState.name)
			&& Objects.equals(this.iso, tokenState.iso)
			&& Objects.equals(this.tokenSupplyType, tokenState.tokenSupplyType)
			&& Objects.equals(this.description, tokenState.description)
			// Note BigDecimal.equal does not return true for different scales
			&& this.granularity.compareTo(tokenState.granularity) == 0
			&& this.totalSupply.compareTo(tokenState.totalSupply) == 0
			&& Objects.equals(this.unallocatedTokens, tokenState.unallocatedTokens);
	}

	@Override
	public String toString() {
		return String.format("Token(%s) name(%s) description(%s) totalSupply(%s) granularity(%s) maxSupply(%s)",
				this.iso, this.name, this.description, this.totalSupply, this.granularity, this.totalSupply);
	}
}
