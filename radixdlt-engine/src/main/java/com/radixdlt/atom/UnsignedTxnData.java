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

package com.radixdlt.atom;

import com.google.common.hash.HashCode;
import com.radixdlt.utils.UInt256;

public final class UnsignedTxnData {
	private final byte[] blob;
	private final HashCode hashToSign;
	private final UInt256 feesPaid;

	public UnsignedTxnData(byte[] blob, HashCode hashToSign, UInt256 feesPaid) {
		this.blob = blob;
		this.hashToSign = hashToSign;
		this.feesPaid = feesPaid;
	}

	public byte[] blob() {
		return blob;
	}

	public HashCode hashToSign() {
		return hashToSign;
	}

	public UInt256 feesPaid() {
		return feesPaid;
	}
}
