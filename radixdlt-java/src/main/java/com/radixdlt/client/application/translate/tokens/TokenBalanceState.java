package com.radixdlt.client.application.translate.tokens;

import com.radixdlt.client.application.translate.ApplicationState;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;
import java.util.stream.Stream;

import com.radixdlt.client.atommodel.tokens.BurnedTokensParticle;
import com.radixdlt.client.atommodel.tokens.ConsumableTokens;
import com.radixdlt.client.core.atoms.particles.Particle;
import org.radix.utils.UInt256s;

import com.radixdlt.client.atommodel.FungibleType;
import com.radixdlt.client.core.atoms.RadixHash;
import com.radixdlt.client.core.atoms.particles.Spin;

/**
 * All the token balances at an address at a given point in time.
 */
public class TokenBalanceState implements ApplicationState {
	public static class Balance {
		private final BigInteger balance;
		private final BigInteger granularity;
		private final Map<RadixHash, ConsumableTokens> consumables;

		private Balance(BigInteger balance, BigInteger granularity, Map<RadixHash, ConsumableTokens> consumables) {
			this.balance = balance;
			this.granularity = granularity;
			this.consumables = consumables;
		}

		private Balance(BigInteger balance, BigInteger granularity, ConsumableTokens s) {
			this(balance, granularity, Collections.singletonMap(((Particle) s).getHash(), s));
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

		public Stream<ConsumableTokens> unconsumedTransferrable() {
			return consumables.entrySet().stream()
				.map(Entry::getValue);
		}

		public static Balance merge(Balance balance, ConsumableTokens tokens, Spin spin) {
			Map<RadixHash, ConsumableTokens> newMap = new HashMap<>(balance.consumables);
			newMap.put(((Particle) tokens).getHash(), tokens);

			final BigInteger amount;
			if (tokens instanceof BurnedTokensParticle) {
				amount = BigInteger.ZERO;
			} else if (spin == Spin.DOWN) {
				amount = UInt256s.toBigInteger(tokens.getAmount()).negate();
			} else {
				amount = UInt256s.toBigInteger(tokens.getAmount());
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

	public static TokenBalanceState merge(TokenBalanceState state, ConsumableTokens tokens, Spin spin) {
		HashMap<TokenTypeReference, Balance> balance = new HashMap<>(state.balance);
		BigInteger amount = UInt256s.toBigInteger(tokens.getAmount());
		BigInteger granularity = UInt256s.toBigInteger(tokens.getGranularity());
		balance.merge(
				tokens.getTokenTypeReference(),
				new Balance(amount, granularity, tokens),
				(bal1, bal2) -> Balance.merge(bal1, tokens, spin)
		);

		return new TokenBalanceState(balance);
	}
}
