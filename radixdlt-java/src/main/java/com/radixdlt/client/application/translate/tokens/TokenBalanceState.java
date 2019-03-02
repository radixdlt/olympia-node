package com.radixdlt.client.application.translate.tokens;

import com.radixdlt.client.application.translate.ApplicationState;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;
import java.util.stream.Stream;

import org.radix.utils.UInt256s;

import com.radixdlt.client.atommodel.FungibleType;
import com.radixdlt.client.atommodel.tokens.OwnedTokensParticle;
import com.radixdlt.client.core.atoms.RadixHash;
import com.radixdlt.client.core.atoms.particles.Spin;
import com.radixdlt.client.core.atoms.particles.SpunParticle;

/**
 * All the token balances at an address at a given point in time.
 */
public class TokenBalanceState implements ApplicationState {
	public static class Balance {
		private final BigInteger balance;
		private final BigInteger granularity;
		private final Map<RadixHash, SpunParticle<OwnedTokensParticle>> consumables;

		private Balance(BigInteger balance, BigInteger granularity, Map<RadixHash, SpunParticle<OwnedTokensParticle>> consumables) {
			this.balance = balance;
			this.granularity = granularity;
			this.consumables = consumables;
		}

		private Balance(BigInteger balance, BigInteger granularity, SpunParticle<OwnedTokensParticle> s) {
			this.balance = balance;
			this.granularity = granularity;
			this.consumables = Collections.singletonMap(RadixHash.of(s.getParticle().getDson()), s);
		}

		public static Balance empty(BigInteger granularity) {
			return new Balance(BigInteger.ZERO, granularity, Collections.emptyMap());
		}

		public BigDecimal getAmount() {
			return TokenTypeReference.subunitsToUnits(balance);
		}

		public BigDecimal getGranularity() {
			return TokenTypeReference.subunitsToUnits(granularity);
		}

		public Stream<OwnedTokensParticle> unconsumedTransferrable() {
			return consumables.entrySet().stream()
				.map(Entry::getValue)
				.filter(c -> c.getSpin() == Spin.UP)
				.filter(c -> c.getParticle().getType() != FungibleType.BURNED)
				.map(SpunParticle::getParticle);
		}

		public static Balance merge(Balance balance, SpunParticle<OwnedTokensParticle> s) {
			OwnedTokensParticle ownedTokensParticle = s.getParticle();
			Map<RadixHash, SpunParticle<OwnedTokensParticle>> newMap = new HashMap<>(balance.consumables);
			newMap.put(RadixHash.of(ownedTokensParticle.getDson()), s);

			final BigInteger amount;
			if (ownedTokensParticle.getType() == FungibleType.BURNED) {
				amount = BigInteger.ZERO;
			} else if (s.getSpin().equals(Spin.DOWN)) {
				amount = UInt256s.toBigInteger(ownedTokensParticle.getAmount()).negate();
			} else {
				amount = UInt256s.toBigInteger(ownedTokensParticle.getAmount());
			}

			BigInteger newBalance = balance.balance.add(amount);
			return new Balance(newBalance, balance.granularity, newMap);
		}
	}

	private final Map<TokenTypeReference, Balance> balance;

	public TokenBalanceState() {
		this.balance = Collections.emptyMap();
	}

	public TokenBalanceState(Map<TokenTypeReference, Balance> balance) {
		this.balance = balance;
	}

	public Map<TokenTypeReference, Balance> getBalance() {
		return Collections.unmodifiableMap(balance);
	}

	public static TokenBalanceState merge(TokenBalanceState state, SpunParticle<OwnedTokensParticle> s) {
		HashMap<TokenTypeReference, Balance> balance = new HashMap<>(state.balance);
		OwnedTokensParticle ownedTokensParticle = s.getParticle();
		BigInteger amount = UInt256s.toBigInteger(ownedTokensParticle.getAmount());
		BigInteger granularity = UInt256s.toBigInteger(ownedTokensParticle.getGranularity());
		balance.merge(
				ownedTokensParticle.getTokenTypeReference(),
				new Balance(amount, granularity, s),
				(bal1, bal2) -> Balance.merge(bal1, s)
		);

		return new TokenBalanceState(balance);
	}
}
