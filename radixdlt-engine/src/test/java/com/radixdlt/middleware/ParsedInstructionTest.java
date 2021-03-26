package com.radixdlt.middleware;

import com.google.common.hash.HashCode;
import com.radixdlt.atom.ParsedInstruction;
import com.radixdlt.crypto.HashUtils;
import nl.jqno.equalsverifier.EqualsVerifier;
import org.junit.Test;

public class ParsedInstructionTest {
	@Test
	public void equalsContract() {
		EqualsVerifier.forClass(ParsedInstruction.class)
			.withPrefabValues(HashCode.class, HashUtils.random256(), HashUtils.random256())
			.verify();
	}
}
