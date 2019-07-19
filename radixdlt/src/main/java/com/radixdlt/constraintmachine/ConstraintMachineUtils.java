package com.radixdlt.constraintmachine;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.Streams;
import com.radixdlt.atoms.ImmutableAtom;
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
import com.radixdlt.store.CMStore;
import com.radixdlt.store.CMStores;
import com.radixdlt.common.Pair;

/**
 * Utility class for low level Constraint Machine "hardware" level validation.
 */
final class ConstraintMachineUtils {
	private ConstraintMachineUtils() {
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
		Spin oldSpin,
		CMStore localCMStore
	) {
		CMStore cmStore = oldSpin == null
			? localCMStore
			: CMStores.virtualizeOverwrite(localCMStore, particle::equals, oldSpin);

		TransitionCheckResult result = SpinStateTransitionValidator.checkParticleTransition(
			particle,
			nextSpin, cmStore
		);

		final CMErrorCode error;

		switch(result) {
			case OKAY:
				error = null;
				break;
			case MISSING_STATE:
				// Follow through
			case MISSING_STATE_FROM_UNSUPPORTED_SHARD:
				error = CMErrorCode.UNKNOWN_PARTICLE;
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

	private static <T> Stream<T> mapPairs(List<IndexedSpunParticle> spunParticles, BiFunction<Particle, Pair<SpunParticle, IndexedSpunParticle>, T> mapper) {
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
	 * @param localCMStore the local store to analyze spins on top of, relevant because of virtualized particles
	 * @return map containing each particle and pointers to results of each spun instance
	 */
	static Stream<CMError> checkInternalSpins(List<IndexedSpunParticle> spunParticles, CMStore localCMStore) {
		return mapPairs(spunParticles, (pp, pair) -> {
			final SpunParticle prev = pair.getFirst();
			final IndexedSpunParticle indexed = pair.getSecond();
			final CMErrorCode error = checkNextSpin(
				pp,
				indexed.getSpunParticle().getSpin(),
				prev == null ? null : prev.getSpin(), localCMStore
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
}
