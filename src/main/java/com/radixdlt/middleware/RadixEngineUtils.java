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

package com.radixdlt.middleware;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableList.Builder;
import com.google.common.collect.ImmutableMap;
import com.radixdlt.common.Atom;
import com.radixdlt.constraintmachine.DataPointer;
import com.radixdlt.constraintmachine.CMInstruction;
import com.radixdlt.constraintmachine.CMMicroInstruction;
import com.radixdlt.serialization.DsonOutput.Output;
import com.radixdlt.serialization.Serialization;
import com.radixdlt.serialization.SerializationException;
import com.radixdlt.store.SpinStateMachine;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import com.radixdlt.constraintmachine.Particle;
import com.radixdlt.constraintmachine.Spin;

/**
 * Utility class for low level Constraint Machine "hardware" level validation.
 */
public final class RadixEngineUtils {
	private static final int MAX_ATOM_SIZE = 1024 * 1024;

	private RadixEngineUtils() {
		throw new IllegalStateException("Cannot instantiate.");
	}

	public static class CMAtomConversionException extends Exception {
		private final DataPointer dataPointer;
		CMAtomConversionException(DataPointer dataPointer, String error) {
			super(error);
			this.dataPointer = dataPointer;
		}

		public DataPointer getDataPointer() {
			return dataPointer;
		}
	}

	static ImmutableList<CMMicroInstruction> toCMMicroInstructions(List<ParticleGroup> particleGroups) throws CMAtomConversionException {
		final HashMap<Particle, Spin> spins = new HashMap<>();
		final ImmutableList.Builder<CMMicroInstruction> microInstructionsBuilder = new Builder<>();
		for (int i = 0; i < particleGroups.size(); i++) {
			ParticleGroup pg = particleGroups.get(i);
			final HashSet<Particle> seen = new HashSet<>();
			for (int j = 0; j < pg.getParticleCount(); j++) {
				SpunParticle sp = pg.getSpunParticle(j);
				Particle particle = sp.getParticle();

				if (seen.contains(particle)) {
					throw new CMAtomConversionException(DataPointer.ofParticle(i, j), "Particle transition must be unique in group");
				}
				seen.add(particle);

				Spin currentSpin = spins.get(particle);
				if (currentSpin == null) {
					Spin checkSpin = SpinStateMachine.prev(sp.getSpin());
					microInstructionsBuilder.add(CMMicroInstruction.checkSpin(particle, checkSpin));
				} else {
					if (!SpinStateMachine.canTransition(currentSpin, sp.getSpin())) {
						throw new CMAtomConversionException(DataPointer.ofParticle(i, j), "Invalid internal spin");
					}
				}

				spins.put(particle, sp.getSpin());
				microInstructionsBuilder.add(CMMicroInstruction.push(particle));
			}
			microInstructionsBuilder.add(CMMicroInstruction.particleGroup());
		}

		return microInstructionsBuilder.build();
	}

	public static SimpleRadixEngineAtom toCMAtom(Atom atom) throws CMAtomConversionException {
		final int computedSize;
		try {
			computedSize = Serialization.getDefault().toDson(atom, Output.PERSIST).length;
		} catch (SerializationException e) {
			throw new IllegalStateException("Could not compute size", e);
		}
		if (computedSize > MAX_ATOM_SIZE) {
			throw new CMAtomConversionException(DataPointer.ofAtom(), "Atom too big");
		}

		final ImmutableList<CMMicroInstruction> microInstructions = toCMMicroInstructions(atom.getParticleGroups());
		final CMInstruction cmInstruction = new CMInstruction(
			microInstructions,
			atom.getHash(),
			ImmutableMap.copyOf(atom.getSignatures())
		);

		return new SimpleRadixEngineAtom(atom, cmInstruction);
	}
}
