package com.radixdlt.middleware;

import com.google.common.hash.HashCode;
import com.radixdlt.atom.SpunParticle;
import com.radixdlt.crypto.HashUtils;
import nl.jqno.equalsverifier.EqualsVerifier;
import nl.jqno.equalsverifier.Warning;
import org.junit.Test;

public class SpunParticleTest {
	@Test
	public void equalsContract() {
		EqualsVerifier.forClass(SpunParticle.class)
				.suppress(Warning.NONFINAL_FIELDS) // "spin" can't be final due to @JsonProperty setter
				.withPrefabValues(HashCode.class, HashUtils.random256(), HashUtils.random256())
				.verify();
	}
}
