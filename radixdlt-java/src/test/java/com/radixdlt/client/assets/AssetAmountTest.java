package com.radixdlt.client.assets;

import org.junit.Test;

import com.radixdlt.client.core.address.EUID;

import static org.junit.Assert.assertEquals;

import java.math.BigInteger;

public class AssetAmountTest {

	@Test
	public void testXRD() {
		assertEquals("0 TEST", new AssetAmount(Asset.XRD, 0).toString());
		assertEquals("0.00001 TEST", new AssetAmount(Asset.XRD, 1).toString());
		assertEquals("0.1 TEST", new AssetAmount(Asset.XRD, 10000).toString());
		assertEquals("1.1 TEST", new AssetAmount(Asset.XRD, 110000).toString());
		assertEquals("1.23456 TEST", new AssetAmount(Asset.XRD, 123456).toString());
	}

	@Test
	public void testPOW() {
		assertEquals("0 POW", new AssetAmount(Asset.POW, 0).toString());
		assertEquals("11 POW", new AssetAmount(Asset.POW, 11).toString());
		assertEquals("12345 POW", new AssetAmount(Asset.POW, 12345).toString());
	}

	@Test
	public void testUnusualSubUnits() {
		// 1 foot = 12 inches
		final Asset foot = new Asset("FOOT", 12, new EUID(BigInteger.valueOf("TEST".hashCode())));
		assertEquals("0 FOOT", new AssetAmount(foot, 0).toString());
		assertEquals("1/12 FOOT", new AssetAmount(foot, 1).toString());
		assertEquals("6/12 FOOT", new AssetAmount(foot, 6).toString());
		assertEquals("1 FOOT", new AssetAmount(foot, 12).toString());
		assertEquals("1 and 6/12 FOOT", new AssetAmount(foot, 18).toString());
		assertEquals("1 and 8/12 FOOT", new AssetAmount(foot, 20).toString());
	}
}
