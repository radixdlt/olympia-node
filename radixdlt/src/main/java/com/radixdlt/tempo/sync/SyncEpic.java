package com.radixdlt.tempo.sync;

import java.util.stream.Stream;

/**
 * A SyncEpic that participates in the {@link SyncAction} flow
 */
public interface SyncEpic {
	/**
	 * Execute this epic with the given action
	 * @param action The action
	 * @return the next actions to be executed given the action
	 */
	Stream<SyncAction> epic(SyncAction action);

	/**
	 * Get the initial actions to be executed once upon starting
	 * @return The initial actions
	 */
	default Stream<SyncAction> initialActions() {
		return Stream.empty();
	}
}
