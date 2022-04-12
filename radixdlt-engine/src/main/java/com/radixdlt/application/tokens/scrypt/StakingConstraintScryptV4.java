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

package com.radixdlt.application.tokens.scrypt;

import com.radixdlt.application.system.state.StakeOwnership;
import com.radixdlt.application.tokens.state.PreparedStake;
import com.radixdlt.application.tokens.state.PreparedUnstakeOwnership;
import com.radixdlt.application.validators.state.AllowDelegationFlag;
import com.radixdlt.application.validators.state.ValidatorOwnerCopy;
import com.radixdlt.atom.REFieldSerialization;
import com.radixdlt.atom.SubstateTypeId;
import com.radixdlt.atomos.ConstraintScrypt;
import com.radixdlt.atomos.Loader;
import com.radixdlt.atomos.SubstateDefinition;
import com.radixdlt.constraintmachine.Authorization;
import com.radixdlt.constraintmachine.DownProcedure;
import com.radixdlt.constraintmachine.EndProcedure;
import com.radixdlt.constraintmachine.PermissionLevel;
import com.radixdlt.constraintmachine.ReadProcedure;
import com.radixdlt.constraintmachine.ReducerResult;
import com.radixdlt.constraintmachine.ReducerState;
import com.radixdlt.constraintmachine.UpProcedure;
import com.radixdlt.constraintmachine.VoidReducerState;
import com.radixdlt.constraintmachine.exceptions.InvalidDelegationException;
import com.radixdlt.constraintmachine.exceptions.InvalidResourceException;
import com.radixdlt.constraintmachine.exceptions.MinimumStakeException;
import com.radixdlt.constraintmachine.exceptions.MismatchException;
import com.radixdlt.constraintmachine.exceptions.NotEnoughResourcesException;
import com.radixdlt.constraintmachine.exceptions.ProcedureException;
import com.radixdlt.crypto.ECPublicKey;
import com.radixdlt.identifiers.REAddr;
import com.radixdlt.utils.UInt256;
import java.util.function.Predicate;

public final class StakingConstraintScryptV4 implements ConstraintScrypt {
  private final UInt256 minimumStake;

  public StakingConstraintScryptV4(UInt256 minimumStake) {
    this.minimumStake = minimumStake;
  }

  @Override
  public void main(Loader os) {
    os.substate(
        new SubstateDefinition<>(
            PreparedStake.class,
            SubstateTypeId.PREPARED_STAKE.id(),
            buf -> {
              REFieldSerialization.deserializeReservedByte(buf);
              var delegate = REFieldSerialization.deserializeKey(buf);
              var owner = REFieldSerialization.deserializeAccountREAddr(buf);
              var amount = REFieldSerialization.deserializeNonZeroUInt256(buf);
              return new PreparedStake(amount, owner, delegate);
            },
            (s, buf) -> {
              REFieldSerialization.serializeReservedByte(buf);
              REFieldSerialization.serializeKey(buf, s.delegateKey());
              REFieldSerialization.serializeREAddr(buf, s.owner());
              buf.put(s.amount().toByteArray());
            }));

    os.substate(
        new SubstateDefinition<>(
            PreparedUnstakeOwnership.class,
            SubstateTypeId.PREPARED_UNSTAKE.id(),
            buf -> {
              REFieldSerialization.deserializeReservedByte(buf);
              var delegate = REFieldSerialization.deserializeKey(buf);
              var owner = REFieldSerialization.deserializeAccountREAddr(buf);
              var amount = REFieldSerialization.deserializeNonZeroUInt256(buf);
              return new PreparedUnstakeOwnership(delegate, owner, amount);
            },
            (s, buf) -> {
              REFieldSerialization.serializeReservedByte(buf);
              REFieldSerialization.serializeKey(buf, s.delegateKey());
              REFieldSerialization.serializeREAddr(buf, s.owner());
              buf.put(s.amount().toByteArray());
            }));

    defineStaking(os);
  }

  private final class OwnerStakePrepare implements ReducerState {
    private final TokenHoldingBucket tokenHoldingBucket;
    private final AllowDelegationFlag allowDelegationFlag;

    OwnerStakePrepare(
        TokenHoldingBucket tokenHoldingBucket, AllowDelegationFlag allowDelegationFlag) {
      this.tokenHoldingBucket = tokenHoldingBucket;
      this.allowDelegationFlag = allowDelegationFlag;
    }

    ReducerState readOwner(ValidatorOwnerCopy ownerCopy) throws ProcedureException {
      if (!allowDelegationFlag.validatorKey().equals(ownerCopy.validatorKey())) {
        throw new ProcedureException("Not matching validator keys");
      }
      var owner = ownerCopy.owner();
      return new StakePrepare(
          tokenHoldingBucket, allowDelegationFlag.validatorKey(), owner::equals);
    }
  }

  private final class StakePrepare implements ReducerState {
    private final TokenHoldingBucket tokenHoldingBucket;
    private final ECPublicKey validatorKey;
    private final Predicate<REAddr> delegateAllowed;

    StakePrepare(
        TokenHoldingBucket tokenHoldingBucket,
        ECPublicKey validatorKey,
        Predicate<REAddr> delegateAllowed) {
      this.tokenHoldingBucket = tokenHoldingBucket;
      this.validatorKey = validatorKey;
      this.delegateAllowed = delegateAllowed;
    }

    ReducerState withdrawTo(PreparedStake preparedStake)
        throws MinimumStakeException, NotEnoughResourcesException, InvalidResourceException,
            InvalidDelegationException, MismatchException {

      tokenHoldingBucket.withdraw(preparedStake.resourceAddr(), preparedStake.amount());

      if (preparedStake.amount().compareTo(minimumStake) < 0) {
        throw new MinimumStakeException(minimumStake, preparedStake.amount());
      }
      if (!preparedStake.delegateKey().equals(validatorKey)) {
        throw new MismatchException("Not matching validator keys");
      }

      if (!delegateAllowed.test(preparedStake.owner())) {
        throw new InvalidDelegationException();
      }

      return tokenHoldingBucket;
    }
  }

  private void defineStaking(Loader os) {
    // Stake
    os.procedure(
        new ReadProcedure<>(
            TokenHoldingBucket.class,
            AllowDelegationFlag.class,
            u -> new Authorization(PermissionLevel.USER, (r, c) -> {}),
            (s, d, r) -> {
              var nextState =
                  (!d.allowsDelegation())
                      ? new OwnerStakePrepare(s, d)
                      : new StakePrepare(s, d.validatorKey(), p -> true);
              return ReducerResult.incomplete(nextState);
            }));
    os.procedure(
        new ReadProcedure<>(
            OwnerStakePrepare.class,
            ValidatorOwnerCopy.class,
            u -> new Authorization(PermissionLevel.USER, (r, c) -> {}),
            (s, d, r) -> {
              var nextState = s.readOwner(d);
              return ReducerResult.incomplete(nextState);
            }));
    os.procedure(
        new UpProcedure<>(
            StakePrepare.class,
            PreparedStake.class,
            u -> new Authorization(PermissionLevel.USER, (r, c) -> {}),
            (s, u, c, r) -> {
              var nextState = s.withdrawTo(u);
              return ReducerResult.incomplete(nextState);
            }));

    // Unstake
    os.procedure(
        new DownProcedure<>(
            VoidReducerState.class,
            StakeOwnership.class,
            d -> d.bucket().withdrawAuthorization(),
            (d, s, r, c) -> ReducerResult.incomplete(new StakeOwnershipHoldingBucket(d))));
    // Additional Unstake
    os.procedure(
        new DownProcedure<>(
            StakeOwnershipHoldingBucket.class,
            StakeOwnership.class,
            d -> d.bucket().withdrawAuthorization(),
            (d, s, r, c) -> {
              s.depositOwnership(d);
              return ReducerResult.incomplete(s);
            }));
    // Change
    os.procedure(
        new UpProcedure<>(
            StakeOwnershipHoldingBucket.class,
            StakeOwnership.class,
            u -> new Authorization(PermissionLevel.USER, (r, c) -> {}),
            (s, u, c, r) -> {
              var ownership = s.withdrawOwnership(u.amount());
              if (!ownership.equals(u)) {
                throw new MismatchException(ownership, u);
              }
              return ReducerResult.incomplete(s);
            }));
    os.procedure(
        new UpProcedure<>(
            StakeOwnershipHoldingBucket.class,
            PreparedUnstakeOwnership.class,
            u -> new Authorization(PermissionLevel.USER, (r, c) -> {}),
            (s, u, c, r) -> {
              var unstake = s.unstake(u.amount());
              if (!unstake.equals(u)) {
                throw new MismatchException(unstake, u);
              }
              return ReducerResult.incomplete(s);
            }));

    // Deallocate Stake Holding Bucket
    os.procedure(
        new EndProcedure<>(
            StakeOwnershipHoldingBucket.class,
            s -> new Authorization(PermissionLevel.USER, (r, c) -> {}),
            (s, c, r) -> s.destroy()));
  }
}
