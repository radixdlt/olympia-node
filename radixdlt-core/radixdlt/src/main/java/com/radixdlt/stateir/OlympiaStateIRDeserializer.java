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

package com.radixdlt.stateir;

import com.google.common.collect.ImmutableList;
import com.google.common.hash.HashCode;
import com.radixdlt.crypto.ECPublicKey;
import com.radixdlt.identifiers.REAddr;
import com.radixdlt.utils.Ints;
import com.radixdlt.utils.Longs;
import com.radixdlt.utils.UInt256;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Supplier;
import java.util.stream.IntStream;

/** A deserializer for the Olympia state IR (intermediate representation). */
public final class OlympiaStateIRDeserializer {

  public OlympiaStateIR deserialize(ByteArrayInputStream bais) {
    return new InternalStatefulOlympiaStateIRDeserializer(bais).deserialize();
  }

  /**
   * An internal state wrapper class so that `bais` doesn't need to be passed as a parameter to
   * every single method. At the same time, because it's private, incorrect uses (multiple
   * `deserialize` calls) are impossible.
   */
  private static final class InternalStatefulOlympiaStateIRDeserializer {
    private final ByteArrayInputStream bais;

    private InternalStatefulOlympiaStateIRDeserializer(ByteArrayInputStream bais) {
      this.bais = Objects.requireNonNull(bais);
    }

    private OlympiaStateIR deserialize() {
      final var validators = readValidators();
      final var resources = readResources();
      final var accounts = readAccounts();
      final var balances = readBalances();
      final var stakes = readStakes();
      final var lastConsensusTimestamp = readLong();
      final var lastEpoch = readLong();
      return new OlympiaStateIR(validators, resources, accounts, balances, stakes, lastConsensusTimestamp, lastEpoch);
    }

    private ImmutableList<OlympiaStateIR.Validator> readValidators() {
      return readList(this::readValidator);
    }

    private OlympiaStateIR.Validator readValidator() {
      return new OlympiaStateIR.Validator(
          readPublicKeyBytes(),
          readString(),
          readString(),
          readBool(),
          readBool(),
          readUint256(),
          readUint256(),
          readInt(),
          readInt());
    }

    private ImmutableList<OlympiaStateIR.Resource> readResources() {
      return readList(this::readResource);
    }

    private OlympiaStateIR.Resource readResource() {
      return new OlympiaStateIR.Resource(
          readAddr(),
          readUint256(),
          readBool(),
          readOptionalInt(),
          readString(),
          readString(),
          readString(),
          readString(),
          readString());
    }

    private ImmutableList<OlympiaStateIR.Account> readAccounts() {
      return readList(() -> new OlympiaStateIR.Account(readPublicKeyBytes()));
    }

    private ImmutableList<OlympiaStateIR.AccountBalance> readBalances() {
      return readList(() -> new OlympiaStateIR.AccountBalance(readInt(), readInt(), readBigInt()));
    }

    private ImmutableList<OlympiaStateIR.Stake> readStakes() {
      return readList(() -> new OlympiaStateIR.Stake(readInt(), readInt(), readUint256()));
    }

    private <T> ImmutableList<T> readList(Supplier<T> supplier) {
      final var size = readInt();
      return IntStream.range(0, size)
          .mapToObj(unused -> supplier.get())
          .collect(ImmutableList.toImmutableList());
    }

    private REAddr readAddr() {
      final var len = readNBytes(1)[0];
      final var addrBytes = readNBytes(len);
      return REAddr.of(addrBytes);
    }

    private HashCode readPublicKeyBytes() {
      return HashCode.fromBytes(readNBytes(ECPublicKey.COMPRESSED_BYTES));
    }

    private Optional<Integer> readOptionalInt() {
      return readBool() ? Optional.of(readInt()) : Optional.empty();
    }

    private int readInt() {
      return Ints.fromByteArray(readNBytes(Integer.BYTES));
    }

    private long readLong() {
      return Longs.fromByteArray(readNBytes(Long.BYTES));
    }

    private BigInteger readBigInt() {
      final var size = readInt();
      final var bytes = readNBytes(size);
      return new BigInteger(bytes);
    }

    private UInt256 readUint256() {
      return UInt256.from(readNBytes(UInt256.BYTES));
    }

    private boolean readBool() {
      return readNBytes(1)[0] == 0x01;
    }

    private String readString() {
      final var size = readInt();
      return new String(readNBytes(size), StandardCharsets.UTF_8);
    }

    private byte[] readNBytes(int len) {
      try {
        return bais.readNBytes(len);
      } catch (IOException e) {
        throw new OlympiaStateIRSerializationException("Failed to read bytes", e);
      }
    }
  }
}
