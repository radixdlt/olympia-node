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

package com.radixdlt.consensus.simulation.invariants.bft;

import com.radixdlt.consensus.VertexMetadata;
import com.radixdlt.consensus.simulation.TestInvariant;
import com.radixdlt.consensus.QuorumCertificate;
import com.radixdlt.consensus.simulation.network.SimulatedNetwork.RunningNetwork;
import io.reactivex.rxjava3.core.Observable;
import io.reactivex.rxjava3.core.Single;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

/**
 * Check that the network is making progress by ensuring that new QCs and epochs
 * are progressively increasing.
 */
public class LivenessInvariant implements TestInvariant {
	private final long duration;
	private final TimeUnit timeUnit;

	public LivenessInvariant(long duration, TimeUnit timeUnit) {
		this.duration = duration;
		this.timeUnit = timeUnit;
	}

	private Comparator<VertexMetadata> vertexMetadataComparator =
		Comparator.comparingLong(VertexMetadata::getEpoch).thenComparing(VertexMetadata::getView);

	@Override
	public Observable<TestInvariantError> check(RunningNetwork network) {
		AtomicReference<VertexMetadata> highestVertexMetadata = new AtomicReference<>(VertexMetadata.ofGenesisAncestor());
		return Observable
			.interval(duration * 2, duration, timeUnit) // 2 times initial duration to account for boot up
			.flatMapSingle(i -> {
				List<Single<VertexMetadata>> qcs = network.getNodes().stream()
						.map(network::getVertexStoreEvents)
						.map(eventsRx -> eventsRx.highQCs().firstOrError().map(QuorumCertificate::getProposed))
						.collect(Collectors.toList());
				return Single.merge(qcs)
					.reduce(VertexMetadata.ofGenesisAncestor(), (o1, o2) -> {
						if (vertexMetadataComparator.compare(o1, o2) > 0) {
							return o1;
						} else {
							return o2;
						}
					});
			})
			.concatMap(vertexMetadata -> {
				if (vertexMetadataComparator.compare(vertexMetadata, highestVertexMetadata.get()) <= 0) {
					return Observable.just(
						new TestInvariantError(
							String.format("Highest QC hasn't increased from %s after %s %s", highestVertexMetadata.get(), duration, timeUnit)
						)
					);
				} else {
					highestVertexMetadata.set(vertexMetadata);
					return Observable.empty();
				}
			});
	}
}
