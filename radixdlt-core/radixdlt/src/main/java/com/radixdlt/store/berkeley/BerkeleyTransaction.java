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
 */

package com.radixdlt.store.berkeley;

import com.radixdlt.store.EngineStore;

/**
 * Implementation of {@link com.radixdlt.store.EngineStore.Transaction} for BDB.
 */
final class BerkeleyTransaction implements EngineStore.Transaction {
	private final com.sleepycat.je.Transaction transaction;

	private BerkeleyTransaction(com.sleepycat.je.Transaction transaction) {
		this.transaction = transaction;
	}

	public static EngineStore.Transaction wrap(com.sleepycat.je.Transaction transaction) {
		return new BerkeleyTransaction(transaction);
	}

	@Override
	public void commit() {
		transaction.commit();
	}

	@Override
	public void abort() {
		transaction.abort();
	}

	@Override
	@SuppressWarnings("unchecked")
	public com.sleepycat.je.Transaction unwrap() {
		return transaction;
	}
}
