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

import java.util.Optional;

/**
 * Interface for obtaining a hosts IP.
 */
public interface HostIp {
	/**
	 * Return a host IP, if possible.
	 * The underlying query mechanism is used to determine a IP address for
	 * the host.
	 * @return An optional host IP address, if one can be determined
	 */
	Optional<String> hostIp();

	/**
	 * Chain two {@link HostIp} methods together.
	 * If this object returns a non-empty host IP, then supply that,
	 * otherwise supply the result of the specified host IP.
	 *
	 * @param other The other {@link HostIp} to use if this returns empty.
	 * @return Either this if non-empty, otherwise other.
	 */
	default HostIp or(HostIp other) {
		return () -> {
			Optional<String> hap = hostIp();
			if (hap.isPresent()) {
				return hap;
			}
			return other.hostIp();
		};
	}
}