/*
 * (C) Copyright 2020 Radix DLT Ltd
 *
 * Radix DLT Ltd licenses this file to you under the Apache License,
 * Version 2.0 (the "License"); you may not use this file except in
 * compliance with the License.  You may obtain a copy of the
 * License at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND,
 * either express or implied.  See the License for the specific
 * language governing permissions and limitations under the License.
 */

package com.radixdlt.atomos;

import com.google.common.collect.ImmutableMap;
import com.radixdlt.atom.REFieldSerialization;
import com.radixdlt.atom.SubstateTypeId;
import com.radixdlt.constraintmachine.Authorization;
import com.radixdlt.constraintmachine.DownProcedure;
import com.radixdlt.constraintmachine.ExecutionContext;
import com.radixdlt.constraintmachine.Particle;
import com.radixdlt.constraintmachine.PermissionLevel;
import com.radixdlt.constraintmachine.Procedures;
import com.radixdlt.constraintmachine.ReducerResult;
import com.radixdlt.constraintmachine.ReducerState;
import com.radixdlt.constraintmachine.SubstateDeserialization;
import com.radixdlt.constraintmachine.SubstateSerialization;
import com.radixdlt.constraintmachine.VirtualIndex;
import com.radixdlt.constraintmachine.exceptions.ProcedureException;
import com.radixdlt.identifiers.REAddr;
import com.radixdlt.serialization.DeserializeException;

import java.nio.ByteBuffer;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;

/**
 * Implementation of the AtomOS interface on top of a UTXO based Constraint Machine.
 */
public final class CMAtomOS {
	private final Map<Class<? extends Particle>, SubstateDefinition<? extends Particle>> substateDefinitions = new HashMap<>();
	private Procedures procedures = Procedures.empty();

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

		public ReducerState claim(UnclaimedREAddr unclaimedREAddr, ExecutionContext ctx) throws ProcedureException {
			if (ctx.permissionLevel() != PermissionLevel.SYSTEM
				&& !ctx.key().map(k -> unclaimedREAddr.allow(k, arg)).orElse(false)) {
				throw new ProcedureException("Invalid key/arg combination.");
			}
			return new REAddrClaim(unclaimedREAddr, arg);
		}
	}

	public CMAtomOS() {
		// PUB_KEY type is already claimed by accounts
		var claimableAddrTypes =
			EnumSet.of(REAddr.REAddrType.NATIVE_TOKEN, REAddr.REAddrType.HASHED_KEY, REAddr.REAddrType.SYSTEM);

		load(os -> {
			os.substate(
				new SubstateDefinition<>(
					UnclaimedREAddr.class,
					SubstateTypeId.UNCLAIMED_READDR.id(),
					buf -> {
						REFieldSerialization.deserializeReservedByte(buf);
						var rri = REFieldSerialization.deserializeREAddr(buf, claimableAddrTypes);
						return new UnclaimedREAddr(rri);
					},
					(s, buf) -> {
						REFieldSerialization.serializeReservedByte(buf);
						REFieldSerialization.serializeREAddr(buf, s.getAddr());
					},
					() -> Set.of(
						new VirtualIndex(new byte[] {SubstateTypeId.UNCLAIMED_READDR.id(), 0, REAddr.REAddrType.SYSTEM.byteValue()}),
						new VirtualIndex(new byte[] {SubstateTypeId.UNCLAIMED_READDR.id(), 0, REAddr.REAddrType.NATIVE_TOKEN.byteValue()}),
						new VirtualIndex(new byte[] {SubstateTypeId.UNCLAIMED_READDR.id(), 0, REAddr.REAddrType.HASHED_KEY.byteValue()},
							3, 26)
					)
				)
			);

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
		});
	}

	public void load(ConstraintScrypt constraintScrypt) {
		var constraintScryptEnv = new ConstraintScryptEnv(ImmutableMap.copyOf(substateDefinitions));
		constraintScrypt.main(constraintScryptEnv);
		substateDefinitions.putAll(constraintScryptEnv.getScryptParticleDefinitions());
		procedures = procedures.combine(constraintScryptEnv.getProcedures());
	}

	public Procedures getProcedures() {
		return procedures;
	}

	public SubstateDeserialization buildSubstateDeserialization() {
		return new SubstateDeserialization(substateDefinitions.values());
	}

	public SubstateSerialization buildSubstateSerialization() {
		return new SubstateSerialization(substateDefinitions.values());
	}

	public Predicate<ByteBuffer> virtualizedUpParticles() {
		var deserialization = buildSubstateDeserialization();
		return p -> {
			var start = p.position();
			var typeByte = p.get();
			Class<? extends Particle> c;
			try {
				c = deserialization.byteToClass(typeByte);
			} catch (DeserializeException e) {
				return false;
			}

			var def = substateDefinitions.get(c);
			for (var virtualized : def.getVirtualized()) {
				p.position(start);
				if (virtualized.test(p)) {
					return true;
				}
			}
			return false;
		};
	}
}
