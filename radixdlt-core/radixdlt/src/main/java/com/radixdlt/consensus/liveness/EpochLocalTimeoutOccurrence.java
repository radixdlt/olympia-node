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
import com.radixdlt.consensus.epoch.EpochView;

/**
 * A timeout which has occurred in the bft node
 */
public final class EpochLocalTimeoutOccurrence {
	private final long epoch;
	private final LocalTimeoutOccurrence localTimeoutOccurrence;

	public EpochLocalTimeoutOccurrence(long epoch, LocalTimeoutOccurrence localTimeoutOccurrence) {
		this.epoch = epoch;
		this.localTimeoutOccurrence = localTimeoutOccurrence;
	}

	public EpochView getEpochView() {
		return new EpochView(epoch, localTimeoutOccurrence.getView());
	}

	public LocalTimeoutOccurrence getBase() {
		return localTimeoutOccurrence;
	}

	public BFTNode getLeader() {
		return localTimeoutOccurrence.getLeader();
	}

	public BFTNode getNextLeader() {
		return localTimeoutOccurrence.getNextLeader();
	}

	@Override
	public String toString() {
		return String.format("%s{epoch=%s event=%s}", this.getClass().getSimpleName(), epoch, localTimeoutOccurrence);
	}
}
