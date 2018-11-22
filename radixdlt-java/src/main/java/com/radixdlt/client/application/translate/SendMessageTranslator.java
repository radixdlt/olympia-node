package com.radixdlt.client.application.translate;

import com.google.gson.JsonArray;
import com.radixdlt.client.application.actions.SendMessageAction;
import com.radixdlt.client.core.atoms.particles.SpunParticle;
import com.radixdlt.client.atommodel.message.MessageParticle;
import com.radixdlt.client.atommodel.message.MessageParticle.MessageParticleBuilder;
import com.radixdlt.client.core.crypto.EncryptedPrivateKey;
import com.radixdlt.client.core.crypto.Encryptor;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class SendMessageTranslator {
	private static final SendMessageTranslator INSTANCE = new SendMessageTranslator();

	public static SendMessageTranslator getInstance() {
		return INSTANCE;
	}

	private SendMessageTranslator() {
	}

	// TODO: figure out correct method signature here (return Single<AtomBuilder> instead?)
	public List<SpunParticle> map(SendMessageAction sendMessageAction) {
		if (sendMessageAction == null) {
			return Collections.emptyList();
		}

		byte[] payload = sendMessageAction.getData().getBytes();
		String application = (String) sendMessageAction.getData().getMetaData().get("application");

		List<SpunParticle> particles = new ArrayList<>();
		MessageParticle messageParticle = new MessageParticleBuilder()
				.payload(payload)
				.setMetaData("application", application)
				.from(sendMessageAction.getFrom())
				.to(sendMessageAction.getTo())
				.build();
		particles.add(SpunParticle.up(messageParticle));

		Encryptor encryptor = sendMessageAction.getData().getEncryptor();
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
