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

package com.radixdlt.atom;

import com.radixdlt.crypto.ECDSASignature;
import com.radixdlt.crypto.ECPublicKey;
import com.radixdlt.crypto.exception.PublicKeyException;
import com.radixdlt.identifiers.REAddr;
import com.radixdlt.serialization.DeserializeException;
import com.radixdlt.utils.RadixConstants;
import com.radixdlt.utils.UInt256;

import java.nio.ByteBuffer;
import java.util.regex.Pattern;

public final class REFieldSerialization {
	private static final Pattern OWASP_URL_REGEX = Pattern.compile(
		"^((((https?|ftps?|gopher|telnet|nntp)://)|(mailto:|news:))"
			+ "(%[0-9A-Fa-f]{2}|[-()_.!~*';/?:@&=+$,A-Za-z0-9])+)([).!';/?:,][[:blank:]])?$"
	);

	private REFieldSerialization() {
		throw new IllegalStateException("Cannot instantiate.");
	}

	public static byte[] serializeSignature(ECDSASignature signature) {
		var buf = ByteBuffer.allocate(32 * 2 + 1);
		buf.put(signature.getV());
		var rArray = signature.getR().toByteArray();
		var r = rArray.length > 32 ? UInt256.from(rArray, 1) : UInt256.from(rArray);
		buf.put(r.toByteArray());
		var sArray = signature.getS().toByteArray();
		var s = sArray.length > 32 ? UInt256.from(sArray, 1) : UInt256.from(sArray);
		buf.put(s.toByteArray());

		return buf.array();
	}

	public static ECDSASignature deserializeSignature(ByteBuffer buf) {
		var v = buf.get();
		var rArray = new byte[32];
		buf.get(rArray);
		var sArray = new byte[32];
		buf.get(sArray);
		return ECDSASignature.deserialize(rArray, sArray, v);
	}

	public static void serializeREAddr(ByteBuffer buf, REAddr rri) {
		buf.put(rri.getBytes());
	}

	public static REAddr deserializeREAddr(ByteBuffer buf) throws DeserializeException {
		var v = buf.get(); // version
		var type = REAddr.REAddrType.parse(v);
		if (type.isEmpty()) {
			throw new DeserializeException("Unknown address type " + v);
		}
		return type.get().parse(buf);
	}

	public static REAddr deserializeAccountREAddr(ByteBuffer buf) throws DeserializeException {
		var v = buf.get(); // version
		var type = REAddr.REAddrType.parse(v);
		if (type.isEmpty()) {
			throw new DeserializeException("Unknown address type " + v);
		}
		var addr = type.get().parse(buf);
		if (!addr.isAccount()) {
			throw new DeserializeException("Address is not an account" + v);
		}
		return addr;
	}

	public static int deserializeInt(ByteBuffer buf) throws DeserializeException {
		return buf.getInt();
	}

	public static Long deserializeNonNegativeLong(ByteBuffer buf) throws DeserializeException {
		var l = buf.getLong();
		if (l < 0) {
			throw new DeserializeException("Long must be positive");
		}
		return l;
	}

	public static UInt256 deserializeUInt256(ByteBuffer buf) {
		var amountDest = new byte[UInt256.BYTES]; // amount
		buf.get(amountDest);
		return UInt256.from(amountDest);
	}

	public static UInt256 deserializeNonZeroUInt256(ByteBuffer buf) throws DeserializeException {
		var amountDest = new byte[UInt256.BYTES]; // amount
		buf.get(amountDest);
		var uint256 = UInt256.from(amountDest);
		if (uint256.isZero()) {
			throw new DeserializeException("Cannot be zero.");
		}
		return uint256;
	}

	public static void serializeKey(ByteBuffer buf, ECPublicKey key) {
		buf.put(key.getCompressedBytes()); // address
	}

	public static ECPublicKey deserializeKey(ByteBuffer buf) throws DeserializeException {
		try {
			var keyBytes = new byte[33];
			buf.get(keyBytes);
			return ECPublicKey.fromBytes(keyBytes);
		} catch (PublicKeyException | IllegalArgumentException e) {
			throw new DeserializeException("Could not deserialize key");
		}
	}

	public static void serializeString(ByteBuffer buf, String s) {
		var sBytes = s.getBytes(RadixConstants.STANDARD_CHARSET);
		if (sBytes.length > 255) {
			throw new IllegalArgumentException("string cannot be greater than 255 chars");
		}
		var len = (byte) sBytes.length;
		buf.put(len); // url length
		buf.put(sBytes); // url
	}

	public static String deserializeString(ByteBuffer buf) {
		var len = Byte.toUnsignedInt(buf.get()); // url
		var dest = new byte[len];
		buf.get(dest);
		return new String(dest, RadixConstants.STANDARD_CHARSET);
	}

	public static String deserializeUrl(ByteBuffer buf) throws DeserializeException {
		var len = Byte.toUnsignedInt(buf.get()); // url
		var dest = new byte[len];
		buf.get(dest);
		var url = new String(dest, RadixConstants.STANDARD_CHARSET);
		if (!url.isEmpty() && !OWASP_URL_REGEX.matcher(url).matches()) {
			throw new DeserializeException("URL: not a valid URL: " + url);
		}
		return url;
	}
}
