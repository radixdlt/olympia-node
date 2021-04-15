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

package com.radixdlt.crypto;

import java.math.BigInteger;
import java.util.Arrays;

import com.radixdlt.crypto.exception.PrivateKeyException;
import com.radixdlt.crypto.exception.PublicKeyException;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class ECKeyUtilsTest {

	@Test
	public void testGreaterOrEqualModulus() {
		byte[] modulus = ECKeyUtils.adjustArray(ECKeyUtils.domain().getN().toByteArray(), ECKeyPair.BYTES);
		assertEquals(ECKeyPair.BYTES, modulus.length);
		assertTrue(ECKeyUtils.greaterOrEqualOrder(modulus));

		byte[] goodKey = ECKeyUtils.adjustArray(ECKeyUtils.domain().getN().subtract(BigInteger.ONE).toByteArray(), ECKeyPair.BYTES);
		assertFalse(ECKeyUtils.greaterOrEqualOrder(goodKey));

		byte[] badKey = new byte[ECKeyPair.BYTES];
		Arrays.fill(badKey, (byte) 0xFF);
		assertTrue(ECKeyUtils.greaterOrEqualOrder(badKey));
	}

	@Test(expected = IllegalArgumentException.class)
    public void testGreaterOrEqualModulusFail() {
		ECKeyUtils.greaterOrEqualOrder(new byte[1]);
	}

	@Test
	public void testAdjustArray() {
		// Test that all smaller or equal lengths are padded correctly
		for (int i = 0; i <= ECKeyPair.BYTES; i += 1) {
			byte[] testArray = new byte[i];
			for (int j = 0; j < i; j += 1) {
				testArray[j] = (byte) (255 - j);
			}
			byte[] paddedArray = ECKeyUtils.adjustArray(testArray, ECKeyPair.BYTES);
			assertEquals(ECKeyPair.BYTES, paddedArray.length);
			int padding = ECKeyPair.BYTES - i;
			for (int j = 0; j < i; j += 1) {
				// Long constants because there is no assertEquals(int, int)
				assertEquals(255L - j, paddedArray[padding + j] & 0xFFL);
			}
		}
		// Test that longer length is truncated correctly
		byte[] testArray = new byte[ECKeyPair.BYTES + 1];
		for (int i = 0; i < ECKeyPair.BYTES; i += 1) {
			testArray[i + 1] = (byte) (255 - i);
		}
		byte[] truncatedArray = ECKeyUtils.adjustArray(testArray, ECKeyPair.BYTES);
		assertEquals(ECKeyPair.BYTES, truncatedArray.length);
		for (int i = 0; i < ECKeyPair.BYTES; i += 1) {
			// Long constants because there is no assertEquals(int, int)
			assertEquals(255L - i, truncatedArray[i] & 0xFFL);
		}
	}

	@Test(expected = IllegalArgumentException.class)
    public void testAdjustArrayFail() {
		// Test that longer length without leading zeros throws exception
		byte[] testArray = new byte[ECKeyPair.BYTES + 1];
		Arrays.fill(testArray, (byte) 1);
		ECKeyUtils.adjustArray(testArray, ECKeyPair.BYTES);
	}

	@Test(expected = PublicKeyException.class)
	public void testValidatePublicFailForNullInput() throws PublicKeyException {
		ECKeyUtils.validatePublic(null);
	}

	@Test(expected = PublicKeyException.class)
	public void testValidatePublicFailForEmptyInput() throws PublicKeyException {
		ECKeyUtils.validatePublic(new byte[] {});
	}

	@Test
	public void testValidatePublicPassForType2Key() throws PublicKeyException {
		var key = new byte[ECPublicKey.COMPRESSED_BYTES];
		key[0] = 0x02;
		ECKeyUtils.validatePublic(key);
	}

	@Test(expected = PublicKeyException.class)
	public void testValidatePublicFailForType2Key() throws PublicKeyException {
		var key = new byte[ECPublicKey.COMPRESSED_BYTES + 1];
		key[0] = 0x02;
		ECKeyUtils.validatePublic(key);
	}

	@Test
	public void testValidatePublicPassForType3Key() throws PublicKeyException {
		var key = new byte[ECPublicKey.COMPRESSED_BYTES];
		key[0] = 0x03;
		ECKeyUtils.validatePublic(key);
	}

	@Test(expected = PublicKeyException.class)
	public void testValidatePublicFailForType3Key() throws PublicKeyException {
		var key = new byte[ECPublicKey.COMPRESSED_BYTES + 1];
		key[0] = 0x03;
		ECKeyUtils.validatePublic(key);
	}

	@Test
	public void testValidatePublicPassForType4Key() throws PublicKeyException {
		var key = new byte[ECPublicKey.UNCOMPRESSED_BYTES];
		key[0] = 0x04;
		ECKeyUtils.validatePublic(key);
	}

	@Test(expected = PublicKeyException.class)
	public void testValidatePublicFailForType4Key() throws PublicKeyException {
		var key = new byte[ECPublicKey.UNCOMPRESSED_BYTES + 1];
		key[0] = 0x04;
		ECKeyUtils.validatePublic(key);
	}

	@Test(expected = PublicKeyException.class)
	public void testValidatePublicFailForUnknownTypeKey() throws PublicKeyException {
		var key = new byte[ECPublicKey.UNCOMPRESSED_BYTES];
		key[0] = 0x05;
		ECKeyUtils.validatePublic(key);
	}

	@Test(expected = PrivateKeyException.class)
	public void testValidatePrivateFailForNullInput() throws PrivateKeyException {
		ECKeyUtils.validatePrivate(null);
	}

	@Test(expected = PrivateKeyException.class)
	public void testValidatePrivateFailForEmptyInput() throws PrivateKeyException {
		ECKeyUtils.validatePrivate(new byte[] {});
	}

	@Test(expected = PrivateKeyException.class)
	public void testValidatePrivateFailForShortInput() throws PrivateKeyException {
		ECKeyUtils.validatePrivate(new byte[ECKeyPair.BYTES - 1]);
	}

	@Test(expected = PrivateKeyException.class)
	public void testValidatePrivateFailForZeroBytes() throws PrivateKeyException {
		ECKeyUtils.validatePrivate(new byte[ECKeyPair.BYTES]);
	}

	@Test(expected = PrivateKeyException.class)
	public void testValidatePrivateFailForIncorrectOrder() throws PrivateKeyException {
		var key = new byte[ECKeyPair.BYTES];
		Arrays.fill(key, (byte) -1);
		ECKeyUtils.validatePrivate(key);
	}
}
