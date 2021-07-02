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
import java.util.EnumSet;
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

	public static void serializeBoolean(ByteBuffer buf, boolean bool) {
		buf.put((byte) (bool ? 1 : 0));
	}

	public static boolean deserializeBoolean(ByteBuffer buf) throws DeserializeException {
		var flag = buf.get();
		if (!(flag == 0 || flag == 1)) {
			throw new DeserializeException("Invalid flag");
		}
		return flag == 1;
	}

	public static void serializeREAddr(ByteBuffer buf, REAddr rri) {
		buf.put(rri.getBytes());
	}

	public static REAddr deserializeREAddr(ByteBuffer buf, EnumSet<REAddr.REAddrType> allowed) throws DeserializeException {
		var v = buf.get(); // version
		var type = REAddr.REAddrType.parse(v);
		if (type.isEmpty()) {
			throw new DeserializeException("Unknown address type " + v);
		}
		if (!allowed.contains(type.get())) {
			throw new DeserializeException("Address type not allowed. Allowed: " + allowed);
		}
		return type.get().parse(buf);
	}

	public static REAddr deserializeResourceAddr(ByteBuffer buf) throws DeserializeException {
		return deserializeREAddr(buf, EnumSet.of(REAddr.REAddrType.NATIVE_TOKEN, REAddr.REAddrType.HASHED_KEY));
	}

	public static REAddr deserializeAccountREAddr(ByteBuffer buf) throws DeserializeException {
		return deserializeREAddr(buf, EnumSet.of(REAddr.REAddrType.PUB_KEY));
	}

	public static int deserializeInt(ByteBuffer buf) throws DeserializeException {
		return buf.getInt();
	}

	public static void deserializeReservedByte(ByteBuffer buf) throws DeserializeException {
		var b = buf.get();
		if (b != 0) {
			throw new DeserializeException("Reserved byte must be 0");
		}
	}

	public static void serializeReservedByte(ByteBuffer buf) {
		buf.put((byte) 0);
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

	public static void serializeUInt256(ByteBuffer buf, UInt256 u) {
		buf.put(u.toByteArray());
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
		serializeBytes(buf, s.getBytes(RadixConstants.STANDARD_CHARSET));
	}

	public static String deserializeString(ByteBuffer buf) {
		return new String(deserializeBytes(buf), RadixConstants.STANDARD_CHARSET);
	}

	public static void serializeBytes(ByteBuffer buf, byte[] bytes) {
		if (bytes.length > 255) {
			throw new IllegalArgumentException("bytes cannot be longer than 255");
		}
		final var len = (byte) bytes.length;
		buf.put(len);
		buf.put(bytes);
	}

	public static byte[] deserializeBytes(ByteBuffer buf) {
		final var len = Byte.toUnsignedInt(buf.get());
		final var dest = new byte[len];
		buf.get(dest);
		return dest;
	}

	public static void serializeFixedLengthBytes(ByteBuffer buf, byte[] bytes, int length) {
		if (bytes.length != length) {
			throw new IllegalArgumentException("Expected " + length + " bytes, but got " + bytes.length);
		}
		buf.put(bytes);
	}

	public static byte[] deserializeFixedLengthBytes(ByteBuffer buf, int length) {
		final var dest = new byte[length];
		buf.get(dest);
		return dest;
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
