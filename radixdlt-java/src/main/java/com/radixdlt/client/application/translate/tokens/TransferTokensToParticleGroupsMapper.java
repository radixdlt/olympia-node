package com.radixdlt.client.application.translate.tokens;

import com.radixdlt.client.application.translate.ShardedParticleStateId;
import com.radixdlt.client.core.atoms.particles.RRI;
import com.radixdlt.client.core.fungible.FungibleParticleTransitioner;
import com.radixdlt.client.core.fungible.FungibleParticleTransitioner.FungibleParticleTransition;
import com.radixdlt.client.core.fungible.NotEnoughFungiblesException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.UnaryOperator;
import java.util.Set;
import java.util.stream.Collectors;

import com.radixdlt.client.atommodel.tokens.TransferrableTokensParticle;
import com.radixdlt.client.core.atoms.particles.Particle;
import java.util.stream.Stream;
import org.radix.utils.UInt256;

import com.google.gson.JsonArray;
import com.radixdlt.client.application.identity.Data;
import com.radixdlt.client.application.translate.Action;
import com.radixdlt.client.application.translate.StatefulActionToParticleGroupsMapper;
import com.radixdlt.client.atommodel.message.MessageParticle;
import com.radixdlt.client.atommodel.message.MessageParticle.MessageParticleBuilder;
import com.radixdlt.client.core.atoms.ParticleGroup;
import com.radixdlt.client.core.atoms.particles.SpunParticle;
import com.radixdlt.client.core.crypto.EncryptedPrivateKey;
import com.radixdlt.client.core.crypto.Encryptor;

/**
 * Maps a send message action to the particles necessary to be included in an atom.
 */
public class TransferTokensToParticleGroupsMapper implements StatefulActionToParticleGroupsMapper {
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

	private List<SpunParticle> mapToAttachmentParticles(TransferTokensAction transfer) {
		final Data attachment = transfer.getAttachment();
		if (attachment != null) {
			List<SpunParticle> spunParticles = new ArrayList<>();
			spunParticles.add(
				SpunParticle.up(
					new MessageParticleBuilder()
						.payload(attachment.getBytes())
						.from(transfer.getFrom())
						.to(transfer.getTo())
						.build()
				)
			);

			Encryptor encryptor = attachment.getEncryptor();
			if (encryptor != null) {
				JsonArray protectorsJson = new JsonArray();
				encryptor.getProtectors().stream().map(EncryptedPrivateKey::base64).forEach(protectorsJson::add);

				byte[] encryptorPayload = protectorsJson.toString().getBytes(StandardCharsets.UTF_8);
				MessageParticle encryptorParticle = new MessageParticleBuilder()
					.payload(encryptorPayload)
					.metaData("application", "encryptor")
					.metaData("contentType", "application/json")
					.from(transfer.getFrom())
					.to(transfer.getTo())
					.build();
				spunParticles.add(SpunParticle.up(encryptorParticle));
			}

			return spunParticles;
		} else {
			return Collections.emptyList();
		}
	}

	@Override
	public Set<ShardedParticleStateId> requiredState(Action action) {
		if (!(action instanceof TransferTokensAction)) {
			return Collections.emptySet();
		}

		TransferTokensAction transfer = (TransferTokensAction) action;

		return Collections.singleton(ShardedParticleStateId.of(TransferrableTokensParticle.class, transfer.getFrom()));
	}

	@Override
	public List<ParticleGroup> mapToParticleGroups(Action action, Stream<Particle> store)
		throws InsufficientFundsException {
		if (!(action instanceof TransferTokensAction)) {
			return Collections.emptyList();
		}

		TransferTokensAction transfer = (TransferTokensAction) action;
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
		final List<SpunParticle> attachmentParticles = this.mapToAttachmentParticles(transfer);
		final List<SpunParticle> sparticles = Stream.concat(transferParticles.stream(), attachmentParticles.stream()).collect(Collectors.toList());

		return Collections.singletonList(
			ParticleGroup.of(sparticles)
		);
	}
}
