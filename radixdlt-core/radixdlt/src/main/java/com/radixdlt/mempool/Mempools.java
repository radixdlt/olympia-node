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

package com.radixdlt.mempool;

import com.radixdlt.consensus.Command;
import com.radixdlt.utils.Pair;

import java.util.List;
import java.util.Set;

/**
 * Mempool which is always empty
 */
public class Mempools {
	private Mempools() {
		throw new IllegalStateException("Cannot instantiate.");
	}

	public static <T> Mempool<T> empty() {
		return new Mempool<>() {
			@Override
			public void add(Command command) throws MempoolFullException, MempoolDuplicateException {
				// No-op
			}

			@Override
			public List<Pair<Command, Exception>> committed(List<T> committed) {
				return List.of();
			}

			@Override
			public List<Command> getCommands(int count, Set<Command> seen) {
				return List.of();
			}
		};
	}
}
