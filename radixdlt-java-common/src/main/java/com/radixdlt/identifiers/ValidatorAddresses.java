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

import com.radixdlt.crypto.ECPublicKey;
import com.radixdlt.crypto.exception.PublicKeyException;
import com.radixdlt.serialization.DeserializeException;
import com.radixdlt.utils.Bits;
import com.radixdlt.utils.functional.Result;

import static com.radixdlt.identifiers.CommonErrors.INVALID_VALIDATOR_ADDRESS;

/**
 * Bech-32 encoding/decoding of validators. Validators are represented as 33-byte
 * compressed EC Public Keys.
 * <p>
 * The data part is a conversion of the 33 byte compressed EC public key to Base32
 * similar to specification described in BIP_0173 for converting witness programs.
 */
public final class ValidatorAddresses {
	private final String hrp;
	public ValidatorAddresses(String hrp) {
		this.hrp = hrp;
	}

	private static byte[] toBech32Data(byte[] bytes) {
		return Bits.convertBits(bytes, 0, bytes.length, 8, 5, true);
	}

	private static byte[] fromBech32Data(byte[] bytes) {
		return Bits.convertBits(bytes, 0, bytes.length, 5, 8, false);
	}

	public String of(ECPublicKey key) {
		var bytes = key.getCompressedBytes();
		var convert = toBech32Data(bytes);
		return Bech32.encode(hrp, convert);
	}

	public ECPublicKey parse(String v) throws DeserializeException {
		Bech32.Bech32Data bech32Data;
		try {
			bech32Data = Bech32.decode(v);
		} catch (AddressFormatException e) {
			throw new DeserializeException("Could not decode string: " + v, e);
		}

		if (!bech32Data.hrp.equals(hrp)) {
			throw new DeserializeException("hrp must be vb but was " + bech32Data.hrp);
		}
		var keyBytes = fromBech32Data(bech32Data.data);
		try {
			return ECPublicKey.fromBytes(keyBytes);
		} catch (PublicKeyException e) {
			throw new DeserializeException("Invalid bytes in validator address: " + v);
		}
	}

	public Result<ECPublicKey> fromString(String input) {
		return Result.wrap(INVALID_VALIDATOR_ADDRESS, () -> parse(input));
	}
}
