package com.radixdlt.constraintmachine;

import nl.jqno.equalsverifier.EqualsVerifier;
import org.junit.Test;

public class CMErrorTest {

	@Test
	public void testEquals() {
		EqualsVerifier.forClass(CMError.class).verify();
	}

}
