/*
 * (C) Copyright 2020 Radix DLT Ltd
 *
 * Radix DLT Ltd licenses this file to you under the Apache License,
 * Version 2.0 (the "License"); you may not use this file except in
 * compliance with the License.  You may obtain a copy of the
 * License at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND,
 * either express or implied.  See the License for the specific
 * language governing permissions and limitations under the License.
 */

package com.radixdlt.consensus.liveness;

import java.util.Optional;

import com.radixdlt.consensus.QuorumCertificate;
import com.radixdlt.consensus.ViewTimeout;
import com.radixdlt.consensus.Vote;
import com.radixdlt.consensus.HighQC;
import com.radixdlt.consensus.bft.View;

/**
 * Manages the pacemaker state machine.
 */
public interface Pacemaker extends PacemakerState {
	/**
	 * Signifies to the pacemaker that a vote has been received.
	 *
	 * @param vote the vote received
	 * @return an {@link Optional} {@link QuorumCertificate} if a
	 * 		quorum was formed with this vote
	 */
	Optional<QuorumCertificate> processVote(Vote vote);

	// FIXME: To be removed when TCs implemented
	void processViewTimeout(ViewTimeout viewTimeout);

	/**
	 * Processes a local timeout, causing the pacemaker to broadcast a
	 * {@link com.radixdlt.consensus.ViewTimeout) to all leaders.
	 * Once a leader forms a quorum of view timeouts, it will proceed
	 * to the next view.
	 *
	 * @param view the view the local timeout is for
	 */
	// FIXME: Note functionality and Javadoc to change once TCs implemented
	void processLocalTimeout(View view);

	/**
	 * Signifies to the pacemaker that a quorum has agreed that a view has
	 * been completed.
	 *
	 * @param highQC the sync info for the view
	 * @return {@code true} if proceeded to a new view
	 */
	boolean processQC(HighQC highQC);
}
