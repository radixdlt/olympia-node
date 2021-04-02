/*
 * (C) Copyright 2021 Radix DLT Ltd
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

package com.radixdlt.store.berkeley;

import com.radixdlt.atom.Atom;
import com.radixdlt.identifiers.AID;

import static java.util.Objects.requireNonNull;

public class FullTransaction {
	private final AID txId;
	private final Atom tx;

	private FullTransaction(AID txId, Atom tx) {
		this.tx = tx;
		this.txId = txId;
	}

	public static FullTransaction create(AID txId, Atom tx) {
		requireNonNull(txId);
		requireNonNull(tx);

		return new FullTransaction(txId, tx);
	}

	public AID getTxId() {
		return txId;
	}

	public Atom getTx() {
		return tx;
	}
}
