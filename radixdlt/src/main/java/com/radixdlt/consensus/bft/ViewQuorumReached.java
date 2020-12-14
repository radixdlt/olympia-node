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
 * A local event message indicating that a view quorum (either QC or TC) has been reached.
 */
public final class ViewQuorumReached {

	private final ViewVotingResult votingResult;

	// the author of the last received message that lead to forming a quorum
	private final BFTNode lastAuthor;

	public ViewQuorumReached(ViewVotingResult votingResult, BFTNode lastAuthor) {
		this.votingResult = Objects.requireNonNull(votingResult);
		this.lastAuthor = Objects.requireNonNull(lastAuthor);
	}

	public BFTNode lastAuthor() {
		return lastAuthor;
	}

	public ViewVotingResult votingResult() {
		return votingResult;
	}

	@Override
	public boolean equals(Object o) {
		if (this == o) {
			return true;
		}
		if (o == null || getClass() != o.getClass()) {
			return false;
		}
		ViewQuorumReached that = (ViewQuorumReached) o;
		return Objects.equals(votingResult, that.votingResult)
				&& Objects.equals(lastAuthor, that.lastAuthor);
	}

	@Override
	public int hashCode() {
		return Objects.hash(votingResult, lastAuthor);
	}
}
