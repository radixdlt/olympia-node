package com.radixdlt.client.core.network;

import io.reactivex.Observable;

/**
 * Inspired by epics in redux-observables. This follows a similar pattern which can be understood as a creator of a
 * stream of actions from a stream of actions.
 *
 */
public interface RadixNetworkEpic {

	/**
	 * Creates a stream of actions from a stream of actions and current/future states.
	 *
	 * Note that this should NEVER let an action "slip through" as this will cause an infinite loop.
	 *
	 * @param actions stream of actions coming in
	 * @param networkState stream of states coming in
	 * @return stream of new actions
	 */
	Observable<RadixNodeAction> epic(Observable<RadixNodeAction> actions, Observable<RadixNetworkState> networkState);
}
