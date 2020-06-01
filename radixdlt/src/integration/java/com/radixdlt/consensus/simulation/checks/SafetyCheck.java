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

package com.radixdlt.consensus.simulation.checks;

import com.radixdlt.consensus.simulation.BFTCheck;
import com.radixdlt.consensus.simulation.SimulatedBFTNetwork;
import com.radixdlt.consensus.VertexStore;
import io.reactivex.rxjava3.core.Observable;
import java.util.Arrays;
import java.util.stream.Collectors;

/**
 * Checks that nodes are committed on the same vertex.
 * TODO: make into real safety check
 */
public class SafetyCheck implements BFTCheck {

	@Override
	public Observable<BFTCheckError> check(SimulatedBFTNetwork network) {
		return Observable.zip(
			network.getNodes().stream()
				.map(network::getVertexStore)
				.map(VertexStore::lastCommittedVertex)
				.collect(Collectors.toList()),
				Arrays::stream
		)
			.map(committedVertices -> committedVertices.distinct().collect(Collectors.toList()))
			.concatMap(committedVertices -> {
				if (committedVertices.size() != 1) {
					return Observable.just(new BFTCheckError("Committed vertices don't match"));
				} else {
					return Observable.empty();
				}
			});
	}
}
