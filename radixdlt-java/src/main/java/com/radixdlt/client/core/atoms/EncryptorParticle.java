package com.radixdlt.client.core.atoms;

import com.radixdlt.client.core.crypto.EncryptedPrivateKey;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Particle which manages an Encryptor or a private key encrypted for specified
 * readers.
 */
public class EncryptorParticle {
	private final List<EncryptedPrivateKey> protectors;

	public EncryptorParticle(List<EncryptedPrivateKey> protectors) {
		this.protectors = new ArrayList<>(protectors);
	}

	public List<EncryptedPrivateKey> getProtectors() {
		return Collections.unmodifiableList(protectors);
	}
}
