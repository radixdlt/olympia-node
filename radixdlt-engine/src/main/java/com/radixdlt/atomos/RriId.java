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

import com.radixdlt.atommodel.tokens.TokenDefinitionUtils;
import com.radixdlt.crypto.HashUtils;
import com.radixdlt.identifiers.RRI;
import com.radixdlt.utils.RadixConstants;
import org.bouncycastle.util.encoders.Hex;

import java.nio.ByteBuffer;
import java.util.Arrays;

/**
 * Hashed Rri used for uniqueness/identification
 */
public final class RriId {
	private static final byte[] NATIVE_TOKEN_ID = new byte[] {1};
	private static final RriId NATIVE_TOKEN_RRI_ID = new RriId(NATIVE_TOKEN_ID);
	private static final int BYTES = 32 + 1;
	private final byte[] id;

	private RriId(byte[] id) {
		if (!(id.length == BYTES && id[0] == 0) && !Arrays.equals(id, NATIVE_TOKEN_ID)) {
			throw new IllegalArgumentException("RriId must be " + BYTES + " length or be native: " + Hex.toHexString(id));
		}
		this.id = id;
	}

	public static RriId readFromBuf(ByteBuffer buf) {
		var type = buf.get();
		if (type == 1) {
			return NATIVE_TOKEN_RRI_ID;
		} else if (type == 0) {
			var bytes = new byte[BYTES];
			bytes[0] = 0;
			buf.get(bytes, 1, BYTES - 1);
			return RriId.from(bytes);
		} else {
			throw new IllegalArgumentException();
		}
	}

	public static RriId from(byte[] id) {
		return new RriId(id);
	}

	public static RriId fromRri(RRI rri) {
		if (rri.getName().equals(TokenDefinitionUtils.getNativeTokenShortCode())) {
			return NATIVE_TOKEN_RRI_ID;
		}

		var id = new byte[BYTES];
		id[0] = (byte) 0;

		var firstHash = HashUtils.sha256(rri.toString().getBytes(RadixConstants.STANDARD_CHARSET));
		firstHash.writeBytesTo(id, 1, 32);
		return new RriId(id);
	}

	public boolean isNativeToken() {
		return id[0] == 1;
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
