package com.radixdlt.client.application.translate.tokens;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;
import java.util.stream.Stream;

import org.radix.utils.UInt256s;

import com.radixdlt.client.atommodel.quarks.FungibleQuark.FungibleType;
import com.radixdlt.client.atommodel.tokens.OwnedTokensParticle;
import com.radixdlt.client.atommodel.tokens.TokenClassReference;
import com.radixdlt.client.core.atoms.RadixHash;
import com.radixdlt.client.core.atoms.particles.Spin;
import com.radixdlt.client.core.atoms.particles.SpunParticle;

/**
 * All the token balances at an address at a given point in time.
 */
public class TokenBalanceState {
	public static class Balance {
		private final BigInteger balance;
		private final Map<RadixHash, SpunParticle<OwnedTokensParticle>> consumables;

		private Balance(BigInteger balance, Map<RadixHash, SpunParticle<OwnedTokensParticle>> consumables) {
			this.balance = balance;
			this.consumables = consumables;
		}

		private Balance(BigInteger balance, SpunParticle<OwnedTokensParticle> s) {
			this.balance = balance;
			this.consumables = Collections.singletonMap(RadixHash.of(s.getParticle().getDson()), s);
		}

		public static Balance empty() {
			return new Balance(BigInteger.ZERO, Collections.emptyMap());
		}

		public BigDecimal getAmount() {
			return TokenClassReference.subunitsToUnits(balance);
		}

		public Stream<OwnedTokensParticle> unconsumedConsumables() {
			return consumables.entrySet().stream()
				.map(Entry::getValue)
				.filter(c -> c.getSpin() == Spin.UP)
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
			return new Balance(newBalance, newMap);
		}
	}

	private final Map<TokenClassReference, Balance> balance;

	public TokenBalanceState() {
		this.balance = Collections.emptyMap();
	}

	public TokenBalanceState(Map<TokenClassReference, Balance> balance) {
		this.balance = balance;
	}

	public Map<TokenClassReference, Balance> getBalance() {
		return Collections.unmodifiableMap(balance);
	}

	public static TokenBalanceState merge(TokenBalanceState state, SpunParticle<OwnedTokensParticle> s) {
		HashMap<TokenClassReference, Balance> balance = new HashMap<>(state.balance);
		OwnedTokensParticle ownedTokensParticle = s.getParticle();
		balance.merge(
				ownedTokensParticle.getTokenClassReference(),
				new Balance(UInt256s.toBigInteger(ownedTokensParticle.getAmount()), s),
				(bal1, bal2) -> Balance.merge(bal1, s)
		);

		return new TokenBalanceState(balance);
	}
}
