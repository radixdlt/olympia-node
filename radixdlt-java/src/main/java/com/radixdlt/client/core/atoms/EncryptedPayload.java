package com.radixdlt.client.core.atoms;

import com.radixdlt.client.core.crypto.CryptoException;
import com.radixdlt.client.core.crypto.ECKeyPair;
import com.radixdlt.client.core.crypto.Encryptor;
import java.util.Optional;

public class EncryptedPayload {
	private final Payload payload;
	private final Optional<Encryptor> encryptor;

	public EncryptedPayload(Payload payload) {
		this.payload = payload;
		this.encryptor = Optional.empty();
	}

	public EncryptedPayload(Payload payload, Encryptor encryptor) {
		this.payload = payload;
		this.encryptor = Optional.of(encryptor);
	}

	public Payload getPayload() {
		return payload;
	}

	public byte[] decrypt(ECKeyPair ecKeyPair) throws CryptoException {
		if (encryptor.isPresent()) {
			return encryptor.get().decrypt(payload.getBytes(), ecKeyPair);
		} else {
			return payload.getBytes();
		}
	}
}
