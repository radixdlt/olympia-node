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

import com.google.common.collect.ImmutableList;
import com.radixdlt.consensus.checks.AllProposalsHaveDirectParentsCheck;
import com.radixdlt.consensus.checks.LivenessCheck;
import com.radixdlt.consensus.checks.NoSyncExceptionCheck;
import com.radixdlt.consensus.checks.NoTimeoutCheck;
import com.radixdlt.consensus.checks.SafetyCheck;
import com.radixdlt.crypto.ECKeyPair;
import com.radixdlt.middleware2.network.TestEventCoordinatorNetwork;
import io.reactivex.rxjava3.core.Completable;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class BFTTest {
	private final int numNodes;
	private final TestEventCoordinatorNetwork network;
	private final ImmutableList<BFTCheck> checks;

	private BFTTest(int numNodes, TestEventCoordinatorNetwork network, ImmutableList<BFTCheck> checks) {
		this.numNodes = numNodes;
		this.network = network;
		this.checks = checks;
	}

	public static class Builder {
		private final TestEventCoordinatorNetwork.Builder networkBuilder = TestEventCoordinatorNetwork.builder();
		private final List<BFTCheck> checks = new ArrayList<>();
		private int numNodes = 1;

		private Builder() {
		}

		public Builder numNodes(int numNodes) {
			this.numNodes = numNodes;
			return this;
		}

		public Builder randomLatency(int minLatency, int maxLatency) {
			networkBuilder.randomLatency(minLatency, maxLatency);
			return this;
		}

		public Builder checkLiveness() {
			this.checks.add(new LivenessCheck(6 * TestEventCoordinatorNetwork.DEFAULT_LATENCY, TimeUnit.MILLISECONDS));
			return this;
		}

		public Builder checkLiveness(long time, TimeUnit timeUnit) {
			this.checks.add(new LivenessCheck(time, timeUnit));
			return this;
		}

		public Builder checkSafety() {
			this.checks.add(new SafetyCheck());
			return this;
		}

		public Builder checkNoTimeouts() {
			this.checks.add(new NoTimeoutCheck());
			return this;
		}

		public Builder checkNoSyncExceptions() {
			this.checks.add(new NoSyncExceptionCheck());
			return this;
		}

		public Builder checkAllProposalsHaveDirectParents() {
			this.checks.add(new AllProposalsHaveDirectParentsCheck());
			return this;

		}

		public BFTTest build() {
			return new BFTTest(numNodes, networkBuilder.build(), ImmutableList.copyOf(checks));
		}
	}

	public static Builder builder() {
		return new Builder();
	}

	public void run(long time, TimeUnit timeUnit) {
		List<ECKeyPair> nodes = Stream.generate(ECKeyPair::generateNew)
			.limit(numNodes)
			.collect(Collectors.toList());
		BFTTestNetwork bftNetwork =  new BFTTestNetwork(nodes, network);
		List<Completable> assertions = this.checks.stream().map(c -> c.check(bftNetwork)).collect(Collectors.toList());
		Completable.mergeArray(bftNetwork.processBFT().flatMapCompletable(e -> Completable.complete()), Completable.merge(assertions))
			.blockingAwait(time, timeUnit);
	}
}
