package com.radixdlt.client.assets;

import org.junit.Test;

import com.radixdlt.client.core.address.EUID;

import static org.junit.Assert.assertEquals;

import java.math.BigInteger;

public class AmountTest {

	@Test
	public void testXRD() {
		assertEquals("0 TEST", Amount.subUnitsOf(0, Asset.XRD).toString());
		assertEquals("0.00001 TEST", Amount.subUnitsOf(1, Asset.XRD).toString());
		assertEquals("0.1 TEST", Amount.subUnitsOf(10000, Asset.XRD).toString());
		assertEquals("1.1 TEST", Amount.subUnitsOf(110000, Asset.XRD).toString());
		assertEquals("1.23456 TEST", Amount.subUnitsOf(123456, Asset.XRD).toString());
	}

	@Test
	public void testPOW() {
		assertEquals("0 POW", Amount.subUnitsOf(0, Asset.POW).toString());
		assertEquals("11 POW", Amount.subUnitsOf(11, Asset.POW).toString());
		assertEquals("12345 POW", Amount.subUnitsOf(12345, Asset.POW).toString());
	}

	@Test
	public void testUnusualSubUnits() {
		// 1 foot = 12 inches
		final Asset foot = new Asset("FOOT", 12, new EUID(BigInteger.valueOf("FOOT".hashCode())));
		assertEquals("0 FOOT", Amount.subUnitsOf(0, foot).toString());
		assertEquals("1/12 FOOT", Amount.subUnitsOf(1, foot).toString());
		assertEquals("6/12 FOOT", Amount.subUnitsOf(6, foot).toString());
		assertEquals("1 FOOT", Amount.subUnitsOf(12, foot).toString());
		assertEquals("1 and 6/12 FOOT", Amount.subUnitsOf(18, foot).toString());
		assertEquals("1 and 8/12 FOOT", Amount.subUnitsOf(20, foot).toString());
	}
}
