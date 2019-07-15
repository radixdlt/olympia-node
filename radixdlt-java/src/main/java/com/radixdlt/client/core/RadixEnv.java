package com.radixdlt.client.core;

import com.google.common.base.Suppliers;
import com.radixdlt.client.core.network.RadixNode;
import java.util.function.Supplier;
import okhttp3.Request;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Helper class to retrieve bootstrap configuration based on standard environmental variables
 */
public final class RadixEnv {
	private static final Logger LOGGER = LoggerFactory.getLogger(RadixEnv.class);
	private static final String RADIX_BOOTSTRAP_TRUSTED_NODE_ENV_VAR = "RADIX_BOOTSTRAP_TRUSTED_NODE";
	private static final String RADIX_BOOTSTRAP_CONFIG_ENV_VAR = "RADIX_BOOTSTRAP_CONFIG";

	private static final Supplier<BootstrapConfig> MEMOIZER = Suppliers.memoize(() -> {
		String bootstrapByTrustedNode = System.getenv(RADIX_BOOTSTRAP_TRUSTED_NODE_ENV_VAR);
		if (bootstrapByTrustedNode != null) {
			LOGGER.info("Using Bootstrap Mechanism: " + RADIX_BOOTSTRAP_TRUSTED_NODE_ENV_VAR + "=" + bootstrapByTrustedNode);
			RadixNode trustedNode = new RadixNode(new Request.Builder().url(bootstrapByTrustedNode).build());
			return new BootstrapByTrustedNode(trustedNode);
		}

		String bootstrapConfigName = System.getenv(RADIX_BOOTSTRAP_CONFIG_ENV_VAR);
		if (bootstrapConfigName != null) {
			LOGGER.info("Using Bootstrap Mechanism: " + RADIX_BOOTSTRAP_CONFIG_ENV_VAR + "=" + bootstrapConfigName);
			return Bootstrap.valueOf(bootstrapConfigName);
		} else {
			LOGGER.info("Using Bootstrap Mechanism: LOCALHOST");
			return Bootstrap.LOCALHOST;
		}
	});

	public static BootstrapConfig getBootstrapConfig() {
		return MEMOIZER.get();
	}

	private RadixEnv() {
		throw new IllegalStateException("Cannot instantiate.");
	}
}
