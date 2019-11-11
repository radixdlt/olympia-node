package com.radixdlt.crypto;

import java.math.BigInteger;
import java.util.Arrays;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class ECKeyUtilsTest {

	@Test
	public void testGreaterOrEqualModulus() {
		byte[] modulus = ECKeyUtils.adjustArray(ECKeyUtils.domain.getN().toByteArray(), ECKeyPair.BYTES);
		assertEquals(ECKeyPair.BYTES, modulus.length);
		assertTrue(ECKeyUtils.greaterOrEqualOrder(modulus));

		byte[] goodKey = ECKeyUtils.adjustArray(ECKeyUtils.domain.getN().subtract(BigInteger.ONE).toByteArray(), ECKeyPair.BYTES);
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
}
