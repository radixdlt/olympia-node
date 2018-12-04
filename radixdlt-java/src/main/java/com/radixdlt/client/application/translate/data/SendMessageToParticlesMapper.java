package com.radixdlt.client.application.translate.data;

import com.google.gson.JsonArray;
import com.radixdlt.client.application.translate.Action;
import com.radixdlt.client.application.translate.ActionToParticlesMapper;
import com.radixdlt.client.atommodel.accounts.RadixAddress;
import com.radixdlt.client.core.atoms.particles.SpunParticle;
import com.radixdlt.client.atommodel.message.MessageParticle;
import com.radixdlt.client.atommodel.message.MessageParticle.MessageParticleBuilder;
import com.radixdlt.client.core.crypto.ECKeyPair;
import com.radixdlt.client.core.crypto.ECPublicKey;
import com.radixdlt.client.core.crypto.EncryptedPrivateKey;
import com.radixdlt.client.core.crypto.Encryptor;

import com.radixdlt.client.core.crypto.Encryptor.EncryptorBuilder;
import io.reactivex.Observable;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Stream;

/**
 * Maps a send message action to the particles necessary to be included in an atom.
 */
public class SendMessageToParticlesMapper implements ActionToParticlesMapper {

	/**
	 * A module capable of creating new securely random ECKeyPairs
	 */
	private final Supplier<ECKeyPair> keyPairGenerator;

	/**
	 * Function which runs every time a mapping is requested via map(action).
	 * Determines what public keys to encrypt a message with given an application level
	 * SendMessageAction.
	 */
	private final Function<SendMessageAction, Stream<ECPublicKey>> encryptionScheme;

	/**
	 * New SendMessage action mapper which by default adds both sender and receiver
	 * public keys as readers of encrypted messages
	 *
	 * @param keyPairGenerator module to be used for creating new securely random ECKeyPairs
	 */
	public SendMessageToParticlesMapper(Supplier<ECKeyPair> keyPairGenerator) {
		this(keyPairGenerator, sendMsg -> Stream.of(sendMsg.getFrom(), sendMsg.getTo()).map(RadixAddress::getPublicKey));
	}

	/**
	 * SendMessage action mapper which uses a given eckeypair generator and encryption
	 * scheme
	 *
	 * @param keyPairGenerator module to be used for creating new securely random ECKeyPairs
	 * @param encryptionScheme function to decide which public keys to encrypt wiht
	 */
	public SendMessageToParticlesMapper(Supplier<ECKeyPair> keyPairGenerator, Function<SendMessageAction, Stream<ECPublicKey>> encryptionScheme) {
		this.keyPairGenerator = keyPairGenerator;
		this.encryptionScheme = encryptionScheme;
	}

	/**
	 * If SendMessageAction is unencrypted, returns a single message particle containing the
	 * payload data.
	 *
	 * If SendMessageAction is encrypted, creates a private key encrypted by both from and to
	 * users, stores that into a message particles and then creates another message particle
	 * with the payload encrypted by the newly created private key.
	 *
	 * @param action the action to map to particles
	 * @return observable of spunparticles to be included in an atom for a given action
	 */
	@Override
	public Observable<SpunParticle> map(Action action) {
		if (!(action instanceof SendMessageAction)) {
			return Observable.empty();
		}

		SendMessageAction sendMessageAction = (SendMessageAction) action;
		List<SpunParticle> particles = new ArrayList<>();

		final byte[] payload;
		if (sendMessageAction.encrypt()) {
			EncryptorBuilder encryptorBuilder = new EncryptorBuilder();
			encryptionScheme.apply(sendMessageAction).forEach(encryptorBuilder::addReader);

			ECKeyPair sharedKey = this.keyPairGenerator.get();

			encryptorBuilder.sharedKey(sharedKey);

			Encryptor encryptor = encryptorBuilder.build();

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

			payload = sharedKey.getPublicKey().encrypt(sendMessageAction.getData());
		} else {
			payload = sendMessageAction.getData();
		}

		MessageParticle messageParticle = new MessageParticleBuilder()
				.payload(payload)
				.setMetaData("application", "message")
				.from(sendMessageAction.getFrom())
				.to(sendMessageAction.getTo())
				.build();
		particles.add(SpunParticle.up(messageParticle));

		return Observable.fromIterable(particles);
	}
}
