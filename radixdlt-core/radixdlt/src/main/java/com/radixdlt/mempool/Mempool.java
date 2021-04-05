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
package com.radixdlt.mempool;

import com.radixdlt.atom.Txn;
import com.radixdlt.utils.Pair;

import java.util.List;
import java.util.function.Consumer;
import java.util.function.Predicate;

/**
 * Basic mempool functionality.
 * <p>
 * Note that conceptually, a mempoolcan be thought of as a list indexable
 * by hash.
 */
public interface Mempool<T> {
	/**
	 * Add a transaction to the local mempool.
	 */
	void add(Txn txn) throws MempoolRejectedException;

	/**
	 * Retrieve a list of atoms from the local mempool for processing by
	 * consensus.
	 * <p>
	 * Note that the supplied {@code seen} parameter is used to avoid inclusion
	 * of atoms that are "in-flight" but not yet committed to the ledger.
	 *
	 * @param count the number of atoms to retrieve
	 * @param seen hashes of commands seen by consensus, but not yet committed to the ledger
	 * @return A list of commands for processing by consensus
	 */
	List<Txn> getTxns(int count, List<T> seen);

	List<Txn> scanUpdateAndGet(Predicate<MempoolMetadata> predicate, Consumer<MempoolMetadata> operator);

	List<Pair<Txn, Exception>> committed(List<T> committed);

	int getCount();
}
