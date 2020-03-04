package com.radixdlt.store;

import nl.jqno.equalsverifier.EqualsVerifier;
import nl.jqno.equalsverifier.Warning;
import org.junit.Test;

public class StoreIndexTest {
	@Test
	public void equalsContract() {
		EqualsVerifier.forClass(StoreIndex.class).verify();
	}
}
