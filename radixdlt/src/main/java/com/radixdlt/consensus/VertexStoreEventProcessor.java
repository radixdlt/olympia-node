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

import com.radixdlt.consensus.bft.GetVerticesErrorResponse;
import com.radixdlt.consensus.bft.GetVerticesResponse;
import com.radixdlt.consensus.bft.VertexStore.GetVerticesRequest;

/**
 * Processor of vertex store events
 */
public interface VertexStoreEventProcessor {

	/**
	 * Process a get vertices request
	 * @param request the get vertices request
	 */
	void processGetVerticesRequest(GetVerticesRequest request);

	/**
	 * Process a get vertices error response
	 * @param response the get vertices error response
	 */
	void processGetVerticesErrorResponse(GetVerticesErrorResponse response);

	/**
	 * Process a get vertices response
	 * @param response the get vertices response
	 */
	void processGetVerticesResponse(GetVerticesResponse response);

	/**
	 * Process a committed state ync
	 * @param committedStateSync the committed state sync
	 */
	void processCommittedStateSync(CommittedStateSync committedStateSync);
}
