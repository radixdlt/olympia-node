package com.radixdlt.client.core;

import com.radixdlt.client.core.network.RadixNode;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import okhttp3.Request;

/**
 * Helper class to retrieve bootstrap configuration based on standard environmental variables
 */
public final class RadixEnv {
	private static final String RADIX_BOOTSTRAP_TRUSTED_NODE_ENV_VAR = "RADIX_BOOTSTRAP_TRUSTED_NODE";
	private static final String RADIX_BOOTSTRAP_CONFIG_ENV_VAR = "RADIX_BOOTSTRAP_CONFIG";

	private static final ConcurrentMap<String, BootstrapConfig> MEMOIZER = new ConcurrentHashMap<>();

	public static BootstrapConfig getBootstrapConfig() {
		return MEMOIZER.computeIfAbsent("", s -> {
			String bootstrapByTrustedNode = System.getenv(RADIX_BOOTSTRAP_TRUSTED_NODE_ENV_VAR);
			if (bootstrapByTrustedNode != null) {
				RadixNode trustedNode = new RadixNode(new Request.Builder().url(bootstrapByTrustedNode).build());
				return new BootstrapByTrustedNode(trustedNode);
			}

			String bootstrapConfigName = System.getenv(RADIX_BOOTSTRAP_CONFIG_ENV_VAR);
			if (bootstrapConfigName != null) {
				return Bootstrap.valueOf(bootstrapConfigName);
			} else {
				return Bootstrap.LOCALHOST;
			}
		});
	}

	private RadixEnv() {
		throw new IllegalStateException("Cannot instantiate.");
	}
}
