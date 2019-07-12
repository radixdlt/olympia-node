package com.radixdlt.store;

import com.radixdlt.atoms.Particle;
import com.radixdlt.atoms.Spin;
import com.radixdlt.common.EUID;
import java.util.Optional;
import java.util.Set;
import java.util.function.Predicate;

/**
 * Utility methods for managing and virtualizing state stores
 */
public final class StateStores {
	private StateStores() {
		throw new IllegalStateException("Cannot instantiate.");
	}

	private static final StateStore EMPTY_STATE_STORE = new StateStore() {
		@Override
		public boolean supports(Set<EUID> destinations) {
			return true;
		}

		@Override
		public Optional<Spin> getSpin(Particle particle) {
			return Optional.empty();
		}
	};

	/**
	 * An empty state store which returns neutral spin for every particle
	 * @return an empty state store
	 */
	public static StateStore empty() {
		return EMPTY_STATE_STORE;
	}

	/**
	 * Virtualizes the default spin for a given particle predicate. That is,
	 * the given spin is the default spin for particles when the given predicate
	 * passes.
	 *
	 * @param base the base state store
	 * @param particleCheck the particle predicate
	 * @param spin the default spin to virtualize with
	 * @return the virtualized state store
	 */
	public static StateStore virtualizeDefault(StateStore base, Predicate<Particle> particleCheck, Spin spin) {
		return new StateStore() {
			@Override
			public boolean supports(Set<EUID> destinations) {
				return base.supports(destinations);
			}

			@Override
			public Optional<Spin> getSpin(Particle particle) {
				Optional<Spin> curSpin = base.getSpin(particle);

				if (base.supports(particle.getDestinations())
					&& particleCheck.test(particle)
					&& curSpin.map(s -> SpinStateMachine.isAfter(spin, s)).orElse(true)
				) {
					return Optional.of(spin);
				}

				return curSpin;
			}
		};
	}

	/**
	 * Virtualizes the spin for a given particle predicate. That is,
	 * the given spin is always returned when the given predicate
	 * passes.
	 *
	 * @param base the base state store
	 * @param particleCheck the particle predicate
	 * @param spin the spin to always return given predicate success
	 * @return the virtualized state store
	 */
	public static StateStore virtualizeOverwrite(StateStore base, Predicate<Particle> particleCheck, Spin spin) {
		return new StateStore() {
			@Override
			public boolean supports(Set<EUID> destinations) {
				return base.supports(destinations);
			}

			@Override
			public Optional<Spin> getSpin(Particle particle) {
				if (particleCheck.test(particle)) {
					return Optional.of(spin);
				}

				return base.getSpin(particle);
			}
		};
	}
}
