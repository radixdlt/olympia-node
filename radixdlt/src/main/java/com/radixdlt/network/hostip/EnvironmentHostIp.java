/*
 * (C) Copyright 2020 Radix DLT Ltd
 *
 * Radix DLT Ltd licenses this file to you under the Apache License,
 * Version 2.0 (the "License"); you may not use this file except in
 * compliance with the License.  You may obtain a copy of the
 * License at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND,
 * either express or implied.  See the License for the specific
 * language governing permissions and limitations under the License.
 */

package com.radixdlt.network.hostip;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.Optional;
import java.util.function.Supplier;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Suppliers;
import com.google.common.net.HostAndPort;

/**
 * Query for a public IP address from environment variable.
 */
final class EnvironmentHostIp implements HostIp {
	private static final Logger log = LogManager.getLogger();

	@VisibleForTesting
	static final String ENV_VAR = "RADIXDLT_HOST_IP_ADDRESS";

	private final Supplier<Optional<String>> result = Suppliers.memoize(() -> hostIp(System.getenv(ENV_VAR)));

	static HostIp create() {
		return new EnvironmentHostIp();
	}

	@Override
	public Optional<String> hostIp() {
		return result.get();
	}

	// Broken out for testing as environment is immutable from Java runtime
	@VisibleForTesting
	Optional<String> hostIp(String value) {
		if (value != null && !value.trim().isEmpty()) {
			try {
				InetAddress address = InetAddress.getByName(value);
				HostAndPort hap = HostAndPort.fromHost(address.getHostAddress());
				log.info("Found address {}", hap);
				return Optional.of(hap.getHost());
			} catch (UnknownHostException | IllegalArgumentException e) {
				log.warn("Environment variable {} is invalid: '{}'", ENV_VAR, value);
			}
		}
		log.info("No suitable address found");
		return Optional.empty();
	}
}