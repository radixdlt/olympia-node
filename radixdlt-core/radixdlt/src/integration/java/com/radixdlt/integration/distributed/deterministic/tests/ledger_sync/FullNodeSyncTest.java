package com.radixdlt.integration.distributed.deterministic.tests.ledger_sync;

import com.radixdlt.consensus.bft.View;
import com.radixdlt.counters.SystemCounters.CounterType;
import com.radixdlt.integration.distributed.deterministic.DeterministicTest;
import com.radixdlt.sync.SyncConfig;
import org.junit.Test;

import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static com.radixdlt.environment.deterministic.network.MessageSelector.firstSelector;
import static org.junit.Assert.assertTrue;

public class FullNodeSyncTest {
	/* maximum state lag is a single command */
	private static final int FULL_NODE_MAX_BEHIND_STATE_VER = 1;

	private void run(int numNodes, int numValidators, View highView, long targetStateVersion) {
		final var syncConfig =
			SyncConfig.of(
				500L,
				0 /* unused */,
				0L /* unused */,
				numNodes, /* send ledger status update to all nodes */
				Integer.MAX_VALUE /* no rate limiting */
			);

		final var bftTest = DeterministicTest.builder()
			.numNodes(numNodes)
			.messageSelector(firstSelector())
			.epochNodeIndexesMapping(epoch -> IntStream.range(0, numValidators))
			.buildWithLedgerAndEpochsAndSync(highView, syncConfig)
			.runUntil(DeterministicTest.ledgerStateVersionOnNode(targetStateVersion, numNodes - 1));

		final var validatorsCounters = IntStream.range(0, numValidators)
			.mapToObj(bftTest::getSystemCounters);

		final var validatorsMaxStateVersion = validatorsCounters
			.map(sc -> sc.get(CounterType.LEDGER_STATE_VERSION))
			.max(Long::compareTo)
			.get();

		final var nonValidatorsStateVersions = IntStream
			.range(numValidators, numNodes - numValidators)
			.mapToObj(bftTest::getSystemCounters)
			.map(sc -> sc.get(CounterType.LEDGER_STATE_VERSION))
			.collect(Collectors.toList());

		nonValidatorsStateVersions.forEach(stateVersion ->
			assertTrue(stateVersion + FULL_NODE_MAX_BEHIND_STATE_VER >= validatorsMaxStateVersion)
		);
	}

	@Test
	public void total_five_nodes_and_a_single_full_node() {
		this.run(5, 4, View.of(100), 1000L);
	}

	@Test
	public void total_50_nodes_and_just_4_validators_two_views_per_epoch() {
		this.run(50, 4, View.of(2), 500L);
	}

	@Test
	public void total_three_nodes_and_a_single_full_node_10k_views_per_epoch() {
		this.run(3, 2, View.of(10000), 1000L);
	}
}
