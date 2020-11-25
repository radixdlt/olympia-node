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

import com.radixdlt.consensus.bft.BFTNode;
import com.radixdlt.consensus.bft.View;
import java.util.Objects;

/**
 * An actual occurrence (as opposed to a potential) of a local timeout
 */
public final class LocalTimeoutOccurrence {
	private final ScheduledLocalTimeout scheduledLocalTimeout;

	public LocalTimeoutOccurrence(ScheduledLocalTimeout scheduledLocalTimeout) {
		this.scheduledLocalTimeout = Objects.requireNonNull(scheduledLocalTimeout);
	}

	public ScheduledLocalTimeout timeout() {
		return scheduledLocalTimeout;
	}

	public View getView() {
		return scheduledLocalTimeout.view();
	}

	public BFTNode getLeader() {
		return scheduledLocalTimeout.viewUpdate().getLeader();
	}

	@Override
	public String toString() {
		return String.format("%s{%s timeout=%s}", this.getClass().getSimpleName(), scheduledLocalTimeout);
	}

	@Override
	public int hashCode() {
		return Objects.hash(scheduledLocalTimeout);
	}

	@Override
	public boolean equals(Object o) {
		if (!(o instanceof LocalTimeoutOccurrence)) {
			return false;
		}

		LocalTimeoutOccurrence other = (LocalTimeoutOccurrence) o;
		return Objects.equals(scheduledLocalTimeout, other.scheduledLocalTimeout);
	}
}
