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
	private static final Logger networkLog = LogManager.getLogger();

	private Set<String> parameters = new HashSet<>();

	public Whitelist(String parameters) {
		if (parameters == null) {
			return;
		}

		String[] split = parameters.split(",");

		for (String parameter : split) {
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

			output[s] = Integer.valueOf(segments[s]);
		}

		return output;
	}

	private boolean isRange(String parameter) {
		if (parameter.contains("-")) {
			return true;
		}

			return false;
		}

	private boolean isInRange(String parameter, String address) {
		String[] hosts = parameter.split("-");

		if (hosts.length != 2) {
			throw new IllegalStateException("Range is invalid");
		}

		int[] target = convert(address);
		int[] low = convert(hosts[0]);
		int[] high = convert(hosts[1]);

		if (low.length != high.length || target.length != low.length) {
			return false;
		}

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

	private boolean isMask(String parameter) {
		if (parameter.contains("*") || parameter.contains("::")) {
			return true;
		}

			return false;
		}

	private boolean isMasked(String parameter, String address) {
		int[] target = convert(address);
		int[] mask = convert(parameter);

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
			String hostAddress = InetAddress.getByName(hostName).getHostAddress();
			for (String parameter : parameters) {
				if (parameter.equalsIgnoreCase(hostName)
					|| isRange(parameter) && isInRange(parameter, hostAddress)
					|| isMask(parameter) && isMasked(parameter, hostAddress)) {
					return true;
				}
			}
		} catch (UnknownHostException ex) {
			networkLog.error("While checking whitelist", ex);
		}

		return false;
	}

	public static Whitelist from(RuntimeProperties properties) {
		return new Whitelist(properties.get("network.whitelist", ""));
	}
}
