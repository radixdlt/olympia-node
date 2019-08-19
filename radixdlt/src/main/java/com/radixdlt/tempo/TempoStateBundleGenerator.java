package com.radixdlt.tempo;

import com.radixdlt.tempo.reactive.TempoState;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

class TempoStateBundleGenerator {
	static final TempoStateBundle EMPTY_BUNDLE = new TempoStateBundle() {
		@Override
		public <T extends TempoState> T get(Class<T> stateClass) {
			throw new TempoException("Requested state '" + stateClass.getSimpleName() + "' was not required");
		}
	};

	private final Map<Class<? extends TempoState>, TempoState> states;

	TempoStateBundleGenerator() {
		this.states = new HashMap<>();
	}

	void put(Class<? extends TempoState> stateClass, TempoState state) {
		this.states.put(stateClass, state);
	}

	<T extends TempoState> T get(Class<T> stateClass) {
		return (T) states.get(stateClass);
	}

	TempoStateBundle bundleFor(Set<Class<? extends TempoState>> requiredStates) {
		if (requiredStates.isEmpty()) {
			return EMPTY_BUNDLE;
		}

		Map<Class<? extends TempoState>, TempoState> statesCopy = new HashMap<>(states);
		return new TempoStateBundle() {
			@Override
			public <T extends TempoState> T get(Class<T> stateClass) {
				if (!requiredStates.contains(stateClass)) {
					throw new TempoException("Requested state '" + stateClass.getSimpleName() + "' was not required");
				}
				T state = (T) statesCopy.get(stateClass);
				if (state == null) {
					throw new TempoException("Required state '" + stateClass.getSimpleName() + "' is not available");
				}
				return state;
			}
		};
	}
}
