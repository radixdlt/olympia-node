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

import com.radixdlt.crypto.Hash;
import io.reactivex.rxjava3.core.Observable;

/**
 * Provider of Rx stream of events coming from Vertex Store
 */
public interface VertexStoreEventsRx {

	/**
	 * Retrieve rx flow of vertices which have been synced
	 * @return flow of vertex hashes
	 */
	Observable<Hash> syncedVertices();

	/**
	 * Retrieve rx flow of vertices which have been committed
	 * @return flow of vertices
	 */
	Observable<Vertex> committedVertices();

	/**
	 * Retrieve rx flow of highest QCS
	 * @return flow of qcs
	 */
	Observable<QuorumCertificate> highQCs();
}
