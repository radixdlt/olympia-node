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

package com.radixdlt.api.store;

import com.radixdlt.api.data.BalanceEntry;
import com.radixdlt.api.data.ScheduledQueueFlush;
import com.radixdlt.api.data.TxHistoryEntry;
import com.radixdlt.environment.EventProcessor;
import com.radixdlt.identifiers.AID;
import com.radixdlt.identifiers.REAddr;
import com.radixdlt.statecomputer.TxnsCommittedToLedger;
import com.radixdlt.utils.UInt384;
import com.radixdlt.utils.functional.Result;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * High level JSON RPC client API store.
 */
public interface ClientApiStore {


	enum BalanceType {
		SPENDABLE, STAKES, UNSTAKES;
	}
	/**
	 * Retrieve list of immediately spendable token balances or stakes.
	 *
	 * @param addr client address
	 * @param type the type of balance
	 *
	 * @return list of token balances
	 */
	Result<List<BalanceEntry>> getTokenBalances(REAddr addr, BalanceType type);

	/**
	 * Get current consensus epoch.
	 *
	 * @return current consensus epoch
	 */
	long getEpoch();

	/**
	 * Get current supply of the specified token.
	 *
	 * @param rri token for which supply is requested
	 *
	 * @return eventually consistent token supply
	 */
	Result<UInt384> getTokenSupply(String rri);

	/**
	 * Retrieve token definition. Note that for mutable supply tokens supply is returned zero.
	 * If actual token supply value is necessary then {@link #getTokenSupply(String)} should be used.
	 *
	 * @param rri token for which definition is requested
	 *
	 * @return token definition.
	 */
	Result<TokenDefinitionRecord> getTokenDefinition(REAddr rri);

	/**
	 * Retrieve transaction history for provided address.
	 *
	 * @param address client address
	 * @param size number of elements to return
	 * @param cursor optional cursor from previous request
	 *
	 * @return list of transaction history entries.
	 */
	Result<List<TxHistoryEntry>> getTransactionHistory(REAddr address, int size, Optional<Instant> cursor);

	/**
	 * Retrieve single transaction history entry.
	 *
	 * @param txId transaction id
	 *
	 * @return transaction history entry.
	 */
	Result<TxHistoryEntry> getTransaction(AID txId);

	Result<REAddr> parseRri(String rri);

	EventProcessor<ScheduledQueueFlush> queueFlushProcessor();

	EventProcessor<TxnsCommittedToLedger> atomsCommittedToLedgerEventProcessor();
}
