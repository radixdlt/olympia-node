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

import com.radixdlt.client.core.atoms.particles.SpunParticle;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import com.google.gson.JsonArray;
import com.google.gson.JsonParser;
import com.radixdlt.client.application.identity.Data;
import com.radixdlt.client.application.identity.RadixIdentity;
import com.radixdlt.client.application.translate.AtomToExecutedActionsMapper;
import com.radixdlt.client.application.translate.data.DecryptedMessage.EncryptionState;
import com.radixdlt.crypto.encryption.EncryptedPrivateKey;
import com.radixdlt.crypto.encryption.Encryptor;
import com.radixdlt.crypto.exception.CryptoException;
import com.radixdlt.identifiers.RadixAddress;
import com.radixdlt.client.atommodel.message.MessageParticle;
import com.radixdlt.client.core.atoms.Atom;

import io.reactivex.Observable;
import io.reactivex.Single;

/**
 * Maps an atom to some number of sent message actions and decrypted.
 */
public class AtomToDecryptedMessageMapper implements AtomToExecutedActionsMapper<DecryptedMessage> {
	private static final JsonParser JSON_PARSER = new JsonParser();

	@Override
	public Class<DecryptedMessage> actionClass() {
		return DecryptedMessage.class;
	}

	@Override
	public Observable<DecryptedMessage> map(Atom atom, RadixIdentity identity) {
		final Optional<MessageParticle> bytesParticle = atom.spunParticles()
			.map(SpunParticle::getParticle)
			.filter(p -> p instanceof MessageParticle)
			.map(p -> (MessageParticle) p)
			.filter(p -> !"encryptor".equals(p.getMetaData("application")))
			.findFirst();

		if (!bytesParticle.isPresent()) {
			return Observable.empty();
		}

		// TODO: don't pass in maps, utilize a metadata builder?
		Map<String, Object> metaData = new HashMap<>();
		metaData.put("signatures", atom.getSignatures());

		bytesParticle.ifPresent(p -> metaData.compute("application", (k, v) -> p.getMetaData("application")));

		final Optional<MessageParticle> encryptorParticle = atom.spunParticles()
			.map(SpunParticle::getParticle)
			.filter(p -> p instanceof MessageParticle)
			.map(p -> (MessageParticle) p)
			.filter(p -> "encryptor".equals(p.getMetaData("application")))
			.findAny();
		metaData.put("encrypted", encryptorParticle.isPresent());

		final Encryptor encryptor;
		if (encryptorParticle.isPresent()) {
			JsonArray protectorsJson = JSON_PARSER.parse(
				new String(encryptorParticle.get().getBytes(),
					StandardCharsets.UTF_8)).getAsJsonArray();
			List<EncryptedPrivateKey> protectors = new ArrayList<>();
			protectorsJson.forEach(protectorJson -> protectors.add(EncryptedPrivateKey.fromBase64(protectorJson.getAsString())));
			encryptor = new Encryptor(protectors);
		} else {
			encryptor = null;
		}

		RadixAddress from = bytesParticle.get().getFrom();
		RadixAddress to = bytesParticle.get().getTo();

		final byte[] bytes = bytesParticle.get().getBytes();
		final Data data = Data.raw(bytes, metaData, encryptor);

		return identity.decrypt(data)
			.map(u -> {
				final EncryptionState encryptionState = encryptorParticle.isPresent()
					? EncryptionState.DECRYPTED : EncryptionState.NOT_ENCRYPTED;
				return new DecryptedMessage(u.getData(), from, to, encryptionState, bytesParticle.get().euid());
			})
			.onErrorResumeNext(e -> {
				if (e instanceof CryptoException) {
					return Single.just(
						new DecryptedMessage(bytes, from, to, EncryptionState.CANNOT_DECRYPT, bytesParticle.get().euid())
					);
				} else {
					return Single.error(e);
				}
			})
			.toObservable();
	}

}
