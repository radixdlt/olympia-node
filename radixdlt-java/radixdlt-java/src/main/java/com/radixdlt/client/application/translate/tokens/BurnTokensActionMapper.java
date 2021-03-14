/*
 * (C) Copyright 2020 Radix DLT Ltd
 *
 * Permission is hereby granted, free of charge, to any person obtaining a
 * copy of this software and associated documentation files (the “Software”),
 * to deal in the Software without restriction, including without limitation
 * the rights to use, copy, modify, merge, publish, distribute, sublicense,
 * and/or sell copies of the Software, and to permit persons to whom the
 * Software is furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be
 * included in all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED “AS IS”, WITHOUT WARRANTY OF ANY KIND, EXPRESS
 * OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL
 * THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING
 * FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER
 * DEALINGS IN THE SOFTWARE.
 */

package com.radixdlt.client.application.translate.tokens;

import com.radixdlt.application.TokenUnitConversions;
import com.radixdlt.client.application.translate.StageActionException;
import com.radixdlt.client.application.translate.ShardedParticleStateId;
import com.radixdlt.client.atommodel.tokens.MutableSupplyTokenDefinitionParticle.TokenTransition;
import com.radixdlt.client.atommodel.tokens.TokenPermission;
import com.radixdlt.client.atommodel.tokens.UnallocatedTokensParticle;
import com.radixdlt.client.core.atoms.particles.SpunParticle;
import com.radixdlt.client.core.fungible.FungibleTransitionMapper;
import com.radixdlt.identifiers.RRI;
import com.radixdlt.client.core.fungible.NotEnoughFungiblesException;
import java.math.BigDecimal;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import com.radixdlt.client.atommodel.tokens.TransferrableTokensParticle;
import com.radixdlt.client.core.atoms.ParticleGroup;
import com.radixdlt.constraintmachine.Particle;

import com.radixdlt.client.application.translate.StatefulActionToParticleGroupsMapper;
import com.radixdlt.identifiers.RadixAddress;

import java.util.stream.Stream;
import com.radixdlt.utils.UInt256;

public class BurnTokensActionMapper implements StatefulActionToParticleGroupsMapper<BurnTokensAction> {
	public BurnTokensActionMapper() {
		// Empty on purpose
	}

	private static List<SpunParticle> mapToParticles(BurnTokensAction burn, List<TransferrableTokensParticle> currentParticles)
		throws NotEnoughFungiblesException {

		final UInt256 totalAmountToBurn = TokenUnitConversions.unitsToSubunits(burn.getAmount());
		if (currentParticles.isEmpty()) {
			throw new NotEnoughFungiblesException(totalAmountToBurn, UInt256.ZERO);
		}

		final RRI token = currentParticles.get(0).getTokenDefinitionReference();
		final UInt256 granularity = currentParticles.get(0).getGranularity();
		final Map<TokenTransition, TokenPermission> permissions = currentParticles.get(0).getTokenPermissions();

		FungibleTransitionMapper<TransferrableTokensParticle, UnallocatedTokensParticle> mapper = new FungibleTransitionMapper<>(
			TransferrableTokensParticle::getAmount,
			amt ->
				new TransferrableTokensParticle(
					amt,
					granularity,
					burn.getAddress(),
					System.nanoTime(),
					token,
					permissions
				),
			amt ->
				new UnallocatedTokensParticle(
					totalAmountToBurn,
					granularity,
					System.nanoTime(),
					token,
					permissions
				)
		);

		return mapper.mapToParticles(currentParticles, totalAmountToBurn);
	}


	@Override
	public Set<ShardedParticleStateId> requiredState(BurnTokensAction burnTokensAction) {
		RadixAddress address = burnTokensAction.getAddress();
		return Collections.singleton(ShardedParticleStateId.of(TransferrableTokensParticle.class, address));
	}

	@Override
	public List<ParticleGroup> mapToParticleGroups(BurnTokensAction burnTokensAction, Stream<Particle> store) throws StageActionException {
		if (burnTokensAction.getAmount().signum() <= 0) {
			throw new IllegalArgumentException("Burn amount must be greater than 0.");
		}

		final RRI tokenRef = burnTokensAction.getRRI();
		final BigDecimal burnAmount = burnTokensAction.getAmount();

		final List<TransferrableTokensParticle> currentParticles = store.map(TransferrableTokensParticle.class::cast)
			.filter(p -> p.getTokenDefinitionReference().equals(tokenRef))
			.collect(Collectors.toList());

		final List<SpunParticle> burnParticles;
		try {
			burnParticles = mapToParticles(burnTokensAction, currentParticles);
		} catch (NotEnoughFungiblesException e) {
			throw new InsufficientFundsException(tokenRef, TokenUnitConversions.subunitsToUnits(e.getCurrent()), burnAmount);
		}

		return Collections.singletonList(
			ParticleGroup.of(burnParticles)
		);
	}
}
