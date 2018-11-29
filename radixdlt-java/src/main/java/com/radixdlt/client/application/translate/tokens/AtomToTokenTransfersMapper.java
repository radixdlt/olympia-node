package com.radixdlt.client.application.translate.tokens;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Optional;

import org.radix.serialization2.DsonOutput;
import org.radix.serialization2.client.Serialize;

import com.google.gson.JsonArray;
import com.google.gson.JsonParser;
import com.radixdlt.client.application.identity.Data;
import com.radixdlt.client.application.identity.RadixIdentity;
import com.radixdlt.client.application.translate.AtomToExecutedActionsMapper;
import com.radixdlt.client.atommodel.accounts.RadixAddress;
import com.radixdlt.client.atommodel.message.MessageParticle;
import com.radixdlt.client.atommodel.quarks.DataQuark;
import com.radixdlt.client.atommodel.tokens.TokenClassReference;
import com.radixdlt.client.core.RadixUniverse;
import com.radixdlt.client.core.atoms.Atom;
import com.radixdlt.client.core.crypto.CryptoException;
import com.radixdlt.client.core.crypto.ECPublicKey;
import com.radixdlt.client.core.crypto.EncryptedPrivateKey;
import com.radixdlt.client.core.crypto.Encryptor;

import io.reactivex.Observable;
import io.reactivex.Single;

/**
 * Maps an atom to some number of token transfer actions.
 */
public class AtomToTokenTransfersMapper implements AtomToExecutedActionsMapper<TokenTransfer> {
	private static final JsonParser JSON_PARSER = new JsonParser();
	private final RadixUniverse universe;

	public AtomToTokenTransfersMapper(RadixUniverse universe) {
		this.universe = universe;
	}

	@Override
	public Observable<TokenTransfer> map(Atom atom, RadixIdentity identity) {
		return Observable.fromIterable(atom.tokenSummary().entrySet())
			.filter(e -> !e.getKey().equals(universe.getPOWToken()))
			.flatMapSingle(e -> {
				List<Entry<ECPublicKey, BigInteger>> summary = new ArrayList<>(e.getValue().entrySet());
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
					from = summary.get(0).getValue().signum() <= 0 ? universe.getAddressFrom(summary.get(0).getKey()) : null;
					to = summary.get(0).getValue().signum() < 0 ? null : universe.getAddressFrom(summary.get(0).getKey());
				} else {
					if (summary.get(0).getValue().signum() > 0) {
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

				final BigDecimal amount = TokenClassReference.subunitsToUnits(summary.get(0).getValue().abs());

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
}
