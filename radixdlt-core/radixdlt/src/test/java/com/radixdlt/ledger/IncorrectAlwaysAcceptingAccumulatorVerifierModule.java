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

package com.radixdlt.ledger;

import com.google.common.collect.ImmutableList;
import com.google.common.hash.HashCode;
import com.google.inject.AbstractModule;
import com.google.inject.Provides;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;

/**
 * Accumulator verifier which incorrectly always gives false positives.
 */
public class IncorrectAlwaysAcceptingAccumulatorVerifierModule extends AbstractModule {
	@Provides
	private LedgerAccumulatorVerifier badVerifier() {
		return new LedgerAccumulatorVerifier() {
			@Override
			public boolean verify(AccumulatorState head, ImmutableList<HashCode> commands, AccumulatorState tail) {
				return true;
			}

			@Override
			public <T> Optional<ImmutableList<T>> verifyAndGetExtension(
				AccumulatorState current,
				ImmutableList<T> commands,
				Function<T, HashCode> hashCodeMapper,
				AccumulatorState tail
			) {
				final long firstVersion = tail.getStateVersion() - commands.size() + 1;
				if (current.getStateVersion() + 1 < firstVersion) {
					// Missing versions
					return Optional.empty();
				}

				if (commands.isEmpty()) {
					return (Objects.equals(current, tail)) ? Optional.of(ImmutableList.of()) : Optional.empty();
				}

				final int startIndex = (int) (current.getStateVersion() + 1 - firstVersion);
				return Optional.of(commands.subList(startIndex, commands.size()));
			}
		};
	}
}
