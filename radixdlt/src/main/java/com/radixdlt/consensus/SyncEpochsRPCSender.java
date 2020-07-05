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

import com.radixdlt.crypto.ECPublicKey;

/**
 * A sender of GetEpoch RPC requests/responses
 */
public interface SyncEpochsRPCSender {

	/**
	 * Send a request to a peer for proof of an epoch
	 * @param node the peer to send to
	 * @param epoch the epoch to retrieve proof for
	 */
	void sendGetEpochRequest(ECPublicKey node, long epoch);

	/**
	 * Send an epoch proof resposne to a peer
	 *
	 * TODO: currently just actually sending an ancestor but should contain
	 * TODO: proof as well
	 *
	 * @param node the peer to send to
	 * @param ancestor the ancestor of the epoch
	 */
	void sendGetEpochResponse(ECPublicKey node, VertexMetadata ancestor);
}
