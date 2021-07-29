/* Copyright 2021 Radix Publishing Ltd incorporated in Jersey (Channel Islands).
 *
 * Licensed under the Radix License, Version 1.0 (the "License"); you may not use this
 * file except in compliance with the License. You may obtain a copy of the License at:
 *
 * radixfoundation.org/licenses/LICENSE-v1
 *
 * The Licensor hereby grants permission for the Canonical version of the Work to be
 * published, distributed and used under or by reference to the Licensor’s trademark
 * Radix ® and use of any unregistered trade names, logos or get-up.
 *
 * The Licensor provides the Work (and each Contributor provides its Contributions) on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied,
 * including, without limitation, any warranties or conditions of TITLE, NON-INFRINGEMENT,
 * MERCHANTABILITY, or FITNESS FOR A PARTICULAR PURPOSE.
 *
 * Whilst the Work is capable of being deployed, used and adopted (instantiated) to create
 * a distributed ledger it is your responsibility to test and validate the code, together
 * with all logic and performance of that code under all foreseeable scenarios.
 *
 * The Licensor does not make or purport to make and hereby excludes liability for all
 * and any representation, warranty or undertaking in any form whatsoever, whether express
 * or implied, to any entity or person, including any representation, warranty or
 * undertaking, as to the functionality security use, value or other characteristics of
 * any distributed ledger nor in respect the functioning or value of any tokens which may
 * be created stored or transferred using the Work. The Licensor does not warrant that the
 * Work or any use of the Work complies with any law or regulation in any territory where
 * it may be implemented or used or that it will be appropriate for any specific purpose.
 *
 * Neither the licensor nor any current or former employees, officers, directors, partners,
 * trustees, representatives, agents, advisors, contractors, or volunteers of the Licensor
 * shall be liable for any direct or indirect, special, incidental, consequential or other
 * losses of any kind, in tort, contract or otherwise (including but not limited to loss
 * of revenue, income or profits, or loss of use or data, or loss of reputation, or loss
 * of any economic or other opportunity of whatsoever nature or howsoever arising), arising
 * out of or in connection with (without limitation of any use, misuse, of any ledger system
 * or use made or its functionality or any performance or operation of any code or protocol
 * caused by bugs or programming or logic errors or otherwise);
 *
 * A. any offer, purchase, holding, use, sale, exchange or transmission of any
 * cryptographic keys, tokens or assets created, exchanged, stored or arising from any
 * interaction with the Work;
 *
 * B. any failure in a transmission or loss of any token or assets keys or other digital
 * artefacts due to errors in transmission;
 *
 * C. bugs, hacks, logic errors or faults in the Work or any communication;
 *
 * D. system software or apparatus including but not limited to losses caused by errors
 * in holding or transmitting tokens by any third-party;
 *
 * E. breaches or failure of security including hacker attacks, loss or disclosure of
 * password, loss of private key, unauthorised use or misuse of such passwords or keys;
 *
 * F. any losses including loss of anticipated savings or other benefits resulting from
 * use of the Work or any changes to the Work (however implemented).
 *
 * You are solely responsible for; testing, validating and evaluation of all operation
 * logic, functionality, security and appropriateness of using the Work for any commercial
 * or non-commercial purpose and for any reproduction or redistribution by You of the
 * Work. You assume all risks associated with Your use of the Work and the exercise of
 * permissions under this License.
 */

package com.radixdlt.integration.distributed.simulation.tests.consensus;

import static org.assertj.core.api.AssertionsForInterfaceTypes.assertThat;

import com.radixdlt.integration.distributed.simulation.monitors.consensus.ConsensusMonitors;
import com.radixdlt.integration.distributed.simulation.NetworkDroppers;
import com.radixdlt.integration.distributed.simulation.NetworkLatencies;
import com.radixdlt.integration.distributed.simulation.NetworkOrdering;
import com.radixdlt.integration.distributed.simulation.SimulationTest;
import com.radixdlt.integration.distributed.simulation.SimulationTest.Builder;
import org.junit.Test;

import java.util.concurrent.TimeUnit;

/**
 * Simulation which randomly selects (uniform distribution) latencies for each message as
 * long as its still within the bounds of synchrony, the idea being that random edge cases
 * may be found.
 */
public class RandomLatencyTest {
	// use a maxLatency of 20x the min latency since we know a round can take up to
	// atleast 6x transmission time. 20x so that we can hit these cases more often
	private final int minLatency = 10;
	private final int maxLatency = 200;
	// the minimum latency per round is determined using the network latency
	// a round can consist of 6 * max_transmission_time (MTT)
	private final int trips = 6;
	private final int synchronousTimeout = maxLatency * trips;

	private Builder bftTestBuilder = SimulationTest.builder()
		.networkModules(
			NetworkOrdering.inOrder(),
			NetworkLatencies.random(minLatency, maxLatency),
			NetworkDroppers.bftSyncMessagesDropped()
		)
		.pacemakerTimeout(synchronousTimeout) // Since no syncing needed 6*MTT required
		.addTestModules(
			ConsensusMonitors.safety(),
			ConsensusMonitors.liveness(synchronousTimeout, TimeUnit.MILLISECONDS),
			ConsensusMonitors.noTimeouts(),
			ConsensusMonitors.directParents()
		);

	/**
	 * Tests a static configuration of 3 nodes with random, high variance in latency
	 */
	@Test
	public void given_3_correct_nodes_in_random_network_and_no_sync__then_all_synchronous_checks_should_pass() {
		SimulationTest test = bftTestBuilder
			.numNodes(3)
			.build();

		final var runningTest = test.run();
		final var checkResults = runningTest.awaitCompletion();

		assertThat(checkResults).allSatisfy((name, error) -> assertThat(error).isNotPresent());
	}

	/**
	 * Tests a static configuration of 4 nodes with random, high variance in latency
	 */
	@Test
	public void given_4_correct_bfts_in_random_network_and_no_sync__then_all_synchronous_checks_should_pass() {
		SimulationTest test = bftTestBuilder
			.numNodes(4)
			.build();

		final var runningTest = test.run();
		final var checkResults = runningTest.awaitCompletion();

		assertThat(checkResults).allSatisfy((name, error) -> assertThat(error).isNotPresent());
	}
}
