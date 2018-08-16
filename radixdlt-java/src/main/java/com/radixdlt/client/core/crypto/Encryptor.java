package com.radixdlt.client.core.crypto;

import java.util.ArrayList;
import java.util.List;

public class Encryptor {
	private final List<EncryptedPrivateKey> protectors;

	public Encryptor(List<EncryptedPrivateKey> protectors) {
		this.protectors = new ArrayList<>(protectors);
	}

	public List<EncryptedPrivateKey> getProtectors() {
		return protectors;
	}

	public byte[] decrypt(byte[] data, ECKeyPair accessor) throws CryptoException {
		for (EncryptedPrivateKey protector : protectors) {
			// TODO: remove exception catching
			try {
				return accessor.decrypt(data, protector);
			} catch (MacMismatchException e) {
			}
		}

		throw new CryptoException("Unable to decrypt any of the " + protectors.size() + " protectors.");
	}
}
