package com.radixdlt.store;

import com.radixdlt.atoms.Particle;
import com.radixdlt.atoms.Spin;
import com.radixdlt.common.EUID;
import java.util.Set;
import java.util.function.Predicate;

/**
 * Utility methods for managing and virtualizing state stores
 */
public final class CMStores {
	private CMStores() {
		throw new IllegalStateException("Cannot instantiate.");
	}

	private static final CMStore EMPTY_STATE_STORE = new CMStore() {
		@Override
		public boolean supports(Set<EUID> destinations) {
			return true;
		}

		@Override
		public Spin getSpin(Particle particle) {
			return Spin.NEUTRAL;
		}
	};

	/**
	 * An empty state store which returns neutral spin for every particle
	 * @return an empty state store
	 */
	public static CMStore empty() {
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
	public static CMStore virtualizeDefault(CMStore base, Predicate<Particle> particleCheck, Spin spin) {
		return new CMStore() {
			@Override
			public boolean supports(Set<EUID> destinations) {
				return base.supports(destinations);
			}

			@Override
			public Spin getSpin(Particle particle) {
				Spin curSpin = base.getSpin(particle);

				if (base.supports(particle.getDestinations())
					&& particleCheck.test(particle)
					&& SpinStateMachine.isAfter(spin, curSpin)
				) {
					return spin;
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
	public static CMStore virtualizeOverwrite(CMStore base, Predicate<Particle> particleCheck, Spin spin) {
		return new CMStore() {
			@Override
			public boolean supports(Set<EUID> destinations) {
				return base.supports(destinations);
			}

			@Override
			public Spin getSpin(Particle particle) {
				if (particleCheck.test(particle)) {
					return spin;
				}

				return base.getSpin(particle);
			}
		};
	}
}
