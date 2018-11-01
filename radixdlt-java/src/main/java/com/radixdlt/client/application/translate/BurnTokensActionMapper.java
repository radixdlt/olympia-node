package com.radixdlt.client.application.translate;

import com.radixdlt.client.application.actions.BurnTokensAction;
import com.radixdlt.client.application.translate.TokenBalanceState.Balance;
import com.radixdlt.client.atommodel.quarks.FungibleQuark.FungibleType;
import com.radixdlt.client.atommodel.tokens.OwnedTokensParticle;
import com.radixdlt.client.atommodel.tokens.TokenClassReference;
import com.radixdlt.client.core.RadixUniverse;
import com.radixdlt.client.core.atoms.particles.SpunParticle;
import com.radixdlt.client.core.crypto.ECKeyPair;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

public class BurnTokensActionMapper {
	private final RadixUniverse universe;

	public BurnTokensActionMapper(RadixUniverse universe) {
		this.universe = universe;
	}

	public List<SpunParticle> map(BurnTokensAction burnTokensAction, TokenBalanceState curState) throws InsufficientFundsException {
		if (burnTokensAction == null) {
			return Collections.emptyList();
		}

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

		long consumerTotal = 0;
		final long subUnitAmount = burnTokensAction.getAmount().multiply(TokenClassReference.getSubUnits()).longValueExact();
		Iterator<OwnedTokensParticle> iterator = unconsumedOwnedTokensParticles.iterator();
		Map<ECKeyPair, Long> newUpQuantities = new HashMap<>();

		// HACK for now
		// TODO: remove this, create a ConsumersCreator
		// TODO: randomize this to decrease probability of collision
		while (consumerTotal < subUnitAmount && iterator.hasNext()) {
			final long left = subUnitAmount - consumerTotal;

			OwnedTokensParticle particle = iterator.next();
			consumerTotal += particle.getAmount();

			final long amount = Math.min(left, particle.getAmount());
			particle.addConsumerQuantities(amount, null, newUpQuantities);

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
