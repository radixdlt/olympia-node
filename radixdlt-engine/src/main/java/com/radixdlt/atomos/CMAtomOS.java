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
import com.google.common.reflect.TypeToken;
import com.radixdlt.constraintmachine.DownProcedure;
import com.radixdlt.constraintmachine.PermissionLevel;
import com.radixdlt.constraintmachine.Procedures;
import com.radixdlt.constraintmachine.ReducerResult2;
import com.radixdlt.constraintmachine.ReducerState;
import com.radixdlt.constraintmachine.VoidParticle;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Predicate;
import com.radixdlt.constraintmachine.Particle;
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

	private static final ParticleDefinition<Particle> VOID_PARTICLE_DEF = ParticleDefinition.builder()
		.staticValidation(v -> {
			throw new UnsupportedOperationException("Should not ever call here");
		})
		.allowTransitionsFromOutsideScrypts()
		.build();

	private static final ParticleDefinition<REAddrParticle> RRI_PARTICLE_DEF = ParticleDefinition.<REAddrParticle>builder()
		.staticValidation(rri -> Result.success())
		.rriMapper(REAddrParticle::getAddr)
		.virtualizeUp(v ->
			v.getAddr().getType() == REAddr.REAddrType.NATIVE_TOKEN
			|| v.getAddr().getType() == REAddr.REAddrType.HASHED_KEY
			// PUB_KEY type is already an account so cannot down
		)
		.allowTransitionsFromOutsideScrypts()
		.build();

	private final Map<Class<? extends Particle>, ParticleDefinition<Particle>> particleDefinitions = new HashMap<>();
	private final Set<String> systemNames;
	private Procedures procedures = Procedures.empty();
	public static final String NAME_REGEX = "[a-z0-9]+";
	public static final Pattern NAME_PATTERN = Pattern.compile(NAME_REGEX);

	public static class REAddrClaim implements ReducerState {
		private final byte[] arg;
		private final REAddrParticle addrParticle;

		public REAddrClaim(REAddrParticle addrParticle, byte[] arg) {
			this.addrParticle = addrParticle;
			this.arg = arg;
		}

		public byte[] getArg() {
			return arg;
		}

		public REAddr getAddr() {
			return this.addrParticle.getAddr();
		}

		@Override
		public TypeToken<? extends ReducerState> getTypeToken() {
			return TypeToken.of(REAddrClaim.class);
		}
	}

	public CMAtomOS(Set<String> systemNames) {
		// RRI particle is a low level particle managed by the OS used for the management of all other resources
		this.particleDefinitions.put(VoidParticle.class, VOID_PARTICLE_DEF);
		this.systemNames = systemNames;

		this.load(os -> {
			os.registerParticle(REAddrParticle.class, RRI_PARTICLE_DEF);
			os.createDownProcedure(new DownProcedure<>(
				REAddrParticle.class, VoidReducerState.class,
				(d, r) -> {
					var name = new String(d.getArg().orElseThrow());
					return systemNames.contains(name) || d.getSubstate().getAddr().isNativeToken()
						? PermissionLevel.SYSTEM : PermissionLevel.USER;
				},
				(d, r, pubKey) -> pubKey.map(k -> d.getSubstate().allow(k, d.getArg())).orElse(false),
				(d, s, r) -> {
					if (d.getArg().isEmpty()) {
						return ReducerResult2.error("REAddr must be claimed with a name");
					}
					var arg = d.getArg().get();
					if (!NAME_PATTERN.matcher(new String(arg)).matches()) {
						return ReducerResult2.error("invalid rri name");
					}
					return ReducerResult2.incomplete(new REAddrClaim(d.getSubstate(), d.getArg().get()));
				}
			));
		});
	}

	public CMAtomOS() {
		this(Set.of());
	}

	public void load(ConstraintScrypt constraintScrypt) {
		var constraintScryptEnv = new ConstraintScryptEnv(ImmutableMap.copyOf(particleDefinitions));
		constraintScrypt.main(constraintScryptEnv);
		this.particleDefinitions.putAll(constraintScryptEnv.getScryptParticleDefinitions());
		this.procedures = this.procedures.combine(constraintScryptEnv.getProcedures());
	}

	public Procedures getProcedures() {
		return procedures;
	}

	public Function<Particle, Result> buildParticleStaticCheck() {
		final ImmutableMap<Class<? extends Particle>, ParticleDefinition<Particle>> particleDefinitions
			= ImmutableMap.copyOf(this.particleDefinitions);
		return p -> {
			final ParticleDefinition<Particle> particleDefinition = particleDefinitions.get(p.getClass());
			if (particleDefinition == null) {
				return Result.error("Unknown particle type: " + p.getClass());
			}

			final Function<Particle, Result> staticValidation = particleDefinition.getStaticValidation();
			final Result staticCheckResult = staticValidation.apply(p);
			if (staticCheckResult.isError()) {
				return staticCheckResult;
			}

			return Result.success();
		};
	}

	public Predicate<Particle> virtualizedUpParticles() {
		Map<? extends Class<? extends Particle>, Predicate<Particle>> virtualizedParticles = particleDefinitions.entrySet().stream()
			.filter(def -> def.getValue().getVirtualizeSpin() != null)
			.collect(Collectors.toMap(Map.Entry::getKey, def -> def.getValue().getVirtualizeSpin()));

		return p -> {
			var virtualizer = virtualizedParticles.get(p.getClass());
			return virtualizer != null && virtualizer.test(p);
		};
	}
}
