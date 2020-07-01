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

import com.radixdlt.client.application.translate.ShardedParticleStateId;
import com.radixdlt.client.application.translate.StageActionException;
import com.radixdlt.client.application.translate.StatefulActionToParticleGroupsMapper;
import com.radixdlt.client.atommodel.tokens.StakedTokensParticle;
import com.radixdlt.client.atommodel.tokens.TransferrableTokensParticle;
import com.radixdlt.client.core.atoms.ParticleGroup;
import com.radixdlt.client.core.atoms.particles.Particle;
import com.radixdlt.client.core.atoms.particles.Spin;
import com.radixdlt.client.core.fungible.FungibleParticleTransitioner;
import com.radixdlt.client.core.fungible.FungibleParticleTransitioner.FungibleParticleTransition;
import com.radixdlt.client.core.fungible.NotEnoughFungiblesException;
import com.radixdlt.identifiers.RRI;

import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Maps a stake tokens action to the particles necessary to be included in an atom.
 */
public class StakeTokensMapper implements StatefulActionToParticleGroupsMapper<StakeTokensAction> {
	public StakeTokensMapper() {
	}

	@Override
	public Set<ShardedParticleStateId> requiredState(StakeTokensAction action) {
		return Collections.singleton(ShardedParticleStateId.of(TransferrableTokensParticle.class, action.getFrom()));
	}

	@Override
	public List<ParticleGroup> mapToParticleGroups(StakeTokensAction stake, Stream<Particle> store) throws StageActionException {
		final RRI tokenRef = stake.getRRI();

		List<TransferrableTokensParticle> tokenConsumables = store
			.map(TransferrableTokensParticle.class::cast)
			.filter(p -> p.getTokenDefinitionReference().equals(tokenRef))
			.collect(Collectors.toList());

		final FungibleParticleTransitioner<TransferrableTokensParticle, StakedTokensParticle> transitioner =
			new FungibleParticleTransitioner<>(
				(amt, consumable) -> new StakedTokensParticle(
					stake.getDelegate(),
					amt,
					consumable.getGranularity(),
					stake.getFrom(),
					System.nanoTime(),
					consumable.getTokenDefinitionReference(),
					System.currentTimeMillis() / 60000L + 60000L,
					consumable.getTokenPermissions()
				),
				stakedTokens -> stakedTokens,
				(amt, consumable) -> new TransferrableTokensParticle(
					amt,
					consumable.getGranularity(),
					consumable.getAddress(),
					System.nanoTime(),
					consumable.getTokenDefinitionReference(),
					System.currentTimeMillis() / 60000L + 60000L,
					consumable.getTokenPermissions()
				),
				tokens -> tokens,
				TransferrableTokensParticle::getAmount
			);


		try {
			FungibleParticleTransition<TransferrableTokensParticle, StakedTokensParticle> transition = transitioner.createTransition(
				tokenConsumables,
				TokenUnitConversions.unitsToSubunits(stake.getAmount())
			);

			ParticleGroup.ParticleGroupBuilder particleGroupBuilder = ParticleGroup.builder();
			transition.getRemoved().forEach(p -> particleGroupBuilder.addParticle(p, Spin.DOWN));
			transition.getMigrated().forEach(p -> particleGroupBuilder.addParticle(p, Spin.UP));
			transition.getTransitioned().forEach(p -> particleGroupBuilder.addParticle(p, Spin.UP));

			return Collections.singletonList(particleGroupBuilder.build());
		} catch (NotEnoughFungiblesException e) {
			throw new InsufficientFundsException(
				tokenRef, TokenUnitConversions.subunitsToUnits(e.getCurrent()), stake.getAmount()
			);
		}

	}
}
