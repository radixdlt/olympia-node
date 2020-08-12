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

package com.radixdlt.integration.distributed.simulation.configuration;

import com.google.common.collect.ImmutableList;
import com.radixdlt.consensus.epoch.EpochChange;
import com.radixdlt.consensus.Vertex;
import com.radixdlt.consensus.VertexMetadata;
import com.radixdlt.consensus.bft.BFTNode;
import com.radixdlt.integration.distributed.simulation.network.SimulationNodes.SimulatedSyncedExecutor;
import com.radixdlt.consensus.bft.BFTValidator;
import com.radixdlt.consensus.bft.BFTValidatorSet;
import com.radixdlt.middleware2.CommittedAtom;
import com.radixdlt.utils.UInt256;
import io.reactivex.rxjava3.core.Observable;
import java.util.List;
import java.util.stream.Collectors;

/**
 * A state computer which never changes epochs
 */
public final class SingleEpochAlwaysSyncedExecutor implements SimulatedSyncedExecutor {
	private final BFTValidatorSet validatorSet;
	private final VertexMetadata ancestor;

	public SingleEpochAlwaysSyncedExecutor(VertexMetadata ancestor, List<BFTNode> nodes) {
		this.ancestor = ancestor;
		this.validatorSet = BFTValidatorSet.from(
			nodes.stream()
				.map(node -> BFTValidator.from(node, UInt256.ONE))
				.collect(Collectors.toList())
		);
	}

	public SingleEpochAlwaysSyncedExecutor(List<BFTNode> nodes) {
		this(VertexMetadata.ofGenesisAncestor(), nodes);
	}

	@Override
	public boolean syncTo(VertexMetadata vertexMetadata, ImmutableList<BFTNode> target, Object opaque) {
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
		return Observable.never();
	}

	@Override
	public BFTValidatorSet initialValidatorSet() {
		return validatorSet;
	}
}
