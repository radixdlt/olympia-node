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

import com.radixdlt.consensus.checks.AllProposalsHaveDirectParentsCheck;
import com.radixdlt.consensus.checks.StrongLivenessCheck;
import com.radixdlt.consensus.checks.NoSyncExceptionCheck;
import com.radixdlt.consensus.checks.NoTimeoutCheck;
import com.radixdlt.consensus.checks.SafetyCheck;
import com.radixdlt.crypto.ECKeyPair;
import com.radixdlt.middleware2.network.TestEventCoordinatorNetwork;
import io.reactivex.rxjava3.core.Observable;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class BFTTest {
	private final int numNodes;
	private final long time;
	private final TimeUnit timeUnit;
	private final int minNetworkLatency;
	private final int maxNetworkLatency;
	private final List<BFTCheck> checks = new ArrayList<>();


	private BFTTest(int numNodes, long time, TimeUnit timeUnit, int minNetworkLatency, int maxNetworkLatency) {
		this.numNodes = numNodes;
		this.time = time;
		this.timeUnit = timeUnit;
		this.minNetworkLatency = minNetworkLatency;
		this.maxNetworkLatency = maxNetworkLatency;
	}

	public static class Builder {
		private int numNodes = 1;
		private long time = 1;
		private TimeUnit timeUnit = TimeUnit.MINUTES;
		private int minNetworkLatency = 50;
		private int maxNetworkLatency = 50;

		private Builder() {
		}

		public Builder numNodes(int numNodes) {
			this.numNodes = numNodes;
			return this;
		}

		public Builder time(long time, TimeUnit timeUnit) {
			this.time = time;
			this.timeUnit = timeUnit;
			return this;
		}

		public Builder networkLatency(int minNetworkLatency, int maxNetworkLatency) {
			this.minNetworkLatency = minNetworkLatency;
			this.maxNetworkLatency = maxNetworkLatency;
			return this;
		}

		public BFTTest build() {
			return new BFTTest(numNodes, time, timeUnit, minNetworkLatency, maxNetworkLatency);
		}
	}

	public static Builder builder() {
		return new Builder();
	}

	public void assertLiveness() {
		this.checks.add(new StrongLivenessCheck());
	}

	public void assertSafety() {
		this.checks.add(new SafetyCheck());
	}

	public void assertNoTimeouts() {
		this.checks.add(new NoTimeoutCheck());
	}

	public void assertNoSyncExceptions() {
		this.checks.add(new NoSyncExceptionCheck());
	}

	public void assertAllProposalsHaveDirectParents() {
		this.checks.add(new AllProposalsHaveDirectParentsCheck());
	}

	public void run() {
		List<ECKeyPair> nodes = Stream.generate(ECKeyPair::generateNew)
			.limit(numNodes)
			.collect(Collectors.toList());
		TestEventCoordinatorNetwork network = TestEventCoordinatorNetwork.builder()
			.minLatency(minNetworkLatency)
			.maxLatency(maxNetworkLatency)
			.build();

		BFTTestNetwork bftNetwork =  new BFTTestNetwork(nodes, network);
		List<Observable<Object>> assertions = this.checks.stream().map(c -> c.check(bftNetwork)).collect(Collectors.toList());
		Observable.mergeArray(bftNetwork.processBFT(), Observable.merge(assertions))
			.take(time, timeUnit)
			.blockingSubscribe();
	}
}
