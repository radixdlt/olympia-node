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

package com.radixdlt.client.lib.api.async;

import com.radixdlt.client.lib.api.AccountAddress;
import com.radixdlt.client.lib.api.NavigationCursor;
import com.radixdlt.client.lib.api.TransactionRequest;
import com.radixdlt.client.lib.dto.ApiConfiguration;
import com.radixdlt.client.lib.dto.ApiData;
import com.radixdlt.client.lib.dto.BuiltTransaction;
import com.radixdlt.client.lib.dto.Checkpoint;
import com.radixdlt.client.lib.dto.ConsensusConfiguration;
import com.radixdlt.client.lib.dto.ConsensusData;
import com.radixdlt.client.lib.dto.EpochData;
import com.radixdlt.client.lib.dto.FinalizedTransaction;
import com.radixdlt.client.lib.dto.LocalAccount;
import com.radixdlt.client.lib.dto.LocalValidatorInfo;
import com.radixdlt.client.lib.dto.MempoolConfiguration;
import com.radixdlt.client.lib.dto.MempoolData;
import com.radixdlt.client.lib.dto.NetworkConfiguration;
import com.radixdlt.client.lib.dto.NetworkData;
import com.radixdlt.client.lib.dto.NetworkId;
import com.radixdlt.client.lib.dto.NetworkPeers;
import com.radixdlt.client.lib.dto.NetworkStats;
import com.radixdlt.client.lib.dto.Proof;
import com.radixdlt.client.lib.dto.RadixEngineConfiguration;
import com.radixdlt.client.lib.dto.RadixEngineData;
import com.radixdlt.client.lib.dto.StakePositions;
import com.radixdlt.client.lib.dto.SyncConfiguration;
import com.radixdlt.client.lib.dto.SyncData;
import com.radixdlt.client.lib.dto.TokenBalances;
import com.radixdlt.client.lib.dto.TokenInfo;
import com.radixdlt.client.lib.dto.TransactionDTO;
import com.radixdlt.client.lib.dto.TransactionHistory;
import com.radixdlt.client.lib.dto.TransactionStatusDTO;
import com.radixdlt.client.lib.dto.TxDTO;
import com.radixdlt.client.lib.dto.UnstakePositions;
import com.radixdlt.client.lib.dto.ValidatorDTO;
import com.radixdlt.client.lib.dto.ValidatorsResponse;
import com.radixdlt.identifiers.AID;

import java.time.Duration;
import java.util.List;
import java.util.Optional;

public interface RadixApi {
	int DEFAULT_PRIMARY_PORT = 8080;
	int DEFAULT_SECONDARY_PORT = 3333;

	static Promise<RadixApi> connect(String baseUrl) {
		return connect(baseUrl, DEFAULT_PRIMARY_PORT, DEFAULT_SECONDARY_PORT);
	}

	static Promise<RadixApi> connect(String baseUrl, int primaryPort, int secondaryPort) {
		return AsyncRadixApi.connect(baseUrl, primaryPort, secondaryPort);
	}

	RadixApi withTrace();

	AsyncRadixApi withTimeout(Duration timeout);

	interface Network {
		Promise<NetworkId> id();

		Promise<NetworkStats> throughput();

		Promise<NetworkStats> demand();

		Promise<NetworkConfiguration> configuration();

		Promise<NetworkData> data();

		Promise<NetworkPeers> peers();
	}

	Network network();

	interface Transaction {
		Promise<BuiltTransaction> build(TransactionRequest request);

		Promise<TxDTO> finalize(FinalizedTransaction request);

		Promise<TxDTO> submit(FinalizedTransaction request);

		Promise<TransactionDTO> lookup(AID txId);

		Promise<TransactionStatusDTO> status(AID txId);
	}

	Transaction transaction();

	interface Token {
		Promise<TokenInfo> describeNative();

		Promise<TokenInfo> describe(String rri);
	}

	Token token();

	interface Local {
		Promise<LocalAccount> accountInfo();

		Promise<TxDTO> submitTxSingleStep();

		Promise<LocalValidatorInfo> validatorInfo();

		Promise<EpochData> currentEpoch();

		Promise<EpochData> nextEpoch();
	}

	Local local();

	interface SingleAccount {
		Promise<TokenBalances> balances(AccountAddress address);

		Promise<TransactionHistory> history(AccountAddress address, int size, Optional<NavigationCursor> cursor);

		Promise<List<StakePositions>> stakes(AccountAddress address);

		Promise<List<UnstakePositions>> unstakes(AccountAddress address);
	}

	SingleAccount account();

	interface Validator {
		Promise<ValidatorsResponse> list(int size, Optional<NavigationCursor> cursor);

		Promise<ValidatorDTO> lookup(String validatorAddress);
	}

	Validator validator();

	interface Api {
		Promise<ApiConfiguration> configuration();

		Promise<ApiData> data();
	}

	Api api();

	interface Consensus {
		Promise<ConsensusConfiguration> configuration();

		Promise<ConsensusData> data();
	}

	Consensus consensus();

	interface Mempool {
		Promise<MempoolConfiguration> configuration();

		Promise<MempoolData> data();
	}

	Mempool mempool();

	interface RadixEngine {
		Promise<RadixEngineConfiguration> configuration();

		Promise<RadixEngineData> data();
	}

	RadixEngine radixEngine();

	interface Sync {
		Promise<SyncConfiguration> configuration();

		Promise<SyncData> data();
	}

	Sync sync();

	interface Ledger {
		Promise<Proof> latest(); //ledger.get_latest_proof

		Promise<Proof> epoch(); //ledger.get_latest_epoch_proof

		Promise<Checkpoint> checkpoints(); //checkpoints.get_checkpoints
	}

	Ledger ledger();
}
