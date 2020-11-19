/*
 *  (C) Copyright 2020 Radix DLT Ltd
 *
 *  Radix DLT Ltd licenses this file to you under the Apache License,
 *  Version 2.0 (the "License"); you may not use this file except in
 *  compliance with the License.  You may obtain a copy of the
 *  License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing,
 *  software distributed under the License is distributed on an
 *  "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND,
 *  either express or implied.  See the License for the specific
 *  language governing permissions and limitations under the License.
 */

package com.radixdlt.consensus.safety;

import com.google.inject.Inject;
import com.radixdlt.consensus.bft.View;

import java.util.Objects;

/**
 * The state maintained to ensure the safety of the consensus system.
 */
public final class SafetyState {
	private final View lastVotedView; // the last view this node voted on (and is thus safe)
	private final View lockedView; // the highest 2-chain head

	@Inject
	public SafetyState() {
		this(View.genesis(), View.genesis());
	}

	public SafetyState(View lastVotedView, View lockedView) {
		this.lastVotedView = Objects.requireNonNull(lastVotedView);
		this.lockedView = Objects.requireNonNull(lockedView);
	}

	static class Builder {
		private final SafetyState original;
		private View lastVotedView;
		private View lockedView;
		private boolean changed = false;

		private Builder(SafetyState safetyState) {
			this.original = safetyState;
		}

		public Builder lastVotedView(View lastVotedView) {
			this.lastVotedView = lastVotedView;
			this.changed = true;
			return this;
		}

		public Builder lockedView(View lockedView) {
			this.lockedView = lockedView;
			this.changed = true;
			return this;
		}

		public SafetyState build() {
			return changed ? new SafetyState(
				lastVotedView == null ? original.lastVotedView : lastVotedView,
				lockedView == null ? original.lockedView : lockedView
			) : original;
		}
	}

	public Builder toBuilder() {
		return new Builder(this);
	}

	public static SafetyState initialState() {
		return new SafetyState();
	}

	@Override
	public boolean equals(Object o) {
		if (this == o) {
			return true;
		}
		if (o == null || getClass() != o.getClass()) {
			return false;
		}
		SafetyState that = (SafetyState) o;
		return Objects.equals(lastVotedView, that.lastVotedView)
			&& Objects.equals(lockedView, that.lockedView);
	}

	@Override
	public int hashCode() {
		return Objects.hash(lastVotedView, lockedView);
	}

	@Override
	public String toString() {
		return String.format("SafetyState{lastVotedView=%s, lockedView=%s}", lastVotedView, lockedView);
	}

	public View getLastVotedView() {
		return lastVotedView;
	}

	public View getLockedView() {
		return lockedView;
	}
}
