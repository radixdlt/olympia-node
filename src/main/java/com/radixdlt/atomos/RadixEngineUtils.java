package com.radixdlt.atomos;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableList.Builder;
import com.google.common.collect.ImmutableMap;
import com.radixdlt.atoms.DataPointer;
import com.radixdlt.atoms.ImmutableAtom;
import com.radixdlt.atoms.ParticleGroup;
import com.radixdlt.constraintmachine.CMInstruction;
import com.radixdlt.constraintmachine.CMMicroInstruction;
import com.radixdlt.serialization.DsonOutput.Output;
import com.radixdlt.serialization.Serialization;
import com.radixdlt.serialization.SerializationException;
import com.radixdlt.store.SpinStateMachine;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import com.radixdlt.atoms.Particle;
import com.radixdlt.atoms.Spin;
import com.radixdlt.atoms.SpunParticle;

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

	public static SimpleRadixEngineAtom toCMAtom(ImmutableAtom atom) throws CMAtomConversionException {
		// TODO: Move to more appropriate place
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
