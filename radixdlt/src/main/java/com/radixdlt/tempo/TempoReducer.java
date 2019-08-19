package com.radixdlt.tempo;

import com.google.common.collect.ImmutableSet;

import java.util.Set;

/**
 * A state reducer in Tempo
 */
public interface TempoReducer<T extends TempoState> {
	/**
	 * Gets the underlying state class.
	 * @return The state class
	 */
	Class<T> stateClass();

	/**
	 * Gets the additionally required state classes.
	 * TODO remove requiredState in reducers, shouldn't require any other state
	 * @return The additionally required state
	 */
	default Set<Class<? extends TempoState>> requiredState() {
		return ImmutableSet.of();
	}

	/**
	 * Gets the initial state.
	 * @return The initial state
	 */
	T initialState();

	/**
	 * Gets the next state after applying a given action
	 * @param prevState The previous state
	 * @param action The action to apply
	 * @return The next state
	 */
	T reduce(T prevState, TempoStateBundle bundle, TempoAction action);
}
