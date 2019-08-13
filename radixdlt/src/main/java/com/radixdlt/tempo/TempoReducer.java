package com.radixdlt.tempo;

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
	 * @return The additionally required state
	 */
	Set<Class<? extends TempoState>> requiredState();

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
