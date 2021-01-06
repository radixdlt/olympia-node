/*
 * (C) Copyright 2020 Radix DLT Ltd
 *
 * Permission is hereby granted, free of charge, to any person obtaining a
 * copy of this software and associated documentation files (the “Software”),
 * to deal in the Software without restriction, including without limitation
 * the rights to use, copy, modify, merge, publish, distribute, sublicense,
 * and/or sell copies of the Software, and to permit persons to whom the
 * Software is furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be
 * included in all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED “AS IS”, WITHOUT WARRANTY OF ANY KIND, EXPRESS
 * OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL
 * THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING
 * FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER
 * DEALINGS IN THE SOFTWARE.
 */

package com.radixdlt.client.core;

import com.google.common.base.Suppliers;
import com.radixdlt.client.core.network.RadixNode;
import java.util.function.Supplier;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import okhttp3.Request;

/**
 * Helper class to retrieve bootstrap configuration based on standard environmental variables
 */
public final class RadixEnv {
	private static final Logger LOGGER = LogManager.getLogger(RadixEnv.class);
	private static final String RADIX_BOOTSTRAP_TRUSTED_NODE_ENV_VAR = "RADIX_BOOTSTRAP_TRUSTED_NODE";
	private static final String RADIX_BOOTSTRAP_CONFIG_ENV_VAR = "RADIX_BOOTSTRAP_CONFIG";

	private static final Supplier<BootstrapConfig> MEMOIZER = Suppliers.memoize(() -> {
		String bootstrapByTrustedNode = System.getenv(RADIX_BOOTSTRAP_TRUSTED_NODE_ENV_VAR);
		if (bootstrapByTrustedNode != null) {
			LOGGER.info("Using Bootstrap Mechanism: {}={}", RADIX_BOOTSTRAP_TRUSTED_NODE_ENV_VAR, bootstrapByTrustedNode);
			RadixNode trustedNode = new RadixNode(new Request.Builder().url(bootstrapByTrustedNode).build());
			return new BootstrapByTrustedNode(trustedNode);
		}

		String bootstrapConfigName = System.getenv(RADIX_BOOTSTRAP_CONFIG_ENV_VAR);
		if (bootstrapConfigName != null) {
			LOGGER.info("Using Bootstrap Mechanism: {}={}", RADIX_BOOTSTRAP_CONFIG_ENV_VAR, bootstrapConfigName);
			return Bootstrap.valueOf(bootstrapConfigName);
		} else {
			LOGGER.info("Using Bootstrap Mechanism: LOCALHOST_SINGLENODE");
			return Bootstrap.LOCALHOST_SINGLENODE;
		}
	});

	public static BootstrapConfig getBootstrapConfig() {
		return MEMOIZER.get();
	}

	private RadixEnv() {
		throw new IllegalStateException("Cannot instantiate.");
	}
}
