package com.radixdlt.chaos;

import com.google.common.hash.HashCode;
import com.radixdlt.crypto.HashUtils;
import nl.jqno.equalsverifier.EqualsVerifier;
import org.junit.Test;

public class MessageFlooderUpdateTest {
	@Test
	public void equalsContract() {
		EqualsVerifier.forClass(MessageFlooderUpdate.class)
			.withPrefabValues(HashCode.class, HashUtils.random256(), HashUtils.random256())
			.verify();
	}
}