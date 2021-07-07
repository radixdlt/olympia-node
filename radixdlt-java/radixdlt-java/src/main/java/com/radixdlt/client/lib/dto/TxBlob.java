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

import java.util.Arrays;

import static java.util.Objects.requireNonNull;

public final class TxBlob {
	private final byte[] blob;
	private final byte[] hashToSign;

	private TxBlob(byte[] blob, byte[] hashToSign) {
		this.blob = blob;
		this.hashToSign = hashToSign;
	}

	public static TxBlob create(byte[] blob, byte[] hashToSign) {
		requireNonNull(blob);
		requireNonNull(hashToSign);

		return new TxBlob(blob, hashToSign);
	}

	@JsonCreator
	public static TxBlob create(
		@JsonProperty(value = "blob", required = true) String blob,
		@JsonProperty(value = "hashOfBlobToSign", required = true) String hashToSign
	) {
		requireNonNull(blob);
		requireNonNull(hashToSign);

		return new TxBlob(Hex.decode(blob), Hex.decode(hashToSign));
	}

	@Override
	public boolean equals(Object o) {
		if (this == o) {
			return true;
		}

		if (!(o instanceof TxBlob)) {
			return false;
		}

		var txBlobDTO = (TxBlob) o;
		return Arrays.equals(blob, txBlobDTO.blob) && Arrays.equals(hashToSign, txBlobDTO.hashToSign);
	}

	@Override
	public int hashCode() {
		int result = Arrays.hashCode(blob);
		result = 31 * result + Arrays.hashCode(hashToSign);
		return result;
	}

	@Override
	public String toString() {
		return "TxBlobDTO(blob=" + Hex.toHexString(blob)
			+ ", hashToSign=" + Hex.toHexString(hashToSign) + ')';
	}

	public byte[] getBlob() {
		return blob;
	}

	public byte[] getHashToSign() {
		return hashToSign;
	}
}
