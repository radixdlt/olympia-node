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

package com.radixdlt.consensus.bft;

import com.radixdlt.consensus.HighQC;
import java.util.Objects;

/**
 * Represents an internal (local) view update.
 */
public final class ViewUpdate {

	private final View currentView;
	private final HighQC highQC;

	private final BFTNode leader;
	private final BFTNode nextLeader;

	private ViewUpdate(View currentView, HighQC highQC, BFTNode leader, BFTNode nextLeader) {
		this.currentView = currentView;
		this.highQC = highQC;
		this.leader = leader;
		this.nextLeader = nextLeader;
	}

	public static ViewUpdate create(View currentView, HighQC highQC, BFTNode leader, BFTNode nextLeader) {
		Objects.requireNonNull(currentView);
		Objects.requireNonNull(leader);
		Objects.requireNonNull(nextLeader);

		return new ViewUpdate(currentView, highQC, leader, nextLeader);
	}

	public static ViewUpdate genesis() {
		return new ViewUpdate(
			View.genesis(), null, null, null);
	}

	public HighQC getHighQC() {
		return highQC;
	}

	public BFTNode getLeader() {
		return leader;
	}

	public BFTNode getNextLeader() {
		return nextLeader;
	}

	public View getCurrentView() {
		return currentView;
	}

	public long uncommittedViewsCount() {
		return Math.max(0L, currentView.number() - highQC.highestCommittedQC().getView().number() - 1);
	}

	@Override
	public String toString() {
		return String.format("%s[%s %s leader=%s next=%s]",
			getClass().getSimpleName(),
			this.currentView,
			this.highQC,
			this.leader,
			this.nextLeader);
	}

	@Override
	public boolean equals(Object o) {
		if (this == o) {
			return true;
		}
		if (o == null || getClass() != o.getClass()) {
			return false;
		}
		final ViewUpdate that = (ViewUpdate) o;
		return Objects.equals(currentView, that.currentView)
			&& Objects.equals(highQC, that.highQC)
			&& Objects.equals(leader, that.leader)
			&& Objects.equals(nextLeader, that.nextLeader);
	}

	@Override
	public int hashCode() {
		return Objects.hash(currentView, highQC, leader, nextLeader);
	}
}
