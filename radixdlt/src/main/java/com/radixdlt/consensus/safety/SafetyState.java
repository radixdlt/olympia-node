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

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.inject.Inject;
import com.radixdlt.consensus.Vote;
import com.radixdlt.consensus.bft.View;
import com.radixdlt.serialization.DsonOutput;
import com.radixdlt.serialization.SerializerConstants;
import com.radixdlt.serialization.SerializerDummy;
import com.radixdlt.serialization.SerializerId2;

import javax.annotation.concurrent.Immutable;
import java.util.Objects;
import java.util.Optional;

/**
 * The state maintained to ensure the safety of the consensus system.
 */
@Immutable
@SerializerId2("consensus.safety_state")
public final class SafetyState {

	@JsonProperty(SerializerConstants.SERIALIZER_NAME)
	@DsonOutput(value = {DsonOutput.Output.API, DsonOutput.Output.WIRE, DsonOutput.Output.PERSIST})
	SerializerDummy serializer = SerializerDummy.DUMMY;

	private final View lockedView; // the highest 2-chain head

	private final Optional<Vote> lastVote;

	@Inject
	public SafetyState() {
		this(View.genesis(), Optional.empty());
	}

	@JsonCreator
	public SafetyState(
		@JsonProperty("locked_view") Long lockedView,
		@JsonProperty("last_vote") Vote lastVote
	) {
		this(View.of(lockedView), Optional.ofNullable(lastVote));
	}

	public SafetyState(
		View lockedView,
		Optional<Vote> lastVote
	) {
		this.lockedView = Objects.requireNonNull(lockedView);
		this.lastVote = Objects.requireNonNull(lastVote);
	}

	static class Builder {
		private final SafetyState original;
		private View lockedView;
		private Vote lastVote;
		private boolean changed = false;

		private Builder(SafetyState safetyState) {
			this.original = safetyState;
		}

		public Builder lockedView(View lockedView) {
			this.lockedView = lockedView;
			this.changed = true;
			return this;
		}

		public Builder lastVote(Vote vote) {
			this.lastVote = vote;
			this.changed = true;
			return this;
		}

		public SafetyState build() {
			if (changed) {
				return new SafetyState(
					lockedView == null ? original.lockedView : lockedView,
					lastVote == null ? original.lastVote : Optional.of(lastVote)
				);
			} else {
				return original;
			}
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
		return Objects.equals(lockedView, that.lockedView)
			&& Objects.equals(lastVote, that.lastVote);
	}

	@Override
	public int hashCode() {
		return Objects.hash(lockedView, lastVote);
	}

	@Override
	public String toString() {
		return String.format("SafetyState{lockedView=%s, lastVote=%s}",
				lockedView, lastVote);
	}

	public View getLastVotedView() {
		return getLastVote().map(Vote::getView).orElse(View.genesis());
	}

	public View getLockedView() {
		return lockedView;
	}

	public Optional<Vote> getLastVote() {
		return lastVote;
	}

	@JsonProperty("locked_view")
	@DsonOutput(DsonOutput.Output.ALL)
	private Long getSerializerLockedView() {
		return this.lockedView == null ? null : this.lockedView.number();
	}

	@JsonProperty("last_vote")
	@DsonOutput(DsonOutput.Output.ALL)
	public Vote getSerializerLastVote() {
		return lastVote.orElse(null);
	}
}
