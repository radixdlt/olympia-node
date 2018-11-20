package com.radixdlt.client.application.translate;

import com.google.gson.JsonArray;
import com.google.gson.JsonParser;
import com.radixdlt.client.application.actions.TransferTokensAction;
import com.radixdlt.client.application.objects.Data;
import com.radixdlt.client.application.translate.TokenBalanceState.Balance;
import com.radixdlt.client.atommodel.quarks.FungibleQuark.FungibleType;
import com.radixdlt.client.core.RadixUniverse;
import com.radixdlt.client.atommodel.accounts.RadixAddress;
import com.radixdlt.client.core.atoms.Atom;
import com.radixdlt.client.atommodel.tokens.TokenClassReference;
import com.radixdlt.client.core.atoms.particles.SpunParticle;
import com.radixdlt.client.atommodel.message.MessageParticle;
import com.radixdlt.client.atommodel.message.MessageParticle.MessageParticleBuilder;
import com.radixdlt.client.atommodel.tokens.OwnedTokensParticle;
import com.radixdlt.client.atommodel.quarks.DataQuark;
import com.radixdlt.client.core.crypto.ECKeyPair;
import com.radixdlt.client.core.crypto.ECPublicKey;
import com.radixdlt.client.core.crypto.EncryptedPrivateKey;
import com.radixdlt.client.core.crypto.Encryptor;
import org.radix.serialization2.DsonOutput;
import org.radix.serialization2.client.Serialize;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import com.radixdlt.client.application.identity.RadixIdentity;
import com.radixdlt.client.application.objects.TokenTransfer;
import com.radixdlt.client.core.crypto.CryptoException;
import io.reactivex.Observable;
import io.reactivex.Single;
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

	public Observable<TokenTransfer> fromAtom(Atom atom, RadixIdentity identity) {
		return Observable.fromIterable(atom.tokenSummary().entrySet())
			.filter(e -> !e.getKey().equals(universe.getPOWToken()))
			.flatMapSingle(e -> {
				List<Entry<ECPublicKey, Long>> summary = new ArrayList<>(e.getValue().entrySet());
				if (summary.isEmpty()) {
					throw new IllegalStateException(
						"Invalid atom: " + Serialize.getInstance().toJson(atom, DsonOutput.Output.ALL)
					);
				}
				if (summary.size() > 2) {
					throw new IllegalStateException(
						"More than two participants in token transfer. " + "Unable to handle: " + summary
					);
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
				final Optional<MessageParticle> bytesParticle =
					atom.getDataParticles().stream()
						.filter(p -> !"encryptor".equals(p.getMetaData("application")))
						.findFirst();

				final BigDecimal amount = TokenClassReference.subUnitsToDecimal(Math.abs(summary.get(0).getValue()));

				if (bytesParticle.isPresent()) {
					Map<String, Object> metaData = new HashMap<>();

					final Optional<MessageParticle> encryptorParticle =
						atom.getDataParticles().stream()
							.filter(p -> "encryptor".equals(p.getMetaData("application")))
							.findAny();
					metaData.put("encrypted", encryptorParticle.isPresent());

					final Encryptor encryptor;
					if (encryptorParticle.isPresent()) {
						String encryptorBytes = new String(
							encryptorParticle.get().getQuarkOrError(DataQuark.class).getBytes(),
							StandardCharsets.UTF_8
						);
						JsonArray protectorsJson = JSON_PARSER.parse(encryptorBytes).getAsJsonArray();
						List<EncryptedPrivateKey> protectors = new ArrayList<>();
						protectorsJson.forEach(
							protectorJson -> protectors.add(EncryptedPrivateKey.fromBase64(protectorJson.getAsString()))
						);

						encryptor = new Encryptor(protectors);
					} else {
						encryptor = null;
					}

					final Data attachment = Data.raw(
						bytesParticle.get().getQuarkOrError(DataQuark.class).getBytes(),
						metaData,
						encryptor
					);

					return Single.just(attachment).flatMap(identity::decrypt)
						.map(unencryptedData ->
							new TokenTransfer(from, to, e.getKey(), amount, unencryptedData, atom.getTimestamp())
						)
						.onErrorResumeNext(ex -> {
							if (ex instanceof CryptoException) {
								return Single.just(
									new TokenTransfer(from, to, e.getKey(), amount, null, atom.getTimestamp())
								);
							} else {
								return Single.error(ex);
							}
						});
				} else {
					return Single.just(new TokenTransfer(from, to, e.getKey(), amount, null, atom.getTimestamp()));
				}
			});
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
						.account(transfer.getFrom())
						.account(transfer.getTo())
						.source(transfer.getFrom())
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
						.account(transfer.getFrom())
						.account(transfer.getTo())
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
