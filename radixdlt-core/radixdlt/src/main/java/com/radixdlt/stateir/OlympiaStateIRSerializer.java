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

import com.google.common.hash.HashCode;
import com.google.common.primitives.Bytes;
import com.radixdlt.identifiers.REAddr;
import com.radixdlt.utils.Ints;
import com.radixdlt.utils.Longs;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;

/** A serializer for the Olympia state IR (intermediate representation). */
public final class OlympiaStateIRSerializer {

  public byte[] serialize(OlympiaStateIR state) throws IOException {
    final var baos = new ByteArrayOutputStream();
    writeValidators(baos, state);
    writeResources(baos, state);
    writeAccounts(baos, state);
    writeBalances(baos, state);
    writeStakes(baos, state);
    baos.write(Longs.toByteArray(state.lastConsensusTimestamp()));
    baos.write(Longs.toByteArray(state.lastEpoch()));
    return baos.toByteArray();
  }

  private void writeValidators(ByteArrayOutputStream baos, OlympiaStateIR state)
      throws IOException {
    serializeList(baos, state.validators(), OlympiaStateIRSerializer::serializeValidator);
  }

  private static byte[] serializeValidator(OlympiaStateIR.Validator validator) {
    return Bytes.concat(
        serializePublicKeyBytes(validator.publicKeyBytes()),
        serializeString(validator.name()),
        serializeString(validator.url()),
        boolToByteArr(validator.allowsDelegation()),
        boolToByteArr(validator.isRegistered()),
        validator.totalStakedXrd().toByteArray(),
        validator.totalStakeUnits().toByteArray(),
        Ints.toByteArray(validator.feeProportionInTenThousandths()),
        Ints.toByteArray(validator.ownerAccountIndex()));
  }

  private static void writeResources(ByteArrayOutputStream baos, OlympiaStateIR state)
      throws IOException {
    serializeList(baos, state.resources(), OlympiaStateIRSerializer::serializeResource);
  }

  private static byte[] serializeResource(OlympiaStateIR.Resource resource) {
    return Bytes.concat(
        serializerReAddr(resource.addr()),
        resource.granularity().toByteArray(),
        boolToByteArr(resource.isMutable()),
        serializeOptionalInt(resource.ownerAccountIndex()),
        serializeString(resource.symbol()),
        serializeString(resource.name()),
        serializeString(resource.description()),
        serializeString(resource.iconUrl()),
        serializeString(resource.url()));
  }

  private static void writeAccounts(ByteArrayOutputStream baos, OlympiaStateIR state)
      throws IOException {
    serializeList(
        baos, state.accounts(), account -> serializePublicKeyBytes(account.publicKeyBytes()));
  }

  private static void writeBalances(ByteArrayOutputStream baos, OlympiaStateIR state)
      throws IOException {
    serializeList(
        baos,
        state.balances(),
        accountBalance ->
            Bytes.concat(
                Ints.toByteArray(accountBalance.accountIndex()),
                Ints.toByteArray(accountBalance.resourceIndex()),
                serializeBigInt(accountBalance.amount())));
  }

  private static void writeStakes(ByteArrayOutputStream baos, OlympiaStateIR state)
      throws IOException {
    serializeList(
        baos,
        state.stakes(),
        stake ->
            Bytes.concat(
                Ints.toByteArray(stake.accountIndex()),
                Ints.toByteArray(stake.validatorIndex()),
                stake.stakeUnitAmount().toByteArray()));
  }

  private static byte[] serializePublicKeyBytes(HashCode publicKeyBytes) {
    return publicKeyBytes.asBytes();
  }

  private static byte[] serializeOptionalInt(Optional<Integer> optInt) {
    return Bytes.concat(
        boolToByteArr(optInt.isPresent()), optInt.map(Ints::toByteArray).orElse(new byte[] {}));
  }

  private static <T> void serializeList(
      ByteArrayOutputStream baos, List<T> list, Function<T, byte[]> serializeItem)
      throws IOException {
    final var sizePrefix = list.size();
    baos.write(Ints.toByteArray(sizePrefix));
    for (T item : list) {
      baos.write(serializeItem.apply(item));
    }
  }

  private static byte[] serializerReAddr(REAddr addr) {
    final var addrBytes = addr.getBytes();
    final var len = addrBytes.length;
    if (len > Byte.MAX_VALUE) {
      throw new OlympiaStateIRSerializationException("REAddr is too long", null);
    }
    return Bytes.concat(
        new byte[] {(byte) len}, // A single byte is sufficient for the length prefix
        addrBytes);
  }

  private static byte[] serializeString(String s) {
    final var bytes = s.getBytes(StandardCharsets.UTF_8);
    final var sizePrefix = Ints.toByteArray(bytes.length);
    return Bytes.concat(sizePrefix, bytes);
  }

  private static byte[] boolToByteArr(boolean b) {
    return b ? new byte[] {0x01} : new byte[] {0x00};
  }

  private static byte[] serializeBigInt(BigInteger bi) {
    final var bytes = bi.toByteArray();
    return Bytes.concat(Ints.toByteArray(bytes.length), bytes);
  }
}
