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

import java.util.Objects;

/**
 * Represents an internal (local) view update.
 */
public final class ViewUpdate {

	private final View currentView;
	// Last view that we had any kind of quorum for
	private final View lastQuorumView;
	// Highest view in which a commit happened
	private final View highestCommitView;

	private final BFTNode leader;
	private final BFTNode nextLeader;

	private ViewUpdate(View currentView, View lastQuorumView, View highestCommitView, BFTNode leader, BFTNode nextLeader) {
		this.currentView = currentView;
		this.lastQuorumView = lastQuorumView;
		this.highestCommitView = highestCommitView;
		this.leader = leader;
		this.nextLeader = nextLeader;
	}

	public static ViewUpdate create(View currentView, View lastQuorumView, View highestCommitView, BFTNode leader, BFTNode nextLeader) {
		Objects.requireNonNull(currentView);
		Objects.requireNonNull(lastQuorumView);
		Objects.requireNonNull(highestCommitView);
		Objects.requireNonNull(leader);
		Objects.requireNonNull(nextLeader);

		return new ViewUpdate(currentView, lastQuorumView, highestCommitView, leader, nextLeader);
	}

	public static ViewUpdate genesis() {
		return new ViewUpdate(View.genesis(), View.genesis(), View.genesis(), null, null);
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

	public View getLastQuorumView() {
		return lastQuorumView;
	}

	public View getHighestCommitView() {
		return highestCommitView;
	}

	public long uncommittedViewsCount() {
		return Math.max(0L, currentView.number() - highestCommitView.number() - 1);
	}

	@Override
	public String toString() {
		return String.format("%s[%s,%s,%s leader=%s nextLeader=%s]",
			getClass().getSimpleName(),
			this.currentView,
			this.lastQuorumView,
			this.highestCommitView,
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
			&& Objects.equals(lastQuorumView, that.lastQuorumView)
			&& Objects.equals(highestCommitView, that.highestCommitView)
			&& Objects.equals(leader, that.leader)
			&& Objects.equals(nextLeader, that.nextLeader);
	}

	@Override
	public int hashCode() {
		return Objects.hash(currentView, lastQuorumView, highestCommitView, leader, nextLeader);
	}
}
