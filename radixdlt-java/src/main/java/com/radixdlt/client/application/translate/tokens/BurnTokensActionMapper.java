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

import com.radixdlt.client.application.translate.StageActionException;
import com.radixdlt.client.application.translate.ShardedParticleStateId;
import com.radixdlt.client.atommodel.tokens.UnallocatedTokensParticle;
import com.radixdlt.client.core.atoms.ParticleGroup.ParticleGroupBuilder;
import com.radixdlt.client.core.atoms.particles.RRI;
import com.radixdlt.client.core.atoms.particles.Spin;
import com.radixdlt.client.core.fungible.FungibleParticleTransitioner;
import com.radixdlt.client.core.fungible.FungibleParticleTransitioner.FungibleParticleTransition;
import com.radixdlt.client.core.fungible.NotEnoughFungiblesException;
import java.math.BigDecimal;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import com.radixdlt.client.atommodel.tokens.TransferrableTokensParticle;
import com.radixdlt.client.core.atoms.ParticleGroup;
import com.radixdlt.client.core.atoms.particles.Particle;

import com.radixdlt.client.application.translate.StatefulActionToParticleGroupsMapper;
import com.radixdlt.client.atommodel.accounts.RadixAddress;

import java.util.stream.Stream;
import org.radix.utils.UInt256;

public class BurnTokensActionMapper implements StatefulActionToParticleGroupsMapper<BurnTokensAction> {
	private final FungibleParticleTransitioner<TransferrableTokensParticle, UnallocatedTokensParticle> transitioner;

	public BurnTokensActionMapper() {
		this.transitioner = new FungibleParticleTransitioner<>(
			(amt, consumable) -> new UnallocatedTokensParticle(
				amt,
				consumable.getGranularity(),
				System.nanoTime(),
				consumable.getTokenDefinitionReference(),
				consumable.getTokenPermissions()
			),
			burnedList -> burnedList,
			(amt, consumable) -> new TransferrableTokensParticle(
				amt,
				consumable.getGranularity(),
				consumable.getAddress(),
				System.nanoTime(),
				consumable.getTokenDefinitionReference(),
				System.currentTimeMillis() / 60000L + 60000L,
				consumable.getTokenPermissions()
			),
			transferredList -> transferredList.stream()
				.map(TransferrableTokensParticle::getAmount)
				.reduce(UInt256::add)
				.map(amt -> Collections.singletonList(
					new TransferrableTokensParticle(
						amt,
						transferredList.get(0).getGranularity(),
						transferredList.get(0).getAddress(),
						System.nanoTime(),
						transferredList.get(0).getTokenDefinitionReference(),
						System.currentTimeMillis() / 60000L + 60000L,
						transferredList.get(0).getTokenPermissions()
					)
				)).orElse(Collections.emptyList()),
			TransferrableTokensParticle::getAmount
		);
	}

	@Override
	public Set<ShardedParticleStateId> requiredState(BurnTokensAction burnTokensAction) {
		RadixAddress address = burnTokensAction.getAddress();
		return Collections.singleton(ShardedParticleStateId.of(TransferrableTokensParticle.class, address));
	}

	@Override
	public List<ParticleGroup> mapToParticleGroups(BurnTokensAction burnTokensAction, Stream<Particle> store) throws StageActionException {
		final RRI tokenRef = burnTokensAction.getRRI();
		final BigDecimal burnAmount = burnTokensAction.getAmount();

		final FungibleParticleTransition<TransferrableTokensParticle, UnallocatedTokensParticle> transition;
		try {
			transition = transitioner.createTransition(
				store.map(TransferrableTokensParticle.class::cast)
					.filter(p -> p.getTokenDefinitionReference().equals(tokenRef))
					.collect(Collectors.toList()),
				TokenUnitConversions.unitsToSubunits(burnAmount)
			);
		} catch (NotEnoughFungiblesException e) {
			throw new InsufficientFundsException(tokenRef, TokenUnitConversions.subunitsToUnits(e.getCurrent()), burnAmount);
		}

		ParticleGroupBuilder particleGroupBuilder = ParticleGroup.builder();
		transition.getRemoved().stream().map(t -> (Particle) t).forEach(p -> particleGroupBuilder.addParticle(p, Spin.DOWN));
		transition.getMigrated().stream().map(t -> (Particle) t).forEach(p -> particleGroupBuilder.addParticle(p, Spin.UP));
		transition.getTransitioned().stream().map(t -> (Particle) t).forEach(p -> particleGroupBuilder.addParticle(p, Spin.UP));

		return Collections.singletonList(particleGroupBuilder.build());
	}
}
