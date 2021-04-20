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

import com.radixdlt.crypto.ECPublicKey;
import com.radixdlt.crypto.exception.PublicKeyException;
import com.radixdlt.serialization.DeserializeException;
import com.radixdlt.utils.Bits;
import org.bitcoinj.core.AddressFormatException;
import org.bitcoinj.core.Bech32;

public class Address {
	private Address() {
		throw new IllegalStateException();
	}

	public static String ofValidator(ECPublicKey key) {
		var bytes = key.getCompressedBytes();
		var convert = Bits.convertBits(bytes, 0, bytes.length, 8, 5, true);
		return Bech32.encode("vb", convert);
	}

	public static ECPublicKey parseValidatorAddress(String v) throws DeserializeException {
		Bech32.Bech32Data bech32Data;
		try {
			bech32Data = Bech32.decode(v);
		} catch (AddressFormatException e) {
			throw new DeserializeException("Could not decode string: " + v, e);
		}
		if (!bech32Data.hrp.equals("vb")) {
			throw new DeserializeException("hrp must be vb but was " + bech32Data.hrp);
		}
		var keyBytes = Bits.convertBits(bech32Data.data, 0, bech32Data.data.length, 5, 8, false);
		try {
			return ECPublicKey.fromBytes(keyBytes);
		} catch (PublicKeyException e) {
			throw new DeserializeException("Invalid bytes in validator address: " + v);
		}
	}
}
