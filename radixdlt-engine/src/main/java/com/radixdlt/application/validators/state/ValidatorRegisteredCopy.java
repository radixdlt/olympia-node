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

package com.radixdlt.application.validators.state;

import static com.radixdlt.atom.REFieldSerialization.*;

import com.radixdlt.application.system.scrypt.SentencingData;
import com.radixdlt.atom.REFieldSerialization;
import com.radixdlt.atom.SubstateTypeId;
import com.radixdlt.atomos.SubstateDefinition;
import com.radixdlt.crypto.ECPublicKey;
import com.radixdlt.serialization.DeserializeException;
import java.nio.ByteBuffer;
import java.util.OptionalLong;

@SuppressWarnings("OptionalUsedAsFieldOrParameterType")
public abstract sealed class ValidatorRegisteredCopy implements ValidatorUpdatingData {
  private final OptionalLong epochUpdate;
  private final ECPublicKey validatorKey;
  private final boolean isRegistered;

  protected ValidatorRegisteredCopy(
      OptionalLong epochUpdate, ECPublicKey validatorKey, boolean isRegistered) {
    this.epochUpdate = epochUpdate;
    this.validatorKey = validatorKey;
    this.isRegistered = isRegistered;
  }

  public OptionalLong epochUpdate() {
    return epochUpdate;
  }

  public ECPublicKey validatorKey() {
    return validatorKey;
  }

  public boolean isRegistered() {
    return isRegistered;
  }

  public abstract void serialize(ByteBuffer buf);

  public static final byte V1 = 0;
  public static final byte V2 = 1;

  public static final SubstateDefinition<ValidatorRegisteredCopy> SUBSTATE_DEFINITION =
      SubstateDefinition.create(
          ValidatorRegisteredCopy.class,
          SubstateTypeId.VALIDATOR_REGISTERED_FLAG_COPY,
          buf -> {
            var version = deserializeReservedByteAsVersion(buf, 1);
            var epochUpdate = deserializeOptionalNonNegativeLong(buf);
            var key = deserializeKey(buf);
            var registeredState = deserializeBoolean(buf);

            return switch (version) {
              case V1 -> ValidatorRegisteredCopy.createV1(epochUpdate, key, registeredState);

              case V2 -> {
                var pendingRegistration = deserializeBoolean(buf);
                var jailedEpoch = deserializeNonNegativeLong(buf);
                var jailLevel = deserializeNonNegativeInt(buf);
                var probationEpochsLeft = deserializeNonNegativeInt(buf);

                yield ValidatorRegisteredCopy.createV2(
                    epochUpdate,
                    key,
                    registeredState,
                    jailedEpoch,
                    jailLevel,
                    probationEpochsLeft,
                    pendingRegistration);
              }

              default -> throw new DeserializeException(
                  "Unsupported version of ValidatorRegisteredCopy: " + version);
            };
          },
          ValidatorRegisteredCopy::serialize,
          REFieldSerialization::deserializeKey,
          (key, buf) -> serializeKey(buf, (ECPublicKey) key),
          key -> ValidatorRegisteredCopy.createVirtual((ECPublicKey) key));

  /** Initial version of the particle, liveness slashing data omitted */
  public static final class ValidatorRegisteredCopyV1 extends ValidatorRegisteredCopy {
    public ValidatorRegisteredCopyV1(
        OptionalLong epochUpdate, ECPublicKey validatorKey, boolean isRegistered) {
      super(epochUpdate, validatorKey, isRegistered);
    }

    @Override
    public void serialize(ByteBuffer buf) {
      serializeReservedByteAsVersion(buf, V1);
      serializeOptionalLong(buf, epochUpdate());
      serializeKey(buf, validatorKey());
      buf.put((byte) (isRegistered() ? 1 : 0));
    }
  }

  /** V2 of the particle, with liveness slashing data included */
  public static final class ValidatorRegisteredCopyV2 extends ValidatorRegisteredCopy {
    private final long jailedEpoch;
    private final int jailLevel;
    private final int probationEpochsLeft;

    private final boolean pendingRegistration;

    public ValidatorRegisteredCopyV2(
        OptionalLong epochUpdate,
        ECPublicKey validatorKey,
        boolean isRegistered,
        long jailedEpoch,
        int jailLevel,
        int probationEpochsLeft,
        boolean pendingRegistration) {
      super(epochUpdate, validatorKey, isRegistered);

      this.jailedEpoch = jailedEpoch;
      this.jailLevel = jailLevel;
      this.probationEpochsLeft = probationEpochsLeft;
      this.pendingRegistration = pendingRegistration;
    }

    @Override
    public void serialize(ByteBuffer buf) {
      serializeReservedByteAsVersion(buf, V2);
      serializeOptionalLong(buf, epochUpdate());
      serializeKey(buf, validatorKey());
      buf.put((byte) (isRegistered() ? 1 : 0));
      buf.put((byte) (isRegistrationPending() ? 1 : 0));
      buf.putLong(jailedEpoch);
      buf.putInt(jailLevel);
      buf.putInt(probationEpochsLeft);
    }

    public long jailedEpoch() {
      return jailedEpoch;
    }

    public int jailLevel() {
      return jailLevel;
    }

    public int probationEpochsLeft() {
      return probationEpochsLeft;
    }

    public boolean isRegistrationPending() {
      return pendingRegistration;
    }
  }

  public static ValidatorRegisteredCopy createV1(
      OptionalLong epochUpdate, ECPublicKey validatorKey, boolean isRegistered) {
    return new ValidatorRegisteredCopyV1(epochUpdate, validatorKey, isRegistered);
  }

  public static ValidatorRegisteredCopy createV2(
      OptionalLong epochUpdate,
      ECPublicKey validatorKey,
      boolean isRegistered,
      long jailedEpoch,
      int jailLevel,
      int probationEpochsLeft,
      boolean pendingRegistration) {
    return new ValidatorRegisteredCopyV2(
        epochUpdate,
        validatorKey,
        isRegistered,
        jailedEpoch,
        jailLevel,
        probationEpochsLeft,
        pendingRegistration);
  }

  public static ValidatorRegisteredCopy createV2(
      OptionalLong epochUpdate,
      ECPublicKey validatorKey,
      boolean isRegistered,
      SentencingData sentencingData) {
    return new ValidatorRegisteredCopyV2(
        epochUpdate,
        validatorKey,
        isRegistered,
        sentencingData.jailedEpoch(),
        sentencingData.jailLevel(),
        sentencingData.probationEpochsLeft(),
        sentencingData.registrationPending());
  }

  public static ValidatorRegisteredCopy createVirtual(ECPublicKey validatorKey) {
    return createV1(OptionalLong.empty(), validatorKey, false);
  }
}
