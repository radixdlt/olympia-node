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

package com.radixdlt.application.validators.scrypt;

import com.radixdlt.application.system.state.EpochData;
import com.radixdlt.application.validators.state.ValidatorRegisteredCopy;
import com.radixdlt.atom.REFieldSerialization;
import com.radixdlt.atom.SubstateTypeId;
import com.radixdlt.atomos.ConstraintScrypt;
import com.radixdlt.atomos.Loader;
import com.radixdlt.atomos.SubstateDefinition;
import com.radixdlt.constraintmachine.Authorization;
import com.radixdlt.constraintmachine.DownProcedure;
import com.radixdlt.constraintmachine.PermissionLevel;
import com.radixdlt.constraintmachine.ReadProcedure;
import com.radixdlt.constraintmachine.ReducerResult;
import com.radixdlt.constraintmachine.ReducerState;
import com.radixdlt.constraintmachine.UpProcedure;
import com.radixdlt.constraintmachine.VoidReducerState;
import com.radixdlt.constraintmachine.exceptions.AuthorizationException;
import com.radixdlt.constraintmachine.exceptions.ProcedureException;
import com.radixdlt.crypto.ECPublicKey;
import java.util.OptionalLong;

public class ValidatorRegisterConstraintScrypt implements ConstraintScrypt {
  private static class UpdatingRegistered implements ReducerState {
    private final ECPublicKey validatorKey;
    private final EpochData epochData;

    UpdatingRegistered(ECPublicKey validatorKey, EpochData epochData) {
      this.validatorKey = validatorKey;
      this.epochData = epochData;
    }

    void update(ValidatorRegisteredCopy update) throws ProcedureException {
      if (!update.validatorKey().equals(validatorKey)) {
        throw new ProcedureException("Cannot update validator");
      }

      var expectedEpoch = epochData.epoch() + 1;
      if (update.epochUpdate().orElseThrow() != expectedEpoch) {
        throw new ProcedureException(
            "Expected epoch to be " + expectedEpoch + " but is " + update.epochUpdate());
      }
    }
  }

  private static class UpdatingRegisteredNeedToReadEpoch implements ReducerState {
    private final ECPublicKey validatorKey;

    UpdatingRegisteredNeedToReadEpoch(ECPublicKey validatorKey) {
      this.validatorKey = validatorKey;
    }

    ReducerState readEpoch(EpochData epochData) {
      return new UpdatingRegistered(validatorKey, epochData);
    }
  }

  @Override
  public void main(Loader os) {
    os.substate(
        new SubstateDefinition<>(
            ValidatorRegisteredCopy.class,
            SubstateTypeId.VALIDATOR_REGISTERED_FLAG_COPY.id(),
            buf -> {
              REFieldSerialization.deserializeReservedByte(buf);
              var epochUpdate = REFieldSerialization.deserializeOptionalNonNegativeLong(buf);
              var key = REFieldSerialization.deserializeKey(buf);
              var flag = REFieldSerialization.deserializeBoolean(buf);
              return new ValidatorRegisteredCopy(epochUpdate, key, flag);
            },
            (s, buf) -> {
              REFieldSerialization.serializeReservedByte(buf);
              REFieldSerialization.serializeOptionalLong(buf, s.epochUpdate());
              REFieldSerialization.serializeKey(buf, s.validatorKey());
              buf.put((byte) (s.isRegistered() ? 1 : 0));
            },
            buf -> REFieldSerialization.deserializeKey(buf),
            (k, buf) -> REFieldSerialization.serializeKey(buf, (ECPublicKey) k),
            k -> new ValidatorRegisteredCopy(OptionalLong.empty(), (ECPublicKey) k, false)));

    os.procedure(
        new DownProcedure<>(
            VoidReducerState.class,
            ValidatorRegisteredCopy.class,
            d ->
                new Authorization(
                    PermissionLevel.USER,
                    (r, c) -> {
                      if (!c.key().map(d.validatorKey()::equals).orElse(false)) {
                        throw new AuthorizationException("Key does not match.");
                      }
                    }),
            (d, s, r, c) -> {
              return ReducerResult.incomplete(
                  new UpdatingRegisteredNeedToReadEpoch(d.validatorKey()));
            }));

    os.procedure(
        new ReadProcedure<>(
            UpdatingRegisteredNeedToReadEpoch.class,
            EpochData.class,
            u -> new Authorization(PermissionLevel.USER, (r, c) -> {}),
            (s, u, r) -> ReducerResult.incomplete(s.readEpoch(u))));

    os.procedure(
        new UpProcedure<>(
            UpdatingRegistered.class,
            ValidatorRegisteredCopy.class,
            u -> new Authorization(PermissionLevel.USER, (r, c) -> {}),
            (s, u, c, r) -> {
              s.update(u);
              return ReducerResult.complete();
            }));
  }
}
