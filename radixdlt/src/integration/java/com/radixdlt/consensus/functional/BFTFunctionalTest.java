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

package com.radixdlt.consensus.functional;

import com.google.common.collect.ImmutableList;
import com.radixdlt.consensus.Vertex;
import com.radixdlt.consensus.VertexSupplier;
import com.radixdlt.consensus.liveness.ProposerElection;
import com.radixdlt.consensus.liveness.RotatingLeaders;
import com.radixdlt.consensus.validators.Validator;
import com.radixdlt.consensus.validators.ValidatorSet;
import com.radixdlt.counters.SystemCounters;
import com.radixdlt.crypto.ECKeyPair;
import com.radixdlt.crypto.ECPublicKey;
import com.radixdlt.utils.UInt256;
import io.reactivex.rxjava3.core.Single;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * A functional BFT test where each event that occurs in the BFT network
 * is emitted and processed synchronously by the caller.
 */
public class BFTFunctionalTest {
	private final ImmutableList<ControlledBFTNode> nodes;
	private final ImmutableList<ECPublicKey> pks;

	public BFTFunctionalTest(int numNodes) {
		ImmutableList<ECKeyPair> keys = Stream.generate(ECKeyPair::generateNew)
			.limit(numNodes)
			.collect(ImmutableList.toImmutableList());
		this.pks = keys.stream()
			.map(ECKeyPair::getPublicKey)
			.collect(ImmutableList.toImmutableList());
		ControlledBFTNetwork network = new ControlledBFTNetwork(pks);
		ProposerElection proposerElection = new RotatingLeaders(pks);
		ValidatorSet validatorSet = ValidatorSet.from(
			pks.stream().map(pk -> Validator.from(pk, UInt256.ONE)).collect(Collectors.toList())
		);

		AtomicReference<ImmutableList<ControlledBFTNode>> nodesForVertexSupplier = new AtomicReference<>();

		VertexSupplier shortCircuitVertexSupplier = (vertexId, node) -> Single.create(emitter -> {
			ImmutableList<ControlledBFTNode> nodes = nodesForVertexSupplier.get();
			if (nodes != null) {
				Vertex vertex = nodes.get(pks.indexOf(node)).getVertexStore().getVertex(vertexId);
				emitter.onSuccess(vertex);
			}
		});

		this.nodes = keys.stream()
			.map(key -> new ControlledBFTNode(
				key,
				network.getSender(key.getPublicKey()),
				network.getReceiver(key.getPublicKey()),
				proposerElection,
				validatorSet,
				shortCircuitVertexSupplier
			))
			.collect(ImmutableList.toImmutableList());

		nodesForVertexSupplier.set(this.nodes);

		nodes.forEach(ControlledBFTNode::start);
	}

	public void processNextMsg(int toIndex, int fromIndex, Class<?> expectedClass) {
		nodes.get(toIndex).processNext(this.pks.get(fromIndex), expectedClass);
	}

	public SystemCounters getSystemCounters(int nodeIndex) {
		return nodes.get(nodeIndex).getSystemCounters();
	}
}
