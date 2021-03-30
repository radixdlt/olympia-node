/*
 * (C) Copyright 2020 Radix DLT Ltd
 *
 * Radix DLT Ltd licenses this file to you under the Apache License,
 * Version 2.0 (the "License"); you may not use this file except in
 * compliance with the License.  You may obtain a copy of the
 * License at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND,
 * either express or implied.  See the License for the specific
 * language governing permissions and limitations under the License.
 */

package com.radixdlt.store;

import com.radixdlt.atom.SubstateId;
import com.radixdlt.constraintmachine.Particle;

import java.util.Optional;

/**
 * Read only store interface for Constraint Machine validation
 */
public interface CMStore {
	/**
	 * Hack for atomic transaction, better to implement
	 * whole function in single interface in future.
	 */
	interface Transaction {
		default void commit() {
		}

		default void abort() {
		}

		default <T> T unwrap() {
			return null;
		}
	}

	Transaction createTransaction();

	boolean isVirtualDown(Transaction txn, SubstateId substateId);

	Optional<Particle> loadUpParticle(Transaction txn, SubstateId substateId);
}
