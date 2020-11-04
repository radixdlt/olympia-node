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

import java.util.Set;

import com.radixdlt.consensus.ViewTimeout;
import com.radixdlt.consensus.Vote;
import com.radixdlt.consensus.bft.BFTNode;

/**
 * Hotstuff's Event-Driven OnNextSyncView as well as vote sending
 */
public interface VoteSender {
	/**
	 * Send a vote message to the specified validator
	 * @param vote the vote message
	 * @param nextLeader the validator the message gets sent to
	 */
	void sendVote(Vote vote, BFTNode nextLeader);

	/**
	 * Send a view timeout message to the specified validator.
	 *
	 * @param viewTimeout the view timeout message
	 * @param nodes the validators to broadcast the message to
	 */
	// FIXME: To be change when TCs implemented
	void broadcastViewTimeout(ViewTimeout viewTimeout, Set<BFTNode> nodes);
}
