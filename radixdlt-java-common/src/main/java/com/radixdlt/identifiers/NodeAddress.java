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

import com.radixdlt.crypto.ECPublicKey;
import com.radixdlt.crypto.exception.PublicKeyException;
import com.radixdlt.serialization.DeserializeException;
import com.radixdlt.utils.Bits;
import org.bitcoinj.core.AddressFormatException;
import org.bitcoinj.core.Bech32;

/**
 * Bech-32 encoding/decoding of a node addresses.
 * <p>
 * The human-readable part is "rdn" ("radix node") for mainnet, "brn" ("betanet radix node") for betanet.
 * <p>
 * The data part is a conversion of the 1-34 byte Radix Engine address
 * {@link REAddr} to Base32 similar to specification described
 * in BIP_0173 for converting witness programs.
 */
public final class NodeAddress {
	private static final String NODE_HRP = "brn"; // "rdx" for mainnet

	private NodeAddress() {
		throw new IllegalStateException("Cannot instantiate.");
	}

	private static byte[] toBech32Data(byte[] bytes) {
		return Bits.convertBits(bytes, 0, bytes.length, 8, 5, true);
	}

	private static byte[] fromBech32Data(byte[] bytes) {
		return Bits.convertBits(bytes, 0, bytes.length, 5, 8, false);
	}

	public static String of(ECPublicKey publicKey) {
		var convert = toBech32Data(publicKey.getCompressedBytes());
		return Bech32.encode(NODE_HRP, convert);
	}

	public static ECPublicKey parse(String v) throws DeserializeException {
		Bech32.Bech32Data bech32Data;
		try {
			bech32Data = Bech32.decode(v);
		} catch (AddressFormatException e) {
			throw new DeserializeException("Could not decode string: " + v, e);
		}

		if (!bech32Data.hrp.equals(NODE_HRP)) {
			throw new DeserializeException("hrp must be " + NODE_HRP + " but was " + bech32Data.hrp);
		}

		try {
			var pubKeyBytes = fromBech32Data(bech32Data.data);
			return ECPublicKey.fromBytes(pubKeyBytes);
		} catch (IllegalArgumentException | PublicKeyException e) {
			throw new DeserializeException("Invalid address", e);
		}
	}
}
