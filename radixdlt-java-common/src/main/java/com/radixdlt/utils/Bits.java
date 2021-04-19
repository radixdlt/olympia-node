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

package com.radixdlt.utils;

import org.bitcoinj.core.AddressFormatException;

import java.io.ByteArrayOutputStream;

public final class Bits {
	private Bits() {
		throw new IllegalStateException("Cannot instantiate");
	}

	public static byte[] convertBits(final byte[] in, final int inStart, final int inLen, final int fromBits,
									  final int toBits, final boolean pad) throws AddressFormatException {
		int acc = 0;
		int bits = 0;
		ByteArrayOutputStream out = new ByteArrayOutputStream(64);
		final int maxv = (1 << toBits) - 1;
		final int maxAcc = (1 << (fromBits + toBits - 1)) - 1;
		for (int i = 0; i < inLen; i++) {
			int value = in[i + inStart] & 0xff;
			if ((value >>> fromBits) != 0) {
				throw new AddressFormatException(
					String.format("Input value '%X' exceeds '%d' bit size", value, fromBits));
			}
			acc = ((acc << fromBits) | value) & maxAcc;
			bits += fromBits;
			while (bits >= toBits) {
				bits -= toBits;
				out.write((acc >>> bits) & maxv);
			}
		}
		if (pad) {
			if (bits > 0) {
				out.write((acc << (toBits - bits)) & maxv);
			}
		} else if (bits >= fromBits || ((acc << (toBits - bits)) & maxv) != 0) {
			throw new AddressFormatException("Could not convert bits, invalid padding");
		}
		return out.toByteArray();
	}
}
