/*
 * (C) Copyright 2021 Radix DLT Ltd
 *
 * Radix DLT Ltd licenses this file to you under the Apache License,
 * Version 2.0 (the "License"); you may not use this file except in
 * compliance with the License.  You may obtain a copy of the
 * License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND,
 * either express or implied.  See the License for the specific
 * language governing permissions and limitations under the License.
 *
 */

package com.radixdlt.networks;

import java.util.Locale;
import java.util.Optional;
import java.util.function.Predicate;
import java.util.stream.Stream;

public enum Network {
	MAINNET(1, "rdx", "rv", "_rr", "rn"),
	STOKENET(2, "tdx", "tv", "_tr", "tn"),
	LOCALNET(99, "ddx", "dv", "_dr", "dn"),

	RELEASENET(3, "tn3", "v3", "_r3", "n3"),
	MILESTONENET(5, "tn5", "v5", "_r5", "n5"),
	DEVOPSNET(6, "tn6", "v6", "_r6", "n6");

	private final int id;
	private final String accountHrp;
	private final String validatorHrp;
	private final String resourceHrpSuffix;
	private final String nodeHrp;

	Network(int id, String accountHrp, String validatorHrp, String resourceHrpSuffix, String nodeHrp) {
		this.id = id;
		this.accountHrp = accountHrp;
		this.validatorHrp = validatorHrp;
		this.resourceHrpSuffix = resourceHrpSuffix;
		this.nodeHrp = nodeHrp;
	}

	public String getAccountHrp() {
		return accountHrp;
	}

	public String getValidatorHrp() {
		return validatorHrp;
	}

	public String getResourceHrpSuffix() {
		return resourceHrpSuffix;
	}

	public String getNodeHrp() {
		return nodeHrp;
	}

	public int getId() {
		return id;
	}

	public Optional<String> genesisTxn() {
		return Optional.empty();
	}

	public static Optional<Network> ofId(int id) {
		return find(network -> network.id == id);
	}

	public static Optional<Network> ofName(String name) {
		var upperCaseName = name.toUpperCase(Locale.US);
		return find(network -> network.name().equals(upperCaseName));
	}

	private static Optional<Network> find(Predicate<Network> predicate) {
		return Stream.of(values())
			.filter(predicate)
			.findAny();
	}
}
