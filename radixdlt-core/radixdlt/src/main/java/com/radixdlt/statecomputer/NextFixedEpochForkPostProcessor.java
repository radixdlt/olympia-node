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

package com.radixdlt.statecomputer;

import com.radixdlt.constraintmachine.REProcessedTxn;
import com.radixdlt.engine.PostProcessor;
import com.radixdlt.engine.PostProcessorException;
import com.radixdlt.engine.parser.REParser;
import com.radixdlt.statecomputer.forks.FixedEpochForkConfig;
import com.radixdlt.store.EngineStore;

import java.util.List;

/**
 * Checks whether the engine should switch to the next fixed epoch fork.
 * If so, adds nextForkHash to result metadata.
 */
public final class NextFixedEpochForkPostProcessor implements PostProcessor<LedgerAndBFTProof> {
	private final REParser reParser;
	private final FixedEpochForkConfig nextFork;

	public NextFixedEpochForkPostProcessor(REParser reParser, FixedEpochForkConfig nextFork) {
		this.reParser = reParser;
		this.nextFork = nextFork;
	}

	@Override
	public LedgerAndBFTProof process(
		LedgerAndBFTProof metadata,
		EngineStore<LedgerAndBFTProof> engineStore,
		List<REProcessedTxn> txns
	) throws PostProcessorException {
		if (metadata.getProof().getNextValidatorSet().isPresent()
				&& nextFork.epoch() == metadata.getProof().getEpoch() + 1) {
			return metadata.withNextForkHash(nextFork.hash());
		} else {
			return metadata;
		}
	}
}
