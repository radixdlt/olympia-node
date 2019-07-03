package com.radixdlt.constraintmachine;

import org.junit.Test;

import nl.jqno.equalsverifier.EqualsVerifier;

public class CMErrorTest {

	@Test
	public void testEquals() {
		EqualsVerifier.forClass(CMError.class).verify();
	}

}
