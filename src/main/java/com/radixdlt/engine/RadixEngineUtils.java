package com.radixdlt.engine;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Streams;
import com.radixdlt.atoms.DataPointer;
import com.radixdlt.atoms.ImmutableAtom;
import com.radixdlt.constraintmachine.CMAtom;
import com.radixdlt.constraintmachine.CMError;
import com.radixdlt.constraintmachine.CMErrorCode;
import com.radixdlt.constraintmachine.CMParticle;
import com.radixdlt.serialization.DsonOutput.Output;
import com.radixdlt.serialization.Serialization;
import com.radixdlt.serialization.SerializationException;
import com.radixdlt.store.CMStore;
import com.radixdlt.store.SpinStateMachine;
import java.util.List;
import java.util.Map;
import java.util.function.BiFunction;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import com.radixdlt.atoms.IndexedSpunParticle;
import com.radixdlt.atoms.Particle;
import com.radixdlt.atoms.Spin;
import com.radixdlt.atoms.SpunParticle;
import com.radixdlt.store.SpinStateTransitionValidator;
import com.radixdlt.store.SpinStateTransitionValidator.TransitionCheckResult;
import com.radixdlt.store.CMStores;
import com.radixdlt.common.Pair;

/**
 * Utility class for low level Constraint Machine "hardware" level validation.
 */
public final class RadixEngineUtils {
	private static final int MAX_ATOM_SIZE = 1024 * 1024;

	private RadixEngineUtils() {
		throw new IllegalStateException("Cannot instantiate.");
	}

	static Map<Particle, ImmutableList<IndexedSpunParticle>> getTransitionsByParticle(ImmutableAtom atom) {
		return atom.indexedSpunParticles()
			.collect(Collectors.groupingBy(
				indexed -> indexed.getSpunParticle().getParticle(),
				ImmutableList.toImmutableList())
			);
	}

	private static CMErrorCode checkNextSpin(
		Particle particle,
		Spin nextSpin,
		Spin oldSpin
	) {
		CMStore engineStore = oldSpin == null
			? CMStores.empty()
			: CMStores.virtualizeOverwrite(CMStores.empty(), particle::equals, oldSpin);

		TransitionCheckResult result = SpinStateTransitionValidator.checkParticleTransition(
			particle,
			nextSpin, engineStore
		);

		final CMErrorCode error;

		switch (result) {
			case OKAY:
				// Follow through
			case MISSING_STATE:
				// Follow through
			case MISSING_STATE_FROM_UNSUPPORTED_SHARD:
				error = null;
				break;
			case CONFLICT:
				error = CMErrorCode.INTERNAL_SPIN_CONFLICT;
				break;
			case MISSING_DEPENDENCY:
				// Missing dependency is okay if this is the first spin of the particle in the atom
				if (oldSpin == null) {
					error = null;
				} else {
					error = CMErrorCode.INTERNAL_SPIN_MISSING_DEPENDENCY;
				}
				break;
			case ILLEGAL_TRANSITION_TO:
				error = CMErrorCode.ILLEGAL_SPIN_TRANSITION;
				break;
			default:
				throw new IllegalStateException("Should not be here. Unhandled error: " + result);
		}

		return error;
	}

	private static <T> Stream<T> mapPairs(
		List<IndexedSpunParticle> spunParticles,
		BiFunction<Particle, Pair<SpunParticle, IndexedSpunParticle>, T> mapper
	) {
		return Streams.mapWithIndex(spunParticles.stream(),
			(indexed, i) -> {
				final SpunParticle prev = i == 0 ? null : spunParticles.get((int) (i - 1)).getSpunParticle();
				return mapper.apply(
					// TODO: clean this part up
					spunParticles.get(0).getSpunParticle().getParticle(),
					Pair.of(prev, indexed)
				);
			});
	}

	/**
	 * Analyze the spins of a particle in an atom.
	 *
	 * @param spunParticles the particle in an atom to analyze
	 * @return map containing each particle and pointers to results of each spun instance
	 */
	static Stream<CMError> checkInternalSpins(List<IndexedSpunParticle> spunParticles) {
		return mapPairs(spunParticles, (pp, pair) -> {
			final SpunParticle prev = pair.getFirst();
			final IndexedSpunParticle indexed = pair.getSecond();
			final CMErrorCode error = checkNextSpin(
				pp,
				indexed.getSpunParticle().getSpin(),
				prev == null ? null : prev.getSpin()
			);
			if (error != null) {
				return Stream.of(new CMError(indexed.getDataPointer(), error));
			} else {
				return Stream.<CMError>empty();
			}
		}).flatMap(l -> l);
	}

	/**
	 * Returns an error for every particle group in the given atom
	 * contains an empty particle group.
	 *
	 * @param atom the atom to check
	 * @return stream of errors for every empty particle group
	 */
	static Stream<CMError> checkParticleGroupsNotEmpty(ImmutableAtom atom) {
		return atom.indexedParticleGroups()
			.filter(i -> i.getParticleGroup().isEmpty())
			.map(i -> new CMError(i.getDataPointer(), CMErrorCode.EMPTY_PARTICLE_GROUP));
	}

	/**
	 * Returns an error for every particle which is encountered more than
	 * once in a particle group within an atom
	 *
	 * @param atom the atom to check
	 * @return stream of errors for every duplicated particle
	 */
	static Stream<CMError> checkParticleTransitionsUniqueInGroup(ImmutableAtom atom) {
		return atom.indexedParticleGroups()
			.flatMap(i -> {
				final Map<Particle, List<IndexedSpunParticle>> particlesInGroup =
					i.indexedSpunParticles().collect(Collectors.groupingBy(ip -> ip.getSpunParticle().getParticle()));
				return particlesInGroup.entrySet().stream()
					.filter(e -> e.getValue().size() > 1)
					.map(e -> new CMError(i.getDataPointer(), CMErrorCode.DUPLICATE_PARTICLES_IN_GROUP));
			});
	}

	public static class CMAtomConversionException extends Exception {
		private final ImmutableSet<CMError> errors;
		CMAtomConversionException(ImmutableSet<CMError> errors) {
			this.errors = errors;
		}

		public ImmutableSet<CMError> getErrors() {
			return errors;
		}
	}

	public static CMAtom toCMAtom(ImmutableAtom atom) throws CMAtomConversionException {
		// TODO: Move to more appropriate place
		final int computedSize;
		try {
			computedSize = Serialization.getDefault().toDson(atom, Output.PERSIST).length;
		} catch (SerializationException e) {
			throw new IllegalStateException("Could not compute size", e);
		}
		if (computedSize > MAX_ATOM_SIZE) {
			throw new CMAtomConversionException(ImmutableSet.of(
				new CMError(DataPointer.ofAtom(), CMErrorCode.KERNEL_ERROR, null, "Atom too big")
			));
		}

		final Map<Particle, ImmutableList<IndexedSpunParticle>> spunParticles = RadixEngineUtils.getTransitionsByParticle(atom);
		final Stream<CMError> badSpinErrs = spunParticles.entrySet().stream()
			.flatMap(e -> RadixEngineUtils.checkInternalSpins(e.getValue()));
		final Stream<CMError> conversionErrs = Streams.concat(
			RadixEngineUtils.checkParticleGroupsNotEmpty(atom),
			RadixEngineUtils.checkParticleTransitionsUniqueInGroup(atom),
			badSpinErrs
		);

		ImmutableSet<CMError> errors = conversionErrs.collect(ImmutableSet.toImmutableSet());
		if (!errors.isEmpty()) {
			throw new CMAtomConversionException(errors);
		}

		final ImmutableList<CMParticle> cmParticles =
			spunParticles.entrySet().stream()
				.map(e -> {
					ImmutableList<IndexedSpunParticle> sp = e.getValue();
					Spin checkSpin = SpinStateMachine.prev(sp.get(0).getSpunParticle().getSpin());
					return new CMParticle(e.getKey(), sp.get(0).getDataPointer(), checkSpin, sp.size());
				})
				.collect(ImmutableList.toImmutableList());
		return new CMAtom(atom, cmParticles);
	}
}
