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

package com.radixdlt.constraintmachine;

import com.radixdlt.constraintmachine.exceptions.CallDataAccessException;
import com.radixdlt.utils.Bytes;
import com.radixdlt.utils.UInt256;

import java.util.Objects;

public final class CallData {
	private final byte[] data;
	public CallData(byte[] data) {
		this.data = Objects.requireNonNull(data);
	}

	public byte get(int offset) throws CallDataAccessException {
		if (offset < 0 || (offset + Byte.BYTES) > data.length) {
			throw new CallDataAccessException(data.length, offset, Byte.BYTES);
		}
		return data[offset];
	}

	public UInt256 getUInt256(int offset) throws CallDataAccessException {
		if (offset < 0 || (offset + UInt256.BYTES) > data.length) {
			throw new CallDataAccessException(data.length, offset, UInt256.BYTES);
		}
		return UInt256.from(data, offset);
	}

	@Override
	public String toString() {
		return String.format("%s{data=%s}", this.getClass().getSimpleName(), Bytes.toHexString(data));
	}
}
