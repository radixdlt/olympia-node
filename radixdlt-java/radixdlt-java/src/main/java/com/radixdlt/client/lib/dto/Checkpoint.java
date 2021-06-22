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

package com.radixdlt.client.lib.dto;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;
import java.util.Objects;

public class Checkpoint {
	private final List<String> txn;
	private final Proof proof;

	private Checkpoint(List<String> txn, Proof proof) {
		this.txn = txn;
		this.proof = proof;
	}

	@JsonCreator
	public static Checkpoint create(
		@JsonProperty(value = "txn", required = true) List<String> txn,
		@JsonProperty(value = "proof", required = true) Proof proof
	) {
		return new Checkpoint(txn, proof);
	}

	@Override
	public boolean equals(Object o) {
		if (this == o) {
			return true;
		}

		if (!(o instanceof Checkpoint)) {
			return false;
		}

		var that = (Checkpoint) o;
		return txn.equals(that.txn) && proof.equals(that.proof);
	}

	@Override
	public int hashCode() {
		return Objects.hash(txn, proof);
	}

	@Override
	public String toString() {
		return "{txn:" + txn + ", proof:" + proof + '}';
	}

	public List<String> getTxn() {
		return txn;
	}

	public Proof getProof() {
		return proof;
	}
}
