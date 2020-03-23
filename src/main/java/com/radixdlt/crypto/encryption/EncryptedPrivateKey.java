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

import org.bouncycastle.util.encoders.Base64;

import java.util.Arrays;

public class EncryptedPrivateKey {
	private final byte[] encryptedPrivateKey;

	public static EncryptedPrivateKey fromBase64(String base64) {
		return new EncryptedPrivateKey(Base64.decode(base64));
	}

	public EncryptedPrivateKey(byte[] encryptedPrivateKey) {
		this.encryptedPrivateKey = encryptedPrivateKey;
	}

	public String base64() {
		return Base64.toBase64String(encryptedPrivateKey);
	}

	public byte[] toByteArray() {
		return Arrays.copyOf(encryptedPrivateKey, encryptedPrivateKey.length);
	}
}
