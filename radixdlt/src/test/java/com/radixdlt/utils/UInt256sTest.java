package com.radixdlt.utils;

import static org.junit.Assert.assertEquals;

import com.radixdlt.utils.UInt256;
import com.radixdlt.utils.UInt256s;
import java.math.BigInteger;
import org.junit.Test;

public class UInt256sTest {
	@Test
	public void when_constructing_int256_from_big_integer__values_compare_equal() {
		for (int pow2 = 0; pow2 <= 255; pow2++) {
			assertEquals(
				UInt256s.fromBigInteger(BigInteger.valueOf(2).pow(pow2)),
				UInt256.TWO.pow(pow2)
			);
		}
	}
}