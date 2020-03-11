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
import com.radixdlt.consensus.View;

import java.util.Objects;

/**
 * The state maintained to ensure the safety of the consensus system.
 */
final class SafetyState {
	final View lastVotedView; // the last view this node voted on
	final View lockedView; // the highest 2-chain head

	@Inject
	protected SafetyState() {
		this(View.of(0L), View.of(0L));
	}

	public SafetyState(View lastVotedView, View lockedView) {
		this.lastVotedView = lastVotedView;
		this.lockedView = lockedView;
	}

	public SafetyState(SafetyState other) {
		this(other.lastVotedView, other.lockedView);
	}

	public SafetyState withLastVotedView(View lastVotedView) {
		return new SafetyState(lastVotedView, this.lockedView);
	}

	public SafetyState withLockedView(View lockedView) {
		return new SafetyState(this.lastVotedView, lockedView);
	}

	public static SafetyState initialState() {
		return new SafetyState(View.of(0L), View.of(0L));
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
		return Objects.equals(lastVotedView, that.lastVotedView) &&
			Objects.equals(lockedView, that.lockedView);
	}

	@Override
	public int hashCode() {
		return Objects.hash(lastVotedView, lockedView);
	}

	@Override
	public String toString() {
		return "SafetyState{" +
			"lastVotedView=" + lastVotedView +
			", lockedView=" + lockedView +
			'}';
	}
}
