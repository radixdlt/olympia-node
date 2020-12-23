/*
 * (C) Copyright 2020 Radix DLT Ltd
 *
 * Radix DLT Ltd licenses this file to you under the Apache License,
 * Version 2.0 (the "License"); you may not use this file except in
 * compliance with the License.  You may obtain a copy of the
 * License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND,
 * either express or implied.  See the License for the specific
 * language governing permissions and limitations under the License.
 */

package com.radixdlt.consensus.liveness;

import com.radixdlt.consensus.bft.View;
import com.radixdlt.consensus.bft.ViewUpdate;
import java.util.Objects;

/**
 * A potential timeout that is scheduled
 */
public final class ScheduledLocalTimeout {
	private final ViewUpdate viewUpdate;
	private final long millisecondsWaitTime;
	private final int count;

	private ScheduledLocalTimeout(
		ViewUpdate viewUpdate,
		long millisecondsWaitTime,
		int count
	) {
		this.viewUpdate = viewUpdate;
		this.millisecondsWaitTime = millisecondsWaitTime;
		this.count = count;
	}

	public static ScheduledLocalTimeout create(ViewUpdate viewUpdate, long millisecondsWaitTime) {
		return new ScheduledLocalTimeout(viewUpdate, millisecondsWaitTime, 0);
	}

	public ScheduledLocalTimeout nextRetry(long millisecondsWaitTime) {
		return new ScheduledLocalTimeout(
			viewUpdate,
			millisecondsWaitTime,
			this.count + 1
		);
	}

	public int count() {
		return count;
	}

	public ViewUpdate viewUpdate() {
		return viewUpdate;
	}

	public View view() {
		return viewUpdate.getCurrentView();
	}

	public long millisecondsWaitTime() {
		return millisecondsWaitTime;
	}

	@Override
	public int hashCode() {
		return Objects.hash(viewUpdate, millisecondsWaitTime, count);
	}

	@Override
	public boolean equals(Object o) {
		if (!(o instanceof ScheduledLocalTimeout)) {
			return false;
		}

		ScheduledLocalTimeout other = (ScheduledLocalTimeout) o;
		return Objects.equals(other.viewUpdate, this.viewUpdate)
			&& other.millisecondsWaitTime == this.millisecondsWaitTime
			&& other.count == this.count;
	}

	@Override
	public String toString() {
		return String.format("%s{view=%s count=%s}", this.getClass().getSimpleName(), viewUpdate, count);
	}
}
