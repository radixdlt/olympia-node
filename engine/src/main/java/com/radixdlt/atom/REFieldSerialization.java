/* Copyright 2021 Radix Publishing Ltd incorporated in Jersey (Channel Islands).
 *
 * Licensed under the Radix License, Version 1.0 (the "License"); you may not use this
 * file except in compliance with the License. You may obtain a copy of the License at:
 *
 * radixfoundation.org/licenses/LICENSE-v1
 *
 * The Licensor hereby grants permission for the Canonical version of the Work to be
 * published, distributed and used under or by reference to the Licensor’s trademark
 * Radix ® and use of any unregistered trade names, logos or get-up.
 *
 * The Licensor provides the Work (and each Contributor provides its Contributions) on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied,
 * including, without limitation, any warranties or conditions of TITLE, NON-INFRINGEMENT,
 * MERCHANTABILITY, or FITNESS FOR A PARTICULAR PURPOSE.
 *
 * Whilst the Work is capable of being deployed, used and adopted (instantiated) to create
 * a distributed ledger it is your responsibility to test and validate the code, together
 * with all logic and performance of that code under all foreseeable scenarios.
 *
 * The Licensor does not make or purport to make and hereby excludes liability for all
 * and any representation, warranty or undertaking in any form whatsoever, whether express
 * or implied, to any entity or person, including any representation, warranty or
 * undertaking, as to the functionality security use, value or other characteristics of
 * any distributed ledger nor in respect the functioning or value of any tokens which may
 * be created stored or transferred using the Work. The Licensor does not warrant that the
 * Work or any use of the Work complies with any law or regulation in any territory where
 * it may be implemented or used or that it will be appropriate for any specific purpose.
 *
 * Neither the licensor nor any current or former employees, officers, directors, partners,
 * trustees, representatives, agents, advisors, contractors, or volunteers of the Licensor
 * shall be liable for any direct or indirect, special, incidental, consequential or other
 * losses of any kind, in tort, contract or otherwise (including but not limited to loss
 * of revenue, income or profits, or loss of use or data, or loss of reputation, or loss
 * of any economic or other opportunity of whatsoever nature or howsoever arising), arising
 * out of or in connection with (without limitation of any use, misuse, of any ledger system
 * or use made or its functionality or any performance or operation of any code or protocol
 * caused by bugs or programming or logic errors or otherwise);
 *
 * A. any offer, purchase, holding, use, sale, exchange or transmission of any
 * cryptographic keys, tokens or assets created, exchanged, stored or arising from any
 * interaction with the Work;
 *
 * B. any failure in a transmission or loss of any token or assets keys or other digital
 * artefacts due to errors in transmission;
 *
 * C. bugs, hacks, logic errors or faults in the Work or any communication;
 *
 * D. system software or apparatus including but not limited to losses caused by errors
 * in holding or transmitting tokens by any third-party;
 *
 * E. breaches or failure of security including hacker attacks, loss or disclosure of
 * password, loss of private key, unauthorised use or misuse of such passwords or keys;
 *
 * F. any losses including loss of anticipated savings or other benefits resulting from
 * use of the Work or any changes to the Work (however implemented).
 *
 * You are solely responsible for; testing, validating and evaluation of all operation
 * logic, functionality, security and appropriateness of using the Work for any commercial
 * or non-commercial purpose and for any reproduction or redistribution by You of the
 * Work. You assume all risks associated with Your use of the Work and the exercise of
 * permissions under this License.
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
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.regex.Pattern;

public final class REFieldSerialization {
  private static final Pattern OWASP_URL_REGEX =
      Pattern.compile(
          "^((((https?|ftps?|gopher|telnet|nntp)://)|(mailto:|news:))"
              + "(%[0-9A-Fa-f]{2}|[-()_.!~*';/?:@&=+$,A-Za-z0-9])+)([).!';/?:,][[:blank:]])?$");

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

  public static ECDSASignature deserializeSignature(ByteBuffer buf) throws DeserializeException {
    var v = buf.get();
    if (v < 0 || v > 3) {
      throw new DeserializeException("Invalid V byte " + v);
    }
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

  public static void serializeOptionalKey(ByteBuffer buf, Optional<ECPublicKey> addr) {
    addr.ifPresentOrElse(
        o -> {
          buf.put((byte) 0x1);
          REFieldSerialization.serializeKey(buf, o);
        },
        () -> {
          buf.put((byte) 0x0);
          buf.put(new byte[ECPublicKey.COMPRESSED_BYTES]);
        });
  }

  public static Optional<ECPublicKey> deserializeOptionalKey(ByteBuffer buf)
      throws DeserializeException {
    var type = buf.get();
    if (type == 0) {
      for (int i = 0; i < ECPublicKey.COMPRESSED_BYTES; i++) {
        if (buf.get() != 0) {
          throw new DeserializeException("Empty key must have 0 value.");
        }
      }
      return Optional.empty();
    } else if (type == 1) {
      return Optional.of(REFieldSerialization.deserializeKey(buf));
    } else {
      throw new DeserializeException("Unknown optionalAccountREAddr: " + type);
    }
  }

  public static void serializeREAddr(ByteBuffer buf, REAddr rri) {
    buf.put(rri.getBytes());
  }

  public static REAddr deserializeREAddr(ByteBuffer buf, EnumSet<REAddr.REAddrType> allowed)
      throws DeserializeException {
    var v = buf.get(); // version
    var type = REAddr.REAddrType.parse(v);
    if (type.isEmpty()) {
      throw new DeserializeException("Unknown address type " + v);
    }
    if (!allowed.contains(type.get())) {
      throw new DeserializeException(
          "Expected address type: " + allowed + " but was: " + type.get());
    }
    return type.get().parse(buf);
  }

  public static REAddr deserializeResourceAddr(ByteBuffer buf) throws DeserializeException {
    return deserializeREAddr(
        buf, EnumSet.of(REAddr.REAddrType.NATIVE_TOKEN, REAddr.REAddrType.HASHED_KEY));
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

  public static int deserializeUnsignedShort(ByteBuffer buf, int min, int max)
      throws DeserializeException {
    var s = buf.getShort();
    var i = Short.toUnsignedInt(s);

    if (i < min) {
      throw new DeserializeException("Min of short value is " + min + " but value is: " + i);
    }

    if (i > max) {
      throw new DeserializeException("Max of short value is " + max + " but value is: " + i);
    }

    return i;
  }

  public static void serializeReservedByte(ByteBuffer buf) {
    buf.put((byte) 0);
  }

  public static void serializeOptionalLong(ByteBuffer buf, OptionalLong optionalLong) {
    optionalLong.ifPresentOrElse(
        e -> {
          buf.put((byte) 0x1);
          buf.putLong(e);
        },
        () -> {
          buf.put((byte) 0x0);
          buf.putLong(0);
        });
  }

  public static OptionalLong deserializeOptionalNonNegativeLong(ByteBuffer buf)
      throws DeserializeException {
    var type = buf.get();
    if (type == 0) {
      var value = REFieldSerialization.deserializeNonNegativeLong(buf);
      if (value != 0) {
        throw new DeserializeException("Empty long must be 0 value.");
      }
      return OptionalLong.empty();
    } else if (type == 1) {
      return OptionalLong.of(REFieldSerialization.deserializeNonNegativeLong(buf));
    } else {
      throw new DeserializeException("Unknown optionalLongType: " + type);
    }
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

  public static void serializeFixedLengthBytes(ByteBuffer buf, byte[] bytes) {
    buf.put(bytes);
  }

  public static byte[] deserializeFixedLengthBytes(ByteBuffer buf, int length) {
    final var dest = new byte[length];
    buf.get(dest);
    return dest;
  }

  public static void serializeString(ByteBuffer buf, String s) {
    var sBytes = s.getBytes(RadixConstants.STANDARD_CHARSET);
    if (sBytes.length > 255) {
      throw new IllegalArgumentException("string cannot be greater than 255 chars");
    }
    var len = (short) sBytes.length;
    buf.putShort(len); // url length
    buf.put(sBytes); // url
  }

  public static String deserializeString(ByteBuffer buf) throws DeserializeException {
    var len = REFieldSerialization.deserializeUnsignedShort(buf, 0, 255);
    var dest = new byte[len];
    buf.get(dest);
    return new String(dest, RadixConstants.STANDARD_CHARSET);
  }

  public static String deserializeUrl(ByteBuffer buf) throws DeserializeException {
    var url = deserializeString(buf);
    if (!isUrlValid(url)) {
      throw new DeserializeException("URL: not a valid URL: " + url);
    }
    return url;
  }

  public static boolean isUrlValid(String url) {
    return url.isEmpty() || OWASP_URL_REGEX.matcher(url).matches();
  }

  public static String requireValidUrl(String url) {
    Objects.requireNonNull(url);

    if (isUrlValid(url)) {
      return url;
    }

    throw new IllegalArgumentException("URL: not a valid URL: " + url);
  }
}
