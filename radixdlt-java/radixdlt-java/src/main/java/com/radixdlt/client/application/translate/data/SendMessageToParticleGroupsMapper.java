/*
 * (C) Copyright 2020 Radix DLT Ltd
 *
 * Permission is hereby granted, free of charge, to any person obtaining a
 * copy of this software and associated documentation files (the “Software”),
 * to deal in the Software without restriction, including without limitation
 * the rights to use, copy, modify, merge, publish, distribute, sublicense,
 * and/or sell copies of the Software, and to permit persons to whom the
 * Software is furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be
 * included in all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED “AS IS”, WITHOUT WARRANTY OF ANY KIND, EXPRESS
 * OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL
 * THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING
 * FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER
 * DEALINGS IN THE SOFTWARE.
 */

package com.radixdlt.client.application.translate.data;

import com.google.gson.JsonArray;
import com.radixdlt.client.application.translate.StatelessActionToParticleGroupsMapper;
import com.radixdlt.crypto.encryption.EncryptedPrivateKey;
import com.radixdlt.crypto.encryption.Encryptor;
import com.radixdlt.crypto.exception.ECIESException;
import com.radixdlt.identifiers.RadixAddress;
import com.radixdlt.client.atommodel.message.MessageParticle;
import com.radixdlt.client.atommodel.message.MessageParticle.MessageParticleBuilder;
import com.radixdlt.client.core.atoms.ParticleGroup;
import com.radixdlt.client.core.atoms.particles.SpunParticle;
import com.radixdlt.crypto.ECKeyPair;
import com.radixdlt.crypto.ECPublicKey;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Stream;

/**
 * Maps a send message action to the particles necessary to be included in an atom.
 */
public class SendMessageToParticleGroupsMapper implements StatelessActionToParticleGroupsMapper<SendMessageAction> {

	/**
	 * A module capable of creating new securely random ECKeyPairs
	 */
	private final Supplier<ECKeyPair> keyPairGenerator;

	/**
	 * Function which runs every time a mapping is requested via mapToParticles(action).
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
	public SendMessageToParticleGroupsMapper(Supplier<ECKeyPair> keyPairGenerator) {
		this(keyPairGenerator, sendMsg -> Stream.of(sendMsg.getFrom(), sendMsg.getTo()).map(RadixAddress::getPublicKey));
	}

	/**
	 * SendMessage action mapper which uses a given eckeypair generator and encryption
	 * scheme
	 *
	 * @param keyPairGenerator module to be used for creating new securely random ECKeyPairs
	 * @param encryptionScheme function to decide which public keys to encrypt wiht
	 */
	public SendMessageToParticleGroupsMapper(
		Supplier<ECKeyPair> keyPairGenerator,
		Function<SendMessageAction,
		Stream<ECPublicKey>> encryptionScheme
	) {
		this.keyPairGenerator = keyPairGenerator;
		this.encryptionScheme = encryptionScheme;
	}

	/**
	 * If SendMessageAction is unencrypted, returns a single message particle containing the
	 * payload data.
	 * <p>
	 * If SendMessageAction is encrypted, creates a private key encrypted by both from and to
	 * users, stores that into a message particles and then creates another message particle
	 * with the payload encrypted by the newly created private key.
	 *
	 * @param action the action to mapToParticles to particles
	 * @return observable of spunparticles to be included in an atom for a given action
	 */
	@Override
	public List<ParticleGroup> mapToParticleGroups(SendMessageAction action) {
		List<SpunParticle> particles = new ArrayList<>();

		final byte[] payload;
		if (action.encrypt()) {
			Encryptor.EncryptorBuilder encryptorBuilder = new Encryptor.EncryptorBuilder();
			this.encryptionScheme.apply(action).forEach(encryptorBuilder::addReader);

			ECKeyPair sharedKey = this.keyPairGenerator.get();

			encryptorBuilder.sharedKey(sharedKey);

			Encryptor encryptor = encryptorBuilder.build();

			JsonArray protectorsJson = new JsonArray();
			encryptor.getProtectors().stream().map(EncryptedPrivateKey::base64).forEach(protectorsJson::add);

			byte[] encryptorPayload = protectorsJson.toString().getBytes(StandardCharsets.UTF_8);
			MessageParticle encryptorParticle = new MessageParticleBuilder()
					.payload(encryptorPayload)
					.metaData("application", "encryptor")
					.metaData("contentType", "application/json")
					.from(action.getFrom())
					.to(action.getTo())
					.build();
			particles.add(SpunParticle.up(encryptorParticle));

			try {
				payload = sharedKey.getPublicKey().encrypt(action.getData());
			} catch (ECIESException e) {
				throw new IllegalStateException("Expected to always be able to encrypt", e);
			}
		} else {
			payload = action.getData();
		}

		MessageParticle messageParticle = new MessageParticleBuilder()
				.payload(payload)
				.metaData("application", "message")
				.from(action.getFrom())
				.to(action.getTo())
				.build();
		particles.add(SpunParticle.up(messageParticle));

		return Collections.singletonList(ParticleGroup.of(particles));
	}
}
