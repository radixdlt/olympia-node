package com.radixdlt.tempo;

import java.util.stream.Stream;

/**
 * A TempoEpic that participates in the {@link TempoAction} flow
 */
public interface TempoEpic {
	/**
	 * Execute this epic with the given action
	 * @param action The action
	 * @return the next actions to be executed given the action
	 */
	Stream<TempoAction> epic(TempoAction action);

	/**
	 * Get the initial actions to be executed once upon starting
	 * @return The initial actions
	 */
	default Stream<TempoAction> initialActions() {
		return Stream.empty();
	}
}
