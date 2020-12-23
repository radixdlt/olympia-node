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

import com.radixdlt.crypto.exception.CryptOperationException;
import org.bouncycastle.crypto.BufferedBlockCipher;
import org.bouncycastle.crypto.CipherParameters;
import org.bouncycastle.crypto.InvalidCipherTextException;
import org.bouncycastle.crypto.engines.AESEngine;
import org.bouncycastle.crypto.modes.CBCBlockCipher;
import org.bouncycastle.crypto.paddings.PKCS7Padding;
import org.bouncycastle.crypto.paddings.PaddedBufferedBlockCipher;
import org.bouncycastle.crypto.params.KeyParameter;
import org.bouncycastle.crypto.params.ParametersWithIV;

import com.radixdlt.SecurityCritical;
import com.radixdlt.SecurityCritical.SecurityKind;

import java.util.Arrays;

@SecurityCritical({ SecurityKind.PK_DECRYPT, SecurityKind.PK_ENCRYPT })
public final class Crypt {

	private Crypt() {
		throw new IllegalStateException("Can't construct");
	}

	static byte[] encrypt(byte[] iv, byte[] data, byte[] keyE) throws CryptOperationException {
		return crypt(CryptOperation.ENCRYPT, iv, data, keyE);
	}

	static byte[] decrypt(byte[] iv, byte[] data, byte[] keyE) throws CryptOperationException {
		return crypt(CryptOperation.DECRYPT, iv, data, keyE);
	}

	private static byte[] crypt(CryptOperation operation, byte[] iv, byte[] data, byte[] keyE) throws CryptOperationException {
		try {
			BufferedBlockCipher cipher = makeBlockCipher(operation.isEncryption(), iv, keyE);

			byte[] buffer = new byte[cipher.getOutputSize(data.length)];

			int length = cipher.processBytes(data, 0, data.length, buffer, 0);
			length += cipher.doFinal(buffer, length);

			if (length < buffer.length) {
				return Arrays.copyOfRange(buffer, 0, length);
			}

			return buffer;
		} catch (InvalidCipherTextException e) {
			throw new CryptOperationException(operation, e);
		}
	}

	private static BufferedBlockCipher makeBlockCipher(boolean encrypt, byte[] iv, byte[] keyE) {
		BufferedBlockCipher cipher = new PaddedBufferedBlockCipher(
				new CBCBlockCipher(new AESEngine()),
				new PKCS7Padding()
		);
		CipherParameters params = new ParametersWithIV(new KeyParameter(keyE), iv);
		cipher.init(encrypt, params);
		return cipher;
	}
}
