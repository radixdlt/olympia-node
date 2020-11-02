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

import com.radixdlt.consensus.bft.BFTNode;
import com.radixdlt.ledger.DtoCommandsAndProof;
import com.radixdlt.ledger.DtoLedgerHeaderAndProof;

/**
 * Network interface for state syncing
 */
public interface StateSyncNetworkSender {
	/**
	 * Sends a sync request to a peer node
	 *
	 * @param node node to send request to
	 * @param currentHeader this nodes current verified header
	 */
	void sendSyncRequest(BFTNode node, DtoLedgerHeaderAndProof currentHeader);

	/**
	 * Sends a sync response to a peer node
	 * @param node node to send response to
	 * @param commandsAndProof list of commands with proof
	 */
	void sendSyncResponse(BFTNode node, DtoCommandsAndProof commandsAndProof);
}
