package com.radixdlt.client.core.address;

import org.junit.Test;

import static org.junit.Assert.assertNotNull;

public class RadixUniverseConfigsTestConfig {

	@Test
	public void createDevelopmentUniverseFromJson() {
		RadixUniverseConfig winterfell = RadixUniverseConfigs.getWinterfell();
		assertNotNull(winterfell);
	}
}
