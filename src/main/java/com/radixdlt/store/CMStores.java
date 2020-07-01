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

package com.radixdlt.store;

import com.radixdlt.constraintmachine.Particle;
import com.radixdlt.constraintmachine.Spin;
import java.util.function.Predicate;

/**
 * Utility methods for managing and virtualizing state stores
 */
public final class CMStores {
	private CMStores() {
		throw new IllegalStateException("Cannot instantiate.");
	}

	private static final CMStore EMPTY_STATE_STORE = particle -> Spin.NEUTRAL;

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
		return particle -> {
			Spin curSpin = base.getSpin(particle);

			if (particleCheck.test(particle) && SpinStateMachine.isAfter(spin, curSpin)
			) {
				return spin;
			}

			return curSpin;
		};
	}
}
