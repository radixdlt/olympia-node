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

import com.radixdlt.crypto.ECPublicKey;
import com.radixdlt.crypto.Hash;
import io.reactivex.rxjava3.core.Single;

/**
 * Interface for Event Coordinator to send things through a network
 */
public interface EventCoordinatorNetworkSender {

	/**
	 * Broadcast a proposal message to all validators in the network
	 * @param proposal the proposal to broadcast
	 */
	void broadcastProposal(Proposal proposal);

	/**
	 * Send a new-view message to a given validator
	 * @param newView the new-view message
	 * @param newViewLeader the validator the message gets sent to
	 */
	void sendNewView(NewView newView, ECPublicKey newViewLeader);

	/**
	 * Send a vote message to a given validator
	 * @param vote the vote message
	 * @param leader the validator the message gets sent to
	 */
	void sendVote(Vote vote, ECPublicKey leader);

	/**
	 * Execute an RPC to retrieve a vertex given an Id from a node
	 * TODO: refactor to maintain a unidirectional data flow
	 *
	 * @param node the node to retrieve the vertex info from
	 * @return single of a vertex which will complete once retrieved
	 */
	Single<Vertex> getVertex(Hash vertexId, ECPublicKey node);
}
