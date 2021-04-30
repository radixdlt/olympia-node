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
import com.radixdlt.identifiers.AID;

import java.util.Objects;

import static java.util.Objects.requireNonNull;

public class TransactionStatusDTO {
	private final AID txId;
	private final TransactionStatus status;

	private TransactionStatusDTO(AID txId, TransactionStatus status) {
		this.txId = txId;
		this.status = status;
	}

	@JsonCreator
	public static TransactionStatusDTO create(
		@JsonProperty(value = "txID", required = true) AID txId,
		@JsonProperty(value = "status", required = true) TransactionStatus status
	) {
		requireNonNull(txId);
		requireNonNull(status);

		return new TransactionStatusDTO(txId, status);
	}

	@Override
	public boolean equals(Object o) {
		if (this == o) {
			return true;
		}

		if (!(o instanceof TransactionStatusDTO)) {
			return false;
		}

		var that = (TransactionStatusDTO) o;
		return txId.equals(that.txId) && status == that.status;
	}

	@Override
	public int hashCode() {
		return Objects.hash(txId, status);
	}

	@Override
	public String toString() {
		return "TransactionStatusDTO(" + txId +	", " + status +	')';
	}

	public AID getTxId() {
		return txId;
	}

	public TransactionStatus getStatus() {
		return status;
	}
}
