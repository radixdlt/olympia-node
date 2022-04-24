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
import com.radixdlt.application.system.state.ValidatorStakeData;
import com.radixdlt.application.validators.state.ValidatorFeeCopy;
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
import com.radixdlt.serialization.DeserializeException;
import java.util.Objects;
import java.util.OptionalLong;

public final class ValidatorUpdateRakeConstraintScrypt implements ConstraintScrypt {
  public static final int RAKE_PERCENTAGE_GRANULARITY = 10 * 10; // 100 == 1.00%, 1 == 0.01%
  public static final int RAKE_MAX = 100 * RAKE_PERCENTAGE_GRANULARITY;
  public static final int RAKE_MIN = 0;
  public static final int MAX_RAKE_INCREASE = 10 * RAKE_PERCENTAGE_GRANULARITY; // 10%

  private final long rakeIncreaseDebounceEpochLength;

  public ValidatorUpdateRakeConstraintScrypt(long rakeIncreaseDebounceEpochLength) {
    this.rakeIncreaseDebounceEpochLength = rakeIncreaseDebounceEpochLength;
  }

  private class UpdatingRakeReady implements ReducerState {
    private final EpochData epochData;
    private final ValidatorStakeData stakeData;

    UpdatingRakeReady(EpochData epochData, ValidatorStakeData stakeData) {
      this.epochData = epochData;
      this.stakeData = stakeData;
    }

    void update(ValidatorFeeCopy update) throws ProcedureException {
      if (!Objects.equals(stakeData.validatorKey(), update.validatorKey())) {
        throw new ProcedureException("Must update same key");
      }

      var rakeIncrease = update.curRakePercentage() - stakeData.rakePercentage();
      if (rakeIncrease > MAX_RAKE_INCREASE) {
        throw new ProcedureException(
            "Max rake increase is "
                + MAX_RAKE_INCREASE
                + " but trying to increase "
                + rakeIncrease);
      }

      var epoch =
          update
              .epochUpdate()
              .orElseThrow(() -> new ProcedureException("Must contain epoch update"));
      if (rakeIncrease > 0) {
        var expectedEpoch = epochData.epoch() + 1 + rakeIncreaseDebounceEpochLength;
        if (epoch != expectedEpoch) {
          throw new ProcedureException(
              "Increasing rake requires epoch delay to " + expectedEpoch + " but was " + epoch);
        }
      } else {
        var expectedEpoch = epochData.epoch() + 1;
        if (epoch != expectedEpoch) {
          throw new ProcedureException(
              "Decreasing rake requires epoch delay to " + expectedEpoch + " but was " + epoch);
        }
      }
    }
  }

  private class UpdatingRakeNeedToReadCurrentRake implements ReducerState {
    private final ECPublicKey validatorKey;

    UpdatingRakeNeedToReadCurrentRake(ECPublicKey validatorKey) {
      this.validatorKey = validatorKey;
    }

    public ReducerState readValidatorStakeState(ValidatorStakeData validatorStakeData)
        throws ProcedureException {
      if (!validatorStakeData.validatorKey().equals(validatorKey)) {
        throw new ProcedureException("Invalid key update");
      }

      return new UpdatingRakeNeedToReadEpoch(validatorStakeData);
    }
  }

  private class UpdatingRakeNeedToReadEpoch implements ReducerState {
    private final ValidatorStakeData validatorStakeData;

    private UpdatingRakeNeedToReadEpoch(ValidatorStakeData validatorStakeData) {
      this.validatorStakeData = validatorStakeData;
    }

    ReducerState readEpoch(EpochData epochData) {
      return new UpdatingRakeReady(epochData, validatorStakeData);
    }
  }

  @Override
  public void main(Loader os) {
    os.substate(
        new SubstateDefinition<>(
            ValidatorFeeCopy.class,
            SubstateTypeId.VALIDATOR_RAKE_COPY.id(),
            buf -> {
              REFieldSerialization.deserializeReservedByte(buf);
              OptionalLong epochUpdate =
                  REFieldSerialization.deserializeOptionalNonNegativeLong(buf);
              var key = REFieldSerialization.deserializeKey(buf);
              var curRakePercentage = REFieldSerialization.deserializeInt(buf);
              if (curRakePercentage < RAKE_MIN || curRakePercentage > RAKE_MAX) {
                throw new DeserializeException("Invalid rake percentage " + curRakePercentage);
              }

              return new ValidatorFeeCopy(epochUpdate, key, curRakePercentage);
            },
            (s, buf) -> {
              REFieldSerialization.serializeReservedByte(buf);
              REFieldSerialization.serializeOptionalLong(buf, s.epochUpdate());
              REFieldSerialization.serializeKey(buf, s.validatorKey());
              buf.putInt(s.curRakePercentage());
            },
            buf -> REFieldSerialization.deserializeKey(buf),
            (k, buf) -> REFieldSerialization.serializeKey(buf, (ECPublicKey) k),
            k -> ValidatorFeeCopy.createVirtual((ECPublicKey) k)));

    os.procedure(
        new DownProcedure<>(
            VoidReducerState.class,
            ValidatorFeeCopy.class,
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
                  new UpdatingRakeNeedToReadCurrentRake(d.validatorKey()));
            }));
    os.procedure(
        new ReadProcedure<>(
            UpdatingRakeNeedToReadEpoch.class,
            EpochData.class,
            u -> new Authorization(PermissionLevel.USER, (r, c) -> {}),
            (s, u, r) -> ReducerResult.incomplete(s.readEpoch(u))));
    os.procedure(
        new ReadProcedure<>(
            UpdatingRakeNeedToReadCurrentRake.class,
            ValidatorStakeData.class,
            u -> new Authorization(PermissionLevel.USER, (r, c) -> {}),
            (s, u, r) -> ReducerResult.incomplete(s.readValidatorStakeState(u))));

    os.procedure(
        new UpProcedure<>(
            UpdatingRakeReady.class,
            ValidatorFeeCopy.class,
            u -> new Authorization(PermissionLevel.USER, (r, c) -> {}),
            (s, u, c, r) -> {
              s.update(u);
              return ReducerResult.complete();
            }));
  }
}
