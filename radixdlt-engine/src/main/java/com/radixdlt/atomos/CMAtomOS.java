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
import com.radixdlt.constraintmachine.Particle;
import com.radixdlt.constraintmachine.PermissionLevel;
import com.radixdlt.constraintmachine.Procedures;
import com.radixdlt.constraintmachine.ReducerResult;
import com.radixdlt.constraintmachine.ReducerState;
import com.radixdlt.constraintmachine.SubstateDeserialization;
import com.radixdlt.constraintmachine.SubstateSerialization;
import com.radixdlt.constraintmachine.VoidReducerState;
import com.radixdlt.constraintmachine.exceptions.AuthorizationException;
import com.radixdlt.constraintmachine.exceptions.ProcedureException;
import com.radixdlt.identifiers.REAddr;

import java.util.EnumSet;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import static com.radixdlt.identifiers.Naming.NAME_PATTERN;

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

	public CMAtomOS(Set<String> systemNames) {
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
					v -> claimableAddrTypes.contains(v.getAddr().getType())
				)
			);
			os.procedure(new DownProcedure<>(
				VoidReducerState.class, UnclaimedREAddr.class,
				d -> {
					final PermissionLevel permissionLevel;
					if (d.getSubstate().getAddr().isNativeToken() || d.getSubstate().getAddr().isSystem()) {
						permissionLevel = PermissionLevel.SYSTEM;
					} else {
						var name = new String(d.getArg().orElseThrow());
						permissionLevel = systemNames.contains(name) ? PermissionLevel.SYSTEM : PermissionLevel.USER;
					}
					return new Authorization(
						permissionLevel,
						(r, ctx) -> {
							if (!ctx.key().map(k -> d.getSubstate().allow(k, d.getArg())).orElse(false)) {
								throw new AuthorizationException("Invalid key/arg combination.");
							}
						}
					);
				},
				(d, s, r) -> {
					if (d.getSubstate().getAddr().isSystem()) {
						return ReducerResult.incomplete(new REAddrClaim(d.getSubstate(), null));
					}

					if (d.getArg().isEmpty()) {
						throw new ProcedureException("REAddr must be claimed with a name");
					}
					var arg = d.getArg().get();
					if (!NAME_PATTERN.matcher(new String(arg)).matches()) {
						throw new ProcedureException("invalid rri name");
					}
					return ReducerResult.incomplete(new REAddrClaim(d.getSubstate(), d.getArg().get()));
				}
			));
		});
	}

	public CMAtomOS() {
		this(Set.of());
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

	@SuppressWarnings("unchecked")
	public Predicate<Particle> virtualizedUpParticles() {
		Map<? extends Class<? extends Particle>, Predicate<Particle>> virtualizedParticles = substateDefinitions.entrySet().stream()
			.filter(def -> def.getValue().getVirtualized() != null)
			.collect(Collectors.toMap(
				Map.Entry::getKey,
				def -> p -> ((Predicate<Particle>) def.getValue().getVirtualized()).test(p))
			);

		return p -> {
			var virtualizer = virtualizedParticles.get(p.getClass());
			return virtualizer != null && virtualizer.test(p);
		};
	}
}
