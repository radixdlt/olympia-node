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

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;

import java.util.Collections;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import com.radixdlt.constraintmachine.Spin;

import static com.radixdlt.constraintmachine.Spin.NEUTRAL;
import static com.radixdlt.constraintmachine.Spin.UP;
import static com.radixdlt.constraintmachine.Spin.DOWN;

/**
 * The spin state machine used for an instance of a particle.
 */
public final class SpinStateMachine {
	private SpinStateMachine() {
		throw new IllegalStateException("Cannot instantiate.");
	}

	/**
	 * Represents a Spin State Machine transition
	 */
	public static final class Transition {

		private final Spin from;
		private final Spin to;
		private static final Map<Spin, Map<Spin, Transition>> transitionMap = new EnumMap<>(Spin.class);

		static {
			for (Spin from : Spin.values()) {
				final EnumMap<Spin, Transition> map = new EnumMap<>(Spin.class);
				for (Spin to : Spin.values()) {
					map.put(to, new Transition(from, to));
				}
				transitionMap.put(from, map);
			}
		}
		Transition(Spin from, Spin to) {
			this.from = from;
			this.to = to;
		}

		public Spin from() {
			return from;
		}

		public Spin to() {
			return to;
		}

		public static Transition of(Spin from, Spin to) {
			Objects.requireNonNull(from);
			Objects.requireNonNull(to);

			return transitionMap.get(from).get(to);
		}

		@Override
		public boolean equals(Object o) {
			if (!(o instanceof Transition)) {
				return false;
			}
			Transition t = (Transition) o;
			return t.from == this.from && t.to == this.to;
		}

		@Override
		public int hashCode() {
			return Objects.hash(from, to);
		}

	}
	private static class SpinStateIndexes {
		private static final ImmutableMap<Spin, Spin> NEXT = ImmutableMap.of(
			NEUTRAL, UP,
			UP, DOWN
		);

		private static final ImmutableMap<Spin, Spin> PREV = ImmutableMap.of(
			DOWN, UP,
			UP, NEUTRAL
		);

		private static final ImmutableSet<Transition> VALID_TRANSITIONS = ImmutableSet.of(
			Transition.of(NEUTRAL, UP),
			Transition.of(UP, DOWN)
		);

		private static final Map<Spin, Set<Spin>> STATES_AFTER_INDEX = ImmutableMap.of(
			NEUTRAL, EnumSet.of(UP, DOWN),
			UP, EnumSet.of(DOWN),
			DOWN, Collections.emptySet()
		);

		private static final Map<Spin, Set<Spin>> STATES_BEFORE_INDEX = ImmutableMap.of(
			NEUTRAL, Collections.emptySet(),
			UP, EnumSet.of(NEUTRAL),
			DOWN, EnumSet.of(NEUTRAL, UP)
		);
	}

	public static Spin next(Spin current) {
		final Spin nextSpin = SpinStateIndexes.NEXT.get(current);
		if (nextSpin == null) {
			throw new IllegalArgumentException("No spin after " + current);
		}
		return nextSpin;
	}

	public static Spin prev(Spin current) {
		final Spin prevSpin = SpinStateIndexes.PREV.get(current);
		if (prevSpin == null) {
			throw new IllegalArgumentException("No spin before " + current);
		}
		return prevSpin;
	}

	/**
	 * Checks if a spin state is after a given spin state non-inclusive in
	 * the sequential spin state machine.
	 *
	 * @param check the spin to check if its after
	 * @param base the baseline spin state to check against
	 * @return true if check is after base, false otherwise
	 */
	public static boolean isAfter(Spin check, Spin base) {
		return SpinStateIndexes.STATES_AFTER_INDEX.get(base).contains(check);
	}

	/**
	 * Checks if a spin state is before a given spin state non-inclusive in
	 * the sequential spin state machine.
	 *
	 * @param check the spin to check if its before
	 * @param base the baseline spin state to check against
	 * @return true if beforeCheck is before cur, false otherwise
	 */
	public static boolean isBefore(Spin check, Spin base) {
		return SpinStateIndexes.STATES_BEFORE_INDEX.get(base).contains(check);
	}

	public static boolean canTransition(Spin from, Spin to) {
		return SpinStateIndexes.VALID_TRANSITIONS.contains(Transition.of(from, to));
	}
}
