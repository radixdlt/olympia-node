package com.radixdlt.middleware;

import nl.jqno.equalsverifier.EqualsVerifier;
import nl.jqno.equalsverifier.Warning;
import org.junit.Test;

public class SpunParticleTest {
	@Test
	public void equalsContract() {
		EqualsVerifier.forClass(SpunParticle.class)
				.suppress(Warning.NONFINAL_FIELDS) // "spin" can't be final due to @JsonProperty setter
				.withIgnoredFields("version") // only used for serialization
				.verify();
	}
}
