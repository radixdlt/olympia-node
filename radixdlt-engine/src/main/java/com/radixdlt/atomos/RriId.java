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

package com.radixdlt.atomos;

import com.radixdlt.crypto.HashUtils;
import com.radixdlt.identifiers.RRI;
import com.radixdlt.utils.RadixConstants;

import java.nio.ByteBuffer;
import java.util.Arrays;

public final class RriId {
	public static final int BYTES = 32;
	private final byte[] id;
	private RriId(byte[] id) {
		this.id = id;
	}

	public static RriId readFromBuf(ByteBuffer buf) {
		var bytes = new byte[BYTES];
		buf.get(bytes);
		return RriId.from(bytes);
	}

	public static RriId from(byte[] id) {
		return new RriId(id);
	}

	public static RriId fromRri(RRI rri) {
		var firstHash = HashUtils.sha256(rri.toString().getBytes(RadixConstants.STANDARD_CHARSET));
		var secondHash = HashUtils.sha256(firstHash.asBytes());
		return new RriId(secondHash.asBytes());
	}

	public byte[] asBytes() {
		return id;
	}

	@Override
	public int hashCode() {
		return Arrays.hashCode(id);
	}

	@Override
	public boolean equals(Object o) {
		if (!(o instanceof RriId)) {
			return false;
		}

		var other = (RriId) o;
		return Arrays.equals(this.id, other.id);
	}
}
