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

package com.radixdlt.middleware2.store;

import com.radixdlt.consensus.bft.VerifiedVertexStoreState;

/**
 * Controls atomic commits of multiple atoms.
 * FIXME: This is simply a hack to get atomic commits implemented.
 * FIXME: A better commit management stategy should be done which integrates
 * FIXME: the PREPARE phase (RPNV1-718)
 */
public interface RadixEngineAtomicCommitManager {
	void startTransaction();
	void commitTransaction();
	void abortTransaction();
	void save(VerifiedVertexStoreState vertexStoreState);
}
