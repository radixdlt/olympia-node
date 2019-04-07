package com.radixdlt.client.application.translate.tokens;

import com.radixdlt.client.application.translate.ShardedAppStateId;
import com.radixdlt.client.core.atoms.particles.RRI;
import com.radixdlt.client.core.fungible.FungibleParticleTransitioner;
import com.radixdlt.client.core.fungible.FungibleParticleTransitioner.FungibleParticleTransition;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
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
import com.radixdlt.client.application.translate.ApplicationState;
import com.radixdlt.client.application.translate.StatefulActionToParticleGroupsMapper;
import com.radixdlt.client.application.translate.tokens.TokenBalanceState.Balance;
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

	private List<SpunParticle> mapToParticles(TransferTokensAction transfer, List<TransferrableTokensParticle> currentParticles) {
		// FIXME: figure out way to combine the following two similar combiners
		UnaryOperator<List<TransferrableTokensParticle>> combiner =
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
	public Set<ShardedAppStateId> requiredState(Action action) {
		if (!(action instanceof TransferTokensAction)) {
			return Collections.emptySet();
		}

		TransferTokensAction transfer = (TransferTokensAction) action;

		return Collections.singleton(ShardedAppStateId.of(TokenBalanceState.class, transfer.getFrom()));
	}

	@Override
	public List<ParticleGroup> mapToParticleGroups(Action action, Map<ShardedAppStateId, ? extends ApplicationState> store)
		throws InsufficientFundsException {
		if (!(action instanceof TransferTokensAction)) {
			return Collections.emptyList();
		}

		TransferTokensAction transfer = (TransferTokensAction) action;
		TokenBalanceState tokenBalanceState = (TokenBalanceState) store.get(ShardedAppStateId.of(TokenBalanceState.class, transfer.getFrom()));
		final RRI tokenRef = transfer.getTokenDefRef();
		final Map<RRI, Balance> allConsumables = tokenBalanceState.getBalance();
		final Balance balance = Optional.ofNullable(
			allConsumables.get(transfer.getTokenDefRef())).orElse(Balance.empty(BigInteger.ONE));

		if (balance.getAmount().compareTo(transfer.getAmount()) < 0) {
			throw new InsufficientFundsException(
				tokenRef, balance.getAmount(), transfer.getAmount()
			);
		}

		List<TransferrableTokensParticle> tokenConsumables = Optional.ofNullable(allConsumables.get(transfer.getTokenDefRef()))
			.map(bal -> bal.unconsumedTransferrable().collect(Collectors.toList()))
			.orElse(Collections.emptyList());

		List<SpunParticle> transferParticles = this.mapToParticles(transfer, tokenConsumables);
		List<SpunParticle> attachmentParticles = this.mapToAttachmentParticles(transfer);

		return Collections.singletonList(ParticleGroup.of(Stream.concat(transferParticles.stream(), attachmentParticles.stream()).collect(Collectors.toList())));
	}
}
