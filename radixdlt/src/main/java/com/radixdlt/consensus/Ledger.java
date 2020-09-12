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

package com.radixdlt.consensus;

import com.radixdlt.consensus.bft.VerifiedVertex;
import com.radixdlt.ledger.VerifiedCommandsAndProof;
import com.radixdlt.ledger.DtoCommandsAndProof;

/**
 * A distributed computer which manages the computed state in a BFT.
 */
public interface Ledger {
	/**
	 * Given a proposed vertex, executes prepare stage on
	 * the state computer, the result of which gets persisted on ledger.
	 *
	 * @param vertex the vertex to compute
	 * @return the results of executing the prepare stage
	 */
	LedgerHeader prepare(VerifiedVertex vertex);

	/**
	 * Check if the ledger is commit synced at a particular state
	 * @param header the metadata to sync to
	 * @return Synced handler
	 */
	OnSynced ifCommitSynced(VerifiedLedgerHeaderAndProof header);

	interface OnSynced {
		OnNotSynced then(Runnable onSynced);
	}

	interface OnNotSynced {
		void elseExecuteAndSendMessageOnSync(Runnable onNotSynced, Object opaque);
	}

	void tryCommit(DtoCommandsAndProof commandsAndProof);

	/**
	 * Commit a command
	 * @param verifiedCommandsAndProof the command to commit
	 */
	void commit(VerifiedCommandsAndProof verifiedCommandsAndProof);
}
