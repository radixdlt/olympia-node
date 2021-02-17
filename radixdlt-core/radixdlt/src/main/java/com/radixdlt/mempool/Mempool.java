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

import com.google.common.hash.HashCode;
import com.radixdlt.consensus.Command;
import java.util.List;
import java.util.Set;

/**
 * Basic mempool functionality.
 * Implementations are expected to be thread safe.
 * <p>
 * Note that conceptually, a mempoolcan be thought of as a list indexable
 * by hash and ordered FIFO by {@link #add(Command)} call order.
 */
public interface Mempool<T> {
	/**
	 * Add a command to the local mempool.
	 * Should be called after atom has been validated.
	 *
	 * @param command The command to add.
	 * @throws MempoolFullException if the mempool cannot accept new submissions.
	 * @throws MempoolDuplicateException if the mempool already has the specified atom
	 */
	void add(T command) throws MempoolFullException, MempoolDuplicateException;

	/**
	 * Remove the referenced atom from the local mempool after it has
	 * been committed by consensus.
	 *
	 * @param cmdHash The hash of the command to remove
	 */
	void removeCommitted(HashCode cmdHash);

	/**
	 * Remove the referenced atom from the local mempool after it has
	 * been rejected by consensus.
	 *
	 * @param cmdHash The hash of the command to remove
	 */
	void removeRejected(HashCode cmdHash);

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
	List<T> getCommands(int count, Set<HashCode> seen);

	/**
	 * Return approximate count of commands in the mempool.
	 * Note that this value will be approximate, and will change dynamically
	 * as atoms are added and removed.
	 */
	int count();
}
