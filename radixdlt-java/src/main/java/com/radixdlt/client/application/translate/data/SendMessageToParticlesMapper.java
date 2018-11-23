package com.radixdlt.client.application.translate.data;

import com.google.gson.JsonArray;
import com.radixdlt.client.application.actions.SendMessageAction;
import com.radixdlt.client.core.atoms.particles.SpunParticle;
import com.radixdlt.client.atommodel.message.MessageParticle;
import com.radixdlt.client.atommodel.message.MessageParticle.MessageParticleBuilder;
import com.radixdlt.client.core.crypto.ECKeyPair;
import com.radixdlt.client.core.crypto.EncryptedPrivateKey;
import com.radixdlt.client.core.crypto.Encryptor;

import com.radixdlt.client.core.crypto.Encryptor.EncryptorBuilder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.Supplier;

/**
 * Maps a send message action to the particles necessary to be included in an atom.
 */
public class SendMessageToParticlesMapper {
	private final Supplier<ECKeyPair> generator;

	public SendMessageToParticlesMapper(Supplier<ECKeyPair> generator) {
		this.generator = generator;
	}

	public List<SpunParticle> map(SendMessageAction sendMessageAction) {
		if (sendMessageAction == null) {
			return Collections.emptyList();
		}

		final byte[] payload;
		final Encryptor encryptor;
		if (sendMessageAction.encrypt()) {
			EncryptorBuilder encryptorBuilder = new EncryptorBuilder();
			encryptorBuilder.addReader(sendMessageAction.getFrom().getPublicKey());
			encryptorBuilder.addReader(sendMessageAction.getTo().getPublicKey());
			ECKeyPair sharedKey = this.generator.get();

			encryptorBuilder.sharedKey(sharedKey);

			encryptor = encryptorBuilder.build();
			payload = sharedKey.getPublicKey().encrypt(sendMessageAction.getData());
		} else {
			encryptor = null;
			payload = sendMessageAction.getData();
		}

		List<SpunParticle> particles = new ArrayList<>();
		MessageParticle messageParticle = new MessageParticleBuilder()
				.payload(payload)
				.setMetaData("application", "message")
				.from(sendMessageAction.getFrom())
				.to(sendMessageAction.getTo())
				.build();
		particles.add(SpunParticle.up(messageParticle));

		if (encryptor != null) {
			JsonArray protectorsJson = new JsonArray();
			encryptor.getProtectors().stream().map(EncryptedPrivateKey::base64).forEach(protectorsJson::add);

			byte[] encryptorPayload = protectorsJson.toString().getBytes(StandardCharsets.UTF_8);
			MessageParticle encryptorParticle = new MessageParticleBuilder()
					.payload(encryptorPayload)
					.setMetaData("application", "encryptor")
					.setMetaData("contentType", "application/json")
					.from(sendMessageAction.getFrom())
					.to(sendMessageAction.getTo())
					.build();
			particles.add(SpunParticle.up(encryptorParticle));
		}

		return particles;
	}
}
