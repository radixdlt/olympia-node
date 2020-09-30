/*
 * (C) Copyright 2020 Radix DLT Ltd
 *
 * Radix DLT Ltd licenses this file to you under the Apache License,
 * Version 2.0 (the "License"); you may not use this file except in
 * compliance with the License.  You may obtain a copy of the
 * License at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND,
 * either express or implied.  See the License for the specific
 * language governing permissions and limitations under the License.
 */

package com.radixdlt.crypto.encryption;

import com.radixdlt.crypto.ECKeyPair;
import com.radixdlt.crypto.ECPublicKey;
import com.radixdlt.crypto.exception.ECIESException;
import com.radixdlt.crypto.exception.EncryptorException;
import com.radixdlt.crypto.exception.PrivateKeyException;
import com.radixdlt.crypto.exception.PublicKeyException;

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
			List<EncryptedPrivateKey> protectors =
				readers.stream()
						.map(sharedKey::encryptPrivateKeyWithPublicKey)
						.collect(Collectors.toList());
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

	public byte[] decrypt(byte[] data, ECKeyPair accessor) throws EncryptorException {
		for (EncryptedPrivateKey protector : protectors) {
			try {
				return accessor.decrypt(data, protector);
			} catch (PrivateKeyException | PublicKeyException | ECIESException e) {
				// exception could so that we can proceed with next `protector`
			}
		}

		throw new EncryptorException("Unable to decrypt any of the " + protectors.size() + " protectors.");
	}
}
