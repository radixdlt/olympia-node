package com.radix;

import com.radixdlt.client.core.BootstrapConfig;

public final class TestEnv {
	private static final BootstrapConfig BOOTSTRAP_CONFIG;
	static {
		String bootstrapConfigName = System.getenv("RADIX_BOOTSTRAP_CONFIG");
		if (bootstrapConfigName != null) {
			BOOTSTRAP_CONFIG = com.radixdlt.client.core.Bootstrap.valueOf(bootstrapConfigName);
		} else {
			BOOTSTRAP_CONFIG = com.radixdlt.client.core.Bootstrap.LOCALHOST;
		}
	}

	public static BootstrapConfig getBootstrapConfig() {
		return BOOTSTRAP_CONFIG;
	}

	private TestEnv() {
		throw new IllegalStateException("Cannot instantiate.");
	}
}
