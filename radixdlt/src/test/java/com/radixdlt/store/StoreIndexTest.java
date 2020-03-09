package com.radixdlt.store;

import nl.jqno.equalsverifier.EqualsVerifier;
import org.junit.Test;

public class StoreIndexTest {
	@Test
	public void equalsContract() {
		EqualsVerifier.forClass(StoreIndex.class).verify();
	}
}
