package com.radixdlt.integration.distributed.simulation.tests.full_function_forks;

import com.google.common.hash.HashCode;
import com.google.inject.AbstractModule;
import com.google.inject.multibindings.Multibinder;
import com.google.inject.multibindings.ProvidesIntoSet;
import com.radixdlt.application.system.FeeTable;
import com.radixdlt.application.tokens.Amount;
import com.radixdlt.consensus.bft.View;
import com.radixdlt.crypto.HashUtils;
import com.radixdlt.statecomputer.forks.ForkBuilder;
import com.radixdlt.statecomputer.forks.CandidateForkPredicates;
import com.radixdlt.statecomputer.forks.MainnetEngineRules;
import com.radixdlt.statecomputer.forks.RERulesConfig;

import java.nio.charset.StandardCharsets;
import java.util.OptionalInt;

public final class MockedForksModule extends AbstractModule {

	private final ForkBuilder baseForkBuilder;

	public MockedForksModule(View epochCeilingView) {
		this.baseForkBuilder = new ForkBuilder(
			"",
			HashCode.fromInt(0),
			0L,
			MainnetEngineRules.olympiaV1,
			new RERulesConfig(
				FeeTable.noFees(),
				OptionalInt.empty(),
				epochCeilingView.number(),
				0L,
				Amount.zero(),
				0L,
				Amount.zero(),
				0,
				10
			)
		);
	}

	@Override
	protected void configure() {
		Multibinder.newSetBinder(binder(), ForkBuilder.class);
	}

	/* three forks at fixed epochs and one fork with stake voting, all based on the most recent "real" fork */

	@ProvidesIntoSet
	ForkBuilder fork1() {
		return copyBaseAtEpoch("fork1", 0);
	}

	@ProvidesIntoSet
	ForkBuilder fork2() {
		return copyBaseAtEpoch("fork2", 5);
	}

	@ProvidesIntoSet
	ForkBuilder fork3() {
		return copyBaseAtEpoch("fork3", 10);
	}

	@ProvidesIntoSet
	ForkBuilder fork4() {
		return copyBaseWithVoting("fork4", 5100, 20L);
	}

	private ForkBuilder copyBaseAtEpoch(String name, long epoch) {
		return new ForkBuilder(
			name,
			HashUtils.sha256(name.getBytes(StandardCharsets.UTF_8)),
			epoch,
			baseForkBuilder.getEngineRulesFactory(),
			baseForkBuilder.getEngineRulesConfig()
		);
	}

	private ForkBuilder copyBaseWithVoting(String name, int percentage, long minEpoch) {
		return new ForkBuilder(
			name,
			HashUtils.sha256(name.getBytes(StandardCharsets.UTF_8)),
			CandidateForkPredicates.stakeVoting(minEpoch, percentage),
			baseForkBuilder.getEngineRulesFactory(),
			baseForkBuilder.getEngineRulesConfig()
		);
	}
}
