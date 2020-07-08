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

package com.radixdlt.consensus.simulation.configuration;

import com.radixdlt.consensus.EpochChange;
import com.radixdlt.consensus.Vertex;
import com.radixdlt.consensus.VertexMetadata;
import com.radixdlt.consensus.simulation.network.SimulationNodes.SimulatedStateComputer;
import com.radixdlt.consensus.validators.Validator;
import com.radixdlt.consensus.validators.ValidatorSet;
import com.radixdlt.crypto.ECPublicKey;
import com.radixdlt.middleware2.CommittedAtom;
import com.radixdlt.utils.UInt256;
import io.reactivex.rxjava3.core.Observable;
import java.util.List;
import java.util.stream.Collectors;

/**
 * A state computer which never changes epochs
 */
public final class SingleEpochAlwaysSyncedStateComputer implements SimulatedStateComputer {
	private final ValidatorSet validatorSet;
	private final VertexMetadata ancestor;

	public SingleEpochAlwaysSyncedStateComputer(VertexMetadata ancestor, List<ECPublicKey> nodes) {
		this.ancestor = ancestor;
		this.validatorSet = ValidatorSet.from(
			nodes.stream()
				.map(p -> Validator.from(p, UInt256.ONE))
				.collect(Collectors.toList())
		);
	}

	public SingleEpochAlwaysSyncedStateComputer(List<ECPublicKey> nodes) {
		this(VertexMetadata.ofGenesisAncestor(), nodes);
	}

	@Override
	public boolean syncTo(VertexMetadata vertexMetadata, List<ECPublicKey> target, Object opaque) {
		return true;
	}

	@Override
	public boolean compute(Vertex vertex) {
		return false;
	}

	@Override
	public void execute(CommittedAtom instruction) {
	}

	@Override
	public Observable<EpochChange> epochChanges() {
		return Observable.just(new EpochChange(ancestor, validatorSet))
			.concatWith(Observable.never());
	}
}
