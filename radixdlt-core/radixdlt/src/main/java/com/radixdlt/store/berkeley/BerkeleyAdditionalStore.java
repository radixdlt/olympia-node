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

package com.radixdlt.store.berkeley;

import com.radixdlt.constraintmachine.REProcessedTxn;
import com.radixdlt.store.DatabaseEnvironment;
import com.sleepycat.je.Transaction;

/**
 * Simple way to add an additional store
 * TODO: perhaps needs to be integrated at a higher level with RadixEngine
 * TODO: Make more generic rather than just attachment to BerkeleyLedgerEntryStore
 * TODO: Implement all other additional databases with this interface
 */
public interface BerkeleyAdditionalStore {
	void open(DatabaseEnvironment dbEnv);
	void close();
	void process(Transaction dbTxn, REProcessedTxn txn);
}
