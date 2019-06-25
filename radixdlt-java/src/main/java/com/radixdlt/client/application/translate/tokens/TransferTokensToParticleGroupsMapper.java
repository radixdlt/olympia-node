package com.radixdlt.client.application.translate.tokens;

import com.google.common.collect.ImmutableMap;
import com.radixdlt.client.application.translate.StageActionException;
import com.radixdlt.client.application.translate.ShardedParticleStateId;
import com.radixdlt.client.core.atoms.particles.RRI;
import com.radixdlt.client.core.fungible.FungibleParticleTransitioner;
import com.radixdlt.client.core.fungible.FungibleParticleTransitioner.FungibleParticleTransition;
import com.radixdlt.client.core.fungible.NotEnoughFungiblesException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.UnaryOperator;
import java.util.Set;
import java.util.stream.Collectors;

import com.radixdlt.client.atommodel.tokens.TransferrableTokensParticle;
import com.radixdlt.client.core.atoms.particles.Particle;
import java.util.stream.Stream;
import org.bouncycastle.util.encoders.Base64;
import org.radix.utils.UInt256;

import com.radixdlt.client.application.translate.StatefulActionToParticleGroupsMapper;
import com.radixdlt.client.core.atoms.ParticleGroup;
import com.radixdlt.client.core.atoms.particles.SpunParticle;

/**
 * Maps a send message action to the particles necessary to be included in an atom.
 */
public class TransferTokensToParticleGroupsMapper implements StatefulActionToParticleGroupsMapper<TransferTokensAction> {
	public TransferTokensToParticleGroupsMapper() {
	}

	private List<SpunParticle> mapToParticles(TransferTokensAction transfer, List<TransferrableTokensParticle> currentParticles)
		throws NotEnoughFungiblesException {
		final UnaryOperator<List<TransferrableTokensParticle>> combiner =
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
				)).orElse(Collections.emptyList());

		final FungibleParticleTransitioner<TransferrableTokensParticle, TransferrableTokensParticle> transitioner =
			new FungibleParticleTransitioner<>(
				(amt, consumable) -> new TransferrableTokensParticle(
					amt,
					consumable.getGranularity(),
					transfer.getTo(),
					System.nanoTime(),
					consumable.getTokenDefinitionReference(),
					System.currentTimeMillis() / 60000L + 60000L,
					consumable.getTokenPermissions()
				),
				combiner,
				(amt, consumable) -> new TransferrableTokensParticle(
					amt,
					consumable.getGranularity(),
					consumable.getAddress(),
					System.nanoTime(),
					consumable.getTokenDefinitionReference(),
					System.currentTimeMillis() / 60000L + 60000L,
					consumable.getTokenPermissions()
				),
				combiner,
				TransferrableTokensParticle::getAmount
			);

		FungibleParticleTransition<TransferrableTokensParticle, TransferrableTokensParticle> transition = transitioner.createTransition(
			currentParticles,
			TokenUnitConversions.unitsToSubunits(transfer.getAmount())
		);

		List<SpunParticle> spunParticles = new ArrayList<>();
		transition.getRemoved().stream().map(t -> (Particle) t).forEach(p -> spunParticles.add(SpunParticle.down(p)));
		transition.getMigrated().stream().map(t -> (Particle) t).forEach(p -> spunParticles.add(SpunParticle.up(p)));
		transition.getTransitioned().stream().map(t -> (Particle) t).forEach(p -> spunParticles.add(SpunParticle.up(p)));

		return spunParticles;
	}

	@Override
	public Set<ShardedParticleStateId> requiredState(TransferTokensAction transfer) {
		return Collections.singleton(ShardedParticleStateId.of(TransferrableTokensParticle.class, transfer.getFrom()));
	}

	@Override
	public List<ParticleGroup> mapToParticleGroups(TransferTokensAction transfer, Stream<Particle> store) throws StageActionException {
		final RRI tokenRef = transfer.getTokenDefRef();

		List<TransferrableTokensParticle> tokenConsumables = store
			.map(TransferrableTokensParticle.class::cast)
			.filter(p -> p.getTokenDefinitionReference().equals(tokenRef))
			.collect(Collectors.toList());

		final List<SpunParticle> transferParticles;
		try {
			transferParticles = this.mapToParticles(transfer, tokenConsumables);
		} catch (NotEnoughFungiblesException e) {
			throw new InsufficientFundsException(
				tokenRef, TokenUnitConversions.subunitsToUnits(e.getCurrent()), transfer.getAmount()
			);
		}

		if (transfer.getAttachment() == null) {
			return Collections.singletonList(
				ParticleGroup.of(transferParticles)
			);
		} else {
			final ImmutableMap<String, String> metaData = ImmutableMap.of(
				"attachment", Base64.toBase64String(transfer.getAttachment())
			);
			return Collections.singletonList(
				ParticleGroup.of(transferParticles, metaData)
			);
		}
	}
}
