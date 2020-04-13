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

import com.radixdlt.properties.RuntimeProperties;

/**
 * Provides a standard {@link HostIp} retriever.
 */
public final class StandardHostIp {

	private StandardHostIp() {
		throw new IllegalStateException("Can't construct");
	}

	/**
	 * Queries the {@value EnvironmentHostIp#ENV_VAR} environment variable
	 * for a host name or IP, and if that fails, uses well-known web services
	 * to determine a public IP address.
	 *
	 * @return A {@link HostIp} object from which a host address can be queried
	 */
	public static HostIp defaultHostIp(RuntimeProperties properties) {
		return
			RuntimePropertiesHostIp.create(properties)
			.or(EnvironmentHostIp.create())
			.or(NetworkQueryHostIp.create(properties));
	}
}
