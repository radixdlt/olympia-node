package com.radixdlt.tempo.reactive;

import com.google.common.collect.ImmutableSet;

import java.util.Set;
import java.util.stream.Stream;

/**
 * A TempoEpic that participates in the {@link TempoAction} flow
 */
public interface TempoEpic {
	/**
	 * Gets the set of required state of this epic
	 */
	default Set<Class<? extends TempoState>> requiredState() {
		return ImmutableSet.of();
	}

	/**
	 * Execute this epic on the given flow
	 * @param flow A {@link TempoFlowSource} providing the states and actions
	 * @return An infinite, cold stream of actions corresponding to the input flow
	 */
	Stream<TempoFlow<TempoAction>> epic(TempoFlowSource flow);

	/**
	 * Get the initial actions to be executed once upon starting
	 * @return The initial actions
	 */
	default Stream<TempoAction> initialActions() {
		return Stream.empty();
	}
}
