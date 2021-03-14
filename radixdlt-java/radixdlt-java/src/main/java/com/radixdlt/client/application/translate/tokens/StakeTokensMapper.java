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

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.radixdlt.application.TokenUnitConversions;
import com.radixdlt.client.application.translate.ShardedParticleStateId;
import com.radixdlt.client.application.translate.StageActionException;
import com.radixdlt.client.application.translate.StatefulActionToParticleGroupsMapper;
import com.radixdlt.client.atommodel.tokens.MutableSupplyTokenDefinitionParticle.TokenTransition;
import com.radixdlt.client.atommodel.tokens.StakedTokensParticle;
import com.radixdlt.client.atommodel.tokens.TokenPermission;
import com.radixdlt.client.atommodel.tokens.TransferrableTokensParticle;
import com.radixdlt.client.atommodel.validators.RegisteredValidatorParticle;
import com.radixdlt.client.core.atoms.ParticleGroup;
import com.radixdlt.constraintmachine.Particle;
import com.radixdlt.atom.SpunParticle;
import com.radixdlt.client.core.fungible.FungibleTransitionMapper;
import com.radixdlt.client.core.fungible.NotEnoughFungiblesException;
import com.radixdlt.identifiers.RRI;

import com.radixdlt.utils.UInt256;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Maps a stake tokens action to the particles necessary to be included in an atom.
 */
public class StakeTokensMapper implements StatefulActionToParticleGroupsMapper<StakeTokensAction> {
	public StakeTokensMapper() {
		// Empty on purpose
	}

	private static List<SpunParticle> mapToParticles(StakeTokensAction stake, List<TransferrableTokensParticle> currentParticles)
		throws NotEnoughFungiblesException {

		final UInt256 totalAmountToRedelegate = TokenUnitConversions.unitsToSubunits(stake.getAmount());
		if (currentParticles.isEmpty()) {
			throw new NotEnoughFungiblesException(totalAmountToRedelegate, UInt256.ZERO);
		}

		final RRI token = currentParticles.get(0).getTokenDefinitionReference();
		final UInt256 granularity = currentParticles.get(0).getGranularity();
		final Map<TokenTransition, TokenPermission> permissions = currentParticles.get(0).getTokenPermissions();

		FungibleTransitionMapper<TransferrableTokensParticle, StakedTokensParticle> mapper = new FungibleTransitionMapper<>(
			TransferrableTokensParticle::getAmount,
			amt ->
				new TransferrableTokensParticle(
					amt,
					granularity,
					stake.getFrom(),
					System.nanoTime(),
					token,
					permissions
				),
			amt ->
				new StakedTokensParticle(
					stake.getDelegate(),
					amt,
					granularity,
					stake.getFrom(),
					System.nanoTime(),
					token,
					permissions
				)
		);

		return mapper.mapToParticles(currentParticles, totalAmountToRedelegate);
	}

	@Override
	public Set<ShardedParticleStateId> requiredState(StakeTokensAction action) {
		return ImmutableSet.of(
			ShardedParticleStateId.of(TransferrableTokensParticle.class, action.getFrom()),
			ShardedParticleStateId.of(RegisteredValidatorParticle.class, action.getDelegate())
		);
	}

	@Override
	public List<ParticleGroup> mapToParticleGroups(StakeTokensAction stake, Stream<Particle> store) throws StageActionException {
		final RRI tokenRef = stake.getRRI();

		List<SpunParticle> particles = new ArrayList<>();
		Map<? extends Class<? extends Particle>, List<Particle>> inputParticlesByClass = store
			.collect(Collectors.groupingBy(Particle::getClass));

		RegisteredValidatorParticle delegate = inputParticlesByClass
			.getOrDefault(RegisteredValidatorParticle.class, ImmutableList.of())
			.stream()
			.map(RegisteredValidatorParticle.class::cast)
			.findFirst()
			.orElseThrow(() -> StakeNotPossibleException.notRegistered(stake.getDelegate()));
		if (!delegate.allowsDelegator(stake.getFrom())) {
			throw StakeNotPossibleException.notAllowed(delegate.getAddress(), stake.getFrom());
		}
		RegisteredValidatorParticle newDelegate = delegate.copyWithNonce(delegate.getNonce() + 1);
		particles.add(SpunParticle.down(delegate));
		particles.add(SpunParticle.up(newDelegate));

		List<TransferrableTokensParticle> stakeConsumables = inputParticlesByClass
			.getOrDefault(TransferrableTokensParticle.class, ImmutableList.of())
			.stream()
			.map(TransferrableTokensParticle.class::cast)
			.filter(p -> p.getTokenDefinitionReference().equals(tokenRef))
			.collect(Collectors.toList());

		try {
			particles.addAll(mapToParticles(stake, stakeConsumables));
		} catch (NotEnoughFungiblesException e) {
			throw new InsufficientFundsException(
				tokenRef, TokenUnitConversions.subunitsToUnits(e.getCurrent()), stake.getAmount()
			);
		}

		// TODO @Incomplete: remove debug statements
		System.out.println(stake);
		particles.forEach(sp -> System.out.println(sp.getSpin() + " " + sp.getParticle()));

		return Collections.singletonList(
			ParticleGroup.of(particles)
		);
	}
}
