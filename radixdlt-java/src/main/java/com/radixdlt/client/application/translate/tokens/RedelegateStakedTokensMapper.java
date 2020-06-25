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
 * Maps a redelegate staked tokens action to the particles necessary to be included in an atom.
 */
public class RedelegateStakedTokensMapper implements StatefulActionToParticleGroupsMapper<RedelegateStakedTokensAction> {
	public RedelegateStakedTokensMapper() {
	}

	@Override
	public Set<ShardedParticleStateId> requiredState(RedelegateStakedTokensAction action) {
		return Collections.singleton(ShardedParticleStateId.of(StakedTokensParticle.class, action.getFrom()));
	}

	@Override
	public List<ParticleGroup> mapToParticleGroups(RedelegateStakedTokensAction redelegate, Stream<Particle> store) throws StageActionException {
		final RRI tokenRef = redelegate.getRRI();

		List<StakedTokensParticle> stakeConsumables = store
			.map(StakedTokensParticle.class::cast)
			.filter(p -> p.getTokenDefinitionReference().equals(tokenRef))
			.collect(Collectors.toList());

		final FungibleParticleTransitioner<StakedTokensParticle, StakedTokensParticle> transitioner =
			new FungibleParticleTransitioner<>(
				(amt, consumable) -> new StakedTokensParticle(
					redelegate.getOldDelegate(),
					amt,
					consumable.getGranularity(),
					redelegate.getFrom(),
					System.nanoTime(),
					consumable.getTokenDefinitionReference(),
					System.currentTimeMillis() / 60000L + 60000L,
					consumable.getTokenPermissions()
				),
				stakedTokens -> stakedTokens,
				(amt, consumable) -> new StakedTokensParticle(
					redelegate.getNewDelegate(),
					amt,
					consumable.getGranularity(),
					consumable.getAddress(),
					System.nanoTime(),
					consumable.getTokenDefinitionReference(),
					System.currentTimeMillis() / 60000L + 60000L,
					consumable.getTokenPermissions()
				),
				stakedTokens -> stakedTokens,
				StakedTokensParticle::getAmount
			);


		try {
			FungibleParticleTransition<StakedTokensParticle, StakedTokensParticle> transition = transitioner.createTransition(
				stakeConsumables,
				TokenUnitConversions.unitsToSubunits(redelegate.getAmount())
			);

			ParticleGroup.ParticleGroupBuilder particleGroupBuilder = ParticleGroup.builder();
			transition.getRemoved().stream().map(t -> (Particle) t).forEach(p -> particleGroupBuilder.addParticle(p, Spin.DOWN));
			transition.getMigrated().stream().map(t -> (Particle) t).forEach(p -> particleGroupBuilder.addParticle(p, Spin.UP));
			transition.getTransitioned().stream().map(t -> (Particle) t).forEach(p -> particleGroupBuilder.addParticle(p, Spin.UP));

			return Collections.singletonList(particleGroupBuilder.build());
		} catch (NotEnoughFungiblesException e) {
			throw new InsufficientFundsException(
				tokenRef, TokenUnitConversions.subunitsToUnits(e.getCurrent()), redelegate.getAmount()
			);
		}

	}
}
