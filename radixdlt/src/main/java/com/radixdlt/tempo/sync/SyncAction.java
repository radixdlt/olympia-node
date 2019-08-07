package com.radixdlt.tempo.sync;

import com.radixdlt.tempo.sync.actions.ScheduleAction;

import java.util.concurrent.TimeUnit;

/**
 * Marker interface for atom sync actions
 */
public interface SyncAction {
	/**
	 * Schedule this action to be dispatched after some delay
	 * @param delay The delay
	 * @param unit The unit of the delay
	 * @return A scheduled action
	 */
	default ScheduleAction schedule(long delay, TimeUnit unit) {
		return new ScheduleAction(this, delay, unit);
	}
}
