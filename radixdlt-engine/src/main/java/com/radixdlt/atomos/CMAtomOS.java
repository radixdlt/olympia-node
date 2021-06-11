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
import com.radixdlt.constraintmachine.AuthorizationException;
import com.radixdlt.constraintmachine.Authorization;
import com.radixdlt.constraintmachine.DownProcedure;
import com.radixdlt.constraintmachine.PermissionLevel;
import com.radixdlt.constraintmachine.ProcedureException;
import com.radixdlt.constraintmachine.Procedures;
import com.radixdlt.constraintmachine.ReducerResult;
import com.radixdlt.constraintmachine.ReducerState;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;
import com.radixdlt.constraintmachine.Particle;
import com.radixdlt.constraintmachine.SubstateDeserialization;
import com.radixdlt.constraintmachine.SubstateSerialization;
import com.radixdlt.constraintmachine.VoidReducerState;
import com.radixdlt.identifiers.REAddr;

import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Implementation of the AtomOS interface on top of a UTXO based Constraint Machine.
 */
// FIXME: rawtypes
@SuppressWarnings("rawtypes")
public final class CMAtomOS {
	private final Map<Class<? extends Particle>, SubstateDefinition<? extends Particle>> substateDefinitions = new HashMap<>();
	private final Set<String> systemNames;
	private Procedures procedures = Procedures.empty();
	public static final String NAME_REGEX = "[a-z0-9]+";
	public static final Pattern NAME_PATTERN = Pattern.compile(NAME_REGEX);

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
			return this.unclaimedREAddr.getAddr();
		}
	}

	public CMAtomOS(Set<String> systemNames) {
		// RRI particle is a low level particle managed by the OS used for the management of all other resources
		this.systemNames = systemNames;

		this.load(os -> {
			os.substate(
				new SubstateDefinition<>(
					UnclaimedREAddr.class,
					Set.of(SubstateTypeId.UNCLAIMED_READDR.id()),
					(b, buf) -> {
						var rri = REFieldSerialization.deserializeREAddr(buf);
						return new UnclaimedREAddr(rri);
					},
					(s, buf) -> {
						buf.put(SubstateTypeId.UNCLAIMED_READDR.id());
						var rri = s.getAddr();
						REFieldSerialization.serializeREAddr(buf, rri);
					},
					v -> v.getAddr().getType() == REAddr.REAddrType.NATIVE_TOKEN
						|| v.getAddr().getType() == REAddr.REAddrType.HASHED_KEY
						|| v.getAddr().isSystem()
					// PUB_KEY type is already claimed by accounts
				)
			);
			os.procedure(new DownProcedure<>(
				UnclaimedREAddr.class, VoidReducerState.class,
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
		this.substateDefinitions.putAll(constraintScryptEnv.getScryptParticleDefinitions());
		this.procedures = this.procedures.combine(constraintScryptEnv.getProcedures());
	}

	public Procedures getProcedures() {
		return procedures;
	}

	public SubstateDeserialization buildSubstateDeserialization() {
		return new SubstateDeserialization(this.substateDefinitions.values());
	}

	public SubstateSerialization buildSubstateSerialization() {
		return new SubstateSerialization(this.substateDefinitions.values());
	}

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
