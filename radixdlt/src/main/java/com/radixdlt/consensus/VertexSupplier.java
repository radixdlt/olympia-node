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
import com.radixdlt.crypto.Hash;
import io.reactivex.rxjava3.core.Single;

/**
 * An asynchronous supplier which retrieves data for a vertex with a given id
 */
public interface VertexSupplier {

	/**
	 * Execute an RPC to retrieve a vertex given an Id from a node
	 * TODO: refactor to maintain a unidirectional data flow
	 *
	 * @param id the id of the vertex to retrieve
	 * @param node the node to retrieve the vertex info from
	 * @return single of a vertex which will complete once retrieved
	 */
	Single<Vertex> getVertex(Hash id, ECPublicKey node);
}
