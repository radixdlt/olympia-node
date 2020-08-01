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

package com.radixdlt.consensus;

import com.radixdlt.consensus.bft.View;
import com.radixdlt.crypto.Hash;

/**
 * Processor of BFT events.
 *
 * Implementations are not expected to be thread-safe.
 */
public interface BFTEventProcessor {
	/**
	 * The initialization call. Must be called first and only once at
	 * the beginning of the BFT's lifetime.
	 */
	void start();

	/**
	 * Process a consensus vote message
	 * @param vote the vote message
	 */
	void processVote(Vote vote);

	/**
	 * Process a consensus new-view message
	 * @param newView the new-view message
	 */
	void processNewView(NewView newView);

	/**
	 * Process a consensus proposal message
	 * @param proposal the proposalmessage
	 */
	void processProposal(Proposal proposal);

	/**
	 * Process a local consensus timeout message
	 * @param view the view corresponding to the timeout
	 */
	void processLocalTimeout(View view);

	/**
	 * Process a local consensus sync message
	 * @param vertexId the id of vertex which has been synced
	 */
	void processLocalSync(Hash vertexId);
}
