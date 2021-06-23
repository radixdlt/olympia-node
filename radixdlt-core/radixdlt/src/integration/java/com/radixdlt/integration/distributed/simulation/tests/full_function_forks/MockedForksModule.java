package com.radixdlt.integration.distributed.simulation.tests.full_function_forks;

import com.google.common.collect.ImmutableList;
import com.google.inject.AbstractModule;
import com.google.inject.Provides;
import com.google.inject.Singleton;
import com.radixdlt.consensus.bft.View;
import com.radixdlt.statecomputer.forks.BetanetForksModule;
import com.radixdlt.statecomputer.forks.ForkConfig;
import com.radixdlt.statecomputer.forks.ForksPredicates;

public final class MockedForksModule extends AbstractModule {

	private final View epochCeilingView;

	public MockedForksModule(View epochCeilingView) {
		this.epochCeilingView = epochCeilingView;
	}

	@Provides
	@Singleton
	ImmutableList<ForkConfig> forksConfig() {
		final var latestBetanetFork = BetanetForksModule.betanetV4();

		/* three forks at fixed epochs and one fork with stake voting, all based on the most recent "real" fork */
		return ImmutableList.of(
			copyOfAtEpoch(latestBetanetFork, "fork1", 0),
			copyOfAtEpoch(latestBetanetFork, "fork2", 5),
			copyOfAtEpoch(latestBetanetFork, "fork3", 10),
			copyOfWithVoting(latestBetanetFork, "fork4", 0.51)
		);
	}

	private ForkConfig copyOfAtEpoch(ForkConfig original, String name, long epoch) {
		return new ForkConfig(
			name,
			ForksPredicates.atEpoch(epoch),
			original.getParser(),
			original.getSubstateSerialization(),
		 	original.getConstraintMachineConfig().metering((procedureKey, param, context) -> { }),
			original.getActionConstructors(),
			original.getBatchVerifier(),
			original.getPostProcessedVerifier(),
			epochCeilingView
		);
	}

	private ForkConfig copyOfWithVoting(ForkConfig original, String name, double required) {
		return new ForkConfig(
			name,
			ForksPredicates.stakeVoting(required),
			original.getParser(),
			original.getSubstateSerialization(),
			original.getConstraintMachineConfig().metering((procedureKey, param, context) -> { }),
			original.getActionConstructors(),
			original.getBatchVerifier(),
			original.getPostProcessedVerifier(),
			epochCeilingView
		);
	}

}
