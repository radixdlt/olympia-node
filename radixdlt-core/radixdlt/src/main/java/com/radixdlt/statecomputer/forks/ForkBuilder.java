package com.radixdlt.statecomputer.forks;

import com.radixdlt.engine.RadixEngine;
import com.radixdlt.statecomputer.LedgerAndBFTProof;
import com.radixdlt.utils.Triplet;

import java.util.function.Function;
import java.util.function.Predicate;

/**
 * An intermediate class used for creating fork configurations.
 * Allows to modify various config parameters before the actual fork config object is created.
 */
public final class ForkBuilder {
	private final String name;
	private final Predicate<Triplet<ForkConfig, RadixEngine<LedgerAndBFTProof>, LedgerAndBFTProof>> executePredicate;
	private final Function<RERulesConfig, RERules> reRulesFactory;
	private final RERulesConfig reRulesConfig;

	public ForkBuilder(
		String name,
		Predicate<Triplet<ForkConfig, RadixEngine<LedgerAndBFTProof>, LedgerAndBFTProof>> executePredicate,
		Function<RERulesConfig, RERules> reRulesFactory,
		RERulesConfig reRulesConfig
	) {
		this.name = name;
		this.executePredicate = executePredicate;
		this.reRulesFactory = reRulesFactory;
		this.reRulesConfig = reRulesConfig;
	}

	public String getName() {
		return name;
	}

	public Predicate<Triplet<ForkConfig, RadixEngine<LedgerAndBFTProof>, LedgerAndBFTProof>> getExecutePredicate() {
		return executePredicate;
	}
	public RERulesConfig getEngineRulesConfig() {
		return reRulesConfig;
	}

	public Function<RERulesConfig, RERules> getEngineRulesFactory() {
		return reRulesFactory;
	}

	public ForkBuilder withEngineRules(RERulesConfig newEngineRules) {
		return new ForkBuilder(name, executePredicate, reRulesFactory, newEngineRules);
	}

	public ForkBuilder withExecutePredicate(Predicate<Triplet<ForkConfig, RadixEngine<LedgerAndBFTProof>, LedgerAndBFTProof>> newExecutePredicate) {
		return new ForkBuilder(name, newExecutePredicate, reRulesFactory, reRulesConfig);
	}

	public ForkConfig build() {
		return new ForkConfig(name, executePredicate, reRulesFactory.apply(reRulesConfig));
	}
}
