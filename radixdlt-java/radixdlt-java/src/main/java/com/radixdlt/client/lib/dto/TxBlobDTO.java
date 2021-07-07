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

import org.bouncycastle.util.encoders.Hex;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.radixdlt.identifiers.AID;

import java.util.Arrays;
import java.util.Objects;

import static java.util.Objects.requireNonNull;

public final class TxBlobDTO {
	private final AID txId;
	private final byte[] blob;

	private TxBlobDTO(AID txId, byte[] blob) {
		this.txId = txId;
		this.blob = blob;
	}

	@JsonCreator
	public static TxBlobDTO create(
		@JsonProperty(value = "txID", required = true) AID txId,
		@JsonProperty(value = "blob", required = true) String blob
	) {
		requireNonNull(txId);
		requireNonNull(blob);

		return new TxBlobDTO(txId, Hex.decode(blob));
	}

	@Override
	public boolean equals(Object o) {
		if (this == o) {
			return true;
		}

		if (!(o instanceof TxBlobDTO)) {
			return false;
		}

		var txBlobDTO = (TxBlobDTO) o;
		return txId.equals(txBlobDTO.txId) && Arrays.equals(blob, txBlobDTO.blob);
	}

	@Override
	public int hashCode() {
		int result = Objects.hash(txId);
		result = 31 * result + Arrays.hashCode(blob);
		return result;
	}

	@Override
	public String toString() {
		return "{" + txId.toJson() + ", " + Hex.toHexString(blob) + '}';
	}

	public AID getTxId() {
		return txId;
	}

	public byte[] getBlob() {
		return blob;
	}
}
