package com.radixdlt.atommodel.system;

import nl.jqno.equalsverifier.EqualsVerifier;
import nl.jqno.equalsverifier.Warning;
import org.junit.Test;

public class SystemParticleTest {
	@Test
	public void equalsContract() {
		EqualsVerifier.forClass(SystemParticle.class)
			.suppress(Warning.NONFINAL_FIELDS)
			.verify();
	}
}