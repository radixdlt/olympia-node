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

import org.bitcoinj.core.Bech32;

import com.radixdlt.utils.Bits;
import com.radixdlt.utils.functional.Result;
import com.radixdlt.utils.functional.Tuple.Tuple2;

import static com.radixdlt.identifiers.CommonErrors.UNABLE_TO_DECODE;
import static com.radixdlt.utils.functional.Tuple.tuple;

/**
 * A Radix resource identifier which encodes addresses with a resource behind them in
 * Bech-32 encoding.
 *
 * The human-readable part is the alphanumeric argument provided when creating the
 * resource followed by "_rb" for betanet and "_rr" for mainnet.
 *
 * The data part is a conversion of the 1-34 byte Radix Engine address
 * {@link com.radixdlt.identifiers.REAddr} to Base32 similar to specification described
 * in BIP_0173 for converting witness programs.
 */
public final class ResourceAddressing {
	private final String hrpSuffix;

	private ResourceAddressing(String hrpSuffix) {
		this.hrpSuffix = hrpSuffix;
	}

	public static ResourceAddressing bech32(String hrpSuffix) {
		return new ResourceAddressing(hrpSuffix);
	}

	private static byte[] toBech32Data(byte[] bytes) {
		return Bits.convertBits(bytes, 0, bytes.length, 8, 5, true);
	}

	private static byte[] fromBech32Data(byte[] bytes) {
		return Bits.convertBits(bytes, 0, bytes.length, 5, 8, false);
	}

	public Tuple2<String, REAddr> parse(String rri) {
		var data = Bech32.decode(rri);
		if (!data.hrp.endsWith(hrpSuffix)) {
			throw new IllegalArgumentException("Address hrp suffix must be " + hrpSuffix + "(" + rri + ")");
		}
		var symbol = data.hrp.substring(0, data.hrp.length() - hrpSuffix.length());
		if (!Naming.NAME_PATTERN.matcher(symbol).matches()) {
			throw new IllegalArgumentException("Invalid symbol in address (" + rri + ")");
		}
		var addrBytes = fromBech32Data(data.data);
		return tuple(symbol, REAddr.of(addrBytes));
	}

	public Result<Tuple2<String, REAddr>> parseFunctional(String rri) {
		return Result.wrap(UNABLE_TO_DECODE, () -> parse(rri));
	}

	public Result<REAddr> parseToAddr(String rri) {
		return Result.wrap(UNABLE_TO_DECODE, () -> parse(rri).map((__, addr) -> addr));
	}

	public String of(String symbol, REAddr addr) {
		var addrBytes = addr.getBytes();
		var bech32Data = toBech32Data(addrBytes);
		return Bech32.encode(symbol + hrpSuffix, bech32Data);
	}
}