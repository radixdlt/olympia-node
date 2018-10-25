package com.radixdlt.client.application.translate;

import com.google.gson.JsonArray;
import com.google.gson.JsonParser;
import com.radixdlt.client.application.actions.TransferTokensAction;
import com.radixdlt.client.application.objects.Data;
import com.radixdlt.client.application.translate.TokenBalanceState.Balance;
import com.radixdlt.client.core.RadixUniverse;
import com.radixdlt.client.core.address.RadixAddress;
import com.radixdlt.client.core.atoms.AccountReference;
import com.radixdlt.client.core.atoms.Atom;
import com.radixdlt.client.core.atoms.TokenClassReference;
import com.radixdlt.client.core.atoms.particles.SpunParticle;
import com.radixdlt.client.core.atoms.particles.StorageParticle;
import com.radixdlt.client.core.atoms.particles.StorageParticle.StorageParticleBuilder;
import com.radixdlt.client.core.atoms.particles.TransferParticle;
import com.radixdlt.client.core.atoms.particles.quarks.DataQuark;
import com.radixdlt.client.core.atoms.particles.quarks.FungibleQuark;
import com.radixdlt.client.core.crypto.ECKeyPair;
import com.radixdlt.client.core.crypto.ECPublicKey;
import com.radixdlt.client.core.crypto.EncryptedPrivateKey;
import com.radixdlt.client.core.crypto.Encryptor;
import org.radix.serialization2.DsonOutput;
import org.radix.serialization2.client.Serialize;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Optional;
import java.util.stream.Collectors;

public class TokenTransferTranslator {
	private final RadixUniverse universe;
	private static final JsonParser JSON_PARSER = new JsonParser();

	public TokenTransferTranslator(RadixUniverse universe) {
		this.universe = universe;
	}

	public List<TransferTokensAction> fromAtom(Atom atom) {
		return atom.tokenSummary().entrySet().stream()
				.filter(e -> !e.getKey().equals(universe.getPOWToken()))
				.map(e -> {
					List<Entry<ECPublicKey, Long>> summary = new ArrayList<>(e.getValue().entrySet());
					if (summary.isEmpty()) {
						throw new IllegalStateException("Invalid atom: "
								+ Serialize.getInstance().toJson(atom, DsonOutput.Output.ALL));
					}
					if (summary.size() > 2) {
						throw new IllegalStateException("More than two participants in token transfer. "
								+ "Unable to handle: " + summary);
					}

					final RadixAddress from;
					final RadixAddress to;
					if (summary.size() == 1) {
						from = summary.get(0).getValue() <= 0L ? universe.getAddressFrom(summary.get(0).getKey()) : null;
						to = summary.get(0).getValue() < 0L ? null : universe.getAddressFrom(summary.get(0).getKey());
					} else {
						if (summary.get(0).getValue() > 0) {
							from = universe.getAddressFrom(summary.get(1).getKey());
							to = universe.getAddressFrom(summary.get(0).getKey());
						} else {
							from = universe.getAddressFrom(summary.get(0).getKey());
							to = universe.getAddressFrom(summary.get(1).getKey());
						}
					}
					final Optional<StorageParticle> bytesParticle = atom.getDataParticles().stream()
							.filter(p -> !"encryptor".equals(p.getMetaData("application")))
							.findFirst();

					// Construct attachment from atom
					final Data attachment;
					if (bytesParticle.isPresent()) {
						Map<String, Object> metaData = new HashMap<>();

						final Optional<StorageParticle> encryptorParticle = atom.getDataParticles().stream()
								.filter(p -> "encryptor".equals(p.getMetaData("application")))
								.findAny();
						metaData.put("encrypted", encryptorParticle.isPresent());

						final Encryptor encryptor;
						if (encryptorParticle.isPresent()) {
							String encryptorBytes = new String(
									encryptorParticle.get().getQuarkOrError(DataQuark.class).getBytes(),
									StandardCharsets.UTF_8);
							JsonArray protectorsJson = JSON_PARSER.parse(encryptorBytes).getAsJsonArray();
							List<EncryptedPrivateKey> protectors = new ArrayList<>();
							protectorsJson.forEach(protectorJson ->
									protectors.add(EncryptedPrivateKey.fromBase64(protectorJson.getAsString()))
							);
							encryptor = new Encryptor(protectors);
						} else {
							encryptor = null;
						}
						attachment = Data.raw(
								bytesParticle.get().getQuarkOrError(DataQuark.class).getBytes(), metaData, encryptor);
					} else {
						attachment = null;
					}

					final BigDecimal amount = TokenClassReference.subUnitsToDecimal(Math.abs(summary.get(0).getValue()));
					return TransferTokensAction.create(from, to, amount, e.getKey(), attachment, atom.getTimestamp());
				})
				.collect(Collectors.toList());
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

		final List<TransferParticle> unconsumedTransferParticles =
				Optional.ofNullable(allConsumables.get(transfer.getTokenClassReference()))
						.map(bal -> bal.unconsumedConsumables().collect(Collectors.toList()))
						.orElse(Collections.emptyList());

		List<SpunParticle> particles = new ArrayList<>();

		// Translate attachment to corresponding atom structure
		final Data attachment = transfer.getAttachment();
		if (attachment != null) {
			particles.add(
				SpunParticle.up(
					new StorageParticleBuilder()
						.payload(attachment.getBytes())
						.account(transfer.getFrom())
						.account(transfer.getTo())
						.build()
				)
			);
			Encryptor encryptor = attachment.getEncryptor();
			if (encryptor != null) {
				JsonArray protectorsJson = new JsonArray();
				encryptor.getProtectors().stream().map(EncryptedPrivateKey::base64).forEach(protectorsJson::add);

				byte[] encryptorPayload = protectorsJson.toString().getBytes(StandardCharsets.UTF_8);
				StorageParticle encryptorParticle = new StorageParticleBuilder()
						.payload(encryptorPayload)
						.setMetaData("application", "encryptor")
						.setMetaData("contentType", "application/json")
						.account(transfer.getFrom())
						.account(transfer.getTo())
						.build();
				particles.add(SpunParticle.up(encryptorParticle));
			}
		}

		long consumerTotal = 0;
		final long subUnitAmount = transfer.getAmount().multiply(TokenClassReference.getSubUnits()).longValueExact();
		Iterator<TransferParticle> iterator = unconsumedTransferParticles.iterator();
		Map<ECKeyPair, Long> consumerQuantities = new HashMap<>();

		// HACK for now
		// TODO: remove this, create a ConsumersCreator
		// TODO: randomize this to decrease probability of collision
		while (consumerTotal < subUnitAmount && iterator.hasNext()) {
			final long left = subUnitAmount - consumerTotal;

			TransferParticle particle = iterator.next();
			consumerTotal += particle.getAmount();

			final long amount = Math.min(left, particle.getAmount());
			particle.addConsumerQuantities(amount, transfer.getTo().toECKeyPair(), consumerQuantities);

			SpunParticle<TransferParticle> down = SpunParticle.down(particle);
			particles.add(down);
		}

		consumerQuantities.entrySet().stream()
			.map(entry -> new TransferParticle(
				entry.getValue(),
				FungibleQuark.FungibleType.AMOUNT,
				new AccountReference(entry.getKey().getPublicKey()),
				System.nanoTime(),
				transfer.getTokenClassReference(),
				System.currentTimeMillis() / 60000L + 60000L
			))
			.map(SpunParticle::up)
			.forEach(particles::add);
		return particles;
	}
}
