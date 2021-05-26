package com.radixdlt.statecomputer;

import com.google.common.hash.HashCode;
import com.radixdlt.crypto.HashUtils;
import nl.jqno.equalsverifier.EqualsVerifier;
import org.junit.Test;

public class TxnsCommittedToLedgerTest {
	@Test
	public void equalsContract() {
		EqualsVerifier.forClass(TxnsCommittedToLedger.class)
			.withPrefabValues(HashCode.class, HashUtils.random256(), HashUtils.random256())
			.verify();
	}
}