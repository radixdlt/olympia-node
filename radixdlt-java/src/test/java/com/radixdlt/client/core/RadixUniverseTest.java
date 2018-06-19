package com.radixdlt.client.core;

import static org.junit.Assert.*;

import org.junit.Test;

public class RadixUniverseTest {

	@Test(expected = IllegalStateException.class)
	public void testRadixUniverseCreationWithoutInitialization() {
		RadixUniverse.getInstance();
	}

	@Test
	public void testRadixUniverseCreation() {
		RadixUniverse.bootstrap(Bootstrap.WINTERFELL);
		RadixUniverse universe = RadixUniverse.getInstance();
		assertNotNull(universe);
		assertNotNull(universe.getSystemPublicKey());
	}
}