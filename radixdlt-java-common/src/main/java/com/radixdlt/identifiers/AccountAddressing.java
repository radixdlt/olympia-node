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

package com.radixdlt.identifiers;

import org.bitcoinj.core.AddressFormatException;
import org.bitcoinj.core.Bech32;

import com.radixdlt.serialization.DeserializeException;
import com.radixdlt.utils.Bits;
import com.radixdlt.utils.functional.Result;

import java.util.Objects;

import static com.radixdlt.identifiers.CommonErrors.INVALID_ACCOUNT_ADDRESS;

/**
 * Bech-32 encoding/decoding of account addresses.
 * <p>
 * The human-readable part is "rdx" for mainnet, "brx" for betanet.
 * <p>
 * The data part is a conversion of the 1-34 byte Radix Engine address
 * {@link com.radixdlt.identifiers.REAddr} to Base32 similar to specification described
 * in BIP_0173 for converting witness programs.
 */
public final class AccountAddressing {
	private final String hrp;

	private AccountAddressing(String hrp) {
		this.hrp = hrp;
	}

	public static AccountAddressing bech32(String hrp) {
		Objects.requireNonNull(hrp);
		return new AccountAddressing(hrp);
	}

	private static byte[] toBech32Data(byte[] bytes) {
		return Bits.convertBits(bytes, 0, bytes.length, 8, 5, true);
	}

	private static byte[] fromBech32Data(byte[] bytes) {
		return Bits.convertBits(bytes, 0, bytes.length, 5, 8, false);
	}

	public String of(REAddr addr) {
		var convert = toBech32Data(addr.getBytes());
		return Bech32.encode(hrp, convert);
	}

	public REAddr parse(String v) throws DeserializeException {
		Bech32.Bech32Data bech32Data;
		try {
			bech32Data = Bech32.decode(v);
		} catch (AddressFormatException e) {
			throw new DeserializeException("Could not decode string: " + v, e);
		}

		if (!bech32Data.hrp.equals(hrp)) {
			throw new DeserializeException("hrp must be " + hrp + " but was " + bech32Data.hrp);
		}

		try {
			var addrBytes = fromBech32Data(bech32Data.data);
			return REAddr.of(addrBytes);
		} catch (IllegalArgumentException e) {
			throw new DeserializeException("Invalid address", e);
		}
	}

	public Result<REAddr> parseFunctional(String addr) {
		return Result.wrap(INVALID_ACCOUNT_ADDRESS, () -> parse(addr));
	}
}
