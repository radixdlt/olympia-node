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

import java.util.List;
import java.util.Optional;
import java.util.function.Function;

/**
 * Verifies whether a given accumulator extends a given one.
 *
 * All implementations should be stateless.
 */
public interface LedgerAccumulatorVerifier {
	// TODO: use stream instead of list
	boolean verify(AccumulatorState head, ImmutableList<HashCode> commands, AccumulatorState tail);

	<T> Optional<List<T>> verifyAndGetExtension(
		AccumulatorState current,
		List<T> commands,
		Function<T, HashCode> hashCodeMapper,
		AccumulatorState tail
	);
}
