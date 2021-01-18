package com.radixdlt.mempool;

import com.google.common.hash.HashCode;
import com.radixdlt.crypto.HashUtils;
import nl.jqno.equalsverifier.EqualsVerifier;
import org.junit.Test;

public class MempoolAddTest {
	@Test
	public void equalsVerifier() {
		EqualsVerifier.forClass(MempoolAdd.class)
			.withPrefabValues(HashCode.class, HashUtils.random256(), HashUtils.random256())
			.verify();
	}
}