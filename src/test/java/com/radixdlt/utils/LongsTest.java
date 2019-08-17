package com.radixdlt.utils;

import org.bouncycastle.util.Arrays;
import org.junit.Assert;
import org.junit.Test;

import static org.junit.Assert.*;

public class LongsTest {
	@Test
	public void testLongsToBytes() {
		byte[] b1 = Longs.toByteArray(1L);
		byte[] b2 = Longs.toByteArray(2L);
		long[] longs = Longs.fromBytes(Arrays.concatenate(b1, b2));
		Assert.assertEquals(longs[0], 1L);
		Assert.assertEquals(longs[1], 2L);
	}
}