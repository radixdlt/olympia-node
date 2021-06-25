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

package com.radixdlt.universe;

import com.radixdlt.atom.Txn;

import java.util.Optional;

public enum Network {
	MAINNET(1, "rdx"),
	TESTNET(2, "tdx"),
	LOCALNET(99, "ldx");

	private final int id;
	private final String accountHrp;

	Network(int id, String accountHrp) {
		this.id = id;
		this.accountHrp = accountHrp;
	}

	public String getAccountHrp() {
		return accountHrp;
	}

	public int getId() {
		return id;
	}

	public Optional<Txn> genesisTxn() {
		return Optional.empty();
	}

	public static Optional<Network> ofId(int id) {
		for (var network : Network.values()) {
			if (network.id == id) {
				return Optional.of(network);
			}
		}

		return Optional.empty();
	}
}
