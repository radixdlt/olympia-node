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

package com.radixdlt.application.system.scrypt;

import com.radixdlt.application.system.state.EpochData;
import com.radixdlt.application.system.state.RoundData;
import com.radixdlt.application.system.state.UnclaimedREAddr;
import com.radixdlt.application.system.state.VirtualParent;
import com.radixdlt.application.tokens.scrypt.TokenHoldingBucket;
import com.radixdlt.atom.REFieldSerialization;
import com.radixdlt.atom.SubstateTypeId;
import com.radixdlt.atomos.ConstraintScrypt;
import com.radixdlt.atomos.Loader;
import com.radixdlt.atomos.SubstateDefinition;
import com.radixdlt.constraintmachine.Authorization;
import com.radixdlt.constraintmachine.DownProcedure;
import com.radixdlt.constraintmachine.ExecutionContext;
import com.radixdlt.constraintmachine.PermissionLevel;
import com.radixdlt.constraintmachine.ReducerResult;
import com.radixdlt.constraintmachine.ReducerState;
import com.radixdlt.constraintmachine.SystemCallProcedure;
import com.radixdlt.constraintmachine.UpProcedure;
import com.radixdlt.constraintmachine.VoidReducerState;
import com.radixdlt.constraintmachine.exceptions.InvalidHashedKeyException;
import com.radixdlt.constraintmachine.exceptions.ProcedureException;
import com.radixdlt.identifiers.REAddr;
import com.radixdlt.utils.Bytes;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.LinkedList;

public final class SystemConstraintScrypt implements ConstraintScrypt {
  public static final int MAX_SYMBOL_LENGTH = 32;

  private static class AllocatingSystem implements ReducerState {}

  private static class AllocatingVirtualState implements ReducerState {
    private final LinkedList<SubstateTypeId> substatesToVirtualize = new LinkedList<>();

    AllocatingVirtualState() {
      substatesToVirtualize.add(SubstateTypeId.VALIDATOR_META_DATA);
      substatesToVirtualize.add(SubstateTypeId.VALIDATOR_STAKE_DATA);
      substatesToVirtualize.add(SubstateTypeId.VALIDATOR_ALLOW_DELEGATION_FLAG);
      substatesToVirtualize.add(SubstateTypeId.VALIDATOR_REGISTERED_FLAG_COPY);
      substatesToVirtualize.add(SubstateTypeId.VALIDATOR_RAKE_COPY);
      substatesToVirtualize.add(SubstateTypeId.VALIDATOR_OWNER_COPY);
      substatesToVirtualize.add(SubstateTypeId.VALIDATOR_SYSTEM_META_DATA);
    }

    public ReducerState createVirtualSubstate(VirtualParent virtualParent)
        throws ProcedureException {
      var typeId = substatesToVirtualize.remove(0);
      if (!Arrays.equals(virtualParent.data(), new byte[] {typeId.id()})) {
        throw new ProcedureException(
            "Expected " + typeId + " but was " + Bytes.toHexString(virtualParent.data()));
      }
      return substatesToVirtualize.isEmpty() ? null : this;
    }
  }

  public static class REAddrClaim implements ReducerState {
    private final byte[] arg;
    private final UnclaimedREAddr unclaimedREAddr;

    public REAddrClaim(UnclaimedREAddr unclaimedREAddr, byte[] arg) {
      this.unclaimedREAddr = unclaimedREAddr;
      this.arg = arg;
    }

    public byte[] getArg() {
      return arg;
    }

    public REAddr getAddr() {
      return unclaimedREAddr.addr();
    }
  }

  public static class REAddrClaimStart implements ReducerState {
    private final byte[] arg;

    public REAddrClaimStart(byte[] arg) {
      this.arg = arg;
    }

    public ReducerState claim(UnclaimedREAddr unclaimedREAddr, ExecutionContext ctx)
        throws ProcedureException, InvalidHashedKeyException {
      if (ctx.permissionLevel() != PermissionLevel.SYSTEM && !ctx.skipAuthorization()) {
        var key = ctx.key().orElseThrow(() -> new ProcedureException("Missing key"));
        unclaimedREAddr.verifyHashedKey(key, arg);
      }
      return new REAddrClaim(unclaimedREAddr, arg);
    }
  }

  @Override
  public void main(Loader os) {
    os.substate(
        new SubstateDefinition<>(
            VirtualParent.class,
            SubstateTypeId.VIRTUAL_PARENT.id(),
            buf -> {
              REFieldSerialization.deserializeReservedByte(buf);
              var data = new byte[buf.remaining()];
              buf.get(data);
              return new VirtualParent(data);
            },
            (s, buf) -> {
              REFieldSerialization.serializeReservedByte(buf);
              buf.put(s.data());
            }));

    // TODO: Down singleton
    os.procedure(
        new UpProcedure<>(
            VoidReducerState.class,
            VirtualParent.class,
            u -> new Authorization(PermissionLevel.SYSTEM, (r, c) -> {}),
            (s, u, c, r) -> {
              if (u.data().length != 1) {
                throw new ProcedureException("Invalid data: " + Bytes.toHexString(u.data()));
              }
              if (u.data()[0] != SubstateTypeId.UNCLAIMED_READDR.id()) {
                throw new ProcedureException("Invalid data: " + Bytes.toHexString(u.data()));
              }
              return ReducerResult.complete();
            }));

    os.substate(
        new SubstateDefinition<>(
            EpochData.class,
            SubstateTypeId.EPOCH_DATA.id(),
            buf -> {
              REFieldSerialization.deserializeReservedByte(buf);
              var epoch = REFieldSerialization.deserializeNonNegativeLong(buf);
              return new EpochData(epoch);
            },
            (s, buf) -> {
              REFieldSerialization.serializeReservedByte(buf);
              buf.putLong(s.epoch());
            }));

    os.substate(
        new SubstateDefinition<>(
            RoundData.class,
            SubstateTypeId.ROUND_DATA.id(),
            buf -> {
              REFieldSerialization.deserializeReservedByte(buf);
              var view = REFieldSerialization.deserializeNonNegativeLong(buf);
              var timestamp = REFieldSerialization.deserializeNonNegativeLong(buf);
              return new RoundData(view, timestamp);
            },
            (s, buf) -> {
              REFieldSerialization.serializeReservedByte(buf);
              buf.putLong(s.view());
              buf.putLong(s.timestamp());
            }));

    os.procedure(
        new SystemCallProcedure<>(
            TokenHoldingBucket.class,
            REAddr.ofSystem(),
            () -> new Authorization(PermissionLevel.USER, (r, c) -> {}),
            (s, d, c) -> {
              var id = d.get(0);
              var syscall =
                  Syscall.of(id)
                      .orElseThrow(() -> new ProcedureException("Invalid call type " + id));
              if (syscall != Syscall.FEE_RESERVE_PUT) {
                throw new ProcedureException("Invalid call type: " + syscall);
              }

              var amt = d.getUInt256(1);
              var tokens = s.withdraw(REAddr.ofNativeToken(), amt);
              c.depositFeeReserve(tokens);
              return ReducerResult.incomplete(s);
            }));

    os.procedure(
        new SystemCallProcedure<>(
            VoidReducerState.class,
            REAddr.ofSystem(),
            () -> new Authorization(PermissionLevel.USER, (r, c) -> {}),
            (s, d, c) -> {
              var id = d.get(0);
              var syscall =
                  Syscall.of(id)
                      .orElseThrow(() -> new ProcedureException("Invalid call type " + id));
              if (syscall == Syscall.FEE_RESERVE_TAKE) {
                var amt = d.getUInt256(1);
                var tokens = c.withdrawFeeReserve(amt);
                return ReducerResult.incomplete(new TokenHoldingBucket(tokens));
              } else if (syscall == Syscall.READDR_CLAIM) {
                var bytes = d.getRemainingBytes(1);
                if (bytes.length > MAX_SYMBOL_LENGTH) {
                  throw new ProcedureException("Address claim too large.");
                }
                return ReducerResult.incomplete(new REAddrClaimStart(bytes));
              } else {
                throw new ProcedureException("Invalid call type: " + syscall);
              }
            }));

    // PUB_KEY type is already claimed by accounts
    var claimableAddrTypes =
        EnumSet.of(
            REAddr.REAddrType.NATIVE_TOKEN, REAddr.REAddrType.HASHED_KEY, REAddr.REAddrType.SYSTEM);
    os.substate(
        new SubstateDefinition<>(
            UnclaimedREAddr.class,
            SubstateTypeId.UNCLAIMED_READDR.id(),
            buf -> {
              REFieldSerialization.deserializeReservedByte(buf);
              var addr = REFieldSerialization.deserializeREAddr(buf, claimableAddrTypes);
              return new UnclaimedREAddr(addr);
            },
            (s, buf) -> {
              REFieldSerialization.serializeReservedByte(buf);
              REFieldSerialization.serializeREAddr(buf, s.addr());
            },
            buf -> REFieldSerialization.deserializeREAddr(buf, claimableAddrTypes),
            (a, buf) -> REFieldSerialization.serializeREAddr(buf, (REAddr) a),
            k -> new UnclaimedREAddr((REAddr) k)));

    os.procedure(
        new DownProcedure<>(
            REAddrClaimStart.class,
            UnclaimedREAddr.class,
            d -> {
              final PermissionLevel permissionLevel;
              if (d.addr().isNativeToken() || d.addr().isSystem()) {
                permissionLevel = PermissionLevel.SYSTEM;
              } else {
                permissionLevel = PermissionLevel.USER;
              }
              return new Authorization(permissionLevel, (r, ctx) -> {});
            },
            (d, s, r, c) -> ReducerResult.incomplete(s.claim(d, c))));

    // For Mainnet Genesis
    os.procedure(
        new UpProcedure<>(
            SystemConstraintScrypt.REAddrClaim.class,
            EpochData.class,
            u -> new Authorization(PermissionLevel.SYSTEM, (r, c) -> {}),
            (s, u, c, r) -> {
              if (u.epoch() != 0) {
                throw new ProcedureException("First epoch must be 0.");
              }

              return ReducerResult.incomplete(new AllocatingSystem());
            }));
    os.procedure(
        new UpProcedure<>(
            AllocatingSystem.class,
            RoundData.class,
            u -> new Authorization(PermissionLevel.SYSTEM, (r, c) -> {}),
            (s, u, c, r) -> {
              if (u.view() != 0) {
                throw new ProcedureException("First view must be 0.");
              }
              return ReducerResult.incomplete(new AllocatingVirtualState());
            }));
    os.procedure(
        new UpProcedure<>(
            AllocatingVirtualState.class,
            VirtualParent.class,
            u -> new Authorization(PermissionLevel.SYSTEM, (r, c) -> {}),
            (s, u, c, r) -> {
              var next = s.createVirtualSubstate(u);
              return next == null ? ReducerResult.complete() : ReducerResult.incomplete(next);
            }));
  }
}
