/*
 * (C) Copyright 2021 Radix DLT Ltd
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

package com.radixdlt.client.store;

import com.radixdlt.client.store.berkeley.ScheduledQueueFlush;
import com.radixdlt.environment.EventProcessor;
import com.radixdlt.identifiers.RRI;
import com.radixdlt.identifiers.RadixAddress;
import com.radixdlt.utils.UInt256;
import com.radixdlt.utils.functional.Result;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * High level JSON RPC client API store.
 */
public interface ClientApiStore {

	/**
	 * Retrieve list of immediately spendable token balances.
	 *
	 * @param address client address
	 *
	 * @return list of token balances
	 */
	Result<List<TokenBalance>> getTokenBalances(RadixAddress address);

	/**
	 * Flush intermediate storage and save particles into persistent DB.
	 */
	void storeCollected();

	/**
	 * Get current supply of the specified token.
	 *
	 * @param rri token for which supply is requested
	 *
	 * @return eventually consistent token supply
	 */
	Result<UInt256> getTokenSupply(RRI rri);

	/**
	 * Retrieve token definition. Note that for mutable supply tokens supply is returned zero.
	 * If actual token supply value is necessary then {@link #getTokenSupply(RRI)} should be used.
	 *
	 * @param rri token for which definition is requested
	 *
	 * @return token definition.
	 */
	Result<TokenDefinitionRecord> getTokenDefinition(RRI rri);

	/**
	 * Retrieve transaction history for provided address.
	 *
	 * @param address client address
	 * @param size number of elements to return
	 * @param cursor optional cursor from previous request
	 *
	 * @return list of transaction history entries.
	 */
	Result<List<TxHistoryEntry>> getTransactionHistory(RadixAddress address, int size, Optional<Instant> cursor);

	EventProcessor<ScheduledQueueFlush> queueFlushProcessor();
}
