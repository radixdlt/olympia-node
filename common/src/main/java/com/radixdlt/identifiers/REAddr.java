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

package com.radixdlt.identifiers;

import com.radixdlt.crypto.ECPublicKey;
import com.radixdlt.crypto.HashUtils;
import com.radixdlt.crypto.exception.PublicKeyException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;
import org.bouncycastle.util.encoders.Hex;

/**
 * A Radix Engine Address. A 1-34 byte array describing a resource or component in the Radix Engine.
 *
 * <p>The first byte of the address describes the type of address followed by additional data
 * depending on the type.
 *
 * <p>Type (first byte) 0x01 : Native Token, 0 data bytes 0x03 : Hashed Key+Nonce, append
 * lower_26_bytes(sha_256_twice(33_byte_compressed_pubkey | arg_nonce)) 0x04 : Public Key, append 33
 * bytes of a compressed EC public key
 */
public final class REAddr {
  public enum REAddrType {
    SYSTEM((byte) 0) {
      public REAddr parse(ByteBuffer buf) {
        return REAddr.ofSystem();
      }

      public Optional<String> verify(ByteBuffer buf) {
        if (buf.hasRemaining()) {
          return Optional.of("System must not have bytes");
        }
        return Optional.empty();
      }
    },
    NATIVE_TOKEN((byte) 1) {
      public REAddr parse(ByteBuffer buf) {
        return REAddr.ofNativeToken();
      }

      public Optional<String> verify(ByteBuffer buf) {
        if (buf.hasRemaining()) {
          return Optional.of("Native token must not have bytes");
        }
        return Optional.empty();
      }
    },
    HASHED_KEY((byte) 3) {
      public REAddr parse(ByteBuffer buf) {
        var addr = new byte[REAddr.HASHED_KEY_BYTES + 1];
        addr[0] = type;
        buf.get(addr, 1, REAddr.HASHED_KEY_BYTES);
        return new REAddr(addr);
      }

      public Optional<String> verify(ByteBuffer buf) {
        if (buf.remaining() != REAddr.HASHED_KEY_BYTES) {
          return Optional.of("Hashed key must have " + HASHED_KEY_BYTES + " bytes");
        }
        return Optional.empty();
      }
    },
    PUB_KEY((byte) 4) {
      public REAddr parse(ByteBuffer buf) {
        var addr = new byte[ECPublicKey.COMPRESSED_BYTES + 1];
        addr[0] = type;
        buf.get(addr, 1, ECPublicKey.COMPRESSED_BYTES);
        return new REAddr(addr);
      }

      public Optional<String> verify(ByteBuffer buf) {
        if (buf.remaining() != ECPublicKey.COMPRESSED_BYTES) {
          return Optional.of(
              "Pub key address must have " + ECPublicKey.COMPRESSED_BYTES + " bytes");
        }
        return Optional.empty();
      }
    };

    static Map<Byte, REAddrType> opMap;

    static {
      opMap =
          Arrays.stream(REAddrType.values())
              .collect(Collectors.toMap(REAddrType::byteValue, r -> r));
    }

    final byte type;

    REAddrType(byte type) {
      this.type = type;
    }

    public byte byteValue() {
      return type;
    }

    public abstract REAddr parse(ByteBuffer buf);

    public abstract Optional<String> verify(ByteBuffer buf);

    public static Optional<REAddrType> parse(byte b) {
      return Optional.ofNullable(opMap.get(b));
    }
  }

  public static final int PUB_KEY_BYTES = ECPublicKey.COMPRESSED_BYTES + 1;
  public static final int HASHED_KEY_BYTES = 26;
  private final byte[] addr;

  REAddr(byte[] addr) {
    this.addr = addr;
  }

  private static REAddr create(byte[] hash) {
    Objects.requireNonNull(hash);
    if (hash.length == 0) {
      throw new IllegalArgumentException();
    }
    if (hash[0] == REAddrType.NATIVE_TOKEN.type) {
      if (hash.length != 1) {
        throw new IllegalArgumentException();
      }
    } else if (hash[0] == REAddrType.HASHED_KEY.type) {
      if (hash.length != 1 + HASHED_KEY_BYTES) {
        throw new IllegalArgumentException();
      }
    }

    return new REAddr(hash);
  }

  public static byte[] pkToHash(String name, ECPublicKey publicKey) {
    var nameBytes = name.getBytes(StandardCharsets.UTF_8);
    var dataToHash = new byte[33 + nameBytes.length];
    System.arraycopy(publicKey.getCompressedBytes(), 0, dataToHash, 0, 33);
    System.arraycopy(nameBytes, 0, dataToHash, 33, nameBytes.length);
    var hash = HashUtils.sha256(dataToHash);
    return Arrays.copyOfRange(hash.asBytes(), 32 - HASHED_KEY_BYTES, 32);
  }

  public boolean allowToClaimAddress(ECPublicKey publicKey, byte[] arg) {
    if (addr[0] == REAddrType.HASHED_KEY.type) {
      var hash = REAddr.pkToHash(new String(arg), publicKey);
      return Arrays.equals(addr, 1, HASHED_KEY_BYTES + 1, hash, 0, HASHED_KEY_BYTES);
    }

    return false;
  }

  public boolean isAccount() {
    return getType() == REAddrType.PUB_KEY;
  }

  public Optional<ECPublicKey> publicKey() {
    if (!isAccount()) {
      return Optional.empty();
    }

    try {
      return Optional.of(ECPublicKey.fromBytes(Arrays.copyOfRange(addr, 1, addr.length)));
    } catch (PublicKeyException e) {
      return Optional.empty();
    }
  }

  // FIXME: Should use AuthorizationException instead but packages a bit of a mess at the moment
  public static class BucketWithdrawAuthorizationException extends Exception {
    private BucketWithdrawAuthorizationException(String msg) {
      super(msg);
    }
  }

  public void verifyWithdrawAuthorization(Optional<ECPublicKey> publicKey)
      throws BucketWithdrawAuthorizationException {
    if (getType() != REAddrType.PUB_KEY) {
      throw new BucketWithdrawAuthorizationException(this + " is not an account address.");
    }

    if (publicKey.isEmpty()) {
      throw new BucketWithdrawAuthorizationException("No key present.");
    }

    if (!Arrays.equals(
        addr,
        1,
        1 + ECPublicKey.COMPRESSED_BYTES,
        publicKey.get().getCompressedBytes(),
        0,
        ECPublicKey.COMPRESSED_BYTES)) {
      throw new BucketWithdrawAuthorizationException("Invalid key.");
    }
  }

  public REAddrType getType() {
    return REAddrType.parse(addr[0]).orElseThrow();
  }

  public boolean isSystem() {
    return addr[0] == REAddrType.SYSTEM.type;
  }

  public boolean isNativeToken() {
    return addr[0] == REAddrType.NATIVE_TOKEN.type;
  }

  public byte[] getBytes() {
    return addr;
  }

  public static REAddr of(byte[] addr) {
    if (addr.length == 0) {
      throw new IllegalArgumentException("Address cannot be empty.");
    }
    var buf = ByteBuffer.wrap(addr);
    var type = REAddrType.parse(buf.get());
    if (type.isEmpty()) {
      throw new IllegalArgumentException("Unknown address type: " + type);
    }
    var error = type.get().verify(buf);
    error.ifPresent(
        str -> {
          throw new IllegalArgumentException(str);
        });
    return new REAddr(addr);
  }

  public static REAddr ofHashedKey(ECPublicKey key, String name) {
    Objects.requireNonNull(key);
    var hash = pkToHash(name, key);
    var buf = ByteBuffer.allocate(HASHED_KEY_BYTES + 1);
    buf.put(REAddrType.HASHED_KEY.type);
    buf.put(hash);
    return create(buf.array());
  }

  public static REAddr ofHashedKey(ByteBuffer readBuf) {
    var buf = ByteBuffer.allocate(HASHED_KEY_BYTES + 1);
    buf.put(REAddrType.HASHED_KEY.type);
    buf.put(readBuf.get(HASHED_KEY_BYTES));
    return create(buf.array());
  }

  public static REAddr ofPubKeyAccount(ECPublicKey key) {
    Objects.requireNonNull(key);
    var buf = ByteBuffer.allocate(ECPublicKey.COMPRESSED_BYTES + 1);
    buf.put(REAddrType.PUB_KEY.type);
    buf.put(key.getCompressedBytes());
    return create(buf.array());
  }

  public static REAddr ofSystem() {
    return create(new byte[] {REAddrType.SYSTEM.type});
  }

  public static REAddr ofNativeToken() {
    return create(new byte[] {REAddrType.NATIVE_TOKEN.type});
  }

  @Override
  public String toString() {
    return Hex.toHexString(this.addr);
  }

  @Override
  public boolean equals(Object o) {
    if (!(o instanceof REAddr)) {
      return false;
    }

    var rri = (REAddr) o;
    return Arrays.equals(rri.addr, addr);
  }

  @Override
  public int hashCode() {
    return Arrays.hashCode(addr);
  }
}
