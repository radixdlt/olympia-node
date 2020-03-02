package com.radixdlt;

import com.radixdlt.store.LedgerEntry;
import nl.jqno.equalsverifier.EqualsVerifier;
import nl.jqno.equalsverifier.Warning;
import org.junit.Test;

public class LedgerEntryTest {
	@Test
	public void equalsContract() {
		EqualsVerifier.forClass(LedgerEntry.class).verify();
	}
}
