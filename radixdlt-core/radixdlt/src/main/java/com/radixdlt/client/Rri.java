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

package com.radixdlt.client;

import com.radixdlt.identifiers.REAddr;
import com.radixdlt.utils.Bits;
import com.radixdlt.utils.Pair;
import com.radixdlt.utils.functional.Result;
import org.bitcoinj.core.AddressFormatException;
import org.bitcoinj.core.Bech32;

/**
 * Radix resource identifier, hrp of the address should match the nonce
 * provided when the resource was first created.
 */
public final class Rri {
	private static final String RRI_HRP_SUFFIX = "_rr";

	private Rri() {
		throw new IllegalStateException("Cannot instantiate.");
	}

	public static REAddr parseUnchecked(String rri) {
		var data = Bech32.decode(rri);
		if (!data.hrp.endsWith(RRI_HRP_SUFFIX)) {
			throw new IllegalArgumentException();
		}
		var hash = data.data;
		if (hash.length > 0) {
			hash = Bits.convertBits(hash, 0, hash.length, 5, 8, false);
		} else {
			return REAddr.ofNativeToken();
		}
		return REAddr.of(hash);
	}

	public static Result<Pair<String, REAddr>> parse(String rri) {
		try {
			var data = Bech32.decode(rri);
			if (!data.hrp.endsWith(RRI_HRP_SUFFIX)) {
				return Result.fail("hrp must end in " + RRI_HRP_SUFFIX);
			}
			var hash = data.data;
			if (hash.length > 0) {
				hash = Bits.convertBits(hash, 0, hash.length, 5, 8, false);
			}
			var symbol = data.hrp.substring(0, data.hrp.length() - RRI_HRP_SUFFIX.length());
			return Result.ok(Pair.of(symbol, REAddr.of(hash)));
		} catch (AddressFormatException e) {
			return Result.fail(e);
		}
	}

	public static String of(String symbol, REAddr rri) {
		final byte[] convert;
		var hash = rri.getBytes();
		if (hash.length != 0) {
			convert = Bits.convertBits(hash, 0, hash.length, 8, 5, true);
		} else {
			convert = hash;
		}
		return Bech32.encode(symbol + RRI_HRP_SUFFIX, convert);
	}
}