package com.radixdlt.integration.distributed.simulation.tests.full_function_forks;

import com.google.common.collect.ImmutableList;
import com.google.inject.AbstractModule;
import com.google.inject.Provides;
import com.google.inject.Singleton;
import com.radixdlt.consensus.bft.View;
import com.radixdlt.statecomputer.forks.ForkBuilder;
import com.radixdlt.statecomputer.forks.ForksPredicates;
import com.radixdlt.statecomputer.forks.MainnetEngineRules;
import com.radixdlt.statecomputer.forks.RERulesConfig;

public final class MockedForksModule extends AbstractModule {

	private final View epochCeilingView;

	public MockedForksModule(View epochCeilingView) {
		this.epochCeilingView = epochCeilingView;
	}

	@Provides
	@Singleton
	ImmutableList<ForkBuilder> forksConfig() {
		final var baseForkBuilder = new ForkBuilder(
			"fork1",
			ForksPredicates.atEpoch(0L),
			MainnetEngineRules.mainnetGenesis,
			new RERulesConfig(false, epochCeilingView.number(), 0L)
		);

		/* three forks at fixed epochs and one fork with stake voting, all based on the most recent "real" fork */
		return ImmutableList.of(
			copyOfAtEpoch(baseForkBuilder, "fork1", 0),
			copyOfAtEpoch(baseForkBuilder, "fork2", 5),
			copyOfAtEpoch(baseForkBuilder, "fork3", 10),
			copyOfWithVoting(baseForkBuilder, "fork4", 0.51)
		);
	}

	private ForkBuilder copyOfAtEpoch(ForkBuilder original, String name, long epoch) {
		return new ForkBuilder(
			name,
			ForksPredicates.atEpoch(epoch),
			original.getEngineRulesFactory(),
			original.getEngineRulesConfig()
		);
	}

	private ForkBuilder copyOfWithVoting(ForkBuilder original, String name, double required) {
		return new ForkBuilder(
			name,
			ForksPredicates.stakeVoting(required),
			original.getEngineRulesFactory(),
			original.getEngineRulesConfig()
		);
	}
}
