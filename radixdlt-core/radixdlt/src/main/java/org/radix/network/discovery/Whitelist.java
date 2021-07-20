/* Copyright 2021 Radix DLT Ltd incorporated in England.
 *
 * Licensed under the Radix License, Version 1.0 (the "License"); you may not use this
 * file except in compliance with the License. You may obtain a copy of the License at:
 *
 * radixfoundation.org/licenses/LICENSE-v1
 *
 * The Licensor hereby grants permission for the Canonical version of the Work to be
 * published, distributed and used under or by reference to the Licensor’s trademark
 * Radix ® and use of any unregistered trade names, logos or get-up.
 *
 * The Licensor provides the Work (and each Contributor provides its Contributions) on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied,
 * including, without limitation, any warranties or conditions of TITLE, NON-INFRINGEMENT,
 * MERCHANTABILITY, or FITNESS FOR A PARTICULAR PURPOSE.
 *
 * Whilst the Work is capable of being deployed, used and adopted (instantiated) to create
 * a distributed ledger it is your responsibility to test and validate the code, together
 * with all logic and performance of that code under all foreseeable scenarios.
 *
 * The Licensor does not make or purport to make and hereby excludes liability for all
 * and any representation, warranty or undertaking in any form whatsoever, whether express
 * or implied, to any entity or person, including any representation, warranty or
 * undertaking, as to the functionality security use, value or other characteristics of
 * any distributed ledger nor in respect the functioning or value of any tokens which may
 * be created stored or transferred using the Work. The Licensor does not warrant that the
 * Work or any use of the Work complies with any law or regulation in any territory where
 * it may be implemented or used or that it will be appropriate for any specific purpose.
 *
 * Neither the licensor nor any current or former employees, officers, directors, partners,
 * trustees, representatives, agents, advisors, contractors, or volunteers of the Licensor
 * shall be liable for any direct or indirect, special, incidental, consequential or other
 * losses of any kind, in tort, contract or otherwise (including but not limited to loss
 * of revenue, income or profits, or loss of use or data, or loss of reputation, or loss
 * of any economic or other opportunity of whatsoever nature or howsoever arising), arising
 * out of or in connection with (without limitation of any use, misuse, of any ledger system
 * or use made or its functionality or any performance or operation of any code or protocol
 * caused by bugs or programming or logic errors or otherwise);
 *
 * A. any offer, purchase, holding, use, sale, exchange or transmission of any
 * cryptographic keys, tokens or assets created, exchanged, stored or arising from any
 * interaction with the Work;
 *
 * B. any failure in a transmission or loss of any token or assets keys or other digital
 * artefacts due to errors in transmission;
 *
 * C. bugs, hacks, logic errors or faults in the Work or any communication;
 *
 * D. system software or apparatus including but not limited to losses caused by errors
 * in holding or transmitting tokens by any third-party;
 *
 * E. breaches or failure of security including hacker attacks, loss or disclosure of
 * password, loss of private key, unauthorised use or misuse of such passwords or keys;
 *
 * F. any losses including loss of anticipated savings or other benefits resulting from
 * use of the Work or any changes to the Work (however implemented).
 *
 * You are solely responsible for; testing, validating and evaluation of all operation
 * logic, functionality, security and appropriateness of using the Work for any commercial
 * or non-commercial purpose and for any reproduction or redistribution by You of the
 * Work. You assume all risks associated with Your use of the Work and the exercise of
 * permissions under this License.
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
