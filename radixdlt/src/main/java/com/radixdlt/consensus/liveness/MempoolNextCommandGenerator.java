/*
 *  (C) Copyright 2020 Radix DLT Ltd
 *
 *  Radix DLT Ltd licenses this file to you under the Apache License,
 *  Version 2.0 (the "License"); you may not use this file except in
 *  compliance with the License.  You may obtain a copy of the
 *  License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing,
 *  software distributed under the License is distributed on an
 *  "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND,
 *  either express or implied.  See the License for the specific
 *  language governing permissions and limitations under the License.
 */

package com.radixdlt.consensus.liveness;

import com.radixdlt.identifiers.AID;
import com.radixdlt.consensus.bft.View;
import com.radixdlt.mempool.Mempool;
import com.radixdlt.middleware2.ClientAtom;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Logic for generating new proposals
 */
public final class MempoolNextCommandGenerator implements NextCommandGenerator {
	private final Mempool mempool;

	public MempoolNextCommandGenerator(Mempool mempool) {
		this.mempool = Objects.requireNonNull(mempool);
	}

	// TODO: check that next proposal works with current vertexStore state
	@Override
	public ClientAtom generateNextCommand(View view,  Set<AID> prepared) {
		final List<ClientAtom> atoms = mempool.getAtoms(1, prepared);

		return !atoms.isEmpty() ? atoms.get(0) : null;
	}
}
