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

import com.google.common.collect.ImmutableSet;
import com.radixdlt.client.application.translate.StageActionException;
import com.radixdlt.client.application.translate.ShardedParticleStateId;
import com.radixdlt.client.atommodel.tokens.MutableSupplyTokenDefinitionParticle;
import com.radixdlt.client.atommodel.tokens.TransferrableTokensParticle;
import com.radixdlt.client.atommodel.tokens.UnallocatedTokensParticle;
import com.radixdlt.client.core.atoms.ParticleGroup.ParticleGroupBuilder;
import com.radixdlt.identifiers.RRI;
import com.radixdlt.client.core.atoms.particles.Spin;
import com.radixdlt.client.core.fungible.FungibleParticleTransitioner;
import com.radixdlt.client.core.fungible.FungibleParticleTransitioner.FungibleParticleTransition;
import com.radixdlt.client.core.fungible.NotEnoughFungiblesException;
import java.math.BigDecimal;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import com.radixdlt.client.core.atoms.ParticleGroup;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import com.radixdlt.utils.UInt256;

import com.radixdlt.client.application.translate.StatefulActionToParticleGroupsMapper;
import com.radixdlt.identifiers.RadixAddress;
import com.radixdlt.client.core.atoms.particles.Particle;

public class MintTokensActionMapper implements StatefulActionToParticleGroupsMapper<MintTokensAction> {
	public MintTokensActionMapper() {
	}

	@Override
	public Set<ShardedParticleStateId> requiredState(MintTokensAction mintTokensAction) {
		RadixAddress tokenDefinitionAddress = mintTokensAction.getRRI().getAddress();

		return ImmutableSet.of(
			ShardedParticleStateId.of(UnallocatedTokensParticle.class, tokenDefinitionAddress),
			ShardedParticleStateId.of(MutableSupplyTokenDefinitionParticle.class, tokenDefinitionAddress)
		);
	}

	@Override
	public List<ParticleGroup> mapToParticleGroups(MintTokensAction mintTokensAction, Stream<Particle> store) throws StageActionException {
		RRI rri = mintTokensAction.getRRI();

		if (mintTokensAction.getAmount().compareTo(BigDecimal.ZERO) <= 0) {
			throw new IllegalArgumentException("Mint amount must be greater than 0.");
		}

		Map<Class<? extends Particle>, List<Particle>> particles = store.collect(Collectors.groupingBy(Particle::getClass));
		final List<Particle> tokDefParticles = particles.get(MutableSupplyTokenDefinitionParticle.class);
		if (tokDefParticles == null
			|| tokDefParticles.stream().noneMatch(p -> ((MutableSupplyTokenDefinitionParticle) p).getRRI().equals(rri))
		) {
			throw new UnknownTokenException(mintTokensAction.getRRI());
		}

		final FungibleParticleTransitioner<UnallocatedTokensParticle, TransferrableTokensParticle> transitioner =
			new FungibleParticleTransitioner<>(
				(amt, consumable) -> new TransferrableTokensParticle(
					amt,
					consumable.getGranularity(),
					mintTokensAction.getAddress(),
					System.nanoTime(),
					consumable.getTokDefRef(),
					System.currentTimeMillis() / 60000L + 60000L,
					consumable.getTokenPermissions()
				),
				mintedTokens -> mintedTokens,
				(amt, consumable) -> new UnallocatedTokensParticle(
					amt,
					consumable.getGranularity(),
					System.nanoTime(),
					consumable.getTokDefRef(),
					consumable.getTokenPermissions()
				),
				unallocated -> unallocated,
				UnallocatedTokensParticle::getAmount
			);

		final FungibleParticleTransition<UnallocatedTokensParticle, TransferrableTokensParticle> transition;
		try {
			transition = transitioner.createTransition(
				particles.getOrDefault(UnallocatedTokensParticle.class, Collections.emptyList())
					.stream()
					.map(UnallocatedTokensParticle.class::cast)
					.filter(p -> p.getTokDefRef().equals(rri))
					.collect(Collectors.toList()),
				TokenUnitConversions.unitsToSubunits(mintTokensAction.getAmount())
			);
		} catch (NotEnoughFungiblesException e) {
			throw new TokenOverMintException(
				mintTokensAction.getRRI(),
				TokenUnitConversions.subunitsToUnits(UInt256.MAX_VALUE),
				TokenUnitConversions.subunitsToUnits(UInt256.MAX_VALUE.subtract(e.getCurrent())),
				mintTokensAction.getAmount()
			);
		}


		ParticleGroupBuilder particleGroupBuilder = ParticleGroup.builder();
		transition.getRemoved().stream().map(t -> (Particle) t).forEach(p -> particleGroupBuilder.addParticle(p, Spin.DOWN));
		transition.getMigrated().stream().map(t -> (Particle) t).forEach(p -> particleGroupBuilder.addParticle(p, Spin.UP));
		transition.getTransitioned().stream().map(t -> (Particle) t).forEach(p -> particleGroupBuilder.addParticle(p, Spin.UP));

		return Collections.singletonList(particleGroupBuilder.build());
	}
}
