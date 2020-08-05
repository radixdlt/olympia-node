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

package com.radixdlt.consensus.sync;

import com.google.common.collect.ImmutableList;
import com.radixdlt.middleware2.CommittedAtom;
import com.radixdlt.network.addressbook.Peer;
import io.reactivex.rxjava3.core.Observable;
import java.util.List;

/**
 * Network interface for state syncing
 */
public interface StateSyncNetwork {

	/**
	 * Retrieve stream of sync responses
	 * @return an unending Observable of sync responses
	 */
	Observable<ImmutableList<CommittedAtom>> syncResponses();

	/**
	 * Retrieve stream of sync requests
 	 * @return an unending Observable of sync requests
	 */
	Observable<SyncRequest> syncRequests();

	/**
	 * Sends a sync request to a peer
	 *
	 * @param peer peer to send request to
	 * @param stateVersion this nodes current stateVersion
	 */
	void sendSyncRequest(Peer peer, long stateVersion);

	/**
	 * Sends a sync response to a peer
	 * @param peer peer to send response to
	 * @param atoms list of atoms in the response
	 */
	void sendSyncResponse(Peer peer, List<CommittedAtom> atoms);
}
