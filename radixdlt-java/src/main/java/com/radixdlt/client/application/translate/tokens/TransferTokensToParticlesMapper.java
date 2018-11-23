package com.radixdlt.client.application.translate.tokens;

import com.google.gson.JsonArray;
import com.radixdlt.client.application.actions.TransferTokensAction;
import com.radixdlt.client.application.objects.Data;
import com.radixdlt.client.application.translate.InsufficientFundsException;
import com.radixdlt.client.application.translate.TokenBalanceState;
import com.radixdlt.client.application.translate.TokenBalanceState.Balance;
import com.radixdlt.client.atommodel.quarks.FungibleQuark.FungibleType;
import com.radixdlt.client.core.RadixUniverse;
import com.radixdlt.client.atommodel.tokens.TokenClassReference;
import com.radixdlt.client.core.atoms.particles.SpunParticle;
import com.radixdlt.client.atommodel.message.MessageParticle;
import com.radixdlt.client.atommodel.message.MessageParticle.MessageParticleBuilder;
import com.radixdlt.client.atommodel.tokens.OwnedTokensParticle;
import com.radixdlt.client.core.crypto.ECKeyPair;
import com.radixdlt.client.core.crypto.EncryptedPrivateKey;
import com.radixdlt.client.core.crypto.Encryptor;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Maps a send message action to the particles necessary to be included in an atom.
 */
public class TransferTokensToParticlesMapper {
	private final RadixUniverse universe;

	public TransferTokensToParticlesMapper(RadixUniverse universe) {
		this.universe = universe;
	}

	public List<SpunParticle> map(TransferTokensAction transfer, TokenBalanceState curState) throws InsufficientFundsException {
		if (transfer == null) {
			return Collections.emptyList();
		}

		final Map<TokenClassReference, Balance> allConsumables = curState.getBalance();

		final TokenClassReference tokenRef = transfer.getTokenClassReference();
		final Balance balance =
				Optional.ofNullable(allConsumables.get(transfer.getTokenClassReference())).orElse(Balance.empty());
		if (balance.getAmount().compareTo(transfer.getAmount()) < 0) {
			throw new InsufficientFundsException(
					tokenRef, balance.getAmount(), transfer.getAmount()
			);
		}

		final List<OwnedTokensParticle> unconsumedOwnedTokensParticles =
				Optional.ofNullable(allConsumables.get(transfer.getTokenClassReference()))
						.map(bal -> bal.unconsumedConsumables().collect(Collectors.toList()))
						.orElse(Collections.emptyList());

		List<SpunParticle> particles = new ArrayList<>();

		// Translate attachment to corresponding atom structure
		final Data attachment = transfer.getAttachment();
		if (attachment != null) {
			particles.add(
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
						.setMetaData("application", "encryptor")
						.setMetaData("contentType", "application/json")
						.from(transfer.getFrom())
						.to(transfer.getTo())
						.build();
				particles.add(SpunParticle.up(encryptorParticle));
			}
		}

		long consumerTotal = 0;
		final long subUnitAmount = transfer.getAmount().multiply(TokenClassReference.getSubUnits()).longValueExact();
		Iterator<OwnedTokensParticle> iterator = unconsumedOwnedTokensParticles.iterator();
		Map<ECKeyPair, Long> consumerQuantities = new HashMap<>();

		// HACK for now
		// TODO: remove this, create a ConsumersCreator
		// TODO: randomize this to decrease probability of collision
		while (consumerTotal < subUnitAmount && iterator.hasNext()) {
			final long left = subUnitAmount - consumerTotal;

			OwnedTokensParticle particle = iterator.next();
			consumerTotal += particle.getAmount();

			final long amount = Math.min(left, particle.getAmount());
			particle.addConsumerQuantities(amount, transfer.getTo().toECKeyPair(), consumerQuantities);

			SpunParticle<OwnedTokensParticle> down = SpunParticle.down(particle);
			particles.add(down);
		}

		consumerQuantities.entrySet().stream()
			.map(entry -> new OwnedTokensParticle(
				entry.getValue(),
				FungibleType.TRANSFERRED,
				universe.getAddressFrom(entry.getKey().getPublicKey()),
				System.nanoTime(),
				transfer.getTokenClassReference(),
				System.currentTimeMillis() / 60000L + 60000L
			))
			.map(SpunParticle::up)
			.forEach(particles::add);
		return particles;
	}
}
