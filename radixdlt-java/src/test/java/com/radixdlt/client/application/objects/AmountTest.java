package com.radixdlt.client.application.objects;

import java.math.BigDecimal;
import org.junit.Test;

import static org.mockito.Mockito.mock;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.when;

public class AmountTest {
	@Test
	public void testBigDecimal() {
		Token token = mock(Token.class);
		when(token.getSubUnits()).thenReturn(1);
		assertThatThrownBy(() -> Amount.of(new BigDecimal("1.1"), token)).isInstanceOf(IllegalArgumentException.class);
		assertEquals(Amount.of(new BigDecimal("1.00"), token), Amount.of(1, token));
	}

	@Test
	public void testXRD() {
		assertEquals("0 XRD", Amount.subUnitsOf(0, Token.TEST).toString());
		assertEquals("0.00001 XRD", Amount.subUnitsOf(1, Token.TEST).toString());
		assertEquals("0.1 XRD", Amount.subUnitsOf(10000, Token.TEST).toString());
		assertEquals("1.1 XRD", Amount.subUnitsOf(110000, Token.TEST).toString());
		assertEquals("1.23456 XRD", Amount.subUnitsOf(123456, Token.TEST).toString());
	}

	@Test
	public void testPOW() {
		assertEquals("0 POW", Amount.subUnitsOf(0, Token.POW).toString());
		assertEquals("11 POW", Amount.subUnitsOf(11, Token.POW).toString());
		assertEquals("12345 POW", Amount.subUnitsOf(12345, Token.POW).toString());
	}

	@Test
	public void testUnusualSubUnits() {
		// 1 foot = 12 inches
		final Token foot = new Token("FOOT", 12);
		assertEquals("0 FOOT", Amount.subUnitsOf(0, foot).toString());
		assertEquals("1/12 FOOT", Amount.subUnitsOf(1, foot).toString());
		assertEquals("6/12 FOOT", Amount.subUnitsOf(6, foot).toString());
		assertEquals("1 FOOT", Amount.subUnitsOf(12, foot).toString());
		assertEquals("1 and 6/12 FOOT", Amount.subUnitsOf(18, foot).toString());
		assertEquals("1 and 8/12 FOOT", Amount.subUnitsOf(20, foot).toString());
	}
}
