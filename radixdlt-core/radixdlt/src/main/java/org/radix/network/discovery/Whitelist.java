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

package org.radix.network.discovery;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

import com.radixdlt.properties.RuntimeProperties;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class Whitelist {
	private static final Logger logger = LogManager.getLogger();

	private final Set<String> parameters = new HashSet<>();

	public Whitelist(String parameters) {
		if (parameters == null) {
			return;
		}

		for (var parameter : parameters.split(",")) {
			if (parameter.trim().length() == 0) {
				continue;
			}

			this.parameters.add(parameter.trim());
		}
	}

	private int[] convert(String host) {
		String[] segments;
		int[] output;

		if (host.contains(".")) {            // IPV4 //
			output = new int[4];
			segments = host.split("\\.");
		} else if (host.contains(":")) {    // IPV6 //
			output = new int[8];
			segments = host.split(":");
		} else {
			return new int[]{0, 0, 0, 0};
		}

		Arrays.fill(output, Integer.MAX_VALUE);
		for (int s = 0; s < segments.length; s++) {
			if (segments[s].equalsIgnoreCase("*")) {
				break;
			}

			output[s] = Integer.parseInt(segments[s]);
		}

		return output;
	}

	private boolean isInRange(String parameter, String address) {
		if (!parameter.contains("-")) {
			return false;
		}

		var hosts = parameter.split("-");

		if (hosts.length != 2) {
			throw new IllegalStateException("Range is invalid");
		}

		var target = convert(address);
		var low = convert(hosts[0]);
		var high = convert(hosts[1]);

		if (low.length != high.length || target.length != low.length) {
			return false;
		}

		//TODO: check if everything is OK with the logic inside, as `if (low[s] < high[s])`part does not look right
		for (int s = 0; s < low.length; s++) {
			if (low[s] < high[s]) {
				int[] swap = low;
				low = high;
				high = swap;
				break;
			}

			if (target[s] < low[s] || target[s] > high[s]) {
				return false;
			}
		}

		return true;
	}

	private boolean isMasked(String parameter, String address) {
		if (!(parameter.contains("*") || parameter.contains("::"))) {
			return false;
		}

		var target = convert(address);
		var mask = convert(parameter);

		if (target.length != mask.length) {
			return false;
		}

		for (int s = 0; s < mask.length; s++) {
			if (mask[s] == Integer.MAX_VALUE) {
				return true;
			} else if (target[s] != mask[s]) {
				return false;
			}
		}

		return false;
	}

	public boolean isWhitelisted(String hostName) {
		if (parameters.isEmpty()) {
			return true;
		}

		try {
			var hostAddress = InetAddress.getByName(hostName).getHostAddress();

			for (var parameter : parameters) {
				if (parameter.equalsIgnoreCase(hostName) || isInRange(parameter, hostAddress) || isMasked(parameter, hostAddress)) {
					return true;
				}
			}
		} catch (UnknownHostException ex) {
			logger.error("While checking whitelist", ex);
		}

		return false;
	}

	public static Whitelist from(RuntimeProperties properties) {
		return new Whitelist(properties.get("network.whitelist", ""));
	}
}
