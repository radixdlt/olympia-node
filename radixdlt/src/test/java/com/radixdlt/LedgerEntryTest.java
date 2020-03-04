package com.radixdlt;

import com.radixdlt.store.LedgerEntry;
import nl.jqno.equalsverifier.EqualsVerifier;
import org.junit.Test;

public class LedgerEntryTest {
	@Test
	public void equalsContract() {
		EqualsVerifier.forClass(LedgerEntry.class)
				.withIgnoredFields("content") // derived from other field(s) in use.
				.verify();
	}
}
