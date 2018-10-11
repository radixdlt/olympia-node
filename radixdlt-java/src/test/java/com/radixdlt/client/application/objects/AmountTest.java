package com.radixdlt.client.application.objects;

import com.radixdlt.client.core.atoms.TokenReference;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class AmountTest {
	@Test
	public void testXRD() {
		TokenReference token = mock(TokenReference.class);
		when(token.getIso()).thenReturn("XRD");

		assertEquals("0 XRD", Amount.subUnitsOf(0, token).toString());
		assertEquals("0.00001 XRD", Amount.subUnitsOf(1, token).toString());
		assertEquals("0.1 XRD", Amount.subUnitsOf(10000, token).toString());
		assertEquals("1.1 XRD", Amount.subUnitsOf(110000, token).toString());
		assertEquals("1.23456 XRD", Amount.subUnitsOf(123456, token).toString());
	}
}
