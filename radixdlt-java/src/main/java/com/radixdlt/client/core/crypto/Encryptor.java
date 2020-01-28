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
