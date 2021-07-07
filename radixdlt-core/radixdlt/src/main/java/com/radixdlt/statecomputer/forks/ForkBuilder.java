/*
 * (C) Copyright 2021 Radix DLT Ltd
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
 *
 */

package com.radixdlt.statecomputer.forks;

import com.google.common.hash.HashCode;
import com.radixdlt.utils.functional.Either;
import java.util.function.Function;

/**
 * An intermediate class used for creating fork configurations.
 * Allows to modify various config parameters before the actual fork config object is created.
 */
public final class ForkBuilder {
	private final String name;
	private final HashCode hash;
	private final Either<Long, CandidateForkPredicate> forkExecution;
	private final RERulesVersion reRulesVersion;
	private final RERulesConfig reRulesConfig;

	public ForkBuilder(
		String name,
		HashCode hash,
		long fixedEpoch,
		RERulesVersion reRulesVersion,
		RERulesConfig reRulesConfig
	) {
		this(name, hash, Either.left(fixedEpoch), reRulesVersion, reRulesConfig);
	}

	public ForkBuilder(
		String name,
		HashCode hash,
		CandidateForkPredicate predicate,
		RERulesVersion reRulesVersion,
		RERulesConfig reRulesConfig
	) {
		this(name, hash, Either.right(predicate), reRulesVersion, reRulesConfig);
	}

	private ForkBuilder(
		String name,
		HashCode hash,
		Either<Long, CandidateForkPredicate> forkExecution,
		RERulesVersion reRulesVersion,
		RERulesConfig reRulesConfig
	) {
		this.name = name;
		this.hash = hash;
		this.forkExecution = forkExecution;
		this.reRulesVersion = reRulesVersion;
		this.reRulesConfig = reRulesConfig;
	}

	public String getName() {
		return name;
	}

	public RERulesConfig getEngineRulesConfig() {
		return reRulesConfig;
	}

	public RERulesVersion getReRulesVersion() {
		return reRulesVersion;
	}

	public ForkBuilder withEngineRulesConfig(RERulesConfig newEngineRulesConfig) {
		return new ForkBuilder(name, hash, forkExecution, reRulesVersion, newEngineRulesConfig);
	}

	public ForkBuilder atFixedEpoch(long fixedEpoch) {
		return new ForkBuilder(name, hash, Either.left(fixedEpoch), reRulesVersion, reRulesConfig);
	}

	public ForkBuilder withStakeVoting(long minEpoch, int requiredStake) {
		return new ForkBuilder(
			name,
			hash,
			Either.right(CandidateForkPredicates.stakeVoting(minEpoch, requiredStake)),
				reRulesVersion,
			reRulesConfig
		);
	}

	public long fixedOrMinEpoch() {
		return forkExecution.fold(Function.identity(), CandidateForkPredicate::minEpoch);
	}

	public ForkConfig build() {
		final var reRules = reRulesVersion.create(reRulesConfig);
		return forkExecution.fold(
			fixedEpoch -> new FixedEpochForkConfig(name, hash, reRules, fixedEpoch),
			predicate -> new CandidateForkConfig(name, hash, reRules, predicate)
		);
	}
}
