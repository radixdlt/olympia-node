package com.radixdlt.tempo;

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
	 * Gets the initial state.
	 * @return The initial state
	 */
	T initialState();

	/**
	 * Gets the next state after applying a given action
	 * @param state The previous state
	 * @param action The action to apply
	 * @return The next state
	 */
	T reduce(T state, TempoAction action);
}
