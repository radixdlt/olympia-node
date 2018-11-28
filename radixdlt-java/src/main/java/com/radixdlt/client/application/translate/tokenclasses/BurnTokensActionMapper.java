package com.radixdlt.client.application.translate.tokenclasses;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.radix.utils.UInt256;
import org.radix.utils.UInt256s;

import com.radixdlt.client.application.translate.Action;
import com.radixdlt.client.application.translate.ActionToParticlesMapper;
import com.radixdlt.client.application.translate.tokens.InsufficientFundsException;
import com.radixdlt.client.application.translate.tokens.TokenBalanceState;
import com.radixdlt.client.application.translate.tokens.TokenBalanceState.Balance;
import com.radixdlt.client.atommodel.accounts.RadixAddress;
import com.radixdlt.client.atommodel.quarks.FungibleQuark.FungibleType;
import com.radixdlt.client.atommodel.tokens.OwnedTokensParticle;
import com.radixdlt.client.atommodel.tokens.TokenClassReference;
import com.radixdlt.client.core.RadixUniverse;
import com.radixdlt.client.core.atoms.particles.SpunParticle;
import com.radixdlt.client.core.crypto.ECKeyPair;

import io.reactivex.Observable;

public class BurnTokensActionMapper implements ActionToParticlesMapper {
	private final RadixUniverse universe;
	private final Function<RadixAddress, Observable<TokenBalanceState>> tokenBalanceState;

	public BurnTokensActionMapper(RadixUniverse universe, Function<RadixAddress, Observable<TokenBalanceState>> tokenBalanceState) {
		this.universe = universe;
		this.tokenBalanceState = tokenBalanceState;
	}

	@Override
	public Observable<SpunParticle> map(Action action) {
		if (!(action instanceof BurnTokensAction)) {
			return Observable.empty();
		}

		BurnTokensAction burnTokensAction = (BurnTokensAction) action;
		return tokenBalanceState.apply(burnTokensAction.getTokenClassReference().getAddress())
			.firstOrError()
			.toObservable()
			.flatMapIterable(state -> this.map(burnTokensAction, state));
	}

	private List<SpunParticle> map(BurnTokensAction burnTokensAction, TokenBalanceState curState) {
		final Map<TokenClassReference, Balance> allConsumables = curState.getBalance();

		final TokenClassReference tokenRef = burnTokensAction.getTokenClassReference();
		final Balance balance =
			Optional.ofNullable(allConsumables.get(burnTokensAction.getTokenClassReference())).orElse(Balance.empty());
		if (balance.getAmount().compareTo(burnTokensAction.getAmount()) < 0) {
			throw new InsufficientFundsException(
				tokenRef, balance.getAmount(), burnTokensAction.getAmount()
			);
		}

		final List<OwnedTokensParticle> unconsumedOwnedTokensParticles =
			Optional.ofNullable(allConsumables.get(burnTokensAction.getTokenClassReference()))
				.map(bal -> bal.unconsumedConsumables().collect(Collectors.toList()))
				.orElse(Collections.emptyList());

		List<SpunParticle> particles = new ArrayList<>();

		BigInteger consumerTotal = BigInteger.ZERO;
		final BigInteger subUnitAmount = burnTokensAction.getAmount().multiply(TokenClassReference.getSubUnits()).toBigIntegerExact();
		Iterator<OwnedTokensParticle> iterator = unconsumedOwnedTokensParticles.iterator();
		Map<ECKeyPair, UInt256> newUpQuantities = new HashMap<>();

		// HACK for now
		// TODO: remove this, create a ConsumersCreator
		// TODO: randomize this to decrease probability of collision
		while (consumerTotal.compareTo(subUnitAmount) < 0 && iterator.hasNext()) {
			final BigInteger left = subUnitAmount.subtract(consumerTotal);

			OwnedTokensParticle particle = iterator.next();
			BigInteger particleAmount = UInt256s.toBigInteger(particle.getAmount());
			consumerTotal = consumerTotal.add(particleAmount);

			final BigInteger amount = left.min(particleAmount);
			particle.addConsumerQuantities(UInt256s.fromBigInteger(amount), null, newUpQuantities);

			SpunParticle<OwnedTokensParticle> down = SpunParticle.down(particle);
			particles.add(down);
		}

		newUpQuantities.entrySet().stream()
			.map(e -> new OwnedTokensParticle(
				e.getValue(),
				e.getKey() == null ? FungibleType.BURNED : FungibleType.TRANSFERRED,
				e.getKey() == null ? burnTokensAction.getTokenClassReference().getAddress()
					: universe.getAddressFrom(e.getKey().getPublicKey()),
				System.nanoTime(),
				burnTokensAction.getTokenClassReference(),
				System.currentTimeMillis() / 60000L + 60000L
			))
			.map(SpunParticle::up)
			.forEach(particles::add);
		return particles;
	}
}
