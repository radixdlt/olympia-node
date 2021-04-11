package com.radixdlt.middleware;

import com.google.common.hash.HashCode;
import com.radixdlt.constraintmachine.REParsedInstruction;
import com.radixdlt.crypto.HashUtils;
import nl.jqno.equalsverifier.EqualsVerifier;
import org.junit.Test;

public class REParsedInstructionTest {
	@Test
	public void equalsContract() {
		EqualsVerifier.forClass(REParsedInstruction.class)
			.withPrefabValues(HashCode.class, HashUtils.random256(), HashUtils.random256())
			.verify();
	}
}
