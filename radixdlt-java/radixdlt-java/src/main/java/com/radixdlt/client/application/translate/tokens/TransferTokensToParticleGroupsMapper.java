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
import com.radixdlt.atommodel.tokens.MutableSupplyTokenDefinitionParticle.TokenTransition;
import com.radixdlt.atommodel.tokens.TokenPermission;
import com.radixdlt.client.core.fungible.FungibleTransitionMapper;
import com.radixdlt.identifiers.RRI;
import com.radixdlt.client.core.fungible.NotEnoughFungiblesException;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import com.radixdlt.atommodel.tokens.TransferrableTokensParticle;
import com.radixdlt.constraintmachine.Particle;
import java.util.stream.Stream;
import com.radixdlt.utils.UInt256;

import com.radixdlt.client.application.translate.StatefulActionToParticleGroupsMapper;
import com.radixdlt.atom.ParticleGroup;
import com.radixdlt.atom.SpunParticle;

/**
 * Maps a send message action to the particles necessary to be included in an atom.
 */
public class TransferTokensToParticleGroupsMapper implements StatefulActionToParticleGroupsMapper<TransferTokensAction> {
	public TransferTokensToParticleGroupsMapper() {
		// Empty on purpose
	}

	private static List<SpunParticle> mapToParticles(TransferTokensAction transfer, List<TransferrableTokensParticle> currentParticles)
		throws NotEnoughFungiblesException {

		final UInt256 totalAmountToTransfer = TokenUnitConversions.unitsToSubunits(transfer.getAmount());
		if (currentParticles.isEmpty()) {
			throw new NotEnoughFungiblesException(totalAmountToTransfer, UInt256.ZERO);
		}

		final RRI token = currentParticles.get(0).getTokDefRef();
		final UInt256 granularity = currentParticles.get(0).getGranularity();
		final Map<TokenTransition, TokenPermission> permissions = currentParticles.get(0).getTokenPermissions();

		FungibleTransitionMapper<TransferrableTokensParticle, TransferrableTokensParticle> mapper = new FungibleTransitionMapper<>(
			TransferrableTokensParticle::getAmount,
			amt ->
				new TransferrableTokensParticle(
					transfer.getFrom(),
					amt,
					granularity,
					token,
					permissions,
					System.nanoTime()
				),
			amt ->
				new TransferrableTokensParticle(
					transfer.getTo(),
					totalAmountToTransfer,
					granularity,
					token,
					permissions,
					System.nanoTime()
				)
		);

		return mapper.mapToParticles(currentParticles, totalAmountToTransfer);
	}

	@Override
	public Set<ShardedParticleStateId> requiredState(TransferTokensAction transfer) {
		return Collections.singleton(ShardedParticleStateId.of(TransferrableTokensParticle.class, transfer.getFrom()));
	}

	@Override
	public List<ParticleGroup> mapToParticleGroups(TransferTokensAction transfer, Stream<Particle> store) throws StageActionException {
		final RRI tokenRef = transfer.getRRI();

		List<TransferrableTokensParticle> tokenConsumables = store
			.map(TransferrableTokensParticle.class::cast)
			.filter(p -> p.getTokDefRef().equals(tokenRef))
			.collect(Collectors.toList());

		final List<SpunParticle> transferParticles;
		try {
			transferParticles = mapToParticles(transfer, tokenConsumables);
		} catch (NotEnoughFungiblesException e) {
			throw new InsufficientFundsException(
				tokenRef, TokenUnitConversions.subunitsToUnits(e.getCurrent()), transfer.getAmount()
			);
		}

		return Collections.singletonList(
			ParticleGroup.of(transferParticles)
		);
	}
}
