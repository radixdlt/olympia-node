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

package com.radixdlt.atom;

import com.radixdlt.constraintmachine.Particle;
import com.radixdlt.constraintmachine.SubstateDeserialization;

import java.util.NoSuchElementException;

/**
 * Store which contains an index into up substates
 */
public interface SubstateStore {

	CloseableCursor<Substate> openIndexedCursor(
		Class<? extends Particle> particleClass,
		SubstateDeserialization deserialization
	);

	static SubstateStore empty() {
		return (c, d) -> new CloseableCursor<>() {
			@Override
			public void close() {
			}

			@Override
			public boolean hasNext() {
				return false;
			}

			@Override
			public Substate next() {
				throw new NoSuchElementException();
			}
		};
	}
}
