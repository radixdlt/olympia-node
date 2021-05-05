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

package com.radixdlt.client.lib.api;

import com.radixdlt.client.lib.dto.BuiltTransactionDTO;
import com.radixdlt.client.lib.dto.FinalizedTransaction;
import com.radixdlt.client.lib.dto.NetworkIdDTO;
import com.radixdlt.client.lib.dto.NetworkStatsDTO;
import com.radixdlt.client.lib.dto.StakePositionsDTO;
import com.radixdlt.client.lib.dto.TokenBalancesDTO;
import com.radixdlt.client.lib.dto.TokenInfoDTO;
import com.radixdlt.client.lib.dto.TransactionDTO;
import com.radixdlt.client.lib.dto.TransactionHistoryDTO;
import com.radixdlt.client.lib.dto.TransactionStatusDTO;
import com.radixdlt.client.lib.dto.TxDTO;
import com.radixdlt.client.lib.dto.UnstakePositionsDTO;
import com.radixdlt.client.lib.dto.ValidatorDTO;
import com.radixdlt.client.lib.dto.ValidatorsResponseDTO;
import com.radixdlt.client.lib.impl.SynchronousRadixApiClient;
import com.radixdlt.identifiers.AID;
import com.radixdlt.utils.functional.Result;

import java.util.List;
import java.util.Optional;

public interface RadixApi {
	static Result<SynchronousRadixApiClient> connect(String baseUrl) {
		return SynchronousRadixApiClient.connect(baseUrl);
	}

	Result<NetworkIdDTO> networkId();

	Result<TokenInfoDTO> nativeToken();

	Result<TokenInfoDTO> tokenInfo(String rri);

	Result<TokenBalancesDTO> tokenBalances(AccountAddress address);

	Result<TransactionHistoryDTO> transactionHistory(AccountAddress address, int size, Optional<NavigationCursor> cursor);

	Result<TransactionDTO> lookupTransaction(AID txId);

	Result<List<StakePositionsDTO>> stakePositions(AccountAddress address);

	Result<List<UnstakePositionsDTO>> unstakePositions(AccountAddress address);

	Result<TransactionStatusDTO> statusOfTransaction(AID txId);

	Result<NetworkStatsDTO> networkTransactionThroughput();

	Result<NetworkStatsDTO> networkTransactionDemand();

	Result<ValidatorsResponseDTO> validators(int size, Optional<NavigationCursor> cursor);

	Result<ValidatorDTO> lookupValidator(String validatorAddress);

	Result<BuiltTransactionDTO> buildTransaction(TransactionRequest request);

	Result<TxDTO> finalizeTransaction(FinalizedTransaction request);

	Result<TxDTO> submitTransaction(FinalizedTransaction request);
}
