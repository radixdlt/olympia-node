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

package com.radixdlt.consensus.epoch;

import com.radixdlt.consensus.QuorumCertificate;
import com.radixdlt.consensus.bft.VerifiedVertex;
import com.radixdlt.consensus.bft.VertexStore;

/**
 * A Vertex Store factory
 */
public interface VertexStoreFactory {

	/**
	 * Creates a new VertexStore given initial vertex and QC
	 * @param genesisVertex the root vertex
	 * @param genesisQC the root QC
	 * @return a new VertexStore
	 */
	VertexStore create(
		VerifiedVertex genesisVertex,
		QuorumCertificate genesisQC
	);
}
