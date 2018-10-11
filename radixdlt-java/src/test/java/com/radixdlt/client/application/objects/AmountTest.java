package com.radixdlt.client.application.objects;

import com.radixdlt.client.core.atoms.Token;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class AmountTest {
	@Test
	public void testXRD() {
		assertEquals("0 XRD", Amount.subUnitsOf(0, Token.of("XRD")).toString());
		assertEquals("0.00001 XRD", Amount.subUnitsOf(1, Token.of("XRD")).toString());
		assertEquals("0.1 XRD", Amount.subUnitsOf(10000, Token.of("XRD")).toString());
		assertEquals("1.1 XRD", Amount.subUnitsOf(110000, Token.of("XRD")).toString());
		assertEquals("1.23456 XRD", Amount.subUnitsOf(123456, Token.of("XRD")).toString());
	}

	@Test
	public void testPOW() {
		assertEquals("0 POW", Amount.subUnitsOf(0, Token.of("POW")).toString());
		assertEquals("0.00011 POW", Amount.subUnitsOf(11, Token.of("POW")).toString());
		assertEquals("0.12345 POW", Amount.subUnitsOf(12345, Token.of("POW")).toString());
	}
}
