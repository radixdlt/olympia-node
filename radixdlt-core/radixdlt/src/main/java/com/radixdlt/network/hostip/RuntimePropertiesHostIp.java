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
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.net.HostAndPort;
import com.radixdlt.properties.RuntimeProperties;

/**
 * Query for a public IP address from {@link RuntimeProperties}.
 */
final class RuntimePropertiesHostIp implements HostIp {
	private static final Logger log = LogManager.getLogger();

	@VisibleForTesting
	static final String HOST_IP_PROPERTY = "network.host_ip";

	private final String value;

	RuntimePropertiesHostIp(RuntimeProperties properties) {
		String hostProperty = properties.get(HOST_IP_PROPERTY, "");
		this.value = hostProperty == null ? "" : hostProperty.trim();
	}

	static HostIp create(RuntimeProperties properties) {
		return new RuntimePropertiesHostIp(properties);
	}

	@Override
	public Optional<String> hostIp() {
		if (!this.value.isEmpty()) {
			try {
				InetAddress address = InetAddress.getByName(this.value);
				HostAndPort hap = HostAndPort.fromHost(address.getHostAddress());
				log.info("Found address {}", hap);
				return Optional.of(hap.getHost());
			} catch (UnknownHostException | IllegalArgumentException e) {
				log.warn("Property {} is invalid: '{}'", HOST_IP_PROPERTY, this.value);
			}
		}
		log.info("No suitable address found");
		return Optional.empty();
	}
}