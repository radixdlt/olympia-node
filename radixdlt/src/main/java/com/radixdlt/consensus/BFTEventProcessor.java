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

import com.radixdlt.consensus.bft.BFTInsertUpdate;
import com.radixdlt.consensus.bft.BFTRebuildUpdate;
import com.radixdlt.consensus.bft.ViewUpdate;
import com.radixdlt.consensus.liveness.ScheduledLocalTimeout;

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
	 * Process a local view update message.
	 *
	 * @param viewUpdate the view update message
	 */
	void processViewUpdate(ViewUpdate viewUpdate);

	/**
	 * Process a consensus vote message.
	 *
	 * @param vote the vote message
	 */
	void processVote(Vote vote);

	/**
	 * Process a consensus view timeout message.
	 *
	 * @param viewTimeout the view timeout message
	 */
	void processViewTimeout(ViewTimeout viewTimeout);

	/**
	 * Process a consensus proposal message.
	 *
	 * @param proposal the proposal message
	 */
	void processProposal(Proposal proposal);

	/**
	 * Process a local consensus timeout message.
	 *
	 * @param scheduledLocalTimeout the view corresponding to the timeout
	 */
	void processLocalTimeout(ScheduledLocalTimeout scheduledLocalTimeout);

	/**
	 * Process a BFT update.
	 *
	 * @param update the BFT update
	 */
	void processBFTUpdate(BFTInsertUpdate update);

	void processBFTRebuildUpdate(BFTRebuildUpdate update);
}
