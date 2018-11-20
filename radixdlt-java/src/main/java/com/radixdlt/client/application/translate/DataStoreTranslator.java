package com.radixdlt.client.application.translate;

import com.google.gson.JsonArray;
import com.google.gson.JsonParser;
import com.radixdlt.client.application.actions.StoreDataAction;
import com.radixdlt.client.application.objects.Data;
import com.radixdlt.client.core.atoms.Atom;
import com.radixdlt.client.core.atoms.particles.SpunParticle;
import com.radixdlt.client.atommodel.message.MessageParticle;
import com.radixdlt.client.atommodel.message.MessageParticle.MessageParticleBuilder;
import com.radixdlt.client.atommodel.quarks.DataQuark;
import com.radixdlt.client.core.crypto.EncryptedPrivateKey;
import com.radixdlt.client.core.crypto.Encryptor;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public class DataStoreTranslator {
	private static final DataStoreTranslator INSTANCE = new DataStoreTranslator();
	private static final JsonParser JSON_PARSER = new JsonParser();

	public static DataStoreTranslator getInstance() {
		return INSTANCE;
	}

	private DataStoreTranslator() {
	}

	// TODO: figure out correct method signature here (return Single<AtomBuilder> instead?)
	public List<SpunParticle> map(StoreDataAction storeDataAction) {
		if (storeDataAction == null) {
			return Collections.emptyList();
		}

		byte[] payload = storeDataAction.getData().getBytes();
		String application = (String) storeDataAction.getData().getMetaData().get("application");

		List<SpunParticle> particles = new ArrayList<>();
		MessageParticle messageParticle = new MessageParticleBuilder()
				.payload(payload)
				.setMetaData("application", application)
				.accounts(storeDataAction.getAddresses())
				.source(storeDataAction.getSource())
				.build();
		particles.add(SpunParticle.up(messageParticle));

		Encryptor encryptor = storeDataAction.getData().getEncryptor();
		if (encryptor != null) {
			JsonArray protectorsJson = new JsonArray();
			encryptor.getProtectors().stream().map(EncryptedPrivateKey::base64).forEach(protectorsJson::add);

			byte[] encryptorPayload = protectorsJson.toString().getBytes(StandardCharsets.UTF_8);
			MessageParticle encryptorParticle = new MessageParticleBuilder()
					.payload(encryptorPayload)
					.setMetaData("application", "encryptor")
					.setMetaData("contentType", "application/json")
					.accounts(storeDataAction.getAddresses())
					.source(storeDataAction.getSource())
					.build();
			particles.add(SpunParticle.up(encryptorParticle));
		}

		return particles;
	}

	public Optional<Data> fromAtom(Atom atom) {
		final Optional<MessageParticle> bytesParticle = atom.getDataParticles().stream()
				.filter(p -> !"encryptor".equals(p.getMetaData("application")))
				.findFirst();

		if (!bytesParticle.isPresent()) {
			return Optional.empty();
		}

		// TODO: don't pass in maps, utilize a metadata builder?
		Map<String, Object> metaData = new HashMap<>();
		metaData.put("timestamp", atom.getTimestamp());
		metaData.put("signatures", atom.getSignatures());

		bytesParticle.ifPresent(p -> metaData.compute("application", (k, v) -> p.getMetaData("application")));

		final Optional<MessageParticle> encryptorParticle = atom.getDataParticles().stream()
				.filter(p -> "encryptor".equals(p.getMetaData("application")))
				.findAny();
		metaData.put("encrypted", encryptorParticle.isPresent());

		final Encryptor encryptor;
		if (encryptorParticle.isPresent()) {
			JsonArray protectorsJson = JSON_PARSER.parse(
					new String(encryptorParticle.get().getQuarkOrError(DataQuark.class).getBytes(),
							StandardCharsets.UTF_8)).getAsJsonArray();
			List<EncryptedPrivateKey> protectors = new ArrayList<>();
			protectorsJson.forEach(protectorJson -> protectors.add(EncryptedPrivateKey.fromBase64(protectorJson.getAsString())));
			encryptor = new Encryptor(protectors);
		} else {
			encryptor = null;
		}

		return Optional.of(Data.raw(bytesParticle.get().getQuarkOrError(DataQuark.class).getBytes(), metaData, encryptor));
	}
}
