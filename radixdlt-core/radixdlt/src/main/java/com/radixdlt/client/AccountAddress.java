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
import com.radixdlt.serialization.DeserializeException;
import com.radixdlt.utils.Bits;
import com.radixdlt.utils.functional.Result;
import org.bitcoinj.core.AddressFormatException;
import org.bitcoinj.core.Bech32;

public final class AccountAddress {
	private AccountAddress() {
		throw new IllegalStateException("Cannot instantiate.");
	}

	private static byte[] toBech32Data(byte[] bytes) {
		return Bits.convertBits(bytes, 0, bytes.length, 8, 5, true);
	}

	private static byte[] fromBech32Data(byte[] bytes) {
		return Bits.convertBits(bytes, 0, bytes.length, 5, 8, false);
	}

	public static String of(REAddr addr) {
		var convert = toBech32Data(addr.getBytes());
		return Bech32.encode("brx", convert);
	}

	public static REAddr parse(String v) throws DeserializeException {
		Bech32.Bech32Data bech32Data;
		try {
			bech32Data = Bech32.decode(v);
		} catch (AddressFormatException e) {
			throw new DeserializeException("Could not decode string: " + v, e);
		}

		if (!bech32Data.hrp.equals("brx")) {
			throw new DeserializeException("hrp must be vb but was " + bech32Data.hrp);
		}
		var addrBytes = fromBech32Data(bech32Data.data);
		return REAddr.of(addrBytes);
	}

	public static Result<REAddr> parseFunctional(String addr) {
		try {
			return Result.ok(parse(addr));
		} catch (Exception e) {
			return Result.fail(e);
		}
	}
}
