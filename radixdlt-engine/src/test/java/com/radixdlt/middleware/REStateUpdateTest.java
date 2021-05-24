package com.radixdlt.middleware;

import com.google.common.hash.HashCode;
import com.radixdlt.constraintmachine.REStateUpdate;
import com.radixdlt.crypto.HashUtils;
import nl.jqno.equalsverifier.EqualsVerifier;
import org.junit.Test;

public class REStateUpdateTest {
	@Test
	public void equalsContract() {
		EqualsVerifier.forClass(REStateUpdate.class)
			.withPrefabValues(HashCode.class, HashUtils.random256(), HashUtils.random256())
			.verify();
	}
}
