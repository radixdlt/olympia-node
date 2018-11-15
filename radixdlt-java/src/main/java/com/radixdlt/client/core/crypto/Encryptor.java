package com.radixdlt.client.core.crypto;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

public class Encryptor {
	public static class EncryptorBuilder {
		private List<ECPublicKey> readers = new ArrayList<>();
		private ECKeyPair sharedKey;
		public EncryptorBuilder() {
		}

		public int getNumReaders() {
			return readers.size();
		}

		public EncryptorBuilder sharedKey(ECKeyPair sharedKey) {
			this.sharedKey = sharedKey;
			return this;
		}

		public EncryptorBuilder addReader(ECPublicKey reader) {
			readers.add(reader);
			return this;
		}

		public Encryptor build() {
			List<EncryptedPrivateKey> protectors = readers.stream().map(sharedKey::encryptPrivateKey).collect(Collectors.toList());
			return new Encryptor(protectors);
		}
	}

	private final List<EncryptedPrivateKey> protectors;

	public Encryptor(List<EncryptedPrivateKey> protectors) {
		this.protectors = new ArrayList<>(protectors);
	}

	public List<EncryptedPrivateKey> getProtectors() {
		return Collections.unmodifiableList(protectors);
	}

	public byte[] decrypt(byte[] data, ECKeyPair accessor) throws CryptoException {
		for (EncryptedPrivateKey protector : protectors) {
			// TODO: remove exception catching
			try {
				return accessor.decrypt(data, protector);
			} catch (CryptoException e) {
			}
		}

		throw new CryptoException("Unable to decrypt any of the " + protectors.size() + " protectors.");
	}
}
