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

package com.radixdlt.application.system.scrypt;

import com.radixdlt.application.system.state.EpochData;
import com.radixdlt.application.system.state.RoundData;
import com.radixdlt.application.system.state.VirtualParent;
import com.radixdlt.application.tokens.scrypt.TokenHoldingBucket;
import com.radixdlt.atom.REFieldSerialization;
import com.radixdlt.atom.SubstateTypeId;
import com.radixdlt.atomos.ConstraintScrypt;
import com.radixdlt.atomos.Loader;
import com.radixdlt.atomos.SubstateDefinition;
import com.radixdlt.application.system.state.UnclaimedREAddr;
import com.radixdlt.constraintmachine.Authorization;
import com.radixdlt.constraintmachine.DownProcedure;
import com.radixdlt.constraintmachine.ExecutionContext;
import com.radixdlt.constraintmachine.PermissionLevel;
import com.radixdlt.constraintmachine.ReducerState;
import com.radixdlt.constraintmachine.UpProcedure;
import com.radixdlt.constraintmachine.VoidReducerState;
import com.radixdlt.constraintmachine.exceptions.InvalidHashedKeyException;
import com.radixdlt.constraintmachine.exceptions.ProcedureException;
import com.radixdlt.constraintmachine.ReducerResult;
import com.radixdlt.constraintmachine.SystemCallProcedure;
import com.radixdlt.identifiers.REAddr;
import com.radixdlt.utils.Bytes;

import java.util.Arrays;
import java.util.EnumSet;
import java.util.LinkedList;
import java.util.Set;

import static com.radixdlt.identifiers.Naming.NAME_PATTERN;

public final class SystemConstraintScrypt implements ConstraintScrypt {
	private final Set<String> systemNames;

	public SystemConstraintScrypt(Set<String> systemNames) {
		this.systemNames = systemNames;
	}

	private static class AllocatingSystem implements ReducerState {
	}

	private static class AllocatingVirtualState implements ReducerState {
		private final LinkedList<SubstateTypeId> substatesToVirtualize = new LinkedList<>();
		AllocatingVirtualState() {
			substatesToVirtualize.add(SubstateTypeId.VALIDATOR_META_DATA);
			substatesToVirtualize.add(SubstateTypeId.VALIDATOR_STAKE_DATA);
			substatesToVirtualize.add(SubstateTypeId.VALIDATOR_ALLOW_DELEGATION_FLAG);
			substatesToVirtualize.add(SubstateTypeId.VALIDATOR_REGISTERED_FLAG_COPY);
			substatesToVirtualize.add(SubstateTypeId.VALIDATOR_RAKE_COPY);
			substatesToVirtualize.add(SubstateTypeId.VALIDATOR_OWNER_COPY);
		}

		public ReducerState createVirtualSubstate(VirtualParent virtualParent) throws ProcedureException {
			var typeId = substatesToVirtualize.remove(0);
			if (!Arrays.equals(virtualParent.getData(), new byte[] {typeId.id()})) {
				throw new ProcedureException("Expected " + typeId + " but was " + Bytes.toHexString(virtualParent.getData()));
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
			return unclaimedREAddr.getAddr();
		}
	}

	public static class REAddrClaimStart implements ReducerState {
		private final byte[] arg;
		public REAddrClaimStart(byte[] arg) {
			this.arg = arg;
		}

		public ReducerState claim(UnclaimedREAddr unclaimedREAddr, ExecutionContext ctx) throws ProcedureException, InvalidHashedKeyException {
			if (ctx.permissionLevel() != PermissionLevel.SYSTEM) {
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
					buf.put(s.getData());
				}
			)
		);

		// TODO: Down singleton
		os.procedure(new UpProcedure<>(
			VoidReducerState.class, VirtualParent.class,
			u -> new Authorization(PermissionLevel.SYSTEM, (r, c) -> { }),
			(s, u, c, r) -> {
				if (u.getData().length != 1) {
					throw new ProcedureException("Invalid data: " + Bytes.toHexString(u.getData()));
				}
				if (u.getData()[0] != SubstateTypeId.UNCLAIMED_READDR.id()) {
					throw new ProcedureException("Invalid data: " + Bytes.toHexString(u.getData()));
				}
				return ReducerResult.complete();
			}
		));

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
					buf.putLong(s.getEpoch());
				}
			)
		);

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
					buf.putLong(s.getView());
					buf.putLong(s.getTimestamp());
				}
			)
		);

		os.procedure(
			new SystemCallProcedure<>(
				TokenHoldingBucket.class, REAddr.ofSystem(),
				() -> new Authorization(PermissionLevel.USER, (r, c) -> { }),
				(s, d, c) -> {
					var id = d.get(0);
					var syscall = Syscall.of(id).orElseThrow(() -> new ProcedureException("Invalid call type " + id));
					if (syscall != Syscall.FEE_RESERVE_PUT) {
						throw new ProcedureException("Invalid call type: " + syscall);
					}

					var amt = d.getUInt256(1);
					var tokens = s.withdraw(REAddr.ofNativeToken(), amt);
					c.depositFeeReserve(tokens);
					return ReducerResult.incomplete(s);
				}
			)
		);

		os.procedure(
			new SystemCallProcedure<>(
				VoidReducerState.class, REAddr.ofSystem(),
				() -> new Authorization(PermissionLevel.USER, (r, c) -> { }),
				(s, d, c) -> {
					var id = d.get(0);
					var syscall = Syscall.of(id).orElseThrow(() -> new ProcedureException("Invalid call type " + id));
					if (syscall == Syscall.FEE_RESERVE_TAKE) {
						var amt = d.getUInt256(1);
						var tokens = c.withdrawFeeReserve(amt);
						return ReducerResult.incomplete(new TokenHoldingBucket(tokens));
					} else if (syscall == Syscall.READDR_CLAIM) {
						var bytes = d.getRemainingBytes(1);
						var str = new String(bytes);
						if (systemNames.contains(str) && c.permissionLevel() != PermissionLevel.SYSTEM) {
							throw new ProcedureException("Not allowed to use name " + str);
						}
						if (!NAME_PATTERN.matcher(str).matches()) {
							throw new ProcedureException("invalid rri name: " + str);
						}

						return ReducerResult.incomplete(new REAddrClaimStart(bytes));
					} else {
						throw new ProcedureException("Invalid call type: " + syscall);
					}
				}
			)
		);

		// PUB_KEY type is already claimed by accounts
		var claimableAddrTypes =
			EnumSet.of(REAddr.REAddrType.NATIVE_TOKEN, REAddr.REAddrType.HASHED_KEY, REAddr.REAddrType.SYSTEM);
		os.substate(
			new SubstateDefinition<>(
				UnclaimedREAddr.class,
				SubstateTypeId.UNCLAIMED_READDR.id(),
				buf -> {
					throw new UnsupportedOperationException();
				},
				(s, buf) -> {
					throw new UnsupportedOperationException();
				},
				buf -> {
					var addr = REFieldSerialization.deserializeREAddr(buf, claimableAddrTypes);
					return new UnclaimedREAddr(addr);
				},
				(a, buf) -> {
					REFieldSerialization.serializeREAddr(buf, (REAddr) a);
					return new UnclaimedREAddr((REAddr) a);
				}
			));

		os.procedure(new DownProcedure<>(
			REAddrClaimStart.class, UnclaimedREAddr.class,
			d -> {
				final PermissionLevel permissionLevel;
				if (d.getAddr().isNativeToken() || d.getAddr().isSystem()) {
					permissionLevel = PermissionLevel.SYSTEM;
				} else {
					permissionLevel = PermissionLevel.USER;
				}
				return new Authorization(permissionLevel, (r, ctx) -> { });
			},
			(d, s, r, c) -> ReducerResult.incomplete(s.claim(d, c))
		));

		// For Mainnet Genesis
		os.procedure(new UpProcedure<>(
			SystemConstraintScrypt.REAddrClaim.class, EpochData.class,
			u -> new Authorization(PermissionLevel.SYSTEM, (r, c) -> { }),
			(s, u, c, r) -> {
				if (u.getEpoch() != 0) {
					throw new ProcedureException("First epoch must be 0.");
				}

				return ReducerResult.incomplete(new AllocatingSystem());
			}
		));
		os.procedure(new UpProcedure<>(
			AllocatingSystem.class, RoundData.class,
			u -> new Authorization(PermissionLevel.SYSTEM, (r, c) -> { }),
			(s, u, c, r) -> {
				if (u.getView() != 0) {
					throw new ProcedureException("First view must be 0.");
				}
				return ReducerResult.incomplete(new AllocatingVirtualState());
			}
		));
		os.procedure(new UpProcedure<>(
			AllocatingVirtualState.class, VirtualParent.class,
			u -> new Authorization(PermissionLevel.SYSTEM, (r, c) -> { }),
			(s, u, c, r) -> {
				var next = s.createVirtualSubstate(u);
				return next == null ? ReducerResult.complete() : ReducerResult.incomplete(next);
			}
		));
	}
}
