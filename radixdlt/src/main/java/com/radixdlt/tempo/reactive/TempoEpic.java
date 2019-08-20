package com.radixdlt.tempo.reactive;

import java.util.stream.Stream;

/**
 * A TempoEpic that participates in the {@link TempoAction} flow
 */
public interface TempoEpic {
	/**
	 * Execute this epic on the given flow
	 * @param flow A {@link TempoFlowSource} providing the states and actions
	 * @return An infinite, hot stream of actions corresponding to the input flow
	 */
	TempoFlow<TempoAction> epic(TempoFlowSource flow);

	/**
	 * Get the initial actions to be executed once upon starting
	 * @return The initial actions
	 */
	default Stream<TempoAction> initialActions() {
		return Stream.empty();
	}
}
