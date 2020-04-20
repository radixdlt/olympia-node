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
import com.radixdlt.consensus.checks.LivenessCheck;
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
	private final long time;
	private final TimeUnit timeUnit;
	private final BFTTestNetwork bftNetwork;
	private final List<Observable<Object>> assertions = new ArrayList<>();
	private static final int MINIMUM_NETWORK_LATENCY = 10;
	// 6 times max latency should be less than BFTTestNetwork.TEST_PACEMAKER_TIMEOUT
	// so we don't get unwanted pacemaker timeouts
	private static final int MAXIMUM_NETWORK_LATENCY = 160;

	public BFTTest(int numNodes, long time, TimeUnit timeUnit) {
		this.time = time;
		this.timeUnit = timeUnit;
		List<ECKeyPair> nodes = Stream.generate(ECKeyPair::generateNew)
			.limit(numNodes)
			.collect(Collectors.toList());
		this.bftNetwork =  new BFTTestNetwork(
			nodes,
			TestEventCoordinatorNetwork.orderedRandomlyLatent(MINIMUM_NETWORK_LATENCY, MAXIMUM_NETWORK_LATENCY)
		);
	}

	public void assertLiveness() {
		this.assertions.add(new LivenessCheck().check(bftNetwork));
	}

	public void assertSafety() {
		this.assertions.add(new SafetyCheck().check(bftNetwork));
	}

	public void assertNoTimeouts() {
		this.assertions.add(new NoTimeoutCheck().check(bftNetwork));
	}

	public void assertNoSyncExceptions() {
		this.assertions.add(new NoSyncExceptionCheck().check(bftNetwork));
	}

	public void assertAllProposalsHaveDirectParents() {
		this.assertions.add(new AllProposalsHaveDirectParentsCheck().check(bftNetwork));
	}

	public void run() {
		Observable.mergeArray(bftNetwork.processBFT(), Observable.merge(this.assertions))
			.take(time, timeUnit)
			.blockingSubscribe();
	}
}
