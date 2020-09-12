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

package com.radixdlt.sync;

import com.radixdlt.consensus.VerifiedLedgerHeaderAndProof;
import com.radixdlt.consensus.bft.BFTNode;
import com.radixdlt.ledger.VerifiedCommandsAndProof;
import io.reactivex.rxjava3.core.Observable;

/**
 * Network interface for state syncing
 */
public interface StateSyncNetwork {

	/**
	 * Retrieve stream of sync responses
	 * @return an unending Observable of sync responses
	 */
	Observable<VerifiedCommandsAndProof> syncResponses();

	/**
	 * Retrieve stream of sync requests
 	 * @return an unending Observable of sync requests
	 */
	Observable<RemoteSyncRequest> syncRequests();

	/**
	 * Sends a sync request to a peer node
	 *
	 * @param node node to send request to
	 * @param currentHeader this nodes current verified header
	 */
	void sendSyncRequest(BFTNode node, VerifiedLedgerHeaderAndProof currentHeader);

	/**
	 * Sends a sync response to a peer node
	 * @param node node to send response to
	 * @param commandsAndProof list of commands with proof
	 */
	void sendSyncResponse(BFTNode node, VerifiedCommandsAndProof commandsAndProof);
}
