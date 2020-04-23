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

import static org.assertj.core.api.AssertionsForInterfaceTypes.assertThat;

import com.radixdlt.consensus.simulation.BFTCheck;
import com.radixdlt.consensus.simulation.BFTNetworkSimulation;
import com.radixdlt.consensus.QuorumCertificate;
import com.radixdlt.consensus.VertexStore;
import com.radixdlt.consensus.View;
import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.core.Observable;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import org.assertj.core.api.Condition;

/**
 * Check that the network is making progress by ensuring that new QCs are
 * progressively increasing in view number.
 */
public class LivenessCheck implements BFTCheck {
	private final long duration;
	private final TimeUnit timeUnit;

	public LivenessCheck(long duration, TimeUnit timeUnit) {
		this.duration = duration;
		this.timeUnit = timeUnit;
	}

	@Override
	public Completable check(BFTNetworkSimulation network) {
		AtomicReference<View> highestQCView = new AtomicReference<>(View.genesis());
		return Observable
			.interval(duration, duration, timeUnit)
			.map(i -> network.getNodes().stream()
				.map(network::getVertexStore)
				.map(VertexStore::getHighestQC)
				.map(QuorumCertificate::getView)
				.max(View::compareTo)
				.get()) // there must be some max highest QC unless allNodes is empty
			.doOnNext(view -> assertThat(view)
				.satisfies(new Condition<>(v -> v.compareTo(highestQCView.get()) > 0,
					"The highest highestQC %s increased since last highestQC %s after %d %s", view, highestQCView.get(), duration, timeUnit)))
			.doOnNext(highestQCView::set)
			.doOnNext(newHighestQCView -> System.out.println("Progressed to new highest QC view " + highestQCView))
			.flatMapCompletable(v -> Completable.complete());
	}
}
